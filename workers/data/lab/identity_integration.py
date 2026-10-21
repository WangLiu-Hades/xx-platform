"""Real PG identity/replay probes; lab task providers remain explicit stand-ins."""
from dataclasses import replace
import unittest

from data_worker.common import canonical, digest, read_json
from data_worker.pg.connection import connect, require_lab
from data_worker.pg.ingest import PgIngestor, StoragePlateAdapter
from data_worker.plate import VERSIONS
from .control import LabControl, LAB_INGEST_BUDGET
from .fixtures import seed_batch, publish_for_lab
from .integration import RealPostgresTests, SAMPLES, lease_for, read_lines, write_json


class LegacyStorageAdapter(StoragePlateAdapter):
    """Seed the released IDs, without changing the frozen inputs or expected data."""
    def normalize(self, raw, source, received_at):
        value = super().normalize(raw, source, received_at)
        value.record["captureId"] = self.identity("capture:" + source + ":" + raw["recordId"])
        value.source_event_id = self.identity("source-event:" + source + ":" + raw["recordId"])
        return value


class IdentityPostgresTests(unittest.TestCase):
    custom = RealPostgresTests.custom

    def setUp(self):
        self.db = connect(self.dsn)

    def tearDown(self):
        self.db.close()

    def prepare_sources(self, label):
        require_lab(self.db)
        self.sources = ("ID_" + label, "ID_" + label + ":B")
        refs = read_json(SAMPLES / "reference-data.json")
        for source in self.sources:
            refs["sources"].append(dict(refs["sources"][0], source=source))
            refs["deviceMappings"].append(dict(refs["deviceMappings"][0], source=source))
        self.refs = refs
        self.adapter = StoragePlateAdapter(refs)
        self.identity_settings = replace(self.settings, adapter=self.adapter,
            fingerprint=digest({"base": self.settings.fingerprint, "identityReferences": refs}))
        with self.db.transaction():
            for source in self.sources:
                self.db.execute("""
                    INSERT INTO capture.ingestion_source
                        (source_code,name,source_kind,adapter_code,protocol_version,enabled)
                    VALUES(%s,%s,'TEST_FILE','synthetic-plate-v0',%s,true)
                    """, (source, "Synthetic identity probe " + source, VERSIONS["schemaVersion"]))
                self.db.execute("""
                    INSERT INTO capture.source_mapping_version
                        (source_code,mapping_version,schema_version,mapping_definition,
                         dedup_rule_version,effective_at,enabled)
                    VALUES(%s,%s,%s,%s::jsonb,%s,clock_timestamp(),true)
                    """, (source, VERSIONS["mappingVersion"], VERSIONS["schemaVersion"],
                          canonical({"synthetic": True, "fingerprint": self.identity_settings.fingerprint}),
                          VERSIONS["dedupVersion"]))

    def writer(self, adapter=None):
        return PgIngestor(self.db, adapter or self.adapter, self.identity_settings, publish_for_lab,
                          control=LabControl(self.db), budget=LAB_INGEST_BUDGET)

    def batch(self, index, **changes):
        raw = read_lines(SAMPLES / "raw/initial.jsonl")[0]
        raw.update(recordId=("B:C", "C")[index], captureTime="2026-10-18T02:00:00Z")
        return self.custom("IDENTITY-" + str(index), rows=[raw], source=self.sources[index], **changes)

    def ingest_new(self, batch, adapter=None):
        seed_batch(self.db, self.identity_settings, batch, self.assets, self.owner)
        return self.writer(adapter).ingest(batch, lease_for(batch))

    def facts(self):
        return self.db.execute("""
            SELECT s.source_code,s.source_record_key,s.id::text AS source_event_id,
                   e.id::text AS capture_id,s.payload_digest,e.batch_id::text,
                   p.capture_id::text AS plate_capture_id
            FROM capture.source_event s
            JOIN capture.capture_event e ON e.source_event_id=s.id
            JOIN capture.plate_record p ON p.capture_id=e.id
            WHERE s.source_code=ANY(%s::text[])
            ORDER BY s.source_code,s.source_record_key
            """, (list(self.sources),)).fetchall()

    def reconnect(self):
        self.db.close()
        self.db = connect(self.dsn)

    def test_new_boundary_pairs_store_two_facts_and_replay(self):
        self.prepare_sources("FRESH")
        batches = [self.batch(0), self.batch(1)]
        for batch in batches:
            summary, rows, cached = self.ingest_new(batch)
            self.assertEqual((summary["acceptedCount"], rows[0]["outcome"], cached), (1, "ACCEPTED", False))
        before = self.facts()
        self.assertEqual(len(before), 2)
        self.assertEqual(len({f["capture_id"] for f in before}), 2)
        self.assertEqual(len({f["source_event_id"] for f in before}), 2)
        self.reconnect()
        for index, original in enumerate(batches):
            self.assertTrue(self.writer().ingest(original, lease_for(original))[2])
            replay = self.batch(index, mode="REPLAY", originalBatchId=original.id)
            summary, rows, cached = self.ingest_new(replay)
            self.assertEqual((summary["duplicateCount"], summary["acceptedCount"], cached), (1, 0, False))
            expected = next(f for f in before if f["source_code"] == self.sources[index])
            self.assertEqual((rows[0]["captureId"], rows[0]["sourceEventId"]),
                             (expected["capture_id"], expected["source_event_id"]))
        self.assertEqual(self.facts(), before)
        write_json(self.output / "identity-fresh.json", {"status": "PASS", "facts": before,
                   "newConnectionReplay": True, "uniqueFactsAfterReplay": len(self.facts())})

    def legacy_replay(self, label, index):
        self.prepare_sources(label)
        original = self.batch(index)
        self.ingest_new(original, LegacyStorageAdapter(self.refs))
        before = self.facts()[0]
        raw = read_lines(original.input_path)[0]
        expected = self.adapter.normalize(raw, self.sources[index], original.metadata["receivedAt"])
        self.assertNotEqual(before["capture_id"], expected.record["captureId"])
        self.assertNotEqual(before["source_event_id"], expected.source_event_id)
        self.reconnect()
        replay = self.batch(index, mode="REPLAY", originalBatchId=original.id)
        summary, rows, cached = self.ingest_new(replay)
        self.assertEqual((summary["duplicateCount"], summary["acceptedCount"], cached), (1, 0, False))
        self.assertEqual((rows[0]["captureId"], rows[0]["sourceEventId"]),
                         (before["capture_id"], before["source_event_id"]))
        summary, _, _ = self.ingest_new(self.batch(1 - index))
        self.assertEqual(summary["acceptedCount"], 1)
        after = self.facts()
        self.assertIn(before, after)
        self.assertEqual(len(after), 2)
        self.assertEqual(len({f["capture_id"] for f in after}), 2)
        self.assertEqual(len({f["source_event_id"] for f in after}), 2)
        # A changed payload must still be rejected under the existing natural key.
        conflict = self.batch(index)
        changed = dict(raw, plateColor="CHANGED")
        conflict.input_path.write_bytes((canonical(changed) + "\n").encode("utf-8"))
        summary, rows, _ = self.ingest_new(conflict)
        self.assertEqual(summary["rejectedCount"], 1)
        self.assertEqual(rows[0]["reasonCode"], "FIXTURE_KEY_CONFLICT")
        self.assertEqual(self.facts(), after)
        write_json(self.output / ("identity-" + label.lower() + ".json"),
                   {"status": "PASS", "legacyFact": before, "facts": after,
                    "legacyIdsRetained": True, "changedPayloadRejected": True})

    def test_existing_legacy_record_colon_replays_with_persisted_ids(self):
        self.legacy_replay("LEGACY_RECORD", 0)

    def test_existing_legacy_source_colon_replays_with_persisted_ids(self):
        self.legacy_replay("LEGACY_SOURCE", 1)
