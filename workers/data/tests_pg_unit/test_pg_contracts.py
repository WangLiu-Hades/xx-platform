"""No database substitute: these tests cover only pure contracts, projection and cursor safety."""
from copy import deepcopy
from dataclasses import replace
from datetime import datetime, timezone, timedelta
import hashlib
import hmac
import json
from pathlib import Path
import unittest
import uuid

from data_worker.common import RowError, WorkerError
from data_worker.pg.connection import connect
from data_worker.pg.ingest import Lease, PgIngestor, StoragePlateAdapter, safe_error_text
from data_worker.pg.projection import to_dto
from data_worker.pg.query import TrustedScope, QuerySpec, QueryBudget, build_query, CursorCodec, CaptureQuerySession
from data_worker.settings import PACKAGE_ROOT

SAMPLES = PACKAGE_ROOT / "fixtures/plate-v0"


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


class PgContractTests(unittest.TestCase):
    def setUp(self):
        self.refs = read(SAMPLES / "reference-data.json")
        self.site_a, self.site_b = [row["id"] for row in self.refs["sites"]]
        self.principal = str(uuid.UUID("c1b4b9c7-0aac-40cc-bab9-67813da28cc7"))
        self.scope = TrustedScope(self.principal, "v1", (self.site_a,), ("PLATE",))
        self.request = {"dataType": "PLATE",
                        "timeRange": {"from": "2026-10-16T08:00:00+08:00", "to": "2026-10-16T09:00:00+08:00"},
                        "pageSize": 3}
        self.spec = QuerySpec.parse(self.request)
        self.codec = CursorCodec(b"test-only-cursor-secret-32-bytes!!")
        self.last = {"captureId": self.refs["targets"][0]["targetId"], "captureTime": "2026-10-16T00:30:00.123456Z"}

    def test_authority_requires_explicit_valid_sites_and_types(self):
        for sites, types in ((None, ("PLATE",)), (["bad"], ["PLATE"]), ([], ["ALL"])):
            with self.subTest(sites=sites), self.assertRaises(WorkerError):
                TrustedScope(self.principal, "v1", sites, types)
        scope = TrustedScope(self.principal, "v1", [self.site_a, self.site_a], ["PLATE", "PLATE"])
        self.assertEqual(scope, self.scope)

    def test_requested_sites_intersect_authority(self):
        spec = QuerySpec.parse(dict(self.request, siteIds=[self.site_a, self.site_b]))
        _, params, empty = build_query(spec, self.scope)
        self.assertEqual(params["sites"], [self.site_a])
        self.assertFalse(empty)
        self.assertTrue(build_query(QuerySpec.parse(dict(self.request, siteIds=[self.site_b])), self.scope)[2])

    def test_empty_authority_and_explicit_empty_filters_return_no_rows(self):
        variants = [
            (self.spec, replace(self.scope, site_ids=())),
            (self.spec, replace(self.scope, data_types=())),
            (self.spec, replace(self.scope, data_types=("MAC",))),
            (QuerySpec.parse(dict(self.request, siteIds=[])), self.scope),
            (QuerySpec.parse(dict(self.request, deviceIds=[])), self.scope),
        ]
        for spec, scope in variants:
            self.assertTrue(build_query(spec, scope)[2])

    def test_query_has_parameterized_half_open_utc_window(self):
        sql, params, _ = build_query(self.spec, self.scope)
        self.assertEqual((params["from"], params["to"]), ("2026-10-16T00:00:00Z", "2026-10-16T01:00:00Z"))
        self.assertIn("e.capture_time >= %(from)s", sql)
        self.assertIn("e.capture_time < %(to)s", sql)
        self.assertNotIn(params["from"], sql)
        self.assertEqual(params["limit"], 4)

    def test_query_data_is_never_interpolated_into_sql(self):
        value = "'; DROP SCHEMA capture CASCADE;--"
        sql, params, _ = build_query(QuerySpec.parse(dict(self.request, plateColor=value)), self.scope)
        self.assertNotIn(value, sql)
        self.assertEqual(params["color"], value)
        for bad in ("DESC; SELECT 1", "captureTime;DELETE", ""):
            with self.assertRaises(WorkerError):
                build_query(replace(self.spec, sort_order=bad), self.scope)

    def test_all_supported_sorts_have_a_unique_cursor_key(self):
        for order in ("ASC", "DESC"):
            for field in ("captureTime", "captureId"):
                sql, params, _ = build_query(replace(self.spec, sort_by=field, sort_order=order), self.scope, self.last)
                self.assertIn("e.id " + order, sql)
                self.assertEqual(params["last_id"], self.last["captureId"])
                self.assertEqual("last_time" in params, field == "captureTime")
                self.assertIn(" > " if order == "ASC" else " < ", sql)

    def test_targets_and_devices_are_bound_uuid_arrays(self):
        target = self.refs["targets"][0]["targetId"]
        device = self.refs["devices"][0]["id"]
        spec = QuerySpec.parse(dict(self.request, targetIds=[target], deviceIds=[device]))
        sql, params, _ = build_query(spec, self.scope)
        self.assertEqual((params["targets"], params["devices"]), ([target], [device]))
        self.assertNotIn(target, sql)
        self.assertIn("EXISTS", sql)

    def test_invalid_ranges_page_sizes_and_unknown_fields_are_rejected(self):
        requests = [dict(self.request, pageSize=value) for value in (0, 101, True, "3")]
        requests += [dict(self.request, targetIds=[]), dict(self.request, cursor="public-cursor"),
                     dict(self.request, trustedScope={"allSites": True}), dict(self.request, plateColor=None),
                     dict(self.request, sortOrder="DROP"), dict(self.request, siteIds=["not-a-uuid"]),
                     dict(self.request, timeRange={"from": "2026-10-16T00:00:00Z", "to": "2026-10-16T00:00:00Z"}),
                     dict(self.request, timeRange={"from": "2026-10-16T00:00:00+08:60", "to": "2026-10-16T02:00:00Z"})]
        for request in requests:
            with self.subTest(request=request), self.assertRaises(WorkerError):
                QuerySpec.parse(request)

    def test_unapproved_raw_plate_lookup_fails_explicitly(self):
        with self.assertRaises(WorkerError) as error:
            QuerySpec.parse(dict(self.request, plateNumber="SYNTH-PLATE-001"))
        self.assertEqual(error.exception.code, "SYSTEM_UNAVAILABLE")

    def test_cursor_round_trip_and_expiry(self):
        token = self.codec.encode("bound", self.last, 2000)
        self.assertEqual(self.codec.decode(token, "bound", now=1999), self.last)
        with self.assertRaises(WorkerError) as error:
            self.codec.decode(token, "bound", now=2000)
        self.assertEqual(error.exception.code, "DATA_CURSOR_INVALID")

    def test_cursor_rejects_tampering_foreign_scope_and_other_signing_key(self):
        token = self.codec.encode("account-scope-query-snapshot", self.last, 2000)
        probes = [token[:-1] + ("0" if token[-1] != "0" else "1"), "invalid", "." * 5, "a" * 5000]
        for value in probes:
            with self.assertRaises(WorkerError):
                self.codec.decode(value, "account-scope-query-snapshot", now=1000)
        with self.assertRaises(WorkerError):
            self.codec.decode(token, "other-account-scope-query-snapshot", now=1000)
        with self.assertRaises(WorkerError):
            CursorCodec(b"x" * 32).decode(token, "account-scope-query-snapshot", now=1000)

    def test_signed_malformed_cursor_contents_are_rejected(self):
        for last in (None, {"captureId": "bad", "captureTime": self.last["captureTime"]},
                     dict(self.last, extra=True), dict(self.last, captureTime="2026-10-16T00:30:00")):
            token = self.codec.encode("bound", last, 2000)
            with self.assertRaises(WorkerError):
                self.codec.decode(token, "bound", now=1000)

    def test_query_budget_is_explicit_and_bounds_window_before_database_use(self):
        with self.assertRaises(WorkerError) as error:
            CaptureQuerySession(None, self.spec, self.scope, QueryBudget(60, 1000, 30))
        self.assertEqual(error.exception.code, "DATA_QUERY_TOO_BROAD")
        with self.assertRaises(WorkerError):
            QueryBudget(3600, 0, 30)

    def test_scope_change_and_closed_session_fail_before_query(self):
        session = CaptureQuerySession(None, self.spec, self.scope, QueryBudget(3600, 1000, 30))
        with self.assertRaises(WorkerError):
            session.page(self.scope)
        session.active, session.expires = True, 9999999999
        with self.assertRaises(WorkerError):
            session.page(replace(self.scope, version="v2"))

    def test_database_projection_keeps_nulls_zero_and_omits_unavailable_image(self):
        row = {"capture_id": uuid.uuid4(), "primary_target_id": None, "device_id": None, "site_id": None,
               "capture_time": datetime(2026, 10, 16, 8, 30, 0, 123456, tzinfo=timezone(timedelta(hours=8))),
               "ingested_at": datetime(2026, 10, 16, 2, tzinfo=timezone.utc), "longitude": 0.0, "latitude": 0.0,
               "source_code": "SYNTH_PLATE_A", "status": "VALID", "plate_color": None,
               "vehicle_image_asset_id": None}
        dto = to_dto(row)
        self.assertEqual(dto["captureTime"], "2026-10-16T00:30:00.123456Z")
        self.assertIsNone(dto["targetId"])
        self.assertEqual((dto["targetType"], dto["longitude"], dto["latitude"]), ("PLATE", 0.0, 0.0))
        self.assertEqual(dto["plate"], {"plateMasked": "SYNTH-PLATE-****"})
        self.assertTrue(dto["isMasked"])
        row.update(longitude=None, latitude=None, vehicle_image_asset_id=uuid.uuid4(), plate_color="BLUE")
        dto = to_dto(row)
        self.assertIsNone(dto["longitude"])
        self.assertEqual(dto["plate"]["vehicleImageAssetId"], str(row["vehicle_image_asset_id"]))

    def test_storage_limits_reject_before_any_database_write(self):
        adapter = StoragePlateAdapter(self.refs)
        raw = json.loads((SAMPLES / "raw/initial.jsonl").read_text(encoding="utf-8").splitlines()[0])
        for values, reason in (({"deviceKey": "x" * 201}, "STORAGE_FIELD_TOO_LONG"),
                               ({"plateColor": "x" * 201}, "STORAGE_FIELD_TOO_LONG"),
                               ({"recordId": "test\x00id"}, "STORAGE_TEXT_INVALID")):
            with self.assertRaises(RowError) as error:
                adapter.normalize(dict(raw, **values), "SYNTH_PLATE_A", "2026-10-16T02:00:00Z")
            self.assertEqual(error.exception.code, reason)
        self.assertIsNone(safe_error_text("a" * 201))
        self.assertIsNone(safe_error_text("a\x00b"))

    def test_invalid_lease_and_missing_publisher_are_rejected(self):
        for attempt, generation in ((0, 1), (1, 0), (True, 1)):
            with self.assertRaises(WorkerError):
                Lease(self.principal, "worker", attempt, generation)
        with self.assertRaises(WorkerError) as error:
            PgIngestor(None, None, None, None)
        self.assertEqual(error.exception.code, "PG_PUBLISHER_REQUIRED")

    def test_missing_connection_configuration_does_not_attempt_authentication(self):
        with self.assertRaises(WorkerError) as error:
            connect(dsn="")
        self.assertEqual(error.exception.code, "PG_CONNECTION_CONFIG_MISSING")
