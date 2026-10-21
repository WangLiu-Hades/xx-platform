"""165-table storage probes; no production approval or TaskStore implementation."""
import unittest
import uuid

from data_worker.pg.connection import connect, require_lab
from .integration import write_json


class CurrentContractPostgresTests(unittest.TestCase):
    def setUp(self):
        import psycopg
        self.pg = psycopg
        self.db = connect(self.dsn)
        self.addCleanup(self.db.close)
        require_lab(self.db)
        self.db.execute("BEGIN")
        self.addCleanup(self.db.execute, "ROLLBACK")

    def rejected(self, sqlstate, operation):
        with self.assertRaises(self.pg.Error) as error:
            with self.db.transaction():
                operation()
        self.assertEqual(error.exception.sqlstate, sqlstate)

    def approval(self, **changes):
        values = dict(id=str(uuid.uuid4()), requester=self.owner, executor=self.owner,
                      payload="0" * 64, ciphertext=b"synthetic encrypted envelope placeholder",
                      votes=1, status="PENDING", expires="2026-10-22T00:00:00Z",
                      grant=None, consumed=None)
        values.update(changes)
        self.db.execute("""
            INSERT INTO operation.action_approval
                (id,requester_user_id,executor_user_id,operation_id,policy_version,payload_digest,
                 frozen_payload_ciphertext,encryption_key_id,summary,required_approvals,status,
                 created_at,expires_at,execution_grant_expires_at,consumed_at)
            VALUES(%(id)s::uuid,%(requester)s::uuid,%(executor)s::uuid,'replayIngestionBatch',
                   'PD-2026-10-19.v1',%(payload)s,%(ciphertext)s,'synthetic-no-real-key',
                   'Synthetic D3-12R DDL probe',%(votes)s,%(status)s,
                   '2026-10-21T00:00:00Z',%(expires)s::timestamptz,%(grant)s::timestamptz,
                   %(consumed)s::timestamptz)
            """, values)
        return values["id"]

    def test_approval_storage_constraints_and_foreign_keys(self):
        self.approval()
        for changes in ({"payload": "X" * 64}, {"ciphertext": b""}, {"votes": 3},
                        {"expires": "2026-10-20T00:00:00Z"}, {"status": "APPROVED"},
                        {"status": "CONSUMED", "grant": "2026-10-21T01:00:00Z"},
                        {"grant": "2026-10-23T00:00:00Z"}):
            with self.subTest(changes=list(changes)):
                self.rejected("23514", lambda: self.approval(**changes))
        self.rejected("23503", lambda: self.approval(requester=str(uuid.uuid4())))
        self.approval(status="CONSUMED", grant="2026-10-21T01:00:00Z", consumed="2026-10-21T00:30:00Z")
        write_json(self.output / "contract-approval-constraints.json", {
            "status": "PASS", "scope": "DDL checks and FK only; no approval authorization service",
            "rollbackRegisteredInCleanup": True, "syntheticCiphertextNotEncryptionValidation": True})

    def test_decisions_are_unique_and_append_only(self):
        approval = self.approval()
        decision = str(uuid.uuid4())
        self.db.execute("""
            INSERT INTO audit.action_approval_decision(id,approval_id,reviewer_user_id,decision,reason)
            VALUES(%s::uuid,%s::uuid,%s::uuid,'APPROVE','Synthetic constraint check')
            """, (decision, approval, self.owner))
        self.rejected("23505", lambda: self.db.execute("""
            INSERT INTO audit.action_approval_decision(id,approval_id,reviewer_user_id,decision,reason)
            VALUES(%s::uuid,%s::uuid,%s::uuid,'REJECT','Synthetic duplicate check')
            """, (str(uuid.uuid4()), approval, self.owner)))
        for command in ("UPDATE audit.action_approval_decision SET reason='A replacement reason' WHERE id=%s::uuid",
                        "DELETE FROM audit.action_approval_decision WHERE id=%s::uuid"):
            self.rejected("23514", lambda: self.db.execute(command, (decision,)))
        row = self.db.execute("SELECT decision,reason FROM audit.action_approval_decision WHERE id=%s::uuid",
                              (decision,)).fetchone()
        self.assertEqual(row["decision"], "APPROVE")
        self.assertEqual(row["reason"], "Synthetic constraint check")
        write_json(self.output / "contract-decision-append-only.json", {
            "status": "PASS", "scope": "Unique vote and mutation trigger only; self-approval exclusion remains service-owned",
            "rollbackRegisteredInCleanup": True})

    def test_decryption_alters_exist_without_worker_decryption_writes(self):
        rows = self.db.execute("""
            SELECT table_name,column_name,is_nullable,data_type FROM information_schema.columns
            WHERE table_schema='operation' AND table_name=ANY(%s::text[])
              AND column_name=ANY(%s::text[])
            """, (["decryption_request", "decryption_result_grant"],
                   ["approval_id", "result_file_id", "consumed_at"])).fetchall()
        fields = {(r["table_name"], r["column_name"]): r for r in rows}
        self.assertEqual(fields[("decryption_request", "approval_id")]["data_type"], "uuid")
        self.assertEqual(fields[("decryption_result_grant", "approval_id")]["data_type"], "uuid")
        self.assertEqual(fields[("decryption_result_grant", "result_file_id")]["is_nullable"], "YES")
        self.assertIn(("decryption_result_grant", "consumed_at"), fields)
        constraint = self.db.execute("""
            SELECT pg_get_constraintdef(oid) AS definition FROM pg_constraint
            WHERE conrelid='operation.decryption_result_grant'::regclass
              AND conname='ck_decrypt_controlled_no_file'
            """).fetchone()
        self.assertIsNotNone(constraint)
        self.assertIn("approval_id IS NULL", constraint["definition"])
        self.assertIn("result_file_id IS NULL", constraint["definition"])

    def test_new_design_does_not_enable_unimplemented_features(self):
        rows = self.db.execute("SELECT feature_code,enabled FROM core.feature_config ORDER BY feature_code").fetchall()
        self.assertEqual(len(rows), 12)
        self.assertTrue(all(row["enabled"] is False for row in rows))
        self.assertTrue({"RAW_INGESTION", "DECRYPTION", "FACE_SEARCH"} <= {r["feature_code"] for r in rows})
