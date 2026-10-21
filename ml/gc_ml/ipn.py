"""IPN Hand: download, landmark extraction from videos (resumable, parallel), dynamic windows.

Annotation lists (from the IPN-hand repo): "./frames/<video> <class_id> <start> <end>", 1-based
frame numbers, class ids per classIndAll.txt. trainlistall.txt / vallistall.txt are disjoint by
subject; we use vallist as the person-independent test set.
"""

from __future__ import annotations

import argparse
import json
import tarfile
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path

import numpy as np

from .classes import DYNAMIC_CLASSES, IPN_TO_DYNAMIC
from .dynamic_features import WINDOW, window_features

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "data" / "raw" / "ipn"
VIDEOS = RAW / "videos"
LANDMARKS = ROOT / "data" / "features" / "ipn_landmarks"
VIDEOS_DRIVE_FOLDER = "1O4Fn_jbAEcKIksXHmQIMcHTyaDw4wt9x"
ANNOTATION_BASE = "https://raw.githubusercontent.com/GibranBenitez/IPN-hand/master/annotation_ipnGesture"

# The app works in "user view" (mirrored front camera). If IPN videos are raw (non-mirrored)
# webcam images, x must be flipped so that "left" means the user's left. The check on G05
# ("throw left") samples is recorded in docs/class_mapping.md.
MIRROR_TO_USER_VIEW = True


def download() -> None:
    import urllib.request

    import gdown

    (RAW / "annotations").mkdir(parents=True, exist_ok=True)
    for f in ("classIndAll.txt", "trainlistall.txt", "vallistall.txt"):
        dst = RAW / "annotations" / f
        if not dst.exists():
            urllib.request.urlretrieve(f"{ANNOTATION_BASE}/{f}", dst)
    VIDEOS.mkdir(parents=True, exist_ok=True)
    gdown.download_folder(id=VIDEOS_DRIVE_FOLDER, output=str(RAW / "videos_tgz"), quiet=False, resume=True)


def unpack() -> None:
    for tgz in sorted((RAW / "videos_tgz").rglob("*.tgz")):
        marker = VIDEOS / f".{tgz.stem}.done"
        if marker.exists():
            continue
        with tarfile.open(tgz) as tf:
            for m in tf.getmembers():
                if m.isfile() and m.name.lower().endswith((".avi", ".mp4")):
                    m.name = Path(m.name).name
                    tf.extract(m, VIDEOS)
        marker.touch()


def _extract_video(args) -> dict:
    video_path, model_path, out_dir = args
    out = Path(out_dir) / (Path(video_path).stem + ".npz")
    if out.exists():
        return {"video": Path(video_path).stem, "skipped": True}
    import cv2
    import mediapipe as mp
    from mediapipe.tasks.python import BaseOptions
    from mediapipe.tasks.python.vision import HandLandmarker, HandLandmarkerOptions, RunningMode

    # Same settings as the app (HandLandmarkerEngine): VIDEO mode, 1 hand, 0.5 confidences.
    opts = HandLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=model_path),
        running_mode=RunningMode.VIDEO,
        num_hands=1,
        min_hand_detection_confidence=0.5,
        min_hand_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    cap = cv2.VideoCapture(str(video_path))
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    lms, present = [], []
    w = h = None
    with HandLandmarker.create_from_options(opts) as det:
        i = 0
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            h, w = frame.shape[:2]
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            res = det.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), int(i * 1000 / fps))
            if res.hand_landmarks:
                pts = np.array([[p.x, p.y] for p in res.hand_landmarks[0]], np.float32)
                if MIRROR_TO_USER_VIEW:
                    pts[:, 0] = 1.0 - pts[:, 0]
                lms.append(pts)
                present.append(True)
            else:
                lms.append(np.full((21, 2), np.nan, np.float32))
                present.append(False)
            i += 1
    cap.release()
    np.savez_compressed(out, lm=np.asarray(lms, np.float32), present=np.asarray(present), fps=fps,
                        aspect=(w / h) if w else 4 / 3)
    return {"video": Path(video_path).stem, "frames": len(lms), "present": float(np.mean(present)) if present else 0.0}


def extract(workers: int, limit: int | None = None) -> None:
    model = ROOT.parent / "app" / "src" / "main" / "assets" / "hand_landmarker.task"
    LANDMARKS.mkdir(parents=True, exist_ok=True)
    videos = sorted(p for p in VIDEOS.iterdir() if p.suffix.lower() in (".avi", ".mp4"))[:limit]
    jobs = [(str(v), str(model), str(LANDMARKS)) for v in videos]
    stats = []
    with ProcessPoolExecutor(workers) as pool:
        for i, fut in enumerate(as_completed([pool.submit(_extract_video, j) for j in jobs]), 1):
            r = fut.result()
            stats.append(r)
            print(f"[{i}/{len(jobs)}] {r}", flush=True)
    (LANDMARKS / "_stats.json").write_text(json.dumps(stats, indent=1))


def read_list(path: Path) -> list[tuple[str, int, int, int]]:
    rows = []
    for line in path.read_text().split("\n"):
        parts = line.split()
        if len(parts) == 4:
            rows.append((Path(parts[0]).name, int(parts[1]), int(parts[2]), int(parts[3])))
    return rows


