package com.xxplatform.api.ingestion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xxplatform.api.client.ingestion.IngestionQueryAdapter;
import com.xxplatform.api.client.ingestion.IngestionQueryAdapter.*;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.IngestionStatus;
import com.xxplatform.api.service.ingestion.*;
import com.xxplatform.api.service.ingestion.IngestionQueryService.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class IngestionQueryAdapterTests {
    private static final UUID ID=new UUID(0,1), OTHER=new UUID(0,2);
    private final ReadScope scope=new ReadScope(new UUID(0,99),"scope1",Set.of(ID));
    private final Client client=mock(Client.class);
    private final ObjectMapper mapper=new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final OffsetDateTime from=OffsetDateTime.parse("2026-10-24T10:00:00Z");
    private final OffsetDateTime to=OffsetDateTime.parse("2026-10-24T11:00:00Z");
    private Map<String,Object> seenFilters;
    private ReadScope seenScope;
    private final IngestionQueryAdapter adapter=new IngestionQueryAdapter(
            client,(a,b,s)->new Window(a==null?from:a,b==null?to:b),mapper);
    private Map<String,Object> row(UUID id) {
        var result=new LinkedHashMap<String,Object>();
        result.put("batchId",id.toString()); result.put("source","PLATE_A");
        result.put("mappingVersion","map1"); result.put("dedupVersion","dedup1");
        result.put("status","SUCCESS"); result.put("acceptedCount",10);
        result.put("rejectedCount",0); result.put("duplicateCount",0);
        result.put("createdAt","2026-10-24T10:00:00Z"); result.put("errorSummary","internal diagnostic");
        return result;
    }
    private Map<String,Object> quality() {
        return Map.of("asOf","2026-10-24T12:00:00Z","acceptedCount",10,
                "rejectedCount",0,"duplicateCount",0,"unmappedDeviceCount",0,"invalidCoordinateCount",0);
    }
    private void fails(ErrorCode code, Runnable work) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(DataApiException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }
    @Test void listMapsDefaultsAndPassesCurrentExplicitScope() {
        when(client.listBatches(anyMap(),any())).thenAnswer(invocation -> {
            seenFilters=invocation.getArgument(0); seenScope=invocation.getArgument(1);
            return Map.of("items",List.of(row(ID)),"page",1,"pageSize",20,"total",1);
        });
        BatchPage page=adapter.findPage(new BatchListQuery(null,null,null,null,null,null),scope);
        assertThat(seenFilters).containsEntry("sortBy","createdAt").containsEntry("sortOrder","DESC")
                .containsEntry("pageSize",20).doesNotContainKeys("source","status");
        assertThat(seenScope).isSameAs(scope);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).errorSummary()).isEqualTo("Batch processing reported an error.");
    }
    @Test void listRejectsAnUnauthorizedReturnedRow() {
        when(client.listBatches(anyMap(),any())).thenReturn(
                Map.of("items",List.of(row(OTHER)),"page",1,"pageSize",20,"total",1));
        fails(ErrorCode.SYSTEM_UNAVAILABLE,()->adapter.findPage(
                new BatchListQuery(null,null,null,null,null,null),scope));
    }
    @Test void largeCountsCannotBeSilentlyTruncated() {
        var record=row(ID); record.put("acceptedCount",3_000_000_000L);
        when(client.listBatches(anyMap(),any())).thenReturn(
                Map.of("items",List.of(record),"page",1,"pageSize",20,"total",1));
        fails(ErrorCode.SYSTEM_UNAVAILABLE,()->adapter.findPage(
                new BatchListQuery(null,null,null,null,null,null),scope));
    }
    @Test void invisibleDetailDoesNotCallWorker() {
        assertThat(adapter.findById(OTHER,scope)).isEmpty();
        verifyNoInteractions(client);
    }
    @Test void qualityResolvesMissingWindowAndOmitsMissingSource() {
        when(client.qualitySummary(anyMap(),any())).thenAnswer(invocation -> {
            seenFilters=invocation.getArgument(0); return quality();
        });
        assertThat(adapter.findQuality(new QualityQuery(null,null,null),scope).acceptedCount()).isEqualTo(10);
        assertThat(seenFilters).containsEntry("from","2026-10-24T10:00:00Z")
                .containsEntry("to","2026-10-24T11:00:00Z").doesNotContainKey("source");
    }
    @Test void qualityConvertsOffsetToUtc() {
        when(client.qualitySummary(anyMap(),any())).thenAnswer(invocation -> {
            seenFilters=invocation.getArgument(0); return quality();
        });
        adapter.findQuality(new QualityQuery(null,OffsetDateTime.parse("2026-10-24T18:00:00+08:00"),
                OffsetDateTime.parse("2026-10-24T19:00:00+08:00")),scope);
        assertThat(seenFilters).containsEntry("from","2026-10-24T10:00:00Z")
                .containsEntry("to","2026-10-24T11:00:00Z");
    }
    @Test void errorCursorIsPassedUnchangedToExistingSession() {
        when(client.listErrors(ID,"opaque-cursor",1,scope)).thenReturn(
                nullableCursorPageFixture());
        assertThat(adapter.findErrors(ID,"opaque-cursor",1,scope).items()).isEmpty();
        verify(client).listErrors(ID,"opaque-cursor",1,scope);
    }
    @Test void workerCursorFailureUsesPublicCode() {
        when(client.listErrors(ID,"bad",20,scope)).thenThrow(new Failure("DATA_CURSOR_INVALID"));
        fails(ErrorCode.DATA_CURSOR_INVALID,()->adapter.findErrors(ID,"bad",20,scope));
    }
    @Test void unrecognizedInternalFailureIsNotSuccessOrRawDiagnostic() {
        when(client.getBatch(ID,scope)).thenThrow(new Failure("INTERNAL_DRIVER_ERROR"));
        fails(ErrorCode.SYSTEM_UNAVAILABLE,()->adapter.findById(ID,scope));
    }
    private static java.util.Map<String,Object> nullableCursorPageFixture() {
        // nextCursor 是必返字段，最后一页也需要显式返回 null。
        var page = new java.util.LinkedHashMap<String,Object>();
        page.put("items", java.util.List.of());
        page.put("pageSize", 1);
        page.put("nextCursor", null);
        return page;
    }
}
