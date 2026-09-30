"""Deterministic BIDMC subject-group split from licensed Fix files outside Git.

Only public recording numbers and aggregate counts are emitted. MIMIC subject
identifiers, demographics and source metadata never enter the repository output.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path


SPLIT_VERSION = "bidmc-subject-v1"
RECORD_NUMBERS = tuple(f"{number:02d}" for number in range(1, 54))
ALREADY_INSPECTED = frozenset({"01", "02"})
SUBJECT_PATTERN = re.compile(r"^MIMIC II matched wdb ID: (s\d+)$", re.MULTILINE)


def read_subject_groups(metadata_dir: Path) -> dict[str, list[str]]:
    groups: dict[str, list[str]] = defaultdict(list)
    for record in RECORD_NUMBERS:
        path = metadata_dir / f"bidmc_{record}_Fix.txt"
        text = path.read_text(encoding="utf-8")
        match = SUBJECT_PATTERN.search(text)
        if match is None:
            raise ValueError(f"subject ID absent in recording {record}")
        groups[match.group(1)].append(record)
    return dict(groups)


def assign_subject_groups(groups: dict[str, list[str]]) -> dict[str, object]:
    expected = set(RECORD_NUMBERS)
    actual = [record for records in groups.values() for record in records]
    if set(actual) != expected or len(actual) != len(expected):
        raise ValueError("metadata must cover every BIDMC recording exactly once")
    splits = {"development": [], "validation": [], "held_out": []}
    group_counts = {name: 0 for name in splits}
    for subject, records in sorted(groups.items()):
        if ALREADY_INSPECTED.intersection(records):
            bucket = "development"
        else:
            digest = hashlib.sha256(f"{SPLIT_VERSION}:{subject}".encode()).digest()
            fraction = int.from_bytes(digest[:8], "big") / 2**64
            bucket = "development" if fraction < 0.25 else (
                "validation" if fraction < 0.50 else "held_out"
            )
        splits[bucket].extend(records)
        group_counts[bucket] += 1
    return {
        "dataset": "BIDMC PPG and Respiration v1.0.0",
        "split_version": SPLIT_VERSION,
        "already_inspected_development_records": sorted(ALREADY_INSPECTED),
        "rule": "same MIMIC subject grouped; previously inspected groups development; remaining groups assigned by SHA-256(split_version:subject) first 64 bits, intervals [0,.25), [.25,.50), [.50,1)",
        "record_numbers": {name: sorted(records) for name, records in splits.items()},
        "record_counts": {name: len(records) for name, records in splits.items()},
        "subject_group_counts": group_counts,
        "claim": "split reservation only; no model, physiological validity or phone-camera evaluation",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata-dir", required=True, type=Path)
    parser.add_argument("--output", type=Path, help="safe JSON manifest destination")
    args = parser.parse_args()
    manifest = assign_subject_groups(read_subject_groups(args.metadata_dir))
    encoded = json.dumps(manifest, indent=2, sort_keys=True) + "\n"
    if args.output is None:
        print(encoded, end="")
    else:
        args.output.write_text(encoded, encoding="utf-8")


if __name__ == "__main__":
    main()
