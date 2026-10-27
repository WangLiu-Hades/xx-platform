"""Terminate the isolated test worker after a real write, before transaction commit."""
import argparse
import json
import os
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from data_worker.pg.connection import connect, require_lab
from data_worker.settings import Batch, PACKAGE_ROOT, load_settings, output_path
from .integration import make_writer, lease_for


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    data = json.loads(output_path(args.manifest).read_text(encoding="utf-8"))
    batch = Batch(data["metadata"], output_path(data["inputPath"]))
    db = connect(env_name="D3_CRASH_PROBE_DSN")
    require_lab(db)
    writer = make_writer(db, load_settings(PACKAGE_ROOT / "config.example.json"))
    original = writer._write_row
    def stop_after_write(*values):
        original(*values)
        os._exit(17)  # Intentional crash: PostgreSQL must roll back the disconnected transaction.
    writer._write_row = stop_after_write
    writer.ingest(batch, lease_for(batch))
    raise RuntimeError("Crash point was not reached")


if __name__ == "__main__":
    main()
