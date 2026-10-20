"""Pure preparation/control tests. Database locking and rollback are covered by the PG suite."""
from dataclasses import replace
import hashlib
import tempfile
from pathlib import Path
import unittest

from data_worker.common import WorkerError
from data_worker.pg.execution import IngestBudget, PublishSession, prepare_batch
from data_worker.pg.ingest import Lease, StoragePlateAdapter
from data_worker.settings import PACKAGE_ROOT, Batch, load_settings
from data_worker.common import read_json


class ExecutionTests(unittest.TestCase):
    def setUp(self):
        self.settings = load_settings(PACKAGE_ROOT / "config.example.json")
        self.batch = self.settings.batches[0]
        self.adapter = StoragePlateAdapter(read_json(PACKAGE_ROOT / "fixtures/plate-v0/reference-data.json"))
        self.lease = Lease(self.batch.metadata["taskId"], "unit-worker", 1, 1)
        self.budget = IngestBudget(1024 * 1024, 100, 60, 1000, 100, 500, 1)
        self.points = []

    def control(self, lease, point):
        self.assertEqual(lease, self.lease)
        self.points.append(point)
        return True

    def prepare(self, **changes):
        args = dict(settings=self.settings, adapter=self.adapter, batch=self.batch,
                    lease=self.lease, control=self.control, budget=self.budget)
        args.update(changes)
        return prepare_batch(**args)

    def test_normalized_buffer_matches_input_digest_and_preserves_bad_row_positions(self):
        prepared = self.prepare()
        self.assertEqual(len(prepared.rows), 34)
        self.assertEqual(prepared.expected["inputSha256"], hashlib.sha256(self.batch.input_path.read_bytes()).hexdigest())
        self.assertEqual(prepared.input_bytes, self.batch.input_path.stat().st_size)
        self.assertTrue(any(row.row["outcome"] == "REJECTED" for row in prepared.rows))
        self.assertEqual([p.phase for p in (self.points[0], self.points[-1])], ["PREPARE_START", "PREPARED"])
        self.assertEqual(self.points[-1].processed_rows, 34)
        self.assertEqual(prepared.rows[0].normalized.record["dataType"], "PLATE")

    def test_control_is_invoked_during_rows_before_publication(self):
        self.prepare()
        self.assertTrue(any(p.phase == "PREPARING" and 0 < p.processed_rows < 34 for p in self.points))

    def test_uncertain_control_result_stops_preparation(self):
        for response in (False, None, 1):
            with self.subTest(response=response), self.assertRaises(WorkerError) as error:
                self.prepare(control=lambda *_: response)
            self.assertEqual(error.exception.code, "TASK_CONTROL_REJECTED")

    def test_control_failure_is_not_retried_or_swallowed(self):
        points = []
        def control(lease, point):
            points.append(point)
            raise WorkerError("CONTROL_OFFLINE", "test")
        with self.assertRaises(WorkerError) as error:
            self.prepare(control=control)
        self.assertEqual(error.exception.code, "CONTROL_OFFLINE")
        self.assertEqual(len(points), 1)

    def test_cancellation_after_work_starts_aborts_buffer(self):
        def cancel(lease, point):
            if point.processed_rows >= 2:
                raise WorkerError("TASK_LEASE_INVALID", "cancelled")
            return True
        with self.assertRaises(WorkerError) as error:
            self.prepare(control=cancel)
        self.assertEqual(error.exception.code, "TASK_LEASE_INVALID")

    def test_row_limit_rejects_the_entire_preparation(self):
        with self.assertRaises(WorkerError) as error:
            self.prepare(budget=replace(self.budget, max_rows=2))
        self.assertEqual(error.exception.code, "INGEST_BATCH_TOO_LARGE")

    def test_file_limit_rejects_before_normalization(self):
        with self.assertRaises(WorkerError) as error:
            self.prepare(budget=replace(self.budget, max_input_bytes=10))
        self.assertEqual(error.exception.code, "INGEST_BATCH_TOO_LARGE")

    def test_oversized_line_is_a_row_error_with_bounded_drain(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder) / "oversized.jsonl"
            p.write_bytes(b"x" * 500 + b"\n")
            batch = Batch(dict(self.batch.metadata), p)
            result = self.prepare(batch=batch, settings=replace(self.settings, max_line_bytes=128))
            self.assertEqual(len(result.rows), 1)
            self.assertEqual(result.rows[0].row["reasonCode"], "INPUT_LINE_TOO_LARGE")
            self.assertEqual(result.expected["inputSha256"], hashlib.sha256(p.read_bytes()).hexdigest())

    def test_preparation_budget_includes_slow_control(self):
        clock = [0.0]
        def slow(*_):
            clock[0] = 61.0
            return True
        with self.assertRaises(WorkerError) as error:
            self.prepare(control=slow, clock=lambda: clock[0])
        self.assertEqual(error.exception.code, "INGEST_PREPARE_TIMEOUT")

    def test_invalid_budgets_fail_before_execution(self):
        for values in ({"max_rows": 0}, {"max_rows": True}, {"prepare_seconds": float("inf")},
                       {"lock_timeout_ms": 2000}, {"checkpoint_seconds": 61}):
            with self.subTest(values=values), self.assertRaises(WorkerError):
                replace(self.budget, **values)

    def test_publish_budget_decreases_and_expires_before_next_statement(self):
        calls = []
        class Recorder:
            def execute(self, sql, params=None):
                calls.append((sql, params))
                return "result"
        clock = [0.0]
        session = PublishSession(Recorder(), self.budget, clock=lambda: clock[0])
        self.assertEqual(session.execute("SELECT 1"), "result")
        clock[0] = 0.5
        session.execute("SELECT 2")
        self.assertLess(int(calls[2][1][0]), int(calls[0][1][0]))
        clock[0] = 2
        with self.assertRaises(WorkerError) as error:
            session.execute("SELECT 3")
        self.assertEqual(error.exception.code, "INGEST_PUBLISH_TIMEOUT")
        self.assertEqual(len(calls), 4)

    def test_publisher_cannot_accidentally_commit_or_send_multiple_statements(self):
        session = PublishSession(None, self.budget)
        for command in ("COMMIT", "ROLLBACK", "BEGIN", "SELECT 1; COMMIT"):
            with self.subTest(command=command), self.assertRaises(WorkerError) as error:
                session.execute(command)
            self.assertEqual(error.exception.code, "PG_PUBLISHER_TRANSACTION_INVALID")
