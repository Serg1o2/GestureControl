"""Low-light benchmark: how MediaPipe (app version) degrades in the dark and what preprocessing helps.

Real dark scenes cannot be re-shot for IPN, so they are simulated with a simple sensor model on
the clean videos (the clean landmarks are the reference):

    linear = srgb_to_linear(frame) * exposure            # fewer photons
    electrons = Poisson(linear * FULL_WELL) + N(0, READ_NOISE)
    out = linear_to_srgb(electrons / FULL_WELL * gain)    # auto-exposure gain, capped at MAX_GAIN
    + mild ISP denoise (3x3 blur) and 8-bit quantization

`gain = min(1 / exposure, MAX_GAIN)`: like a phone, the camera first raises the gain (noise grows)
and only below 1 / MAX_GAIN of the light the image also gets darker.

Enhancements are candidates for the app (cheap per-pixel ops a phone can do on every frame):
- gamma: adaptive gamma that brings the mean luma to TARGET_LUMA (a 256-entry LUT on device);
- gamma_blur: 3x3 blur, then the same gamma (noise suppression before the stretch);
- clahe: OpenCV CLAHE on the L channel (reference point; costly on the phone);
- blur3: 3x3 box blur only (noise suppression without changing brightness);
- half: 2x downscale with area averaging (noise suppression; MediaPipe resizes to 192/224 px anyway);
- conf70: no image change, detector presence/tracking thresholds 0.7 instead of 0.5.

    python -m gc_ml.lowlight run --videos 8 --workers 10     # landmarks for every condition
    python -m gc_ml.lowlight report                          # metrics -> artifacts/lowlight/report.md

Outputs per condition: data/features/ipn_landmarks_ll/<condition>/<video>.npz in the same format
as ipn.extract (used by the gesture replay bench), plus the handedness score per frame.
"""

from __future__ import annotations

import argparse
import json
import zlib
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path

import numpy as np

from .ipn import LANDMARKS, MIRROR_TO_USER_VIEW, RAW, VIDEOS, read_list

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "features" / "ipn_landmarks_ll"
REPORT_DIR = ROOT / "artifacts" / "lowlight"

FULL_WELL = 800.0
READ_NOISE = 2.5
MAX_GAIN = 8.0
TARGET_LUMA = 0.45

# exposure = fraction of the light of the original (well-lit) video.
# "dark2x" = "dark" with twice the exposure time, i.e. the camera allowed to drop to 15 fps.
LEVELS = {"dim": 0.1, "dark2x": 0.06, "dark": 0.03, "vdark": 0.012}
ENHANCE = ["none", "gamma", "gamma_blur", "clahe", "blur3", "half", "conf70"]
# Detector confidences per variant: "conf70" leaves the image alone but raises MediaPipe's presence
# and tracking thresholds (the app uses 0.5), against "ghost" hands kept alive by sensor noise.
CONFIDENCE = {"conf70": 0.7}


def conditions() -> list[str]:
    return [f"{lvl}_{enh}" for lvl in LEVELS for enh in ENHANCE]


def _srgb_to_linear(x: np.ndarray) -> np.ndarray:
    return np.where(x <= 0.04045, x / 12.92, ((x + 0.055) / 1.055) ** 2.4)


def _linear_to_srgb(x: np.ndarray) -> np.ndarray:
    x = np.clip(x, 0.0, 1.0)
    return np.where(x <= 0.0031308, x * 12.92, 1.055 * np.power(x, 1 / 2.4) - 0.055)


_TO_LINEAR = _srgb_to_linear(np.arange(256) / 255.0).astype(np.float32)
_SRGB_BINS = 4096
_TO_SRGB = (_linear_to_srgb(np.arange(_SRGB_BINS) / (_SRGB_BINS - 1)) * 255.0 + 0.5).astype(np.uint8)


def degrade(rgb: np.ndarray, exposure: float, rng: np.random.Generator) -> np.ndarray:
    """uint8 RGB -> simulated low-light uint8 RGB (see module doc).

    Shot noise uses the Gaussian approximation of Poisson (variance = mean), which is accurate
    above a few electrons and ~10x faster; sRGB conversions are lookup tables.
    """
    import cv2

    electrons = _TO_LINEAR[rgb] * np.float32(exposure * FULL_WELL)
    noise = rng.standard_normal(rgb.shape, dtype=np.float32)
    electrons += noise * np.sqrt(electrons + np.float32(READ_NOISE ** 2))
    gain = min(1.0 / exposure, MAX_GAIN)
    idx = electrons * np.float32(gain / FULL_WELL * (_SRGB_BINS - 1))
    out = _TO_SRGB[np.clip(idx, 0, _SRGB_BINS - 1).astype(np.int32)]
    return cv2.blur(out, (3, 3))


