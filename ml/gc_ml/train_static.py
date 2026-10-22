"""Static pose MLP: train, evaluate, export to TFLite with metadata and parity test vectors.

Input: data/features/static_*.npz with
  x (N, 42) normalized shapes, y (N,) POSE_CLASSES index, split (N,) 0=train 1=val 2=test,
  source (N,) dataset id (0=hagrid annotations, 1=hagrid re-extracted, 2=mine, 3=IPN low light).
"""

from __future__ import annotations

import argparse
import sys
import json
import time
from pathlib import Path

import numpy as np

from .augment import augment_shapes
from .classes import POSE_CLASSES
from .metrics import apply_thresholds, markdown, report
from .normalize import FEATURES, NORM_VERSION

SEED = 42
ROOT = Path(__file__).resolve().parents[1]


def load(paths: list[Path]) -> dict[str, np.ndarray]:
    parts = [np.load(p) for p in paths]
    return {k: np.concatenate([p[k] for p in parts]) for k in ("x", "y", "split", "source")}


def build_model(n_classes: int):
    import tensorflow as tf

    inp = tf.keras.Input(shape=(FEATURES,), name="shape")
    h = tf.keras.layers.Dense(128, activation="relu")(inp)
    h = tf.keras.layers.Dropout(0.2)(h)
    h = tf.keras.layers.Dense(64, activation="relu")(h)
    h = tf.keras.layers.Dropout(0.1)(h)
    out = tf.keras.layers.Dense(n_classes, activation="softmax", name="probs")(h)
    return tf.keras.Model(inp, out)


def class_weights(y: np.ndarray, n: int, cap: float = 10.0) -> dict[int, float]:
    counts = np.bincount(y, minlength=n).astype(np.float64)
    w = counts.sum() / np.maximum(counts, 1) / n
    w = np.clip(w / w[counts > 0].min(), 1.0, cap)
    return {i: float(w[i]) for i in range(n)}


def batches(x, y, batch, rng, augment):
    idx = rng.permutation(len(x))
    for s in range(0, len(idx), batch):
        b = idx[s:s + batch]
        xb = augment_shapes(x[b], rng) if augment else x[b]
        yield xb, y[b]


def train(data: dict[str, np.ndarray], out_dir: Path, epochs: int, batch: int = 1024, oversample: int = 5,
          oversample_dark: int = 1) -> dict:
    import tensorflow as tf

    tf.keras.utils.set_random_seed(SEED)
    rng = np.random.default_rng(SEED)
    n = len(POSE_CLASSES)
    tr = data["split"] == 0
    va = data["split"] == 1
    # Landmarks from our MediaPipe version (sources 1, 2) match the app best: repeat them `oversample` times.
    # The low-light rows (source 3) are correlated video frames: their own repeat factor.
    tr_idx = np.where(tr)[0]
    own = tr_idx[np.isin(data["source"][tr_idx], [1, 2])]
    dark = tr_idx[data["source"][tr_idx] == 3]
    tr_idx = np.concatenate([tr_idx] + [own] * (oversample - 1) + [dark] * (oversample_dark - 1))
    xtr, ytr = data["x"][tr_idx], data["y"][tr_idx]
    xva, yva = data["x"][va], data["y"][va]
    own_va = data["source"][va] > 0
    print(f"train {len(xtr)}  val {len(xva)}  classes {np.bincount(ytr, minlength=n).tolist()}")

    model = build_model(n)
    steps = int(np.ceil(len(xtr) / batch)) * epochs
    lr = tf.keras.optimizers.schedules.CosineDecay(1e-3, steps)
    model.compile(tf.keras.optimizers.Adam(lr), "sparse_categorical_crossentropy", metrics=["accuracy"])
    cw = class_weights(ytr, n)
    sw_table = np.array([cw[i] for i in range(n)], dtype=np.float32)

    best, best_weights, history = -1.0, None, []
    for epoch in range(epochs):
        t0 = time.time()
        losses = []
        for xb, yb in batches(xtr, ytr, batch, rng, augment=True):
            losses.append(model.train_on_batch(xb, yb, sample_weight=sw_table[yb])[0])
        hits = model.predict(xva, batch_size=8192, verbose=0).argmax(1) == yva
        # Selection score: mean of all-val accuracy and our-MediaPipe val accuracy (closest to the app).
        val_acc = float(hits.mean()) if not own_va.any() else float((hits.mean() + hits[own_va].mean()) / 2)
        history.append({"epoch": epoch + 1, "loss": float(np.mean(losses)), "val_acc": val_acc})
        print(f"epoch {epoch + 1}/{epochs} loss {np.mean(losses):.4f} val_acc {val_acc:.4f} ({time.time() - t0:.0f}s)")
        if val_acc > best:
            best, best_weights = val_acc, model.get_weights()
    model.set_weights(best_weights)
    out_dir.mkdir(parents=True, exist_ok=True)
    model.save(out_dir / "pose_mlp.keras")
    return {"history": history, "best_val_acc": best, "class_weights": cw}


