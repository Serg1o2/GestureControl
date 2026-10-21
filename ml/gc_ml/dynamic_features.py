"""Per-window features for the dynamic model (Kotlin twin: gestures/.../ml/DynamicFeatures.kt).

A window is WINDOW consecutive frames at ~30 fps. Per frame (45 values):
- 42: normalized hand shape (normalize.normalize_shape), missing frames filled from neighbours;
- 2: palm-center position relative to the palm center of the window's last frame, in isotropic
  units divided by the mean hand size of the window (so motion is scale invariant). Direction is
  kept (user view: +x = user's right, +y = down);
- 1: hand present flag.
"""

from __future__ import annotations

import numpy as np

from .normalize import hand_size, normalize_shape, to_isotropic

WINDOW = 24
FRAME_FEATURES = 45
PALM = [0, 5, 9, 13, 17]
MIN_PRESENT = 0.6


def _fill(values: np.ndarray, present: np.ndarray) -> np.ndarray:
    """Forward fill, then backward fill missing frames (axis 0)."""
    out = values.copy()
    idx = np.where(present)[0]
    if len(idx) == 0:
        return out
    last = idx[0]
    for i in range(len(out)):
        if present[i]:
            last = i
        else:
            out[i] = out[last]
    return out


def window_features(lm: np.ndarray, present: np.ndarray, aspect: float) -> np.ndarray | None:
    """lm (T, 21, 2) image-normalized (user view), present (T,) -> (T, 45) or None if too sparse."""
    if len(lm) != WINDOW or present.mean() < MIN_PRESENT or not present[-1]:
        return None
    filled = _fill(lm, present)
    shape = normalize_shape(filled, aspect)
    iso = to_isotropic(filled, aspect)
    palm = iso[:, PALM, :].mean(1)
    size = float(np.mean(hand_size(filled, aspect)[present]))
    motion = (palm - palm[-1]) / max(size, 1e-6)
    feats = np.concatenate([shape, motion, present[:, None].astype(np.float32)], axis=1)
    return feats.astype(np.float32)
