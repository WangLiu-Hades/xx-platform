package com.xxplatform.api.client.ingestion;

import com.xxplatform.api.generated.model.IngestionStatus;
import com.xxplatform.api.service.ingestion.IngestionQueryService.BatchRow;
import com.xxplatform.api.service.ingestion.IngestionQueryService.QualityQuery;
import com.xxplatform.api.service.ingestion.IngestionQueryService.QualitySummary;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ReadScope;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery.id;

/**
 * Immutable development fixture only. Matches the existing experimental
 * synthetic-m03-quality-v1 policy: visible SUCCESS batches, processed item time,
 * processing outcomes, distinct (source, device key), and flagged row count.
 * Formal policy and real read budgets still belong to the DEV-01/DEV-04 handoff.
 */
public final class MockIngestionQualityQuery {
    public static final OffsetDateTime DEMO_AS_OF =
            OffsetDateTime.parse("2026-10-24T16:00:00Z");
    private final Map<UUID, BatchRow> batches;
    private final List<QualityItem> items;
    private final OffsetDateTime asOf;

    public MockIngestionQualityQuery(List<BatchRow> batches) {
        this(batches, demoItems(), DEMO_AS_OF);
    }

    public MockIngestionQualityQuery(List<BatchRow> batches, List<QualityItem> items,
                                     OffsetDateTime asOf) {
        this.batches = batches.stream().collect(
                Collectors.toUnmodifiableMap(BatchRow::batchId, Function.identity()));
        this.items = List.copyOf(items);
        this.asOf = Objects.requireNonNull(asOf).withOffsetSameInstant(ZoneOffset.UTC);
    }

    public QualitySummary summary(QualityQuery filters, ReadScope scope) {
        int accepted = 0;
        int rejected = 0;
        int duplicate = 0;
        int invalidCoordinate = 0;
        Set<SourceDevice> unmappedDevices = new HashSet<>();
        for (QualityItem item : items) {
            BatchRow batch = batches.get(item.batchId());
            if (batch == null || batch.status() != IngestionStatus.SUCCESS
                    || !scope.batchIds().contains(batch.batchId())) continue;
            if (filters.source() != null && !filters.source().equals(batch.source())) continue;
            Instant processed = item.processedAt().toInstant();
            if (filters.from() != null && processed.isBefore(filters.from().toInstant())) continue;
            if (filters.to() != null && !processed.isBefore(filters.to().toInstant())) continue;

            switch (item.outcome()) {
                case ACCEPTED -> accepted++;
                case REJECTED -> rejected++;
                case DUPLICATE -> duplicate++;
            }
            if (item.unmappedDevice() && item.sourceDeviceKey() != null) {
                unmappedDevices.add(new SourceDevice(batch.source(), item.sourceDeviceKey()));
            }
            if (item.invalidCoordinate()) invalidCoordinate++;
        }
        return new QualitySummary(asOf, filters.source(), accepted, rejected, duplicate,
                unmappedDevices.size(), invalidCoordinate);
    }

    public enum Outcome { ACCEPTED, REJECTED, DUPLICATE }

    public record QualityItem(UUID batchId, OffsetDateTime processedAt, Outcome outcome,
                              String sourceDeviceKey, boolean unmappedDevice,
                              boolean invalidCoordinate) {
        public QualityItem {
            Objects.requireNonNull(batchId);
            Objects.requireNonNull(processedAt);
            Objects.requireNonNull(outcome);
        }
    }

    private record SourceDevice(String source, String deviceKey) {}

    public static List<QualityItem> demoItems() {
        List<QualityItem> result = new ArrayList<>();
        for (int batchNumber : new int[] {1, 3, 7}) {
            OffsetDateTime processed = OffsetDateTime.parse(batchNumber == 7
                    ? "2026-10-24T15:00:30Z" : "2026-10-24T11:00:30Z");
            for (int row = 0; row < 10; row++) {
                result.add(new QualityItem(id(batchNumber), processed, Outcome.ACCEPTED,
                        "synthetic-device-" + batchNumber, false, false));
            }
        }
        // These two diagnostics belong to FAILED batch 2 and are excluded.
        for (int row = 0; row < 2; row++) {
            result.add(new QualityItem(id(2), OffsetDateTime.parse("2026-10-24T10:00:30Z"),
                    Outcome.REJECTED, "synthetic-device-2", false, true));
        }
        return List.copyOf(result);
    }
}
