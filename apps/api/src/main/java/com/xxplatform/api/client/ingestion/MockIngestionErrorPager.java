package com.xxplatform.api.client.ingestion;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ErrorPage;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ErrorRow;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ReadScope;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery.id;

/**
 * Development fixture pagination only. Data is immutable for this process.
 * Tokens are opaque random references, bound to the complete trusted scope,
 * batch and page size. Restart, expiration or bounded-cache eviction invalidates
 * a token. Production attempt/snapshot lifetime belongs to the real query adapter.
 */
public final class MockIngestionErrorPager {
    private final Map<UUID, List<StoredError>> rows;
    private final Clock clock;
    private final Duration lifetime;
    private final int maxCursors;
    private final Map<String, Continuation> cursors = new LinkedHashMap<>();

    public MockIngestionErrorPager(Map<UUID, List<StoredError>> rows) {
        this(rows, Clock.systemUTC(), Duration.ofMinutes(10), 256);
    }

    public MockIngestionErrorPager(Map<UUID, List<StoredError>> rows, Clock clock,
                                  Duration lifetime, int maxCursors) {
        this.clock = Objects.requireNonNull(clock);
        this.lifetime = Objects.requireNonNull(lifetime);
        if (lifetime.isZero() || lifetime.isNegative() || maxCursors < 1) {
            throw new IllegalArgumentException("Cursor lifetime and capacity must be positive");
        }
        this.maxCursors = maxCursors;
        Map<UUID, List<StoredError>> copy = new LinkedHashMap<>();
        rows.forEach((batchId, errors) -> copy.put(Objects.requireNonNull(batchId),
                errors.stream().sorted(Comparator
                        .comparingInt((StoredError error) -> error.row().rowNumber())
                        .thenComparing(error -> error.id().toString())).toList()));
        this.rows = Map.copyOf(copy);
    }

    public synchronized ErrorPage page(UUID batchId, String cursor,
                                       int pageSize, ReadScope scope) {
        if (pageSize < 1 || pageSize > 100) {
            throw new DataApiException(ErrorCode.VALIDATION_ERROR, "pageSize",
                    "pageSize must be between 1 and 100");
        }
        if (!scope.batchIds().contains(batchId)) {
            throw new DataApiException(ErrorCode.DATA_NOT_FOUND, null, "Batch not found");
        }
        Instant now = clock.instant();
        cursors.values().removeIf(position -> !now.isBefore(position.expiresAt()));
        int offset = 0;
        Instant expiresAt = now.plus(lifetime);
        if (cursor != null) {
            Continuation position = cursors.get(cursor);
            if (position == null || !position.batchId().equals(batchId)
                    || position.pageSize() != pageSize || !position.scope().equals(scope)) {
                throw invalidCursor();
            }
            offset = position.offset();
            expiresAt = position.expiresAt();
        }
        List<StoredError> available = rows.getOrDefault(batchId, List.of());
        int end = (int) Math.min((long) offset + pageSize, available.size());
        List<ErrorRow> items = available.subList(offset, end).stream()
                .map(StoredError::row).toList();
        String nextCursor = null;
        if (end < available.size()) {
            while (cursors.size() >= maxCursors) {
                cursors.remove(cursors.keySet().iterator().next());
            }
            nextCursor = "mock-errors-" + UUID.randomUUID();
            cursors.put(nextCursor, new Continuation(batchId, pageSize, scope, end, expiresAt));
        }
        return new ErrorPage(items, nextCursor, pageSize);
    }

    private static DataApiException invalidCursor() {
        return new DataApiException(ErrorCode.DATA_CURSOR_INVALID, "cursor",
                "Cursor is invalid or expired; restart from the first page");
    }

    public record StoredError(UUID id, ErrorRow row) {
        public StoredError {
            Objects.requireNonNull(id);
            Objects.requireNonNull(row);
        }
    }

    private record Continuation(UUID batchId, int pageSize, ReadScope scope,
                                int offset, Instant expiresAt) {}

    public static Map<UUID, List<StoredError>> demoRows() {
        // Exactly two public synthetic errors match batch 2's rejectedCount=2.
        // These are not raw file data or internal exception diagnostics.
        return Map.of(id(2), List.of(
                new StoredError(id(8001), new ErrorRow(3, "synthetic-record-003",
                        "captureTime", ErrorCode.VALIDATION_ERROR,
                        "Synthetic record: captureTime format is invalid")),
                new StoredError(id(8002), new ErrorRow(8, "synthetic-record-008",
                        "longitude", ErrorCode.VALIDATION_ERROR,
                        "Synthetic record: longitude is outside the accepted range"))));
    }
}
