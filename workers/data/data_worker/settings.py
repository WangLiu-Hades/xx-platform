"""CLI configuration and immutable batch descriptions."""
from dataclasses import dataclass
from pathlib import Path
import os

from .common import WorkerError, check, digest, read_json, utc, uuid_text
from .plate import ADAPTER, VERSIONS, PlateAdapter

PACKAGE_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = PACKAGE_ROOT.parents[1]
OUTPUT_ROOT = Path(os.environ.get("D3_ARTIFACT_ROOT", PACKAGE_ROOT / ".artifacts")).resolve()
BATCH_FIELDS = ("batchId", "taskId", "name", "source", "mappingVersion", "dedupVersion",
                "schemaVersion", "mode", "originalBatchId", "inputFile", "receivedAt")


@dataclass
class Batch:
    metadata: dict
    input_path: Path

    @property
    def id(self):
        return self.metadata["batchId"]


@dataclass
class Settings:
    adapter: PlateAdapter
    fingerprint: str
    batches: list
    max_line_bytes: int
    state_path: Path
    output_root: Path


def inside(path, root, label):
    try:
        resolved = Path(path).resolve()
        resolved.relative_to(root.resolve())
    except (ValueError, OSError) as exc:
        raise WorkerError("PATH_OUTSIDE_ROOT", label + " must stay below " + str(root)) from exc
    check(resolved != root.resolve(), label + " must name a child path")
    return resolved


def output_path(path):
    return inside(path, OUTPUT_ROOT, "Offline output")


def load_settings(config_path):
    config_path = config_path.resolve()
    config = read_json(config_path)
    check(isinstance(config, dict) and config.get("synthetic") is True and
          config.get("adapter") == ADAPTER, "Only the explicit synthetic PLATE configuration is supported")
    for key in ("referenceData", "batchesFile", "statePath", "outputRoot"):
        check(isinstance(config.get(key), str) and bool(config[key]), "Missing path: " + key)
    limit = config.get("maxLineBytes", 65536)
    check(type(limit) is int and 128 <= limit <= 1048576, "maxLineBytes must be 128..1048576")
    refs = read_json((config_path.parent / config["referenceData"]).resolve())
    try:
        adapter = PlateAdapter(refs)
    except (TypeError, KeyError, ValueError) as exc:
        raise WorkerError("CONFIG_INVALID", "Malformed reference registry") from exc
    manifest_path = (config_path.parent / config["batchesFile"]).resolve()
    manifest = read_json(manifest_path)
    check(isinstance(manifest, list) and bool(manifest), "Batch manifest must be a nonempty list")
    batches, ids, names = [], set(), set()
    for entry in manifest:
        check(isinstance(entry, dict) and set(BATCH_FIELDS) <= set(entry), "Missing batch metadata")
        data = {key: entry[key] for key in BATCH_FIELDS}  # caseIds is test metadata, never execution input.
        check(uuid_text(data["batchId"]) and uuid_text(data["taskId"]), "Invalid batch/task UUID")
        check(isinstance(data["name"], str) and bool(data["name"].strip()), "Invalid batch name")
        check(data["batchId"] not in ids and data["name"] not in names, "Duplicate batch ID/name")
        check(isinstance(data["source"], str) and data["source"] in adapter.sources, "Unregistered source")
        for key in ("schemaVersion", "mappingVersion", "dedupVersion"):
            check(data[key] == VERSIONS[key], "Unsupported version: " + key, "VERSION_UNSUPPORTED")
        check(data["mode"] in ("INGEST", "REPLAY"), "Unsupported batch mode")
        original = data["originalBatchId"]
        check((data["mode"] == "INGEST" and original is None) or
              (data["mode"] == "REPLAY" and uuid_text(original) and original != data["batchId"]),
              "Invalid originalBatchId")
        try:
            data["receivedAt"] = utc(data["receivedAt"])
        except (ValueError, TypeError, OverflowError) as exc:
            raise WorkerError("CONFIG_INVALID", "Invalid receivedAt") from exc
        check(isinstance(data["inputFile"], str) and bool(data["inputFile"]), "Invalid input path")
        input_path = inside(manifest_path.parent / data["inputFile"], manifest_path.parent, "Batch input")
        check(input_path.is_file(), "Batch input file is missing")
        batches.append(Batch(data, input_path))
        ids.add(data["batchId"])
        names.add(data["name"])
    # Lock reference mappings and policy across restarts; changing mappings needs a separate state.
    fingerprint = digest({"storeVersion": 1, "adapter": ADAPTER, "versions": VERSIONS,
                          "references": refs, "maxLineBytes": limit})
    return Settings(adapter, fingerprint, batches, limit,
                    output_path(OUTPUT_ROOT / config["statePath"]),
                    output_path(OUTPUT_ROOT / config["outputRoot"]))
