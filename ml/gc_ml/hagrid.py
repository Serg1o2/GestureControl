"""HaGRIDv2: download annotations, build static features, re-extract a sample with our MediaPipe.

Annotation layout (per image id): bboxes [[x, y, w, h]...] (normalized), labels [...], user_id,
hand_landmarks [[[x, y] * 21] or [] per bbox]. Splits come from the folder name (train/val/test)
inside the archive; if absent, a stable hash of user_id decides (split by person).
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import zipfile
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path

import numpy as np

from .classes import HAGRID_ALL, POSE_INDEX, hagrid_pose
from .normalize import NUM_LANDMARKS, normalize_shape

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "data" / "raw" / "hagrid"
FEATURES_DIR = ROOT / "data" / "features"
BASE_URL = "https://rndml-team-cv.obs.ru-moscow-1.hc.sbercloud.ru/datasets/hagrid_v2"
ANNOTATIONS_URL = f"{BASE_URL}/annotations_with_landmarks/annotations.zip"
IMAGES_512_URL = f"{BASE_URL}/hagridv2_512.zip"
SPLITS = {"train": 0, "val": 1, "test": 2}


def split_of(path: str, user_id: str) -> int:
    parts = path.replace("\\", "/").lower().split("/")
    for name, code in SPLITS.items():
        if name in parts:
            return code
    h = int(hashlib.md5(user_id.encode()).hexdigest(), 16) % 100
    return 2 if h < 15 else 1 if h < 24 else 0


def iter_annotations(zip_path: Path):
    """Yields (image_id, label, landmarks (21, 2), split, user_id) for every annotated hand."""
    with zipfile.ZipFile(zip_path) as zf:
        for name in zf.namelist():
            if not name.endswith(".json"):
                continue
            data = json.loads(zf.read(name))
            for image_id, ann in data.items():
                user = str(ann.get("user_id", image_id))
                split = split_of(name, user)
                for label, lms in zip(ann.get("labels", []), ann.get("hand_landmarks", [])):
                    if not lms or len(lms) != NUM_LANDMARKS:
                        continue
                    yield image_id, label, np.asarray(lms, dtype=np.float32)[:, :2], split, user


def build_annotation_features(zip_path: Path, out: Path, aspect: float) -> dict:
    """Static features from the landmarks shipped with the annotations (another MediaPipe version, no z)."""
    xs, ys, splits, labels = [], [], [], []
    for _, label, lms, split, _ in iter_annotations(zip_path):
        xs.append(lms)
        ys.append(POSE_INDEX[hagrid_pose(label)])
        splits.append(split)
        labels.append(HAGRID_ALL.index(label) if label in HAGRID_ALL else -1)
    lm = np.stack(xs)
    x = normalize_shape(lm, aspect)
    ok = np.isfinite(x).all(1)
    out.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(
        out,
        x=x[ok], y=np.asarray(ys)[ok], split=np.asarray(splits)[ok],
        source=np.zeros(ok.sum(), np.int8), hagrid_label=np.asarray(labels)[ok],
    )
    # Raw sample for the Kotlin parity vectors (export_vectors.py).
    pick = np.random.default_rng(0).permutation(np.where(ok)[0])[:500]
    np.savez_compressed(FEATURES_DIR / "hagrid_sample_landmarks.npz", lm=lm[pick], aspect=np.full(len(pick), aspect, np.float32))
    stats = {"hands": int(len(lm)), "kept": int(ok.sum()), "aspect": aspect,
             "per_split": np.bincount(np.asarray(splits)[ok], minlength=3).tolist(),
             "per_class": np.bincount(np.asarray(ys)[ok], minlength=len(POSE_INDEX)).tolist()}
    (out.with_suffix(".json")).write_text(json.dumps(stats, indent=2))
    return stats


# --- Re-extraction of a sample of 512px images with our MediaPipe version ---------------------


def _detector(model_path: str):
    import mediapipe as mp
    from mediapipe.tasks.python import BaseOptions
    from mediapipe.tasks.python.vision import HandLandmarker, HandLandmarkerOptions, RunningMode

    opts = HandLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=model_path),
        running_mode=RunningMode.IMAGE,
        num_hands=2,
        min_hand_detection_confidence=0.5,
        min_hand_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    return mp, HandLandmarker.create_from_options(opts)


def _match_hand(detected: list[np.ndarray], bbox: list[float]) -> np.ndarray | None:
    """Pick the detected hand whose landmark centroid lies in the labeled bbox (closest to its center)."""
    x, y, w, h = bbox
    best, best_d = None, 1e9
    for lms in detected:
        cx, cy = lms[:, 0].mean(), lms[:, 1].mean()
        if x - 0.1 * w <= cx <= x + 1.1 * w and y - 0.1 * h <= cy <= y + 1.1 * h:
            d = (cx - x - w / 2) ** 2 + (cy - y - h / 2) ** 2
            if d < best_d:
                best, best_d = lms, d
    return best


_connections: dict[str, object] = {}


def _connection(url: str):
    """One persistent HTTPS connection per process: the TLS handshake to the far server dominates."""
    import http.client
    import urllib.parse

    host = urllib.parse.urlparse(url).netloc
    if host not in _connections:
        _connections[host] = http.client.HTTPSConnection(host, timeout=60)
    return _connections[host]


def fetch_member(url: str, member: dict, retries: int = 4) -> bytes:
    """One HTTP range request for a zip member: local header + data; inflates if deflated."""
    import struct
    import urllib.parse
    import zlib

    start = member["offset"]
    end = start + 30 + len(member["name"].encode()) + 1024 + member["csize"]
    path = urllib.parse.urlparse(url).path
    for attempt in range(retries):
        try:
            conn = _connection(url)
            conn.request("GET", path, headers={"Range": f"bytes={start}-{end}", "Connection": "keep-alive"})
            resp = conn.getresponse()
            buf = resp.read()
            if resp.status not in (200, 206):
                raise IOError(f"HTTP {resp.status}")
            n, e = struct.unpack("<HH", buf[26:30])
            data = buf[30 + n + e:30 + n + e + member["csize"]]
            return data if member["method"] == 0 else zlib.decompress(data, -15)
        except Exception:
            _connections.pop(urllib.parse.urlparse(url).netloc, None)
            if attempt == retries - 1:
                raise
    raise RuntimeError("unreachable")


def _extract_chunk(args) -> dict:
    """Worker: fetch images by range requests, run MediaPipe, return landmarks + aspect."""
    chunk_id, members, ann, model_path, out_dir = args
    out_file = Path(out_dir) / f"chunk_{chunk_id:05d}.npz"
    if out_file.exists():
        return {"chunk": chunk_id, "skipped": True}
    import cv2

    mp, det = _detector(model_path)
    rows = {"lm": [], "aspect": [], "label": [], "split": [], "ann_lm": []}
    lost = 0
    failed = 0
    for member in members:
        image_id = Path(member["name"]).stem
        a = ann.get(image_id)
        if a is None:
            continue
        try:
            buf = np.frombuffer(fetch_member(IMAGES_512_URL, member), np.uint8)
        except Exception:
            failed += 1
            continue
        img = cv2.imdecode(buf, cv2.IMREAD_COLOR)
        if img is None:
            failed += 1
            continue
        h, w = img.shape[:2]
        res = det.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(img, cv2.COLOR_BGR2RGB)))
        detected = [np.array([[p.x, p.y] for p in hand], np.float32) for hand in res.hand_landmarks]
        for bbox, label, ann_lm in zip(a["bboxes"], a["labels"], a.get("hand_landmarks", [])):
            lms = _match_hand(detected, bbox)
            if lms is None:
                lost += 1
                continue
            rows["lm"].append(lms)
            rows["aspect"].append(w / h)
            rows["label"].append(label)
            rows["split"].append(a["split"])
            rows["ann_lm"].append(np.asarray(ann_lm, np.float32)[:, :2] if ann_lm else np.full((21, 2), np.nan, np.float32))
    det.close()
    np.savez_compressed(
        out_file,
        lm=np.asarray(rows["lm"], np.float32).reshape(-1, 21, 2),
        aspect=np.asarray(rows["aspect"], np.float32),
        label=np.asarray(rows["label"]),
        split=np.asarray(rows["split"], np.int8),
        ann_lm=np.asarray(rows["ann_lm"], np.float32).reshape(-1, 21, 2),
        lost=np.asarray(lost),
        failed=np.asarray(failed),
    )
    return {"chunk": chunk_id, "hands": len(rows["lm"]), "lost": lost, "failed": failed}


def zip_directory(url: str, cache: Path) -> list[dict]:
    """Central directory of the remote zip (name, offset, sizes), downloaded once and cached."""
    if cache.exists():
        return json.loads(cache.read_text())
    from remotezip import RemoteZip

    with RemoteZip(url) as rz:
        entries = [
            {"name": i.filename, "offset": i.header_offset, "csize": i.compress_size, "method": i.compress_type}
            for i in rz.infolist() if i.filename.lower().endswith((".jpg", ".jpeg"))
        ]
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_text(json.dumps(entries))
    return entries


def reextract_sample(ann_zip: Path, per_class: int, workers: int, out_dir: Path, model_path: Path, seed: int = 42) -> dict:
    """Downloads only `per_class` images of each class (HTTP range requests) and extracts landmarks.

    Chunks already on disk are skipped, so the run can be resumed after an interruption.
    """
    ann: dict[str, dict] = {}
    with zipfile.ZipFile(ann_zip) as zf:
        for name in zf.namelist():
            if name.endswith(".json"):
                for image_id, a in json.loads(zf.read(name)).items():
                    a["split"] = split_of(name, str(a.get("user_id", image_id)))
                    ann[image_id] = a
    members = zip_directory(IMAGES_512_URL, RAW / "hagridv2_512_directory.json")
    rng = np.random.default_rng(seed)
    by_class: dict[str, list[dict]] = {}
    for m in members:
        by_class.setdefault(Path(m["name"]).parent.name, []).append(m)
    chosen = []
    for cls, ms in sorted(by_class.items()):
        idx = rng.permutation(len(ms))[:per_class]
        chosen.extend(ms[i] for i in idx)
    chosen.sort(key=lambda m: m["name"])
    out_dir.mkdir(parents=True, exist_ok=True)
    chunks = [chosen[i:i + 100] for i in range(0, len(chosen), 100)]
    jobs = [
        (i, c, {Path(m["name"]).stem: ann.get(Path(m["name"]).stem) for m in c}, str(model_path), str(out_dir))
        for i, c in enumerate(chunks)
    ]
    done = 0
    with ProcessPoolExecutor(workers) as pool:
        for fut in as_completed([pool.submit(_extract_chunk, j) for j in jobs]):
            r = fut.result()
            done += 1
            print(f"chunk {r['chunk']} {r} ({done}/{len(jobs)})", flush=True)
    return {"images": len(chosen), "chunks": len(chunks)}


def build_reextracted_features(chunk_dir: Path, out: Path) -> dict:
    parts = [np.load(p, allow_pickle=False) for p in sorted(chunk_dir.glob("chunk_*.npz"))]
    lm = np.concatenate([p["lm"] for p in parts])
    aspect = np.concatenate([p["aspect"] for p in parts])
    labels = np.concatenate([p["label"] for p in parts])
    split = np.concatenate([p["split"] for p in parts])
    lost = int(sum(int(p["lost"]) for p in parts))
    x = normalize_shape(lm, aspect)
    y = np.array([POSE_INDEX[hagrid_pose(str(l))] for l in labels])
    np.savez_compressed(out, x=x, y=y, split=split, source=np.ones(len(x), np.int8))
    stats = {"hands": int(len(x)), "lost_hands": lost, "loss_rate": lost / max(lost + len(x), 1),
             "aspect_values": {str(round(float(a), 3)): int(c) for a, c in zip(*np.unique(aspect.round(3), return_counts=True))}}
    out.with_suffix(".json").write_text(json.dumps(stats, indent=2))
    return stats


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("annotations")
    a.add_argument("--aspect", type=float, required=True, help="image width / height of HaGRID photos")
    r = sub.add_parser("reextract")
    r.add_argument("--per-class", type=int, default=150)
    r.add_argument("--workers", type=int, default=8)
    sub.add_parser("reextract-features")
    args = ap.parse_args()

    if args.cmd == "annotations":
        print(build_annotation_features(RAW / "annotations.zip", FEATURES_DIR / "static_hagrid_ann.npz", args.aspect))
    elif args.cmd == "reextract":
        model = ROOT.parent / "app" / "src" / "main" / "assets" / "hand_landmarker.task"
        print(reextract_sample(RAW / "annotations.zip", args.per_class, args.workers, FEATURES_DIR / "hagrid_mp_chunks", model))
    elif args.cmd == "reextract-features":
        print(build_reextracted_features(FEATURES_DIR / "hagrid_mp_chunks", FEATURES_DIR / "static_hagrid_mp.npz"))


if __name__ == "__main__":
    main()
