package com.xxplatform.api.service.ingestion;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.TaskStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * DEV-02 domain implementation. Nested ports are Java integration seams, not
 * a claim that DEV-01 already exposes these Java methods or an HTTP protocol.
 * The adapter must execute planning with locked, currently authorized metadata.
 */
public final class IngestionDomainGateway implements
        IngestionBatchSubmissionService.SubmissionGateway,
        IngestionBatchReplayService.ReplayGateway,
        IngestionBackfillService.BackfillGateway {
    private final AtomicSubmission submission;
    public IngestionDomainGateway(AtomicSubmission submission) {
        this.submission = Objects.requireNonNull(submission);
    }

    @Override
    public IngestionBatchSubmissionService.SubmissionReceipt createOrReplay(
            IngestionBatchSubmissionService.AuthorizedActor actor, String operation,
            String key, IngestionBatchSubmissionService.SubmissionInput input) {
        checkOperation(operation, "createIngestionBatch");
        Committed result = submit(new Actor(actor.principalId(), actor.scopeVersion()),
                operation, key, null, new PublicRequest(null, input), catalog -> {
            Source source = enabled(catalog, input.source());
            versions(source, input.mappingVersion(), input.dedupVersion());
            FileInfo file = activeFile(catalog, input.sourceFileId(), source.code());
            return new FrozenInput("INGEST", source.code(), file.id(),
                    input.mappingVersion(), input.dedupVersion(), null, null,
                    null, null, null, false, source.policyVersion(),
                    source.version(), file.version(), null);
        });
        return new IngestionBatchSubmissionService.SubmissionReceipt(
                result.batchId(), result.taskId(), result.status(), result.createdAt());
    }

    @Override
    public IngestionBatchReplayService.ReplayReceipt createOrReplay(
            IngestionBatchReplayService.ReplayActor actor, String operation, UUID batchId,
            String key, UUID approvalId, IngestionBatchReplayService.ReplayCommand command) {
        checkOperation(operation, "replayIngestionBatch");
        Committed result = submit(new Actor(actor.principalId(), actor.scopeVersion()),
                operation, key, approvalId, new PublicRequest(batchId, command), catalog -> {
            BatchInfo original = catalog.batch(batchId);
            if (original == null || !batchId.equals(original.id())) {
                throw problem(ErrorCode.DATA_NOT_FOUND, null, "Batch not found");
            }
            if (!original.retained()) {
                throw problem(ErrorCode.FILE_EXPIRED, null, "Original input is outside retention");
            }
            if (original.version() == null || original.version().isBlank()) {
                throw unavailable("Original batch version is unavailable");
            }
            Source source = enabled(catalog, original.source());
            String mapping = command.mappingVersion() == null
                    ? original.mappingVersion() : command.mappingVersion();
            String dedup = command.dedupVersion() == null
                    ? original.dedupVersion() : command.dedupVersion();
            versions(source, mapping, dedup);
            FileInfo file = activeFile(catalog, original.fileId(), source.code());
            long generation = catalog.nextReplayGeneration(original.id());
            if (generation < 1) throw unavailable("Replay generation could not be allocated");
            return new FrozenInput("REPLAY", source.code(), file.id(), mapping, dedup,
                    original.id(), generation, utc(original.from()), utc(original.to()),
                    command.reason(), true, source.policyVersion(),
                    source.version(), file.version(), original.version());
        });
        if (batchId.equals(result.batchId())) {
            throw unavailable("Replay submission did not return a new batch");
        }
        return new IngestionBatchReplayService.ReplayReceipt(
                result.taskId(), result.status(), result.createdAt(), result.batchId());
    }

    @Override
    public IngestionBackfillService.BackfillReceipt createOrReplay(
            IngestionBackfillService.BackfillActor actor, String operation, String key,
            UUID approvalId, IngestionBackfillService.BackfillCommand command) {
        checkOperation(operation, "createIngestionBackfill");
        Committed result = submit(new Actor(actor.principalId(), actor.scopeVersion()),
                operation, key, approvalId, new PublicRequest(null, command), catalog -> {
            Source source = enabled(catalog, command.source());
            OffsetDateTime from = utc(command.from()), to = utc(command.to());
            if (from == null || to == null || !from.isBefore(to)) {
                throw problem(ErrorCode.VALIDATION_ERROR, "timeRange", "A valid time range is required");
            }
            if (source.retainedFrom() == null || source.retainedTo() == null) {
                throw unavailable("An explicit retention policy is required");
            }
            if (from.isBefore(source.retainedFrom()) || to.isAfter(source.retainedTo())) {
                throw problem(ErrorCode.DATA_INVALID_FILTER, "timeRange",
                        "Requested range is outside available retention");
            }
            versions(source, source.defaultMapping(), source.defaultDedup());
            return new FrozenInput("BACKFILL", source.code(), null,
                    source.defaultMapping(), source.defaultDedup(), null, null, from, to,
                    command.reason(), true, source.policyVersion(), source.version(), null, null);
        });
        return new IngestionBackfillService.BackfillReceipt(
                result.taskId(), result.status(), result.createdAt());
    }

    private Committed submit(Actor actor, String operation, String key, UUID approvalId,
                             PublicRequest request, Planner planner) {
        Committed result = submission.submit(actor, operation, key, approvalId, request, planner);
        if (result == null || result.taskId() == null || result.batchId() == null
                || result.status() == null || result.createdAt() == null) {
            throw unavailable("Submission returned an incomplete committed receipt");
        }
        return result;
    }
    private static Source enabled(LockedCatalog catalog, String code) {
        Source source = catalog.source(code);
        if (source == null || !Objects.equals(code, source.code()) || !source.enabled()) {
            throw problem(ErrorCode.REQUIREMENT_PENDING, "source", "Source is not available");
        }
        if (source.policyVersion() == null || source.policyVersion().isBlank()
                || source.version() == null || source.version().isBlank()) {
            throw unavailable("Source policy version is not available");
        }
        return source;
    }
    private static void versions(Source source, String mapping, String dedup) {
        if (mapping == null || dedup == null
                || !Objects.equals(source.mappingDedupPairs().get(mapping), dedup)) {
            throw problem(ErrorCode.DATA_INVALID_FILTER, "mappingVersion",
                    "Mapping and deduplication versions are unavailable or incompatible");
        }
    }
    private static FileInfo activeFile(LockedCatalog catalog, UUID id, String source) {
        if (id == null) throw problem(ErrorCode.FILE_EXPIRED, "sourceFileId", "Original input is unavailable");
        FileInfo file = catalog.file(id);
        if (file == null || !id.equals(file.id())) {
            throw problem(ErrorCode.DATA_NOT_FOUND, "sourceFileId", "File not found");
        }
        if (!file.retained() || (file.expiresAt() != null
                && !file.expiresAt().isAfter(catalog.now()))) {
            throw problem(ErrorCode.FILE_EXPIRED, "sourceFileId", "Input file has expired");
        }
        if (!"ACTIVE".equals(file.status()) || !"RAW_INGESTION".equals(file.category())
                || !Objects.equals(source, file.source())) {
            throw problem(ErrorCode.FILE_INVALID, "sourceFileId",
                    "An ACTIVE raw input file matching the source is required");
        }
        if (file.version() == null || file.version().isBlank()) throw unavailable("File version is unavailable");
        return file;
    }
    private static void checkOperation(String actual, String expected) {
        if (!expected.equals(actual)) throw unavailable("Unexpected domain operation");
    }
    private static OffsetDateTime utc(OffsetDateTime time) {
        return time == null ? null : time.withOffsetSameInstant(ZoneOffset.UTC);
    }
    private static DataApiException problem(ErrorCode code, String field, String message) {
        return new DataApiException(code, field, message);
    }
    private static DataApiException unavailable(String message) {
        return problem(ErrorCode.SYSTEM_UNAVAILABLE, null, message);
    }

    public record Actor(UUID principalId, String scopeVersion) {}
    public record PublicRequest(UUID sourceBatchId, Object typedBody) {
        @Override public String toString() { return "PublicRequest[redacted]"; }
    }
    public record Source(String code, boolean enabled, Map<String,String> mappingDedupPairs,
            String defaultMapping, String defaultDedup, OffsetDateTime retainedFrom,
            OffsetDateTime retainedTo, String policyVersion, String version) {
        public Source { mappingDedupPairs = Map.copyOf(mappingDedupPairs); }
    }
    public record FileInfo(UUID id, String status, String category, String source,
            OffsetDateTime expiresAt, boolean retained, String version) {}
    public record BatchInfo(UUID id, String source, UUID fileId, String mappingVersion,
            String dedupVersion, OffsetDateTime from, OffsetDateTime to,
            boolean retained, String version) {}
    public record FrozenInput(String mode, String source, UUID fileId, String mappingVersion,
            String dedupVersion, UUID originalBatchId, Long replayGeneration,
            OffsetDateTime from, OffsetDateTime to, String reason, boolean approvalRequired,
            String policyVersion, String sourceVersion, String fileVersion, String batchVersion) {
        @Override public String toString() { return "FrozenInput[redacted]"; }
    }
    public record Committed(UUID batchId, UUID taskId, TaskStatus status, OffsetDateTime createdAt) {}
    @FunctionalInterface public interface Planner { FrozenInput plan(LockedCatalog catalog); }
    public interface LockedCatalog {
        OffsetDateTime now();
        Source source(String code);
        FileInfo file(UUID id);
        BatchInfo batch(UUID id);
        long nextReplayGeneration(UUID sourceBatchId);
    }
    /**
     * Required DEV-01/DEV-02 transaction bridge:
     * reauthorize; identify actor+operation+key and canonical original typed input;
     * return an already committed matching receipt BEFORE running planner;
     * reject conflicts/tombstones; reconcile an unknown commit outcome.
     * For a new action run planner with locked scoped metadata, recheck the frozen
     * policy/resource versions, independently verify and consume approval when
     * required, then atomically persist NEW batch, shared task and receipt.
     * Do not implement a second TaskStore or a process-local idempotency cache.
     * retained flags/bounds come from the current approved 12-calendar-month policy.
     * File/source version strings identify the locked server-side revisions.
     */
    @FunctionalInterface public interface AtomicSubmission {
        Committed submit(Actor actor, String operation, String key, UUID approvalId,
                         PublicRequest request, Planner planner);
    }
}
