"""Aggregate exploratory BIDMC RR results by recording, never by window alone."""

from __future__ import annotations

import argparse
import json
import statistics
from pathlib import Path

from bidmc_respiration_baseline import METHOD_VERSION, evaluate, read_record
from fetch_bidmc_development import development_names


def summarize(reports: list[dict[str, object]]) -> dict[str, object]:
    if not reports:
        raise ValueError("at least one recording report is required")
    accepted = [report for report in reports if report["evaluated_windows"] > 0]
    keys = ("mae_vs_annotator_1_breaths_per_min",
            "bias_vs_annotator_1_breaths_per_min",
            "mae_vs_annotator_2_breaths_per_min",
            "bias_vs_annotator_2_breaths_per_min",
            "mean_absolute_annotator_difference_breaths_per_min")
    return {
        "method_version": METHOD_VERSION,
        "recordings_attempted": len(reports),
        "recordings_with_estimate": len(accepted),
        "windows_attempted_overlapping": sum(report["attempted_windows"] for report in reports),
        "windows_evaluated_overlapping": sum(report["evaluated_windows"] for report in reports),
        "rejected_windows": {
            reason: sum(report["rejected_windows"][reason] for report in reports)
            for reason in ("no_annotated_reference", "no_candidate_estimate")
        },
        **{f"recording_mean_{key}": statistics.mean(report[key] for report in accepted)
           if accepted else None for key in keys},
        "recording_max_mae_vs_annotator_1_breaths_per_min":
        max(report["mae_vs_annotator_1_breaths_per_min"] for report in accepted)
        if accepted else None,
        "recording_max_mae_vs_annotator_2_breaths_per_min":
        max(report["mae_vs_annotator_2_breaths_per_min"] for report in accepted)
        if accepted else None,
        "recording_max_annotator_difference_breaths_per_min":
        max(report["mean_absolute_annotator_difference_breaths_per_min"]
            for report in accepted) if accepted else None,
        "claim": "exploratory hospital sensor PPG development subset; no phone-camera validation",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv-dir", required=True, type=Path)
    parser.add_argument("--split-manifest", type=Path,
                        default=Path(__file__).with_name("bidmc_split_manifest.json"))
    args = parser.parse_args()
    split = json.loads(args.split_manifest.read_text(encoding="utf-8"))
    development_names(split)  # Reject unknown or malformed split versions.
    reports = []
    for record in split["record_numbers"]["development"]:
        ppg, rate, first, second, _ = read_record(args.csv_dir, record)
        reports.append(evaluate(ppg, rate, first, second))
    result = summarize(reports)
    result["split_version"] = split["split_version"]
    result["subject_groups_reserved_development"] = split["subject_group_counts"]["development"]
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
