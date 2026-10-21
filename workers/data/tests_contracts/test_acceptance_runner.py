"""Acceptance orchestration regressions; all PG/report substitutions stay in memory."""
from copy import deepcopy
from pathlib import Path
import runpy
import sys
import types
import unittest
from unittest.mock import patch

from data_worker.contracts import verify_contracts
from data_worker.settings import PACKAGE_ROOT, OUTPUT_ROOT


class AcceptanceRunnerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.contract = verify_contracts()
        cls.runner = staticmethod(runpy.run_path(str(PACKAGE_ROOT / "run_d3_12r.py"))["main"])
        cls.package = staticmethod(runpy.run_path(str(PACKAGE_ROOT / "verify_package.py"))["main"])

    def pg_record(self, status="PASS", error=None):
        record = {"status": status, "postgresExecuted": status != "NOT_RUN",
                  "schemaApplied": status != "NOT_RUN", "contractBaseline": deepcopy(self.contract),
                  "sourceHashes": {"unit-fixture": "memory-only"},
                  "schemaSha256": self.contract["sourceHashes"]["contracts/database/schema.sql"],
                  "platformTables": 165, "testGroups": {"platform": 12, "m03": 15, "identity": 3, "d3_12r": 4},
                  "testsRun": 34 if status != "NOT_RUN" else 0, "failures": int(status == "FAIL"),
                  "errors": 0, "skipped": 0, "clusterStopExitCode": 0,
                  "runDirectory": str(OUTPUT_ROOT / "virtual-pg")}
        if error:
            record["errorCode"] = error
        return record

    def exercise_runner(self, pg, *, pg_exit=0, package_exit=0, local_only=False,
                        fresh_pg=True, fresh_package=True, other_package_error=False):
        sources = {"unit-fixture": "memory-only"}
        local = {"localChecks": "PASS", "codeHashes": sources, "contractBaseline": self.contract,
                 "tests": {"total": 1}}
        required = {"status": "PASS" if package_exit == 0 else "FAIL", "sourceHashes": sources,
                    "contractBaseline": self.contract, "validationRequired": True,
                    "pendingPostgresOnly": not other_package_error and (local_only or pg["status"] == "NOT_RUN")}
        counts, captured = {}, {}

        def digest(path):
            name = path.name
            if name == "acceptance.json":
                return "local-digest"
            if (name == "postgres-checks.json" and not fresh_pg) or (
                    name == "d3-12r-package-checks.json" and not fresh_package):
                return "unchanged"
            counts[name] = counts.get(name, 0) + 1
            return str(counts[name])

        def read(path):
            if path.name == "postgres-checks.json":
                return pg
            if path.name == "latest.json":
                return {"runDirectory": "s1/virtual", "acceptanceSha256": "local-digest"}
            if path.name == "acceptance.json":
                return local
            if path.name == "d3-12r-package-checks.json":
                return required
            raise AssertionError(str(path))

        def execute(command, log, phase):
            return {"postgres": pg_exit, "local": 0, "static-package": 0,
                    "required-package": package_exit}[phase]

        replacements = {
            "OUTPUT_ROOT": OUTPUT_ROOT, "PACKAGE_ROOT": PACKAGE_ROOT,
            "verify_contracts": lambda: self.contract, "file_hash": digest, "read_json": read,
            "execute": execute, "save": lambda path, value: captured.update({path.name: deepcopy(value)}),
            "runpy": types.SimpleNamespace(run_path=lambda path: {"source_hashes": lambda: sources})}
        argv = ["run_d3_12r.py"] + (["--local-only"] if local_only else [])
        # No test cluster, subprocess, acceptance file or validation log is created.
        with patch.dict(self.runner.__globals__, replacements), patch.object(sys, "argv", argv), \
                patch.object(Path, "mkdir"), patch.object(Path, "is_file", return_value=True), \
                patch.object(Path, "read_bytes", return_value=b"IN-MEMORY TEST ONLY"), \
                patch.object(Path, "write_bytes"), patch("builtins.print"):
            code = self.runner()
        return code, captured

    def check_outcome(self, pg, expected, state, **options):
        code, saved = self.exercise_runner(pg, **options)
        self.assertEqual(code, 0 if expected == "PASS" else 2)
        self.assertEqual(saved["acceptance.json"]["status"], expected)
        self.assertEqual(saved["acceptance.json"]["postgresVerification"], state)
        self.assertEqual(saved["d3-12r-local.json"]["postgresVerification"], state)
        return saved

    def test_native_unavailable_is_pending_without_claiming_execution(self):
        pg = self.pg_record("NOT_RUN", "PG_NATIVE_TOOLS_UNAVAILABLE")
        saved = self.check_outcome(pg, "PENDING_POSTGRES", "PENDING", pg_exit=2, package_exit=1)
        self.assertIs(saved["d3-12r-postgres.json"]["postgresExecuted"], False)
        self.assertEqual(saved["acceptance.json"]["postgresTestsExecuted"], 0)

    def test_explicit_skip_is_pending_even_if_an_old_pg_pass_exists(self):
        self.check_outcome(self.pg_record(), "PENDING_POSTGRES", "PENDING", package_exit=1, local_only=True)

    def test_executed_pg_test_failure_is_fail(self):
        self.check_outcome(self.pg_record("FAIL"), "FAIL", "FAIL", pg_exit=2, package_exit=1)

    def test_package_failure_after_pg_success_is_fail(self):
        self.check_outcome(self.pg_record(), "FAIL", "PASS", package_exit=1)

    def test_unrecognized_not_run_failure_is_fail(self):
        self.check_outcome(self.pg_record("NOT_RUN", "PG_LAB_FAILED"), "FAIL", "FAIL", pg_exit=2, package_exit=1)

    def test_stale_or_missing_pg_evidence_is_fail(self):
        for fresh in (False, True):
            pg = self.pg_record()
            if fresh:
                pg["sourceHashes"] = {"old": "snapshot"}
            with self.subTest(fresh=fresh):
                self.check_outcome(pg, "FAIL", "FAIL", fresh_pg=fresh, package_exit=1)

    def test_stale_package_report_is_fail_even_when_child_exits_zero(self):
        self.check_outcome(self.pg_record(), "FAIL", "PASS", fresh_package=False)

    def test_matching_fresh_pg_and_package_reports_pass(self):
        self.check_outcome(self.pg_record(), "PASS", "PASS")

    def test_pending_pg_does_not_hide_an_unrelated_package_error(self):
        self.check_outcome(self.pg_record("NOT_RUN", "PG_NATIVE_TOOLS_UNAVAILABLE"),
                           "FAIL", "PENDING", pg_exit=2, package_exit=1, other_package_error=True)

    def test_not_run_package_diagnostic_has_no_invented_failures_or_cluster_stop(self):
        sources = {"unit-fixture": "memory-only"}
        local = {"status": "PASS", "sourceHashes": sources, "contractBaseline": self.contract,
                 "tests": {"total": 128, "counts": {"tests_m03": 26, "tests_contracts": 25, "tests": 77},
                           "failures": 0, "errors": 0, "skipped": 0}}
        pg = self.pg_record("NOT_RUN", "PG_NATIVE_TOOLS_UNAVAILABLE")
        for key in ("failures", "errors", "skipped", "clusterStopExitCode"):
            pg.pop(key)
        captured = {}

        def read(path):
            if path.name == "d3-12r-local.json":
                return local
            if path.name == "d3-12r-postgres.json":
                return pg
            if path.name in ("manifest.json", "export.json"):
                return {"files": {}}
            raise AssertionError(str(path))

        replacements = {"files": lambda: [], "source_hashes": lambda: sources,
                        "verify_contracts": lambda repository: self.contract, "read": read}
        with patch.dict(self.package.__globals__, replacements), \
                patch.object(sys, "argv", ["verify_package.py", "--require-validation"]), \
                patch.object(Path, "write_bytes", lambda path, value: captured.update(data=value)), \
                patch("builtins.print"):
            code = self.package()
        import json
        report = json.loads(captured["data"])
        self.assertEqual(code, 1)
        self.assertEqual(report["status"], "FAIL")
        self.assertIs(report["pendingPostgresOnly"], True)
        self.assertEqual(report["errors"], ["Current 165-table PostgreSQL acceptance is pending"])
