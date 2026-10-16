"""Actual PostgreSQL tests. Imported only by the real database runner, never the unit runner."""
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from copy import deepcopy
from dataclasses import replace
import hashlib
import io
import json
from pathlib import Path
import runpy
import threading
import unittest
from unittest.mock import patch
import uuid

from data_worker.common import WorkerError, canonical, read_json
from data_worker.pg.connection import connect
from data_worker.pg.ingest import Lease, PgIngestor, StoragePlateAdapter
from data_worker.pg.query import TrustedScope, QuerySpec, QueryBudget, CaptureQuerySession
from data_worker.settings import Batch, PACKAGE_ROOT, load_settings, REPO_ROOT
from .fixtures import LAB_WORKER, seed_references, seed_batch, publish_for_lab

PROJECT = Path(__file__).resolve().parents[1]
SAMPLES = PACKAGE_ROOT / "fixtures/plate-v0"
BUDGET = QueryBudget(86400 * 31, 5000, 60)  # Synthetic test budget, not a signed business default.


def read_lines(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_lines(path, values):
    path.write_text("".join(canonical(value) + "\n" for value in values), encoding="utf-8")


class ProbeRollback(Exception):
    pass


@contextmanager
def rollback_probe(db):
    try:
        with db.transaction():
            yield
            raise ProbeRollback()
    except ProbeRollback:
        pass


def make_writer(db, settings):
    refs = read_json(SAMPLES / "reference-data.json")
    return PgIngestor(db, StoragePlateAdapter(refs), settings, publish_for_lab)


def lease_for(batch):
    return Lease(batch.metadata["taskId"], LAB_WORKER, 1, 1)


def prepare_golden(dsn, settings, assets, output, progress):
    db = connect(dsn)
    try:
        progress("seed_reference_data")
        owner = seed_references(db, settings, assets)
        progress("seed_task_batches")
        for batch in settings.batches:
            seed_batch(db, settings, batch, assets, owner)
        results, rows = [], []
        # Every batch runs on a newly opened connection, so replay cannot rely on process memory.
        for batch in settings.batches:
            db.close()
            db = connect(dsn)
            progress("ingest_golden_batch", batchName=batch.metadata["name"])
            summary, actual, cached = make_writer(db, settings).ingest(batch, lease_for(batch))
            if cached:
                raise AssertionError("Fresh database unexpectedly returned a cached batch")
            results.append(summary)
            rows.extend(actual)
        progress("export_golden_facts")
        facts = make_writer(db, settings).facts()
        write_lines(output / "rows.jsonl", rows)
        write_lines(output / "captures.jsonl", facts)
        write_json(output / "batches.json", results)
        quality = db.execute("""
            SELECT q.issue_code,q.field_name,q.reason,i.batch_id::text,i.row_number,i.processing_status
            FROM capture.quality_issue q JOIN capture.ingestion_item i ON i.id=q.ingestion_item_id
            ORDER BY i.batch_id,i.row_number,q.issue_code
            """).fetchall()
        write_lines(output / "quality.jsonl", quality)
        return owner, rows, facts, results
    finally:
        db.close()


class RealPostgresTests(unittest.TestCase):
    dsn = None

    def setUp(self):
        self.db = connect(self.dsn)
        self.writer = make_writer(self.db, self.settings)
        self.refs = read_json(SAMPLES / "reference-data.json")
        self.scope = TrustedScope(self.owner, "synthetic-scope-v1", (self.refs["sites"][0]["id"],), ("PLATE",))
        self.request = {"dataType": "PLATE", "timeRange": {"from": "2026-10-16T00:00:00Z",
                                                        "to": "2026-10-16T01:00:00Z"}, "pageSize": 3}

    def tearDown(self):
        self.db.close()

    def custom(self, label, rows=None, **changes):
        raw = read_lines(SAMPLES / "raw/initial.jsonl")[0]
        raw.update(recordId="INTEGRATION-" + label, captureTime="2026-10-18T02:00:00Z")
        values = rows if rows is not None else [raw]
        metadata = deepcopy(self.settings.batches[0].metadata)
        metadata.update(batchId=str(uuid.uuid4()), taskId=str(uuid.uuid4()), name=label, originalBatchId=None, mode="INGEST")
        metadata.update(changes)
        path = self.assets / (metadata["batchId"] + ".jsonl")
        write_lines(path, values)
        metadata["inputFile"] = path.name
        return Batch(metadata, path)

    def all_pages(self, session, scope):
        pages, ids, cursor = [], [], None
        for _ in range(100):
            page = session.page(scope, cursor)
            current = [item["captureId"] for item in page["items"]]
            pages.append(current)
            ids.extend(current)
            cursor = page["nextCursor"]
            if cursor is None:
                return pages, ids
        self.fail("Pagination did not terminate")

    def test_golden_rows_facts_counts_and_actual_dtos(self):
        expected_rows = read_lines(SAMPLES / "expected/rows.jsonl")
        self.assertEqual(len(self.golden_rows), len(expected_rows))
        for actual, expected in zip(self.golden_rows, expected_rows):
            self.assertEqual({k: actual[k] for k in expected if k != "caseId"},
                             {k: v for k, v in expected.items() if k != "caseId"})
        fact_index = lambda items: {(f["source"], f["sourceRecordKey"]):
                                   {k: v for k, v in f.items() if k != "caseId"} for f in items}
        self.assertEqual(fact_index(self.golden_facts), fact_index(read_lines(SAMPLES / "expected/captures.jsonl")))
        self.assertEqual(self.golden_batches, read_json(SAMPLES / "expected/summary.json")["batches"])
        schemas = read_json(REPO_ROOT / "apps/web/src/api/contract.json")["components"]["schemas"]
        validate = runpy.run_path(str(PACKAGE_ROOT / "lab/contract_validation.py"))["validate_dto"]
        for fact in self.golden_facts:
            validate(fact["record"], schemas["PlateCaptureRecord"], schemas)
        for table, expected in (("capture.source_event", 16), ("capture.capture_event", 16),
                                ("capture.plate_record", 16), ("capture.ingestion_item", 72),
                                ("capture.quality_issue", 43), ("analysis.task_row_error", 39)):
            self.assertEqual(self.db.execute("SELECT count(*) AS n FROM " + table).fetchone()["n"], expected)

    def test_query_eight_frozen_cases_and_query_plan(self):
        outputs = []
        for case in read_json(SAMPLES / "expected/queries.json"):
            request = dict(self.request, timeRange={"from": case["from"], "to": case["to"]}, pageSize=case["pageSize"])
            if case["targetId"] is not None:
                request["targetIds"] = [case["targetId"]]
            scope = TrustedScope(self.owner, "synthetic-scope-v1", tuple(case["authorizedSiteIds"]),
                                 tuple(case["authorizedDataTypes"]))
            with CaptureQuerySession(self.db, QuerySpec.parse(request), scope, BUDGET) as session:
                plan = session.page(scope, explain=True)
                pages, ids = self.all_pages(session, scope)
            self.assertEqual(ids, case["expectedCaptureIds"], case["name"])
            self.assertEqual(pages, case["expectedPages"], case["name"])
            outputs.append({"name": case["name"], "captureIds": ids, "pages": pages, "plan": plan})
        write_json(self.output / "queries-and-plans.json", outputs)

    def test_query_sort_options_and_authority(self):
        from datetime import datetime
        instant = lambda value: datetime.fromisoformat(value.replace("Z", "+00:00"))
        facts = [f["record"] for f in self.golden_facts if f["record"]["siteId"] in self.scope.site_ids and
                 instant("2026-10-16T00:00:00Z") <= instant(f["record"]["captureTime"]) < instant("2026-10-16T01:00:00Z")]
        # Datetime ordering, not lexical RFC3339 ordering, for fractional seconds.
        from datetime import datetime
        for field in ("captureTime", "captureId"):
            for order in ("ASC", "DESC"):
                key = ((lambda r: (datetime.fromisoformat(r["captureTime"].replace("Z", "+00:00")), r["captureId"]))
                       if field == "captureTime" else lambda r: r["captureId"])
                expected = [r["captureId"] for r in sorted(facts, key=key, reverse=order == "DESC")]
                spec = QuerySpec.parse(dict(self.request, sortBy=field, sortOrder=order))
                with CaptureQuerySession(self.db, spec, self.scope, BUDGET) as session:
                    self.assertEqual(self.all_pages(session, self.scope)[1], expected)
        foreign_site = self.refs["sites"][1]["id"]
        with CaptureQuerySession(self.db, QuerySpec.parse(dict(self.request, siteIds=[foreign_site])),
                                 self.scope, BUDGET) as session:
            self.assertEqual(session.page(self.scope)["items"], [])

    def test_cursor_tamper_scope_change_and_new_session_are_rejected(self):
        spec = QuerySpec.parse(self.request)
        with CaptureQuerySession(self.db, spec, self.scope, BUDGET) as session:
            token = session.page(self.scope)["nextCursor"]
            self.assertIsNotNone(token)
            with self.assertRaises(WorkerError):
                session.page(self.scope, token + "x")
            with self.assertRaises(WorkerError):
                session.page(replace(self.scope, version="revoked"), token)
        with CaptureQuerySession(self.db, spec, self.scope, BUDGET) as session:
            with self.assertRaises(WorkerError):
                session.page(self.scope, token)

    def test_same_batch_retry_and_changed_input(self):
        before = self.writer.fact_count()
        batch = self.settings.batches[0]
        _, rows, cached = self.writer.ingest(batch, lease_for(batch))
        self.assertTrue(cached)
        self.assertEqual(len(rows), 34)
        self.assertEqual(self.writer.fact_count(), before)
        copy = self.assets / "changed-original-copy.jsonl"
        copy.write_bytes(batch.input_path.read_bytes() + b"\n")
        with self.assertRaises(WorkerError) as error:
            self.writer.ingest(Batch(batch.metadata, copy), lease_for(batch))
        self.assertEqual(error.exception.code, "BATCH_INPUT_CHANGED")

    def test_transaction_failure_rolls_back_then_retry_succeeds(self):
        with rollback_probe(self.db):
            batch = self.custom("ROLLBACK")
            seed_batch(self.db, self.settings, batch, self.assets, self.owner)
            original = self.writer._write_row
            def fail(row, lease, occurred):
                original(row, lease, occurred)
                raise RuntimeError("Injected worker interruption after database writes")
            with patch.object(self.writer, "_write_row", side_effect=fail), self.assertRaises(RuntimeError):
                self.writer.ingest(batch, lease_for(batch))
            self.assertEqual(self.db.execute("SELECT count(*) AS n FROM capture.capture_event WHERE batch_id=%s::uuid",
                                             (batch.id,)).fetchone()["n"], 0)
            self.assertEqual(self.db.execute("SELECT count(*) AS n FROM capture.ingestion_item WHERE batch_id=%s::uuid",
                                             (batch.id,)).fetchone()["n"], 0)
            summary, _, _ = self.writer.ingest(batch, lease_for(batch))
            self.assertEqual(summary["acceptedCount"], 1)

    def test_invalid_storage_values_are_isolated_and_do_not_claim_key(self):
        with rollback_probe(self.db):
            raw = read_lines(SAMPLES / "raw/initial.jsonl")[0]
            raw["recordId"] = "STORAGE-TEST"
            batch = self.custom("STORAGE", [dict(raw, plateColor="x" * 201), raw])
            seed_batch(self.db, self.settings, batch, self.assets, self.owner)
            summary, rows, _ = self.writer.ingest(batch, lease_for(batch))
            self.assertEqual((summary["acceptedCount"], summary["rejectedCount"]), (1, 1))
            self.assertEqual(rows[0]["reasonCode"], "STORAGE_FIELD_TOO_LONG")

    def test_source_file_hash_mismatch_is_rejected_before_claiming_events(self):
        with rollback_probe(self.db):
            batch = self.custom("SOURCE-FILE-MISMATCH")
            seed_batch(self.db, self.settings, batch, self.assets, self.owner)
            self.db.execute("""
                UPDATE core.file_asset SET sha256=%s
                WHERE id=(SELECT source_file_id FROM capture.ingestion_batch WHERE id=%s::uuid)
                """, ("0" * 64, batch.id))
            with self.assertRaises(WorkerError) as error:
                self.writer.ingest(batch, lease_for(batch))
            self.assertEqual(error.exception.code, "PG_SOURCE_FILE_INVALID")
            self.assertEqual(self.db.execute("SELECT count(*) AS n FROM capture.capture_event WHERE batch_id=%s::uuid",
                                             (batch.id,)).fetchone()["n"], 0)

    def test_lease_cancel_expiry_generation_and_attempt_guards(self):
        cases = [("cancel_requested_at=clock_timestamp()", "CANCELLED"),
                 ("lease_until=clock_timestamp()-interval '1 second'", "EXPIRED"),
                 ("lease_generation=2", "STALE"), ("current_attempt_no=2", "ATTEMPT")]
        for assignment, label in cases:
            with self.subTest(label=label), rollback_probe(self.db):
                batch = self.custom(label)
                seed_batch(self.db, self.settings, batch, self.assets, self.owner)
                self.db.execute("UPDATE analysis.async_task SET " + assignment + " WHERE id=%s::uuid",
                                (batch.metadata["taskId"],))
                with self.assertRaises(WorkerError) as error:
                    self.writer.ingest(batch, lease_for(batch))
                self.assertEqual(error.exception.code, "TASK_LEASE_INVALID")

    def test_publisher_expiry_rolls_back_domain_and_task_together(self):
        with rollback_probe(self.db):
            batch = self.custom("PUBLISH-EXPIRED")
            seed_batch(self.db, self.settings, batch, self.assets, self.owner)
            def expire_and_publish(db, lease, current):
                db.execute("UPDATE analysis.async_task SET lease_until=clock_timestamp()-interval '1 second' WHERE id=%s::uuid",
                           (lease.task_id,))
                return publish_for_lab(db, lease, current)
            writer = PgIngestor(self.db, self.writer.adapter, self.settings, expire_and_publish)
            with self.assertRaises(WorkerError):
                writer.ingest(batch, lease_for(batch))
            self.assertEqual(self.db.execute("SELECT status FROM capture.ingestion_batch WHERE id=%s::uuid",
                                             (batch.id,)).fetchone()["status"], "RUNNING")
            self.assertEqual(self.db.execute("SELECT count(*) AS n FROM capture.capture_event WHERE batch_id=%s::uuid",
                                             (batch.id,)).fetchone()["n"], 0)

    def test_z_concurrent_batches_with_same_event_create_one_fact(self):
        first = self.custom("CONCURRENT-A")
        raw = read_lines(first.input_path)
        second = self.custom("CONCURRENT-B", raw)
        for batch in (first, second):
            seed_batch(self.db, self.settings, batch, self.assets, self.owner)
        before = self.writer.fact_count()
        barrier = threading.Barrier(2)
        def execute(batch):
            db = connect(self.dsn)
            try:
                barrier.wait(timeout=10)
                return make_writer(db, self.settings).ingest(batch, lease_for(batch))[0]
            finally:
                db.close()
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(execute, [first, second]))
        self.assertEqual(sum(r["acceptedCount"] for r in results), 1)
        self.assertEqual(sum(r["duplicateCount"] for r in results), 1)
        self.assertEqual(self.writer.fact_count(), before + 1)
        write_json(self.output / "concurrency-probe.json", results)

    def test_zz_repeatable_read_snapshot_excludes_concurrent_late_arrival(self):
        expected = read_json(SAMPLES / "expected/queries.json")[0]["expectedCaptureIds"]
        spec = QuerySpec.parse(self.request)
        with CaptureQuerySession(self.db, spec, self.scope, BUDGET) as session:
            first = session.page(self.scope)
            raw = read_lines(SAMPLES / "raw/initial.jsonl")[0]
            raw.update(recordId="SNAPSHOT-LATE-ARRIVAL", captureTime="2026-10-16T00:22:00Z")
            batch = self.custom("SNAPSHOT", [raw])
            other = connect(self.dsn)
            try:
                seed_batch(other, self.settings, batch, self.assets, self.owner)
                make_writer(other, self.settings).ingest(batch, lease_for(batch))
            finally:
                other.close()
            ids = [r["captureId"] for r in first["items"]]
            token = first["nextCursor"]
            while token:
                page = session.page(self.scope, token)
                ids.extend(r["captureId"] for r in page["items"])
                token = page["nextCursor"]
            self.assertEqual(ids, expected)
        with CaptureQuerySession(self.db, spec, self.scope, BUDGET) as session:
            self.assertEqual(len(self.all_pages(session, self.scope)[1]), len(expected) + 1)
        write_json(self.output / "snapshot-probe.json", {"originalIds": expected, "snapshotIds": ids,
                                                       "newSessionCount": len(expected) + 1})


