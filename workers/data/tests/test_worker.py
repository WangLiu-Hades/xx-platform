"""Behavioral regression: frozen D3-02 oracle, restart, isolation, and rollback."""
from contextlib import redirect_stdout
from copy import deepcopy
import hashlib
import io
import json
from pathlib import Path
import runpy
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import uuid

from data_worker.cli import main
from data_worker.common import RowError, WorkerError, canonical, digest
from data_worker.pipeline import process_batch
from data_worker.plate import PlateAdapter, parse_line
from data_worker.report import export_report
from data_worker.settings import Batch, PACKAGE_ROOT, load_settings, OUTPUT_ROOT, REPO_ROOT
from data_worker.store import Ledger

PROJECT = Path(__file__).resolve().parents[1]
SAMPLES = PACKAGE_ROOT / "fixtures/plate-v0"
TEST_ROOT = OUTPUT_ROOT / "d3-03-04/test-work"
CONFIG = PROJECT / "config.example.json"


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def read_lines(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


def golden_equal(test, rows, facts, batches):
    expected_rows = read_lines(SAMPLES / "expected/rows.jsonl")
    test.assertEqual(len(rows), 72)
    for actual, expected in zip(rows, expected_rows):
        test.assertEqual({key: actual[key] for key in expected if key != "caseId"},
                         {key: value for key, value in expected.items() if key != "caseId"})
    expected_facts = read_lines(SAMPLES / "expected/captures.jsonl")
    by_identity = lambda values: {
        (fact["source"], fact["sourceRecordKey"]): {key: value for key, value in fact.items() if key != "caseId"}
        for fact in values}
    test.assertEqual(by_identity(facts), by_identity(expected_facts))
    test.assertEqual(batches, read(SAMPLES / "expected/summary.json")["batches"])


class WorkerTests(unittest.TestCase):
    def setUp(self):
        TEST_ROOT.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix="case-", dir=str(TEST_ROOT))
        self.directory = Path(self.temp.name).resolve()
        self.settings = load_settings(CONFIG)
        self.adapter = self.settings.adapter
        self.state = self.directory / "state.sqlite3"
        self.ledger = Ledger(self.state, self.settings.fingerprint)
        self.raw = read_lines(SAMPLES / "raw/initial.jsonl")[0]

    def tearDown(self):
        self.ledger.close()
        # Verify the exact recursive cleanup destination is inside the dedicated test workspace.
        self.directory.relative_to(TEST_ROOT.resolve())
        self.assertNotEqual(self.directory, TEST_ROOT.resolve())
        self.temp.cleanup()

    def restart(self):
        self.ledger.close()
        self.ledger = Ledger(self.state, self.settings.fingerprint)

    def custom_batch(self, rows=None, data=None, **changes):
        metadata = deepcopy(self.settings.batches[0].metadata)
        metadata.update(batchId=str(uuid.uuid4()), taskId=str(uuid.uuid4()), name="TEST")
        metadata.update(changes)
        path = self.directory / (metadata["batchId"] + ".jsonl")
        if data is None:
            data = "".join(canonical(row) + "\n" for row in rows).encode("utf-8")
        path.write_bytes(data)
        metadata["inputFile"] = path.name
        return Batch(metadata, path)

    def run_batch(self, batch, limit=65536):
        return process_batch(self.ledger, self.adapter, batch, limit)

    def test_frozen_72_rows_and_16_dtos_match_across_restarts(self):
        for batch in self.settings.batches:
            self.run_batch(batch)
            self.restart()
        rows, facts, batches, _ = self.ledger.snapshot()
        golden_equal(self, rows, facts, batches)
        schemas = read(REPO_ROOT / "apps/web/src/api/contract.json")["components"]["schemas"]
        validate = runpy.run_path(str(PACKAGE_ROOT / "lab/contract_validation.py"))["validate_dto"]
        for fact in facts:
            validate(fact["record"], schemas["PlateCaptureRecord"], schemas)
        self.assertEqual(len({fact["record"]["captureId"] for fact in facts}), 16)

    def test_new_valid_data_without_case_ids_or_expected_inputs(self):
        raws = [dict(self.raw, recordId="NEW-" + str(i), captureTime="2026-11-02T12:34:56.000123-03:30")
                for i in range(40)]
        batch = self.custom_batch(raws)
        self.assertNotIn("caseIds", batch.metadata)
        summary, cached = self.run_batch(batch)
        self.assertEqual(summary["acceptedCount"], 40)
        self.assertFalse(cached)
        self.assertEqual(self.ledger.get_fact("SYNTH_PLATE_A", "NEW-39")["record"]["captureTime"],
                         "2026-11-02T16:04:56.000123Z")

    def test_row_isolation_and_physical_numbers_for_malformed_input(self):
        invalid = [
            (b"\xff\n", "INPUT_UTF8_INVALID"),
            (b"{broken\n", "INPUT_JSON_INVALID"),
            (b"[]\n", "INPUT_OBJECT_REQUIRED"),
            (b'{"recordId":"A","recordId":"B"}\n', "INPUT_JSON_INVALID"),
            (b'{"longitude":NaN}\n', "INPUT_JSON_INVALID"),
            (b'{"longitude":1e999}\n', "INPUT_JSON_INVALID"),
            (b'{"plateText":"\\ud800"}\n', "INPUT_JSON_INVALID"),
            (b"x" * 2000 + b"\n", "INPUT_LINE_TOO_LARGE"),
        ]
        data = b"\n \t\r\n" + b"".join(item[0] for item in invalid) + canonical(self.raw).encode("utf-8")
        summary, _ = self.run_batch(self.custom_batch(data=data), limit=512)
        rows = self.ledger.snapshot()[0]
        self.assertEqual(summary["rejectedCount"], 8)
        self.assertEqual(summary["acceptedCount"], 1)
        self.assertEqual([row["rowNumber"] for row in rows], list(range(3, 12)))
        self.assertEqual([row["reasonCode"] for row in rows[:-1]], [item[1] for item in invalid])
        self.assertEqual(rows[-1]["outcome"], "ACCEPTED")

    def test_empty_input_is_a_committed_zero_row_batch(self):
        summary, _ = self.run_batch(self.custom_batch(data=b"\r\n\t\n"))
        self.assertEqual(summary["inputRows"], 0)
        self.assertEqual(summary["factCountAfter"], 0)

    def test_strict_time_rejects_invalid_offsets_and_precision(self):
        for value in ("2026-10-16T08:00:00+08:60", "2026-10-16T08:00:00+24:00",
                      "2026-02-30T08:00:00Z", "2026-10-16T08:00:00.1234567Z",
                      "2026-10-16T08:00:00", "2026-10-16T24:00:00Z", 123):
            with self.subTest(value=value), self.assertRaises(RowError) as error:
                self.adapter.normalize(dict(self.raw, captureTime=value), "SYNTH_PLATE_A", "2026-10-16T02:00:00Z")
            self.assertEqual(error.exception.code, "FIXTURE_TIME_INVALID")

    def test_coordinate_types_pairs_and_extremes(self):
        for values in ({"longitude": False}, {"longitude": "0"}, {"longitude": 181},
                       {"latitude": -91}, {"longitude": 10 ** 400}, {"latitude": None}):
            with self.subTest(values=str(values)[:50]), self.assertRaises(RowError):
                self.adapter.normalize(dict(self.raw, **values), "SYNTH_PLATE_A", "2026-10-16T02:00:00Z")
        for lon, lat in ((0, 0), (-180, -90), (180, 90), (None, None)):
            normalized = self.adapter.normalize(dict(self.raw, longitude=lon, latitude=lat),
                                                "SYNTH_PLATE_A", "2026-10-16T02:00:00Z")
            self.assertEqual((normalized.record["longitude"], normalized.record["latitude"]), (lon, lat))
        raw = dict(self.raw)
        raw.pop("latitude")
        with self.assertRaises(RowError) as error:
            self.adapter.normalize(raw, "SYNTH_PLATE_A", "2026-10-16T02:00:00Z")
        self.assertEqual(error.exception.code, "FIXTURE_COORDINATE_PAIR")

    def test_optional_omission_null_and_mask(self):
        raw = dict(self.raw, plateColor=None)
        for key in ("vehicleImageRef", "longitude", "latitude", "crs"):
            raw.pop(key)
        record = self.adapter.normalize(raw, "SYNTH_PLATE_A", "2026-10-16T02:00:00Z").record
        self.assertIsNone(record["plate"]["plateColor"])
        self.assertNotIn("vehicleImageAssetId", record["plate"])
        self.assertIsNone(record["longitude"])
        self.assertIsNone(record["latitude"])
        self.assertNotIn(raw["plateText"], canonical(record))
        with self.assertRaises(RowError) as error:
            self.adapter.normalize(dict(raw, vehicleImageRef=None), "SYNTH_PLATE_A", "2026-10-16T02:00:00Z")
        self.assertEqual(error.exception.code, "FIXTURE_IMAGE_REFERENCE")

    def test_unknown_device_and_target_preserve_nulls_with_warnings(self):
        normalized = self.adapter.normalize(dict(self.raw, deviceKey="UNKNOWN", plateText="SYNTH-UNKNOWN"),
                                            "SYNTH_PLATE_A", "2026-10-16T02:00:00Z")
        for key in ("deviceId", "siteId", "targetId"):
            self.assertIsNone(normalized.record[key])
        self.assertEqual(normalized.record["targetType"], "PLATE")
        self.assertEqual(normalized.warnings, ["FIXTURE_DEVICE_UNMAPPED", "FIXTURE_TARGET_UNRESOLVED"])

    def test_invalid_row_does_not_claim_identity(self):
        summary, _ = self.run_batch(self.custom_batch([dict(self.raw, latitude=100), self.raw]))
        self.assertEqual((summary["rejectedCount"], summary["acceptedCount"]), (1, 1))
        self.assertEqual(self.ledger.fact_count(), 1)

    def test_whitespace_and_json_key_order_do_not_break_deduplication(self):
        data = (json.dumps(self.raw, sort_keys=False, indent=None) + "\n" + canonical(self.raw) + "\n").encode("utf-8")
        summary, _ = self.run_batch(self.custom_batch(data=data))
        self.assertEqual((summary["acceptedCount"], summary["duplicateCount"]), (1, 1))

    def test_changed_payload_keeps_first_fact_and_ingested_at(self):
        first = self.custom_batch([self.raw])
        self.run_batch(first)
        original = self.ledger.get_fact("SYNTH_PLATE_A", self.raw["recordId"])
        second = self.custom_batch([dict(self.raw, plateColor="DIFFERENT"), self.raw],
                                   receivedAt="2026-12-01T00:00:00Z")
        summary, _ = self.run_batch(second)
        self.assertEqual((summary["rejectedCount"], summary["duplicateCount"]), (1, 1))
        self.assertEqual(original, self.ledger.get_fact("SYNTH_PLATE_A", self.raw["recordId"]))
        self.assertEqual(self.ledger.snapshot()[0][1]["reasonCode"], "FIXTURE_KEY_CONFLICT")

    def test_source_namespaces_and_device_mappings_are_separate(self):
        self.run_batch(self.custom_batch([self.raw]))
        self.run_batch(self.custom_batch([self.raw], source="SYNTH_PLATE_B"))
        facts = self.ledger.snapshot()[1]
        self.assertEqual(len(facts), 2)
        self.assertNotEqual(facts[0]["record"]["captureId"], facts[1]["record"]["captureId"])
        self.assertNotEqual(facts[0]["record"]["siteId"], facts[1]["record"]["siteId"])

    def test_distinct_events_for_same_target_and_time_are_retained(self):
        self.run_batch(self.custom_batch([self.raw, dict(self.raw, recordId="DISTINCT")]))
        self.assertEqual(self.ledger.fact_count(), 2)

    def test_identical_batch_retry_returns_stored_results(self):
        batch = self.custom_batch([self.raw])
        before, cached = self.run_batch(batch)
        self.assertFalse(cached)
        self.restart()
        after, cached = self.run_batch(batch)
        self.assertTrue(cached)
        self.assertEqual(after, before)
        self.assertEqual(len(self.ledger.snapshot()[0]), 1)

    def test_changed_input_for_same_batch_id_is_rejected(self):
        batch = self.custom_batch([self.raw])
        self.run_batch(batch)
        batch.input_path.write_bytes((canonical(dict(self.raw, plateColor="CHANGED")) + "\n").encode("utf-8"))
        with self.assertRaises(WorkerError) as error:
            self.run_batch(batch)
        self.assertEqual(error.exception.code, "BATCH_INPUT_CHANGED")
        self.assertEqual(self.ledger.fact_count(), 1)

    def test_changed_metadata_for_same_batch_id_is_rejected(self):
        batch = self.custom_batch([self.raw])
        self.run_batch(batch)
        batch.metadata["receivedAt"] = "2026-12-01T00:00:00Z"
        with self.assertRaises(WorkerError) as error:
            self.run_batch(batch)
        self.assertEqual(error.exception.code, "BATCH_INPUT_CHANGED")

    def test_replay_requires_original_source_and_identical_input(self):
        original = self.custom_batch([self.raw])
        replay = self.custom_batch([self.raw], mode="REPLAY", originalBatchId=original.id)
        with self.assertRaises(WorkerError) as error:
            self.run_batch(replay)
        self.assertEqual(error.exception.code, "REPLAY_ORIGINAL_MISSING")
        self.run_batch(original)
        for bad in (
            self.custom_batch([self.raw], mode="REPLAY", originalBatchId=original.id, source="SYNTH_PLATE_B"),
            self.custom_batch([dict(self.raw, plateColor="CHANGED")], mode="REPLAY", originalBatchId=original.id),
        ):
            with self.assertRaises(WorkerError) as error:
                self.run_batch(bad)
            self.assertEqual(error.exception.code, "REPLAY_INPUT_CHANGED")
        summary, _ = self.run_batch(replay)
        self.assertEqual((summary["duplicateCount"], summary["factCountAfter"]), (1, 1))

    def test_exception_rolls_back_facts_rows_and_batch_then_retry_succeeds(self):
        batch = self.custom_batch([self.raw, dict(self.raw, recordId="SECOND")])
        original_put = self.ledger.put_row
        def fail_on_second(row):
            original_put(row)
            if row["rowNumber"] == 2:
                raise RuntimeError("Injected disk/worker failure")
        with patch.object(self.ledger, "put_row", side_effect=fail_on_second), self.assertRaises(RuntimeError):
            self.run_batch(batch)
        self.restart()
        rows, facts, batches, _ = self.ledger.snapshot()
        self.assertEqual((rows, facts, batches), ([], [], []))
        summary, _ = self.run_batch(batch)
        self.assertEqual(summary["acceptedCount"], 2)

    def test_changed_file_between_hash_and_read_rolls_back(self):
        batch = self.custom_batch([self.raw])
        initial_hash = hashlib.sha256(batch.input_path.read_bytes()).hexdigest()
        def change_file(path):
            path.write_bytes(path.read_bytes() + b"\n")
            return initial_hash
        with patch("data_worker.pipeline.file_digest", side_effect=change_file), self.assertRaises(WorkerError) as error:
            self.run_batch(batch)
        self.assertEqual(error.exception.code, "INPUT_CHANGED_DURING_READ")
        self.assertEqual(self.ledger.snapshot()[:3], ([], [], []))

    def test_state_rejects_different_reference_or_policy_fingerprint(self):
        with self.assertRaises(WorkerError) as error:
            Ledger(self.state, "different")
        self.assertEqual(error.exception.code, "STATE_VERSION_MISMATCH")
        self.run_batch(self.custom_batch([self.raw]))
        self.assertEqual(self.ledger.fact_count(), 1)

    def test_existing_unrelated_database_is_not_modified(self):
        path = self.directory / "unrelated.sqlite3"
        connection = sqlite3.connect(str(path))
        connection.execute("CREATE TABLE notes(value TEXT)")
        connection.execute("INSERT INTO notes VALUES ('preserve')")
        connection.commit()
        connection.close()
        before = path.read_bytes()
        with self.assertRaises(WorkerError) as error:
            Ledger(path, self.settings.fingerprint)
        self.assertEqual(error.exception.code, "STATE_FORMAT_UNSUPPORTED")
        self.assertEqual(path.read_bytes(), before)

    def test_reference_registry_rejects_conflicting_device_mapping(self):
        refs = read(SAMPLES / "reference-data.json")
        refs["deviceMappings"].append(deepcopy(refs["deviceMappings"][0]))
        with self.assertRaises(WorkerError):
            PlateAdapter(refs)

    def test_config_rejects_unsupported_mapping_version(self):
        manifest = read(SAMPLES / "batches.json")
        manifest[0]["mappingVersion"] = "not-supported"
        manifest_path = self.directory / "batches.json"
        manifest_path.write_text(canonical(manifest), encoding="utf-8")
        config = read(CONFIG)
        config["batchesFile"] = str(manifest_path)
        config["referenceData"] = str(SAMPLES / "reference-data.json")
        config["statePath"] = str(self.state)
        config["outputRoot"] = str(self.directory / "reports")
        path = self.directory / "config.json"
        path.write_text(canonical(config), encoding="utf-8")
        with self.assertRaises(WorkerError) as error:
            load_settings(path)
        self.assertEqual(error.exception.code, "VERSION_UNSUPPORTED")

    def test_report_contains_traceable_quality_and_verified_hashes(self):
        for batch in self.settings.batches:
            self.run_batch(batch)
        target = self.directory / "report"
        summary = export_report(self.ledger, target)
        self.assertEqual(summary["totals"], {"ACCEPTED": 16, "DUPLICATE": 17, "REJECTED": 39})
        quality = read_lines(target / "quality.jsonl")
        self.assertEqual(len(quality), 43)
        self.assertEqual(sum(row["severity"] == "ERROR" for row in quality), 39)
        for row in quality:
            self.assertTrue(row["batchId"] and row["rowNumber"] and row["reasonCode"])
        for name, expected in read(target / "export.json")["files"].items():
            self.assertEqual(hashlib.sha256((target / name).read_bytes()).hexdigest(), expected)
        with self.assertRaises(WorkerError) as error:
            export_report(self.ledger, target)
        self.assertEqual(error.exception.code, "OUTPUT_EXISTS")

    def test_cli_rejects_output_escape_before_creating_state(self):
        state = self.directory / "unused.sqlite3"
        with redirect_stdout(io.StringIO()) as output:
            code = main(["--state", str(state), "--output", str(PACKAGE_ROOT.parent / "outside-report")])
        self.assertEqual(code, 2)
        self.assertFalse(state.exists())
        self.assertIn("PATH_OUTSIDE_ROOT", output.getvalue())

    def test_report_failure_can_retry_committed_batch_without_duplication(self):
        state = self.directory / "cli.sqlite3"
        args = ["--state", str(state), "--batch", "B01_INITIAL", "--output", str(self.directory / "report")]
        with patch("data_worker.cli.export_report", side_effect=OSError("Injected export failure")):
            with redirect_stdout(io.StringIO()):
                self.assertEqual(main(args), 2)
        with redirect_stdout(io.StringIO()) as output:
            self.assertEqual(main(args), 0)
        events = [json.loads(line) for line in output.getvalue().splitlines()]
        self.assertTrue(next(row for row in events if row["event"] == "batch.committed")["cached"])
        self.assertEqual(read(self.directory / "report/summary.json")["totalProcessingRows"], 34)

    def test_cli_restart_and_complete_retry_in_separate_processes(self):
        state = self.directory / "process.sqlite3"
        def invoke(name, selections):
            args = [sys.executable, str(PROJECT / "run_worker.py"), "--state", str(state),
                    "--output", str(self.directory / name)]
            for value in selections:
                args += ["--batch", value]
            result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding="utf-8", timeout=30)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertNotIn(self.raw["plateText"], result.stdout)
            return [json.loads(line) for line in result.stdout.splitlines()]
        invoke("initial", ["B01_INITIAL"])
        invoke("restart", ["B02_REPLAY", "B03_INCREMENTAL", "B04_SOURCE_NAMESPACE"])
        events = invoke("retry", [])
        self.assertTrue(all(event["cached"] for event in events if event["event"] == "batch.committed"))
        report = self.directory / "restart"
        golden_equal(self, read_lines(report / "rows.jsonl"), read_lines(report / "captures.jsonl"),
                     read(report / "summary.json")["batches"])
        for name in ("rows.jsonl", "captures.jsonl", "quality.jsonl", "summary.json"):
            self.assertEqual((report / name).read_bytes(), (self.directory / "retry" / name).read_bytes())


if __name__ == "__main__":
    unittest.main()
