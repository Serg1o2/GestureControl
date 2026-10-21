"""Baseline: MediaPipe Gesture Recognizer vs our pose model on the same HaGRID photos.

Step 1 (.venv-extract): `python -m gc_ml.baseline collect --per-class 20` downloads a separate photo
sample (seed 7), runs the Gesture Recognizer and our Hand Landmarker, saves predictions + landmarks.
Step 2 (.venv-train): `python -m gc_ml.baseline evaluate` runs our TFLite model on those landmarks
and writes artifacts/baseline/report.md (shared classes only).
"""

from __future__ import annotations

import argparse
import json
import sys
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path

import numpy as np

from .classes import MP_RECOGNIZER_TO_POSE, POSE_CLASSES, POSE_INDEX, hagrid_pose

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts" / "baseline"
RECOGNIZER_URL = "https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/latest/gesture_recognizer.task"


def _worker(args) -> list[dict]:
    members, labels, recognizer_path, landmarker_path = args
    import cv2
    import mediapipe as mp
    from mediapipe.tasks.python import BaseOptions
    from mediapipe.tasks.python.vision import (
        GestureRecognizer, GestureRecognizerOptions, HandLandmarker, HandLandmarkerOptions, RunningMode,
    )

    from .hagrid import IMAGES_512_URL, fetch_member

    rec = GestureRecognizer.create_from_options(GestureRecognizerOptions(
        base_options=BaseOptions(model_asset_path=recognizer_path), running_mode=RunningMode.IMAGE, num_hands=1))
    lmk = HandLandmarker.create_from_options(HandLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=landmarker_path), running_mode=RunningMode.IMAGE, num_hands=1,
        min_hand_detection_confidence=0.5, min_hand_presence_confidence=0.5, min_tracking_confidence=0.5))
    rows = []
    for m, label in zip(members, labels):
        try:
            img = cv2.imdecode(np.frombuffer(fetch_member(IMAGES_512_URL, m), np.uint8), cv2.IMREAD_COLOR)
        except Exception:
            continue
        if img is None:
            continue
        h, w = img.shape[:2]
        image = mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(img, cv2.COLOR_BGR2RGB))
        r = rec.recognize(image)
        mp_label = r.gestures[0][0].category_name if r.gestures else "None"
        d = lmk.detect(image)
        lm = [[p.x, p.y] for p in d.hand_landmarks[0]] if d.hand_landmarks else None
        rows.append({"label": label, "mp": mp_label, "lm": lm, "aspect": w / h})
    rec.close()
    lmk.close()
    return rows


def collect(per_class: int, workers: int) -> None:
    from .download import download
    from .hagrid import RAW, zip_directory, IMAGES_512_URL

    recognizer = ROOT / "data" / "raw" / "gesture_recognizer.task"
    if not recognizer.exists():
        download(RECOGNIZER_URL, recognizer)
    landmarker = ROOT.parent / "app" / "src" / "main" / "assets" / "hand_landmarker.task"
    members = zip_directory(IMAGES_512_URL, RAW / "hagridv2_512_directory.json")
    rng = np.random.default_rng(7)
    by_class: dict[str, list[dict]] = {}
    for m in members:
        by_class.setdefault(Path(m["name"]).parent.name, []).append(m)
    chosen = [(m, cls) for cls, ms in sorted(by_class.items()) if not cls.startswith(".")
              for m in (ms[i] for i in rng.permutation(len(ms))[:per_class])]
    chunks = [chosen[i::workers] for i in range(workers)]
    rows = []
    with ProcessPoolExecutor(workers) as pool:
        futs = [pool.submit(_worker, ([c[0] for c in ch], [c[1] for c in ch], str(recognizer), str(landmarker))) for ch in chunks]
        for f in as_completed(futs):
            rows.extend(f.result())
            print(f"collected {len(rows)}", flush=True)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "collected.json").write_text(json.dumps(rows))


def evaluate() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    from .metrics import apply_thresholds, markdown, report
    from .normalize import normalize_shape
    from .train_static import tflite_predict

    rows = json.loads((OUT / "collected.json").read_text())
    meta = json.loads((ROOT / "artifacts" / "static" / "pose_mlp.json").read_text())
    y = np.array([POSE_INDEX[hagrid_pose(r["label"])] for r in rows])
    mp_pred = np.array([POSE_INDEX[MP_RECOGNIZER_TO_POSE.get(r["mp"], "OTHER")] for r in rows])
    has = np.array([r["lm"] is not None for r in rows])
    ours = np.zeros(len(rows), int)  # no hand detected -> OTHER
    if has.any():
        lm = np.array([r["lm"] for r in rows if r["lm"] is not None], np.float32)
        aspect = np.array([r["aspect"] for r in rows if r["lm"] is not None], np.float32)
        probs = tflite_predict(ROOT / "artifacts" / "static" / "pose_mlp.tflite", normalize_shape(lm, aspect))
        ours[has] = apply_thresholds(probs, meta["thresholds"])
    # All 7 pose classes have a Gesture Recognizer counterpart (ILoveYou / None -> OTHER).
    onehot = lambda p: np.eye(len(POSE_CLASSES))[p]
    md = ["## Baseline: MediaPipe Gesture Recognizer vs pose_mlp (same HaGRID photos)", "",
          f"Photos: {len(rows)}, hand found by our landmarker: {has.mean() * 100:.1f}%. "
          "Images where no hand is found count as OTHER for both.", ""]
    md.append(markdown(report(y, onehot(mp_pred), POSE_CLASSES), POSE_CLASSES, "MediaPipe Gesture Recognizer"))
    md.append(markdown(report(y, onehot(ours), POSE_CLASSES), POSE_CLASSES, "pose_mlp (quantized TFLite, thresholds)"))
    (OUT / "report.md").write_text("\n".join(md), encoding="utf-8")
    print("\n".join(md))


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("collect")
    c.add_argument("--per-class", type=int, default=20)
    c.add_argument("--workers", type=int, default=6)
    sub.add_parser("evaluate")
    args = ap.parse_args()
    if args.cmd == "collect":
        collect(args.per_class, args.workers)
    else:
        evaluate()


if __name__ == "__main__":
    main()
