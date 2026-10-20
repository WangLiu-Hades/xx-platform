"""Bounded preparation and publication primitives; H04 control is injected by the caller."""
from dataclasses import dataclass
import hashlib
import math
import time

from ..common import RowError, check, digest
from ..pipeline import input_lines
from ..plate import parse_line


@dataclass(frozen=True)
class IngestBudget:
    max_input_bytes: int
    max_rows: int
    prepare_seconds: float
    publish_timeout_ms: int
    lock_timeout_ms: int
    lease_margin_ms: int
    checkpoint_rows: int = 32
    checkpoint_seconds: float = 0.25

    def __post_init__(self):
        for name in ("max_input_bytes", "max_rows", "publish_timeout_ms", "lock_timeout_ms",
                     "lease_margin_ms", "checkpoint_rows"):
            check(type(getattr(self, name)) is int and getattr(self, name) > 0,
                  "Positive explicit ingestion limits are required", "INGEST_BUDGET_INVALID")
        for name in ("prepare_seconds", "checkpoint_seconds"):
            value = getattr(self, name)
            check(type(value) in (int, float) and math.isfinite(value) and value > 0,
                  "Finite positive time limits are required", "INGEST_BUDGET_INVALID")
        check(self.lock_timeout_ms <= self.publish_timeout_ms and
              self.checkpoint_seconds <= self.prepare_seconds,
              "Control/lock limits exceed their phase budget", "INGEST_BUDGET_INVALID")


@dataclass(frozen=True)
class ControlPoint:
    phase: str
    processed_rows: int
    input_bytes: int


@dataclass(frozen=True)
class PreparedRow:
    row: dict
    normalized: object
    device_key: object


@dataclass(frozen=True)
class PreparedBatch:
    expected: dict
    rows: tuple
    input_bytes: int


def safe_error_text(value, limit=200):
    return value if isinstance(value, str) and len(value) <= limit and "\x00" not in value else None


def frozen_descriptor(settings, batch, input_hash):
    return {"synthetic": True, "referenceFingerprint": settings.fingerprint,
            "inputSha256": input_hash, "batch": batch.metadata,
            "signature": digest({"batch": batch.metadata, "inputSha256": input_hash})}


def prepare_batch(settings, adapter, batch, lease, control, budget, clock=time.monotonic):
    """Read once into a bounded normalized buffer. No database or task row lock is needed."""
    check(isinstance(budget, IngestBudget) and callable(control),
          "Explicit ingestion budget and control are required", "INGEST_CONFIGURATION_INVALID")
    deadline = clock() + budget.prepare_seconds
    rows = []
    byte_count = 0
    next_check = clock()
    last_check_rows = 0
    hasher = hashlib.sha256()

    def pulse(phase="PREPARING", force=False):
        nonlocal next_check, last_check_rows
        check(clock() < deadline, "Preparation exceeded its time budget", "INGEST_PREPARE_TIMEOUT")
        if force or clock() >= next_check or len(rows) - last_check_rows >= budget.checkpoint_rows:
            check(control(lease, ControlPoint(phase, len(rows), byte_count)) is True,
                  "H04 control rejected or could not confirm execution", "TASK_CONTROL_REJECTED")
            check(clock() < deadline, "Preparation exceeded its time budget", "INGEST_PREPARE_TIMEOUT")
            last_check_rows = len(rows)
            next_check = clock() + budget.checkpoint_seconds

    class BoundedHash:
        def update(self, block):
            nonlocal byte_count
            byte_count += len(block)
            check(byte_count <= budget.max_input_bytes,
                  "Input exceeds the bounded-batch limit", "INGEST_BATCH_TOO_LARGE")
            hasher.update(block)
            pulse()

    pulse("PREPARE_START", force=True)
    check(batch.input_path.stat().st_size <= budget.max_input_bytes,
          "Input exceeds the bounded-batch limit", "INGEST_BATCH_TOO_LARGE")
    for number, block, input_error in input_lines(batch.input_path, settings.max_line_bytes, BoundedHash()):
        check(len(rows) < budget.max_rows, "Too many input rows for atomic publication",
              "INGEST_BATCH_TOO_LARGE")
        row = {"batchId": batch.id, "batchName": batch.metadata["name"], "taskId": lease.task_id,
               "source": batch.metadata["source"], "rowNumber": number, "sourceRecordKey": None,
               "outcome": None, "captureId": None, "sourceEventId": None, "reasonCode": None,
               "field": None, "warnings": []}
        normalized = device_key = None
        try:
            if input_error:
                raise input_error
            raw = parse_line(block)
            row["sourceRecordKey"] = safe_error_text(raw.get("recordId"))
            normalized = adapter.normalize(raw, batch.metadata["source"], batch.metadata["receivedAt"])
            device_key = raw["deviceKey"]
        except RowError as exc:
            row.update(outcome="REJECTED", reasonCode=exc.code, field=safe_error_text(exc.field) or "$")
        rows.append(PreparedRow(row, normalized, device_key))
        pulse()
    pulse("PREPARED", force=True)
    return PreparedBatch(frozen_descriptor(settings, batch, hasher.hexdigest()), tuple(rows), byte_count)


class PublishSession:
    """Execute only in the caller's open transaction, using a decreasing SQL time budget.

    This is an interface guard for a trusted DEV-01 publisher, not a security sandbox.
    The publisher must not open another connection or commit the transaction itself.
    """
    def __init__(self, connection, budget, clock=time.monotonic):
        self._connection, self._budget, self._clock = connection, budget, clock
        self._deadline = clock() + budget.publish_timeout_ms / 1000

    def remaining_ms(self):
        remaining = int((self._deadline - self._clock()) * 1000)
        check(remaining > 0, "Atomic publication exceeded its time budget", "INGEST_PUBLISH_TIMEOUT")
        return remaining

    def execute(self, sql, params=None):
        first = sql.lstrip().split(None, 1)[0].upper() if isinstance(sql, str) and sql.strip() else ""
        check(first in ("SELECT", "INSERT", "UPDATE", "DELETE", "WITH") and ";" not in sql,
              "Publisher must use a single data statement and leave transaction control to the worker",
              "PG_PUBLISHER_TRANSACTION_INVALID")
        remaining = self.remaining_ms()
        self._connection.execute(
            "SELECT set_config('statement_timeout',%s,true),set_config('lock_timeout',%s,true)",
            (str(remaining), str(min(remaining, self._budget.lock_timeout_ms))))
        result = self._connection.execute(sql, params)
        self.remaining_ms()
        return result
