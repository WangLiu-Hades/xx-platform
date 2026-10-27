"""Export a consistent ledger snapshot; final directory appears only after all files are written."""
from collections import Counter
from datetime import datetime, timezone
import hashlib
import uuid

from .common import canonical, check
from .plate import VERSIONS
from .settings import output_path


def quality_records(rows):
    for row in rows:
        identity = {key: row[key] for key in ("batchId", "batchName", "taskId", "source",
                                             "rowNumber", "sourceRecordKey", "outcome", "captureId")}
        if row["outcome"] == "REJECTED":
            yield dict(identity, severity="ERROR", reasonCode=row["reasonCode"], field=row["field"])
        for reason in row["warnings"]:
            field = {"FIXTURE_DEVICE_UNMAPPED": "deviceKey", "FIXTURE_TARGET_UNRESOLVED": "plateText"}[reason]
            yield dict(identity, severity="WARNING", reasonCode=reason, field=field)


def export_report(ledger, directory):
    directory = output_path(directory)
    check(not directory.exists(), "Report destination already exists; choose a new output path",
          "OUTPUT_EXISTS")
    rows, facts, batches, fingerprint = ledger.snapshot()
    totals = Counter(row["outcome"] for row in rows)
    quality = list(quality_records(rows))
    summary = {
        "synthetic": True, "execution": "offline-sqlite", "versions": VERSIONS,
        "stateFingerprint": fingerprint, "batches": batches,
        "totalProcessingRows": len(rows), "finalFacts": len(facts),
        "totals": {key: totals[key] for key in ("ACCEPTED", "DUPLICATE", "REJECTED")},
        "qualityRecords": len(quality),
    }
    directory.parent.mkdir(parents=True, exist_ok=True)
    staging = directory.with_name(directory.name + ".pending-" + uuid.uuid4().hex)
    staging.mkdir()
    hashes = {}
    def write(name, content):
        data = content.encode("utf-8")
        (staging / name).write_bytes(data)
        hashes[name] = hashlib.sha256(data).hexdigest()
    for name, values in (("rows.jsonl", rows), ("captures.jsonl", facts), ("quality.jsonl", quality)):
        write(name, "".join(canonical(value) + "\n" for value in values))
    write("summary.json", canonical(summary) + "\n")
    write("export.json", canonical({
        "complete": True, "createdAt": datetime.now(timezone.utc).isoformat(),
        "stateFingerprint": fingerprint, "files": dict(hashes),
    }) + "\n")
    staging.rename(directory)
    return summary
