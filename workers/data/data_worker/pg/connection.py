"""Lazy Psycopg import keeps the existing offline worker dependency-free."""
import os
from ..common import WorkerError, check


def connect(dsn=None, env_name="D3_TEST_DSN"):
    if dsn is None:
        dsn = os.environ.get(env_name)
    check(isinstance(dsn, str) and bool(dsn), "Set the database connection environment variable",
          "PG_CONNECTION_CONFIG_MISSING")
    try:
        import psycopg
        from psycopg.rows import dict_row
    except (ImportError, OSError) as exc:
        raise WorkerError("PG_DRIVER_UNAVAILABLE",
                          "Install requirements-postgres.txt in a working Python environment") from exc
    try:
        connection = psycopg.connect(dsn, autocommit=True, row_factory=dict_row,
                                     connect_timeout=5, application_name="dev3-synthetic-lab",
                                     gssencmode="disable")
        connection.execute("SET TIME ZONE 'UTC'")
        return connection
    except psycopg.Error as exc:
        # Never include DSN, server messages, SQL parameters or passwords in CLI errors.
        raise WorkerError("PG_CONNECT_FAILED", "PostgreSQL connection failed; check local configuration") from exc


def require_lab(connection):
    row = connection.execute("SELECT current_database() AS name").fetchone()
    check(row["name"].startswith("d3_lab_"), "Write runner requires a dedicated d3_lab_ database",
          "PG_LAB_REQUIRED")
    marker = connection.execute(
        "SELECT value FROM dev3_test.marker WHERE name='purpose'").fetchone()
    check(marker and marker["value"] == "synthetic-d3-05-06",
          "Missing isolated test database marker", "PG_LAB_REQUIRED")
