"""H03 internal PLATE query module; authority must be supplied by DEV-01."""
from dataclasses import dataclass, asdict
from datetime import datetime
import base64
import hashlib
import hmac
import secrets
import time
import uuid

from ..common import WorkerError, canonical, check, digest, strict_json, utc, uuid_text
from .projection import SELECT_FIELDS, FROM_TABLES, timestamp, to_dto


def query_check(condition, message):
    check(condition, message, "DATA_QUERY_INVALID")


def ids(value, name, maximum=None, nonempty=False):
    query_check(isinstance(value, (list, tuple)), name + " must be an explicit list")
    query_check(maximum is None or len(value) <= maximum, name + " exceeds limit")
    query_check(not nonempty or bool(value), name + " cannot be empty")
    query_check(all(uuid_text(item) for item in value), name + " contains an invalid UUID")
    return tuple(sorted(set(value)))


@dataclass(frozen=True)
class TrustedScope:
    principal_id: str
    version: str
    site_ids: tuple
    data_types: tuple

    def __post_init__(self):
        query_check(uuid_text(self.principal_id), "Invalid trusted principal")
        query_check(isinstance(self.version, str) and 0 < len(self.version) <= 200, "Invalid scope version")
        object.__setattr__(self, "site_ids", ids(self.site_ids, "authorized sites"))
        query_check(isinstance(self.data_types, (list, tuple)) and
                    all(item in ("FENCE", "MAC", "PLATE", "FACE") for item in self.data_types),
                    "Invalid authorized data types")
        object.__setattr__(self, "data_types", tuple(sorted(set(self.data_types))))


@dataclass(frozen=True)
class QuerySpec:
    start: str
    end: str
    site_ids: object = None
    target_ids: object = None
    device_ids: object = None
    plate_color: object = None
    page_size: int = 20
    sort_by: str = "captureTime"
    sort_order: str = "DESC"

    @classmethod
    def parse(cls, request):
        allowed = {"dataType", "timeRange", "siteIds", "targetIds", "deviceIds",
                   "plateColor", "plateNumber", "pageSize", "sortBy", "sortOrder"}
        query_check(isinstance(request, dict) and set(request) <= allowed, "Unsupported query fields")
        query_check(request.get("dataType") == "PLATE", "This query module only supports PLATE")
        if "plateNumber" in request:
            raise WorkerError("REQUIREMENT_PENDING", "Resolve raw plate identifiers through the approved target service")
        window = request.get("timeRange")
        query_check(isinstance(window, dict) and set(window) == {"from", "to"}, "Time range is required")
        try:
            start, end = utc(window["from"]), utc(window["to"])
            query_check(datetime.fromisoformat(start.replace("Z", "+00:00")) <
                        datetime.fromisoformat(end.replace("Z", "+00:00")), "from must precede to")
        except (ValueError, TypeError, OverflowError) as exc:
            raise WorkerError("DATA_QUERY_INVALID", "Invalid timestamp") from exc
        page_size = request.get("pageSize", 20)
        query_check(type(page_size) is int and 1 <= page_size <= 100, "pageSize must be 1..100")
        sort_by, sort_order = request.get("sortBy", "captureTime"), request.get("sortOrder", "DESC")
        query_check(sort_by in ("captureTime", "captureId") and sort_order in ("ASC", "DESC"),
                    "Unsupported sort")
        color = request.get("plateColor")
        if "plateColor" in request:
            query_check(isinstance(color, str) and len(color) <= 200 and "\x00" not in color, "Invalid plateColor")
        values = {}
        for public, internal in (("siteIds", "site_ids"), ("targetIds", "target_ids"), ("deviceIds", "device_ids")):
            values[internal] = (ids(request[public], public, 100 if public != "siteIds" else None,
                                    nonempty=public == "targetIds") if public in request else None)
        return cls(start, end, **values, plate_color=color, page_size=page_size,
                   sort_by=sort_by, sort_order=sort_order)


@dataclass(frozen=True)
class QueryBudget:
    max_window_seconds: int
    statement_timeout_ms: int
    session_seconds: int

    def __post_init__(self):
        for value in (self.max_window_seconds, self.statement_timeout_ms, self.session_seconds):
            query_check(type(value) is int and value > 0, "An explicit positive server query budget is required")


def build_query(spec, scope, last=None):
    # Recheck the whitelist here: callers can construct the dataclass without parse().
    query_check(spec.sort_by in ("captureTime", "captureId") and spec.sort_order in ("ASC", "DESC"),
                "Unsupported sort")
    query_check(type(spec.page_size) is int and 1 <= spec.page_size <= 100, "Invalid page size")
    sites = set(scope.site_ids)
    if spec.site_ids is not None:
        sites &= set(spec.site_ids)
    empty = not sites or "PLATE" not in scope.data_types or spec.device_ids == ()
    params = {"sites": sorted(sites), "from": spec.start, "to": spec.end, "limit": spec.page_size + 1}
    where = ["e.status='VALID'", "e.data_type='PLATE'", "e.site_id=ANY(%(sites)s::uuid[])",
             "e.capture_time >= %(from)s::timestamptz", "e.capture_time < %(to)s::timestamptz"]
    if spec.target_ids is not None:
        params["targets"] = list(spec.target_ids)
        where.append("(e.primary_target_id=ANY(%(targets)s::uuid[]) OR EXISTS "
                     "(SELECT 1 FROM capture.capture_target ct WHERE ct.capture_id=e.id "
                     "AND ct.target_type='PLATE' AND ct.target_id=ANY(%(targets)s::uuid[])))")
    if spec.device_ids is not None:
        params["devices"] = list(spec.device_ids)
        where.append("e.device_id=ANY(%(devices)s::uuid[])")
    if spec.plate_color is not None:
        params["color"] = spec.plate_color
        where.append("p.plate_color=%(color)s")
    comparison = "<" if spec.sort_order == "DESC" else ">"
    if last is not None:
        params["last_id"] = last["captureId"]
        if spec.sort_by == "captureTime":
            params["last_time"] = last["captureTime"]
            where.append("(e.capture_time,e.id) " + comparison +
                         " (%(last_time)s::timestamptz,%(last_id)s::uuid)")
        else:
            where.append("e.id " + comparison + " %(last_id)s::uuid")
    columns = "e.capture_time " + spec.sort_order + ",e.id " + spec.sort_order if spec.sort_by == "captureTime" else "e.id " + spec.sort_order
    sql = ("SELECT " + SELECT_FIELDS + FROM_TABLES + " WHERE " + " AND ".join(where) +
           " ORDER BY " + columns + " LIMIT %(limit)s")
    return sql, params, empty


