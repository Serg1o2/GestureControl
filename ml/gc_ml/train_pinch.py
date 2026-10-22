"""Pinch detector for the volume slider: a small binary model next to the pose model (not inside it).

Why separate: adding a PINCH class to the pose model cost fist-open hits on the replay bench (the
model drifted away from what works on webcam video), however it was trained. A detector used only by
the slider cannot change the other gestures.

Data (person-independent splits, as for the pose model):
- positives: HaGRID "ok" (thumb and index tips touching, other fingers extended) — annotation
  landmarks and photos re-extracted with our MediaPipe (hagrid_mp_* chunks, oversampled);
- negatives: every other HaGRID class except "grip" (a pinch with curled fingers: ambiguous, the
  slider's geometric check covers it), plus IPN Hand frames where people idle, point, throw or
  open a fist — clean and simulated low light — i.e. the webcam situations where the slider must not
  start. IPN click and zoom segments (G01/G02/G08-G11) are left out: they contain real pinches.

    python -m gc_ml.train_pinch        # (.venv-train) -> artifacts/pinch/, app assets, JVM weights
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

import numpy as np

from .augment import augment_shapes
from .classes import HAGRID_ALL
from .ipn import LANDMARKS, RAW, read_list
from .lowlight import OUT as LOWLIGHT_OUT
from .normalize import FEATURES, NORM_VERSION, normalize_shape
from .replay_export import _splits
from .train_static import build_model, tflite_predict

SEED = 42
ROOT = Path(__file__).resolve().parents[1]
FEAT = ROOT / "data" / "features"
OUT = ROOT / "artifacts" / "pinch"
CLASSES = ["OTHER", "PINCH"]
IPN_NEGATIVE = {"D0X", "B0A", "B0B", "G03", "G04", "G05", "G06", "G07"}
SPLIT_CODE = {"train": 0, "val": 1, "test": 2}
# Source ids: 0 HaGRID annotations, 1 HaGRID our MediaPipe, 2 IPN clean, 3 IPN low light.


def hagrid_rows() -> dict[str, np.ndarray]:
    ok, grip = HAGRID_ALL.index("ok"), HAGRID_ALL.index("grip")
    d = np.load(FEAT / "static_hagrid_ann.npz")
    keep = d["hagrid_label"] != grip
    x, lab, split = d["x"][keep], d["hagrid_label"][keep], d["split"][keep]
    xs, ys, ss, src = [x], [(lab == ok).astype(np.int64)], [split], [np.zeros(len(x), np.int8)]
    for chunk_dir in sorted(p for p in FEAT.glob("hagrid_mp*") if p.is_dir()):
        for f in sorted(chunk_dir.glob("chunk_*.npz")):
            c = np.load(f, allow_pickle=False)
            labels = np.array([str(v) for v in c["label"]])
            m = labels != "grip"
            if not m.any():
                continue
            xs.append(normalize_shape(c["lm"][m], c["aspect"][m]))
            ys.append((labels[m] == "ok").astype(np.int64))
            ss.append(c["split"][m])
            src.append(np.ones(int(m.sum()), np.int8))
    return {"x": np.concatenate(xs), "y": np.concatenate(ys), "split": np.concatenate(ss), "source": np.concatenate(src)}


def ipn_rows(stride: int) -> dict[str, np.ndarray]:
    classes = {int(l.split()[0]): l.split()[1] for l in (RAW / "annotations" / "classIndAll.txt").read_text().split("\n") if l.strip()}
    segs: dict[str, list[tuple[int, int]]] = {}
    for name in ("trainlistall.txt", "vallistall.txt"):
        for video, cls, s, e in read_list(RAW / "annotations" / name):
            if classes[cls] in IPN_NEGATIVE:
                segs.setdefault(video, []).append((s - 1, e))
    splits = _splits()
    sources = [(LANDMARKS, 2)] + [(LOWLIGHT_OUT / c, 3) for c in ("dim_none", "dark2x_none", "dark_none")]
    xs, ss, src = [], [], []
    for folder, sid in sources:
        for f in sorted(folder.glob("*.npz")):
            if f.stem not in segs or f.stem not in splits:
                continue
            d = np.load(f)
            idx = np.concatenate([np.arange(s, min(e, len(d["present"])), stride) for s, e in segs[f.stem]])
            idx = idx[d["present"][idx]]
            if len(idx) == 0:
                continue
            xs.append(normalize_shape(d["lm"][idx], float(d["aspect"])))
            ss.append(np.full(len(idx), SPLIT_CODE[splits[f.stem]], np.int8))
            src.append(np.full(len(idx), sid, np.int8))
    x = np.concatenate(xs)
    return {"x": x, "y": np.zeros(len(x), np.int64), "split": np.concatenate(ss), "source": np.concatenate(src)}


def choose_threshold(p_pinch: np.ndarray, y: np.ndarray, max_fp: float) -> float:
    """Lowest threshold whose false-positive rate on the validation negatives is at most [max_fp]."""
    neg = np.sort(p_pinch[y == 0])
    if len(neg) == 0:
        return 0.5
    t = float(neg[min(len(neg) - 1, int(np.ceil((1 - max_fp) * len(neg))))])
    return round(min(max(t, 0.5), 0.99), 3)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--epochs", type=int, default=15)
    ap.add_argument("--ipn-stride", type=int, default=4)
    ap.add_argument("--max-fp", type=float, default=0.003, help="false positives allowed on validation negatives")
    args = ap.parse_args()

    import tensorflow as tf

    tf.keras.utils.set_random_seed(SEED)
    rng = np.random.default_rng(SEED)
    h, i = hagrid_rows(), ipn_rows(args.ipn_stride)
    data = {k: np.concatenate([h[k], i[k]]) for k in h}
    ok = np.isfinite(data["x"]).all(1)
    data = {k: v[ok] for k, v in data.items()}
    tr, va, te = (data["split"] == s for s in (0, 1, 2))
    tr_idx = np.where(tr)[0]
    own = tr_idx[data["source"][tr_idx] == 1]
    tr_idx = np.concatenate([tr_idx] + [own] * 4)  # our-MediaPipe photos match the app best
    xtr, ytr = data["x"][tr_idx], data["y"][tr_idx]
    print(f"train {len(xtr)} (pinch {int(ytr.sum())})  val {int(va.sum())}  test {int(te.sum())}")

    model = build_model(len(CLASSES))
    batch = 1024
    steps = int(np.ceil(len(xtr) / batch)) * args.epochs
    model.compile(tf.keras.optimizers.Adam(tf.keras.optimizers.schedules.CosineDecay(1e-3, steps)),
                  "sparse_categorical_crossentropy", metrics=["accuracy"])
    weight = np.array([1.0, min(10.0, (ytr == 0).sum() / max((ytr == 1).sum(), 1))], np.float32)
    best, best_w = -1.0, None
    for epoch in range(args.epochs):
        t0 = time.time()
        idx = rng.permutation(len(xtr))
        for s in range(0, len(idx), batch):
            b = idx[s:s + batch]
            model.train_on_batch(augment_shapes(xtr[b], rng), ytr[b], sample_weight=weight[ytr[b]])
        p = model.predict(data["x"][va], batch_size=8192, verbose=0)[:, 1]
        yv = data["y"][va]
        # Selection: recall on pinches at the operating threshold of this epoch.
        t = choose_threshold(p, yv, args.max_fp)
        score = float((p[yv == 1] >= t).mean()) if (yv == 1).any() else 0.0
        print(f"epoch {epoch + 1}/{args.epochs} val recall@{t} {score:.4f} ({time.time() - t0:.0f}s)")
        if score > best:
            best, best_w = score, model.get_weights()
    model.set_weights(best_w)
    OUT.mkdir(parents=True, exist_ok=True)
    model.save(OUT / "pinch_mlp.keras")

    pv = model.predict(data["x"][va], batch_size=8192, verbose=0)[:, 1]
    threshold = choose_threshold(pv, data["y"][va], args.max_fp)
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    (OUT / "pinch_mlp.tflite").write_bytes(conv.convert())

    # Test report per source (person-independent test split), with the quantized model.
    pt = tflite_predict(OUT / "pinch_mlp.tflite", data["x"][te])[:, 1]
    yt, st = data["y"][te], data["source"][te]
    names = {0: "HaGRID annotations", 1: "HaGRID our MediaPipe", 2: "IPN clean (negatives)", 3: "IPN low light (negatives)"}
    report = {"threshold": threshold, "max_fp_val": args.max_fp, "test": {}}
    lines = ["# Pinch detector (pinch_mlp)", "", f"Threshold {threshold} (≤ {args.max_fp:.1%} false positives on validation negatives).", "",
             "| test source | pinches | recall | negatives | false positives |", "|---|---|---|---|---|"]
    for sid, name in names.items():
        m = st == sid
        if not m.any():
            continue
        pos, neg = m & (yt == 1), m & (yt == 0)
        rec = float((pt[pos] >= threshold).mean()) if pos.any() else float("nan")
        fp = float((pt[neg] >= threshold).mean()) if neg.any() else float("nan")
        report["test"][name] = {"pinches": int(pos.sum()), "recall": rec, "negatives": int(neg.sum()), "false_positives": fp}
        lines.append(f"| {name} | {int(pos.sum())} | {rec:.1%} | {int(neg.sum())} | {fp:.2%} |")
    (OUT / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))

    meta = {"name": "pinch_mlp", "version": time.strftime("%Y%m%d-%H%M"), "norm": NORM_VERSION, "input": FEATURES,
            "classes": CLASSES, "thresholds": [0.0, threshold], "test": report["test"]}
    (OUT / "pinch_mlp.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    # JVM twin weights (DenseMlp format) for the replay bench and tests.
    res = ROOT.parent / "gestures" / "src" / "test" / "resources" / "ml"
    with (res / "pinch_mlp_weights.txt").open("w", newline="\n") as f:
        f.write("classes " + ",".join(CLASSES) + "\n")
        for layer in model.layers:
            if isinstance(layer, tf.keras.layers.Dense):
                w, b = layer.get_weights()
                f.write(f"layer {w.shape[0]} {w.shape[1]} {layer.get_config()['activation']}\n")
                for row in w:
                    f.write(",".join(repr(float(v)) for v in row) + "\n")
                f.write(",".join(repr(float(v)) for v in b) + "\n")
    assets = ROOT.parent / "app" / "src" / "main" / "assets" / "models"
    for name in ("pinch_mlp.tflite", "pinch_mlp.json"):
        (assets / name).write_bytes((OUT / name).read_bytes())
    print(f"saved to {OUT}, app assets and {res / 'pinch_mlp_weights.txt'}")


if __name__ == "__main__":
    main()
