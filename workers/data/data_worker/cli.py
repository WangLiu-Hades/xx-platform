"""Command-line entry; no HTTP service, third-party packages, or remote database."""
import argparse
from datetime import datetime, timezone
from pathlib import Path
import sqlite3
import sys
import uuid

from .common import WorkerError, canonical, check
from .pipeline import process_batch
from .report import export_report
from .settings import load_settings, output_path
from .store import Ledger


def log(event, level="INFO", **fields):
    print(canonical(dict(timestamp=datetime.now(timezone.utc).isoformat(),
                         level=level, event=event, **fields)), flush=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path,
                        default=Path(__file__).resolve().parents[1] / "config.example.json")
    parser.add_argument("--batch", action="append", default=[], help="Batch name or UUID; repeat to select several")
    parser.add_argument("--state", type=Path, help="SQLite state below the configured artifact directory; relative to cwd")
    parser.add_argument("--output", type=Path, help="New report directory below the configured artifact directory; relative to cwd")
    args = parser.parse_args(argv)
    ledger = None
    try:
        settings = load_settings(args.config)
        state_path = output_path(args.state) if args.state else settings.state_path
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S")
        output = output_path(args.output) if args.output else settings.output_root / (stamp + "-" + uuid.uuid4().hex[:8])
        check(state_path.suffix in (".sqlite", ".sqlite3", ".db"), "State path must have a SQLite extension")
        check(not output.exists(), "Report destination already exists; choose another path", "OUTPUT_EXISTS")
        check(output not in state_path.parents and state_path != output,
              "State must be outside the report directory")
        selected = settings.batches
        if args.batch:
            known = {value for batch in selected for value in (batch.id, batch.metadata["name"])}
            check(set(args.batch) <= known, "Unknown --batch selection", "BATCH_UNKNOWN")
            selected = [batch for batch in selected if batch.id in args.batch or batch.metadata["name"] in args.batch]
        ledger = Ledger(state_path, settings.fingerprint)
        log("worker.started", synthetic=True, selectedBatches=len(selected), state=str(state_path))
        for batch in selected:
            log("batch.started", batchId=batch.id, batchName=batch.metadata["name"])
            summary, cached = process_batch(ledger, settings.adapter, batch, settings.max_line_bytes)
            # These are local ingestion outcomes, not H04 task status transitions.
            log("batch.committed", cached=cached, **summary)
        summary = export_report(ledger, output)
        log("report.exported", output=str(output), finalFacts=summary["finalFacts"],
            totalProcessingRows=summary["totalProcessingRows"], totals=summary["totals"])
        return 0
    except WorkerError as exc:
        log("worker.failed", "ERROR", code=exc.code, message=str(exc))
        return 2
    except (OSError, sqlite3.Error) as exc:
        # Do not print raw row data, SQL parameters, or platform credentials.
        log("worker.failed", "ERROR", code="LOCAL_IO_FAILED", errorType=type(exc).__name__,
            message="Local I/O failed; committed batches remain in state. Check paths, permissions or locks.")
        return 2
    finally:
        if ledger is not None:
            ledger.close()
