"""Low-light fine-tuning data for the pose model: teacher labels from clean frames, inputs from dark ones.

IPN Hand has no pose labels, but gc_ml.lowlight gives the same frame twice: clean and degraded.
The current pose model (the teacher) labels the clean frame when it is confident; the degraded
frame's landmarks get that label. Training on these pairs teaches the model to keep its answer
when low light makes MediaPipe's landmarks noisy (consistency / self-training).

    python -m gc_ml.dark_pairs build      # (.venv-train) -> data/features/static_ipn_dark.npz
    python -m gc_ml.dark_pairs evaluate --models OLD.keras NEW.keras   # agreement per condition, test split

Rows: x = normalized dark shape, y = teacher pose, split from the IPN person-independent split
(train/val/test), source = 3. Frames where the teacher is unsure are skipped, so the labels are
as good as the teacher on clean, well-lit frames.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np

from .classes import POSE_CLASSES
from .ipn import LANDMARKS
from .lowlight import OUT as LOWLIGHT_OUT
from .normalize import normalize_shape
from .replay_export import _splits

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "features" / "static_ipn_dark.npz"
TEACHER = ROOT / "artifacts" / "static" / "pose_mlp.keras"
TEACHER_META = ROOT / "artifacts" / "static" / "pose_mlp.json"
SOURCE_ID = 3
SPLIT_CODE = {"train": 0, "val": 1, "test": 2}


def build(conditions: list[str], stride: int, min_conf: float, teacher_dir: Path | None = None) -> dict:
    import tensorflow as tf

    model_path = (teacher_dir / "pose_mlp.keras") if teacher_dir else TEACHER
    meta_path = (teacher_dir / "pose_mlp.json") if teacher_dir else TEACHER_META
    teacher = tf.keras.models.load_model(model_path)
    thresholds = np.array(json.loads(meta_path.read_text())["thresholds"], np.float32)
    splits = _splits()
    xs, ys, sp, cs = [], [], [], []
    agree = {}  # condition -> (teacher-agreeing dark predictions, total), before fine-tuning
    for ci, cond in enumerate(conditions):
        hits = total = 0
        for f in sorted((LOWLIGHT_OUT / cond).glob("*.npz")):
            ref = LANDMARKS / f.name
            if not ref.exists() or f.stem not in splits:
                continue
            clean, dark = np.load(ref), np.load(f)
            aspect = float(clean["aspect"])
            n = min(len(clean["present"]), len(dark["present"]))
            idx = np.arange(0, n, stride)
            idx = idx[clean["present"][idx] & dark["present"][idx]]
            if len(idx) == 0:
                continue
            p_clean = teacher.predict(normalize_shape(clean["lm"][idx], aspect), batch_size=4096, verbose=0)
            best = p_clean.argmax(1)
            conf = p_clean.max(1)
            # Confident and above the class threshold the app uses (OTHER has threshold 0).
            keep = (conf >= min_conf) & (conf >= thresholds[best])
            x_dark = normalize_shape(dark["lm"][idx[keep]], aspect)
            y = best[keep]
            p_dark = teacher.predict(x_dark, batch_size=4096, verbose=0) if len(x_dark) else np.zeros((0, len(POSE_CLASSES)))
            hits += int((p_dark.argmax(1) == y).sum())
            total += int(len(y))
            xs.append(x_dark)
            ys.append(y)
            sp.append(np.full(len(y), SPLIT_CODE[splits[f.stem]], np.int8))
            cs.append(np.full(len(y), ci, np.int8))
        agree[cond] = (hits, total)
    x = np.concatenate(xs).astype(np.float32)
    y = np.concatenate(ys).astype(np.int64)
    split = np.concatenate(sp)
    np.savez_compressed(OUT, x=x, y=y, split=split, source=np.full(len(x), SOURCE_ID, np.int8),
                        cond=np.concatenate(cs), conditions=np.array(conditions))
    stats = {
        "rows": int(len(x)),
        "per_split": np.bincount(split, minlength=3).tolist(),
        "per_class": {c: int((y == i).sum()) for i, c in enumerate(POSE_CLASSES)},
        "teacher_agreement_on_dark_before": {c: (h / t if t else None) for c, (h, t) in agree.items()},
        "conditions": conditions,
        "stride": stride,
        "min_conf": min_conf,
    }
    OUT.with_suffix(".json").write_text(json.dumps(stats, indent=2))
    return stats


def evaluate(models: list[Path]) -> dict:
    """Teacher agreement on the test split, per condition, for each model (Keras or TFLite)."""
    import tensorflow as tf

    from .train_static import tflite_predict

    d = np.load(OUT)
    te = d["split"] == SPLIT_CODE["test"]
    out = {}
    for m in models:
        if m.suffix == ".tflite":
            probs = tflite_predict(m, d["x"][te])
        else:
            probs = tf.keras.models.load_model(m).predict(d["x"][te], batch_size=8192, verbose=0)
        pred = probs.argmax(1)
        row = {}
        for ci, cond in enumerate(d["conditions"]):
            mask = d["cond"][te] == ci
            if mask.any():
                row[str(cond)] = float((pred[mask] == d["y"][te][mask]).mean())
        row["all"] = float((pred == d["y"][te]).mean())
        out[str(m)] = row
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["build", "evaluate"])
    ap.add_argument("--models", nargs="+", type=Path)
    ap.add_argument("--teacher", type=Path, help="model folder of the teacher (default: artifacts/static)")
    ap.add_argument("--conditions", nargs="+", default=None, help="low-light condition folders (default: all)")
    ap.add_argument("--stride", type=int, default=3)
    ap.add_argument("--min-conf", type=float, default=0.9)
    args = ap.parse_args()
    if args.cmd == "evaluate":
        print(json.dumps(evaluate(args.models), indent=2))
        return
    conds = args.conditions or sorted(p.name for p in LOWLIGHT_OUT.iterdir() if p.is_dir())
    print(json.dumps(build(conds, args.stride, args.min_conf, args.teacher), indent=2))


if __name__ == "__main__":
    main()