def choose_thresholds(probs: np.ndarray, y: np.ndarray, target_fp: float = 0.01) -> list[float]:
    """Per-class confidence threshold: lowest value keeping OTHER→class false positives below target."""
    other = y == 0
    thresholds = [0.0]
    for c in range(1, probs.shape[1]):
        p = probs[other, c][probs[other].argmax(1) == c]
        n_other = max(int(other.sum()), 1)
        t = 0.5
        for cand in np.linspace(0.5, 0.99, 50):
            if (p >= cand).sum() / n_other <= target_fp / (probs.shape[1] - 1):
                t = float(cand)
                break
            t = float(cand)
        thresholds.append(round(t, 3))
    return thresholds


def export_tflite(model_path: Path, out_dir: Path, x_cal: np.ndarray) -> tuple[Path, Path]:
    import tensorflow as tf

    model = tf.keras.models.load_model(model_path)
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    float_bytes = conv.convert()
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]  # dynamic-range int8 weights
    quant_bytes = conv.convert()
    f = out_dir / "pose_mlp_float.tflite"
    q = out_dir / "pose_mlp.tflite"
    f.write_bytes(float_bytes)
    q.write_bytes(quant_bytes)
    return f, q


def tflite_predict(path: Path, x: np.ndarray) -> np.ndarray:
    import tensorflow as tf

    interp = tf.lite.Interpreter(model_path=str(path))
    interp.allocate_tensors()
    inp = interp.get_input_details()[0]
    out = interp.get_output_details()[0]
    res = np.empty((len(x), len(POSE_CLASSES)), dtype=np.float32)
    for i in range(len(x)):
        interp.set_tensor(inp["index"], x[i:i + 1].astype(np.float32))
        interp.invoke()
        res[i] = interp.get_tensor(out["index"])[0]
    return res


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")  # reports contain non-ASCII symbols; Windows consoles default to cp1251
    ap = argparse.ArgumentParser()
    ap.add_argument("--features", nargs="+", type=Path, required=True)
    ap.add_argument("--out", type=Path, default=ROOT / "artifacts" / "static")
    ap.add_argument("--epochs", type=int, default=25)
    ap.add_argument("--oversample", type=int, default=5, help="repeat factor for our-MediaPipe and own samples")
    ap.add_argument("--oversample-dark", type=int, default=1, help="repeat factor for the low-light rows (source 3)")
    ap.add_argument("--report", type=Path, default=ROOT / "artifacts" / "static" / "report.md")
    args = ap.parse_args()

    data = load(args.features)
    info = train(data, args.out, args.epochs, oversample=args.oversample, oversample_dark=args.oversample_dark)

    import tensorflow as tf

    model = tf.keras.models.load_model(args.out / "pose_mlp.keras")
    va = data["split"] == 1
    te = data["split"] == 2
    thresholds = choose_thresholds(model.predict(data["x"][va], batch_size=8192, verbose=0), data["y"][va])
    probs_te = model.predict(data["x"][te], batch_size=8192, verbose=0)

    md = ["## Static pose model (Keras, test split by person)", ""]
    thresholded: dict = {}
    for src, name in [(None, "all test"), (0, "HaGRID annotations"), (1, "HaGRID re-extracted (our MediaPipe)"), (2, "my recordings"),
                      (3, "IPN low light (teacher labels from clean frames)")]:
        mask = np.ones(te.sum(), bool) if src is None else data["source"][te] == src
        if mask.sum() == 0:
            continue
        rep = report(data["y"][te][mask], probs_te[mask], POSE_CLASSES, threshold=0.0)
        md.append(markdown(rep, POSE_CLASSES, f"{name}, argmax"))
        rep_t = report(data["y"][te][mask], probs_te[mask], POSE_CLASSES, pred=apply_thresholds(probs_te[mask], thresholds))
        md.append(markdown(rep_t, POSE_CLASSES, f"{name}, with per-class thresholds {thresholds}"))
        thresholded[src] = rep_t

    fpath, qpath = export_tflite(args.out / "pose_mlp.keras", args.out, data["x"][data["split"] == 0][:1000])
    sample = data["x"][te][:2000]
    keras_p = model.predict(sample, verbose=0)
    q_p = tflite_predict(qpath, sample)
    f_p = tflite_predict(fpath, sample)
    parity = {
        "float_max_abs_diff": float(np.abs(keras_p - f_p).max()),
        "quant_max_abs_diff": float(np.abs(keras_p - q_p).max()),
        "quant_argmax_agreement": float((keras_p.argmax(1) == q_p.argmax(1)).mean()),
    }
    md.append("### Keras vs TFLite parity (2000 test samples)\n")
    md.append(f"- float TFLite max |Δp|: {parity['float_max_abs_diff']:.2e}")
    md.append(f"- quantized TFLite max |Δp|: {parity['quant_max_abs_diff']:.4f}, argmax agreement {parity['quant_argmax_agreement'] * 100:.2f}%")
    md.append(f"- model size: float {fpath.stat().st_size} B, quantized {qpath.stat().st_size} B\n")
    q_rep = report(data["y"][te], tflite_predict(qpath, data["x"][te]), POSE_CLASSES)
    md.append(markdown(q_rep, POSE_CLASSES, "quantized TFLite, all test, argmax"))

    meta = {
        "name": "pose_mlp",
        "version": time.strftime("%Y%m%d-%H%M"),
        "norm": NORM_VERSION,
        "input": FEATURES,
        "classes": POSE_CLASSES,
        "thresholds": thresholds,
        "test_accuracy_quant": q_rep["accuracy"],
        "train_info": {"best_val_acc": info["best_val_acc"], "epochs": args.epochs, "seed": SEED},
    }
    (args.out / "pose_mlp.json").write_text(json.dumps(meta, indent=2))
    goals = []
    for src, label in [(0, "HaGRID test (annotation landmarks)"), (1, "HaGRID test (our MediaPipe, true aspect)")]:
        r = thresholded.get(src)
        if r:
            goals.append({"goal": f"Static poses, accuracy ≥ 95%: {label}", "value": f"{r['accuracy'] * 100:.2f}%", "status": "✅" if r["accuracy"] >= 0.95 else "❌"})
            goals.append({"goal": f"Background false positives ≤ 2% per frame: {label}", "value": f"{r['background_fp_rate'] * 100:.2f}%", "status": "✅" if r["background_fp_rate"] <= 0.02 else "❌"})
    goals.append({"goal": "My phone recordings ≥ 90% after fine-tuning", "value": "no recordings yet", "status": "⏳"})
    goals.append({"goal": "Inference ≤ 5 ms on the phone", "value": f"model {qpath.stat().st_size} B; measured by connectedDebugAndroidTest", "status": "⏳"})
    (args.out / "summary.json").write_text(json.dumps({"goals": goals}, ensure_ascii=False, indent=2), encoding="utf-8")
    args.report.write_text("\n".join(md), encoding="utf-8")
    print("\n".join(md))
    print(json.dumps(parity))


if __name__ == "__main__":
    main()
