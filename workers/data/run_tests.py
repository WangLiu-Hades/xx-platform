"""Run offline behavior tests and record evidence below the configured artifact directory/d3-03-04."""
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import sqlite3
import sys
import unittest

sys.dont_write_bytecode = True
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

from data_worker.settings import OUTPUT_ROOT

PROJECT = Path(__file__).resolve().parent
PACKAGE_ROOT = PROJECT
SAMPLES = PACKAGE_ROOT / "fixtures/plate-v0"
EVIDENCE = OUTPUT_ROOT / "d3-03-04"


def hashes():
    return {path.relative_to(SAMPLES).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in SAMPLES.rglob("*") if path.is_file()}


def main():
    before = hashes()
    manifest = json.loads((SAMPLES / "manifest.json").read_text(encoding="utf-8"))
    frozen_ok = all(before.get(name) == value for name, value in manifest["files"].items())
    suite = unittest.defaultTestLoader.discover(str(PROJECT / "tests"), pattern="test_*.py")
    stream = io.StringIO()
    result = unittest.TextTestRunner(stream=stream, verbosity=2).run(suite)
    unchanged = before == hashes()
    passed = result.wasSuccessful() and frozen_ok and unchanged and not result.skipped
    report = {
        "status": "PASS" if passed else "FAIL",
        "checkedAtUtc": datetime.now(timezone.utc).isoformat(),
        "pythonVersion": sys.version.split()[0], "sqliteVersion": sqlite3.sqlite_version,
        "testsRun": result.testsRun, "failures": len(result.failures), "errors": len(result.errors),
        "skipped": len(result.skipped), "fixtureManifestValid": frozen_ok, "fixturesUnchanged": unchanged,
        "sampleHashes": before,
        "scope": ["offline PLATE parser and normalization", "frozen 72-row / 16-fact regression",
                  "strict JSONL isolation", "source-scoped deduplication", "restart and replay",
                  "batch rollback and retry", "version and path guards", "structured logs and quality export",
                  "16 actual DTOs checked against existing OpenAPI subset"],
        "postgresExecuted": False, "h04Integrated": False, "vendorProtocolApproved": False,
    }
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (EVIDENCE / "tests.txt").write_text(stream.getvalue(), encoding="utf-8")
    (EVIDENCE / "checks.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(stream.getvalue())
    print(json.dumps({key: value for key, value in report.items() if key != "sampleHashes"},
                     ensure_ascii=False, indent=2))
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
