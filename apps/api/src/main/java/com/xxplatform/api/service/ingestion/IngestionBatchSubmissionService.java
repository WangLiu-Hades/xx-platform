package com.xxplatform.api.service.ingestion;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.TaskStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/** Domain ports are a DEV-02 integration proposal; shared implementations are pending. */
public final class IngestionBatchSubmissionService {
    public static final String OPERATION_ID = "createIngestionBatch";
    private final WriteAccess access;
    private final SubmissionGateway gateway;

    public IngestionBatchSubmissionService(WriteAccess access, SubmissionGateway gateway) {
        this.access = Objects.requireNonNull(access);
        this.gateway = Objects.requireNonNull(gateway);
    }

    public SubmissionReceipt submit(String csrfToken, String idempotencyKey, SubmissionInput input) {
        requireText(csrfToken, "xCSRFToken");
        if (csrfToken.length() < 16) throw invalid("xCSRFToken", "Token must contain at least 16 characters");
        requireText(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.length() < 16 || idempotencyKey.length() > 160) {
            throw invalid("idempotencyKey", "Key must contain between 16 and 160 characters");
        }
        if (input == null) throw invalid("body", "Request body is required");
        requireText(input.source(), "source");
        if (input.source().length() > 80) throw invalid("source", "source must not exceed 80 characters");
        if (input.sourceFileId() == null) throw invalid("sourceFileId", "sourceFileId is required");
        requireText(input.mappingVersion(), "mappingVersion");
        requireText(input.dedupVersion(), "dedupVersion");

        // Shape validation is not authentication. Reauthorize every call, including replay.
        AuthorizedActor actor = Objects.requireNonNull(access.requireWriteAccess(csrfToken));
        return Objects.requireNonNull(
                gateway.createOrReplay(actor, OPERATION_ID, idempotencyKey, input),
                "Submission gateway returned no persisted receipt");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field, field + " is required");
    }

    private static DataApiException invalid(String field, String message) {
        return new DataApiException(ErrorCode.VALIDATION_ERROR, field, message);
    }

    /**
     * Trusted adapter must use the current SID/request and trusted Origin, compare
     * the bound CSRF token, require ingestion:write and resolve fresh scope.
     * The token string alone or a client principal must never grant write access.
     */
    @FunctionalInterface
    public interface WriteAccess {
        AuthorizedActor requireWriteAccess(String csrfToken);
    }

    /**
     * Scope idempotency to actor+operation+key and canonical input. Recheck current
     * access on replay, reject mismatched/tombstoned keys. For new submissions,
     * validate authorized ACTIVE RAW_INGESTION file and source/mapping/dedup
     * bindings, then atomically commit batch+shared task+idempotency receipt.
     * File validation and creation cannot have an unchecked race. Unknown commit
     * outcomes require shared reconciliation. Return only after durable commit.
     * No task store, state machine or local replay cache is implemented here.
     */
    @FunctionalInterface
    public interface SubmissionGateway {
        SubmissionReceipt createOrReplay(AuthorizedActor actor, String operationId,
                                          String idempotencyKey, SubmissionInput input);
    }

    public record AuthorizedActor(UUID principalId, String scopeVersion) {
        public AuthorizedActor {
            Objects.requireNonNull(principalId);
            Objects.requireNonNull(scopeVersion);
        }
    }

    public record SubmissionInput(String source, UUID sourceFileId,
                                  String mappingVersion, String dedupVersion) {}

    public record SubmissionReceipt(UUID batchId, UUID taskId, TaskStatus status,
                                    OffsetDateTime createdAt) {
        public SubmissionReceipt {
            Objects.requireNonNull(batchId);
            Objects.requireNonNull(taskId);
            Objects.requireNonNull(status);
            createdAt = Objects.requireNonNull(createdAt).withOffsetSameInstant(ZoneOffset.UTC);
        }
    }

    private static DataApiException dependencyUnavailable() {
        return new DataApiException(ErrorCode.SYSTEM_UNAVAILABLE, null,
                "Shared write authorization, ACTIVE file validation and atomic task submission are not connected");
    }

    public static IngestionBatchSubmissionService unavailable() {
        return new IngestionBatchSubmissionService(
                csrf -> { throw dependencyUnavailable(); },
                (actor, operation, key, input) -> { throw dependencyUnavailable(); });
    }
}
