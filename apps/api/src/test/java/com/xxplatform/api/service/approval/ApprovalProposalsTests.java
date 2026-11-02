package com.xxplatform.api.service.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;

/** 提案解析：键集合、摘要稳定性、严格校验、复核清单脱敏与预算。 */
class ApprovalProposalsTests {

    private static final String CONTEXT_ID = "00000000-0000-4000-8000-000000000001";
    private static final String PROFILE_ID = "00000000-0000-4000-8000-000000000010";
    private static final String REQUEST_ID = "00000000-0000-4000-8000-000000000002";
    private static final String USER_ID = "00000000-0000-4000-8000-000000000020";

    /** 生成一个形状合法的 UUID 字符串，供批量设备列表使用。 */
    private static String uuid(int suffix) {
        return String.format("00000000-0000-4000-8000-%012d", suffix);
    }

    @Test
    void acceptsCreateExportProposalWithRequestBody() {
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(Map.of(
                "operationId", "createExport",
                "requestBody", Map.of("contextId", CONTEXT_ID, "format", "XLSX")));

        assertEquals(ApprovalOperation.CREATE_EXPORT, proposal.operation());
        assertEquals(64, proposal.digestHex().length());
        assertTrue(proposal.digestHex().matches("[0-9a-f]{64}"));
        assertEquals(1, proposal.requiredApprovals());
        assertTrue(proposal.reviewItems().stream().anyMatch(item -> item.fieldCode().equals("request.contextId")));
    }

    @Test
    void digestIsStableAcrossKeyOrder() {
        Map<String, Object> first = Map.of("operationId", "createExport",
                "requestBody", Map.of("format", "XLSX", "contextId", CONTEXT_ID));
        Map<String, Object> second = Map.of("operationId", "createExport",
                "requestBody", Map.of("contextId", CONTEXT_ID, "format", "XLSX"));

        assertEquals(ApprovalProposals.parse(first).digestHex(), ApprovalProposals.parse(second).digestHex());
    }

    @Test
    void digestChangesWhenContentChanges() {
        String a = ApprovalProposals.parse(Map.of("operationId", "createExport",
                "requestBody", Map.of("contextId", CONTEXT_ID, "format", "XLSX"))).digestHex();
        String b = ApprovalProposals.parse(Map.of("operationId", "createExport",
                "requestBody", Map.of("contextId", CONTEXT_ID, "format", "CSV"))).digestHex();
        assertTrue(!a.equals(b));
    }

    @Test
    void rejectsUnknownOperation() {
        ApiException error = assertThrows(ApiException.class,
                () -> ApprovalProposals.parse(Map.of("operationId", "dropDatabase")));
        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertEquals("proposal", error.details().get(0).field());
    }

