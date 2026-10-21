"""Pinned D3-12R contract checks; old fixtures and reports remain immutable."""
from pathlib import Path
import hashlib
import json
import re

from .common import check

PACKAGE = Path(__file__).resolve().parents[1]
REPOSITORY = PACKAGE.parents[1]
BASELINE = PACKAGE / "fixtures/contract-pd-2026-10-19-v1.json"

# PD-2026-10-19.v1 limits. Retention (12 calendar months) is independent.
QUERY_WINDOW_SECONDS = 31 * 86400
STATEMENT_TIMEOUT_MS = 5000
CURSOR_SECONDS = 15 * 60
INGEST_MAX_BYTES = 100 * 1024 * 1024
INGEST_MAX_ROWS = 100000


def baseline():
    return json.loads(BASELINE.read_text(encoding="utf-8"))


def verify_contracts(repository=REPOSITORY):
    """Verify all pinned inputs before starting a suite or claiming current acceptance."""
    repository = Path(repository).resolve()
    expected = baseline()
    actual = {}
    for name, digest in expected["sources"].items():
        path = (repository / name).resolve()
        check(path.is_relative_to(repository) and path.is_file(),
              "Required contract file is missing: " + name, "CONTRACT_BASELINE_CHANGED")
        actual[name] = hashlib.sha256(path.read_bytes()).hexdigest()
        check(actual[name] == digest, "Reviewed contract differs: " + name, "CONTRACT_BASELINE_CHANGED")
    sql = (repository / "contracts/database/schema.sql").read_text(encoding="utf-8")
    tables = sorted(re.findall(r"^CREATE TABLE ([a-z_]+\.[a-z_]+) \(", sql, re.MULTILINE))
    check(tables == expected["platformTables"] and len(tables) == expected["platformTableCount"],
          "Platform table catalog differs from the reviewed contract", "CONTRACT_CATALOG_CHANGED")
    api = (repository / "contracts/api/openapi.yaml").read_text(encoding="utf-8")
    version = re.search(r'^  version: "([^"]+)"$', api, re.MULTILINE)
    check(version is not None and version[1] == expected["openapiVersion"],
          "OpenAPI version differs", "CONTRACT_CATALOG_CHANGED")
    mirror = json.loads((repository / "apps/web/src/api/contract.json").read_text(encoding="utf-8"))
    operations, schemas = mirror["operations"], mirror["components"]["schemas"]
    operation_ids = re.findall(r'^      operationId: "?([A-Za-z][A-Za-z0-9_]*)"?$', api, re.MULTILINE)
    check(len(operations) == len(operation_ids) == expected["operationCount"] and
          set(operations) == set(operation_ids) and len(schemas) == expected["schemaCount"],
          "Generated mirror/catalog does not match the pinned OpenAPI", "CONTRACT_CATALOG_CHANGED")
    for name, route in expected["m03Operations"].items():
        check(all(operations[name][key] == value for key, value in route.items()),
              "M03 operation route differs: " + name, "CONTRACT_CATALOG_CHANGED")
    check(set(expected["requiredSchemas"]) <= set(schemas),
          "A data DTO is missing", "CONTRACT_CATALOG_CHANGED")
    limits = dict(queryWindowSeconds=QUERY_WINDOW_SECONDS, statementTimeoutMs=STATEMENT_TIMEOUT_MS,
                  cursorSeconds=CURSOR_SECONDS, ingestMaxBytes=INGEST_MAX_BYTES, ingestMaxRows=INGEST_MAX_ROWS)
    check(limits == expected["limits"], "Runtime limits differ from the reviewed policy", "CONTRACT_CATALOG_CHANGED")
    return {"baselineId": expected["baselineId"], "repositoryCommit": expected["repositoryCommit"],
            "policyVersion": expected["policyVersion"], "openapiVersion": expected["openapiVersion"],
            "platformTableCount": len(tables), "operationCount": len(operations), "schemaCount": len(schemas),
            "sourceHashes": actual}


def pg_evidence_state(record, sources, contract):
    """Separate a verified pass, a known unrun prerequisite and failed/invalid evidence."""
    if pg_evidence_matches(record, sources, contract):
        return "PASS"
    pending_codes = {"PG_EXPLICITLY_SKIPPED", "PG_NATIVE_TOOLS_MISSING",
                     "PG_NATIVE_TOOLS_UNAVAILABLE", "PG_VENV_FAILED", "PG_DRIVER_INSTALL_FAILED"}
    pending = (record.get("status") == "NOT_RUN" and record.get("postgresExecuted") is False
               and record.get("schemaApplied") in (None, False)
               and record.get("errorCode") in pending_codes
               and record.get("sourceHashes") == sources and record.get("contractBaseline") == contract
               and all(type(record.get(k, 0)) is int and record.get(k, 0) == 0
                       for k in ("testsRun", "failures", "errors", "skipped"))
               and record.get("clusterStopExitCode") in (None, 0))
    return "PENDING" if pending else "FAIL"


def pg_evidence_matches(record, sources, contract):
    """Historical/NOT_RUN evidence cannot satisfy the current contract and source gate."""
    groups = record.get("testGroups", {})
    return (record.get("status") == "PASS" and record.get("postgresExecuted") is True
            and record.get("schemaApplied") is True and record.get("contractBaseline") == contract
            and record.get("schemaSha256") == contract["sourceHashes"]["contracts/database/schema.sql"]
            and record.get("platformTables") == contract["platformTableCount"]
            and isinstance(groups, dict) and all(type(v) is int and v > 0 for v in groups.values())
            and all(groups.get(name, 0) > 0 for name in ("platform", "m03", "identity", "d3_12r"))
            and type(record.get("testsRun")) is int and record["testsRun"] == sum(groups.values())
            and record.get("sourceHashes") == sources
            and all(type(record.get(k)) is int and record[k] == 0 for k in
                    ("failures", "errors", "skipped", "clusterStopExitCode")))
