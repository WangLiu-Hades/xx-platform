"""Create a separate local PG cluster, verify the original schema and worker, then stop it."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import uuid

sys.dont_write_bytecode = True
sys.stdout.reconfigure(encoding="utf-8")
from data_worker.common import WorkerError, check, read_json
from data_worker.settings import PACKAGE_ROOT, output_path, OUTPUT_ROOT

PROJECT = Path(__file__).resolve().parent
EVIDENCE = OUTPUT_ROOT / "d3-05-06"


def find_postgres_bin(explicit=None):
    """Prefer an explicit path, then PATH, then the installed Windows PG 16 tools."""
    if explicit is not None:
        return Path(explicit).resolve()
    found = shutil.which("pg_ctl")
    if found:
        return Path(found).resolve().parent
    if os.name == "nt":
        for variable in ("ProgramW6432", "ProgramFiles", "ProgramFiles(x86)"):
            base = os.environ.get(variable)
            if not base:
                continue
            candidate = Path(base) / "PostgreSQL" / "16" / "bin"
            if all((candidate / name).is_file() for name in ("pg_ctl.exe", "initdb.exe")):
                return candidate.resolve()
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--postgres-bin", type=Path, help="Directory containing initdb and pg_ctl")
    args = parser.parse_args()
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S") + "-" + uuid.uuid4().hex[:8]
    root = output_path(EVIDENCE / "pg-lab" / run_id)
    root.mkdir(parents=True, exist_ok=False)
    data = root / "data"
    flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    environment = dict(os.environ, PYTHONDONTWRITEBYTECODE="1")
    report = {"status": "NOT_RUN", "postgresExecuted": False, "runDirectory": str(root),
              "existingServerModified": False, "nativeToolChecks": []}
    pg_ctl = None

    def run(command, name, timeout=60, env=None):
        with (root / (name + ".log")).open("w", encoding="utf-8") as stream:
            result = subprocess.run([str(v) for v in command], stdout=stream, stderr=subprocess.STDOUT,
                                    text=True, encoding="utf-8", errors="replace", timeout=timeout,
                                    creationflags=flags, env=env or environment)
        return result.returncode

    try:
        candidate = find_postgres_bin(args.postgres_bin)
        check(candidate is not None, "PostgreSQL tools were not found", "PG_NATIVE_TOOLS_MISSING")
        pg_bin = candidate.resolve()
        report["postgresBin"] = str(pg_bin)
        pg_ctl = pg_bin / ("pg_ctl.exe" if os.name == "nt" else "pg_ctl")
        initdb = pg_bin / ("initdb.exe" if os.name == "nt" else "initdb")
        for executable in (initdb, pg_ctl):
            check(executable.is_file(), "A PostgreSQL executable is missing", "PG_NATIVE_TOOLS_MISSING")
            code = run([executable, "--version"], executable.stem + "-version", 15)
            report["nativeToolChecks"].append({"tool": executable.stem, "exitCode": code})
        check(all(row["exitCode"] == 0 for row in report["nativeToolChecks"]),
              "PostgreSQL tools could not start in this process environment", "PG_NATIVE_TOOLS_UNAVAILABLE")

        # Isolated Python dependency installation; never change the user's global interpreter.
        venv = OUTPUT_ROOT / ".venv-pg"
        python = venv / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
        if not python.is_file():
            check(run([sys.executable, "-m", "venv", venv], "venv", 60) == 0,
                  "Could not create the local Python environment", "PG_VENV_FAILED")
        probe = run([python, "-c", "import psycopg; assert psycopg.__version__ == '3.2.13'"], "driver-probe", 15)
        if probe != 0:
            check(run([python, "-m", "pip", "install", "--disable-pip-version-check",
                       "-r", PROJECT / "requirements-postgres.txt"], "dependencies", 300) == 0,
                  "Could not install the pinned PostgreSQL driver", "PG_DRIVER_INSTALL_FAILED")

        owner = {"purpose": "synthetic-d3-05-06", "dataDirectory": str(data.resolve()), "runId": run_id}
        (root / "lab-owner.json").write_text(json.dumps(owner, indent=2) + "\n", encoding="utf-8")
        check(run([initdb, "-D", data, "-U", "d3_lab_admin", "--auth-local=trust", "--auth-host=trust",
                   "--encoding=UTF8", "--locale=C", "--no-instructions"], "initdb", 60) == 0,
              "Could not initialize the separate test cluster", "PG_INITDB_FAILED")
        with socket.socket() as probe_socket:
            probe_socket.bind(("127.0.0.1", 0))
            port = probe_socket.getsockname()[1]
        # Only loopback is exposed; no Windows service, system configuration or existing data is changed.
        check(run([pg_ctl, "-D", data, "-l", root / "postgres.log",
                   "-o", "-h 127.0.0.1 -p " + str(port), "-w", "start"], "start", 60) == 0,
              "Could not start the separate test cluster", "PG_START_FAILED")
        child_env = dict(environment, D3_LAB_ADMIN_DSN=(
            "host=127.0.0.1 port=" + str(port) + " user=d3_lab_admin dbname=postgres sslmode=disable"))
        exit_code = run([python, PROJECT / "run_pg_integration.py", "--cluster-dir", data,
                         "--output", root / "artifacts", "--assets", root / "assets"], "verification", 300, child_env)
        checks = root / "artifacts/integration-checks.json"
        if checks.is_file():
            report.update(read_json(checks))
        else:
            report.update(status="FAIL", errorCode="PG_INTEGRATION_REPORT_MISSING")
        report["integrationExitCode"] = exit_code
        if exit_code != 0 and report["status"] == "PASS":
            report["status"] = "FAIL"
    except Exception as exc:
        report.update(status="NOT_RUN" if not report["postgresExecuted"] else "FAIL",
                      errorCode=getattr(exc, "code", "PG_LAB_FAILED"), errorType=type(exc).__name__,
                      message=str(exc) if isinstance(exc, WorkerError) else "Local test environment did not complete")
    finally:
        # Exact owned path check before stopping our cluster. No recursive deletion is performed.
        if pg_ctl and (data / "postmaster.pid").is_file():
            resolved = data.resolve()
            resolved.relative_to(root.resolve())
            marker = read_json(root / "lab-owner.json")
            check(marker["dataDirectory"] == str(resolved), "Cluster path no longer matches ownership marker")
            stop_code = run([pg_ctl, "-D", data, "-m", "fast", "-w", "stop"], "stop", 60)
            report["clusterStopExitCode"] = stop_code
            if stop_code != 0:
                report["status"] = "FAIL"
                report["errorCode"] = "PG_LAB_STOP_FAILED"
        report["checkedAtUtc"] = datetime.now(timezone.utc).isoformat()
        report["runDirectory"] = str(root)
        text = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
        (root / "lab-run.json").write_text(text, encoding="utf-8")
        (EVIDENCE / "postgres-checks.json").write_text(text, encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "sourceHashes"}, ensure_ascii=False, indent=2))
    return 0 if report["status"] == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
