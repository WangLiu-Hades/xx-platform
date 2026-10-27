package com.xxplatform.api.service.ingestion;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.TaskStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/** DEV-02 domain orchestration; shared implementations are pending. */
public final class IngestionBackfillService {
    public static final String OPERATION_ID = "createIngestionBackfill";
    private final BackfillAccess access;
    private final BackfillGateway gateway;
    public IngestionBackfillService(BackfillAccess access, BackfillGateway gateway) {
        this.access = Objects.requireNonNull(access);
        this.gateway = Objects.requireNonNull(gateway);
    }

    public BackfillReceipt create(String csrf, String key, String approval, BackfillCommand command) {
        text(csrf, "xCSRFToken");
        if (csrf.length() < 16) throw invalid("xCSRFToken", "Token must contain at least 16 characters");
        text(key, "idempotencyKey");
        if (key.length() < 16 || key.length() > 160) throw invalid("idempotencyKey", "Key must contain 16 to 160 characters");
        if (command == null) throw invalid("body", "Request body is required");
        text(command.source(), "source");
        text(command.reason(), "reason");
        if (command.reason().length() < 10) throw invalid("reason", "reason must contain at least 10 characters");
        if (command.from() == null || command.to() == null) throw invalid("timeRange", "Both time bounds are required");
        if (!command.from().isBefore(command.to())) throw invalid("timeRange.to", "to must be later than from");
        UUID approvalId = null;
        if (approval != null) {
            try {
                approvalId = UUID.fromString(approval);
                if (!approvalId.toString().equalsIgnoreCase(approval)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException error) { throw invalid("approvalId", "X-Approval-Id must be a UUID"); }
        }
        var actor = Objects.requireNonNull(access.requireBackfillAccess(csrf, command.source()));
        return Objects.requireNonNull(gateway.createOrReplay(actor, OPERATION_ID, key, approvalId,
                new BackfillCommand(command.source(), command.from().withOffsetSameInstant(ZoneOffset.UTC),
                        command.to().withOffsetSameInstant(ZoneOffset.UTC), command.reason())));
    }

    private static void text(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field, field + " is required");
    }
    private static DataApiException invalid(String field, String message) {
        return new DataApiException(ErrorCode.VALIDATION_ERROR, field, message);
    }

    /** Current SID, bound CSRF/Origin, ingestion:replay and fresh source/site tuples. */
    @FunctionalInterface public interface BackfillAccess {
        BackfillActor requireBackfillAccess(String csrf, String source);
    }
    /**
     * Trusted adapter requirements: validate real SourceProfile availability and
     * the current 12-calendar-month retention policy, freeze source/time/versions,
     * validate P-005 independent approval and matching P-019 typed proposal.
     * Atomically consume approval and create shared task plus durable idempotency
     * receipt. Scope keys to actor+operation and canonical input; reauthorize
     * receipt replays without consuming approval twice; reject tombstones and
     * conflicting inputs; reconcile unknown commits. Do not invent raw source
     * tables or overwrite existing facts. DEV-04 executes through shared H04.
     */
    @FunctionalInterface public interface BackfillGateway {
        BackfillReceipt createOrReplay(BackfillActor actor, String operationId, String key,
                                       UUID approvalId, BackfillCommand command);
    }
    public record BackfillActor(UUID principalId, String scopeVersion) {
        public BackfillActor { Objects.requireNonNull(principalId); Objects.requireNonNull(scopeVersion); }
    }
    public record BackfillCommand(String source, OffsetDateTime from, OffsetDateTime to, String reason) {}
    public record BackfillReceipt(UUID taskId, TaskStatus status, OffsetDateTime createdAt) {
        public BackfillReceipt {
            Objects.requireNonNull(taskId); Objects.requireNonNull(status);
            createdAt = Objects.requireNonNull(createdAt).withOffsetSameInstant(ZoneOffset.UTC);
        }
    }
    private static DataApiException unavailableProblem() {
        return new DataApiException(ErrorCode.SYSTEM_UNAVAILABLE, null,
                "Shared backfill authorization, source profile, independent approval and atomic task creation are not connected");
    }
    public static IngestionBackfillService unavailable() {
        return new IngestionBackfillService((csrf, source) -> { throw unavailableProblem(); },
                (actor, operation, key, approval, command) -> { throw unavailableProblem(); });
    }
}
