"""Landmark-level augmentations (numpy, applied per batch on normalized shape features)."""

from __future__ import annotations

import numpy as np

from .normalize import FEATURES, NUM_LANDMARKS


def augment_shapes(
    x: np.ndarray,
    rng: np.random.Generator,
    max_rotation_deg: float = 25.0,
    aspect_jitter: float = 0.3,
    noise_std: float = 0.02,
    scale_jitter: float = 0.08,
) -> np.ndarray:
    """x: (N, 42) normalized shapes -> augmented copy.

    - rotation around the wrist (kept moderate so thumb-up / thumb-down stay distinct);
    - anisotropic x stretch: covers unknown or wrong camera aspect ratios;
    - global scale jitter: imperfect hand-size estimate;
    - gaussian noise per landmark: detector jitter.
    Mirroring is not needed: chirality is canonicalized by the normalizer.
    """
    n = x.shape[0]
    pts = x.reshape(n, NUM_LANDMARKS, 2).copy()

    angle = np.deg2rad(rng.uniform(-max_rotation_deg, max_rotation_deg, size=n)).astype(np.float32)
    c, s = np.cos(angle), np.sin(angle)
    px, py = pts[..., 0].copy(), pts[..., 1].copy()
    pts[..., 0] = c[:, None] * px - s[:, None] * py
    pts[..., 1] = s[:, None] * px + c[:, None] * py

    stretch = np.exp(rng.uniform(-aspect_jitter, aspect_jitter, size=n)).astype(np.float32)
    pts[..., 0] *= stretch[:, None]

    scale = np.exp(rng.uniform(-scale_jitter, scale_jitter, size=n)).astype(np.float32)
    pts *= scale[:, None, None]

    pts += rng.normal(0.0, noise_std, size=pts.shape).astype(np.float32)
    pts[:, 0, :] = 0.0  # the wrist stays the origin
    return pts.reshape(n, FEATURES)


def time_warp(seq: np.ndarray, rng: np.random.Generator, low: float = 0.75, high: float = 1.33) -> np.ndarray:
    """Resample a (T, F) sequence to simulate faster/slower execution, keeping length T."""
    t = seq.shape[0]
    factor = rng.uniform(low, high)
    src = np.clip(np.arange(t) * factor - (factor - 1) * (t - 1) / 2, 0, t - 1)
    lo = np.floor(src).astype(int)
    hi = np.minimum(lo + 1, t - 1)
    w = (src - lo)[:, None]
    return (seq[lo] * (1 - w) + seq[hi] * w).astype(np.float32)
