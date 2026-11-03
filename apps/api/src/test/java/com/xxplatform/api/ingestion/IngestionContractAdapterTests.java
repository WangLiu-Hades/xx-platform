package com.xxplatform.api.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xxplatform.api.client.ingestion.IngestionContractAdapter;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.service.ingestion.BatchListQuery;
import com.xxplatform.api.service.ingestion.IngestionQueryService.*;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IngestionContractAdapterTests {
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final IngestionContractAdapter adapter = new IngestionContractAdapter(mapper);
    private final ReadScope scope = new ReadScope(ID, "contract-test-v1", Set.of(ID));
    private final BatchListQuery query = new BatchListQuery(1, 20, null, null, null, null);

    private Map<String, Object> batch() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("batchId", ID.toString()); row.put("source", "PLATE_TEST_A");
        row.put("mappingVersion", "synthetic-map-v1"); row.put("dedupVersion", "synthetic-dedup-v1");
        row.put("status", "SUCCESS"); row.put("acceptedCount", 10L);
        row.put("rejectedCount", 0); row.put("duplicateCount", 0);
        row.put("createdAt", "2026-10-24T11:00:00Z");
        return row;
    }

    private Map<String, Object> page(Map<String, Object> row) {
        return Map.of("items", List.of(row), "page", 1, "pageSize", 20, "total", 1L);
    }

    private QualityQuery quality() {
        return new QualityQuery(null, OffsetDateTime.parse("2026-10-24T19:00:00+08:00"),
                OffsetDateTime.parse("2026-10-24T20:00:00+08:00"));
    }

    private void rejected(Runnable action) {
        DataApiException failure = assertThrows(DataApiException.class, action::run);
        assertEquals(ErrorCode.SYSTEM_UNAVAILABLE, failure.code());
    }

    @Test void defaultsMatchWorkerParser() {
        Map<String, Object> spec = adapter.listSpec(query);
        assertEquals(20, spec.get("pageSize"));
        assertEquals("DESC", spec.get("sortOrder"));
        assertFalse(spec.containsKey("source"));
        assertFalse(spec.containsKey("status"));
        assertEquals(List.of(ID.toString()), adapter.trustedScope(scope).get("batch_ids"));
    }

    @Test void responseMapsIntoExistingBusinessObjects() {
        BatchPage result = adapter.readPage(page(batch()), query, scope);
        assertEquals(ID, result.items().get(0).batchId());
        assertEquals(10, result.items().get(0).acceptedCount());
        assertEquals(1, result.total());
    }

    @Test void unauthorizedProviderRowIsRejected() {
        Map<String, Object> row = batch();
        row.put("batchId", "00000000-0000-4000-8000-000000000999");
        rejected(() -> adapter.readPage(page(row), query, scope));
    }

    @Test void pageMetadataCannotChange() {
        Map<String, Object> raw = new LinkedHashMap<>(page(batch()));
        raw.put("pageSize", 100);
        rejected(() -> adapter.readPage(raw, query, scope));
    }

    @Test void countCannotBeMissingFractionalOrOverflowed() {
        for (Object value : List.of(-1, 1.5, Long.MAX_VALUE, "10")) {
            Map<String, Object> row = batch(); row.put("acceptedCount", value);
            rejected(() -> adapter.readPage(page(row), query, scope));
        }
        Map<String, Object> missing = batch(); missing.remove("acceptedCount");
        rejected(() -> adapter.readPage(page(missing), query, scope));
    }

    @Test void detailMustMatchRequestedId() {
        rejected(() -> adapter.readBatch(batch(), UUID.randomUUID(), scope));
    }

    @Test void errorPagePreservesOpaqueCursorAndTerminalNull() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("items", List.of(Map.of("rowNumber", 8, "code", "VALIDATION_ERROR",
                "reason", "Input row failed validation")));
        raw.put("pageSize", 1); raw.put("nextCursor", "opaque-worker-cursor");
        assertEquals("opaque-worker-cursor", adapter.readErrors(raw, 1).nextCursor());
        raw.put("nextCursor", null);
        assertNull(adapter.readErrors(raw, 1).nextCursor());
    }

    @Test void internalErrorLabelsCannotBecomePublicRowCodes() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("items", List.of(Map.of("rowNumber", 1, "code", "PG_PRIVATE_LABEL", "reason", "Hidden")));
        raw.put("pageSize", 20); raw.put("nextCursor", null);
        rejected(() -> adapter.readErrors(raw, 20));
        assertEquals(ErrorCode.SYSTEM_UNAVAILABLE, adapter.workerFailure("PG_PRIVATE_LABEL").code());
        assertEquals(ErrorCode.DATA_CURSOR_INVALID, adapter.workerFailure("DATA_CURSOR_INVALID").code());
    }

    @Test void qualityWindowUsesUtcAndCannotInventMissingBounds() {
        Map<String, Object> spec = adapter.qualitySpec(quality());
        assertEquals("2026-10-24T11:00:00Z", spec.get("from"));
        assertEquals("2026-10-24T12:00:00Z", spec.get("to"));
        rejected(() -> adapter.qualitySpec(new QualityQuery(null, null, null)));
    }

    @Test void qualityRequiresAllCountersAndMatchingSource() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("asOf", "2026-10-24T12:00:00Z"); raw.put("acceptedCount", 10);
        raw.put("rejectedCount", 0); raw.put("duplicateCount", 0);
        raw.put("unmappedDeviceCount", 0); raw.put("invalidCoordinateCount", 0);
        assertEquals(10, adapter.readQuality(raw, null).acceptedCount());
        rejected(() -> adapter.readQuality(raw, "PLATE_TEST_A"));
        raw.remove("invalidCoordinateCount");
        rejected(() -> adapter.readQuality(raw, null));
    }

    @Test void exportExamplesForActualWorkerParserWhenRequested() throws Exception {
        String evidence = System.getenv("DEV02_CONTRACT_EVIDENCE");
        if (evidence == null || evidence.isBlank()) return;
        Path directory = Path.of(evidence);
        Files.createDirectories(directory);
        Map<String, Object> examples = Map.of(
                "listSpec", adapter.listSpec(query),
                "scope", adapter.trustedScope(scope),
                "qualitySpec", adapter.qualitySpec(quality()));
        Files.writeString(directory.resolve("m03-contract-examples.json"),
                mapper.writeValueAsString(examples), java.nio.charset.StandardCharsets.UTF_8);
    }
}
