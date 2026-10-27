"""Transactional platform-table writes; caller owns task publication through an injected callback."""
from dataclasses import dataclass
from copy import deepcopy

from ..common import RowError, WorkerError, canonical, check, digest, file_digest, uuid_text
from ..plate import PlateAdapter, VERSIONS
from .projection import SELECT_FIELDS, FROM_TABLES, to_fact
from .execution import IngestBudget, ControlPoint, PublishSession, prepare_batch, frozen_descriptor, safe_error_text


@dataclass(frozen=True)
class Lease:
    task_id: str
    worker_id: str
    attempt_no: int
    generation: int

    def __post_init__(self):
        check(uuid_text(self.task_id) and isinstance(self.worker_id, str) and
              0 < len(self.worker_id) <= 200, "Invalid task lease")
        check(type(self.attempt_no) is int and self.attempt_no >= 1 and
              type(self.generation) is int and self.generation >= 1, "Invalid attempt/generation")


class StoragePlateAdapter(PlateAdapter):
    def normalize(self, raw, source, received_at):
        result = super().normalize(raw, source, received_at)
        for field in ("recordId", "deviceKey", "plateColor"):
            value = raw.get(field)
            if isinstance(value, str):
                if "\x00" in value:
                    raise RowError("STORAGE_TEXT_INVALID", field)
                if len(value) > 200:
                    raise RowError("STORAGE_FIELD_TOO_LONG", field)
        return result


def frozen_input(settings, batch):
    return frozen_descriptor(settings, batch, file_digest(batch.input_path))


