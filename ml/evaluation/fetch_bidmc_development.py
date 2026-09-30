"""Fetch only reserved BIDMC development Signals/Breaths outside Git."""

from __future__ import annotations

import argparse
import hashlib
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import urlopen

from bidmc_subject_split import SPLIT_VERSION, assign_subject_groups, read_subject_groups
from fetch_bidmc_metadata import BASE_URL


def development_names(split_manifest: dict[str, object]) -> list[str]:
    if split_manifest.get("split_version") != SPLIT_VERSION:
        raise ValueError("unknown BIDMC subject split")
    records = split_manifest["record_numbers"]["development"]
    if len(records) != len(set(records)) or not all(
        isinstance(record, str) and len(record) == 2 and record.isdigit()
        for record in records
    ):
        raise ValueError("invalid development recording numbers")
    return [f"bidmc_{record}_{kind}.csv" for record in records
            for kind in ("Signals", "Breaths")]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata-dir", required=True, type=Path)
    parser.add_argument("--split-manifest", type=Path,
                        default=Path(__file__).with_name("bidmc_split_manifest.json"))
    args = parser.parse_args()
    destination = args.metadata_dir.resolve()
    repository = Path(__file__).resolve().parents[2]
    if destination == repository or repository in destination.parents:
        parser.error("source data must remain outside the repository")
    manifest = json.loads(args.split_manifest.read_text(encoding="utf-8"))
    canonical = assign_subject_groups(read_subject_groups(destination))
    if manifest["record_numbers"] != canonical["record_numbers"]:
        raise ValueError("split manifest differs from checksum-verified subject groups")
    names = development_names(manifest)
    checksum_path = destination / "SHA256SUMS.txt"
    hashes = {}
    for line in checksum_path.read_text(encoding="utf-8").splitlines():
        digest, _, path = line.partition(" ")
        hashes[Path(path.strip().lstrip("*")).name] = digest
    if not all(name in hashes and len(hashes[name]) == 64 for name in names):
        raise ValueError("official checksum list lacks development source files")

    def acquire(name: str) -> None:
        path = destination / name
        if path.exists():
            content = path.read_bytes()
        else:
            with urlopen(f"{BASE_URL}/bidmc_csv/{name}", timeout=60) as response:
                content = response.read()
        if hashlib.sha256(content).hexdigest() != hashes[name]:
            raise ValueError(f"checksum mismatch for {name}")
        path.write_bytes(content)

    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(acquire, names))
    print(f"Verified {len(names)} development Signals/Breaths files in {destination}")


if __name__ == "__main__":
    main()
