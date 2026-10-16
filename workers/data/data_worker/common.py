"""Shared serialization and validation, using Python 3.9+ standard library."""
from datetime import datetime, timezone
import hashlib
import json
import math
import re
import uuid


class WorkerError(Exception):
    def __init__(self, code, message):
        super().__init__(message)
        self.code = code


class RowError(Exception):
    def __init__(self, code, field=None):
        super().__init__(code)
        self.code, self.field = code, field


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":"), allow_nan=False)


def digest(value):
    return hashlib.sha256(canonical(value).encode("utf-8")).hexdigest()


def file_digest(path):
    hasher = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(65536), b""):
            hasher.update(block)
    return hasher.hexdigest()


def _pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def _constant(value):
    raise ValueError("Nonfinite JSON number")


def _float(value):
    result = float(value)
    if not math.isfinite(result):
        raise ValueError("Nonfinite JSON number")
    return result


def strict_json(text):
    value = json.loads(text, object_pairs_hook=_pairs,
                       parse_constant=_constant, parse_float=_float)
    # Also reject escaped unpaired surrogates; JSONL outputs must be valid UTF-8.
    canonical(value).encode("utf-8")
    return value


def read_json(path):
    try:
        return strict_json(path.read_text(encoding="utf-8"))
    except (UnicodeError, ValueError, RecursionError) as exc:
        raise WorkerError("CONFIG_JSON_INVALID", "Invalid configuration JSON: " + path.name) from exc


def utc(value):
    """Strict RFC3339 subset: seconds, 0..6 fractional digits, explicit offset."""
    pattern = (r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
               r"(?:\.[0-9]{1,6})?(?:Z|[+-][0-9]{2}:[0-9]{2})")
    if not isinstance(value, str) or re.fullmatch(pattern, value) is None:
        raise ValueError("Expected RFC3339 timestamp with timezone")
    if not value.endswith("Z") and (int(value[-5:-3]) > 23 or int(value[-2:]) > 59):
        raise ValueError("Invalid timezone offset")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return parsed.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def check(condition, message, code="CONFIG_INVALID"):
    if not condition:
        raise WorkerError(code, message)


def uuid_text(value):
    try:
        return isinstance(value, str) and str(uuid.UUID(value)) == value
    except (ValueError, TypeError, AttributeError):
        return False
