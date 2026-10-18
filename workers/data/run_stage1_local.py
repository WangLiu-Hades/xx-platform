"""Run offline S1 checks and export an honest acceptance bundle; never start PostgreSQL."""
import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import unittest
import uuid

sys.dont_write_bytecode = True
sys.stdout.reconfigure(encoding="utf-8")
PROJECT = Path(__file__).resolve().parent
PACKAGE_ROOT = PROJECT
from data_worker.common import WorkerError, canonical, check, file_digest, read_json
from data_worker.metrics import audit_export
from data_worker.settings import output_path, OUTPUT_ROOT

def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def lines(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]

def snapshot(root):
    return {p.relative_to(root).as_posix(): file_digest(p) for p in sorted(root.rglob("*"))
            if p.is_file() and not any(part in ("__pycache__", ".artifacts", ".venv-pg", "validation")
                                      for part in p.relative_to(root).parts)}

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="New directory under the configured artifact directory")
    args = parser.parse_args(argv)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S")
    default = OUTPUT_ROOT / "s1/runs" / (stamp + "-" + uuid.uuid4().hex[:8])
    try:
        destination = output_path(args.output) if args.output else default
        check(not destination.exists(), "Choose a new output directory", "OUTPUT_EXISTS")
        destination.mkdir(parents=True)
    except (WorkerError, OSError) as exc:
        print(json.dumps({"localChecks": "FAIL", "errorType": type(exc).__name__}))
        return 1

    samples = PACKAGE_ROOT / "fixtures/plate-v0"
    frozen = snapshot(samples)
    source_before = snapshot(PROJECT)
    report = {"checkedAtUtc": datetime.now(timezone.utc).isoformat(), "localChecks": "FAIL",
              "s1Status": "INCOMPLETE", "postgresExecutedThisRun": False,
              "h04Integration": "NOT_IMPLEMENTED", "vendorProtocol": "PENDING",
              "scope": "Synthetic SQLite, M03 execution/read contracts, regression and engineering metrics"}
    try:
        suites, counts = [], {}
        for folder in ("tests", "tests_pg_unit", "tests_metrics", "tests_m03"):
            suite = unittest.TestLoader().discover(str(PROJECT / folder), pattern="test_*.py")
            suites.append(suite)
            counts[folder] = suite.countTestCases()
        stream = io.StringIO()
        result = unittest.TextTestRunner(stream=stream, verbosity=2).run(unittest.TestSuite(suites))
        (destination / "unit-tests.txt").write_text(stream.getvalue(), encoding="utf-8")
        report["tests"] = {"counts": counts, "total": result.testsRun, "failures": len(result.failures),
                           "errors": len(result.errors), "skipped": len(result.skipped)}
        check(result.wasSuccessful() and not result.skipped, "Local regression failed", "LOCAL_TESTS_FAILED")

        def invoke(name, batches):
            command = [sys.executable, "-B", str(PROJECT / "run_worker.py"), "--state",
                       str(destination / "state.sqlite3"), "--output", str(destination / name)]
            for batch in batches:
                command += ["--batch", batch]
            done = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60,
                                  creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            (destination / (name + ".log.jsonl")).write_bytes(done.stdout)
            (destination / (name + ".stderr.txt")).write_bytes(done.stderr)
            check(done.returncode == 0, "Worker process failed: " + name, "WORKER_PROCESS_FAILED")
            return audit_export(destination / name)

        initial = invoke("initial", ["B01_INITIAL"])
        complete = invoke("after-restart", ["B02_REPLAY", "B03_INCREMENTAL", "B04_SOURCE_NAMESPACE"])
        retry = invoke("retry", [])
        save(destination / "metrics-initial.json", initial)
        save(destination / "metrics.json", complete)
        save(destination / "metrics-retry.json", retry)

        actual = lines(destination / "after-restart/rows.jsonl")
        expected = lines(samples / "expected/rows.jsonl")
        row_match = len(actual) == len(expected) and all(
            {k: row.get(k) for k in expectation if k != "caseId"} ==
            {k: v for k, v in expectation.items() if k != "caseId"}
            for row, expectation in zip(actual, expected))
        fact_index = lambda values: {(v["source"], v["sourceRecordKey"]):
                                      {k: x for k, x in v.items() if k != "caseId"} for v in values}
        actual_facts = lines(destination / "after-restart/captures.jsonl")
        expected_facts = lines(samples / "expected/captures.jsonl")
        fact_match = (len(actual_facts) == len(expected_facts) and
                      fact_index(actual_facts) == fact_index(expected_facts))
        batch_match = read_json(destination / "after-restart/summary.json")["batches"] == \
                      read_json(samples / "expected/summary.json")["batches"]
        same_bytes = all((destination / "after-restart" / name).read_bytes() ==
                         (destination / "retry" / name).read_bytes()
                         for name in ("rows.jsonl", "captures.jsonl", "quality.jsonl", "summary.json"))
        retry_events = [event for event in lines(destination / "retry.log.jsonl")
                        if event["event"] == "batch.committed"]
        retry_cached = len(retry_events) == 4 and all(event["cached"] for event in retry_events)
        report["offlineExecution"] = {
            "processes": 3, "goldenRowsMatch": row_match, "goldenFactsMatch": fact_match,
            "goldenBatchesMatch": batch_match, "retryBytesUnchanged": same_bytes,
            "allRetryBatchesCached": retry_cached, "processedRows": len(actual), "factInventory": len(actual_facts),
            "metricsAudit": complete["auditStatus"]}
        check(all((row_match, fact_match, batch_match, same_bytes, retry_cached)),
              "Actual execution differs from frozen expectations", "OFFLINE_ACCEPTANCE_FAILED")
        report["sampleHashesUnchanged"] = frozen == snapshot(samples)
        check(report["sampleHashesUnchanged"], "Frozen sample changed", "SAMPLE_CHANGED")
        pg_path = OUTPUT_ROOT / "d3-05-06/postgres-checks.json"
        pg_record = read_json(pg_path) if pg_path.is_file() else {
            "status": "NOT_RUN", "postgresExecuted": False,
            "reason": "Run run_pg_lab.py to verify this checkout against PostgreSQL."}
        report["postgresEvidence"] = {
            "path": pg_path.relative_to(OUTPUT_ROOT).as_posix(), "sha256": file_digest(pg_path) if pg_path.is_file() else None,
            "recorded": pg_record}
        pg_passed = (pg_record.get("status") == "PASS" and pg_record.get("postgresExecuted") is True
                     and pg_record.get("schemaApplied") is True and type(pg_record.get("testsRun")) is int
                     and pg_record["testsRun"] >= 12
                     and pg_record["testsRun"] == sum(pg_record.get("testGroups", {}).values())
                     and pg_record.get("testGroups", {}).get("m03", 0) > 0
                     and pg_record.get("sourceHashes") == source_before
                     and all(pg_record.get(key) == 0 for key in ("failures", "errors", "skipped"))
                     and pg_record.get("clusterStopExitCode") == 0)
        report["postgresVerification"] = "PASS" if pg_passed else "PENDING"
        report["openItems"] = [
            "DEV-01 agreement and actual H04 claim/renew/cancel/retry/publication integration",
            "Agree bounded ingestion limits, quality policy and public cursor/session ownership with DEV-01",
            "Business approval of P-006/P-020 and missing source counters/telemetry",
            "DEV-04 independent reproduction"]
        if not pg_passed:
            report["openItems"].insert(0, "Run the current PostgreSQL suite against this source snapshot and collect plans/control probes")
        check(snapshot(PROJECT) == source_before, "Worker source changed during local validation",
              "SOURCE_CHANGED_DURING_TEST")
        report["localChecks"] = "PASS"
    except (WorkerError, OSError, ValueError, subprocess.SubprocessError) as exc:
        report["errorCode"] = getattr(exc, "code", "LOCAL_REVIEW_FAILED")
        report["errorType"] = type(exc).__name__
        # Do not copy raw input, environment variables, DSNs or SQL to the report.
    report["codeHashes"] = source_before
    report["sampleHashes"] = frozen
    report["artifactHashes"] = {p.relative_to(destination).as_posix(): file_digest(p)
                                for p in sorted(destination.rglob("*")) if p.is_file()}
    save(destination / "acceptance.json", report)
    latest = OUTPUT_ROOT / "s1/latest.json"
    latest.parent.mkdir(parents=True, exist_ok=True)
    save(latest, {"localChecks": report["localChecks"], "s1Status": report["s1Status"],
                  "runDirectory": destination.relative_to(OUTPUT_ROOT).as_posix(),
                  "acceptanceSha256": file_digest(destination / "acceptance.json")})
    print(json.dumps({k: v for k, v in report.items()
                      if k not in ("codeHashes", "sampleHashes", "artifactHashes", "postgresEvidence")},
                     ensure_ascii=False, indent=2))
    print("Evidence: " + str(destination))
    return 0 if report["localChecks"] == "PASS" else 1

if __name__ == "__main__":
    raise SystemExit(main())
