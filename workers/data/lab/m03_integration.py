"""M03 real-PostgreSQL probes using explicitly isolated task-control fixtures."""
from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
import json
import os
import subprocess
import sys
import threading
import unittest
import uuid

from data_worker.common import WorkerError, read_json
from data_worker.pg.connection import connect
from data_worker.pg.execution import ControlPoint
from data_worker.pg.ingest import Lease, PgIngestor
from data_worker.pg.ingestion_reads import (
    TrustedBatchScope, IngestionReadBudget, IngestionReadSession, BatchListSpec, QualitySpec,
    SYNTHETIC_QUALITY_POLICY, synthetic_error_projection)
from data_worker.settings import PACKAGE_ROOT, REPO_ROOT
from .control import LabControl, LAB_INGEST_BUDGET
from .fixtures import seed_batch, publish_for_lab, LAB_WORKER
from .integration import RealPostgresTests, make_writer, lease_for, read_lines, write_json
from .contract_validation import validate_dto


class M03PostgresTests(unittest.TestCase):
    custom = RealPostgresTests.custom

    def setUp(self):
        self.db = connect(self.dsn)
        self.writer = make_writer(self.db, self.settings)
        self.scope = TrustedBatchScope(self.owner, "synthetic-complete-batch-grant-v1",
                                       tuple(b.id for b in self.settings.batches))
        self.read_budget = IngestionReadBudget(3000, 30, 86400 * 7, 1000)
        self.schemas = read_json(REPO_ROOT / "apps/web/src/api/contract.json")["components"]["schemas"]

    def tearDown(self):
        self.db.close()

    def reader(self, scope=None, **options):
        return IngestionReadSession(self.db, scope or self.scope, self.read_budget,
            quality_policy=SYNTHETIC_QUALITY_POLICY, error_projection=synthetic_error_projection, **options)

    def facts_for(self, batch):
        return self.db.execute("SELECT count(*) AS n FROM capture.capture_event WHERE batch_id=%s::uuid",
                               (batch.id,)).fetchone()["n"]

    def paused(self, label, action, phase="PREPARING"):
        batch = self.custom(label)
        seed_batch(self.db, self.settings, batch, self.assets, self.owner, lease_seconds=10)
        entered, release = threading.Event(), threading.Event()
        budget = replace(LAB_INGEST_BUDGET, checkpoint_rows=1)
        def worker():
            db, control_db = connect(self.dsn), connect(self.dsn)
            try:
                base = LabControl(control_db)
                paused_once = False
                def control(lease, point):
                    nonlocal paused_once
                    base(lease, point)
                    if not paused_once and point.phase == phase and point.processed_rows >= 1:
                        paused_once = True
                        entered.set()
                        if not release.wait(timeout=10):
                            raise RuntimeError("Control probe was not released")
                    return True
                writer = PgIngestor(db, self.writer.adapter, self.settings, publish_for_lab,
                                    control=control, budget=budget)
                return writer.ingest(batch, lease_for(batch))
            finally:
                control_db.close()
                db.close()
        with ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(worker)
            try:
                self.assertTrue(entered.wait(timeout=5), "Worker did not reach a preparation checkpoint")
                action(batch)
            finally:
                release.set()
            try:
                result = future.result(timeout=10)
                return batch, result, None
            except WorkerError as exc:
                return batch, None, exc

    def request_cancel(self, batch):
        with self.db.transaction():
            self.db.execute("SELECT set_config('lock_timeout','300',true),set_config('statement_timeout','1000',true)")
            self.db.execute("UPDATE analysis.async_task SET cancel_requested_at=clock_timestamp(),version=version+1 WHERE id=%s::uuid",
                            (batch.metadata["taskId"],))

    def replace_attempt_for_lab(self, batch):
        """A recovery stand-in, not the DEV-01 task service or a general recovery endpoint."""
        task = batch.metadata["taskId"]
        with self.db.transaction():
            self.db.execute("SELECT set_config('lock_timeout','300',true),set_config('statement_timeout','1000',true)")
            self.db.execute("SELECT id FROM analysis.async_task WHERE id=%s::uuid FOR UPDATE", (task,))
            self.db.execute("UPDATE analysis.async_task SET lease_until=clock_timestamp()-interval '1 second' WHERE id=%s::uuid",
                            (task,))
            self.db.execute("""UPDATE analysis.task_attempt SET result='FAILED',finished_at=clock_timestamp(),
                            error_code='LAB_WORKER_EXPIRED' WHERE task_id=%s::uuid AND attempt_no=1""", (task,))
            self.db.execute("""UPDATE analysis.async_task SET status='QUEUED',lease_until=NULL,lease_owner=NULL,
                            version=version+1 WHERE id=%s::uuid""", (task,))
            self.db.execute("""UPDATE analysis.async_task SET status='RUNNING',current_attempt_no=2,lease_generation=2,
                            lease_owner=%s,lease_until=clock_timestamp()+interval '30 seconds',version=version+1
                            WHERE id=%s::uuid""", (LAB_WORKER, task))
            self.db.execute("""INSERT INTO analysis.task_attempt(task_id,attempt_no,started_at,worker_id,lease_generation)
                            VALUES(%s::uuid,2,clock_timestamp(),%s,2)""", (task, LAB_WORKER))
        return Lease(task, LAB_WORKER, 2, 2)

    def test_cancel_during_preparation_is_recorded_without_waiting_for_batch_lock(self):
        batch, result, error = self.paused("M03-CANCEL-DURING", self.request_cancel)
        self.assertIsNone(result)
        self.assertEqual(error.code, "TASK_LEASE_INVALID")
        self.assertEqual(self.facts_for(batch), 0)
        self.assertEqual(self.db.execute("SELECT count(*) AS n FROM capture.ingestion_item WHERE batch_id=%s::uuid",
                                         (batch.id,)).fetchone()["n"], 0)
        write_json(self.output / "m03-runtime-cancel.json", {"batchId": batch.id, "facts": 0,
            "otherConnectionCancelCommitted": True, "workerError": error.code, "phase": "PREPARING"})

    def test_cancel_after_preparation_is_rechecked_before_publication(self):
        batch, result, error = self.paused("M03-CANCEL-BOUNDARY", self.request_cancel, "BEFORE_PUBLISH")
        self.assertIsNone(result)
        self.assertEqual(error.code, "TASK_LEASE_INVALID")
        self.assertEqual(self.facts_for(batch), 0)

    def test_other_connection_can_renew_during_preparation(self):
        observed = {}
        def renew(batch):
            task = batch.metadata["taskId"]
            before = self.db.execute("SELECT lease_until FROM analysis.async_task WHERE id=%s::uuid", (task,)).fetchone()["lease_until"]
            LabControl(self.db, lease_seconds=120)(lease_for(batch), ControlPoint("EXTERNAL_RENEW", 1, 1))
            after = self.db.execute("SELECT lease_until FROM analysis.async_task WHERE id=%s::uuid", (task,)).fetchone()["lease_until"]
            observed["extended"] = after > before
        batch, result, error = self.paused("M03-RENEW-DURING", renew)
        self.assertIsNone(error)
        self.assertTrue(observed["extended"])
        self.assertEqual(result[0]["acceptedCount"], 1)
        self.assertEqual(self.facts_for(batch), 1)
        write_json(self.output / "m03-runtime-renew.json", {"batchId": batch.id, "otherConnectionRenewed": True,
                                                         "acceptedCount": 1})

    def test_superseded_worker_cannot_publish_after_preparation(self):
        batch, result, error = self.paused("M03-SUPERSEDED", self.replace_attempt_for_lab)
        self.assertIsNone(result)
        self.assertEqual(error.code, "TASK_LEASE_INVALID")
        self.assertEqual(self.facts_for(batch), 0)
        new_lease = Lease(batch.metadata["taskId"], LAB_WORKER, 2, 2)
        result = self.writer.ingest(batch, new_lease)
        self.assertEqual(result[0]["acceptedCount"], 1)
        current = self.db.execute("SELECT status,current_attempt_no FROM analysis.async_task WHERE id=%s::uuid",
                                  (batch.metadata["taskId"],)).fetchone()
        self.assertEqual((current["status"], current["current_attempt_no"]), ("SUCCESS", 2))

    def test_process_crash_rolls_back_and_new_attempt_publishes_once(self):
        batch = self.custom("M03-PROCESS-CRASH")
        seed_batch(self.db, self.settings, batch, self.assets, self.owner)
        manifest = self.assets / (batch.id + "-crash.json")
        write_json(manifest, {"metadata": batch.metadata, "inputPath": str(batch.input_path)})
        done = subprocess.run([sys.executable, "-B", "-m", "lab.crash_probe", str(manifest)],
            cwd=str(PACKAGE_ROOT), env=dict(os.environ, D3_CRASH_PROBE_DSN=self.dsn, PYTHONDONTWRITEBYTECODE="1"),
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=15,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        (self.output / "m03-crash-worker.log").write_bytes(done.stdout + done.stderr)
        self.assertEqual(done.returncode, 17, "Crash worker did not reach the post-write exit")
        self.assertEqual(self.facts_for(batch), 0)
        new_lease = self.replace_attempt_for_lab(batch)
        result = self.writer.ingest(batch, new_lease)
        self.assertEqual(result[0]["acceptedCount"], 1)
        self.assertEqual(self.facts_for(batch), 1)
        write_json(self.output / "m03-crash-recovery.json", {"processExitCode": done.returncode,
            "uncommittedFactsAfterCrash": 0, "recoveredAttempt": 2, "finalFactsForBatch": 1,
            "recoveryProvider": "isolated lab fixture"})

    def test_discarded_commit_response_is_reconciled_without_duplicate_rows(self):
        batch = self.custom("M03-LOST-RESPONSE")
        seed_batch(self.db, self.settings, batch, self.assets, self.owner)
        self.writer.ingest(batch, lease_for(batch))  # Deliberately discard a successfully committed response.
        other = connect(self.dsn)
        try:
            result = make_writer(other, self.settings).ingest(batch, lease_for(batch))
        finally:
            other.close()
        self.assertTrue(result[2])
        self.assertEqual(result[0]["acceptedCount"], 1)
        self.assertEqual(self.facts_for(batch), 1)

    def test_publication_statement_deadline_rolls_back_written_facts(self):
        batch = self.custom("M03-PUBLISH-TIMEOUT")
        seed_batch(self.db, self.settings, batch, self.assets, self.owner)
        def slow(db, lease, current):
            db.execute("SELECT pg_sleep(2)")
            return publish_for_lab(db, lease, current)
        writer = PgIngestor(self.db, self.writer.adapter, self.settings, slow,
            control=LabControl(self.db), budget=replace(LAB_INGEST_BUDGET, publish_timeout_ms=500, lock_timeout_ms=200))
        with self.assertRaises(Exception) as error:
            writer.ingest(batch, lease_for(batch))
        self.assertEqual(getattr(error.exception, "sqlstate", None), "57014")
        self.assertEqual(self.facts_for(batch), 0)
        self.assertEqual(self.db.execute("SELECT status FROM capture.ingestion_batch WHERE id=%s::uuid",
                                         (batch.id,)).fetchone()["status"], "RUNNING")

    def test_insufficient_lease_budget_rejects_before_writes(self):
        batch = self.custom("M03-SHORT-LEASE")
        seed_batch(self.db, self.settings, batch, self.assets, self.owner, lease_seconds=2)
        def check_only(lease, point):
            self.writer.assert_lease(lease)
            return True
        writer = PgIngestor(self.db, self.writer.adapter, self.settings, publish_for_lab,
                            control=check_only, budget=LAB_INGEST_BUDGET)
        with self.assertRaises(WorkerError) as error:
            writer.ingest(batch, lease_for(batch))
        self.assertEqual(error.exception.code, "TASK_LEASE_TOO_SHORT")
        self.assertEqual(self.facts_for(batch), 0)

    def test_oversized_batch_rejected_without_partial_rows(self):
        raw = read_lines(PACKAGE_ROOT / "fixtures/plate-v0/raw/initial.jsonl")[0]
        batch = self.custom("M03-BOUNDED", [dict(raw, recordId="BOUND-A"), dict(raw, recordId="BOUND-B")])
        seed_batch(self.db, self.settings, batch, self.assets, self.owner)
        writer = PgIngestor(self.db, self.writer.adapter, self.settings, publish_for_lab,
            control=LabControl(self.db), budget=replace(LAB_INGEST_BUDGET, max_rows=1))
        with self.assertRaises(WorkerError) as error:
            writer.ingest(batch, lease_for(batch))
        self.assertEqual(error.exception.code, "INGEST_BATCH_TOO_LARGE")
        self.assertEqual(self.facts_for(batch), 0)

    def test_ingestor_rejects_caller_owned_transaction(self):
        batch = self.custom("M03-AMBIENT")
        seed_batch(self.db, self.settings, batch, self.assets, self.owner)
        with self.db.transaction(), self.assertRaises(WorkerError) as error:
            self.writer.ingest(batch, lease_for(batch))
        self.assertEqual(error.exception.code, "PG_INGEST_TRANSACTION_REQUIRED")
        self.assertEqual(self.facts_for(batch), 0)

    def test_batch_list_detail_filters_and_unique_tiebreaker_match_contract(self):
        ids = []
        with self.reader() as reader:
            for page in (1, 2):
                value = reader.list_batches(BatchListSpec(page=page, page_size=2, sort_by="status", sort_order="ASC"), self.scope)
                validate_dto(value, self.schemas["IngestionBatchPage"], self.schemas)
                self.assertEqual(value["total"], 4)
                ids.extend(item["batchId"] for item in value["items"])
            self.assertEqual(ids, sorted(self.scope.batch_ids))
            self.assertEqual(reader.list_batches(BatchListSpec(page=3, page_size=2), self.scope)["items"], [])
            subset = reader.list_batches(BatchListSpec(source="SYNTH_PLATE_B"), self.scope)
            self.assertEqual(subset["total"], 1)
            detail = reader.get_batch(self.settings.batches[0].id, self.scope)
            validate_dto(detail, self.schemas["IngestionBatch"], self.schemas)
            self.assertEqual((detail["acceptedCount"], detail["duplicateCount"], detail["rejectedCount"]), (14, 1, 19))
        write_json(self.output / "m03-batch-reads.json", {"stableOrderedBatchIds": ids, "detail": detail})

    def test_batch_scope_and_revocation_do_not_leak_foreign_counts(self):
        scope = replace(self.scope, batch_ids=(self.settings.batches[0].id,))
        with self.reader(scope) as reader:
            self.assertEqual(reader.list_batches(BatchListSpec(), scope)["total"], 1)
            with self.assertRaises(WorkerError) as error:
                reader.get_batch(self.settings.batches[-1].id, scope)
            self.assertEqual(error.exception.code, "DATA_NOT_FOUND")
            with self.assertRaises(WorkerError):
                reader.list_batches(BatchListSpec(), replace(scope, version="revoked"))
        empty = replace(self.scope, batch_ids=())
        with self.reader(empty) as reader:
            self.assertEqual(reader.list_batches(BatchListSpec(), empty)["total"], 0)

    def test_error_pages_match_contract_and_reject_foreign_snapshot(self):
        batch = self.settings.batches[0]
        actual, token = [], None
        key = b"lab-only-reused-cursor-secret-32!"
        with self.reader(cursor_key=key) as reader:
            first = reader.list_errors(batch.id, self.scope, page_size=3)
            token = first["nextCursor"]
            self.assertIsNotNone(token)
            value = first
            while True:
                validate_dto(value, self.schemas["IngestionErrorPage"], self.schemas)
                actual.extend(value["items"])
                if value["nextCursor"] is None:
                    break
                value = reader.list_errors(batch.id, self.scope, page_size=3, cursor=value["nextCursor"])
            self.assertEqual(len(actual), 19)
            self.assertEqual(len({r["rowNumber"] for r in actual}), 19)
            self.assertTrue(all("sourceRecordKey" not in r for r in actual))
            with self.assertRaises(WorkerError):
                reader.list_errors(batch.id, self.scope, page_size=4, cursor=token)
        with self.reader(cursor_key=key) as reader, self.assertRaises(WorkerError):
            reader.list_errors(batch.id, self.scope, page_size=3, cursor=token)
        write_json(self.output / "m03-error-reads.json", {"items": actual, "currentAttemptOnly": True,
                                                        "rawRecordKeysExposed": False})

    def test_current_attempt_errors_do_not_overwrite_previous_attempt_diagnostics(self):
        raw = read_lines(PACKAGE_ROOT / "fixtures/plate-v0/raw/initial.jsonl")[0]
        raw.update(recordId="M03-ATTEMPT-HISTORY", plateColor="x" * 201)
        batch = self.custom("M03-ATTEMPT-HISTORY", [raw])
        seed_batch(self.db, self.settings, batch, self.assets, self.owner)
        item_id = self.writer.adapter.identity("item:" + batch.id + ":1")
        previous_error_id = self.writer.adapter.identity("row-error:" + item_id + ":1")
        self.db.execute("""
            INSERT INTO analysis.task_row_error(id,task_id,attempt_no,row_number,source_record_key,
                                               field_name,error_code,reason)
            VALUES(%s::uuid,%s::uuid,1,1,'M03-ATTEMPT-HISTORY','plateColor','STORAGE_FIELD_TOO_LONG','lab old attempt')
            """, (previous_error_id, batch.metadata["taskId"]))
        lease = self.replace_attempt_for_lab(batch)
        result = self.writer.ingest(batch, lease)
        self.assertEqual(result[0]["rejectedCount"], 1)
        history = self.db.execute(
            "SELECT id,attempt_no FROM analysis.task_row_error WHERE task_id=%s::uuid ORDER BY attempt_no",
            (batch.metadata["taskId"],)).fetchall()
        self.assertEqual([row["attempt_no"] for row in history], [1, 2])
        self.assertNotEqual(history[0]["id"], history[1]["id"])
        scope = replace(self.scope, batch_ids=(batch.id,))
        with self.reader(scope) as reader:
            errors = reader.list_errors(batch.id, scope)
            self.assertEqual(len(errors["items"]), 1)
            validate_dto(errors, self.schemas["IngestionErrorPage"], self.schemas)

    def test_quality_counts_rows_without_join_multiplication_and_counts_distinct_devices(self):
        expected_invalid = sum(row["reasonCode"] in SYNTHETIC_QUALITY_POLICY.invalid_coordinate_codes for row in self.golden_rows)
        devices = set()
        raw_rows = {b.id: read_lines(b.input_path) for b in self.settings.batches}
        for row in self.golden_rows:
            if "FIXTURE_DEVICE_UNMAPPED" in row["warnings"]:
                devices.add((row["source"], raw_rows[row["batchId"]][row["rowNumber"] - 1]["deviceKey"]))
        with self.reader() as reader:
            result = reader.quality_summary(QualitySpec("2026-10-16T00:00:00Z", "2026-10-18T00:00:00Z"), self.scope)
            validate_dto(result, self.schemas["IngestionQualitySummary"], self.schemas)
            self.assertEqual((result["acceptedCount"], result["duplicateCount"], result["rejectedCount"]), (16, 17, 39))
            self.assertEqual(result["unmappedDeviceCount"], len(devices))
            self.assertEqual(result["invalidCoordinateCount"], expected_invalid)
            # Exclusive upper bound excludes the replay at exactly 2026-10-17T02:00:00Z.
            initial = reader.quality_summary(QualitySpec("2026-10-16T02:00:00Z", "2026-10-17T02:00:00Z"), self.scope)
            self.assertEqual((initial["acceptedCount"], initial["duplicateCount"], initial["rejectedCount"]), (14, 1, 19))
            source = reader.quality_summary(QualitySpec("2026-10-16T00:00:00Z", "2026-10-18T00:00:00Z", "SYNTH_PLATE_B"), self.scope)
            self.assertEqual(source["acceptedCount"], 1)
        write_json(self.output / "m03-quality-reads.json", {"policy": SYNTHETIC_QUALITY_POLICY.version,
            "allGrantedBatches": result, "exclusiveUpperBound": initial, "singleSource": source})