class PgIngestor:
    def __init__(self, connection, adapter, settings, publisher, *, control=None, budget=None):
        check(callable(publisher), "A DEV-01 publication callback is required", "PG_PUBLISHER_REQUIRED")
        check(callable(control), "An explicit H04 control callback is required", "PG_CONTROL_REQUIRED")
        check(isinstance(budget, IngestBudget), "An explicit bounded-batch budget is required",
              "PG_INGEST_BUDGET_REQUIRED")
        self.db, self.adapter, self.settings, self.publisher = connection, adapter, settings, publisher
        self.control, self.budget, self._session = control, budget, None

    def _execute(self, sql, params=None):
        return (self._session or self.db).execute(sql, params)

    def assert_lease(self, lease, minimum_seconds=0):
        row = self._execute("""
            SELECT t.id, EXTRACT(epoch FROM t.lease_until-clock_timestamp()) AS remaining_seconds
            FROM analysis.async_task t
            JOIN analysis.task_attempt a ON a.task_id=t.id AND a.attempt_no=t.current_attempt_no
            WHERE t.id=%s::uuid AND t.status='RUNNING' AND NOT t.deleted
              AND t.lease_owner=%s AND t.current_attempt_no=%s AND t.lease_generation=%s
              AND t.lease_until>clock_timestamp() AND t.cancel_requested_at IS NULL
              AND a.worker_id=t.lease_owner AND a.lease_generation=t.lease_generation AND a.result IS NULL
            """, (lease.task_id, lease.worker_id, lease.attempt_no, lease.generation)).fetchone()
        check(row is not None, "Lease expired, cancelled, replaced, or attempt is inconsistent", "TASK_LEASE_INVALID")
        check(float(row["remaining_seconds"]) > minimum_seconds,
              "Renew the lease before the bounded publication window", "TASK_LEASE_TOO_SHORT")

    def _check_input(self, batch, expected, task, stored):
        check(task["parameter_snapshot"] == expected and task["parameter_hash"] == digest(expected),
              "Task input is not the frozen synthetic input", "BATCH_INPUT_CHANGED")
        check(str(stored["task_id"]) == batch.metadata["taskId"] and
              stored["source_code"] == batch.metadata["source"] and
              stored["mapping_version"] == batch.metadata["mappingVersion"] and
              stored["dedup_rule_version"] == batch.metadata["dedupVersion"] and
              stored["mode"] == batch.metadata["mode"] and
              (str(stored["original_batch_id"]) if stored["original_batch_id"] else None) ==
              batch.metadata["originalBatchId"], "Batch metadata differs from frozen task", "BATCH_INPUT_CHANGED")

    def ingest(self, batch, lease):
        check(self.db.autocommit and int(self.db.info.transaction_status) == 0,
              "Ingestor must own its transaction on an idle autocommit connection",
              "PG_INGEST_TRANSACTION_REQUIRED")
        batch = type(batch)(deepcopy(batch.metadata), batch.input_path)
        check(lease.task_id == batch.metadata["taskId"], "Task lease does not match batch")
        # Read-only preflight. In particular there is no task FOR UPDATE during preparation.
        task = self._execute("SELECT * FROM analysis.async_task WHERE id=%s::uuid",
                             (lease.task_id,)).fetchone()
        stored = self._execute("SELECT * FROM capture.ingestion_batch WHERE id=%s::uuid",
                               (batch.id,)).fetchone()
        check(task is not None and stored is not None, "DEV-01 task/batch seed is missing",
              "PG_BATCH_SEED_MISSING")
        check(task["task_type"] == "INGESTION" and not task["deleted"],
              "Unsupported or deleted task", "PG_TASK_INVALID")
        cached = stored["status"] == "SUCCESS" and task["status"] == "SUCCESS"
        # A completed immutable batch can be retrieved without reviving its old lease.
        control = (lambda *_: True) if cached else self.control
        prepared = prepare_batch(self.settings, self.adapter, batch, lease, control, self.budget)
        self._check_input(batch, prepared.expected, task, stored)
        if not cached:
            check(control(lease, ControlPoint("BEFORE_PUBLISH", len(prepared.rows), prepared.input_bytes)) is True,
                  "H04 did not confirm publication control", "TASK_CONTROL_REJECTED")
        try:
            with self.db.transaction():
                self._session = PublishSession(self.db, self.budget)
                result = self._publish(batch, lease, prepared)
                self._session.remaining_ms()
            return result
        finally:
            self._session = None

    def _publish(self, batch, lease, prepared):
        expected = prepared.expected
        rows = []
        counts = {"ACCEPTED": 0, "DUPLICATE": 0, "REJECTED": 0}
        # The final bounded transaction locks task, then batch. DEV-01 must use this order.
        task = self._execute("SELECT * FROM analysis.async_task WHERE id=%s::uuid FOR UPDATE",
                             (lease.task_id,)).fetchone()
        stored = self._execute("SELECT * FROM capture.ingestion_batch WHERE id=%s::uuid FOR UPDATE",
                               (batch.id,)).fetchone()
        check(task is not None and stored is not None, "DEV-01 task/batch seed is missing",
              "PG_BATCH_SEED_MISSING")
        check(task["task_type"] == "INGESTION" and not task["deleted"], "Unsupported or deleted task",
              "PG_TASK_INVALID")
        self._check_input(batch, expected, task, stored)
        if stored["status"] == "SUCCESS":
            check(task["status"] == "SUCCESS", "Batch/task publication is inconsistent",
                  "PG_PUBLICATION_INCONSISTENT")
            return self._summary(batch, stored), self.load_rows(batch), True
        check(stored["status"] == "RUNNING", "Batch must already be running", "PG_BATCH_NOT_RUNNING")
        self.assert_lease(lease, (self.budget.publish_timeout_ms + self.budget.lease_margin_ms) / 1000)
        if stored["source_file_id"] is not None:
            source_file = self._execute(
                "SELECT sha256,status FROM core.file_asset WHERE id=%s::uuid FOR SHARE",
                (stored["source_file_id"],)).fetchone()
            check(source_file is not None and source_file["status"] == "ACTIVE" and
                  source_file["sha256"] == expected["inputSha256"],
                  "Source file metadata does not match the frozen input", "PG_SOURCE_FILE_INVALID")
        count = self._execute("SELECT count(*) AS n FROM capture.ingestion_item WHERE batch_id=%s::uuid",
                              (batch.id,)).fetchone()["n"]
        check(count == 0, "Unfinished batch contains prior rows; reconciliation is required", "PG_PARTIAL_BATCH")
        mapping = self._execute("""
            SELECT m.schema_version, m.dedup_rule_version, m.mapping_definition, s.protocol_version
            FROM capture.source_mapping_version m JOIN capture.ingestion_source s USING(source_code)
            WHERE m.source_code=%s AND m.mapping_version=%s AND m.enabled AND s.enabled
              AND s.adapter_code='synthetic-plate-v0'
            FOR SHARE OF m,s
            """, (batch.metadata["source"], batch.metadata["mappingVersion"])).fetchone()
        check(mapping is not None and mapping["schema_version"] == batch.metadata["schemaVersion"] and
              mapping["dedup_rule_version"] == batch.metadata["dedupVersion"] and
              mapping["protocol_version"] == batch.metadata["schemaVersion"] and
              mapping["mapping_definition"] == {"synthetic": True, "fingerprint": self.settings.fingerprint},
              "Source/mapping is unavailable or differs from input", "PG_MAPPING_INVALID")
        if batch.metadata["mode"] == "REPLAY":
            original = self._execute("""
                SELECT b.status,b.source_code,t.parameter_snapshot
                FROM capture.ingestion_batch b JOIN analysis.async_task t ON t.id=b.task_id
                WHERE b.id=%s::uuid
                """, (batch.metadata["originalBatchId"],)).fetchone()
            check(original and original["status"] == "SUCCESS" and
                  original["source_code"] == batch.metadata["source"] and
                  isinstance(original["parameter_snapshot"], dict) and
                  original["parameter_snapshot"].get("inputSha256") == expected["inputSha256"],
                  "Replay requires committed original source and bytes", "REPLAY_INPUT_CHANGED")
        for item in prepared.rows:
            self._session.remaining_ms()
            row = dict(item.row)
            if item.normalized is not None:
                try:
                    outcome, capture_id, source_id = self._write_fact(
                        batch, stored, {"deviceKey": item.device_key}, item.normalized, row["rowNumber"])
                    row.update(outcome=outcome, captureId=capture_id, sourceEventId=source_id,
                               warnings=item.normalized.warnings)
                except RowError as exc:
                    row.update(outcome="REJECTED", reasonCode=exc.code, field=safe_error_text(exc.field) or "$")
            self._write_row(row, lease, batch.metadata["receivedAt"])
            rows.append(row)
            counts[row["outcome"]] += 1
        self.assert_lease(lease)
        self._execute("""
            UPDATE capture.ingestion_batch
            SET accepted_count=%s,rejected_count=%s,duplicate_count=%s,
                status='SUCCESS',finished_at=clock_timestamp()
            WHERE id=%s::uuid AND status='RUNNING'
            """, (counts["ACCEPTED"], counts["REJECTED"], counts["DUPLICATE"], batch.id))
        check(self.publisher(self._session, lease, batch) is True,
              "Task publication was rejected", "TASK_PUBLICATION_REJECTED")
        published = self._execute("""
            SELECT t.id FROM analysis.async_task t
            JOIN analysis.task_attempt a ON a.task_id=t.id AND a.attempt_no=%s
            WHERE t.id=%s::uuid AND t.status='SUCCESS' AND a.result='SUCCESS'
              AND a.lease_generation=%s AND t.lease_generation=%s
            """, (lease.attempt_no, lease.task_id, lease.generation, lease.generation)).fetchone()
        check(published is not None, "Publisher did not finish the matching task attempt",
              "PG_PUBLICATION_INCONSISTENT")
        summary = {"batchId": batch.id, "name": batch.metadata["name"], "inputRows": len(rows),
                   "acceptedCount": counts["ACCEPTED"], "duplicateCount": counts["DUPLICATE"],
                   "rejectedCount": counts["REJECTED"], "factCountAfter": self.fact_count()}
        return summary, rows, False

    def _write_fact(self, batch, stored, raw, value, number):
        record = value.record
        image = record["plate"].get("vehicleImageAssetId")
        if image is not None:
            available = self._execute("SELECT id FROM core.file_asset WHERE id=%s::uuid AND status='ACTIVE' FOR SHARE",
                                        (image,)).fetchone()
            if not available:
                raise RowError("FIXTURE_IMAGE_REFERENCE", "vehicleImageRef")
        # Both the deterministic primary key and the natural key can race.
        # Ignore either unique conflict, then verify the natural key and payload below.
        inserted = self._execute("""
            INSERT INTO capture.source_event
                (id,source_code,source_record_key,data_type,source_device_key,source_capture_time,
                 raw_file_id,payload_digest,first_received_at)
            VALUES(%s::uuid,%s,%s,'PLATE',%s,%s::timestamptz,%s::uuid,%s,%s::timestamptz)
            ON CONFLICT DO NOTHING RETURNING id
            """, (value.source_event_id, batch.metadata["source"], value.key, raw["deviceKey"],
                  record["captureTime"], stored["source_file_id"], value.payload_digest, record["ingestedAt"])).fetchone()
        if inserted is None:
            existing = self._execute("""
                SELECT s.id,s.payload_digest,e.id AS capture_id
                FROM capture.source_event s LEFT JOIN capture.capture_event e ON e.source_event_id=s.id
                WHERE s.source_code=%s AND s.source_record_key=%s
                """, (batch.metadata["source"], value.key)).fetchone()
            check(existing is not None, "Concurrent source event disappeared", "PG_SOURCE_FACT_INCOMPLETE")
            if existing["payload_digest"] != value.payload_digest:
                raise RowError("FIXTURE_KEY_CONFLICT", "recordId")
            check(existing["capture_id"] is not None, "Source event has no capture fact", "PG_SOURCE_FACT_INCOMPLETE")
            return "DUPLICATE", str(existing["capture_id"]), str(existing["id"])
        self._execute("""
            INSERT INTO capture.capture_event
                (id,source_event_id,batch_id,data_type,primary_target_id,target_type,device_id,site_id,
                 region_code_at_capture,device_category_at_capture,capture_time,ingested_at,
                 location,location_source,status,trace_id)
            VALUES(%s::uuid,%s::uuid,%s::uuid,'PLATE',%s::uuid,'PLATE',%s::uuid,%s::uuid,
                   (SELECT region_code FROM core.site WHERE id=%s::uuid),
                   (SELECT category_code FROM core.device WHERE id=%s::uuid),%s::timestamptz,%s::timestamptz,
                   CASE WHEN %s::double precision IS NULL THEN NULL
                        ELSE ST_SetSRID(ST_MakePoint(%s::double precision,%s::double precision),4326) END,
                   %s,'VALID',%s)
            """, (record["captureId"], value.source_event_id, batch.id, record["targetId"], record["deviceId"],
                  record["siteId"], record["siteId"], record["deviceId"], record["captureTime"], record["ingestedAt"],
                  record["longitude"], record["longitude"], record["latitude"],
                  "SOURCE" if record["longitude"] is not None else None, "d3-lab:" + batch.id))
        self._execute("""
            INSERT INTO capture.plate_record(capture_id,plate_target_id,plate_color,vehicle_image_asset_id)
            VALUES(%s::uuid,%s::uuid,%s,%s::uuid)
            """, (record["captureId"], record["targetId"], record["plate"].get("plateColor"), image))
        return "ACCEPTED", record["captureId"], value.source_event_id

    def _write_row(self, row, lease, occurred_at):
        item_id = self.adapter.identity("item:" + row["batchId"] + ":" + str(row["rowNumber"]))
        self._execute("""
            INSERT INTO capture.ingestion_item
                (id,batch_id,row_number,source_event_id,capture_id,processing_status,processed_at,record_kind)
            VALUES(%s::uuid,%s::uuid,%s,%s::uuid,%s::uuid,%s,%s::timestamptz,'CAPTURE')
            """, (item_id, row["batchId"], row["rowNumber"], row["sourceEventId"],
                  row["captureId"], row["outcome"], occurred_at))
        issues = []
        if row["outcome"] == "REJECTED":
            issues.append((row["reasonCode"], row["field"]))
            self._execute("""
                INSERT INTO analysis.task_row_error
                    (id,task_id,attempt_no,row_number,source_record_key,field_name,error_code,reason)
                VALUES(%s::uuid,%s::uuid,%s,%s,%s,%s,%s,%s)
                """, (self.adapter.identity("row-error:" + item_id + ":" + str(lease.attempt_no)), lease.task_id, lease.attempt_no,
                      row["rowNumber"], row["sourceRecordKey"], row["field"], row["reasonCode"], row["reasonCode"]))
        for warning in row["warnings"]:
            issues.append((warning, "deviceKey" if warning == "FIXTURE_DEVICE_UNMAPPED" else "plateText"))
        for code, field in issues:
            self._execute("""
                INSERT INTO capture.quality_issue
                    (id,ingestion_item_id,issue_code,field_name,reason,occurred_at,rule_version)
                VALUES(%s::uuid,%s::uuid,%s,%s,%s,%s::timestamptz,%s)
                """, (self.adapter.identity("quality:" + item_id + ":" + code), item_id, code, field,
                      code, occurred_at, VERSIONS["qualityVersion"]))

    def fact_count(self):
        return self._execute("""
            SELECT count(*) AS n FROM capture.capture_event e
            JOIN capture.source_event s ON s.id=e.source_event_id
            WHERE s.source_code=ANY(%s::text[]) AND e.data_type='PLATE'
            """, (sorted(self.adapter.sources),)).fetchone()["n"]

    def facts(self):
        return [to_fact(row) for row in self._execute(
            "SELECT " + SELECT_FIELDS + FROM_TABLES +
            " WHERE e.data_type='PLATE' AND s.source_code=ANY(%s::text[]) ORDER BY s.source_code,s.source_record_key",
            (sorted(self.adapter.sources),)).fetchall()]

    def _summary(self, batch, stored):
        return {"batchId": batch.id, "name": batch.metadata["name"],
                "inputRows": stored["accepted_count"] + stored["duplicate_count"] + stored["rejected_count"],
                "acceptedCount": stored["accepted_count"], "duplicateCount": stored["duplicate_count"],
                "rejectedCount": stored["rejected_count"], "factCountAfter": self.fact_count()}

    def load_rows(self, batch):
        stored_rows = self._execute("""
            SELECT i.row_number,i.processing_status,i.capture_id,i.source_event_id,
                   COALESCE(s.source_record_key,err.source_record_key) AS source_record_key,
                   err.error_code,err.field_name,
                   COALESCE((SELECT jsonb_agg(q.issue_code ORDER BY q.issue_code)
                     FROM capture.quality_issue q WHERE q.ingestion_item_id=i.id AND q.issue_code IN
                     ('FIXTURE_DEVICE_UNMAPPED','FIXTURE_TARGET_UNRESOLVED')),'[]'::jsonb) AS warnings
            FROM capture.ingestion_item i
            LEFT JOIN capture.source_event s ON s.id=i.source_event_id
            LEFT JOIN LATERAL (
                SELECT er.source_record_key,er.error_code,er.field_name
                FROM analysis.task_row_error er
                WHERE er.task_id=%s::uuid AND er.row_number=i.row_number
                ORDER BY er.attempt_no DESC LIMIT 1
            ) err ON i.processing_status='REJECTED'
            WHERE i.batch_id=%s::uuid ORDER BY i.row_number
            """, (batch.metadata["taskId"], batch.id)).fetchall()
        return [{"batchId": batch.id, "batchName": batch.metadata["name"], "taskId": batch.metadata["taskId"],
                 "source": batch.metadata["source"], "rowNumber": row["row_number"],
                 "sourceRecordKey": row["source_record_key"], "outcome": row["processing_status"],
                 "captureId": str(row["capture_id"]) if row["capture_id"] else None,
                 "sourceEventId": str(row["source_event_id"]) if row["source_event_id"] else None,
                 "reasonCode": row["error_code"], "field": row["field_name"], "warnings": row["warnings"]}
                for row in stored_rows]