def run_integration(dsn, assets, output, progress=None):
    progress = progress or (lambda *args, **kwargs: None)
    settings = load_settings(PROJECT / "config.example.json")
    owner, rows, facts, batches = prepare_golden(dsn, settings, assets, output, progress)
    RealPostgresTests.dsn = dsn
    RealPostgresTests.settings, RealPostgresTests.assets, RealPostgresTests.output = settings, assets, output
    RealPostgresTests.owner = owner
    RealPostgresTests.golden_rows, RealPostgresTests.golden_facts, RealPostgresTests.golden_batches = rows, facts, batches
    stream = io.StringIO()
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(RealPostgresTests)
    progress("run_database_tests", preparedTests=suite.countTestCases())
    result = unittest.TextTestRunner(stream=stream, verbosity=2).run(suite)
    (output / "integration-tests.txt").write_text(stream.getvalue(), encoding="utf-8")
    progress("collect_database_versions")
    db = connect(dsn)
    try:
        versions = db.execute("SELECT version() AS postgres,postgis_full_version() AS postgis").fetchone()
        final_facts = make_writer(db, settings).fact_count()
    finally:
        db.close()
    return {"status": "PASS" if result.wasSuccessful() and not result.skipped else "FAIL",
            "testsRun": result.testsRun, "failures": len(result.failures), "errors": len(result.errors),
            "skipped": len(result.skipped), "postgresExecuted": True, "versions": versions,
            "goldenProcessingRows": len(rows), "goldenFacts": len(facts), "goldenQueryCases": 8,
            "finalFactsIncludingConcurrencyProbes": final_facts,
            "taskPublisher": "isolated test stand-in, not DEV-01 production H04"}
