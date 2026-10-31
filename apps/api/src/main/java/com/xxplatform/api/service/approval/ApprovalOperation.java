package com.xxplatform.api.service.approval;

import java.util.Arrays;
import java.util.Optional;

/**
 * 契约允许发起审批的 30 个操作（{@code ActionApprovalProposal} 的 discriminator 全集）。
 *
 * <p>审批**不接受任意未登记操作**：{@link #parse(String)} 对未知 operationId 返回空，
 * 服务层据此回 400，而不是落一条永远无法复核的申请。每个操作同时带两个策略信息：
 *
 * <ul>
 *   <li>{@code permission} —— 协议要求"审批人必须具备对应动作权限和范围"。申请人也按同一
 *       权限码校验：自己都做不了的动作，不应当先申请再让别人批。</li>
 *   <li>批准票数 —— 解密类固定 2 票；设备类批量操作（一次涉及 ≥10 台）2 票；其余 1 票。
 *       这条规则在 {@link #requiredApprovals(int)} 里集中表达，与协议"两名独立审批人
 *       （解密、批量 ≥10 台）"一致。</li>
 * </ul>
 *
 * <p>本枚举是**临时口径**：PD-2026-10-19.v1 标注"待甲方确认实际审批人/密钥/用途"，
 * 真正落地时应改为从配置或审批策略表读取，而不是继续硬编码。
 */
public enum ApprovalOperation {

    CREATE_DEVICE_COMMAND("createDeviceCommand", "下发设备命令", "device:command", true),
    UPDATE_DEVICE_PARAMETERS("updateDeviceParameters", "修改设备参数", "device:command", true),
    RETRY_DEVICE_COMMAND("retryDeviceCommand", "重试设备命令", "device:command", true),
    REVISE_DEVICE_COMMAND("reviseDeviceCommand", "修订设备命令", "device:command", true),
    SET_ROLE_PERMISSIONS("setRolePermissions", "设置角色权限", "role:grant", false),
    SET_ROLE_SCOPES("setRoleScopes", "设置角色数据范围", "role:grant", false),
    CREATE_USER("createUser", "创建用户", "user:write", false),
    UPDATE_USER("updateUser", "修改用户", "user:write", false),
    DELETE_USER("deleteUser", "删除用户", "user:write", false),
    DISABLE_USER("disableUser", "停用用户", "user:disable", false),
    ENABLE_USER("enableUser", "启用用户", "user:disable", false),
    CREATE_ROLE("createRole", "创建角色", "role:grant", false),
    UPDATE_ROLE("updateRole", "修改角色", "role:grant", false),
    DELETE_ROLE("deleteRole", "删除角色", "role:grant", false),
    CHANGE_USER_PASSWORD("changeUserPassword", "重置用户口令", "user:write", false),
    REPLAY_INGESTION_BATCH("replayIngestionBatch", "重放接入批次", "ingestion:replay", false),
    CREATE_INGESTION_BACKFILL("createIngestionBackfill", "创建接入补采", "ingestion:replay", false),
    MERGE_PROFILES("mergeProfiles", "合并档案", "profile:merge", false),
    SPLIT_PROFILE("splitProfile", "拆分档案", "profile:merge", false),
    CREATE_EXPORT("createExport", "创建导出", "capture:export", false),
    EXPORT_ANALYSIS_RESULTS("exportAnalysisResults", "导出研判结果", "capture:export", false),
    EXPORT_ANALYSIS_TASKS("exportAnalysisTasks", "导出研判任务", "capture:export", false),
    EXPORT_DATASET("exportDataset", "导出数据集", "dataset:export", false),
    EXPORT_PERSON_LIBRARY("exportPersonLibrary", "导出人员库", "person-library:export", false),
    EXPORT_PERSON_LIBRARIES("exportPersonLibraries", "导出人员库集合", "person-library:export", false),
    EXPORT_PROFILE("exportProfile", "导出档案", "profile:export", false),
    EXPORT_METRICS("exportMetrics", "导出指标", "dashboard:read", false),
    EXPORT_AUDIT_EVENTS("exportAuditEvents", "导出审计事件", "audit:export", false),
    EXPORT_SMS_LOGS("exportSmsLogs", "导出短信日志", "delivery:export", false),
    READ_DECRYPTION_RESULT("readDecryptionResult", "查看解密结果", "capture:decrypt", false);

    /** 批量设备操作达到这个数量就需要两名审批人（协议：批量 ≥10 台）。 */
    public static final int BATCH_APPROVAL_THRESHOLD = 10;

    private final String operationId;
    private final String label;
    private final String permission;
    private final boolean deviceBatch;

    ApprovalOperation(String operationId, String label, String permission, boolean deviceBatch) {
        this.operationId = operationId;
        this.label = label;
        this.permission = permission;
        this.deviceBatch = deviceBatch;
    }

    public String operationId() {
        return operationId;
    }

    public String label() {
        return label;
    }

    public String permission() {
        return permission;
    }

    /** 一次涉及多少台设备；只有设备类批量操作会用到。 */
    public boolean needsSecondApproval(int batchSize) {
        if (this == READ_DECRYPTION_RESULT) {
            return true;
        }
        return deviceBatch && batchSize >= BATCH_APPROVAL_THRESHOLD;
    }

    /** 需要的批准票数：1 或 2（与 {@code required_approvals IN (1,2)} 的库约束一致）。 */
    public int requiredApprovals(int batchSize) {
        return needsSecondApproval(batchSize) ? 2 : 1;
    }

    public static Optional<ApprovalOperation> parse(String operationId) {
        if (operationId == null || operationId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = operationId.trim();
        return Arrays.stream(values())
                .filter(op -> op.operationId.equals(trimmed))
                .findFirst();
    }
}
