"""Fetch and checksum BIDMC grouping metadata into a directory outside Git."""

from __future__ import annotations

import argparse
import hashlib
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import urlopen

from bidmc_subject_split import RECORD_NUMBERS


BASE_URL = "https://physionet.org/files/bidmc/1.0.0"


def expected_metadata_hashes(checksums: str) -> dict[str, str]:
    expected = {}
    for line in checksums.splitlines():
        digest, _, name = line.partition(" ")
        name = name.strip().lstrip("*")
        if name.startswith("bidmc_csv/") and name.endswith("_Fix.txt"):
            expected[Path(name).name] = digest
    names = {f"bidmc_{record}_Fix.txt" for record in RECORD_NUMBERS}
    if set(expected) != names or any(len(value) != 64 for value in expected.values()):
        raise ValueError("official checksum list does not cover 53 Fix metadata files")
    return expected


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata-dir", required=True, type=Path)
    args = parser.parse_args()
    destination = args.metadata_dir.resolve()
    repository = Path(__file__).resolve().parents[2]
    if destination == repository or repository in destination.parents:
        parser.error("source metadata must remain outside the repository")
    destination.mkdir(parents=True, exist_ok=True)

    with urlopen(f"{BASE_URL}/SHA256SUMS.txt", timeout=30) as response:
        manifest = response.read()
    expected = expected_metadata_hashes(manifest.decode("utf-8"))
    (destination / "SHA256SUMS.txt").write_bytes(manifest)

    def acquire(name: str) -> str:
        path = destination / name
        if path.exists():
            content = path.read_bytes()
        else:
            with urlopen(f"{BASE_URL}/bidmc_csv/{name}", timeout=30) as response:
                content = response.read()
        if hashlib.sha256(content).hexdigest() != expected[name]:
            raise ValueError(f"checksum mismatch for {name}")
        path.write_bytes(content)
        return name

    with ThreadPoolExecutor(max_workers=6) as pool:
        verified = list(pool.map(acquire, sorted(expected)))
    print(f"Verified {len(verified)} BIDMC Fix metadata files in {destination}")


if __name__ == "__main__":
    main()
