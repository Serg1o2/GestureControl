"""My recordings from the phone's training logger -> features for fine-tuning and evaluation.

Files: data/raw/mine/*.csv written by LandmarkRecorder (`# label:`, `# aspect:` headers,
LandmarkCsv rows). Landmarks are already in user view (mirrored), like the app sees them.
Split by recording session (file), never by frame: 70% train, 15% val, 15% test, stable hash.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np

from .classes import POSE_INDEX
from .normalize import normalize_shape

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "data" / "raw" / "mine"
OUT = ROOT / "data" / "features" / "static_mine.npz"


def parse(path: Path) -> tuple[str | None, float, np.ndarray]:
    label, aspect, rows = None, 0.75, []
    for line in path.read_text().splitlines():
        if line.startswith("# label:"):
            label = line.split(":", 1)[1].strip() or None
        elif line.startswith("# aspect:"):
            aspect = float(line.split(":", 1)[1])
        elif line and not line.startswith("#") and not line.startswith("t_ms"):
            v = line.split(",")
            if len(v) == 1 + 21 * 3:
                rows.append(np.array(v[1:], np.float32).reshape(21, 3)[:, :2])
    return label, aspect, np.asarray(rows, np.float32).reshape(-1, 21, 2)


def session_split(name: str) -> int:
    h = int(hashlib.md5(name.encode()).hexdigest(), 16) % 100
    return 2 if h < 15 else 1 if h < 30 else 0


def build_static() -> dict:
    xs, ys, splits = [], [], []
    skipped = []
    for f in sorted(RAW.rglob("*.csv")):
        label, aspect, lm = parse(f)
        # Static poses only; NO_GESTURE recordings are the OTHER (background) class.
        pose = "OTHER" if label == "NO_GESTURE" else label
        if pose not in POSE_INDEX or len(lm) == 0:
            skipped.append(f.name)
            continue
        xs.append(normalize_shape(lm, aspect))
        ys.append(np.full(len(lm), POSE_INDEX[pose]))
        splits.append(np.full(len(lm), session_split(f.name), np.int8))
    if not xs:
        raise SystemExit(f"No labeled static recordings in {RAW}")
    x = np.concatenate(xs)
    np.savez_compressed(OUT, x=x, y=np.concatenate(ys), split=np.concatenate(splits), source=np.full(len(x), 2, np.int8))
    stats = {"frames": int(len(x)), "skipped_files": skipped}
    OUT.with_suffix(".json").write_text(json.dumps(stats, indent=2))
    return stats


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["features"])
    ap.parse_args()
    print(build_static())


if __name__ == "__main__":
    main()
