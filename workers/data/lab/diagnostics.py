"""Safe database diagnostic fields: no connection strings, SQL text or source row values."""
import re

from data_worker.common import WorkerError


def failure_details(exc):
    result = {"errorType": type(exc).__name__}
    if isinstance(exc, WorkerError):
        result["errorCode"] = exc.code
        result["message"] = str(exc)
    else:
        result["message"] = "Database verification failed at the recorded stage."
    current = exc
    visited = set()
    chain = []
    while current is not None and id(current) not in visited:
        visited.add(id(current))
        chain.append(type(current).__name__)
        state = getattr(current, "sqlstate", None)
        if isinstance(state, str) and re.fullmatch(r"[0-9A-Z]{5}", state):
            result["sqlState"] = state
        diag = getattr(current, "diag", None)
        if diag is not None:
            allowed = {"schema_name": "schemaName", "table_name": "tableName",
                       "column_name": "columnName", "constraint_name": "constraintName"}
            for source, target in allowed.items():
                value = getattr(diag, source, None)
                # All platform identifiers use this bounded alphabet. Exclude diagnostic detail/query.
                if isinstance(value, str) and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]{0,199}", value):
                    result[target] = value
        current = current.__cause__
    result["exceptionTypes"] = chain
    return result
