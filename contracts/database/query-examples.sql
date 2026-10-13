-- PostgreSQL 14 / PostGIS 3.0-era functions. PREPARE only; no business mutation runs on loading.
-- Parameters are canonical server-validated values; client-provided scope is not authority.

PREPARE capture_cursor(text,uuid[],timestamptz,timestamptz,timestamptz,uuid,integer) AS
SELECT e.id,e.data_type,e.primary_target_id,e.capture_time,e.device_id,e.site_id,
       ST_X(e.location) AS longitude,ST_Y(e.location) AS latitude
FROM capture.capture_event e
WHERE e.status='VALID' AND e.data_type=$1 AND e.site_id=ANY($2)
  AND e.capture_time >= $3 AND e.capture_time < $4
  AND ($5 IS NULL OR (e.capture_time,e.id)<($5,$6))
ORDER BY e.capture_time DESC,e.id DESC LIMIT $7;

PREPARE target_captures(uuid,uuid[],timestamptz,timestamptz,integer) AS
WITH ids AS (
 SELECT id FROM capture.capture_event WHERE status='VALID' AND primary_target_id=$1
    AND site_id=ANY($2) AND capture_time >= $3 AND capture_time < $4
 UNION
 SELECT e.id FROM capture.capture_target t JOIN capture.capture_event e ON e.id=t.capture_id
 WHERE t.target_id=$1 AND e.status='VALID' AND e.site_id=ANY($2)
    AND e.capture_time >= $3 AND e.capture_time < $4
)
SELECT e.id,e.capture_time FROM ids JOIN capture.capture_event e USING(id)
ORDER BY e.capture_time DESC,e.id DESC LIMIT $5;

PREPARE map_bbox(double precision,double precision,double precision,double precision,uuid[],timestamptz,timestamptz,integer) AS
SELECT id,data_type,ST_AsGeoJSON(location)::json AS geometry
FROM capture.capture_event
WHERE status='VALID' AND location IS NOT NULL AND site_id=ANY($5)
  AND capture_time >= $6 AND capture_time < $7
  AND location && ST_MakeEnvelope($1,$2,$3,$4,4326)
ORDER BY id LIMIT $8;
-- The service enforces finite bbox/zoom/limit. For exact nonrectangular areas use ST_Intersects.
-- WGS84 distances in meters must use geography after the bounded bbox candidate filter.

PREPARE claim_task(text,integer,integer) AS
WITH picked AS (
 SELECT id FROM analysis.async_task
 WHERE status='QUEUED' AND deleted=false AND next_run_at<=now()
 ORDER BY priority,next_run_at,created_at,id
 FOR UPDATE SKIP LOCKED LIMIT $2
)
UPDATE analysis.async_task t
SET status='RUNNING',lease_owner=$1,lease_until=now()+make_interval(secs=>$3),
    lease_generation=t.lease_generation+1,current_attempt_no=t.current_attempt_no+1,
    started_at=COALESCE(t.started_at,now()),version=t.version+1
FROM picked WHERE t.id=picked.id
RETURNING t.id,t.current_attempt_no,t.lease_generation,t.lease_until;
-- PENDING input preparation transitions to QUEUED in a separate validated transaction.
-- Insert the task_attempt with returned generation in the same claim transaction.

PREPARE finish_task(uuid,text,bigint) AS
UPDATE analysis.async_task
SET status='SUCCESS',progress=100,finished_at=now(),lease_until=NULL,version=version+1
WHERE id=$1 AND status='RUNNING' AND lease_owner=$2 AND lease_generation=$3
 AND lease_until>now() AND cancel_requested_at IS NULL
RETURNING id;
-- In one transaction lock/verify the lease, publish the matching domain attempt, set attempt.result,
-- then complete the task. Zero rows aborts all publication; never publish before this fencing check.

PREPARE published_analysis(uuid,uuid,integer) AS
SELECT r.id,r.kind,r.target_id,r.data_type,r.capture_count,r.site_count,r.region_count
FROM analysis.analysis_job j
JOIN analysis.async_task t ON t.id=j.task_id AND t.status='SUCCESS'
JOIN analysis.analysis_result r ON r.analysis_job_id=j.id AND r.attempt_no=j.published_attempt_no
WHERE j.id=$1 AND j.deleted=false AND ($2 IS NULL OR r.id>$2)
ORDER BY r.id LIMIT $3;
-- Current user/site/type checks still apply to each result/evidence before serialization.

PREPARE dataset_at_version(uuid,bigint,uuid,integer) AS
SELECT id,element_type,capture_id,target_id,target_type,source_capture_id
FROM analysis.dataset_item
WHERE dataset_id=$1 AND valid_from_version<=$2
  AND (valid_to_version IS NULL OR valid_to_version>$2)
  AND ($3 IS NULL OR id>$3)
ORDER BY id LIMIT $4;

PREPARE dataset_intersection(uuid,bigint,uuid,bigint) AS
SELECT target_id FROM analysis.dataset_item
WHERE dataset_id=$1 AND element_type='TARGET' AND valid_from_version<=$2
 AND (valid_to_version IS NULL OR valid_to_version>$2)
INTERSECT
SELECT target_id FROM analysis.dataset_item
WHERE dataset_id=$3 AND element_type='TARGET' AND valid_from_version<=$4
 AND (valid_to_version IS NULL OR valid_to_version>$4);
-- UNION/EXCEPT use the same pinned-version predicates; CAPTURE compares capture_id.
-- Check compatibility and current data permissions before materializing a result dataset.

PREPARE audit_cursor(uuid,boolean,timestamptz,timestamptz,timestamptz,uuid,integer) AS
SELECT id,actor_login_name_snapshot,actor_display_name_snapshot,action,resource_type,
       resource_id,occurred_at,result,trace_id
FROM audit.audit_event
WHERE ($2 OR actor_user_id=$1) AND occurred_at >= $3 AND occurred_at < $4
 AND ($5 IS NULL OR (occurred_at,id)<($5,$6))
ORDER BY occurred_at DESC,id DESC LIMIT $7;
-- $2 is computed by server audit:read/admin policy, never accepted as a client privilege flag.

PREPARE sms_log_cursor(uuid,timestamptz,timestamptz,integer) AS
SELECT id,task_name_snapshot,content_summary,recipient_id,status,sent_at,
       error_code,error_reason,provider_receipt_ref,trace_id
FROM operation.delivery
WHERE channel_type='SMS' AND ($1 IS NULL OR recipient_id=$1)
 AND sent_at >= $2 AND sent_at < $3
ORDER BY sent_at DESC,id DESC LIMIT $4;
-- Add ownership/permissions predicate as required; resolve recipient search using versioned HMAC.

PREPARE metric_buckets(text,text,uuid,text,timestamptz,timestamptz) AS
SELECT s.bucket_start,s.bucket_end,s.value,s.numerator,s.denominator,s.as_of,s.status,
       d.unit,d.refresh_policy,s.error_code
FROM core.metric_snapshot s JOIN core.metric_definition d USING(metric_code,definition_version)
WHERE s.metric_code=$1 AND s.definition_version=$2 AND s.scope_id=$3 AND s.granularity=$4
 AND s.bucket_start >= $5 AND s.bucket_start < $6
ORDER BY s.bucket_start;
-- Nonadditive definitions must not be summed across buckets; source counters and ingestion differ.
