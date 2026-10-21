"""Identity boundary regression; synthetic inputs, immutable fixture oracle."""
from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest
import uuid

from data_worker.common import canonical
from data_worker.pipeline import process_batch
from data_worker.plate import PlateAdapter
from data_worker.pg.ingest import StoragePlateAdapter
from data_worker.settings import Batch, PACKAGE_ROOT, OUTPUT_ROOT, load_settings
from data_worker.store import Ledger

SAMPLES = PACKAGE_ROOT / "fixtures/plate-v0"
TEST_ROOT = OUTPUT_ROOT / "identity-tests"
RECEIVED = "2026-10-16T02:00:00Z"


def references(sources):
    refs = json.loads((SAMPLES / "reference-data.json").read_text(encoding="utf-8"))
    refs["sources"] = [dict(refs["sources"][0], source=source) for source in sources]
    refs["deviceMappings"] = [dict(refs["deviceMappings"][0], source=source) for source in sources]
    return refs


class LegacyPlateAdapter(PlateAdapter):
    """Reproduce the released recipe only to seed pre-upgrade facts."""
    def normalize(self, raw, source, received_at):
        value = super().normalize(raw, source, received_at)
        value.record["captureId"] = self.identity("capture:" + source + ":" + raw["recordId"])
        value.source_event_id = self.identity("source-event:" + source + ":" + raw["recordId"])
        return value


