"""Internal runner used by run_pg_lab.py, against its newly-created local cluster only."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
import runpy
from pathlib import Path
import sys
import uuid

sys.dont_write_bytecode = True
sys.stdout.reconfigure(encoding="utf-8")
from data_worker.common import WorkerError, check, read_json
from data_worker.pg.connection import connect
from lab.diagnostics import failure_details
from data_worker.settings import PACKAGE_ROOT, output_path, OUTPUT_ROOT, REPO_ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cluster-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--admin-env", default="D3_LAB_ADMIN_DSN")
    args = parser.parse_args()
    output = output_path(args.output)
    output.mkdir(parents=True, exist_ok=False)
    report = {"status": "NOT_RUN", "postgresExecuted": False, "schemaApplied": False,
              "phase": "prepare", "stages": []}

    def stage(name, **fields):
        report["phase"] = name
        report["stages"].append({"name": name, "atUtc": datetime.now(timezone.utc).isoformat(), **fields})
        (output / "integration-progress.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"event": "pg.stage", "phase": name, **fields}), flush=True)

    checker = runpy.run_path(str(PACKAGE_ROOT / "verify_package.py"))
    source_before = checker["source_hashes"]()
    report["sourceHashes"] = source_before
    admin = database = None
    try:
        stage("load_driver")
        from psycopg import sql
        from psycopg.conninfo import make_conninfo
        cluster = output_path(args.cluster_dir)
        marker = read_json(cluster.parent / "lab-owner.json")
        check(marker.get("purpose") == "synthetic-d3-05-06" and marker.get("dataDirectory") == str(cluster),
              "Cluster ownership marker is missing", "PG_LAB_REQUIRED")
        stage("connect_owned_cluster")
        dsn = os.environ.get(args.admin_env)
        admin = connect(dsn)
        actual = admin.execute("SHOW data_directory").fetchone()["data_directory"]
        check(Path(actual).resolve() == cluster, "Connected server is not the newly-created test cluster", "PG_LAB_REQUIRED")
        report["postgresExecuted"] = True
        stage("verify_schema_baseline")
        baseline = read_json(PACKAGE_ROOT / "fixtures/contract-baseline.json")["sources"]
        schema_path = REPO_ROOT / "contracts/database/schema.sql"
        schema_bytes = schema_path.read_bytes()
        schema_hash = hashlib.sha256(schema_bytes).hexdigest()
        check(schema_hash == baseline["contracts/database/schema.sql"],
              "Platform schema changed; review the new contract before integration", "SCHEMA_BASELINE_CHANGED")
        database_name = "d3_lab_" + uuid.uuid4().hex[:16]
        stage("create_isolated_database")
        admin.execute(sql.SQL("CREATE DATABASE {} TEMPLATE template0").format(sql.Identifier(database_name)))
        report["databaseName"], report["schemaSha256"] = database_name, schema_hash
        database_dsn = make_conninfo(dsn, dbname=database_name)
        database = connect(database_dsn)
        # Execute the original complete schema, including foreign keys, indexes and triggers.
        stage("apply_original_schema")
        database.execute(schema_bytes.decode("utf-8"), prepare=False)
        database.execute("CREATE SCHEMA dev3_test; CREATE TABLE dev3_test.marker(name text PRIMARY KEY,value text NOT NULL);")
        database.execute("INSERT INTO dev3_test.marker VALUES('purpose','synthetic-d3-05-06')")
        report["schemaApplied"] = True
        stage("verify_schema_tables")
        table_count = database.execute("""
            SELECT count(*) AS n FROM information_schema.tables
            WHERE table_schema=ANY(%s::text[]) AND table_type='BASE TABLE'
            """, (["auth", "core", "capture", "analysis", "operation", "audit"],)).fetchone()["n"]
        check(table_count == 163, "Unexpected platform table count", "SCHEMA_INCOMPLETE")
        database.close()
        database = None
        from lab.integration import run_integration
        report.update(run_integration(database_dsn, output_path(args.assets), output, progress=stage))
        report["platformTables"] = table_count
        check(checker["source_hashes"]() == source_before, "Worker source changed during PG validation",
              "SOURCE_CHANGED_DURING_TEST")
        stage("completed")
    except Exception as exc:
        report.update(status="FAIL" if report["postgresExecuted"] else "NOT_RUN",
                      errorCode=getattr(exc, "code", "PG_INTEGRATION_FAILED"),
                      failedPhase=report["phase"])
        report.update(failure_details(exc))
    finally:
        for connection in (database, admin):
            if connection is not None:
                connection.close()
        report["checkedAtUtc"] = datetime.now(timezone.utc).isoformat()
        (output / "integration-checks.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                                                       encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "sourceHashes"}, ensure_ascii=False, indent=2))
    return 0 if report["status"] == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
