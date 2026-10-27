"""Run from any directory: python path/to/run_worker.py --help."""
import sys

sys.dont_write_bytecode = True
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
from data_worker.cli import main

if __name__ == "__main__":
    raise SystemExit(main())
