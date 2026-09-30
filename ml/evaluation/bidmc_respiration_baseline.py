"""Exploratory sensor-PPG respiratory baseline; never used for Carda user output.

Input is the openly licensed BIDMC CSV set kept outside Git. No threshold or
physiological accuracy claim is inferred from this script or one recording.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import statistics
from pathlib import Path

import numpy as np
from scipy import signal


METHOD_VERSION = "bidmc-rr-baseline-0.1"
WINDOW_SECONDS = 60
STEP_SECONDS = 30
MIN_BREATHS = 3
RESPIRATORY_BAND_HZ = (0.1, 0.6)  # 6–36 breaths/min; exploratory window


def estimate_rr(ppg: np.ndarray, sample_rate_hz: float) -> float | None:
    """Return the strongest low-frequency PPG baseline oscillation in breaths/min."""
    if len(ppg) < sample_rate_hz * WINDOW_SECONDS * 0.9:
        return None
    if not np.isfinite(ppg).all() or float(np.std(ppg)) <= 1e-8:
        return None
    sos = signal.butter(3, RESPIRATORY_BAND_HZ, btype="bandpass", fs=sample_rate_hz,
                        output="sos")
    respiratory_component = signal.sosfiltfilt(sos, ppg)
    if float(np.std(respiratory_component)) <= 1e-8:
        return None
    # The band-pass already removes slow drift; constant centering avoids an
    # unstable linear-detrend path seen in SciPy 1.16.3 on this fixture.
    frequency, density = signal.periodogram(respiratory_component, fs=sample_rate_hz,
                                             window="hann", detrend="constant")
    band = (frequency >= RESPIRATORY_BAND_HZ[0]) & (frequency <= RESPIRATORY_BAND_HZ[1])
    if not np.any(band) or not np.isfinite(density[band]).all():
        return None
    return float(frequency[band][int(np.argmax(density[band]))] * 60.0)


def reference_rr(breath_samples: list[int], start: int, end: int,
                 sample_rate_hz: float) -> float | None:
    count = sum(start <= sample < end for sample in breath_samples)
    return count * 60.0 / ((end - start) / sample_rate_hz) if count >= MIN_BREATHS else None


def parse_breath_column(rows: list[dict[str, str]], column: str) -> list[int]:
    """Parse BIDMC's independently sized annotation columns with trailing NaN."""
    samples: list[int] = []
    padding_started = False
    for row in rows:
        value = row[column].strip()
        if value.lower() in ("", "nan"):
            padding_started = True
            continue
        if padding_started:
            raise ValueError("breath annotation resumes after missing-value padding")
        sample = int(value)
        if sample < 0 or (samples and sample <= samples[-1]):
            raise ValueError("breath annotations must be increasing sample indices")
        samples.append(sample)
    if not samples:
        raise ValueError("breath annotation column is empty")
    return samples


def read_record(csv_dir: Path, record: str) -> tuple[np.ndarray, float, list[int], list[int], dict[str, str]]:
    if not record.isdigit() or len(record) != 2:
        raise ValueError("record must be a two-digit BIDMC record number")
    signal_path = csv_dir / f"bidmc_{record}_Signals.csv"
    breath_path = csv_dir / f"bidmc_{record}_Breaths.csv"
    with signal_path.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    if not rows or " PLETH" not in rows[0] or "Time [s]" not in rows[0]:
        raise ValueError("BIDMC signal CSV lacks Time [s] or PLETH")
    times = np.asarray([float(row["Time [s]"]) for row in rows], dtype=float)
    ppg = np.asarray([float(row[" PLETH"]) for row in rows], dtype=float)
    deltas = np.diff(times)
    if not np.isfinite(deltas).all() or np.any(deltas < 0) or times[-1] <= times[0]:
        raise ValueError("signal time must be finite and nondecreasing")
    # BIDMC's CSV time column rounds to 0.01 s after 100 s, so adjacent
    # 125-Hz samples can share a printed timestamp. Check sample-index timing.
    sample_rate_hz = (len(times) - 1) / float(times[-1] - times[0])
    if not 124.0 <= sample_rate_hz <= 126.0:
        raise ValueError(f"unexpected BIDMC sample rate: {sample_rate_hz}")
    indexed_times = times[0] + np.arange(len(times)) / sample_rate_hz
    if float(np.max(np.abs(times - indexed_times))) > 0.01:
        raise ValueError("signal timestamps deviate from indexed sampling")
    with breath_path.open(newline="", encoding="utf-8") as handle:
        breaths = list(csv.DictReader(handle))
    if not breaths:
        raise ValueError("breath annotations are empty")
    annotator_1 = parse_breath_column(breaths, "breaths ann1 [signal sample no]")
    annotator_2 = parse_breath_column(breaths, " breaths ann2 [signal sample no]")
    hashes = {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
              for path in (signal_path, breath_path)}
    return ppg, sample_rate_hz, annotator_1, annotator_2, hashes


def evaluate(ppg: np.ndarray, sample_rate_hz: float, annotator_1: list[int],
             annotator_2: list[int]) -> dict[str, object]:
    window = round(WINDOW_SECONDS * sample_rate_hz)
    step = round(STEP_SECONDS * sample_rate_hz)
    errors: list[float] = []
    errors_second: list[float] = []
    annotator_differences: list[float] = []
    rejected = {"no_annotated_reference": 0, "no_candidate_estimate": 0}
    attempted = 0
    for start in range(0, len(ppg) - window + 1, step):
        attempted += 1
        end = start + window
        first = reference_rr(annotator_1, start, end, sample_rate_hz)
        second = reference_rr(annotator_2, start, end, sample_rate_hz)
        if first is None or second is None:
            rejected["no_annotated_reference"] += 1
            continue
        candidate = estimate_rr(ppg[start:end], sample_rate_hz)
        if candidate is None:
            rejected["no_candidate_estimate"] += 1
            continue
        errors.append(candidate - first)
        errors_second.append(candidate - second)
        annotator_differences.append(second - first)
    return {
        "method_version": METHOD_VERSION,
        "window_seconds": WINDOW_SECONDS,
        "step_seconds": STEP_SECONDS,
        "attempted_windows": attempted,
        "evaluated_windows": len(errors),
        "rejected_windows": rejected,
        "mae_vs_annotator_1_breaths_per_min": statistics.mean(map(abs, errors)) if errors else None,
        "bias_vs_annotator_1_breaths_per_min": statistics.mean(errors) if errors else None,
        "mae_vs_annotator_2_breaths_per_min": statistics.mean(map(abs, errors_second))
        if errors_second else None,
        "bias_vs_annotator_2_breaths_per_min": statistics.mean(errors_second)
        if errors_second else None,
        "mean_absolute_annotator_difference_breaths_per_min":
        statistics.mean(map(abs, annotator_differences)) if annotator_differences else None,
        "mean_annotator_2_minus_1_breaths_per_min": statistics.mean(annotator_differences)
        if annotator_differences else None,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv-dir", required=True, type=Path,
                        help="directory containing BIDMC CSV files, outside Git")
    parser.add_argument("--record", default="01", help="two-digit BIDMC recording")
    args = parser.parse_args()
    ppg, rate, first, second, hashes = read_record(args.csv_dir, args.record)
    report = evaluate(ppg, rate, first, second)
    report.update({"dataset": "BIDMC PPG and Respiration v1.0.0",
                   "record": args.record, "sample_rate_hz": rate,
                   "input_sha256": hashes,
                   "domain": "clinical pulse-ox PPG, not a phone camera",
                   "claim": "exploratory offline baseline only; no Carda respiratory validation"})
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
