"""Dynamic gesture 1D-CNN on WINDOW-frame sequences (IPN Hand), evaluation, TFLite export.

Input: data/features/dynamic_ipn.npz with x (N, WINDOW, 45), y (N,), split (N,).
"""

from __future__ import annotations

import argparse
import sys
import json
import time
from pathlib import Path

import numpy as np

from .augment import time_warp
from .classes import DYNAMIC_CLASSES
from .dynamic_features import FRAME_FEATURES, WINDOW
from .metrics import markdown, report
from .normalize import NORM_VERSION

SEED = 42
ROOT = Path(__file__).resolve().parents[1]
LEFT, RIGHT = DYNAMIC_CLASSES.index("SWIPE_LEFT"), DYNAMIC_CLASSES.index("SWIPE_RIGHT")


def build_model():
    import tensorflow as tf

    inp = tf.keras.Input(shape=(WINDOW, FRAME_FEATURES), name="window")
    h = tf.keras.layers.Conv1D(64, 5, padding="same", activation="relu")(inp)
    h = tf.keras.layers.Conv1D(64, 5, padding="same", dilation_rate=2, activation="relu")(h)
    h = tf.keras.layers.Conv1D(64, 3, padding="same", dilation_rate=4, activation="relu")(h)
    h = tf.keras.layers.GlobalAveragePooling1D()(h)
    h = tf.keras.layers.Dropout(0.3)(h)
    h = tf.keras.layers.Dense(64, activation="relu")(h)
    out = tf.keras.layers.Dense(len(DYNAMIC_CLASSES), activation="softmax", name="probs")(h)
    return tf.keras.Model(inp, out)


def augment(x: np.ndarray, y: np.ndarray, rng: np.random.Generator) -> tuple[np.ndarray, np.ndarray]:
    x = x.copy()
    y = y.copy()
    for i in range(len(x)):
        if rng.random() < 0.5:
            x[i] = time_warp(x[i], rng)
        # Mirror: the shape is chirality-canonical (unchanged), only the x motion flips,
        # which swaps left/right swipes.
        if rng.random() < 0.5:
            x[i, :, 42] *= -1
            if y[i] == LEFT:
                y[i] = RIGHT
            elif y[i] == RIGHT:
                y[i] = LEFT
        x[i, :, :44] += rng.normal(0, 0.02, size=(WINDOW, 44)).astype(np.float32)
        # Random frame dropout (hand briefly lost): copy the previous frame, present = 0.
        drop = rng.random(WINDOW) < 0.05
        drop[-1] = False
        for t in np.where(drop)[0]:
            if t > 0:
                x[i, t, :44] = x[i, t - 1, :44]
                x[i, t, 44] = 0
    return x, y


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")  # reports contain non-ASCII symbols; Windows consoles default to cp1251
    import tensorflow as tf

    ap = argparse.ArgumentParser()
    ap.add_argument("--features", type=Path, default=ROOT / "data" / "features" / "dynamic_ipn.npz")
    ap.add_argument("--out", type=Path, default=ROOT / "artifacts" / "dynamic")
    ap.add_argument("--epochs", type=int, default=30)
    args = ap.parse_args()

    tf.keras.utils.set_random_seed(SEED)
    rng = np.random.default_rng(SEED)
    d = np.load(args.features)
    x, y, split = d["x"], d["y"], d["split"]
    tr, va, te = split == 0, split == 1, split == 2
    n = len(DYNAMIC_CLASSES)
    counts = np.bincount(y[tr], minlength=n)
    weights = (counts.sum() / np.maximum(counts, 1) / n).astype(np.float32)
    weights = np.clip(weights / weights[counts > 0].min(), 1, 20)
    print("train", counts.tolist(), "val", np.bincount(y[va], minlength=n).tolist(), "test", np.bincount(y[te], minlength=n).tolist())

    model = build_model()
    model.compile(tf.keras.optimizers.Adam(1e-3), "sparse_categorical_crossentropy", metrics=["accuracy"])
    best, best_w = -1.0, None
    for epoch in range(args.epochs):
        t0 = time.time()
        idx = rng.permutation(np.where(tr)[0])
        losses = []
        for s in range(0, len(idx), 256):
            b = idx[s:s + 256]
            xb, yb = augment(x[b], y[b], rng)
            losses.append(model.train_on_batch(xb, yb, sample_weight=weights[yb])[0])
        pv = model.predict(x[va], verbose=0).argmax(1)
        # Balanced accuracy: IPN is dominated by NO_GESTURE windows.
        bal = float(np.mean([(pv[y[va] == c] == c).mean() for c in range(n) if (y[va] == c).any()]))
        print(f"epoch {epoch + 1} loss {np.mean(losses):.4f} val_balanced_acc {bal:.4f} ({time.time() - t0:.0f}s)")
        if bal > best:
            best, best_w = bal, model.get_weights()
    model.set_weights(best_w)
    args.out.mkdir(parents=True, exist_ok=True)
    model.save(args.out / "dynamic_cnn.keras")

    probs = model.predict(x[te], verbose=0)
    rep = report(y[te], probs, DYNAMIC_CLASSES)
    rep_t = report(y[te], probs, DYNAMIC_CLASSES, threshold=0.8)
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    (args.out / "dynamic_cnn.tflite").write_bytes(conv.convert())
    meta = {"name": "dynamic_cnn", "version": time.strftime("%Y%m%d-%H%M"), "norm": NORM_VERSION, "window": WINDOW,
            "frame_features": FRAME_FEATURES, "classes": DYNAMIC_CLASSES, "best_val_balanced_acc": best,
            "test_accuracy": rep["accuracy"]}
    (args.out / "dynamic_cnn.json").write_text(json.dumps(meta, indent=2))
    md = ["## Dynamic model (IPN Hand, test = official validation subjects)", "",
          markdown(rep, DYNAMIC_CLASSES, "argmax"), markdown(rep_t, DYNAMIC_CLASSES, "confidence ≥ 0.8")]
    (args.out / "report.md").write_text("\n".join(md), encoding="utf-8")
    print("\n".join(md))


if __name__ == "__main__":
    main()
