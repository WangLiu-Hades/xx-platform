-- 仅用于已核验的DB-DESIGN-2升级；先备份并在隔离库验证，禁止重跑schema.sql。
-- 不自动处理旧解密许可：必须先检查、撤销与清理旧明文文件，再单独制定历史迁移。
BEGIN;
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM operation.decryption_result_grant) THEN
    RAISE EXCEPTION 'Existing decryption grants require a reviewed legacy migration';
  END IF;
END $$;
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

UPDATE core.feature_config SET policy_version='PD-2026-10-18.v1',pending_reason=CASE WHEN enabled THEN NULL ELSE '实现/真实资源未就绪；按暂定规则开发' END;
COMMIT;
