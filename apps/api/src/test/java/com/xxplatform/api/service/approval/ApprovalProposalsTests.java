package com.xxplatform.api.service.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;

/** 提案解析：键集合、摘要稳定性、复核清单脱敏。 */
class ApprovalProposalsTests {

    @Test
    void acceptsCreateExportProposalWithRequestBody() {
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(Map.of(
                "operationId", "createExport",
                "requestBody", Map.of("contextId", "00000000-0000-4000-8000-000000000001", "format", "XLSX")));

        assertEquals(ApprovalOperation.CREATE_EXPORT, proposal.operation());
        assertEquals(64, proposal.digestHex().length());
        assertTrue(proposal.digestHex().matches("[0-9a-f]{64}"));
        assertEquals(1, proposal.requiredApprovals());
        assertTrue(proposal.reviewItems().stream().anyMatch(item -> item.fieldCode().equals("request.contextId")));
    }

    @Test
    void digestIsStableAcrossKeyOrder() {
        Map<String, Object> first = Map.of("operationId", "createExport",
                "requestBody", Map.of("format", "XLSX", "contextId", "ctx-1"));
        Map<String, Object> second = Map.of("operationId", "createExport",
                "requestBody", Map.of("contextId", "ctx-1", "format", "XLSX"));

        assertEquals(ApprovalProposals.parse(first).digestHex(), ApprovalProposals.parse(second).digestHex());
    }

    @Test
    void digestChangesWhenContentChanges() {
        String a = ApprovalProposals.parse(Map.of("operationId", "createExport",
                "requestBody", Map.of("format", "XLSX"))).digestHex();
        String b = ApprovalProposals.parse(Map.of("operationId", "createExport",
                "requestBody", Map.of("format", "CSV"))).digestHex();
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
                "requestBody", Map.of("format", "XLSX"),
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
                "pathParameters", Map.of("requestId", "00000000-0000-4000-8000-000000000002")));

        assertEquals(2, proposal.requiredApprovals());
        assertEquals("path.requestId", proposal.reviewItems().get(0).fieldCode());
    }

    @Test
    void masksSensitiveValuesInReviewItems() {
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(Map.of(
                "operationId", "changeUserPassword",
                "requestBody", Map.of("userId", "u-1", "newPassword", "Secret123!")));

        String masked = proposal.reviewItems().stream()
                .filter(item -> item.fieldCode().equals("request.newPassword"))
                .findFirst().orElseThrow().displayValue();
        assertEquals("***", masked);
        assertTrue(proposal.reviewItems().stream().noneMatch(item -> item.displayValue().contains("Secret123!")));
    }

    @Test
    void expandsDeviceIdsIntoOneReviewItemPerDevice() {
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(Map.of(
                "operationId", "createDeviceCommand",
                "requestBody", Map.of("deviceIds", List.of("d1", "d2", "d3"))));

        long deviceItems = proposal.reviewItems().stream()
                .filter(item -> item.fieldCode().startsWith("request.deviceIds["))
                .count();
        assertEquals(3, deviceItems);
        assertEquals(1, proposal.requiredApprovals());
    }

    @Test
    void deviceBatchAtThresholdNeedsTwoApprovals() {
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(Map.of(
                "operationId", "createDeviceCommand",
                "requestBody", Map.of("deviceIds", List.of("d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "d10"))));

        assertEquals(2, proposal.requiredApprovals());
    }

    @Test
    void reviewItemsCanBeRebuiltFromCanonicalJson() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", "createExport");
        payload.put("requestBody", Map.of("format", "XLSX", "purpose", "盘点"));
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(payload);

        List<ApprovalProposals.ReviewItem> rebuilt = ApprovalProposals.reviewItems(proposal.canonicalJson());
        assertEquals(proposal.reviewItems().size(), rebuilt.size());
        assertEquals(proposal.reviewItems().get(0).displayValue(), rebuilt.get(0).displayValue());
    }
}
