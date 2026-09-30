import unittest
import warnings
import tempfile
from pathlib import Path

import numpy as np

from bidmc_respiration_baseline import estimate_rr, evaluate, parse_breath_column, read_record
from bidmc_development_report import summarize


class RespiratoryBaselineTest(unittest.TestCase):
    def test_synthetic_low_frequency_modulation_is_detected(self):
        sample_rate = 125.0
        times = np.arange(120 * int(sample_rate)) / sample_rate
        ppg = (np.sin(2 * np.pi * 1.2 * times)
               + 0.3 * np.sin(2 * np.pi * 0.3 * times))
        breaths = list(range(0, len(ppg), round(sample_rate / 0.3)))
        with warnings.catch_warnings():
            warnings.simplefilter("error", RuntimeWarning)
            report = evaluate(ppg, sample_rate, breaths, breaths)
        self.assertEqual(3, report["attempted_windows"])
        self.assertEqual(3, report["evaluated_windows"])
        self.assertLess(report["mae_vs_annotator_1_breaths_per_min"], 1.1)
        self.assertLess(report["mae_vs_annotator_2_breaths_per_min"], 1.1)
        self.assertEqual(0.0, report["mean_absolute_annotator_difference_breaths_per_min"])

    def test_flat_signal_is_withheld(self):
        self.assertIsNone(estimate_rr(np.ones(60 * 125), 125.0))

    def test_development_report_weights_recordings_and_counts_rejections(self):
        common = {"attempted_windows": 15, "evaluated_windows": 14,
                  "rejected_windows": {"no_annotated_reference": 1,
                                       "no_candidate_estimate": 0},
                  "bias_vs_annotator_1_breaths_per_min": 0.0,
                  "mae_vs_annotator_2_breaths_per_min": 1.0,
                  "bias_vs_annotator_2_breaths_per_min": 0.0,
                  "mean_absolute_annotator_difference_breaths_per_min": 0.0}
        reports = [{**common, "mae_vs_annotator_1_breaths_per_min": value}
                   for value in (1.0, 3.0)]
        result = summarize(reports)
        self.assertEqual(2, result["recordings_attempted"])
        self.assertEqual(30, result["windows_attempted_overlapping"])
        self.assertEqual(2.0,
                         result["recording_mean_mae_vs_annotator_1_breaths_per_min"])
        self.assertEqual(3.0,
                         result["recording_max_mae_vs_annotator_1_breaths_per_min"])
        self.assertEqual(2, result["rejected_windows"]["no_annotated_reference"])

    def test_csv_rounded_times_do_not_imply_missing_samples(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with (root / "bidmc_01_Signals.csv").open("w") as handle:
                handle.write("Time [s], PLETH\n")
                for index in range(501):
                    handle.write(f"{index / 125:.2f},0.5\n")
            (root / "bidmc_01_Breaths.csv").write_text(
                "breaths ann1 [signal sample no], breaths ann2 [signal sample no]\n"
                "189,187\n"
            )
            _, rate, _, _, _ = read_record(root, "01")
            self.assertEqual(125.0, rate)

    def test_trailing_annotation_nan_is_padding_but_internal_gap_is_rejected(self):
        column = "ann"
        rows = [{column: "1"}, {column: "3"}, {column: "NaN"}]
        self.assertEqual([1, 3], parse_breath_column(rows, column))
        with self.assertRaises(ValueError):
            parse_breath_column(rows + [{column: "5"}], column)


if __name__ == "__main__":
    unittest.main()
