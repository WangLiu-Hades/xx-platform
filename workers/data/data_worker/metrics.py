"""Audit complete synthetic SQLite exports and compute internal engineering metrics only."""
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
from pathlib import Path
import re

from .common import WorkerError, canonical, check, strict_json, utc, uuid_text

VERSION = "d3-engineering-metrics-v0"
FILES = ("rows.jsonl", "captures.jsonl", "quality.jsonl", "summary.json")
OUTCOMES = ("ACCEPTED", "DUPLICATE", "REJECTED")
WARNING_FIELDS = {"FIXTURE_DEVICE_UNMAPPED": "deviceKey",
                  "FIXTURE_TARGET_UNRESOLVED": "plateText"}


def require(condition, label):
    check(condition, label, "METRIC_EVIDENCE_INVALID")


def timestamp(value):
    try:
        return datetime.fromisoformat(utc(value).replace("Z", "+00:00"))
    except (ValueError, TypeError, OverflowError) as exc:
        raise WorkerError("METRIC_EVIDENCE_INVALID", "Invalid evidence timestamp") from exc


def count_value(value):
    return type(value) is int and value >= 0


def load_export(directory):
    """Read each byte sequence once, then hash and parse that exact sequence."""
    directory = Path(directory).resolve()
    try:
        manifest_bytes = (directory / "export.json").read_bytes()
        manifest = strict_json(manifest_bytes.decode("utf-8"))
        require(isinstance(manifest, dict) and manifest.get("complete") is True,
                "Export is incomplete")
        require(isinstance(manifest.get("files"), dict) and set(manifest["files"]) == set(FILES),
                "Export must contain exactly the four known evidence files")
        payloads = {}
        for name in FILES:
            path = (directory / name).resolve()
            require(path.parent == directory, "Export file resolves outside the export directory")
            payload = path.read_bytes()
            require(hashlib.sha256(payload).hexdigest() == manifest["files"][name],
                    "Export checksum differs: " + name)
            payloads[name] = payload.decode("utf-8")
        require((directory / "export.json").read_bytes() == manifest_bytes, "Export changed during audit")
        result = {"manifest": manifest, "exportSha256": hashlib.sha256(manifest_bytes).hexdigest(),
                  "summary": strict_json(payloads["summary.json"])}
        for name, key in (("rows.jsonl", "rows"), ("captures.jsonl", "facts"), ("quality.jsonl", "quality")):
            result[key] = [strict_json(line) for line in payloads[name].splitlines()]
        return result
    except WorkerError:
        raise
    except (OSError, ValueError, UnicodeError, RecursionError) as exc:
        raise WorkerError("METRIC_EVIDENCE_INVALID", "Cannot read a complete export") from exc


