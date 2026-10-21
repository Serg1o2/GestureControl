"""Downloads with resume (HTTP Range) and SHA-256 recording in ml/hashes.json."""

from __future__ import annotations

import argparse
import hashlib
import json
import urllib.request
from pathlib import Path

from .hagrid import ANNOTATIONS_URL

ROOT = Path(__file__).resolve().parents[1]
HASHES = ROOT / "hashes.json"


def download(url: str, dst: Path, chunk: int = 1 << 20) -> Path:
    dst.parent.mkdir(parents=True, exist_ok=True)
    have = dst.stat().st_size if dst.exists() else 0
    req = urllib.request.Request(url, headers={"Range": f"bytes={have}-"} if have else {})
    try:
        resp = urllib.request.urlopen(req, timeout=120)
    except urllib.error.HTTPError as e:
        if e.code == 416:  # already complete
            return dst
        raise
    with resp, dst.open("ab" if have and resp.status == 206 else "wb") as f:
        while block := resp.read(chunk):
            f.write(block)
    return dst


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while block := f.read(1 << 22):
            h.update(block)
    return h.hexdigest()


def record_hash(name: str, path: Path) -> None:
    hashes = json.loads(HASHES.read_text()) if HASHES.exists() else {}
    digest = sha256(path)
    if name in hashes and hashes[name]["sha256"] != digest:
        raise SystemExit(f"{name}: hash mismatch ({digest} != {hashes[name]['sha256']})")
    hashes[name] = {"sha256": digest, "size": path.stat().st_size}
    HASHES.write_text(json.dumps(hashes, indent=2) + "\n")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("what", choices=["hagrid"])
    args = ap.parse_args()
    if args.what == "hagrid":
        dst = ROOT / "data" / "raw" / "hagrid" / "annotations.zip"
        download(ANNOTATIONS_URL, dst)
        record_hash("hagrid/annotations.zip", dst)


if __name__ == "__main__":
    main()
