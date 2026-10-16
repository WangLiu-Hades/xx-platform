"""Short database checkpoints for the isolated lab only; this is not a production H04 service."""
from data_worker.common import check
from data_worker.pg.connection import require_lab
from data_worker.pg.execution import IngestBudget

# Explicit experiment limits. P-003 production budgets remain to be agreed.
LAB_INGEST_BUDGET = IngestBudget(8 * 1024 * 1024, 1000, 60, 3000, 500, 1000)


class LabControl:
    def __init__(self, connection, lease_seconds=30):
        require_lab(connection)
        check(type(lease_seconds) is int and lease_seconds >= 5, "Invalid lab lease duration")
        self.db, self.lease_seconds = connection, lease_seconds
        self.points = []

    def __call__(self, lease, point):
        check(self.db.autocommit and int(self.db.info.transaction_status) == 0,
              "Lab checkpoint requires an idle connection", "PG_LAB_CONTROL_TRANSACTION")
        with self.db.transaction():
            self.db.execute("SELECT set_config('statement_timeout','1000',true),set_config('lock_timeout','500',true)")
            renewed = self.db.execute("""
                UPDATE analysis.async_task t
                SET lease_until=GREATEST(t.lease_until,clock_timestamp()+make_interval(secs=>%s)),
                    version=version+1
                WHERE t.id=%s::uuid AND t.status='RUNNING' AND NOT t.deleted
                  AND t.lease_owner=%s AND t.current_attempt_no=%s AND t.lease_generation=%s
                  AND t.lease_until>clock_timestamp() AND t.cancel_requested_at IS NULL
                  AND EXISTS (
                    SELECT 1 FROM analysis.task_attempt a WHERE a.task_id=t.id
                      AND a.attempt_no=t.current_attempt_no AND a.worker_id=t.lease_owner
                      AND a.lease_generation=t.lease_generation AND a.result IS NULL)
                RETURNING t.id
                """, (self.lease_seconds, lease.task_id, lease.worker_id, lease.attempt_no, lease.generation)).fetchone()
            check(renewed is not None, "Lab lease is expired, cancelled or superseded", "TASK_LEASE_INVALID")
        self.points.append({"phase": point.phase, "rows": point.processed_rows, "bytes": point.input_bytes})
        return True
