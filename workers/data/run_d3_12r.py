"""Run D3-12R against the pinned current contracts; exit 2 while real PG remains incomplete."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import sys
import time
import uuid

sys.dont_write_bytecode = True
sys.stdout.reconfigure(encoding="utf-8")
from data_worker.common import WorkerError, check, read_json
from data_worker.contracts import verify_contracts, pg_evidence_state
from data_worker.settings import PACKAGE_ROOT, OUTPUT_ROOT

PREFIX = "d3-12r"


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))


def file_hash(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


def execute(command, log, phase):
    with log.open("wb") as stream:
        process = subprocess.Popen([str(x) for x in command], stdout=stream, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        while True:
            try:
                return process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                print(json.dumps({"event": "d3_12r.progress", "phase": phase}), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--postgres-bin", type=Path)
    parser.add_argument("--local-only", action="store_true",
                        help="Explicitly leave real PostgreSQL pending; never returns overall PASS")
    args = parser.parse_args()
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S") + "-" + uuid.uuid4().hex[:8]
    destination = OUTPUT_ROOT / "d3-12r/runs" / run_id
    destination.mkdir(parents=True, exist_ok=False)
    result = {"status": "FAIL", "businessScope": "Synthetic data and reviewed contract compatibility",
              "h04Integration": "NOT_IMPLEMENTED", "independentTeamVerification": "PENDING",
              "runDirectory": destination.relative_to(OUTPUT_ROOT).as_posix(), "commandExitCodes": {}}
    checker = runpy.run_path(str(PACKAGE_ROOT / "verify_package.py"))
    sources = checker["source_hashes"]()
    result["sourceHashes"] = sources
    try:
        contract = verify_contracts()
        result["contractBaseline"] = contract
        pg_path = OUTPUT_ROOT / "d3-05-06/postgres-checks.json"
        pg_before = file_hash(pg_path)
        if not args.local_only:
            command = [sys.executable, "-B", PACKAGE_ROOT / "run_pg_lab.py"]
            if args.postgres_bin:
                command += ["--postgres-bin", args.postgres_bin]
            result["commandExitCodes"]["postgres"] = execute(command, destination / "postgres-runner.txt", "postgres")
        fresh_pg = not args.local_only and pg_path.is_file() and file_hash(pg_path) != pg_before
        pg = read_json(pg_path) if fresh_pg else {
            "status": "NOT_RUN", "postgresExecuted": False, "schemaApplied": False,
            "errorCode": "PG_EXPLICITLY_SKIPPED" if args.local_only else "PG_FRESH_REPORT_MISSING",
            "contractBaseline": contract, "sourceHashes": sources}
        local_pointer = OUTPUT_ROOT / "s1/latest.json"
        local_before = file_hash(local_pointer)
        result["commandExitCodes"]["local"] = execute(
            [sys.executable, "-B", PACKAGE_ROOT / "run_stage1_local.py"],
            destination / "local-runner.txt", "local")
        check(local_pointer.is_file() and file_hash(local_pointer) != local_before,
              "The local run did not produce fresh evidence", "LOCAL_FRESH_REPORT_MISSING")
        pointer = read_json(local_pointer)
        local_dir = (OUTPUT_ROOT / pointer["runDirectory"]).resolve()
        check(local_dir.is_relative_to(OUTPUT_ROOT), "Local evidence escaped output root", "LOCAL_EVIDENCE_INVALID")
        local_path = local_dir / "acceptance.json"
        check(file_hash(local_path) == pointer["acceptanceSha256"], "Local evidence digest differs", "LOCAL_EVIDENCE_INVALID")
        local = read_json(local_path)
        local_passed = (result["commandExitCodes"]["local"] == 0 and local.get("localChecks") == "PASS"
                        and local.get("codeHashes") == sources and local.get("contractBaseline") == contract)
        pg_state = pg_evidence_state(pg, sources, contract)
        if pg_state == "PASS" and (not fresh_pg or result["commandExitCodes"].get("postgres") != 0):
            pg_state = "FAIL"
        if pg_state == "PENDING" and not args.local_only and (
                not fresh_pg or result["commandExitCodes"].get("postgres") != 2):
            pg_state = "FAIL"
        pg_passed = pg_state == "PASS"
        validation = PACKAGE_ROOT / "validation"
        shared_local = {key: local[key] for key in ("checkedAtUtc", "scope", "tests", "offlineExecution",
                        "sampleHashesUnchanged", "h04Integration", "s1Status", "contractBaseline") if key in local}
        shared_local.update(status="PASS" if local_passed else "FAIL", sourceHashes=local.get("codeHashes", {}),
                            postgresVerification=pg_state)
        shared_pg = {key: pg[key] for key in (
            "status", "checkedAtUtc", "postgresExecuted", "schemaApplied", "schemaSha256", "testsRun", "testGroups",
            "failures", "errors", "skipped", "platformTables", "goldenProcessingRows", "goldenFacts", "goldenQueryCases",
            "finalFactsIncludingConcurrencyProbes", "taskPublisher", "m03ControlProvider", "h04Integration",
            "clusterStopExitCode", "sourceHashes", "contractBaseline", "errorCode", "nativeToolChecks") if key in pg}
        shared_pg["scope"] = "Current 165-table synthetic data suite; formal H04/approval services are not covered"
        shared_pg["s1Status"] = "INCOMPLETE"
        # Keep full environment details in local artifacts; share version numbers only.
        versions = pg.get("versions", {})
        for field, text, pattern in (
                ("postgresVersion", versions.get("postgres", ""), r"PostgreSQL (\d+(?:\.\d+)+)"),
                ("postgisVersion", versions.get("postgis", ""), r'POSTGIS="(\d+(?:\.\d+)+)')):
            match = re.search(pattern, text)
            if match:
                shared_pg[field] = match[1]
        save(validation / (PREFIX + "-local.json"), shared_local)
        save(validation / (PREFIX + "-postgres.json"), shared_pg)
        if local_passed:
            (validation / (PREFIX + "-local-tests.txt")).write_bytes((local_dir / "unit-tests.txt").read_bytes().replace(b"\r\n", b"\n"))
        if pg_passed:
            pg_artifacts = Path(pg["runDirectory"]) / "artifacts"
            check(pg_artifacts.resolve().is_relative_to(OUTPUT_ROOT), "PG evidence escaped output root", "PG_EVIDENCE_INVALID")
            for original, target in (("integration-tests.txt", "postgres-tests.txt"),
                    ("contract-approval-constraints.json", "approval-constraints.json"),
                    ("contract-decision-append-only.json", "decision-append-only.json")):
                (validation / (PREFIX + "-" + target)).write_bytes((pg_artifacts / original).read_bytes().replace(b"\r\n", b"\n"))
        result["localVerification"] = "PASS" if local_passed else "FAIL"
        result["postgresVerification"] = pg_state
        result["localTests"] = local.get("tests", {}).get("total", 0)
        result["postgresTestsExecuted"] = pg.get("testsRun", 0)
        result["commandExitCodes"]["staticPackage"] = execute(
            [sys.executable, "-B", PACKAGE_ROOT / "verify_package.py", "--validation-prefix", PREFIX + "-static"],
            destination / "static-package.txt", "static-package")
        required_path = validation / (PREFIX + "-package-checks.json")
        required_before = file_hash(required_path)
        result["commandExitCodes"]["requiredPackage"] = execute(
            [sys.executable, "-B", PACKAGE_ROOT / "verify_package.py", "--validation-prefix", PREFIX, "--require-validation"],
            destination / "required-package.txt", "required-package")
        check(checker["source_hashes"]() == sources, "Source changed during acceptance", "SOURCE_CHANGED_DURING_TEST")
        static_passed = result["commandExitCodes"]["staticPackage"] == 0
        fresh_required = required_path.is_file() and file_hash(required_path) != required_before
        required = read_json(required_path) if fresh_required else {}
        required_current = (fresh_required and required.get("sourceHashes") == sources
                            and required.get("contractBaseline") == contract
                            and required.get("validationRequired") is True)
        required_passed = (required_current and required.get("status") == "PASS"
                           and result["commandExitCodes"]["requiredPackage"] == 0)
        complete = local_passed and pg_passed and static_passed and required_passed
        pending = (local_passed and static_passed and pg_state == "PENDING" and required_current
                   and required.get("status") == "FAIL" and required.get("pendingPostgresOnly") is True
                   and result["commandExitCodes"]["requiredPackage"] == 1)
        result["status"] = "PASS" if complete else ("PENDING_POSTGRES" if pending else "FAIL")
    except (WorkerError, OSError, ValueError, KeyError, subprocess.SubprocessError) as exc:
        result.update(errorCode=getattr(exc, "code", "D3_12R_FAILED"), errorType=type(exc).__name__)
    result["checkedAtUtc"] = datetime.now(timezone.utc).isoformat()
    save(destination / "acceptance.json", result)
    save(OUTPUT_ROOT / "d3-12r/latest.json", {"status": result["status"], "runDirectory": result["runDirectory"],
         "acceptanceSha256": file_hash(destination / "acceptance.json")})
    print(json.dumps({k: v for k, v in result.items() if k != "sourceHashes"}, ensure_ascii=False, indent=2))
    return 0 if result["status"] == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