    @Test
    void rejectsUnknownKeys() {
        assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of(
                "operationId", "createExport",
                "requestBody", Map.of("contextId", CONTEXT_ID, "format", "XLSX"),
                "extra", true)));
    }

    @Test
    void rejectsEmptyPayload() {
        assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of("operationId", "createExport")));
        assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of()));
    }

    @Test
    void pathParametersProposalWorksForDecryptionReview() {
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(Map.of(
                "operationId", "readDecryptionResult",
                "pathParameters", Map.of("requestId", REQUEST_ID)));

        assertEquals(2, proposal.requiredApprovals());
        assertEquals("path.requestId", proposal.reviewItems().get(0).fieldCode());
    }

    @Test
    void masksSensitiveValuesInReviewItems() {
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(Map.of(
                "operationId", "changeUserPassword",
                "pathParameters", Map.of("userId", USER_ID),
                "requestBody", Map.of("reason", "Administrator reset with documented purpose", "newPassword", "Secret123!Long15")));

        String masked = proposal.reviewItems().stream()
                .filter(item -> item.fieldCode().equals("request.newPassword"))
                .findFirst().orElseThrow().displayValue();
        assertEquals("***", masked);
        assertTrue(proposal.reviewItems().stream()
                .noneMatch(item -> item.displayValue().contains("Secret123!Long15")
                        || item.displayValue().contains("OldSecret1!Long15")));
    }

    @Test
    void rejectsPasswordsThatThePasswordPolicyWouldRejectAtExecutionTime() {
        // M04：口令 15-128 个 Unicode 码点。提案层若接受 8-14 位口令，审批人批准的就是一个
        // 执行阶段必然失败的动作，等于把校验成本转嫁给审批人。
        Map<String, Object> tooShort = userBody("Short12!");
        ApiException shortError = assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of(
                "operationId", "createUser", "requestBody", tooShort)));
        assertEquals(ErrorCode.VALIDATION_ERROR, shortError.code());

        Map<String, Object> compliant = userBody("Compliant123!15");
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(Map.of(
                "operationId", "createUser", "requestBody", compliant));
        assertEquals(ApprovalOperation.CREATE_USER, proposal.operation());
        // 口令仍然是敏感值：复核清单里不能出现明文。
        assertTrue(proposal.reviewItems().stream()
                .filter(item -> item.fieldCode().equals("request.initialPassword"))
                .allMatch(item -> "***".equals(item.displayValue())));
    }

    @Test
    void masksSensitiveValuesNestedInSerializedJsonStrings() {
        // M11：真实载荷里常见"把一段 JSON 当字符串塞进字段"的形态；
        // 如果按标量原样展示，里面的 password 就是明文泄露。
        String embedded = "{\"password\":\"Hidden1!\",\"deviceId\":\"" + uuid(7) + "\"}";
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(Map.of(
                "operationId", "createDeviceCommand",
                "requestBody", commandBody(List.of(uuid(1)), List.of(Map.of("parameterCode", "raw", "valueType", "TEXT", "textValue", embedded)))));

        assertTrue(proposal.reviewItems().stream()
                        .noneMatch(item -> item.displayValue().contains("Hidden1!")),
                "序列化 JSON 字符串里的密码必须被打码");
        assertTrue(proposal.reviewItems().stream()
                .anyMatch(item -> item.fieldCode().endsWith("textValue.password")));
    }

    @Test
    void expandsDeviceIdsIntoOneReviewItemPerDevice() {
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(Map.of(
                "operationId", "createDeviceCommand",
                "requestBody", commandBody(List.of(uuid(1), uuid(2), uuid(3)), List.of())));

        long deviceItems = proposal.reviewItems().stream()
                .filter(item -> item.fieldCode().startsWith("request.deviceIds["))
                .count();
        assertEquals(3, deviceItems);
        assertEquals(1, proposal.requiredApprovals());
    }

    @Test
    void deviceBatchAtThresholdNeedsTwoApprovals() {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ids.add(uuid(i));
        }
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(Map.of(
                "operationId", "createDeviceCommand",
                "requestBody", commandBody(ids, List.of())));

        assertEquals(2, proposal.requiredApprovals());
    }

    @Test
    void reviewItemsCanBeRebuiltFromCanonicalJson() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", "createExport");
        payload.put("requestBody", Map.of("contextId", CONTEXT_ID, "format", "XLSX",
                "purpose", "盘点导出核查用途说明"));
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(payload);

        List<ApprovalProposals.ReviewItem> rebuilt = ApprovalProposals.reviewItems(proposal.canonicalJson());
        assertEquals(proposal.reviewItems().size(), rebuilt.size());
        assertEquals(proposal.reviewItems().get(0).displayValue(), rebuilt.get(0).displayValue());
    }

    @Test
    void everyApprovalOperationHasAProposalSpec() {
        // 漂移防护：ApprovalOperation 新增操作却忘了在 ApprovalProposals 里加字段白名单时，
        // 该操作会以"未知的 operationId"永远无法提交审批——这条断言把它变成编译/测试期错误。
        java.util.Set<String> declared = new java.util.HashSet<>();
        for (ApprovalOperation operation : ApprovalOperation.values()) {
            declared.add(operation.operationId());
        }
        assertEquals(declared, ApprovalProposals.supportedOperationIds());
    }

    @Test
    void rejectsRequestBodyKeysOutsideTheOperationSchema() {
        // M10：createExport 没有 userId 这个字段；"多带一个键"不能被静默忽略。
        ApiException error = assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of(
                "operationId", "createExport",
                "requestBody", Map.of("format", "XLSX", "contextId", CONTEXT_ID, "userId", "u-1"))));
        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertTrue(error.getMessage().contains("userId"));
    }

    @Test
    void rejectsPathParametersThatDoNotMatchThePathTemplate() {
        ApiException error = assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of(
                "operationId", "readDecryptionResult",
                "pathParameters", Map.of("requestId", REQUEST_ID, "tenantId", uuid(9)))));
        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertTrue(error.getMessage().contains("tenantId"));
    }

    @Test
    void rejectsInvalidBusinessBodyEvenWhenKeysAreAllowed() {
        // M10：键名合法但值不合法（format 拼错、contextId 不是 uuid）同样不得生成"已核验"申请。
        ApiException badFormat = assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of(
                "operationId", "createExport",
                "requestBody", Map.of("contextId", CONTEXT_ID, "format", "PDF"))));
        assertEquals(ErrorCode.VALIDATION_ERROR, badFormat.code());

        ApiException badContext = assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of(
                "operationId", "createExport",
                "requestBody", Map.of("contextId", "ctx-1", "format", "XLSX"))));
        assertEquals(ErrorCode.VALIDATION_ERROR, badContext.code());
    }

    @Test
    void updateDeviceParametersRequiresIfMatch() {
        // 契约把 If-Match 列为 updateDeviceParameters 的必填项：缺了就冻结不了"批的是哪个版本"。
        ApiException error = assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of(
                "operationId", "updateDeviceParameters",
                "pathParameters", Map.of("deviceId", uuid(3)),
                "requestBody", Map.of("changes", List.of(Map.of("parameterCode", "powerClass", "valueType", "TEXT", "textValue", "P2"))))));
        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertTrue(error.getMessage().contains("ifMatch"));
    }

    @Test
    void ifMatchIsBoundIntoTheDigest() {
        Map<String, Object> without = Map.of("operationId", "updateDeviceParameters",
                "pathParameters", Map.of("deviceId", uuid(3)), "ifMatch", "\"6\"",
                "requestBody", Map.of("changes", List.of(Map.of("parameterCode", "powerClass", "valueType", "TEXT", "textValue", "P2")), "reason", "Synthetic parameter change"));
        Map<String, Object> with = new LinkedHashMap<>(without);
        with.put("ifMatch", "\"7\"");

        ApprovalProposals.Proposal first = ApprovalProposals.parse(without);
        ApprovalProposals.Proposal second = ApprovalProposals.parse(with);
        assertTrue(!first.digestHex().equals(second.digestHex()));
        assertTrue(second.canonicalJson().contains("ifMatch"));
    }

    @Test
    void rejectsIfMatchOfWrongShape() {
        assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of(
                "operationId", "exportProfile",
                "pathParameters", Map.of("profileId", PROFILE_ID),
                "ifMatch", Map.of("version", 7))));
    }

    @Test
    void rejectsDisplayValuesBeyondThePerItemBudget() {
        // M11：旧实现把 9000 字符切成 3 段继续展示；协议要求"超预算就拆分申请/拒绝"，不允许
        // 用"拆段"把超长内容塞进同一个审批里。
        String longValue = "x".repeat(9000);
        ApiException error = assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of(
                "operationId", "createExport",
                "requestBody", Map.of("format", "CSV", "contextId", CONTEXT_ID, "purpose", longValue))));
        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertTrue(error.getMessage().contains("4096"), error.getMessage());
        assertTrue(error.getMessage().contains("拆分申请"), error.getMessage());
    }

    @Test
    void rejectsProposalsWhoseReviewItemsExceedTheCap() {
        // 10000 台设备 + 1 个 name = 10001 条复核项；截断会让审批人以为"就这些"，所以必须整体拒绝。
        List<String> deviceIds = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            deviceIds.add(uuid(i));
        }
        ApiException error = assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of(
                "operationId", "createDeviceCommand",
                "requestBody", commandBody(deviceIds, List.of()))));
        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertTrue(error.getMessage().startsWith("审批展示项"), error.getMessage());
    }

    private static Map<String, Object> commandBody(List<String> devices, List<?> changes) {
        return Map.of("commandType", "APPLY_PARAMETERS", "deviceIds", devices,
                "parameterChanges", changes, "reason", "Synthetic parameter update", "name", "Synthetic device command");
    }

    private static Map<String, Object> userBody(String password) {
        return Map.of("userCode", "u-ok", "loginName", "u-ok", "displayName", "Synthetic user",
                "initialPassword", password, "roleIds", List.of(uuid(3)),
                "siteScope", Map.of("allSites", false, "siteIds", List.of()),
                "dataScope", Map.of("allSites", false, "siteIds", List.of(), "dataTypes", List.of(), "ownOnly", true, "scopeVersion", 0));
    }

    @Test
    void roleProposalCannotOmitAnyRequiredInputField() {
        Map<String, Object> role = Map.of("roleCode", "SYNTHETIC", "name", "Synthetic role", "permissionCodes", List.of("dashboard:read"),
                "menuIds", List.of(), "siteScope", Map.of("allSites", false, "siteIds", List.of()),
                "dataScope", Map.of("allSites", false, "siteIds", List.of(), "dataTypes", List.of(), "ownOnly", false, "scopeVersion", 0));
        assertEquals(ApprovalOperation.CREATE_ROLE, ApprovalProposals.parse(Map.of("operationId", "createRole", "requestBody", role)).operation());
        for (String field : role.keySet()) {
            Map<String, Object> missing = new LinkedHashMap<>(role);
            missing.remove(field);
            assertThrows(ApiException.class, () -> ApprovalProposals.parse(Map.of("operationId", "createRole", "requestBody", missing)), field);
        }
    }

    @Test
    void adminPasswordResetUsesReasonAndNewPasswordWithoutCurrentPassword() {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("newPassword", "SyntheticLongNewPassword!2026", "reason", "Synthetic administrative reset"));
        Map<String, Object> request = Map.of("operationId", "changeUserPassword", "pathParameters", Map.of("userId", USER_ID), "requestBody", body);
        assertEquals(ApprovalOperation.CHANGE_USER_PASSWORD, ApprovalProposals.parse(request).operation());
        body.put("currentPassword", "MustNotBeRequiredOrAccepted");
        assertThrows(ApiException.class, () -> ApprovalProposals.parse(request));
    }

    @Test
    void parameterChangesAreTypedArraysAndIfMatchIsAQuotedVersion() {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("changes", List.of(Map.of("parameterCode", "powerClass", "valueType", "TEXT", "textValue", "P2")),
                "reason", "Synthetic legal parameter update"));
        Map<String, Object> request = new LinkedHashMap<>(Map.of("operationId", "updateDeviceParameters", "pathParameters", Map.of("deviceId", uuid(3)),
                "requestBody", body, "ifMatch", "\"0\""));
        assertEquals(ApprovalOperation.UPDATE_DEVICE_PARAMETERS, ApprovalProposals.parse(request).operation());
        request.put("ifMatch", "0");
        assertThrows(ApiException.class, () -> ApprovalProposals.parse(request));
        request.put("ifMatch", "\"0\"");
        body.put("changes", Map.of("powerClass", "P2"));
        assertThrows(ApiException.class, () -> ApprovalProposals.parse(request));
    }
}
