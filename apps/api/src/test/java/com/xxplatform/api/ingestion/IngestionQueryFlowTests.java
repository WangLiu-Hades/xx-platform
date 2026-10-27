package com.xxplatform.api.ingestion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xxplatform.api.client.ingestion.IngestionQueryAdapter;
import com.xxplatform.api.client.ingestion.QualityQueryWindowPolicy;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.service.ingestion.*;
import com.xxplatform.api.service.ingestion.IngestionQueryService.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestionQueryFlowTests {
    private static final UUID ID=new UUID(0,1), SECOND=new UUID(0,2);
    private final ReadScope scope=new ReadScope(new UUID(0,99),"scope1",Set.of(ID,SECOND));
    private final IngestionQueryAdapter.Client client=mock(IngestionQueryAdapter.Client.class);
    private final ObjectMapper mapper=new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final OffsetDateTime from=OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private final OffsetDateTime to=OffsetDateTime.parse("2026-10-25T00:00:00Z");
    private final IngestionQueryAdapter adapter=new IngestionQueryAdapter(client,
            current -> new IngestionQueryAdapter.Window(from,to),Duration.ofDays(366),mapper);
    private BatchListQuery query() { return new BatchListQuery(null,null,null,null,null,null); }
    private Map<String,Object> row() {
        var row=new LinkedHashMap<String,Object>();
        row.put("batchId",ID.toString()); row.put("source","PLATE_A");
        row.put("mappingVersion","mapping-v1"); row.put("dedupVersion","dedup-v1");
        row.put("status","SUCCESS"); row.put("createdAt","2026-10-24T10:00:00Z");
        row.put("acceptedCount",10); row.put("rejectedCount",0); row.put("duplicateCount",0);
        return row;
    }
    private Map<String,Object> errors(Object cursor) {
        var page=new LinkedHashMap<String,Object>();
        page.put("items",List.of()); page.put("pageSize",20); page.put("nextCursor",cursor);
        return page;
    }
    private void fails(ErrorCode code, Runnable work) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(DataApiException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }

    @Test void duplicateBatchesAreRejected() {
        // 同一页不能把同一个批次重复返回。
        when(client.listBatches(anyMap(),same(scope))).thenReturn(
                Map.of("items",List.of(row(),row()),"page",1,"pageSize",20,"total",2));
        fails(ErrorCode.SYSTEM_UNAVAILABLE,()->adapter.findPage(query(),scope));
    }
    @Test void totalCannotExceedExplicitBatchGrants() {
        when(client.listBatches(anyMap(),same(scope))).thenReturn(
                Map.of("items",List.of(row()),"page",1,"pageSize",20,"total",3));
        fails(ErrorCode.SYSTEM_UNAVAILABLE,()->adapter.findPage(query(),scope));
    }
    @Test void blankSourceInAProviderReplyIsRejected() {
        var row=row(); row.put("source"," ");
        when(client.getBatch(ID,scope)).thenReturn(row);
        fails(ErrorCode.SYSTEM_UNAVAILABLE,()->adapter.findById(ID,scope));
    }
    @Test void numericEnumInAProviderReplyIsRejected() {
        var row=row(); row.put("status",0);
        when(client.getBatch(ID,scope)).thenReturn(row);
        fails(ErrorCode.SYSTEM_UNAVAILABLE,()->adapter.findById(ID,scope));
    }
    @Test void privateErrorSummaryIsNotReturned() {
        var row=row(); row.put("errorSummary","private SQL diagnostic");
        when(client.getBatch(ID,scope)).thenReturn(row);
        assertThat(adapter.findById(ID,scope).orElseThrow().errorSummary())
                .isEqualTo("Batch processing reported an error.");
    }
    @Test void requiredNullableCursorMustStillBePresent() {
        var page=errors(null); page.remove("nextCursor");
        when(client.listErrors(ID,null,20,scope)).thenReturn(page);
        fails(ErrorCode.SYSTEM_UNAVAILABLE,()->adapter.findErrors(ID,null,20,scope));
    }
    @Test void emptyErrorPageCannotClaimThereIsAnotherPage() {
        when(client.listErrors(ID,null,20,scope)).thenReturn(errors("next"));
        fails(ErrorCode.SYSTEM_UNAVAILABLE,()->adapter.findErrors(ID,null,20,scope));
    }
    @Test void cursorAndCurrentScopeAreForwardedUnchanged() {
        when(client.listErrors(ID,"opaque-cursor",20,scope)).thenReturn(errors(null));
        assertThat(adapter.findErrors(ID,"opaque-cursor",20,scope).nextCursor()).isNull();
        verify(client).listErrors(ID,"opaque-cursor",20,scope);
    }
    @Test void qualityUsesTheConcretePolicyAndWholeRetainedRange() {
        // 验证查询入口实际使用时间策略，而不只是单独测试策略类。
        when(client.qualitySummary(anyMap(),same(scope))).thenAnswer(invocation -> {
            Map<String,Object> filters=invocation.getArgument(0);
            assertThat(filters).containsEntry("from","2026-01-01T00:00:00Z")
                    .containsEntry("to","2026-10-25T00:00:00Z").doesNotContainKey("source");
            return Map.of("asOf","2026-10-25T00:00:00Z","acceptedCount",10,
                    "rejectedCount",0,"duplicateCount",0,"unmappedDeviceCount",0,"invalidCoordinateCount",0);
        });
        assertThat(adapter.findQuality(new QualityQuery(null,null,null),scope).acceptedCount()).isEqualTo(10);
    }
    @Test void workerFailureIsMappedWithoutAFakeSuccess() {
        when(client.getBatch(ID,scope)).thenThrow(new IngestionQueryAdapter.Failure("DATA_QUERY_INVALID"));
        fails(ErrorCode.DATA_INVALID_FILTER,()->adapter.findById(ID,scope));
    }
    @Test void sourceSpecificQualityCannotReturnAnotherSource() {
        when(client.qualitySummary(anyMap(),same(scope))).thenReturn(
                Map.of("asOf","2026-10-25T00:00:00Z","source","OTHER","acceptedCount",10,
                    "rejectedCount",0,"duplicateCount",0,"unmappedDeviceCount",0,"invalidCoordinateCount",0));
        fails(ErrorCode.SYSTEM_UNAVAILABLE,()->adapter.findQuality(new QualityQuery("PLATE_A",null,null),scope));
    }
}
