package com.xxplatform.api.service.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** 审批操作目录：登记范围、权限映射与票数规则。 */
class ApprovalOperationTests {

    @Test
    void catalogsEveryProposalInTheContract() {
        assertEquals(30, ApprovalOperation.values().length);
    }

    @Test
    void parsesOnlyRegisteredOperationIds() {
        assertEquals(Optional.of(ApprovalOperation.CREATE_EXPORT),
                ApprovalOperation.parse("createExport"));
        assertTrue(ApprovalOperation.parse("unknownOperation").isEmpty());
        assertTrue(ApprovalOperation.parse(null).isEmpty());
        assertTrue(ApprovalOperation.parse(" ").isEmpty());
    }

    @Test
    void everyOperationCarriesAPermission() {
        for (ApprovalOperation operation : ApprovalOperation.values()) {
            assertFalse(operation.permission().isBlank(), operation.operationId());
            assertTrue(operation.permission().contains(":"), operation.operationId());
        }
    }

    @Test
    void decryptionAlwaysNeedsTwoApprovals() {
        assertEquals(2, ApprovalOperation.READ_DECRYPTION_RESULT.requiredApprovals(0));
        assertEquals("capture:decrypt", ApprovalOperation.READ_DECRYPTION_RESULT.permission());
    }

    @Test
    void deviceBatchesNeedTwoApprovalsOnlyAtTenDevices() {
        assertEquals(1, ApprovalOperation.CREATE_DEVICE_COMMAND.requiredApprovals(9));
        assertEquals(2, ApprovalOperation.CREATE_DEVICE_COMMAND.requiredApprovals(10));
        assertEquals(2, ApprovalOperation.REVISE_DEVICE_COMMAND.requiredApprovals(200));
    }

    @Test
    void nonDeviceOperationsStayAtOneApprovalEvenWithManyItems() {
        assertEquals(1, ApprovalOperation.CREATE_EXPORT.requiredApprovals(500));
        assertEquals(1, ApprovalOperation.MERGE_PROFILES.requiredApprovals(500));
    }

    @Test
    void exportOperationsAllRequireCaptureExport() {
        assertEquals("capture:export", ApprovalOperation.CREATE_EXPORT.permission());
        assertEquals("capture:export", ApprovalOperation.EXPORT_ANALYSIS_RESULTS.permission());
        assertEquals("capture:export", ApprovalOperation.EXPORT_ANALYSIS_TASKS.permission());
    }
}
