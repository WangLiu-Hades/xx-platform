package com.xxplatform.api.service.plate;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.service.ingestion.DataApiException;
import com.xxplatform.api.client.plate.PlateQueryAdapter;
import com.xxplatform.api.service.plate.PlateQueryService.*;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;

/**
 * DEV-02 contribution consumed by DEV-01's shared POST /exports.
 * No ExportApi, task store, approval service or file download route is created here.
 */
public final class CaptureExportSourceAdapter {
    private static final Set<String> COLUMNS = Set.of("captureId","dataType","captureTime",
            "ingestedAt","deviceId","siteId","displayTarget","source","status",
            "longitude","latitude","imsiMasked","imeiMasked","macMasked","plateMasked","faceImageAssetId");
    private final ReadAccess access;
    private final ContextLookup contexts;
    private final PlateQueryAdapter queryAdapter;
    private final Clock clock;
    public CaptureExportSourceAdapter(ReadAccess access, ContextLookup contexts,
                                       PlateQueryAdapter queryAdapter, Clock clock) {
        this.access = Objects.requireNonNull(access); this.contexts = Objects.requireNonNull(contexts);
        this.queryAdapter = Objects.requireNonNull(queryAdapter); this.clock = Objects.requireNonNull(clock);
    }
    public Source prepare(String csrf, UUID contextId, String format, List<String> columns, String purpose) {
        if (contextId == null || format == null || !Set.of("XLSX","CSV","ZIP").contains(format)) {
            throw new DataApiException(ErrorCode.VALIDATION_ERROR, "body",
                    "contextId and an allowed export format are required");
        }
        if (columns != null && (columns.isEmpty() || columns.stream().anyMatch(c -> c == null || !COLUMNS.contains(c)))) {
            throw new DataApiException(ErrorCode.VALIDATION_ERROR, "columns", "Invalid export columns");
        }
        Authority authority = Objects.requireNonNull(access.requireAccess("capture:export", csrf));
        FrozenContext context = contexts.load(contextId, authority);
        if (context == null || !contextId.equals(context.id())) {
            throw new DataApiException(ErrorCode.DATA_NOT_FOUND, "contextId", "Context not found");
        }
        if (context.expiresAt() == null || !context.expiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw new DataApiException(ErrorCode.CONTEXT_EXPIRED, "contextId", "Context has expired");
        }
        if (!"CAPTURE_SEARCH".equals(context.kind()) || context.query() == null) {
            throw new DataApiException(ErrorCode.DATA_INVALID_FILTER, "contextId",
                    "A capture search Context is required");
        }
        // Whole frozen query: paging cursor/pageSize are deliberately not part of the source.
        var plan = queryAdapter.prepare(authority, context.query(), false);
        return new Source(contextId, format, columns, purpose, plan, authority, context.expiresAt());
    }
    public record FrozenContext(UUID id, String kind, OffsetDateTime expiresAt, CaptureQuery query) {}
    public record Source(UUID contextId, String format, List<String> columns, String purpose,
            PlateQueryAdapter.PreparedQuery query, Authority authority, OffsetDateTime contextExpiresAt) {
        public Source { columns = columns == null ? null : List.copyOf(columns); }
        @Override public String toString() { return "CaptureExportSource[redacted]"; }
    }
    /** Load the server's entire frozen query, not Context.targets preview or client page IDs. */
    @FunctionalInterface public interface ContextLookup {
        FrozenContext load(UUID contextId, Authority currentAuthority);
    }
}
