-- V3__approval_aggregate_and_policy_v1.sql
-- 目的：把契约 contracts/database/schema.sql 在 2026-10-18（commit b2f6374，作者 Cling02，
-- 提交信息 "Sync provisional API decisions and development baseline"）追加的
-- PD-2026-10-18.v1 结构与种子，补成正式增量迁移。
--
-- 背景：V1__baseline_design_2.sql 是 2026-10-17 契约的逐字节快照（163 张表）。
-- 契约随后变成 165 张表 + 4 个新列 + 3 个索引 + 1 个触发器 + 12 行 feature_config 文案，
-- 但没有配套迁移：空库按 V1 建出来就比契约少两张审批表，审批接口会在运行时
-- 报 42P01（relation does not exist），而已有库因为 V1 checksum 未变不会报错（静默缺失）。
--
-- 内容来源：本条迁移的 DDL 段逐行取自 contracts/database/schema.sql 的
-- "PD-2026-10-18.v1" 追加块（该块本身以 ALTER 形式追加在基线之后），
-- 因此契约执行结果与 V1+V2+V3 执行结果一致；种子段同源。
--
-- 幂等性说明：不写 IF NOT EXISTS。Flyway 保证本条在同一个库上只执行一次；
-- 若某个开发库曾手工跑过 contracts/database/migrations/PD_20261018_approval.sql，
-- 需要先确认 to_regclass('operation.action_approval') 为空，否则请重建该库
-- （不要手工改 V3 的 DDL 绕过，那会让各环境结构悄悄分叉）。
--
-- 验证：空库依次执行 V1→V2→V3 后，information_schema/pg_indexes/pg_constraint/pg_trigger
-- 与直接执行 schema.sql 的结果逐项一致（见 apps/api/scripts/verify-migration-parity.ps1）。

BEGIN;

-- PD-2026-10-18.v1：暂定存储设计，尚待甲方确认；不含实际密钥/审批人员。
CREATE TABLE operation.action_approval (
  id uuid PRIMARY KEY,
  requester_user_id uuid NOT NULL REFERENCES auth.app_user(id),
  executor_user_id uuid NOT NULL REFERENCES auth.app_user(id),
  operation_id varchar(200) NOT NULL,
  policy_version varchar(200) NOT NULL,
  payload_digest char(64) NOT NULL CHECK (payload_digest ~ '^[a-f0-9]{64}$'),
  frozen_payload_ciphertext bytea NOT NULL CHECK (octet_length(frozen_payload_ciphertext)>0),
  encryption_key_id varchar(200) NOT NULL,
  summary varchar(2000) NOT NULL,
  status varchar(200) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED','CONSUMED')),
  required_approvals smallint NOT NULL CHECK (required_approvals IN (1,2)),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  execution_grant_expires_at timestamptz,
  consumed_at timestamptz,
  version bigint NOT NULL DEFAULT 1 CHECK (version>=1),
  CHECK (expires_at>created_at),
  CHECK (execution_grant_expires_at IS NULL OR execution_grant_expires_at<=expires_at),
  CHECK (status NOT IN ('APPROVED','CONSUMED') OR execution_grant_expires_at IS NOT NULL),
  CHECK ((status='CONSUMED')=(consumed_at IS NOT NULL))
);
CREATE INDEX ix_action_approval_requester ON operation.action_approval(requester_user_id,created_at,id);
CREATE INDEX ix_action_approval_pending ON operation.action_approval(status,expires_at,id);
COMMENT ON TABLE operation.action_approval IS 'PD-2026-10-18.v1暂定审批聚合；冻结快照认证加密，摘要不代替鉴权；APPROVED转换仅受控服务事务可做';
COMMENT ON COLUMN operation.action_approval.frozen_payload_ciphertext IS '加密封装含随机nonce/tag；不存密码、密钥或标识明文；部署secret注入key';

CREATE TABLE audit.action_approval_decision (
  id uuid PRIMARY KEY,
  approval_id uuid NOT NULL REFERENCES operation.action_approval(id),
  reviewer_user_id uuid NOT NULL REFERENCES auth.app_user(id),
  decision varchar(200) NOT NULL CHECK (decision IN ('APPROVE','REJECT')),
  reason varchar(1000) NOT NULL CHECK (char_length(reason)>=10),
  decided_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(approval_id,reviewer_user_id)
);
CREATE TRIGGER tr_action_approval_decision_append_only
  BEFORE UPDATE OR DELETE ON audit.action_approval_decision
  FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
