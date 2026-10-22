"""Export IPN landmark streams to CSV for the Kotlin gesture replay bench (`./gradlew :gestures:replayBench`).

One CSV per video in the LandmarkCsv format (t_ms, x0, y0, z0, ...; z = 0, a row with only t_ms =
no hand), user-view coordinates, with header comments the bench reads:

    # ipn: <video>
    # split: train | val | test        (person-independent split, as in ipn.build_windows)
    # aspect: <width / height>
    # segment: <start_ms>,<end_ms>,<IPN label>

    python -m gc_ml.replay_export                 # clean landmarks of every extracted video
    python -m gc_ml.replay_export --lowlight      # + every low-light condition (gc_ml.lowlight)

Output: data/replay/<condition>/<video>.csv (condition "clean" or a low-light condition name).
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np

from .ipn import LANDMARKS, RAW, read_list
from .lowlight import OUT as LOWLIGHT_OUT

ROOT = Path(__file__).resolve().parents[1]
REPLAY = ROOT / "data" / "replay"
SEED = 42


def _splits() -> dict[str, str]:
    train = sorted({r[0] for r in read_list(RAW / "annotations" / "trainlistall.txt")})
    test = {r[0] for r in read_list(RAW / "annotations" / "vallistall.txt")}
    # Same 15% hold-out of whole training videos as ipn.build_windows (seed 42).
    rng = np.random.default_rng(SEED)
    val = set(rng.permutation(train)[: max(1, len(train) * 15 // 100)])
    out = {v: ("val" if v in val else "train") for v in train}
    out.update({v: "test" for v in test})
    return out


def _segments() -> dict[str, list[tuple[int, int, str]]]:
    classes = {int(l.split()[0]): l.split()[1]
               for l in (RAW / "annotations" / "classIndAll.txt").read_text().split("\n") if l.strip()}
    segs: dict[str, list[tuple[int, int, str]]] = {}
    for name in ("trainlistall.txt", "vallistall.txt"):
        for video, cls, start, end in read_list(RAW / "annotations" / name):
            segs.setdefault(video, []).append((start, end, classes[cls]))
    return segs


def export_file(npz: Path, out: Path, split: str, segments: list[tuple[int, int, str]]) -> None:
    d = np.load(npz)
    lm, present, fps, aspect = d["lm"], d["present"], float(d["fps"]), float(d["aspect"])
    ms = lambda frame: int(round(frame * 1000 / fps))  # noqa: E731
    lines = [f"# ipn: {npz.stem}", f"# split: {split}", f"# aspect: {aspect:.6f}"]
    # IPN frame numbers are 1-based and inclusive.
    lines += [f"# segment: {ms(s - 1)},{ms(e)},{label}" for s, e, label in sorted(segments)]
    lines.append("t_ms," + ",".join(f"x{i},y{i},z{i}" for i in range(21)))
    for i in range(len(lm)):
        if present[i]:
            lines.append(f"{ms(i)}," + ",".join(f"{x:.5f},{y:.5f},0" for x, y in lm[i]))
        else:
            lines.append(str(ms(i)))
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--lowlight", action="store_true")
    args = ap.parse_args()
    splits, segs = _splits(), _segments()
    sources = [("clean", LANDMARKS)]
    if args.lowlight and LOWLIGHT_OUT.exists():
        sources += [(p.name, p) for p in sorted(LOWLIGHT_OUT.iterdir()) if p.is_dir()]
    for cond, src in sources:
        n = 0
        for npz in sorted(src.glob("*.npz")):
            if npz.stem not in segs:
                continue
            out = REPLAY / cond / f"{npz.stem}.csv"
            if out.exists() and out.stat().st_mtime >= npz.stat().st_mtime:
                continue
            export_file(npz, out, splits.get(npz.stem, "train"), segs[npz.stem])
            n += 1
        print(f"{cond}: {n} files exported")


if __name__ == "__main__":
    main()
