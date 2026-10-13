"""Compatibility entry: current repository layout verification. Historical originals are preserved."""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).resolve().parents[3]/'tools/layout/verify_layout.py'),run_name='__main__')