class CursorCodec:
    def __init__(self, key):
        query_check(isinstance(key, bytes) and len(key) >= 32, "Cursor key must have at least 32 bytes")
        self.key = key

    def encode(self, binding, last, expires):
        raw = canonical({"version": 1, "binding": binding, "last": last, "expires": expires}).encode("utf-8")
        body = base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")
        return body + "." + hmac.new(self.key, body.encode("ascii"), hashlib.sha256).hexdigest()

    def decode(self, token, binding, now=None):
        try:
            if not isinstance(token, str) or len(token) > 4096:
                raise ValueError()
            body, signature = token.split(".")
            expected = hmac.new(self.key, body.encode("ascii"), hashlib.sha256).hexdigest()
            if not hmac.compare_digest(expected, signature):
                raise ValueError()
            raw = base64.b64decode(body + "=" * (-len(body) % 4), altchars=b"-_", validate=True)
            data = strict_json(raw.decode("utf-8"))
            if not isinstance(data, dict) or set(data) != {"version", "binding", "last", "expires"}:
                raise ValueError()
            if data["version"] != 1 or data["binding"] != binding or type(data["expires"]) not in (int, float):
                raise ValueError()
            if data["expires"] <= (time.time() if now is None else now):
                raise ValueError()
            last = data["last"]
            if not isinstance(last, dict) or set(last) != {"captureId", "captureTime"}:
                raise ValueError()
            if not uuid_text(last["captureId"]) or utc(last["captureTime"]) != last["captureTime"]:
                raise ValueError()
            return last
        except (ValueError, TypeError, UnicodeError, KeyError, OverflowError) as exc:
            raise WorkerError("DATA_CURSOR_INVALID", "Cursor is invalid, expired, or belongs to another query") from exc


class CaptureQuerySession:
    """One bounded REPEATABLE READ transaction across all pages; cursor dies with the session."""
    def __init__(self, connection, spec, scope, budget, key=None):
        self.connection, self.spec, self.scope, self.budget = connection, spec, scope, budget
        seconds = (datetime.fromisoformat(spec.end.replace("Z", "+00:00")) -
                   datetime.fromisoformat(spec.start.replace("Z", "+00:00"))).total_seconds()
        check(seconds <= budget.max_window_seconds, "Query exceeds server time-window budget", "DATA_QUERY_TOO_BROAD")
        self.codec = CursorCodec(key or secrets.token_bytes(32))
        self.active = False

    def __enter__(self):
        from psycopg.pq import TransactionStatus
        check(self.connection.autocommit and self.connection.info.transaction_status == TransactionStatus.IDLE,
              "Query sessions require a dedicated idle autocommit connection", "PG_QUERY_CONNECTION_BUSY")
        self.transaction = self.connection.transaction()
        self.transaction.__enter__()
        try:
            self.connection.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY")
            self.connection.execute("SELECT set_config('statement_timeout', %s, true)",
                                    (str(self.budget.statement_timeout_ms),))
            self.connection.execute("SELECT set_config('idle_in_transaction_session_timeout', %s, true)",
                                    (str(self.budget.session_seconds * 1000),))
            snapshot = self.connection.execute("SELECT txid_current_snapshot()::text AS value").fetchone()["value"]
            self.expires = time.time() + self.budget.session_seconds
            self.binding = digest({"query": asdict(self.spec), "scope": asdict(self.scope),
                                   "snapshot": snapshot, "session": str(uuid.uuid4())})
            self.active = True
            return self
        except BaseException:
            import sys
            self.transaction.__exit__(*sys.exc_info())
            raise

    def __exit__(self, kind, value, traceback):
        self.active = False
        return self.transaction.__exit__(kind, value, traceback)

    def page(self, current_scope, cursor=None, explain=False):
        check(self.active and time.time() < self.expires, "Query session has expired", "DATA_CURSOR_INVALID")
        check(current_scope == self.scope, "Authorization scope changed; start a new query", "DATA_CURSOR_INVALID")
        last = self.codec.decode(cursor, self.binding) if cursor is not None else None
        sql, params, empty = build_query(self.spec, current_scope, last)
        if empty:
            return {"items": [], "nextCursor": None, "pageSize": self.spec.page_size}
        if explain:
            # SQL is SELECT-only and EXPLAIN ANALYZE is deliberately not used.
            return self.connection.execute("EXPLAIN (FORMAT JSON) " + sql, params).fetchone()
        records = self.connection.execute(sql, params).fetchall()
        selected = records[:self.spec.page_size]
        next_cursor = None
        if len(records) > self.spec.page_size:
            end = selected[-1]
            next_cursor = self.codec.encode(self.binding, {"captureId": str(end["capture_id"]),
                                                          "captureTime": timestamp(end["capture_time"])}, self.expires)
        return {"items": [to_dto(row) for row in selected], "nextCursor": next_cursor, "pageSize": self.spec.page_size}
