"""Landmark normalization shared with the app (Kotlin twin: gestures/.../ml/LandmarkNormalizer.kt).

Version "norm-v1". Any change here must bump NORM_VERSION and be mirrored in Kotlin; the parity
test vectors (export_test_vectors) catch divergence.

Input: 21 landmarks (x, y) normalized to the image (0..1), plus the image aspect ratio
(width / height) of the frame the landmarks were computed on.

Steps:
1. Isotropic coordinates: X = x * aspect, Y = y (so that a unit is the same length on both axes).
2. Origin at the wrist (landmark 0).
3. Scale: divide by the wrist -> middle MCP distance (landmark 0 -> 9), the "hand size".
4. Chirality: hands are mirrored to one canonical chirality, so left/right hands and mirrored
   cameras look the same. The sign of the 2D cross product (index MCP - wrist) x (pinky MCP - wrist)
   decides; negative -> flip X. Rotation is NOT normalized (thumb up vs thumb down must differ).
Output: 42 floats (x0, y0, x1, y1, ...).
"""

from __future__ import annotations

import numpy as np

NORM_VERSION = "norm-v1"
NUM_LANDMARKS = 21
FEATURES = NUM_LANDMARKS * 2

WRIST, INDEX_MCP, MIDDLE_MCP, PINKY_MCP = 0, 5, 9, 17
EPS = 1e-6


def to_isotropic(xy: np.ndarray, aspect: float | np.ndarray) -> np.ndarray:
    """xy: (..., 21, 2) normalized image coords -> isotropic (x scaled by width / height)."""
    out = np.array(xy, dtype=np.float32, copy=True)
    out[..., 0] *= np.asarray(aspect, dtype=np.float32)[..., None] if np.ndim(aspect) else aspect
    return out


def chirality(iso: np.ndarray) -> np.ndarray:
    """Sign of the palm triangle orientation: (..., 21, 2) -> (...) in {-1, +1}."""
    a = iso[..., INDEX_MCP, :] - iso[..., WRIST, :]
    b = iso[..., PINKY_MCP, :] - iso[..., WRIST, :]
    cross = a[..., 0] * b[..., 1] - a[..., 1] * b[..., 0]
    return np.where(cross < 0, -1.0, 1.0).astype(np.float32)


def normalize_shape(xy: np.ndarray, aspect: float | np.ndarray) -> np.ndarray:
    """(..., 21, 2) image-normalized landmarks -> (..., 42) normalized hand-shape features."""
    iso = to_isotropic(xy, aspect)
    centered = iso - iso[..., WRIST:WRIST + 1, :]
    size = np.linalg.norm(centered[..., MIDDLE_MCP, :], axis=-1)
    size = np.maximum(size, EPS)[..., None, None]
    shape = centered / size
    sign = chirality(iso)[..., None]
    shape[..., 0] *= sign
    return shape.reshape(*shape.shape[:-2], FEATURES).astype(np.float32)


def hand_size(xy: np.ndarray, aspect: float | np.ndarray) -> np.ndarray:
    iso = to_isotropic(xy, aspect)
    return np.linalg.norm(iso[..., MIDDLE_MCP, :] - iso[..., WRIST, :], axis=-1)
