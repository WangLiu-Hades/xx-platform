"""Explicit DEV-01/DEV-04 stand-ins for a fresh, marked, isolated test database."""
import struct
import zlib
import hashlib
from pathlib import Path

from data_worker.common import canonical, check, digest, read_json
from data_worker.pg.connection import require_lab
from data_worker.pg.ingest import frozen_input
from data_worker.plate import VERSIONS
from data_worker.settings import PACKAGE_ROOT, output_path, OUTPUT_ROOT

LAB_WORKER = "dev3-isolated-test-worker"
def png_chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)


PNG = (b"\x89PNG\r\n\x1a\n" +
       png_chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0)) +
       png_chunk(b"IDAT", zlib.compress(b"\x00\x00\x00\x00")) + png_chunk(b"IEND", b""))


def file_asset(db, adapter, assets, asset_id, name, content, category, mime):
    assets = output_path(assets)
    assets.mkdir(parents=True, exist_ok=True)
    path = assets / (asset_id + Path(name).suffix)
    if path.exists():
        check(path.read_bytes() == content, "Existing test asset has different content")
    else:
        path.write_bytes(content)
    sha = hashlib.sha256(content).hexdigest()
    existing = db.execute("SELECT sha256 FROM core.file_asset WHERE id=%s::uuid", (asset_id,)).fetchone()
    if existing:
        check(existing["sha256"] == sha, "Test file metadata mismatch")
        return asset_id
    db.execute("""
        INSERT INTO core.file_asset
            (id,storage_key,file_name,category,mime_type,byte_size,sha256,status,trace_id)
        VALUES(%s::uuid,%s,%s,%s,%s,%s,%s,'ACTIVE','dev3-synthetic-seed')
        """, (asset_id, path.relative_to(OUTPUT_ROOT).as_posix(), name, category, mime, len(content), sha))
    return asset_id


def seed_references(db, settings, assets):
    require_lab(db)
    refs = read_json(PACKAGE_ROOT / "fixtures/plate-v0/reference-data.json")
    adapter = settings.adapter
    owner = adapter.identity("lab-user")
    with db.transaction():
        db.execute("""
            INSERT INTO auth.app_user(id,user_code,login_name,display_name,password_hash,status)
            VALUES(%s::uuid,'D3_TEST','d3_test_disabled','Synthetic test owner','DISABLED_TEST_ACCOUNT','DISABLED')
            """, (owner,))
        db.execute("""
            INSERT INTO core.region(region_code,name,boundary_version)
            VALUES('D3_TEST_REGION','Synthetic region','synthetic-v0')
            """)
        for site in refs["sites"]:
            db.execute("""
                INSERT INTO core.site(id,site_code,name,region_code)
                VALUES(%s::uuid,%s,%s,'D3_TEST_REGION')
                """, (site["id"], "TEST-" + site["id"], site["label"]))
        for device in refs["devices"]:
            db.execute("""
                INSERT INTO core.device(id,serial_number,name,site_id,category_code)
                VALUES(%s::uuid,%s,'Synthetic device',%s::uuid,'TEST_PLATE')
                """, (device["id"], "TEST-" + device["id"], device["siteId"]))
        for source in refs["sources"]:
            db.execute("""
                INSERT INTO capture.ingestion_source(source_code,name,source_kind,adapter_code,protocol_version,enabled)
                VALUES(%s,%s,'TEST_FILE','synthetic-plate-v0',%s,true)
                """, (source["source"], "Synthetic " + source["source"], VERSIONS["schemaVersion"]))
            db.execute("""
                INSERT INTO capture.source_mapping_version
                    (source_code,mapping_version,schema_version,mapping_definition,dedup_rule_version,effective_at,enabled)
                VALUES(%s,%s,%s,%s::jsonb,%s,clock_timestamp(),true)
                """, (source["source"], VERSIONS["mappingVersion"], VERSIONS["schemaVersion"],
                      canonical({"synthetic": True, "fingerprint": settings.fingerprint}), VERSIONS["dedupVersion"]))
        for target in refs["targets"]:
            db.execute("""
                INSERT INTO capture.target(id,target_type,normalization_version)
                VALUES(%s::uuid,'PLATE','synthetic-plate-v0')
                """, (target["targetId"],))
        for asset in refs["files"]:
            file_asset(db, adapter, assets, asset["fileAssetId"], "synthetic-image.png",
                       PNG, "TEST_IMAGE", "image/png")
    return owner


