"""Local SQLite ledger for offline regression only; not the platform PostgreSQL schema."""
from contextlib import contextmanager
import json
import sqlite3

from .common import canonical, check

SCHEMA = (
    "CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
    """CREATE TABLE IF NOT EXISTS batches (
        sequence INTEGER PRIMARY KEY, batch_id TEXT NOT NULL UNIQUE,
        signature TEXT NOT NULL, input_digest TEXT NOT NULL,
        metadata_json TEXT NOT NULL, summary_json TEXT NOT NULL)""",
    """CREATE TABLE IF NOT EXISTS facts (
        source TEXT NOT NULL, source_key TEXT NOT NULL,
        capture_id TEXT NOT NULL UNIQUE, source_event_id TEXT NOT NULL UNIQUE,
        first_batch_id TEXT NOT NULL REFERENCES batches(batch_id),
        body_json TEXT NOT NULL, PRIMARY KEY (source, source_key))""",
    """CREATE TABLE IF NOT EXISTS row_results (
        batch_id TEXT NOT NULL REFERENCES batches(batch_id), row_number INTEGER NOT NULL,
        body_json TEXT NOT NULL, PRIMARY KEY (batch_id, row_number))""",
)


class Ledger:
    def __init__(self, path, fingerprint):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(str(path), timeout=5, isolation_level=None)
        self.connection.row_factory = sqlite3.Row
        try:
            self.connection.execute("PRAGMA foreign_keys=ON")
            self.connection.execute("PRAGMA synchronous=FULL")
            with self.transaction():
                tables = {row[0] for row in self.connection.execute(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")}
                check(not tables or tables == {"metadata", "batches", "facts", "row_results"},
                      "Existing database is not an offline worker ledger", "STATE_FORMAT_UNSUPPORTED")
                for statement in SCHEMA:
                    self.connection.execute(statement)
                stored = self.connection.execute("SELECT value FROM metadata WHERE key='fingerprint'").fetchone()
                check(stored is None or stored["value"] == fingerprint,
                      "State belongs to different references or rules; use a new state path",
                      "STATE_VERSION_MISMATCH")
                if stored is None:
                    self.connection.execute("INSERT INTO metadata VALUES ('fingerprint', ?)", (fingerprint,))
        except BaseException:
            self.close()
            raise

    def close(self):
        self.connection.close()

    @contextmanager
    def transaction(self):
        self.connection.execute("BEGIN IMMEDIATE")
        try:
            yield
            self.connection.commit()
        except BaseException:
            self.connection.rollback()
            raise

    def get_batch(self, batch_id):
        return self.connection.execute("SELECT * FROM batches WHERE batch_id=?", (batch_id,)).fetchone()

    def start_batch(self, batch, signature, input_digest):
        self.connection.execute(
            "INSERT INTO batches(batch_id,signature,input_digest,metadata_json,summary_json) VALUES(?,?,?,?,?)",
            (batch.id, signature, input_digest, canonical(batch.metadata), "{}"))

    def finish_batch(self, batch_id, summary):
        self.connection.execute("UPDATE batches SET summary_json=? WHERE batch_id=?",
                                (canonical(summary), batch_id))

    def get_fact(self, source, key):
        row = self.connection.execute("SELECT body_json FROM facts WHERE source=? AND source_key=?",
                                      (source, key)).fetchone()
        return json.loads(row["body_json"]) if row else None

    def put_fact(self, fact):
        self.connection.execute(
            "INSERT INTO facts(source,source_key,capture_id,source_event_id,first_batch_id,body_json)"
            " VALUES(?,?,?,?,?,?)",
            (fact["source"], fact["sourceRecordKey"], fact["record"]["captureId"],
             fact["sourceEventId"], fact["firstBatchId"], canonical(fact)))

    def put_row(self, row):
        self.connection.execute("INSERT INTO row_results VALUES(?,?,?)",
                                (row["batchId"], row["rowNumber"], canonical(row)))

    def fact_count(self):
        return self.connection.execute("SELECT COUNT(*) FROM facts").fetchone()[0]

    def snapshot(self):
        """Consistent local snapshot; capture ordering here is identity order, not an API query."""
        with self.transaction():
            rows = [json.loads(row[0]) for row in self.connection.execute(
                "SELECT r.body_json FROM row_results r JOIN batches b ON r.batch_id=b.batch_id"
                " ORDER BY b.sequence,r.row_number")]
            facts = [json.loads(row[0]) for row in self.connection.execute(
                "SELECT body_json FROM facts ORDER BY source,source_key")]
            batches = [json.loads(row[0]) for row in self.connection.execute(
                "SELECT summary_json FROM batches ORDER BY sequence")]
            fingerprint = self.connection.execute("SELECT value FROM metadata WHERE key='fingerprint'").fetchone()[0]
        return rows, facts, batches, fingerprint