def mean_luma(rgb: np.ndarray) -> float:
    """Mean Rec.601 luma in [0, 1] on a 4x subsampled grid (what the app computes per frame)."""
    s = rgb[::4, ::4].astype(np.float32)
    return float((0.299 * s[..., 0] + 0.587 * s[..., 1] + 0.114 * s[..., 2]).mean() / 255.0)


def gamma_lut(luma: float, target: float = TARGET_LUMA, max_gamma: float = 1.0, min_gamma: float = 0.3) -> np.ndarray:
    """LUT for out = in ** g with g chosen so that mean luma maps to `target`; never darkens."""
    luma = min(max(luma, 1e-3), 0.999)
    g = float(np.clip(np.log(target) / np.log(luma), min_gamma, max_gamma))
    return (np.power(np.arange(256) / 255.0, g) * 255.0 + 0.5).astype(np.uint8)


def enhance(rgb: np.ndarray, how: str) -> np.ndarray:
    import cv2

    if how in ("none", "conf70"):
        return rgb
    if how == "blur3":
        return cv2.blur(rgb, (3, 3))
    if how == "half":
        return cv2.resize(rgb, (rgb.shape[1] // 2, rgb.shape[0] // 2), interpolation=cv2.INTER_AREA)
    if how == "clahe":
        lab = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB)
        lab[..., 0] = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8)).apply(lab[..., 0])
        return cv2.cvtColor(lab, cv2.COLOR_LAB2RGB)
    src = cv2.blur(rgb, (3, 3)) if how == "gamma_blur" else rgb
    return cv2.LUT(src, gamma_lut(mean_luma(rgb)))


def _job(args) -> list[dict]:
    """One video, one darkness level: the degraded frame goes through every enhancement."""
    video_path, level, model_path, out_dir = args
    stem = Path(video_path).stem
    todo = [how for how in ENHANCE if not (Path(out_dir) / f"{level}_{how}" / f"{stem}.npz").exists()]
    if not todo:
        return [{"video": stem, "level": level, "skipped": True}]
    import cv2
    import mediapipe as mp
    from mediapipe.tasks.python import BaseOptions
    from mediapipe.tasks.python.vision import HandLandmarker, HandLandmarkerOptions, RunningMode

    exposure = LEVELS[level]
    rng = np.random.default_rng(zlib.crc32(f"{stem}/{level}".encode()))
    def options(how: str):
        conf = CONFIDENCE.get(how, 0.5)
        return HandLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=model_path),
            running_mode=RunningMode.VIDEO,
            num_hands=1,
            min_hand_detection_confidence=0.5,
            min_hand_presence_confidence=conf,
            min_tracking_confidence=conf,
        )

    cap = cv2.VideoCapture(str(video_path))
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    dets = {how: HandLandmarker.create_from_options(options(how)) for how in todo}
    acc = {how: ([], [], []) for how in todo}  # landmarks, present, handedness score
    luma = []
    w = h = None
    i = 0
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        h, w = frame.shape[:2]
        dark = degrade(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB), exposure, rng)
        luma.append(mean_luma(dark))
        for how in todo:
            rgb = np.ascontiguousarray(enhance(dark, how))
            res = dets[how].detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), int(i * 1000 / fps))
            lms, present, score = acc[how]
            if res.hand_landmarks:
                pts = np.array([[p.x, p.y] for p in res.hand_landmarks[0]], np.float32)
                if MIRROR_TO_USER_VIEW:
                    pts[:, 0] = 1.0 - pts[:, 0]
                lms.append(pts)
                present.append(True)
                score.append(res.handedness[0][0].score if res.handedness else np.nan)
            else:
                lms.append(np.full((21, 2), np.nan, np.float32))
                present.append(False)
                score.append(np.nan)
        i += 1
    cap.release()
    out = []
    for how in todo:
        dets[how].close()
        lms, present, score = acc[how]
        f = Path(out_dir) / f"{level}_{how}" / f"{stem}.npz"
        f.parent.mkdir(parents=True, exist_ok=True)
        np.savez_compressed(f, lm=np.asarray(lms, np.float32), present=np.asarray(present), fps=fps,
                            aspect=(w / h) if w else 4 / 3, score=np.asarray(score, np.float32),
                            luma=np.asarray(luma, np.float32))
        out.append({"video": stem, "condition": f"{level}_{how}", "frames": len(lms),
                    "present": float(np.mean(present)) if present else 0.0})
    return out


