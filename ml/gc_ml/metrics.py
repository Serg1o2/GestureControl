"""Evaluation helpers producing markdown for docs/accuracy.md."""

from __future__ import annotations

import numpy as np


def confusion(y_true: np.ndarray, y_pred: np.ndarray, n: int) -> np.ndarray:
    m = np.zeros((n, n), dtype=np.int64)
    np.add.at(m, (y_true, y_pred), 1)
    return m


def apply_thresholds(probs: np.ndarray, thresholds: list[float], other_index: int = 0) -> np.ndarray:
    """Like the app: the top class counts only if it reaches its own threshold, otherwise OTHER."""
    top = probs.argmax(1)
    ok = probs[np.arange(len(probs)), top] >= np.asarray(thresholds)[top]
    return np.where(ok, top, other_index)


def report(y_true: np.ndarray, probs: np.ndarray, classes: list[str], other_index: int = 0,
           threshold: float = 0.0, pred: np.ndarray | None = None) -> dict:
    """Accuracy, per-class recall/precision, confusion matrix, background false-positive rate.

    The background false-positive rate is the share of OTHER samples that would trigger a gesture,
    i.e. are predicted as a non-OTHER class with confidence >= threshold.
    """
    pred = probs.argmax(1) if pred is None else pred
    conf = probs.max(1)
    n = len(classes)
    cm = confusion(y_true, pred, n)
    recall = np.diag(cm) / np.maximum(cm.sum(1), 1)
    precision = np.diag(cm) / np.maximum(cm.sum(0), 1)
    bg = y_true == other_index
    fp = (pred[bg] != other_index) & (conf[bg] >= threshold)
    # Gesture-only accuracy: samples of real gestures classified correctly.
    gest = ~bg
    return {
        "n": int(len(y_true)),
        "accuracy": float((pred == y_true).mean()),
        "gesture_accuracy": float((pred[gest] == y_true[gest]).mean()) if gest.any() else float("nan"),
        "background_fp_rate": float(fp.mean()) if bg.any() else float("nan"),
        "recall": {c: float(r) for c, r in zip(classes, recall)},
        "precision": {c: float(p) for c, p in zip(classes, precision)},
        "confusion": cm.tolist(),
        "threshold": threshold,
    }


def markdown(rep: dict, classes: list[str], title: str) -> str:
    lines = [f"### {title}", ""]
    lines.append(f"- samples: {rep['n']}")
    lines.append(f"- accuracy: **{rep['accuracy'] * 100:.2f}%**")
    lines.append(f"- accuracy on gesture classes (without OTHER): **{rep['gesture_accuracy'] * 100:.2f}%**")
    cond = f", confidence ≥ {rep['threshold']:.2f}" if rep["threshold"] > 0 else ""
    lines.append(
        f"- background false positives per frame (OTHER predicted as a gesture{cond}): "
        f"**{rep['background_fp_rate'] * 100:.2f}%**"
    )
    lines.append("")
    lines.append("| class | recall | precision |")
    lines.append("|---|---|---|")
    for c in classes:
        lines.append(f"| {c} | {rep['recall'][c] * 100:.1f}% | {rep['precision'][c] * 100:.1f}% |")
    lines.append("")
    lines.append("Confusion matrix (rows = true, columns = predicted):")
    lines.append("")
    lines.append("| | " + " | ".join(classes) + " |")
    lines.append("|---" * (len(classes) + 1) + "|")
    for c, row in zip(classes, rep["confusion"]):
        lines.append(f"| **{c}** | " + " | ".join(str(v) for v in row) + " |")
    lines.append("")
    return "\n".join(lines)