def seed_batch(db, settings, batch, assets, owner, lease_seconds=1800):
    require_lab(db)
    adapter = settings.adapter
    snapshot = frozen_input(settings, batch)
    raw_asset = adapter.identity("raw-file:" + batch.metadata["inputFile"])
    with db.transaction():
        file_asset(db, adapter, assets, raw_asset, batch.input_path.name, batch.input_path.read_bytes(),
                   "RAW_INGESTION", "application/x-ndjson")
        db.execute("""
            INSERT INTO analysis.async_task
                (id,name,task_type,owner_user_id,status,parameter_schema_version,parameter_snapshot,parameter_hash,
                 idempotency_key,lease_owner,lease_until,lease_generation,current_attempt_no,started_at,trace_id)
            VALUES(%s::uuid,%s,'INGESTION',%s::uuid,'RUNNING','synthetic-ingestion-v0',%s::jsonb,%s,%s,%s,
                   clock_timestamp()+make_interval(secs=>%s),1,1,clock_timestamp(),%s)
            """, (batch.metadata["taskId"], batch.metadata["name"], owner, canonical(snapshot), digest(snapshot),
                  batch.id, LAB_WORKER, lease_seconds, "d3-lab:" + batch.id))
        db.execute("""
            INSERT INTO analysis.task_attempt(task_id,attempt_no,started_at,worker_id,lease_generation)
            VALUES(%s::uuid,1,clock_timestamp(),%s,1)
            """, (batch.metadata["taskId"], LAB_WORKER))
        db.execute("""
            INSERT INTO capture.ingestion_batch
                (id,task_id,source_code,source_file_id,mapping_version,dedup_rule_version,mode,
                 original_batch_id,status,reason,trace_id)
            VALUES(%s::uuid,%s::uuid,%s,%s::uuid,%s,%s,%s,%s::uuid,'RUNNING','Synthetic integration test',%s)
            """, (batch.id, batch.metadata["taskId"], batch.metadata["source"], raw_asset,
                  batch.metadata["mappingVersion"], batch.metadata["dedupVersion"], batch.metadata["mode"],
                  batch.metadata["originalBatchId"], "d3-lab:" + batch.id))


def publish_for_lab(db, lease, batch):
    """Test stand-in ONLY. DEV-01 must supply the reviewed publisher for real task integration."""
    require_lab(db)
    task = db.execute("""
        UPDATE analysis.async_task
        SET status='SUCCESS',progress=100,finished_at=clock_timestamp(),lease_until=NULL,
            resource_type='INGESTION_BATCH',resource_id=%s::uuid,version=version+1
        WHERE id=%s::uuid AND status='RUNNING' AND lease_owner=%s
          AND current_attempt_no=%s AND lease_generation=%s AND lease_until>clock_timestamp()
          AND cancel_requested_at IS NULL AND NOT deleted
        RETURNING id
        """, (batch.id, lease.task_id, lease.worker_id, lease.attempt_no, lease.generation)).fetchone()
    if task is None:
        return False
    attempt = db.execute("""
        UPDATE analysis.task_attempt SET result='SUCCESS',finished_at=clock_timestamp()
        WHERE task_id=%s::uuid AND attempt_no=%s AND worker_id=%s AND lease_generation=%s AND result IS NULL
        RETURNING task_id
        """, (lease.task_id, lease.attempt_no, lease.worker_id, lease.generation)).fetchone()
    return attempt is not None
