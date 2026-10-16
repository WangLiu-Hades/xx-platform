"""M03 internal reads for an explicit DEV-01 batch grant and an agreed quality policy.

No HTTP routes or permission resolver are implemented here. The synthetic policy is a
proposal for lab verification; production policy and grants must be supplied by DEV-01.
"""
import base64
from dataclasses import dataclass
from datetime import datetime
import hashlib
import hmac
import secrets
import time
import uuid

from ..common import WorkerError, canonical, check, digest, strict_json, utc, uuid_text
from .projection import timestamp

STATUSES = ("PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELLED")


def valid(ok, message):
    check(ok, message, "DATA_INVALID_FILTER")


def text_value(value, label, maximum=200):
    valid(isinstance(value, str) and 0 < len(value) <= maximum and
          not any(ord(c) < 32 for c in value), "Invalid " + label)
    return value


def instant(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


@dataclass(frozen=True)
class TrustedBatchScope:
    principal_id: str
    version: str
    batch_ids: tuple

    def __post_init__(self):
        valid(uuid_text(self.principal_id), "Invalid trusted principal")
        text_value(self.version, "grant version")
        valid(isinstance(self.batch_ids, (list, tuple)) and all(uuid_text(x) for x in self.batch_ids),
              "Explicit authorized batch UUIDs are required")
        object.__setattr__(self, "batch_ids", tuple(sorted(set(self.batch_ids))))


@dataclass(frozen=True)
class IngestionReadBudget:
    statement_timeout_ms: int
    session_seconds: int
    max_window_seconds: int
    max_authorized_batches: int

    def __post_init__(self):
        valid(all(type(v) is int and v > 0 for v in
                  (self.statement_timeout_ms, self.session_seconds, self.max_window_seconds,
                   self.max_authorized_batches)), "Explicit positive read limits are required")


@dataclass(frozen=True)
class BatchListSpec:
    page: int = 1
    page_size: int = 20
    source: object = None
    status: object = None
    sort_by: str = "createdAt"
    sort_order: str = "DESC"

    def __post_init__(self):
        valid(type(self.page) is int and self.page >= 1, "page must be a positive integer")
        valid(type(self.page_size) is int and 1 <= self.page_size <= 100, "pageSize must be 1..100")
        valid(self.sort_by in ("createdAt", "status") and self.sort_order in ("ASC", "DESC"),
              "Unsupported batch sort")
        if self.source is not None:
            text_value(self.source, "source", 80)
        valid(self.status is None or self.status in STATUSES, "Invalid ingestion status")

    @classmethod
    def parse(cls, value):
        valid(isinstance(value, dict) and set(value) <=
              {"page", "pageSize", "source", "status", "sortBy", "sortOrder"}, "Unsupported batch filters")
        valid(all(v is not None for v in value.values()), "Omit absent filters; do not supply null")
        return cls(value.get("page", 1), value.get("pageSize", 20), value.get("source"), value.get("status"),
                   value.get("sortBy", "createdAt"), value.get("sortOrder", "DESC"))


@dataclass(frozen=True)
class QualitySpec:
    start: str
    end: str
    source: object = None

    def __post_init__(self):
        try:
            start, end = utc(self.start), utc(self.end)
        except (ValueError, TypeError, OverflowError) as exc:
            raise WorkerError("DATA_INVALID_FILTER", "Invalid quality time window") from exc
        valid(instant(start) < instant(end), "from must precede to")
        object.__setattr__(self, "start", start)
        object.__setattr__(self, "end", end)
        if self.source is not None:
            text_value(self.source, "source", 80)

    @classmethod
    def parse(cls, value):
        valid(isinstance(value, dict) and {"from", "to"} <= set(value) <= {"from", "to", "source"},
              "An explicit from/to quality window is required")
        valid(all(v is not None for v in value.values()), "Omit absent source; do not supply null")
        return cls(value["from"], value["to"], value.get("source"))


@dataclass(frozen=True)
class QualityPolicy:
    version: str
    time_basis: str
    unmapped_unit: str
    unmapped_codes: tuple
    invalid_coordinate_codes: tuple

    def __post_init__(self):
        text_value(self.version, "quality policy version")
        valid(self.time_basis == "ITEM_PROCESSED_AT" and self.unmapped_unit == "SOURCE_DEVICE",
              "Unsupported quality aggregation policy")
        for name in ("unmapped_codes", "invalid_coordinate_codes"):
            values = getattr(self, name)
            valid(isinstance(values, (tuple, list)) and bool(values), "Quality issue codes are required")
            object.__setattr__(self, name, tuple(sorted(set(text_value(v, "issue code") for v in values))))


# Experimental rules only. processed_at is synthetic receivedAt in the current adapter.
SYNTHETIC_QUALITY_POLICY = QualityPolicy(
    "synthetic-m03-quality-v1", "ITEM_PROCESSED_AT", "SOURCE_DEVICE",
    ("FIXTURE_DEVICE_UNMAPPED",),
    ("FIXTURE_COORDINATE_PAIR", "FIXTURE_COORDINATE_INVALID", "FIXTURE_CRS_UNSUPPORTED"))


BATCH_COLUMNS = """b.id AS batch_id,b.task_id,b.source_code,b.source_file_id,
    b.mapping_version,b.dedup_rule_version,b.status,b.accepted_count,b.rejected_count,b.duplicate_count,
    b.created_at,b.finished_at,b.error_summary"""
BATCH_FROM = """ FROM capture.ingestion_batch b
    JOIN analysis.async_task t ON t.id=b.task_id """
VISIBLE = "b.id=ANY(%(grants)s::uuid[]) AND NOT t.deleted"


def batch_projection(row):
    dto = {"batchId": str(row["batch_id"]), "source": row["source_code"],
           "mappingVersion": row["mapping_version"], "dedupVersion": row["dedup_rule_version"],
           "status": row["status"], "acceptedCount": row["accepted_count"],
           "rejectedCount": row["rejected_count"], "duplicateCount": row["duplicate_count"],
           "createdAt": timestamp(row["created_at"]), "finishedAt": timestamp(row["finished_at"]) if row["finished_at"] is not None else None,
           # Database diagnostics must not become a public response verbatim.
           "errorSummary": "Batch processing reported an error." if row["error_summary"] else None}
    for name, column in (("taskId", "task_id"), ("sourceFileId", "source_file_id")):
        if row[column] is not None:
            dto[name] = str(row[column])
    return dto


def batch_where(scope, spec):
    valid(isinstance(scope, TrustedBatchScope) and isinstance(spec, BatchListSpec), "Invalid batch query")
    where = VISIBLE
    params = {"grants": list(scope.batch_ids)}
    if spec.source is not None:
        where += " AND b.source_code=%(source)s"
        params["source"] = spec.source
    if spec.status is not None:
        where += " AND b.status=%(status)s"
        params["status"] = spec.status
    return where, params


KNOWN_ROW_CODES = frozenset((
    "INPUT_UTF8_INVALID", "INPUT_JSON_INVALID", "INPUT_OBJECT_REQUIRED", "INPUT_LINE_TOO_LARGE",
    "FIXTURE_REQUIRED", "FIXTURE_UNKNOWN_FIELD", "FIXTURE_DATA_TYPE", "FIXTURE_FIELD_TYPE",
    "FIXTURE_TIME_INVALID", "FIXTURE_COORDINATE_PAIR", "FIXTURE_COORDINATE_INVALID",
    "FIXTURE_CRS_UNSUPPORTED", "FIXTURE_IMAGE_REFERENCE", "FIXTURE_KEY_CONFLICT",
    "STORAGE_TEXT_INVALID", "STORAGE_FIELD_TOO_LONG"))
SAFE_FIELDS = frozenset(("$", "recordId", "dataType", "deviceKey", "captureTime", "plateText",
                        "longitude", "latitude", "crs", "plateColor", "vehicleImageRef"))


def synthetic_error_projection(row):
    """Proposal for synthetic row errors; no raw record key or free-form diagnostic is exposed."""
    check(row["error_code"] in KNOWN_ROW_CODES, "A public mapping is required for this row error",
          "REQUIREMENT_PENDING")
    dto = {"rowNumber": row["row_number"], "code": "VALIDATION_ERROR",
           "reason": "Input row failed the selected mapping or validation rule."}
    if row["field_name"] in SAFE_FIELDS:
        dto["field"] = row["field_name"]
    return dto


class ErrorCursor:
    def __init__(self, key):
        valid(isinstance(key, bytes) and len(key) >= 32, "Cursor key must have at least 32 bytes")
        self.key = key

    def encode(self, binding, last, expires):
        data = canonical({"v": 1, "binding": binding, "last": list(last), "expires": expires}).encode("utf-8")
        body = base64.urlsafe_b64encode(data).rstrip(b"=")
        mac = hmac.new(self.key, body, hashlib.sha256).hexdigest().encode("ascii")
        return (body + b"." + mac).decode("ascii")

    def decode(self, value, binding, now=None):
        try:
            if not isinstance(value, str) or not 0 < len(value) <= 4096:
                raise ValueError()
            body, mac = value.encode("ascii").split(b".")
            expected = hmac.new(self.key, body, hashlib.sha256).hexdigest().encode("ascii")
            if not hmac.compare_digest(mac, expected):
                raise ValueError()
            data = strict_json(base64.b64decode(body + b"=" * (-len(body) % 4), altchars=b"-_", validate=True).decode("utf-8"))
            if not isinstance(data, dict) or set(data) != {"v", "binding", "last", "expires"}:
                raise ValueError()
            last = data["last"]
            if (type(data["v"]) is not int or data["v"] != 1 or data["binding"] != binding or type(data["expires"]) not in (int, float) or
                    data["expires"] <= (time.time() if now is None else now) or not isinstance(last, list) or
                    len(last) != 2 or type(last[0]) is not int or last[0] < 1 or not uuid_text(last[1])):
                raise ValueError()
            return tuple(last)
        except (ValueError, TypeError, KeyError, UnicodeError, OverflowError):
            raise WorkerError("DATA_CURSOR_INVALID", "Error cursor is invalid or expired") from None


class IngestionReadSession:
    def __init__(self, connection, scope, budget, *, quality_policy, error_projection, cursor_key=None):
        valid(isinstance(scope, TrustedBatchScope) and isinstance(budget, IngestionReadBudget),
              "Explicit trusted scope and read budget are required")
        check(len(scope.batch_ids) <= budget.max_authorized_batches, "Batch grant exceeds the read budget",
              "DATA_QUERY_TOO_BROAD")
        valid(isinstance(quality_policy, QualityPolicy) and callable(error_projection),
              "Explicit quality and public error policies are required")
        self.db, self.scope, self.budget = connection, scope, budget
        self.quality_policy, self.error_projection = quality_policy, error_projection
        self.codec = ErrorCursor(cursor_key or secrets.token_bytes(32))
        self.active = False
        self.token = None

    def __enter__(self):
        valid(not self.active and self.db.autocommit and int(self.db.info.transaction_status) == 0,
              "Read session requires an idle autocommit connection")
        self.tx = self.db.transaction()
        self.tx.__enter__()
        try:
            self.db.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY")
            self.deadline = time.monotonic() + self.budget.session_seconds
            self.expires = time.time() + self.budget.session_seconds
            self.active = True
            self.token = str(uuid.uuid4())
            self.as_of = timestamp(self._execute("SELECT transaction_timestamp() AS ts").fetchone()["ts"])
            return self
        except BaseException:
            import sys
            self.active = False
            self.tx.__exit__(*sys.exc_info())
            raise

    def __exit__(self, kind, value, traceback):
        self.active = False
        return self.tx.__exit__(kind, value, traceback)

    def _guard(self, current_scope):
        check(self.active, "Read session is closed", "DATA_CURSOR_INVALID")
        check(current_scope == self.scope, "Trusted batch grant changed; start a new session", "DATA_CURSOR_INVALID")
        check(time.monotonic() < self.deadline, "Read snapshot expired", "DATA_CURSOR_INVALID")

    def _execute(self, sql, params=None):
        remaining = int((self.deadline - time.monotonic()) * 1000)
        check(remaining > 0, "Read snapshot expired", "DATA_CURSOR_INVALID")
        self.db.execute("SELECT set_config('statement_timeout',%s,true)",
                        (str(min(self.budget.statement_timeout_ms, remaining)),))
        return self.db.execute(sql, params)

    def list_batches(self, spec, current_scope):
        self._guard(current_scope)
        valid(isinstance(spec, BatchListSpec), "Expected batch list filters")
        if not self.scope.batch_ids:
            return {"items": [], "page": spec.page, "pageSize": spec.page_size, "total": 0}
        where, params = batch_where(self.scope, spec)
        total = self._execute("SELECT count(*) AS n" + BATCH_FROM + " WHERE " + where, params).fetchone()["n"]
        if (spec.page - 1) * spec.page_size >= total:
            return {"items": [], "page": spec.page, "pageSize": spec.page_size, "total": total}
        order = {"createdAt": "b.created_at", "status": "b.status"}[spec.sort_by]
        sql = ("SELECT " + BATCH_COLUMNS + BATCH_FROM + " WHERE " + where +
               " ORDER BY " + order + " " + spec.sort_order + ",b.id " + spec.sort_order +
               " LIMIT %(limit)s OFFSET %(offset)s")
        params.update(limit=spec.page_size, offset=(spec.page - 1) * spec.page_size)
        rows = self._execute(sql, params).fetchall()
        return {"items": [batch_projection(row) for row in rows], "page": spec.page,
                "pageSize": spec.page_size, "total": total}

    def _batch(self, batch_id):
        valid(uuid_text(batch_id), "Invalid batch UUID")
        check(batch_id in self.scope.batch_ids, "Batch not found", "DATA_NOT_FOUND")
        row = self._execute("SELECT " + BATCH_COLUMNS + ",t.current_attempt_no" + BATCH_FROM +
                            " WHERE " + VISIBLE + " AND b.id=%(batch)s::uuid",
                            {"grants": list(self.scope.batch_ids), "batch": batch_id}).fetchone()
        check(row is not None, "Batch not found", "DATA_NOT_FOUND")
        return row

    def get_batch(self, batch_id, current_scope):
        self._guard(current_scope)
        return batch_projection(self._batch(batch_id))

    def list_errors(self, batch_id, current_scope, *, page_size=20, cursor=None):
        self._guard(current_scope)
        valid(type(page_size) is int and 1 <= page_size <= 100, "pageSize must be 1..100")
        batch = self._batch(batch_id)
        # Old attempts remain in task history. This view describes the snapshot's current attempt.
        binding = digest({"principal": self.scope.principal_id, "scope": self.scope.version,
                          "grants": self.scope.batch_ids, "snapshot": self.token, "batch": batch_id,
                          "attempt": batch["current_attempt_no"], "pageSize": page_size})
        last = self.codec.decode(cursor, binding) if cursor is not None else None
        sql = """SELECT er.id,er.row_number,er.source_record_key,er.field_name,er.error_code,er.reason
                 FROM analysis.task_row_error er WHERE er.task_id=%(task)s::uuid
                 AND er.attempt_no=%(attempt)s"""
        params = {"task": str(batch["task_id"]), "attempt": batch["current_attempt_no"], "limit": page_size + 1}
        if last is not None:
            sql += " AND (er.row_number,er.id)>(%(row)s,%(id)s::uuid)"
            params.update(row=last[0], id=last[1])
        sql += " ORDER BY er.row_number,er.id LIMIT %(limit)s"
        rows = self._execute(sql, params).fetchall()
        page = rows[:page_size]
        next_cursor = (self.codec.encode(binding, (page[-1]["row_number"], str(page[-1]["id"])), self.expires)
                       if len(rows) > page_size else None)
        return {"items": [self.error_projection(row) for row in page], "nextCursor": next_cursor,
                "pageSize": page_size}

    def quality_summary(self, spec, current_scope):
        self._guard(current_scope)
        valid(isinstance(spec, QualitySpec), "Expected explicit quality window")
        check((instant(spec.end) - instant(spec.start)).total_seconds() <= self.budget.max_window_seconds,
              "Quality window exceeds server budget", "DATA_QUERY_TOO_BROAD")
        result = {"asOf": self.as_of, "acceptedCount": 0, "rejectedCount": 0, "duplicateCount": 0,
                  "unmappedDeviceCount": 0, "invalidCoordinateCount": 0}
        if spec.source is not None:
            result["source"] = spec.source
        if not self.scope.batch_ids:
            return result
        params = {"grants": list(self.scope.batch_ids), "start": spec.start, "end": spec.end,
                  "unmapped": list(self.quality_policy.unmapped_codes),
                  "invalid": list(self.quality_policy.invalid_coordinate_codes)}
        source_sql = ""
        if spec.source is not None:
            source_sql = " AND b.source_code=%(source)s"
            params["source"] = spec.source
        sql = """
            WITH eligible AS (
                SELECT i.id,i.processing_status,i.source_event_id,b.source_code
                FROM capture.ingestion_item i
                JOIN capture.ingestion_batch b ON b.id=i.batch_id
                JOIN analysis.async_task t ON t.id=b.task_id
                WHERE b.id=ANY(%(grants)s::uuid[]) AND NOT t.deleted AND b.status='SUCCESS'
                  AND i.processed_at >= %(start)s::timestamptz AND i.processed_at < %(end)s::timestamptz
            """ + source_sql + """
            ), flags AS (
                SELECT e.id,
                  bool_or(q.issue_code=ANY(%(unmapped)s::text[])) AS unmapped,
                  bool_or(q.issue_code=ANY(%(invalid)s::text[])) AS invalid
                FROM eligible e LEFT JOIN capture.quality_issue q ON q.ingestion_item_id=e.id GROUP BY e.id
            )
            SELECT count(*) FILTER(WHERE e.processing_status='ACCEPTED') AS accepted,
                   count(*) FILTER(WHERE e.processing_status='REJECTED') AS rejected,
                   count(*) FILTER(WHERE e.processing_status='DUPLICATE') AS duplicate,
                   count(DISTINCT (e.source_code,s.source_device_key))
                     FILTER(WHERE f.unmapped AND s.source_device_key IS NOT NULL) AS unmapped,
                   count(*) FILTER(WHERE f.invalid) AS invalid
            FROM eligible e JOIN flags f ON f.id=e.id
            LEFT JOIN capture.source_event s ON s.id=e.source_event_id
            """
        row = self._execute(sql, params).fetchone()
        for public, key in (("acceptedCount", "accepted"), ("rejectedCount", "rejected"),
                            ("duplicateCount", "duplicate"), ("unmappedDeviceCount", "unmapped"),
                            ("invalidCoordinateCount", "invalid")):
            result[public] = row[key]
        return result