def pick_videos(n: int, split: str = "test") -> list[Path]:
    """First n extracted videos of one IPN split, spread over subjects.

    "test" = the official validation subjects (never trained on); "train" = training subjects,
    used to make low-light fine-tuning data (gc_ml.dark_pairs).
    """
    lst = "vallistall.txt" if split == "test" else "trainlistall.txt"
    val = sorted({r[0] for r in read_list(RAW / "annotations" / lst)})
    have = [v for v in val if (VIDEOS / f"{v}.avi").exists() and (LANDMARKS / f"{v}.npz").exists()]
    by_subject: dict[str, list[str]] = {}
    for v in have:
        by_subject.setdefault(v.rsplit("_", 2)[0], []).append(v)
    picked: list[str] = []
    while len(picked) < min(n, len(have)):
        for vids in by_subject.values():
            if vids and len(picked) < n:
                picked.append(vids.pop(0))
    return [VIDEOS / f"{v}.avi" for v in picked]


def run(n_videos: int, workers: int, only: list[str] | None, split: str = "test") -> None:
    model = ROOT.parent / "app" / "src" / "main" / "assets" / "hand_landmarker.task"
    levels = only or list(LEVELS)
    jobs = [(str(v), lvl, str(model), str(OUT)) for v in pick_videos(n_videos, split) for lvl in levels]
    with ProcessPoolExecutor(workers) as pool:
        for i, fut in enumerate(as_completed([pool.submit(_job, j) for j in jobs]), 1):
            print(f"[{i}/{len(jobs)}] {fut.result()}", flush=True)


# ---------------------------------------------------------------- metrics

PALM = [0, 5, 9, 13, 17]


def _hand_size(lm: np.ndarray, aspect: float) -> np.ndarray:
    d = (lm[..., 9, :] - lm[..., 0, :]) * [aspect, 1.0]
    return np.linalg.norm(d, axis=-1)


def compare(ref: dict, cond: dict) -> dict:
    """Per-video metrics of a condition against the clean reference."""
    aspect = float(ref["aspect"])
    rp, cp = ref["present"], cond["present"]
    n = min(len(rp), len(cp))
    rp, cp = rp[:n], cp[:n]
    rl, cl = ref["lm"][:n], cond["lm"][:n]
    both = rp & cp
    size = _hand_size(rl[both], aspect)
    err = np.linalg.norm((cl[both] - rl[both]) * [aspect, 1.0], axis=-1) / np.maximum(size, 1e-6)[:, None]
    # Jitter: frame-to-frame second difference of the palm centre, beyond what the reference has.
    def jitter(lm, pres):
        palm = (lm[:, PALM, :] * [aspect, 1.0]).mean(1)
        ok = pres[2:] & pres[1:-1] & pres[:-2]
        acc = np.linalg.norm(palm[2:] - 2 * palm[1:-1] + palm[:-2], axis=-1)
        s = _hand_size(lm[1:-1], aspect)
        return acc[ok] / np.maximum(s[ok], 1e-6)
    # Jumps: palm moves more than one hand size between consecutive detected frames.
    def jumps(lm, pres):
        palm = (lm[:, PALM, :] * [aspect, 1.0]).mean(1)
        ok = pres[1:] & pres[:-1]
        d = np.linalg.norm(palm[1:] - palm[:-1], axis=-1) / np.maximum(_hand_size(lm[1:], aspect), 1e-6)
        return int((d[ok] > 1.0).sum())
    return {
        "frames": int(n),
        "ref_hand_frames": int(rp.sum()),
        "detected": int(both.sum()),
        "false_hand": int((cp & ~rp).sum()),
        "no_hand_frames": int((~rp).sum()),
        "err": err.mean(1),
        "jitter": jitter(cl, cp),
        "ref_jitter": jitter(rl, rp),
        "jumps": jumps(cl, cp),
        "ref_jumps": jumps(rl, rp),
        "score": cond["score"][:n][cp] if "score" in cond else np.array([]),
        "luma": float(np.mean(cond["luma"])) if "luma" in cond else float("nan"),
    }


