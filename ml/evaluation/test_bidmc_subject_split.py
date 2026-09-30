import unittest

from bidmc_subject_split import RECORD_NUMBERS, assign_subject_groups
from fetch_bidmc_metadata import expected_metadata_hashes
from fetch_bidmc_development import development_names


class SubjectSplitTest(unittest.TestCase):
    def test_duplicate_subject_records_stay_together_and_seen_records_are_development(self):
        groups = {f"s{int(record):05d}": [record] for record in RECORD_NUMBERS}
        groups.pop("s00003")
        groups["s00001"].append("03")
        manifest = assign_subject_groups(groups)
        buckets = manifest["record_numbers"]
        self.assertIn("01", buckets["development"])
        self.assertIn("02", buckets["development"])
        self.assertIn("03", buckets["development"])
        self.assertEqual(set(RECORD_NUMBERS), set(sum(buckets.values(), [])))
        self.assertEqual(len(RECORD_NUMBERS), sum(manifest["record_counts"].values()))

    def test_missing_record_is_rejected(self):
        groups = {f"s{int(record):05d}": [record] for record in RECORD_NUMBERS[:-1]}
        with self.assertRaises(ValueError):
            assign_subject_groups(groups)

    def test_checksum_manifest_requires_all_grouping_files(self):
        entries = [
            f"{'0' * 64} bidmc_csv/bidmc_{record}_Fix.txt"
            for record in RECORD_NUMBERS
        ]
        self.assertEqual(53, len(expected_metadata_hashes("\n".join(entries))))
        with self.assertRaises(ValueError):
            expected_metadata_hashes("\n".join(entries[:-1]))

    def test_development_download_selection_excludes_held_out(self):
        groups = {f"s{int(record):05d}": [record] for record in RECORD_NUMBERS}
        manifest = assign_subject_groups(groups)
        names = development_names(manifest)
        self.assertEqual(2 * manifest["record_counts"]["development"], len(names))
        for record in manifest["record_numbers"]["held_out"]:
            self.assertNotIn(f"bidmc_{record}_Signals.csv", names)
            self.assertNotIn(f"bidmc_{record}_Breaths.csv", names)


if __name__ == "__main__":
    unittest.main()
