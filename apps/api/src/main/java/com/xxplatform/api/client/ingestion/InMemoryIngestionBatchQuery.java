package com.xxplatform.api.client.ingestion;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.service.ingestion.DataApiException;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ErrorPage;
import com.xxplatform.api.service.ingestion.IngestionQueryService.QualityQuery;
import com.xxplatform.api.service.ingestion.IngestionQueryService.QualitySummary;
import com.xxplatform.api.generated.model.IngestionStatus;
import com.xxplatform.api.generated.model.SortOrder;
import com.xxplatform.api.service.ingestion.BatchListQuery;
import com.xxplatform.api.service.ingestion.IngestionQueryService.BatchPage;
import com.xxplatform.api.service.ingestion.IngestionQueryService.BatchQuery;
import com.xxplatform.api.service.ingestion.IngestionQueryService.BatchRow;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ReadScope;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryIngestionBatchQuery implements BatchQuery {
    private final List<BatchRow> rows;
    private final MockIngestionErrorPager errors;
    private final MockIngestionQualityQuery quality;

    public InMemoryIngestionBatchQuery(List<BatchRow> rows) {
        this(rows, new MockIngestionErrorPager(MockIngestionErrorPager.demoRows()));
    }

    public InMemoryIngestionBatchQuery(List<BatchRow> rows, MockIngestionErrorPager errors) {
        this.rows = List.copyOf(rows);
        this.errors = java.util.Objects.requireNonNull(errors);
        this.quality = new MockIngestionQualityQuery(this.rows);
    }

    @Override
    public BatchPage findPage(BatchListQuery filters, ReadScope scope) {
        Comparator<BatchRow> order = "status".equals(filters.sortBy())
                ? Comparator.comparing(row -> row.status().toString())
                : Comparator.comparing(row -> row.createdAt().toInstant());
        order = order.thenComparing(row -> row.batchId().toString());
        if (filters.sortOrder() == SortOrder.DESC) order = order.reversed();

        List<BatchRow> matching = rows.stream()
                .filter(row -> scope.batchIds().contains(row.batchId()))
                .filter(row -> filters.source() == null
                        || filters.source().equals(row.source()))
                .filter(row -> filters.status() == null || filters.status() == row.status())
                .sorted(order)
                .toList();

        // Use long arithmetic: page=Integer.MAX_VALUE must not overflow.
        long offset = ((long) filters.page() - 1L) * filters.pageSize();
        List<BatchRow> page = offset >= matching.size() ? List.of()
                : matching.subList((int) offset,
                        (int) Math.min(offset + filters.pageSize(), matching.size()));
        return new BatchPage(page, filters.page(), filters.pageSize(), matching.size());
    }

    @Override
    public Optional<BatchRow> findById(UUID batchId, ReadScope scope) {
        if (!scope.batchIds().contains(batchId)) return Optional.empty();
        return rows.stream().filter(row -> row.batchId().equals(batchId)).findFirst();
    }

    @Override
    public ErrorPage findErrors(UUID batchId, String cursor, int pageSize, ReadScope scope) {
        if (findById(batchId, scope).isEmpty()) {
            throw new DataApiException(ErrorCode.DATA_NOT_FOUND, null, "Batch not found");
        }
        return errors.page(batchId, cursor, pageSize, scope);
    }

    @Override
    public QualitySummary findQuality(QualityQuery filters, ReadScope scope) {
        return quality.summary(filters, scope);
    }

    public static UUID id(int number) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012d", number));
    }

    public static ReadScope demoScope() {
        return new ReadScope(id(900), "synthetic-scope-v1",
                java.util.Set.of(id(1), id(2), id(3), id(4), id(5), id(6)));
    }

    public static List<BatchRow> demoRows() {
        return List.of(
                row(1, "PLATE_TEST_A", IngestionStatus.SUCCESS, 11),
                row(2, "PLATE_TEST_A", IngestionStatus.FAILED, 10),
                row(3, "PLATE_TEST_B", IngestionStatus.SUCCESS, 11),
                row(4, "PLATE_TEST_A", IngestionStatus.RUNNING, 12),
                row(5, "PLATE_TEST_B", IngestionStatus.PENDING, 9),
                row(6, "PLATE_TEST_A", IngestionStatus.CANCELLED, 8),
                // This row exists, but the demonstration scope cannot see it.
                row(7, "PLATE_TEST_A", IngestionStatus.SUCCESS, 15));
    }

    private static BatchRow row(int n, String source, IngestionStatus status, int hour) {
        OffsetDateTime created = OffsetDateTime.parse(
                String.format("2026-10-24T%02d:00:00Z", hour));
        boolean terminal = status == IngestionStatus.SUCCESS
                || status == IngestionStatus.FAILED || status == IngestionStatus.CANCELLED;
        return new BatchRow(id(n), source, "synthetic-map-v1", "synthetic-dedup-v1",
                status, status == IngestionStatus.SUCCESS ? 10 : 0,
                status == IngestionStatus.FAILED ? 2 : 0, 0, created,
                id(100 + n), id(200 + n), terminal ? created.plusMinutes(1) : null,
                status == IngestionStatus.FAILED ? "Synthetic processing failure" : null);
    }
}