class IdentityTests(unittest.TestCase):
    def setUp(self):
        TEST_ROOT.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix="case-", dir=str(TEST_ROOT))
        self.directory = Path(self.temp.name).resolve()
        self.settings = load_settings(PACKAGE_ROOT / "config.example.json")
        self.raw = json.loads((SAMPLES / "raw/initial.jsonl").read_text(encoding="utf-8").splitlines()[0])
        self.refs = references(["A", "A:B"])
        self.adapter = PlateAdapter(self.refs)
        self.state = self.directory / "state.sqlite3"
        self.ledger = Ledger(self.state, "identity-boundary-test")

    def tearDown(self):
        self.ledger.close()
        self.directory.relative_to(TEST_ROOT.resolve())
        self.assertNotEqual(self.directory, TEST_ROOT.resolve())
        self.temp.cleanup()

    def restart(self):
        self.ledger.close()
        self.ledger = Ledger(self.state, "identity-boundary-test")
        self.adapter = PlateAdapter(self.refs)

    def batch(self, source, key, **changes):
        metadata = deepcopy(self.settings.batches[0].metadata)
        metadata.update(batchId=str(uuid.uuid4()), taskId=str(uuid.uuid4()),
                        name="IDENTITY", source=source, mode="INGEST", originalBatchId=None)
        metadata.update(changes)
        path = self.directory / (metadata["batchId"] + ".jsonl")
        path.write_bytes((canonical(dict(self.raw, recordId=key)) + "\n").encode("utf-8"))
        metadata["inputFile"] = path.name
        return Batch(metadata, path)

    def run_batch(self, batch, adapter=None):
        return process_batch(self.ledger, adapter or self.adapter, batch, 65536)

    def ids(self, source, key, adapter=None):
        value = (adapter or self.adapter).normalize(dict(self.raw, recordId=key), source, RECEIVED)
        return value.record["captureId"], value.source_event_id

    def test_reported_source_record_boundary_collision(self):
        left, right = self.ids("A", "B:C"), self.ids("A:B", "C")
        self.assertNotEqual(left[0], right[0])
        self.assertNotEqual(left[1], right[1])

    def test_all_separator_partitions_are_distinct(self):
        text = "A::B:C::D"
        pairs = [(text[:i], text[i + 1:]) for i, c in enumerate(text) if c == ":"]
        adapter = PlateAdapter(references([source for source, _ in pairs]))
        ids = [identity for source, key in pairs for identity in self.ids(source, key, adapter)]
        self.assertEqual(len(set(ids)), 2 * len(pairs))

    def test_json_escaping_unicode_and_delimiters_are_distinct(self):
        sources = ["A", "A:B", "A%3AB", "源:甲", 'A:"B\\C', '["A","B"]']
        keys = ["C", "B:C", "%3A", '["B","C"]', 'B:"C\\D', "行:\nC", "é:C", "e\u0301:C"]
        adapter = PlateAdapter(references(sources))
        ids = [value for source in sources for key in keys for value in self.ids(source, key, adapter)]
        self.assertEqual(len(set(ids)), 2 * len(sources) * len(keys))

    def test_new_and_legacy_lookalike_domains_do_not_alias(self):
        pairs = [("A", "B:C"), ("A:B", "C"), ("A", "B"),
                 ("plate-id-v2", '["capture","A","B:C"]'),
                 ("capture-v2", '["A","B:C"]'), ("source-event", "A:B:C")]
        adapter = PlateAdapter(references(sorted({source for source, _ in pairs})))
        ids = [value for source, key in pairs for value in self.ids(source, key, adapter)]
        self.assertEqual(len(set(ids)), 2 * len(pairs))

    def test_unambiguous_legacy_ids_and_frozen_oracle_are_unchanged(self):
        legacy = LegacyPlateAdapter(self.refs)
        self.assertEqual(self.ids("A", "B"), self.ids("A", "B", legacy))
        adapter = self.settings.adapter
        for line in (SAMPLES / "expected/captures.jsonl").read_text(encoding="utf-8").splitlines():
            fact = json.loads(line)
            self.assertEqual(self.ids(fact["source"], fact["sourceRecordKey"], adapter),
                             (fact["record"]["captureId"], fact["sourceEventId"]))

    def test_storage_and_offline_adapters_are_deterministic(self):
        storage = StoragePlateAdapter(self.refs)
        for source, key in [("A", "B:C"), ("A:B", "C"), ("A", "B")]:
            expected = self.ids(source, key)
            self.assertEqual(expected, self.ids(source, key, PlateAdapter(self.refs)))
            self.assertEqual(expected, self.ids(source, key, storage))
            self.assertTrue(all(uuid.UUID(value).version == 5 for value in expected))
        changed_seed = PlateAdapter(dict(self.refs, seed=self.refs["seed"] + 1))
        self.assertNotEqual(self.ids("A", "B:C"), self.ids("A", "B:C", changed_seed))

    def test_both_events_persist_and_replay_after_restart(self):
        batches = [self.batch("A", "B:C"), self.batch("A:B", "C")]
        for batch in batches:
            self.assertEqual(self.run_batch(batch)[0]["acceptedCount"], 1)
        before = self.ledger.snapshot()[1]
        self.restart()
        for batch in batches:
            self.assertTrue(self.run_batch(batch)[1])
            replay = self.batch(batch.metadata["source"],
                                "B:C" if batch.metadata["source"] == "A" else "C",
                                mode="REPLAY", originalBatchId=batch.id)
            summary, cached = self.run_batch(replay)
            self.assertFalse(cached)
            self.assertEqual((summary["acceptedCount"], summary["duplicateCount"]), (0, 1))
        self.assertEqual(self.ledger.snapshot()[1], before)
        self.assertEqual(self.ledger.fact_count(), 2)

    def assert_legacy_replay(self, source, key, other_source, other_key):
        old = self.batch(source, key)
        self.run_batch(old, LegacyPlateAdapter(self.refs))
        before = self.ledger.get_fact(source, key)
        self.restart()
        replay = self.batch(source, key, mode="REPLAY", originalBatchId=old.id)
        summary, _ = self.run_batch(replay)
        self.assertEqual(summary["duplicateCount"], 1)
        self.assertEqual(self.ledger.get_fact(source, key), before)
        row = self.ledger.snapshot()[0][-1]
        self.assertEqual((row["captureId"], row["sourceEventId"]),
                         (before["record"]["captureId"], before["sourceEventId"]))
        summary, _ = self.run_batch(self.batch(other_source, other_key))
        self.assertEqual(summary["acceptedCount"], 1)
        self.assertEqual(self.ledger.fact_count(), 2)

    def test_existing_legacy_record_with_colon_keeps_id_on_replay(self):
        self.assert_legacy_replay("A", "B:C", "A:B", "C")

    def test_existing_legacy_source_with_colon_keeps_id_on_replay(self):
        self.assert_legacy_replay("A:B", "C", "A", "B:C")

    def test_changed_payload_for_colon_key_is_rejected_without_overwrite(self):
        self.run_batch(self.batch("A", "B:C"))
        before = self.ledger.get_fact("A", "B:C")
        batch = self.batch("A", "B:C")
        batch.input_path.write_bytes((canonical(dict(self.raw, recordId="B:C", plateColor="CHANGED")) + "\n").encode())
        summary, _ = self.run_batch(batch)
        self.assertEqual((summary["rejectedCount"], summary["acceptedCount"]), (1, 0))
        self.assertEqual(self.ledger.snapshot()[0][-1]["reasonCode"], "FIXTURE_KEY_CONFLICT")
        self.assertEqual(self.ledger.get_fact("A", "B:C"), before)
