"""One command from raw data to .tflite in the app assets.

    python run_all.py              # full run (skips steps whose outputs exist)
    python run_all.py --pilot      # small subset to check the pipeline end to end
    python run_all.py --force STEP # redo one step

Two virtual environments are used because MediaPipe and TensorFlow pin different protobuf
versions: .venv-extract (MediaPipe, data) and .venv-train (TensorFlow). See README.md.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

ML = Path(__file__).resolve().parent
REPO = ML.parent
PY_EXTRACT = ML / ".venv-extract" / "Scripts" / "python.exe"
PY_TRAIN = ML / ".venv-train" / "Scripts" / "python.exe"
FEAT = ML / "data" / "features"
ASSETS = REPO / "app" / "src" / "main" / "assets" / "models"

# HaGRIDv2 photos: mostly 3:4 portrait (11/15 in a probe; others 9:16 or landscape). The annotation
# landmarks have no image size, so 0.75 is assumed; the x-stretch augmentation (±30%) covers the rest.
HAGRID_ASPECT = "0.75"


def run(py: Path, *args: str) -> None:
    cmd = [str(py), "-m", *args]
    print("\n>>>", " ".join(cmd), flush=True)
    subprocess.run(cmd, cwd=ML, check=True, env={**os.environ, "PYTHONIOENCODING": "utf-8"})


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pilot", action="store_true")
    ap.add_argument("--force", action="append", default=[])
    ap.add_argument("--skip-dynamic", action="store_true")
    args = ap.parse_args()
    pilot = args.pilot

    def needed(step: str, output: Path) -> bool:
        return step in args.force or not output.exists()

    # 1. Static data: HaGRID annotations (landmarks shipped with the dataset).
    if needed("hagrid-download", ML / "data" / "raw" / "hagrid" / "annotations.zip"):
        run(PY_EXTRACT, "gc_ml.download", "hagrid")
    if needed("hagrid-features", FEAT / "static_hagrid_ann.npz"):
        run(PY_EXTRACT, "gc_ml.hagrid", "annotations", "--aspect", HAGRID_ASPECT)

    # 2. Re-extraction of a photo sample with our MediaPipe (domain check + extra training data).
    if needed("hagrid-reextract", FEAT / "static_hagrid_mp.npz"):
        run(PY_EXTRACT, "gc_ml.hagrid", "reextract", "--per-class", "20" if pilot else "150", "--workers", "8")
        run(PY_EXTRACT, "gc_ml.hagrid", "reextract-features")

    # 3. Static model.
    static_inputs = [str(FEAT / "static_hagrid_ann.npz"), str(FEAT / "static_hagrid_mp.npz")]
    mine = FEAT / "static_mine.npz"
    if mine.exists():
        static_inputs.append(str(mine))
    if needed("train-static", ML / "artifacts" / "static" / "pose_mlp.tflite"):
        run(PY_TRAIN, "gc_ml.train_static", "--features", *static_inputs, "--epochs", "3" if pilot else "25")

    # 4. Dynamic data and model (IPN Hand).
    if not args.skip_dynamic:
        if needed("ipn-download", ML / "data" / "raw" / "ipn" / "videos_tgz"):
            run(PY_EXTRACT, "gc_ml.ipn", "download")
        run(PY_EXTRACT, "gc_ml.ipn", "unpack")
        run(PY_EXTRACT, "gc_ml.ipn", "extract", "--workers", "8", *(["--limit", "10"] if pilot else []))
        if needed("ipn-windows", FEAT / "dynamic_ipn.npz"):
            run(PY_EXTRACT, "gc_ml.ipn", "windows")
        if needed("train-dynamic", ML / "artifacts" / "dynamic" / "dynamic_cnn.tflite"):
            run(PY_TRAIN, "gc_ml.train_dynamic", "--epochs", "3" if pilot else "30")

    # 5. Parity vectors and app assets.
    run(PY_EXTRACT, "gc_ml.export_vectors", "--what", "norm")
    run(PY_TRAIN, "gc_ml.export_vectors", "--what", "model")
    run(PY_TRAIN, "gc_ml.export_vectors", "--what", "weights")
    run(PY_EXTRACT, "gc_ml.baseline", "collect", "--per-class", "5" if pilot else "20")
    run(PY_TRAIN, "gc_ml.baseline", "evaluate")
    run(PY_EXTRACT, "gc_ml.accuracy_doc")
    ASSETS.mkdir(parents=True, exist_ok=True)
    for f in ("pose_mlp.tflite", "pose_mlp.json"):
        shutil.copy2(ML / "artifacts" / "static" / f, ASSETS / f)
    print(f"\nDone. Model copied to {ASSETS}. Rebuild the app: ./gradlew assembleDebug")


if __name__ == "__main__":
    sys.exit(main())