def report() -> dict:
    rows = {}
    for cdir in sorted(p for p in OUT.iterdir() if p.is_dir()):
        parts = []
        for f in sorted(cdir.glob("*.npz")):
            ref = LANDMARKS / f.name
            if ref.exists():
                parts.append(compare(dict(np.load(ref)), dict(np.load(f))))
        if not parts:
            continue
        err = np.concatenate([p["err"] for p in parts])
        jit = np.concatenate([p["jitter"] for p in parts])
        rjit = np.concatenate([p["ref_jitter"] for p in parts])
        ref_hand = sum(p["ref_hand_frames"] for p in parts)
        rows[cdir.name] = {
            "videos": len(parts),
            "luma": float(np.mean([p["luma"] for p in parts])),
            "recall": sum(p["detected"] for p in parts) / max(ref_hand, 1),
            "false_hand": sum(p["false_hand"] for p in parts) / max(sum(p["no_hand_frames"] for p in parts), 1),
            "err_median": float(np.median(err)) if len(err) else float("nan"),
            "err_p90": float(np.percentile(err, 90)) if len(err) else float("nan"),
            "bad_frames": float(np.mean(err > 0.25)) if len(err) else float("nan"),
            "jitter_median": float(np.median(jit)) if len(jit) else float("nan"),
            "ref_jitter_median": float(np.median(rjit)) if len(rjit) else float("nan"),
            "jumps_per_min": sum(p["jumps"] for p in parts) / (sum(p["frames"] for p in parts) / 30 / 60),
            "ref_jumps_per_min": sum(p["ref_jumps"] for p in parts) / (sum(p["frames"] for p in parts) / 30 / 60),
            "score_median": float(np.nanmedian(np.concatenate([p["score"] for p in parts]))),
        }
        # Does a low handedness score flag bad landmarks? (usable as a per-frame quality gate)
        sc = np.concatenate([p["score"][: len(p["err"])] for p in parts if len(p["score"])])
        if len(sc) == len(err) and len(err) > 100:
            low = sc < np.nanpercentile(sc, 20)
            rows[cdir.name]["bad_frames_low_score"] = float(np.mean(err[low] > 0.25))
            rows[cdir.name]["bad_frames_high_score"] = float(np.mean(err[~low] > 0.25))
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    (REPORT_DIR / "summary.json").write_text(json.dumps(rows, indent=1))
    lines = [
        "# Low-light benchmark (MediaPipe 0.10.20, IPN test subjects)",
        "",
        f"Sensor model: full well {FULL_WELL:.0f} e-, read noise {READ_NOISE} e-, max gain {MAX_GAIN}x; "
        f"levels (fraction of the original light): {LEVELS}. Reference = clean video landmarks. "
        "Errors are mean landmark distance in hand sizes on frames where both found the hand; "
        "'bad' = error > 0.25 hand sizes. Jitter = median palm acceleration per frame (hand sizes).",
        "",
        "| condition | luma | recall | false hand | err median | err p90 | bad frames | jitter (ref) | jumps/min (ref) | bad @ low score / rest |",
        "|---|---|---|---|---|---|---|---|---|---|",
    ]
    for name, r in rows.items():
        lines.append(
            f"| {name} | {r['luma']:.2f} | {r['recall']:.1%} | {r['false_hand']:.2%} | {r['err_median']:.3f} | "
            f"{r['err_p90']:.3f} | {r['bad_frames']:.1%} | {r['jitter_median']:.3f} ({r['ref_jitter_median']:.3f}) | "
            f"{r['jumps_per_min']:.1f} ({r['ref_jumps_per_min']:.1f}) | "
            f"{r.get('bad_frames_low_score', float('nan')):.1%} / {r.get('bad_frames_high_score', float('nan')):.1%} |"
        )
    (REPORT_DIR / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    return rows


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("--videos", type=int, default=8)
    r.add_argument("--workers", type=int, default=10)
    r.add_argument("--only", nargs="*", help="darkness levels (default: all)")
    r.add_argument("--split", choices=["test", "train"], default="test")
    sub.add_parser("report")
    args = ap.parse_args()
    if args.cmd == "run":
        run(args.videos, args.workers, args.only, args.split)
    else:
        report()


if __name__ == "__main__":
    main()