def build_windows(out: Path, stride_neg: int = 6, seed: int = 42) -> dict:
    """Labelled fixed-length windows from annotated segments.

    Gesture segments: windows ending in the last 30% of the segment (+3 frames), so the window
    contains most of the movement. NO_GESTURE: windows strided over non-gesture segments.
    """
    classes = {int(l.split()[0]): l.split()[1] for l in (RAW / "annotations" / "classIndAll.txt").read_text().split("\n") if l.strip()}
    rng = np.random.default_rng(seed)
    xs, ys, splits, video_ids = [], [], [], []
    lists = [(RAW / "annotations" / "trainlistall.txt", 0), (RAW / "annotations" / "vallistall.txt", 2)]
    train_videos = sorted({r[0] for r in read_list(lists[0][0])})
    # Hold out ~15% of the training videos (whole videos) for validation.
    val_videos = set(rng.permutation(train_videos)[: max(1, len(train_videos) * 15 // 100)])
    missing_frames = total_frames = 0
    for list_path, split_code in lists:
        for video, cls_id, start, end in read_list(list_path):
            f = LANDMARKS / f"{video}.npz"
            if not f.exists():
                continue
            d = np.load(f)
            lm, present, aspect = d["lm"], d["present"], float(d["aspect"])
            label = IPN_TO_DYNAMIC[classes[cls_id]]
            y = DYNAMIC_CLASSES.index(label)
            split = 1 if split_code == 0 and video in val_videos else split_code
            s, e = start - 1, min(end, len(lm))
            total_frames += e - s
            missing_frames += int((~present[s:e]).sum())
            if label == "NO_GESTURE":
                ends = range(s + WINDOW, e + 1, stride_neg)
            else:
                length = e - s
                ends = range(max(s + WINDOW, s + int(length * 0.7)), min(e + 3, len(lm)) + 1)
            for end_i in ends:
                feats = window_features(lm[end_i - WINDOW:end_i], present[end_i - WINDOW:end_i], aspect)
                if feats is None:
                    continue
                xs.append(feats)
                ys.append(y)
                splits.append(split)
                video_ids.append(video)
    x = np.stack(xs)
    np.savez_compressed(out, x=x, y=np.asarray(ys), split=np.asarray(splits, np.int8), video=np.asarray(video_ids))
    stats = {"windows": int(len(x)), "per_class": np.bincount(ys, minlength=len(DYNAMIC_CLASSES)).tolist(),
             "per_split": np.bincount(splits, minlength=3).tolist(),
             "hand_missing_rate_in_segments": missing_frames / max(total_frames, 1)}
    out.with_suffix(".json").write_text(json.dumps(stats, indent=2))
    return stats


def check_mirror() -> dict:
    """Mean palm x travel (user view, in hand sizes) over G05 'throw left' / G06 'throw right' segments.

    With correct mirroring, throw left must move toward negative x (the user's left).
    """
    classes = {int(l.split()[0]): l.split()[1] for l in (RAW / "annotations" / "classIndAll.txt").read_text().split("\n") if l.strip()}
    travel: dict[str, list[float]] = {"G05": [], "G06": []}
    for list_name in ("trainlistall.txt", "vallistall.txt"):
        for video, cls_id, start, end in read_list(RAW / "annotations" / list_name):
            label = classes[cls_id]
            f = LANDMARKS / f"{video}.npz"
            if label not in travel or not f.exists():
                continue
            d = np.load(f)
            lm, present, aspect = d["lm"][start - 1:end], d["present"][start - 1:end], float(d["aspect"])
            if present.sum() < 4:
                continue
            palm = lm[present][:, [0, 5, 9, 13, 17], 0].mean(1) * aspect
            size = np.nanmean(np.linalg.norm((lm[present][:, 9] - lm[present][:, 0]) * [aspect, 1], axis=1))
            travel[label].append(float((palm[-1] - palm[0]) / max(size, 1e-6)))
    result = {k: {"n": len(v), "mean_dx_hand_sizes": float(np.mean(v)) if v else None} for k, v in travel.items()}
    ok = result["G05"]["n"] and result["G06"]["n"] and result["G05"]["mean_dx_hand_sizes"] < 0 < result["G06"]["mean_dx_hand_sizes"]
    result["mirroring_consistent"] = bool(ok)
    return result


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("download")
    sub.add_parser("unpack")
    e = sub.add_parser("extract")
    e.add_argument("--workers", type=int, default=8)
    e.add_argument("--limit", type=int, default=None)
    sub.add_parser("windows")
    sub.add_parser("check-mirror")
    args = ap.parse_args()
    if args.cmd == "download":
        download()
    elif args.cmd == "unpack":
        unpack()
    elif args.cmd == "extract":
        extract(args.workers, args.limit)
    elif args.cmd == "windows":
        print(build_windows(ROOT / "data" / "features" / "dynamic_ipn.npz"))
    elif args.cmd == "check-mirror":
        print(check_mirror())


if __name__ == "__main__":
    main()
