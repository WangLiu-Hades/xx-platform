"""Record pure PostgreSQL-module tests plus the existing offline regression; no PG claim."""
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import sys
import unittest

sys.dont_write_bytecode = True
sys.stdout.reconfigure(encoding="utf-8")
from data_worker.settings import OUTPUT_ROOT

PROJECT = Path(__file__).resolve().parent
PACKAGE_ROOT = PROJECT
EVIDENCE = OUTPUT_ROOT / "d3-05-06"


def main():
    samples = PACKAGE_ROOT / "fixtures/plate-v0"
    before = {p.relative_to(samples).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in samples.rglob("*") if p.is_file()}
    loader = unittest.TestLoader()
    old = loader.discover(str(PROJECT / "tests"), pattern="test_*.py")
    new = unittest.TestLoader().discover(str(PROJECT / "tests_pg_unit"), pattern="test_*.py")
    old_count, new_count = old.countTestCases(), new.countTestCases()
    stream = io.StringIO()
    result = unittest.TextTestRunner(stream=stream, verbosity=2).run(unittest.TestSuite([old, new]))
    after = {p.relative_to(samples).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
             for p in samples.rglob("*") if p.is_file()}
    passed = result.wasSuccessful() and not result.skipped and before == after
    report = {"status": "PASS" if passed else "FAIL", "checkedAtUtc": datetime.now(timezone.utc).isoformat(),
              "offlineRegressionTests": old_count, "postgresModuleUnitTests": new_count,
              "testsRun": result.testsRun, "failures": len(result.failures), "errors": len(result.errors),
              "skipped": len(result.skipped), "fixturesUnchanged": before == after,
              "postgresExecuted": False, "scope": "Pure contracts, planner, cursor, projection; existing SQLite regression"}
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (EVIDENCE / "unit-tests.txt").write_text(stream.getvalue(), encoding="utf-8")
    (EVIDENCE / "unit-checks.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(stream.getvalue())
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
