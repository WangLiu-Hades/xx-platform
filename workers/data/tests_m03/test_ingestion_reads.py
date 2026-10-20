"""M03 request, projection and cursor contracts; SQL results are tested against real PostgreSQL."""
from dataclasses import replace
from datetime import datetime, timezone
import json
from pathlib import Path
import unittest
import uuid

from data_worker.common import WorkerError
from data_worker.pg.ingestion_reads import (
    BatchListSpec, QualitySpec, QualityPolicy, TrustedBatchScope, IngestionReadBudget,
    IngestionReadSession, ErrorCursor, batch_where, batch_projection, synthetic_error_projection,
    SYNTHETIC_QUALITY_POLICY)
from data_worker.settings import REPO_ROOT
from lab.contract_validation import validate_dto


class IngestionReadContractTests(unittest.TestCase):
    def setUp(self):
        self.principal, self.batch = str(uuid.uuid4()), str(uuid.uuid4())
        self.scope = TrustedBatchScope(self.principal, "unit-scope-v1", [self.batch])
        self.budget = IngestionReadBudget(1000, 30, 86400, 100)
        self.codec = ErrorCursor(b"test-only-m03-cursor-key-32-bytes")
        self.schemas = json.loads((REPO_ROOT / "apps/web/src/api/contract.json").read_text(encoding="utf-8"))["components"]["schemas"]

    def session(self, scope=None, budget=None):
        return IngestionReadSession(None, scope or self.scope, budget or self.budget,
            quality_policy=SYNTHETIC_QUALITY_POLICY, error_projection=synthetic_error_projection)

    def test_grant_is_explicit_canonical_and_immutable(self):
        self.assertEqual(TrustedBatchScope(self.principal, "unit-scope-v1", [self.batch, self.batch]), self.scope)
        for value in (None, ["bad"], "*"):
            with self.subTest(value=value), self.assertRaises(WorkerError):
                TrustedBatchScope(self.principal, "v1", value)
        self.assertEqual(TrustedBatchScope(self.principal, "v1", []).batch_ids, ())

    def test_public_filter_cannot_supply_a_grant_or_sort_expression(self):
        for value in ({"scope": {"all": True}}, {"sortBy": "createdAt; DROP TABLE x"},
                      {"pageSize": True}, {"page": -1}, {"pageSize": 101}, {"source": None},
                      {"status": "QUEUED"}, {"sortOrder": "descending"}):
            with self.subTest(value=value), self.assertRaises(WorkerError):
                BatchListSpec.parse(value)

    def test_source_is_bound_and_untrusted_text_never_enters_sql(self):
        value = "x' OR true--"
        where, params = batch_where(self.scope, BatchListSpec.parse({"source": value, "status": "SUCCESS"}))
        self.assertNotIn(value, where)
        self.assertEqual(params["source"], value)
        self.assertEqual(params["grants"], [self.batch])
        self.assertIn("NOT t.deleted", where)

    def test_quality_window_is_explicit_and_converted_to_utc(self):
        value = QualitySpec.parse({"from": "2026-10-16T08:00:00+08:00", "to": "2026-10-16T09:00:00+08:00"})
        self.assertEqual((value.start, value.end), ("2026-10-16T00:00:00Z", "2026-10-16T01:00:00Z"))
        for request in ({}, {"from": value.start}, {"from": value.end, "to": value.start},
                        {"from": value.start, "to": value.end, "timeBasis": "captureTime"},
                        {"from": value.start, "to": value.end, "source": None}):
            with self.subTest(request=request), self.assertRaises(WorkerError):
                QualitySpec.parse(request)

    def test_unknown_quality_policy_cannot_silently_change_counting(self):
        with self.assertRaises(WorkerError):
            replace(SYNTHETIC_QUALITY_POLICY, time_basis="CAPTURE_TIME")
        with self.assertRaises(WorkerError):
            replace(SYNTHETIC_QUALITY_POLICY, unmapped_unit="ROW")
        with self.assertRaises(WorkerError):
            replace(SYNTHETIC_QUALITY_POLICY, invalid_coordinate_codes=())

    def test_scope_budget_rejects_oversized_explicit_grants(self):
        scope = TrustedBatchScope(self.principal, "v1", [self.batch, str(uuid.uuid4())])
        with self.assertRaises(WorkerError) as error:
            self.session(scope, replace(self.budget, max_authorized_batches=1))
        self.assertEqual(error.exception.code, "DATA_QUERY_TOO_BROAD")

    def test_batch_projection_handles_nullable_fields_without_leaking_diagnostics(self):
        row = {"batch_id": uuid.UUID(self.batch), "source_code": "SYNTH", "task_id": uuid.uuid4(),
               "source_file_id": None, "mapping_version": "v1", "dedup_rule_version": "v1", "status": "RUNNING",
               "accepted_count": 0, "rejected_count": 0, "duplicate_count": 0,
               "created_at": datetime(2026, 10, 16, tzinfo=timezone.utc), "finished_at": None,
               "error_summary": "private path and database diagnostic"}
        dto = batch_projection(row)
        validate_dto(dto, self.schemas["IngestionBatch"], self.schemas)
        self.assertNotIn("sourceFileId", dto)
        self.assertIsNone(dto["finishedAt"])
        self.assertNotIn("private path", dto["errorSummary"])
        page = {"items": [dto], "page": 1, "pageSize": 20, "total": 1}
        validate_dto(page, self.schemas["IngestionBatchPage"], self.schemas)
        for invalid in (dict(page, items={}), dict(page, items=[dict(dto, acceptedCount=True)])):
            with self.assertRaises(ValueError):
                validate_dto(invalid, self.schemas["IngestionBatchPage"], self.schemas)

    def test_row_error_projection_fits_public_schema_and_omits_raw_identifiers(self):
        row = {"row_number": 2, "error_code": "FIXTURE_REQUIRED", "field_name": "plateText",
               "source_record_key": "secret source identity", "reason": "private raw input"}
        dto = synthetic_error_projection(row)
        validate_dto(dto, self.schemas["IngestionError"], self.schemas)
        self.assertEqual(dto["code"], "VALIDATION_ERROR")
        self.assertNotIn("sourceRecordKey", dto)
        self.assertNotIn("private", dto["reason"])
        page = {"items": [dto], "nextCursor": None, "pageSize": 20}
        validate_dto(page, self.schemas["IngestionErrorPage"], self.schemas)
        for invalid in (dict(page, items="not a page"), dict(page, items=[dict(dto, rowNumber=0)])):
            with self.assertRaises(ValueError):
                validate_dto(invalid, self.schemas["IngestionErrorPage"], self.schemas)
        dto = synthetic_error_projection(dict(row, field_name="untrusted-field-name"))
        self.assertNotIn("field", dto)

    def test_unmapped_internal_error_requires_explicit_public_policy(self):
        with self.assertRaises(WorkerError) as error:
            synthetic_error_projection({"error_code": "VENDOR_UNKNOWN"})
        self.assertEqual(error.exception.code, "REQUIREMENT_PENDING")

    def test_error_cursor_binds_scope_query_attempt_and_snapshot(self):
        last = (8, str(uuid.uuid4()))
        token = self.codec.encode("snapshot-scope-attempt-page", last, 2000)
        self.assertEqual(self.codec.decode(token, "snapshot-scope-attempt-page", now=1999), last)
        for binding in ("other-user", "other-attempt", "other-session", "other-page-size"):
            with self.assertRaises(WorkerError):
                self.codec.decode(token, binding, now=1999)

    def test_error_cursor_rejects_tampering_expiry_and_malformed_signed_payload(self):
        token = self.codec.encode("binding", (8, str(uuid.uuid4())), 2000)
        with self.assertRaises(WorkerError):
            self.codec.decode(token, "binding", now=2000)
        with self.assertRaises(WorkerError):
            self.codec.decode(token + "x", "binding", now=1000)
        for last in ((0, str(uuid.uuid4())), (True, str(uuid.uuid4())), (1, "bad")):
            with self.assertRaises(WorkerError):
                self.codec.decode(self.codec.encode("binding", last, 2000), "binding", now=1000)

    def test_closed_or_changed_scope_session_fails_before_database_access(self):
        session = self.session()
        with self.assertRaises(WorkerError):
            session.list_batches(BatchListSpec(), self.scope)
        session.active, session.deadline = True, float("inf")
        with self.assertRaises(WorkerError):
            session.get_batch(self.batch, replace(self.scope, version="revoked"))
        with self.assertRaises(WorkerError):
            session.get_batch(str(uuid.uuid4()), self.scope)

    def test_empty_grant_never_turns_into_an_unfiltered_list_or_summary(self):
        scope = replace(self.scope, batch_ids=())
        session = self.session(scope)
        session.active, session.deadline, session.as_of = True, float("inf"), "2026-10-16T00:00:00Z"
        self.assertEqual(session.list_batches(BatchListSpec(), scope)["total"], 0)
        result = session.quality_summary(QualitySpec("2026-10-16T00:00:00Z", "2026-10-16T01:00:00Z"), scope)
        self.assertEqual(result["acceptedCount"], 0)
        validate_dto(result, self.schemas["IngestionQualitySummary"], self.schemas)

    def test_quality_budget_rejects_oversized_window_before_query(self):
        session = self.session()
        session.active, session.deadline = True, float("inf")
        with self.assertRaises(WorkerError) as error:
            session.quality_summary(QualitySpec("2026-10-16T00:00:00Z", "2026-10-18T00:00:00Z"), self.scope)
        self.assertEqual(error.exception.code, "DATA_QUERY_TOO_BROAD")
