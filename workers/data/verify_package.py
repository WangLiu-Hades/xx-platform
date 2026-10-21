"""Check the portable package, frozen fixture bytes and documentation links."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import sys
from urllib.parse import unquote
from data_worker.common import WorkerError
from data_worker.contracts import verify_contracts, pg_evidence_state

sys.dont_write_bytecode = True
sys.stdout.reconfigure(encoding="utf-8")
ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]
IGNORED = {"__pycache__", ".artifacts", ".venv-pg"}

def read(path):
    return json.loads(path.read_text(encoding="utf-8"))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def files():
    return sorted(p for p in ROOT.rglob("*") if p.is_file() and
                  not any(part in IGNORED for part in p.relative_to(ROOT).parts))

def source_hashes():
    return {p.relative_to(ROOT).as_posix(): sha(p) for p in files()
            if "validation" not in p.relative_to(ROOT).parts}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-validation", action="store_true")
    parser.add_argument("--validation-prefix", default="d3-12r",
                        help="Evidence family under validation; preserves earlier reports")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9-]{0,63}", args.validation_prefix):
        parser.error("--validation-prefix must use lowercase letters, digits and hyphens")
    prefix = args.validation_prefix
    if prefix in {"m03", "identity-v2"}:
        parser.error("Historical evidence families are immutable; choose a new prefix")
    errors, links = [], 0
    def expect(ok, message):
        if not ok:
            errors.append(message)
    package = files()
    fixture = ROOT / "fixtures/plate-v0"
    manifest = read(fixture / "manifest.json")
    for name, expected in manifest["files"].items():
        expect(sha(fixture / name) == expected, "Frozen fixture differs: " + name)
    for folder in ("after-restart", "retry"):
        export = ROOT / "fixtures/metrics" / folder
        for name, expected in read(export / "export.json")["files"].items():
            expect(sha(export / name) == expected, "Metric fixture differs: " + folder + "/" + name)
    contract = None
    try:
        contract = verify_contracts(REPO)
    except (WorkerError, OSError, ValueError, KeyError) as exc:
        errors.append("Current contract check failed: " + getattr(exc, "code", type(exc).__name__))
    for path in package:
        relative = path.relative_to(ROOT).as_posix()
        expect(not path.is_symlink(), "Unexpected symlink: " + relative)
        expect(path.stat().st_size < 5_000_000, "Unexpected large package file: " + relative)
        data = path.read_bytes()
        try:
            text = data.decode("utf-8-sig")
        except UnicodeDecodeError:
            errors.append("Unexpected binary package file: " + relative)
            continue
        expect(not re.search(r"[A-Za-z]:[\\/]+Users[\\/]", text), "Personal absolute path: " + relative)
        expect(not re.search(r"-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----|github_pat_[A-Za-z0-9_]{20,}|ghp_[A-Za-z0-9]{30,}|AKIA[A-Z0-9]{16}", text),
               "Credential-like content: " + relative)
        if path.suffix == ".py":
            try:
                compile(text, str(path), "exec")
            except SyntaxError:
                errors.append("Invalid Python syntax: " + relative)
        if path.suffix == ".md":
            for link in re.findall(r"\[[^\]]*\]\(([^)]+)\)", text):
                link = link.strip().strip("<>")
                if re.match(r"^(https?://|mailto:|#)", link):
                    continue
                target = (path.parent / unquote(link.split("#", 1)[0])).resolve()
                links += 1
                expect(target.is_file() or target.is_dir(), "Broken link in " + relative + ": " + link)
    hashes = source_hashes()
    pending_postgres_only = False
    if args.require_validation:
        local = read(ROOT / ("validation/" + prefix + "-local.json"))
        pg = read(ROOT / ("validation/" + prefix + "-postgres.json"))
        expect(local.get("status") == "PASS" and local.get("tests", {}).get("total", 0) >= 67
               and local["tests"].get("total") == sum(local["tests"].get("counts", {}).values())
               and local["tests"].get("counts", {}).get("tests_m03", 0) > 0
               and local["tests"].get("counts", {}).get("tests_contracts", 0) > 0, "Local validation missing")
        expect(local.get("sourceHashes") == hashes, "Local evidence does not match package source")
        expect(contract is not None and local.get("contractBaseline") == contract, "Local evidence contract differs")
        for field in ("failures", "errors", "skipped"):
            count = local.get("tests", {}).get(field)
            expect(type(count) is int and count == 0, "Local " + field + " count is missing or nonzero")
        pg_state = pg_evidence_state(pg, hashes, contract) if contract is not None else "FAIL"
        pending_postgres_only = not errors and pg_state == "PENDING"
        expect(pg.get("sourceHashes") == hashes, "PostgreSQL evidence does not match package source")
        expect(pg_state == "PASS", "Current 165-table PostgreSQL acceptance is pending"
               if pg_state == "PENDING" else "Current 165-table PostgreSQL acceptance failed or is invalid")
        # NOT_RUN has no test counters or cluster to stop. Missing counters are not failed tests.
        if pg.get("postgresExecuted") is True:
            expect(pg.get("clusterStopExitCode") == 0, "PostgreSQL cluster stop is missing or failed")
            for field in ("failures", "errors", "skipped"):
                count = pg.get(field)
                expect(type(count) is int and count == 0, "PostgreSQL " + field + " count is missing or nonzero")
    report = {
        "status": "PASS" if not errors else "FAIL", "checkedAtUtc": datetime.now(timezone.utc).isoformat(),
        "scope": "Package syntax, links, frozen fixtures, schema digest and evidence/source alignment",
        "packageFiles": len(package), "localLinks": links, "frozenDataFiles": len(manifest["files"]),
        "validationRequired": args.require_validation, "sourceHashes": hashes, "errors": errors,
        "postgresExecutedByThisCheck": False, "contractBaseline": contract,
        "pendingPostgresOnly": pending_postgres_only,
    }
    (ROOT / ("validation/" + prefix + "-package-checks.json")).write_bytes((json.dumps(report, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
    print(json.dumps({k: v for k, v in report.items() if k != "sourceHashes"}, ensure_ascii=False, indent=2))
    return 0 if not errors else 1

if __name__ == "__main__":
    raise SystemExit(main())
