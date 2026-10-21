"""Exports parity test vectors for the Kotlin side.

- gestures/src/test/resources/ml/norm_vectors.csv: aspect, 42 raw landmark values, 42 expected
  features (LandmarkNormalizer vs normalize.py, plain JVM unit test).
- app/src/androidTest/assets/pose_vectors.csv: 42 features, expected probabilities of the
  shipped quantized TFLite model (on-device instrumented test, also measures latency).
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np

from .normalize import normalize_shape

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
SEED = 7


def sample_landmarks(n: int, rng: np.random.Generator, source: Path | None) -> tuple[np.ndarray, np.ndarray]:
    """Real HaGRID hands if available (plus their mirror images), else random hand-like points."""
    if source is not None and source.exists():
        d = np.load(source)
        lm = d["lm"][rng.permutation(len(d["lm"]))[:n // 2]]
        mirrored = lm.copy()
        mirrored[..., 0] = 1 - mirrored[..., 0]
        lm = np.concatenate([lm, mirrored])
        # Varied aspect ratios exercise the isotropic scaling (values need not match the photos).
        aspect = rng.choice([0.5625, 0.75, 1.333, 1.778], len(lm))
    else:
        base = rng.uniform(0.3, 0.7, size=(n, 1, 2))
        lm = base + rng.normal(0, 0.08, size=(n, 21, 2))
        aspect = rng.choice([0.5625, 0.75, 1.333, 1.778], n)
    return lm.astype(np.float32), np.asarray(aspect, np.float32)


def export_norm(out: Path, n: int, source: Path | None) -> None:
    rng = np.random.default_rng(SEED)
    lm, aspect = sample_landmarks(n, rng, source)
    feats = normalize_shape(lm, aspect)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w") as f:
        f.write("# aspect, 42 raw (x0,y0..x20,y20), 42 expected norm-v1 features\n")
        for a, l, x in zip(aspect, lm.reshape(len(lm), -1), feats):
            f.write(",".join(repr(float(v)) for v in [a, *l, *x]) + "\n")
    print(f"{out}: {len(lm)} rows")


def export_model(out: Path, model: Path, features: Path, n: int) -> None:
    from .train_static import tflite_predict

    rng = np.random.default_rng(SEED)
    d = np.load(features)
    test = d["x"][d["split"] == 2]
    x = test[rng.permutation(len(test))[:n]]
    p = tflite_predict(model, x)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w") as f:
        f.write("# 42 features, expected probabilities of the quantized model\n")
        for xi, pi in zip(x, p):
            f.write(",".join(repr(float(v)) for v in [*xi, *pi]) + "\n")
    print(f"{out}: {len(x)} rows")


def export_weights(keras_path: Path, features: Path, n: int) -> None:
    """Dense weights for the JVM twin (DenseMlp) plus float-model vectors to compare against."""
    import tensorflow as tf

    from .classes import POSE_CLASSES

    model = tf.keras.models.load_model(keras_path)
    res = REPO / "gestures" / "src" / "test" / "resources" / "ml"
    res.mkdir(parents=True, exist_ok=True)
    with (res / "pose_mlp_weights.txt").open("w", newline="\n") as f:
        f.write("classes " + ",".join(POSE_CLASSES) + "\n")
        for layer in model.layers:
            if not isinstance(layer, tf.keras.layers.Dense):
                continue  # Input / Dropout do not change inference
            w, b = layer.get_weights()
            act = layer.get_config()["activation"]
            f.write(f"layer {w.shape[0]} {w.shape[1]} {act}\n")
            for row in w:
                f.write(",".join(repr(float(v)) for v in row) + "\n")
            f.write(",".join(repr(float(v)) for v in b) + "\n")
    rng = np.random.default_rng(SEED)
    d = np.load(features)
    test = d["x"][d["split"] == 2]
    x = test[rng.permutation(len(test))[:n]]
    p = model.predict(x, verbose=0)
    with (res / "pose_mlp_vectors.csv").open("w", newline="\n") as f:
        f.write("# 42 features, expected probabilities of the float Keras model\n")
        for xi, pi in zip(x, p):
            f.write(",".join(repr(float(v)) for v in [*xi, *pi]) + "\n")
    print(f"{res}: weights + {len(x)} vectors")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--what", choices=["norm", "model", "weights"], required=True)
    ap.add_argument("--keras", type=Path, default=ROOT / "artifacts" / "static" / "pose_mlp.keras")
    ap.add_argument("--n", type=int, default=200)
    ap.add_argument("--landmarks", type=Path, default=ROOT / "data" / "features" / "hagrid_sample_landmarks.npz")
    ap.add_argument("--model", type=Path, default=ROOT / "artifacts" / "static" / "pose_mlp.tflite")
    ap.add_argument("--features", type=Path, default=ROOT / "data" / "features" / "static_hagrid_ann.npz")
    args = ap.parse_args()
    if args.what == "weights":
        export_weights(args.keras, args.features, args.n)
    elif args.what == "norm":
        export_norm(REPO / "gestures" / "src" / "test" / "resources" / "ml" / "norm_vectors.csv", args.n, args.landmarks)
    else:
        export_model(REPO / "app" / "src" / "androidTest" / "assets" / "pose_vectors.csv", args.model, args.features, args.n)


if __name__ == "__main__":
    main()
