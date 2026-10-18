"""Pure validation and normalization for plate-synthetic-v0 (not a vendor adapter)."""
from dataclasses import dataclass
import math
import uuid

from .common import RowError, check, digest, strict_json, utc, uuid_text

ADAPTER = "synthetic-plate-v0"
VERSIONS = {"schemaVersion": "plate-synthetic-v0", "mappingVersion": "plate-map-v0",
            "dedupVersion": "plate-dedup-v0", "qualityVersion": "plate-quality-v0"}
REQUIRED = ("recordId", "dataType", "deviceKey", "captureTime", "plateText")
OPTIONAL = ("longitude", "latitude", "crs", "plateColor", "vehicleImageRef")
MASK = "SYNTH-PLATE-****"


@dataclass
class Normalized:
    key: str
    payload_digest: str
    source_event_id: str
    record: dict
    warnings: list


def parse_line(raw_bytes):
    try:
        text = raw_bytes.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise RowError("INPUT_UTF8_INVALID", "$") from exc
    try:
        value = strict_json(text)
    except (ValueError, UnicodeError, RecursionError) as exc:
        raise RowError("INPUT_JSON_INVALID", "$") from exc
    if not isinstance(value, dict):
        raise RowError("INPUT_OBJECT_REQUIRED", "$")
    return value


class PlateAdapter:
    def __init__(self, refs):
        check(isinstance(refs, dict) and refs.get("synthetic") is True,
              "Reference registry must be explicitly synthetic")
        check(type(refs.get("seed")) is int and uuid_text(refs.get("uuidNamespace")),
              "Invalid synthetic UUID recipe")
        self.seed = refs["seed"]
        self.namespace = uuid.UUID(refs["uuidNamespace"])
        for name in ("sources", "sites", "devices", "deviceMappings", "targets", "files"):
            check(isinstance(refs.get(name), list) and
                  all(isinstance(row, dict) for row in refs[name]), "Invalid reference list: " + name)
        self.sources = set()
        for row in refs["sources"]:
            source = row.get("source")
            check(isinstance(source, str) and 0 < len(source) <= 80 and source.strip() == source,
                  "Invalid synthetic source code")
            check(source not in self.sources and row.get("productionEnabled") is False
                  and row.get("adapter") == ADAPTER, "Duplicate source or unsupported adapter")
            self.sources.add(source)
        check(bool(self.sources), "At least one synthetic source is required")
        sites = set()
        for row in refs["sites"]:
            check(uuid_text(row.get("id")) and row["id"] not in sites, "Invalid or duplicate site")
            sites.add(row["id"])
        devices = {}
        for row in refs["devices"]:
            check(uuid_text(row.get("id")) and row["id"] not in devices and row.get("siteId") in sites,
                  "Invalid device/site reference")
            devices[row["id"]] = row["siteId"]
        self.devices = {}
        for row in refs["deviceMappings"]:
            check(row.get("source") in self.sources and isinstance(row.get("deviceKey"), str)
                  and bool(row["deviceKey"].strip()), "Invalid device mapping key")
            key = (row["source"], row["deviceKey"])
            check(key not in self.devices and row.get("deviceId") in devices
                  and devices[row["deviceId"]] == row.get("siteId"), "Invalid or duplicate device mapping")
            self.devices[key] = row
        self.targets = {}
        for row in refs["targets"]:
            check(isinstance(row.get("rawValue"), str) and bool(row["rawValue"].strip())
                  and row["rawValue"] not in self.targets and row.get("targetType") == "PLATE"
                  and uuid_text(row.get("targetId")), "Invalid or duplicate target reference")
            self.targets[row["rawValue"]] = row["targetId"]
        self.files = {}
        for row in refs["files"]:
            check(isinstance(row.get("sourceRef"), str) and bool(row["sourceRef"].strip())
                  and row["sourceRef"] not in self.files and uuid_text(row.get("fileAssetId")),
                  "Invalid or duplicate image reference")
            self.files[row["sourceRef"]] = row["fileAssetId"]

    def identity(self, label):
        return str(uuid.uuid5(self.namespace, str(self.seed) + ":" + label))

    def normalize(self, raw, source, received_at):
        check(source in self.sources, "Source is not registered")
        for field in REQUIRED:
            value = raw.get(field)
            if value is None or (isinstance(value, str) and not value.strip()):
                raise RowError("FIXTURE_REQUIRED", field)
        unknown = sorted(set(raw) - set(REQUIRED) - set(OPTIONAL))
        if unknown:
            raise RowError("FIXTURE_UNKNOWN_FIELD", unknown[0])
        if raw["dataType"] != "PLATE":
            raise RowError("FIXTURE_DATA_TYPE", "dataType")
        for field in ("recordId", "deviceKey", "plateText"):
            if not isinstance(raw[field], str):
                raise RowError("FIXTURE_FIELD_TYPE", field)
        if len(raw["recordId"]) > 200:
            raise RowError("FIXTURE_FIELD_TYPE", "recordId")
        try:
            capture_time = utc(raw["captureTime"])
        except (ValueError, TypeError, OverflowError) as exc:
            raise RowError("FIXTURE_TIME_INVALID", "captureTime") from exc
        lon, lat = raw.get("longitude"), raw.get("latitude")
        if ("longitude" in raw) != ("latitude" in raw) or (lon is None) != (lat is None):
            raise RowError("FIXTURE_COORDINATE_PAIR", "latitude" if lat is None else "longitude")
        if lon is not None:
            for field, number, bound in (("longitude", lon, 180), ("latitude", lat, 90)):
                # Compare bounds before isfinite, so huge integers cannot overflow float conversion.
                if type(number) not in (int, float) or not -bound <= number <= bound or not math.isfinite(number):
                    raise RowError("FIXTURE_COORDINATE_INVALID", field)
            if raw.get("crs") != "EPSG:4326":
                raise RowError("FIXTURE_CRS_UNSUPPORTED", "crs")
        if "vehicleImageRef" in raw:
            ref = raw["vehicleImageRef"]
            if not isinstance(ref, str) or ref not in self.files:
                raise RowError("FIXTURE_IMAGE_REFERENCE", "vehicleImageRef")
        if "plateColor" in raw and raw["plateColor"] is not None and not isinstance(raw["plateColor"], str):
            raise RowError("FIXTURE_FIELD_TYPE", "plateColor")

        device = self.devices.get((source, raw["deviceKey"]))
        target = self.targets.get(raw["plateText"])
        warnings = []
        if device is None:
            warnings.append("FIXTURE_DEVICE_UNMAPPED")
        if target is None:
            warnings.append("FIXTURE_TARGET_UNRESOLVED")
        plate = {"plateMasked": MASK}
        if "plateColor" in raw:
            plate["plateColor"] = raw["plateColor"]
        if "vehicleImageRef" in raw:
            plate["vehicleImageAssetId"] = self.files[raw["vehicleImageRef"]]
        record = {
            "captureId": self.identity("capture:" + source + ":" + raw["recordId"]),
            "dataType": "PLATE", "targetType": "PLATE", "targetId": target,
            "deviceId": device["deviceId"] if device else None,
            "siteId": device["siteId"] if device else None,
            "captureTime": capture_time, "ingestedAt": received_at,
            "longitude": lon, "latitude": lat, "source": source, "status": "VALID",
            "displayTarget": MASK, "isMasked": True, "plate": plate,
        }
        return Normalized(raw["recordId"], digest(raw),
                          self.identity("source-event:" + source + ":" + raw["recordId"]),
                          record, warnings)
