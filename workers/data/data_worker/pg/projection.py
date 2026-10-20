"""PLATE projection from platform tables, without storing raw identifiers in DTOs."""
from datetime import datetime, timezone
from ..common import utc
from ..plate import MASK

SELECT_FIELDS = """
e.id AS capture_id, e.data_type, e.primary_target_id, e.target_type,
e.device_id, e.site_id, e.capture_time, e.ingested_at,
ST_X(e.location) AS longitude, ST_Y(e.location) AS latitude,
s.source_code, e.status, p.plate_color,
CASE WHEN image.status='ACTIVE' THEN p.vehicle_image_asset_id END AS vehicle_image_asset_id,
s.id AS source_event_id, s.source_record_key, s.payload_digest, e.batch_id
"""
FROM_TABLES = """
FROM capture.capture_event e
JOIN capture.source_event s ON s.id=e.source_event_id
JOIN capture.plate_record p ON p.capture_id=e.id
LEFT JOIN core.file_asset image ON image.id=p.vehicle_image_asset_id
"""


def timestamp(value):
    if isinstance(value, datetime):
        if value.tzinfo is None:
            raise ValueError("Database returned a naive timestamp")
        return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
    return utc(value)


def identifier(value):
    return None if value is None else str(value)


def to_dto(row):
    plate = {"plateMasked": MASK}
    # The current SQL schema cannot distinguish omitted optional values from explicit null.
    if row["plate_color"] is not None:
        plate["plateColor"] = row["plate_color"]
    if row["vehicle_image_asset_id"] is not None:
        plate["vehicleImageAssetId"] = str(row["vehicle_image_asset_id"])
    return {
        "captureId": str(row["capture_id"]), "dataType": "PLATE", "targetType": "PLATE",
        "targetId": identifier(row["primary_target_id"]), "deviceId": identifier(row["device_id"]),
        "siteId": identifier(row["site_id"]), "captureTime": timestamp(row["capture_time"]),
        "ingestedAt": timestamp(row["ingested_at"]), "longitude": row["longitude"], "latitude": row["latitude"],
        "source": row["source_code"], "status": row["status"],
        "displayTarget": MASK, "isMasked": True, "plate": plate,
    }


def to_fact(row):
    return {"source": row["source_code"], "sourceRecordKey": row["source_record_key"],
            "sourceEventId": str(row["source_event_id"]), "firstBatchId": str(row["batch_id"]),
            "payloadDigest": row["payload_digest"], "record": to_dto(row)}