def _audit(data):
    summary, manifest = data["summary"], data["manifest"]
    rows, facts, quality = data["rows"], data["facts"], data["quality"]
    require(isinstance(summary, dict) and summary.get("synthetic") is True and
            summary.get("execution") == "offline-sqlite", "Only complete synthetic SQLite exports are supported")
    require(isinstance(summary.get("stateFingerprint"), str) and
            re.fullmatch(r"[0-9a-f]{64}", summary["stateFingerprint"]) is not None and
            summary["stateFingerprint"] == manifest.get("stateFingerprint"), "Reference fingerprint mismatch")
    as_of = timestamp(manifest["createdAt"]).isoformat().replace("+00:00", "Z")
    require(isinstance(summary["batches"], list), "Missing batch inventory")
    batches = {}
    for batch in summary["batches"]:
        require(isinstance(batch, dict) and uuid_text(batch["batchId"]) and
                batch["batchId"] not in batches, "Duplicate or invalid batch ID")
        require(isinstance(batch["name"], str) and batch["name"], "Invalid batch name")
        require(all(count_value(batch[key]) for key in
                    ("inputRows", "acceptedCount", "duplicateCount", "rejectedCount", "factCountAfter")),
                "Invalid batch counters")
        batches[batch["batchId"]] = batch

    fact_index, source_keys, event_ids = {}, set(), set()
    for fact in facts:
        require(isinstance(fact, dict) and isinstance(fact["record"], dict), "Invalid fact")
        record = fact["record"]
        key = (fact["source"], fact["sourceRecordKey"])
        require(all(isinstance(value, str) and value for value in key), "Invalid fact source key")
        require(uuid_text(record["captureId"]) and record["captureId"] not in fact_index and
                key not in source_keys and uuid_text(fact["sourceEventId"]) and
                fact["sourceEventId"] not in event_ids, "Duplicate fact identity")
        require(fact["firstBatchId"] in batches and record["source"] == fact["source"] and
                record["status"] == "VALID" and record["dataType"] == "PLATE",
                "Invalid fact origin or unsupported fact type")
        require(isinstance(fact["payloadDigest"], str) and
                re.fullmatch(r"[0-9a-f]{64}", fact["payloadDigest"]), "Invalid payload digest")
        timestamp(record["captureTime"])
        timestamp(record["ingestedAt"])
        fact_index[record["captureId"]] = fact
        source_keys.add(key)
        event_ids.add(fact["sourceEventId"])

    row_keys, accepted = set(), Counter()
    counts, batch_counts, source_counts = Counter(), defaultdict(Counter), defaultdict(Counter)
    batch_identity, expected_quality = {}, Counter()
    identity_fields = ("batchId", "batchName", "taskId", "source", "rowNumber",
                       "sourceRecordKey", "outcome", "captureId")
    for row in rows:
        require(isinstance(row, dict) and row["batchId"] in batches, "Unknown row batch")
        row_key = (row["batchId"], row["rowNumber"])
        require(type(row["rowNumber"]) is int and row["rowNumber"] > 0 and row_key not in row_keys,
                "Duplicate or invalid physical row number")
        require(row["outcome"] in OUTCOMES and row["batchName"] == batches[row["batchId"]]["name"] and
                uuid_text(row["taskId"]) and isinstance(row["source"], str) and row["source"],
                "Invalid row outcome or trace metadata")
        identity = (row["taskId"], row["source"])
        require(row["batchId"] not in batch_identity or batch_identity[row["batchId"]] == identity,
                "Task/source changed within a batch")
        batch_identity[row["batchId"]] = identity
        warnings = row["warnings"]
        require(isinstance(warnings, list) and all(isinstance(w, str) and w in WARNING_FIELDS for w in warnings)
                and len(warnings) == len(set(warnings)), "Unsupported or duplicate warnings")
        if row["outcome"] == "REJECTED":
            require(row["captureId"] is None and row["sourceEventId"] is None and not warnings and
                    isinstance(row["reasonCode"], str) and row["reasonCode"], "Rejected row claimed a fact")
        else:
            require(row["captureId"] in fact_index and row["reasonCode"] is None and row["field"] is None,
                    "Accepted/duplicate row has no fact or has rejection fields")
            fact = fact_index[row["captureId"]]
            require((row["source"], row["sourceRecordKey"], row["sourceEventId"]) ==
                    (fact["source"], fact["sourceRecordKey"], fact["sourceEventId"]),
                    "Row points to a different source event")
            if row["outcome"] == "ACCEPTED":
                require(fact["firstBatchId"] == row["batchId"], "Fact first batch differs from accepted row")
                accepted[row["captureId"]] += 1
        base = {key: row[key] for key in identity_fields}
        if row["outcome"] == "REJECTED":
            expected_quality[canonical(dict(base, severity="ERROR", reasonCode=row["reasonCode"],
                                            field=row["field"]))] += 1
        for warning in warnings:
            expected_quality[canonical(dict(base, severity="WARNING", reasonCode=warning,
                                            field=WARNING_FIELDS[warning]))] += 1
        row_keys.add(row_key)
        counts[row["outcome"]] += 1
        batch_counts[row["batchId"]][row["outcome"]] += 1
        source_counts[row["source"]][row["outcome"]] += 1
    require(set(accepted) == set(fact_index) and all(n == 1 for n in accepted.values()),
            "Complete ledger requires exactly one accepted row per fact")
    require(Counter(canonical(issue) for issue in quality) == expected_quality,
            "Quality evidence differs from row errors/warnings")
    require(isinstance(summary["totals"], dict) and set(summary["totals"]) == set(OUTCOMES) and
            all(count_value(summary["totals"][key]) and summary["totals"][key] == counts[key]
                for key in OUTCOMES), "Summary outcome counters differ")
    require(all(count_value(summary[key]) for key in ("totalProcessingRows", "finalFacts", "qualityRecords"))
            and summary["totalProcessingRows"] == len(rows) and summary["finalFacts"] == len(facts) and
            summary["qualityRecords"] == len(quality), "Summary inventory counters differ")
    cumulative = 0
    per_batch = []
    for batch_id, batch in batches.items():
        actual = batch_counts[batch_id]
        cumulative += actual["ACCEPTED"]
        require(batch["inputRows"] == sum(actual.values()) and
                batch["acceptedCount"] == actual["ACCEPTED"] and
                batch["duplicateCount"] == actual["DUPLICATE"] and
                batch["rejectedCount"] == actual["REJECTED"] and batch["factCountAfter"] == cumulative,
                "Batch counts or inventory checkpoint differ")
        per_batch.append(dict(batch, source=batch_identity.get(batch_id, (None, None))[1]))
    per_source = [{"source": source, "processingRows": sum(c.values()), "acceptedRows": c["ACCEPTED"],
                   "duplicateRows": c["DUPLICATE"], "rejectedRows": c["REJECTED"],
                   "factInventory": sum(f["source"] == source for f in facts)}
                  for source, c in sorted(source_counts.items())]

    values = [
        ("D3_PROCESSING_ROWS", len(rows), "row"),
        ("D3_ACCEPTED_ROWS", counts["ACCEPTED"], "row"),
        ("D3_DUPLICATE_ROWS", counts["DUPLICATE"], "row"),
        ("D3_REJECTED_ROWS", counts["REJECTED"], "row"),
        ("D3_FACT_INVENTORY", len(facts), "fact"),
        ("D3_QUALITY_ERRORS", sum(i["severity"] == "ERROR" for i in quality), "issue"),
        ("D3_QUALITY_WARNINGS", sum(i["severity"] == "WARNING" for i in quality), "issue"),
    ]
    metrics = {code: {"value": value, "unit": unit, "status": "READY", "asOf": as_of}
               for code, value, unit in values}
    metrics["D3_REJECTION_RATIO"] = {
        "value": counts["REJECTED"] / len(rows) if rows else None, "unit": "ratio",
        "status": "READY" if rows else "UNAVAILABLE", "asOf": as_of,
        "numerator": counts["REJECTED"], "denominator": len(rows),
        "reason": None if rows else "EMPTY_DENOMINATOR",
    }
    metrics["SOURCE_CAPTURE_COUNT"] = {"value": None, "unit": "event", "status": "UNAVAILABLE",
                                       "asOf": None, "reason": "SOURCE_COUNTERS_NOT_PROVIDED"}
    metrics["DEVICE_ONLINE_RATIO"] = {"value": None, "unit": "ratio", "status": "UNAVAILABLE",
                                      "asOf": None, "reason": "TELEMETRY_AND_POLICY_NOT_PROVIDED"}

    def observed_high_watermark(field):
        values = [f["record"][field] for f in facts]
        return utc(max(values, key=timestamp)) if values else None

    return {"definitionVersion": VERSION, "scope": "COMPLETE_SYNTHETIC_LEDGER",
            "auditStatus": "PASS", "publicApiReady": False, "postgresExecuted": False,
            "exportSha256": data["exportSha256"], "stateFingerprint": summary["stateFingerprint"],
            "asOf": as_of, "metrics": metrics, "byBatch": per_batch, "bySource": per_source,
            "watermarks": {"maxObservedCaptureTime": observed_high_watermark("captureTime"),
                           "maxObservedIngestedAt": observed_high_watermark("ingestedAt"),
                           "sourceCompletenessWatermark": None},
            "limitations": ["Synthetic receive times are not measured commit times",
                            "Observed maximum timestamp is not a source-completeness watermark",
                            "No device counters, telemetry policy, public authorization or time-bucket metrics"]}


def audit_export(directory):
    try:
        return _audit(load_export(directory))
    except WorkerError:
        raise
    except (KeyError, TypeError, ValueError, AttributeError, IndexError) as exc:
        raise WorkerError("METRIC_EVIDENCE_INVALID", "Malformed or inconsistent evidence") from exc