REVOKE UPDATE,DELETE,TRUNCATE ON audit.action_approval_decision FROM PUBLIC;
COMMENT ON TABLE audit.action_approval_decision IS 'PD-2026-10-18.v1独立复核事实，仅追加；服务端排除申请人/执行人自批并重校权限，currentPassword不落库';

ALTER TABLE operation.decryption_request ADD COLUMN approval_id uuid
  UNIQUE REFERENCES operation.action_approval(id);
ALTER TABLE operation.decryption_result_grant ALTER COLUMN result_file_id DROP NOT NULL;
ALTER TABLE operation.decryption_result_grant ADD COLUMN approval_id uuid REFERENCES operation.action_approval(id);
ALTER TABLE operation.decryption_result_grant ADD COLUMN consumed_at timestamptz;
ALTER TABLE operation.decryption_result_grant ADD CONSTRAINT ck_decrypt_controlled_no_file
  CHECK (approval_id IS NULL OR result_file_id IS NULL);
CREATE UNIQUE INDEX uq_decrypt_request_approval ON operation.decryption_result_grant(request_id,authorized_user_id,approval_id);
COMMENT ON COLUMN operation.decryption_request.approval_id IS '同事务创建readDecryptionResult审批，执行人为recipient';
COMMENT ON COLUMN operation.decryption_result_grant.approval_id IS '新受控模式必须非空且绑定request/recipient，result_file_id必须为空；服务事务校验，不保存持久明文文件';
COMMENT ON COLUMN operation.decryption_result_grant.consumed_at IS '一次查看先同事务消费，HTTP断开不重放明文';


-- ---------------------------------------------------------------- 种子对齐
-- 契约把 12 条 feature_config 的 pending_reason / policy_version 从 "PENDING" 改为
-- PD-2026-10-18.v1 口径。用 INSERT ... ON CONFLICT 而不是 UPDATE，
-- 这样空库与已有库都收敛到同一结果（V1 已插过 12 行）。

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('FACE_SEARCH',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1')
ON CONFLICT (feature_code) DO UPDATE
  SET pending_reason = EXCLUDED.pending_reason,
      policy_version = EXCLUDED.policy_version;
INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('DECRYPTION',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1')
ON CONFLICT (feature_code) DO UPDATE
  SET pending_reason = EXCLUDED.pending_reason,
      policy_version = EXCLUDED.policy_version;
INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('COMPANION_ANALYSIS',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1')
ON CONFLICT (feature_code) DO UPDATE
  SET pending_reason = EXCLUDED.pending_reason,
      policy_version = EXCLUDED.policy_version;
INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('COLLISION_ANALYSIS',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1')
ON CONFLICT (feature_code) DO UPDATE
  SET pending_reason = EXCLUDED.pending_reason,
      policy_version = EXCLUDED.policy_version;
INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('REGION_STATISTICS',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1')
ON CONFLICT (feature_code) DO UPDATE
  SET pending_reason = EXCLUDED.pending_reason,
      policy_version = EXCLUDED.policy_version;
INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('PROFILE_ASSOCIATION',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1')
ON CONFLICT (feature_code) DO UPDATE
  SET pending_reason = EXCLUDED.pending_reason,
      policy_version = EXCLUDED.policy_version;
INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('RESIDENCE_MODEL',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1')
ON CONFLICT (feature_code) DO UPDATE
  SET pending_reason = EXCLUDED.pending_reason,
      policy_version = EXCLUDED.policy_version;
INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('WATCH_ACTIVATION',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1')
ON CONFLICT (feature_code) DO UPDATE
  SET pending_reason = EXCLUDED.pending_reason,
      policy_version = EXCLUDED.policy_version;
INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('NOTIFICATION_SEND',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1')
ON CONFLICT (feature_code) DO UPDATE
  SET pending_reason = EXCLUDED.pending_reason,
      policy_version = EXCLUDED.policy_version;
INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('DEVICE_COMMAND',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1')
ON CONFLICT (feature_code) DO UPDATE
  SET pending_reason = EXCLUDED.pending_reason,
      policy_version = EXCLUDED.policy_version;
INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('REPORT_GENERATION',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1')
ON CONFLICT (feature_code) DO UPDATE
  SET pending_reason = EXCLUDED.pending_reason,
      policy_version = EXCLUDED.policy_version;
INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('RAW_INGESTION',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1')
ON CONFLICT (feature_code) DO UPDATE
  SET pending_reason = EXCLUDED.pending_reason,
      policy_version = EXCLUDED.policy_version;

COMMIT;
