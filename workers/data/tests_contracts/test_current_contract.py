"""D3-12R contract drift, actual DTOs, policy boundaries and acceptance gates."""
from copy import deepcopy
from pathlib import Path
import json
import shutil
import tempfile
import unittest

from data_worker.common import WorkerError
from data_worker.contracts import baseline, verify_contracts, pg_evidence_matches
from data_worker.pg.query import QueryBudget, QuerySpec, CaptureQuerySession, TrustedScope
from data_worker.pg.ingestion_reads import IngestionReadBudget
from data_worker.pg.execution import IngestBudget
from data_worker.settings import PACKAGE_ROOT, REPO_ROOT
from lab.contract_validation import validate_dto


class CurrentContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.contract = verify_contracts()
        cls.mirror = json.loads((REPO_ROOT / "apps/web/src/api/contract.json").read_text(encoding="utf-8"))
        cls.schemas = cls.mirror["components"]["schemas"]

    def assert_contract_drift(self, name, data=None):
        with tempfile.TemporaryDirectory(prefix="d3-12r-contract-") as directory:
            root = Path(directory)
            for relative in baseline()["sources"]:
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(REPO_ROOT / relative, path)
            if data is None:
                (root / name).unlink()
            else:
                (root / name).write_bytes(data)
            with self.assertRaises(WorkerError) as error:
                verify_contracts(root)
            self.assertEqual(error.exception.code, "CONTRACT_BASELINE_CHANGED")

    def test_current_authorities_and_catalog_are_verified_together(self):
        self.assertEqual((self.contract["platformTableCount"], self.contract["operationCount"],
                          self.contract["schemaCount"]), (165, 198, 623))
        self.assertEqual(self.contract["openapiVersion"], "1.0.0-dev.4-provisional")
        self.assertEqual(set(self.contract["sourceHashes"]), {
            "contracts/database/schema.sql", "contracts/api/openapi.yaml", "apps/web/src/api/contract.json"})
        self.assertTrue({"audit.action_approval_decision", "operation.action_approval"}
                        <= set(baseline()["platformTables"]))

    def test_legacy_baseline_is_retained_but_not_used_as_current(self):
        old = json.loads((PACKAGE_ROOT / "fixtures/contract-baseline.json").read_text(encoding="utf-8"))
        self.assertEqual(old["openapi_info_version"], "1.0.0-dev.2")
        self.assertNotEqual(old["sources"]["contracts/database/schema.sql"],
                            self.contract["sourceHashes"]["contracts/database/schema.sql"])

    def test_schema_drift_is_rejected_even_if_table_count_is_unchanged(self):
        name = "contracts/database/schema.sql"
        self.assert_contract_drift(name, (REPO_ROOT / name).read_bytes() + b"\n-- unreviewed schema edit\n")

    def test_openapi_drift_cannot_hide_behind_a_current_schema(self):
        name = "contracts/api/openapi.yaml"
        self.assert_contract_drift(name, (REPO_ROOT / name).read_bytes().replace(b"dev.4-provisional", b"dev.3-provisional"))

    def test_generated_mirror_drift_cannot_hide_behind_current_openapi(self):
        name = "apps/web/src/api/contract.json"
        value = deepcopy(self.mirror)
        del value["operations"]["replayIngestionBatch"]
        self.assert_contract_drift(name, json.dumps(value).encode())

    def test_missing_contract_fails_closed(self):
        self.assert_contract_drift("contracts/database/schema.sql")

    def test_frozen_capture_facts_fit_current_plate_dto(self):
        rows = (PACKAGE_ROOT / "fixtures/plate-v0/expected/captures.jsonl").read_text(encoding="utf-8").splitlines()
        self.assertEqual(len(rows), 16)
        for line in rows:
            validate_dto(json.loads(line)["record"], self.schemas["PlateCaptureRecord"], self.schemas)

    def test_current_m03_routes_and_approval_header_are_preserved(self):
        expected = {
            "listIngestionBatches": ("GET", "/ingestion-batches"),
            "createIngestionBatch": ("POST", "/ingestion-batches"),
            "getIngestionBatch": ("GET", "/ingestion-batches/{batchId}"),
            "listIngestionErrors": ("GET", "/ingestion-batches/{batchId}/errors"),
            "getIngestionQualitySummary": ("GET", "/ingestion/quality-summary"),
            "replayIngestionBatch": ("POST", "/ingestion-batches/{batchId}/replay"),
            "createIngestionBackfill": ("POST", "/ingestion/backfills"),
        }
        for name, route in expected.items():
            operation = self.mirror["operations"][name]
            self.assertEqual((operation["method"], operation["path"]), route)
            if name in ("replayIngestionBatch", "createIngestionBackfill"):
                header = next(p for p in operation["parameters"] if p["name"] == "X-Approval-Id")
                self.assertEqual((header["in"], header["schema"]["format"]), ("header", "uuid"))
                # OAS marks this conditional header optional; the actual service must
                # enforce the matching risk rule and consume approval in its transaction.
                self.assertFalse(header["required"])

    def test_replay_reason_and_unknown_fields_obey_current_dto(self):
        spec = self.schemas["ReplayInput"]
        validate_dto({"reason": "synthetic replay reason"}, spec, self.schemas)
        for value in ({"reason": "short"}, {"reason": "synthetic replay reason", "approved": True}):
            with self.assertRaises(ValueError):
                validate_dto(value, spec, self.schemas)

    def test_query_budget_cannot_expand_with_the_one_year_retention(self):
        QueryBudget(31 * 86400, 5000, 900)
        for values in ((31 * 86400 + 1, 5000, 900), (86400, 5001, 900), (86400, 5000, 901)):
            with self.subTest(values=values), self.assertRaises(WorkerError):
                QueryBudget(*values)
        request = {"dataType": "PLATE", "timeRange": {
            "from": "2026-01-01T00:00:00Z", "to": "2026-02-02T00:00:00Z"}}
        scope = TrustedScope("00000000-0000-4000-8000-000000000001", "v1", [], ["PLATE"])
        with self.assertRaises(WorkerError) as error:
            CaptureQuerySession(None, QuerySpec.parse(request), scope, QueryBudget(31 * 86400, 5000, 900))
        self.assertEqual(error.exception.code, "DATA_QUERY_TOO_BROAD")

    def test_m03_read_budget_obeys_query_sql_and_cursor_limits(self):
        IngestionReadBudget(5000, 900, 31 * 86400, 100)
        for values in ((5001, 900, 86400, 100), (5000, 901, 86400, 100),
                       (5000, 900, 31 * 86400 + 1, 100)):
            with self.subTest(values=values), self.assertRaises(WorkerError):
                IngestionReadBudget(*values)

    def test_ingestion_budget_cannot_exceed_reviewed_file_and_row_caps(self):
        IngestBudget(100 * 1024 * 1024, 100000, 60, 3000, 500, 1000)
        for size, rows in ((100 * 1024 * 1024 + 1, 100000), (1024, 100001)):
            with self.subTest(size=size, rows=rows), self.assertRaises(WorkerError):
                IngestBudget(size, rows, 60, 3000, 500, 1000)

    def pg_record(self):
        # In-memory gate fixture only: never written as execution evidence.
        return {"status": "PASS", "postgresExecuted": True, "schemaApplied": True,
                "contractBaseline": deepcopy(self.contract),
                "schemaSha256": self.contract["sourceHashes"]["contracts/database/schema.sql"],
                "platformTables": 165, "testGroups": {"platform": 12, "m03": 15, "identity": 3, "d3_12r": 4},
                "testsRun": 34, "sourceHashes": {"unit-fixture": "unit-fixture"},
                "failures": 0, "errors": 0, "skipped": 0, "clusterStopExitCode": 0}

    def test_pg_gate_requires_executed_schema_and_clean_cluster_stop(self):
        record = self.pg_record()
        self.assertTrue(pg_evidence_matches(record, record["sourceHashes"], self.contract))
        for field, value in (("postgresExecuted", False), ("schemaApplied", False),
                             ("clusterStopExitCode", None), ("clusterStopExitCode", 1),
                             ("errors", 1), ("testsRun", 30), ("status", "NOT_RUN")):
            changed = dict(record, **{field: value})
            with self.subTest(field=field, value=value):
                self.assertFalse(pg_evidence_matches(changed, record["sourceHashes"], self.contract))

    def test_pg_gate_rejects_old_163_table_acceptance(self):
        record = self.pg_record()
        for field, value in (("platformTables", 163), ("contractBaseline", None),
                             ("schemaSha256", "legacy-sha"), ("sourceHashes", {"old": "source"})):
            with self.subTest(field=field):
                self.assertFalse(pg_evidence_matches(dict(record, **{field: value}), record["sourceHashes"], self.contract))

    def test_pg_gate_requires_new_contract_cases_and_matching_authorities(self):
        record = self.pg_record()
        record["testGroups"].pop("d3_12r")
        record["testsRun"] = 30
        self.assertFalse(pg_evidence_matches(record, record["sourceHashes"], self.contract))
        record = self.pg_record()
        record["contractBaseline"]["sourceHashes"]["contracts/api/openapi.yaml"] = "stale-api"
        self.assertFalse(pg_evidence_matches(record, record["sourceHashes"], self.contract))
