package com.xxplatform.api.service.ingestion;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.TaskStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/** DEV-02 domain ports for integration; trusted shared implementations are pending. */
public final class IngestionBatchReplayService {
    public static final String OPERATION_ID = "replayIngestionBatch";
    private final ReplayAccess access;
    private final ReplayGateway gateway;

    public IngestionBatchReplayService(ReplayAccess access, ReplayGateway gateway) {
        this.access = Objects.requireNonNull(access);
        this.gateway = Objects.requireNonNull(gateway);
    }

    public ReplayReceipt replay(UUID batchId, String csrfToken, String idempotencyKey,
                                String approvalHeader, ReplayCommand command) {
        if (batchId == null) throw invalid("batchId", "batchId is required");
        requireText(csrfToken, "xCSRFToken");
        if (csrfToken.length() < 16) throw invalid("xCSRFToken", "Token must contain at least 16 characters");
        requireText(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.length() < 16 || idempotencyKey.length() > 160) {
            throw invalid("idempotencyKey", "Key must contain between 16 and 160 characters");
        }
        if (command == null) throw invalid("body", "Request body is required");
        requireText(command.reason(), "reason");
        if (command.reason().length() < 10) throw invalid("reason", "reason must contain at least 10 characters");
        if (command.mappingVersion() != null) requireText(command.mappingVersion(), "mappingVersion");
        if (command.dedupVersion() != null) requireText(command.dedupVersion(), "dedupVersion");
        UUID approvalId = approvalId(approvalHeader);

        // Length and UUID checks do not authorize an action or approve a replay.
        // Resolve actual identity and current source-batch visibility on every call.
        ReplayActor actor = Objects.requireNonNull(access.requireReplayAccess(csrfToken, batchId));
        return Objects.requireNonNull(gateway.createOrReplay(
                actor, OPERATION_ID, batchId, idempotencyKey, approvalId, command),
                "Replay gateway returned no persisted receipt");
    }

    private static UUID approvalId(String value) {
        if (value == null) return null;
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return parsed;
        } catch (IllegalArgumentException error) {
            throw invalid("approvalId", "X-Approval-Id must be a canonical UUID");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field, field + " is required");
    }

    private static DataApiException invalid(String field, String message) {
        return new DataApiException(ErrorCode.VALIDATION_ERROR, field, message);
    }

    /**
     * Trusted adapter uses current SID, bound CSRF and trusted Origin; requires
     * ingestion:replay and fresh source/site scope. Missing/deleted/invisible
     * source batches share DATA_NOT_FOUND. Client identity never grants access.
     */
    @FunctionalInterface
    public interface ReplayAccess {
        ReplayActor requireReplayAccess(String csrfToken, UUID sourceBatchId);
    }

    /**
     * Integration requirements, not an implemented shared API:
     * - Scope durable idempotency to actor+operation+key, with source batch and
     *   typed body in its canonical digest; recheck current access on every replay.
     *   Reject conflicting/tombstoned keys; reconcile unknown commit outcomes.
     * - Recheck the source batch, retained source/file, approved source profile,
     *   mapping/dedup versions and current 12-calendar-month policy under a
     *   consistent transaction. Omitted versions must be resolved from the source
     *   and frozen, never guessed. Do not restore expired data.
     * - P-005 requires independent approval. Apply current P-005/P-019 policy,
     *   verify matching operation/path/typed payload/resource versions and consume
     *   approval atomically for a new action. Header absence is not an exemption;
     *   replaying an already committed receipt must not consume approval again.
     * - Atomically create a NEW batch with replayOfBatchId/replayGeneration,
     *   shared task, idempotency receipt and approval consumption. Never overwrite
     *   the original batch or raw capture facts. Preserve source-event identity
     *   and avoid duplicate insertion/counting when the Worker publishes results.
     * - Return the shared TaskCreated receipt only after durable commit. No task
     *   state machine, database writes or approval cache is implemented here.
     */
    @FunctionalInterface
    public interface ReplayGateway {
        ReplayReceipt createOrReplay(ReplayActor actor, String operationId, UUID sourceBatchId,
                String idempotencyKey, UUID approvalId, ReplayCommand command);
    }

    public record ReplayActor(UUID principalId, String scopeVersion) {
        public ReplayActor {
            Objects.requireNonNull(principalId);
            Objects.requireNonNull(scopeVersion);
        }
    }

    public record ReplayCommand(String reason, String mappingVersion, String dedupVersion) {}

    public record ReplayReceipt(UUID taskId, TaskStatus status, OffsetDateTime createdAt, UUID resourceId) {
        public ReplayReceipt(UUID taskId, TaskStatus status, OffsetDateTime createdAt) {
            this(taskId, status, createdAt, null);
        }
        public String getResourceType() { return resourceId == null ? null : "INGESTION"; }
        public ReplayReceipt {
            Objects.requireNonNull(taskId);
            Objects.requireNonNull(status);
            createdAt = Objects.requireNonNull(createdAt).withOffsetSameInstant(ZoneOffset.UTC);
        }
    }

    private static DataApiException unavailableProblem() {
        return new DataApiException(ErrorCode.SYSTEM_UNAVAILABLE, null,
                "Shared replay authorization, independent approval and atomic replay task creation are not connected");
    }

    public static IngestionBatchReplayService unavailable() {
        return new IngestionBatchReplayService(
                (csrf, batchId) -> { throw unavailableProblem(); },
                (actor, operation, batchId, key, approvalId, command) -> { throw unavailableProblem(); });
    }
}
