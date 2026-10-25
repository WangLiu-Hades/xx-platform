package com.xxplatform.api.service.ingestion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xxplatform.api.generated.model.IngestionStatus;
import com.xxplatform.api.common.ErrorCode;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class IngestionQueryService {
    private final ReadAccess access;
    private final BatchQuery query;

    public IngestionQueryService(ReadAccess access, BatchQuery query) {
        this.access = Objects.requireNonNull(access);
        this.query = Objects.requireNonNull(query);
    }

    public BatchPage list(BatchListQuery filters) {
        // Obtain trusted authorization on EVERY call, never from query parameters.
        ReadScope scope = Objects.requireNonNull(access.requireReadScope());
        return query.findPage(filters, scope);
    }

    public BatchRow get(UUID batchId) {
        if (batchId == null) {
            throw new DataApiException(
                    ErrorCode.VALIDATION_ERROR, "batchId", "batchId is required");
        }
        ReadScope scope = Objects.requireNonNull(access.requireReadScope());
        return query.findById(batchId, scope).orElseThrow(() ->
                new DataApiException(ErrorCode.DATA_NOT_FOUND, null, "Batch not found"));
    }

    public ErrorPage errors(UUID batchId, String cursor, Integer requestedPageSize) {
        if (batchId == null) {
            throw new DataApiException(
                    ErrorCode.VALIDATION_ERROR, "batchId", "batchId is required");
        }
        int pageSize = requestedPageSize == null ? 20 : requestedPageSize;
        if (pageSize < 1 || pageSize > 100) {
            throw new DataApiException(
                    ErrorCode.VALIDATION_ERROR, "pageSize", "pageSize must be between 1 and 100");
        }
        ReadScope scope = Objects.requireNonNull(access.requireReadScope());
        query.findById(batchId, scope).orElseThrow(() ->
                new DataApiException(ErrorCode.DATA_NOT_FOUND, null, "Batch not found"));
        return query.findErrors(batchId, cursor, pageSize, scope);
    }

    public QualitySummary quality(String source, OffsetDateTime from, OffsetDateTime to) {
        QualityQuery filters = new QualityQuery(source, from, to);
        ReadScope scope = Objects.requireNonNull(access.requireReadScope());
        return query.findQuality(filters, scope);
    }

    // Adapter point for DEV-01. Its implementation must authenticate the session,
    // require ingestion:read and resolve the current trusted data scope.
    @FunctionalInterface
    public interface ReadAccess {
        ReadScope requireReadScope();
    }

    // Adapter point for DEV-04. Scope must constrain both rows and total.
    public interface BatchQuery {
        BatchPage findPage(BatchListQuery filters, ReadScope scope);
        Optional<BatchRow> findById(UUID batchId, ReadScope scope);
        ErrorPage findErrors(UUID batchId, String cursor, int pageSize, ReadScope scope);
        QualitySummary findQuality(QualityQuery filters, ReadScope scope);
    }

    public record ReadScope(UUID principalId, String version, Set<UUID> batchIds) {
        public ReadScope {
            Objects.requireNonNull(principalId);
            Objects.requireNonNull(version);
            batchIds = Set.copyOf(batchIds);
        }
    }

    public record BatchPage(List<BatchRow> items, int page, int pageSize, int total) {
        public BatchPage {
            items = List.copyOf(items);
        }
    }

    public record QualityQuery(String source, OffsetDateTime from, OffsetDateTime to) {
        public QualityQuery {
            if (from != null && to != null && !from.toInstant().isBefore(to.toInstant())) {
                throw new DataApiException(ErrorCode.VALIDATION_ERROR, "to",
                        "to must be later than from");
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QualitySummary(
            OffsetDateTime asOf, String source, int acceptedCount, int rejectedCount,
            int duplicateCount, int unmappedDeviceCount, int invalidCoordinateCount) {}

    public record ErrorPage(List<ErrorRow> items, String nextCursor, int pageSize) {
        public ErrorPage {
            items = List.copyOf(items);
        }
    }

    public record ErrorRow(int rowNumber, String sourceRecordKey, String field,
                           ErrorCode code, String reason) {
        public ErrorRow {
            if (rowNumber < 1) throw new IllegalArgumentException("rowNumber must be positive");
            Objects.requireNonNull(code);
            Objects.requireNonNull(reason);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BatchRow(
            UUID batchId, String source, String mappingVersion, String dedupVersion,
            IngestionStatus status, int acceptedCount, int rejectedCount,
            int duplicateCount, OffsetDateTime createdAt, UUID sourceFileId,
            UUID taskId, OffsetDateTime finishedAt, String errorSummary) {}
}
