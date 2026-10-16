"""Stream JSONL, isolate bad rows, and atomically commit one offline batch."""
import hashlib
import json

from .common import RowError, check, digest, file_digest
from .plate import parse_line


def input_lines(path, max_line_bytes, hasher):
    """Bounded reads; oversized records are drained without retaining their whole payload."""
    with path.open("rb") as stream:
        number = 0
        while True:
            block = stream.readline(max_line_bytes + 1)
            if not block:
                break
            number += 1
            hasher.update(block)
            oversized = len(block) > max_line_bytes
            if oversized:
                while block and not block.endswith(b"\n"):
                    block = stream.readline(max_line_bytes + 1)
                    hasher.update(block)
                yield number, None, RowError("INPUT_LINE_TOO_LARGE", "$")
            elif block.strip():
                yield number, block, None


def process_batch(ledger, adapter, batch, max_line_bytes):
    """A repeated batch ID returns its committed result; a REPLAY has its own batch ID."""
    input_hash = file_digest(batch.input_path)
    signature = digest({"batch": batch.metadata, "inputSha256": input_hash})
    with ledger.transaction():
        stored = ledger.get_batch(batch.id)
        if stored is not None:
            check(stored["signature"] == signature,
                  "Committed batch ID cannot be reused with changed metadata/input",
                  "BATCH_INPUT_CHANGED")
            return json.loads(stored["summary_json"]), True
        if batch.metadata["mode"] == "REPLAY":
            original = ledger.get_batch(batch.metadata["originalBatchId"])
            check(original is not None, "Replay requires an already committed original batch", "REPLAY_ORIGINAL_MISSING")
            check(json.loads(original["metadata_json"])["source"] == batch.metadata["source"]
                  and original["input_digest"] == input_hash,
                  "Synthetic replay must preserve original source and input bytes", "REPLAY_INPUT_CHANGED")
        ledger.start_batch(batch, signature, input_hash)
        counts = {"ACCEPTED": 0, "DUPLICATE": 0, "REJECTED": 0}
        stream_hash = hashlib.sha256()
        for number, block, input_error in input_lines(batch.input_path, max_line_bytes, stream_hash):
            row = {"batchId": batch.id, "batchName": batch.metadata["name"],
                   "taskId": batch.metadata["taskId"], "source": batch.metadata["source"],
                   "rowNumber": number, "sourceRecordKey": None, "outcome": None,
                   "captureId": None, "sourceEventId": None, "reasonCode": None,
                   "field": None, "warnings": []}
            try:
                if input_error is not None:
                    raise input_error
                raw = parse_line(block)
                row["sourceRecordKey"] = raw.get("recordId") if isinstance(raw.get("recordId"), str) else None
                normalized = adapter.normalize(raw, batch.metadata["source"], batch.metadata["receivedAt"])
                existing = ledger.get_fact(batch.metadata["source"], normalized.key)
                if existing is not None and existing["payloadDigest"] != normalized.payload_digest:
                    raise RowError("FIXTURE_KEY_CONFLICT", "recordId")
                if existing is None:
                    fact = {"source": batch.metadata["source"], "sourceRecordKey": normalized.key,
                            "sourceEventId": normalized.source_event_id, "firstBatchId": batch.id,
                            "payloadDigest": normalized.payload_digest, "record": normalized.record}
                    ledger.put_fact(fact)
                    row["outcome"] = "ACCEPTED"
                else:
                    fact = existing
                    row["outcome"] = "DUPLICATE"
                row.update(captureId=fact["record"]["captureId"], sourceEventId=fact["sourceEventId"],
                           warnings=normalized.warnings)
            except RowError as error:
                row.update(outcome="REJECTED", reasonCode=error.code, field=error.field)
            ledger.put_row(row)
            counts[row["outcome"]] += 1
        check(stream_hash.hexdigest() == input_hash, "Input changed while processing; batch rolled back",
              "INPUT_CHANGED_DURING_READ")
        summary = {"batchId": batch.id, "name": batch.metadata["name"], "inputRows": sum(counts.values()),
                   "acceptedCount": counts["ACCEPTED"], "duplicateCount": counts["DUPLICATE"],
                   "rejectedCount": counts["REJECTED"], "factCountAfter": ledger.fact_count()}
        ledger.finish_batch(batch.id, summary)
    return summary, False
