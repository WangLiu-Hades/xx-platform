"""Exercise evidence tampering and counting semantics, independently of PostgreSQL."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from data_worker.common import WorkerError, canonical
from data_worker.metrics import audit_export

from data_worker.settings import PACKAGE_ROOT, OUTPUT_ROOT
BASE = PACKAGE_ROOT / "fixtures/metrics/after-restart"


class MetricAuditTests(unittest.TestCase):
    def setUp(self):
        root = OUTPUT_ROOT / "d3-07-08/tmp"
        root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix="metric-test-", dir=str(root))
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name)
        for name in ("export.json", "summary.json", "rows.jsonl", "captures.jsonl", "quality.jsonl"):
            (self.path / name).write_bytes((BASE / name).read_bytes())

    def read(self, name):
        text = (self.path / name).read_text(encoding="utf-8")
        return [json.loads(line) for line in text.splitlines()] if name.endswith("jsonl") else json.loads(text)

    def save(self, name, value, rehash=True):
        text = "".join(canonical(item) + "\n" for item in value) if name.endswith("jsonl") else canonical(value) + "\n"
        (self.path / name).write_text(text, encoding="utf-8")
        if rehash and name != "export.json":
            manifest = self.read("export.json")
            manifest["files"][name] = hashlib.sha256((self.path / name).read_bytes()).hexdigest()
            self.save("export.json", manifest, False)

    def assert_invalid(self):
        with self.assertRaises(WorkerError) as caught:
            audit_export(self.path)
        self.assertEqual(caught.exception.code, "METRIC_EVIDENCE_INVALID")

    def test_frozen_actual_inventory_and_processing_are_distinct(self):
        metrics = audit_export(self.path)["metrics"]
        expected = {"D3_PROCESSING_ROWS": 72, "D3_ACCEPTED_ROWS": 16, "D3_DUPLICATE_ROWS": 17,
                    "D3_REJECTED_ROWS": 39, "D3_FACT_INVENTORY": 16,
                    "D3_QUALITY_ERRORS": 39, "D3_QUALITY_WARNINGS": 4}
        for name, value in expected.items():
            self.assertEqual(metrics[name]["value"], value)
        self.assertEqual(metrics["D3_REJECTION_RATIO"]["numerator"], 39)
        self.assertEqual(metrics["D3_REJECTION_RATIO"]["denominator"], 72)

    def test_replay_adds_processing_but_no_facts(self):
        batches = audit_export(self.path)["byBatch"]
        self.assertEqual(batches[1]["inputRows"], 34)
        self.assertEqual(batches[1]["duplicateCount"], 15)
        self.assertEqual(batches[1]["acceptedCount"], 0)
        self.assertEqual(batches[1]["factCountAfter"], batches[0]["factCountAfter"])

    def test_full_retry_does_not_increment_any_metrics(self):
        previous = audit_export(self.path)
        retried = audit_export(PACKAGE_ROOT / "fixtures/metrics/retry")
        values = lambda result: {name: item["value"] for name, item in result["metrics"].items()}
        self.assertEqual(values(previous), values(retried))
        self.assertEqual(previous["bySource"], retried["bySource"])

    def test_missing_telemetry_is_unavailable_not_zero(self):
        result = audit_export(self.path)
        for name in ("SOURCE_CAPTURE_COUNT", "DEVICE_ONLINE_RATIO"):
            self.assertIsNone(result["metrics"][name]["value"])
            self.assertIsNone(result["metrics"][name]["asOf"])
            self.assertEqual(result["metrics"][name]["status"], "UNAVAILABLE")
        self.assertIsNone(result["watermarks"]["sourceCompletenessWatermark"])
        self.assertFalse(result["publicApiReady"])

    def test_checksum_tampering_rejected(self):
        with (self.path / "rows.jsonl").open("a", encoding="utf-8") as stream:
            stream.write("{}\n")
        self.assert_invalid()

    def test_incomplete_export_rejected(self):
        manifest = self.read("export.json")
        manifest["complete"] = False
        self.save("export.json", manifest)
        self.assert_invalid()

    def test_unexpected_manifest_path_rejected(self):
        manifest = self.read("export.json")
        manifest["files"]["../outside.json"] = "0" * 64
        self.save("export.json", manifest)
        self.assert_invalid()

    def test_missing_artifact_rejected(self):
        (self.path / "quality.jsonl").unlink()
        self.assert_invalid()

    def test_duplicate_row_not_double_counted(self):
        rows = self.read("rows.jsonl")
        rows.append(copy.deepcopy(rows[0]))
        self.save("rows.jsonl", rows)
        self.assert_invalid()

    def test_duplicate_fact_not_double_counted(self):
        facts = self.read("captures.jsonl")
        facts.append(copy.deepcopy(facts[0]))
        self.save("captures.jsonl", facts)
        self.assert_invalid()

    def test_wrong_event_reference_rejected_even_when_capture_exists(self):
        rows = self.read("rows.jsonl")
        row = next(r for r in rows if r["outcome"] == "DUPLICATE")
        row["sourceRecordKey"] = "another-event"
        self.save("rows.jsonl", rows)
        self.assert_invalid()

    def test_orphan_fact_rejected(self):
        rows = self.read("rows.jsonl")
        rows = [r for r in rows if not (r["outcome"] == "ACCEPTED" and r["rowNumber"] == 1)]
        self.save("rows.jsonl", rows)
        self.assert_invalid()

    def test_missing_quality_issue_rejected(self):
        self.save("quality.jsonl", self.read("quality.jsonl")[1:])
        self.assert_invalid()

    def test_quality_attached_to_wrong_task_rejected(self):
        issues = self.read("quality.jsonl")
        issues[0]["taskId"] = "00000000-0000-4000-8000-000000000001"
        self.save("quality.jsonl", issues)
        self.assert_invalid()

    def test_drifted_batch_source_rejected(self):
        rows = self.read("rows.jsonl")
        next(r for r in rows if r["outcome"] == "REJECTED")["source"] = "OTHER_SOURCE"
        self.save("rows.jsonl", rows)
        self.assert_invalid()

    def test_unknown_outcome_rejected(self):
        rows = self.read("rows.jsonl")
        rows[0]["outcome"] = "SUCCESS"
        self.save("rows.jsonl", rows)
        self.assert_invalid()

    def test_naive_fact_timestamp_rejected(self):
        facts = self.read("captures.jsonl")
        facts[0]["record"]["captureTime"] = "2026-10-16T12:00:00"
        self.save("captures.jsonl", facts)
        self.assert_invalid()

    def test_incorrect_summary_rejected(self):
        summary = self.read("summary.json")
        summary["totals"]["DUPLICATE"] += 1
        self.save("summary.json", summary)
        self.assert_invalid()

    def test_incorrect_inventory_checkpoint_rejected(self):
        summary = self.read("summary.json")
        summary["batches"][1]["factCountAfter"] = 29
        self.save("summary.json", summary)
        self.assert_invalid()

    def test_boolean_counter_rejected(self):
        summary = self.read("summary.json")
        summary["batches"][-1]["acceptedCount"] = True
        self.save("summary.json", summary)
        self.assert_invalid()

    def test_fingerprint_mismatch_rejected(self):
        summary = self.read("summary.json")
        summary["stateFingerprint"] = "0" * 64
        self.save("summary.json", summary)
        self.assert_invalid()

    def test_empty_complete_ledger_has_zero_counts_and_no_ratio(self):
        for name in ("rows.jsonl", "captures.jsonl", "quality.jsonl"):
            self.save(name, [])
        summary = self.read("summary.json")
        summary.update(batches=[], finalFacts=0, totalProcessingRows=0, qualityRecords=0,
                       totals={"ACCEPTED": 0, "DUPLICATE": 0, "REJECTED": 0})
        self.save("summary.json", summary)
        result = audit_export(self.path)
        self.assertEqual(result["metrics"]["D3_FACT_INVENTORY"]["value"], 0)
        self.assertIsNone(result["metrics"]["D3_REJECTION_RATIO"]["value"])
        self.assertEqual(result["metrics"]["D3_REJECTION_RATIO"]["reason"], "EMPTY_DENOMINATOR")
        self.assertTrue(all(value is None for value in result["watermarks"].values()))
