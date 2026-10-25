package com.xxplatform.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.api.DataServiceBindings;
import com.xxplatform.api.client.ingestion.IngestionQueryAdapter;
import com.xxplatform.api.client.plate.PlateQueryAdapter;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.*;
import com.xxplatform.api.service.ingestion.*;
import com.xxplatform.api.service.plate.*;
import com.xxplatform.api.service.plate.PlateQueryService.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises normal-profile HTTP -> DEV2 orchestration -> supplied upstream ports. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=integration")
class DataIntegrationTests {
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper mapper;
    @Autowired CaptureExportSourceAdapter exports;
    @MockitoBean DataServiceBindings.SharedAccess access;
    @MockitoBean IngestionDomainGateway.AtomicSubmission submissions;
    @MockitoBean IngestionQueryAdapter.Client ingestion;
    @MockitoBean IngestionQueryAdapter.WindowPolicy windows;
    @MockitoBean PlateQueryAdapter.Client plate;
    @MockitoBean PlateQueryAdapter.TargetLookup targets;
    @MockitoBean CaptureExportSourceAdapter.ContextLookup contexts;
    private final UUID batch = new UUID(0,1), actor = new UUID(0,2), task = new UUID(0,3);
    private final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    private IngestionQueryService.ReadScope scope;
    private Authority authority;
    private HttpHeaders headers() {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-CSRF-Token", "integration-csrf-token");
        h.set("Idempotency-Key", "integration-idempotency-key");
        return h;
    }
    @BeforeEach void setUp() {
        scope = new IngestionQueryService.ReadScope(actor, "scope-v1", Set.of(batch));
        authority = new Authority(actor,"scope-v1",List.of(new ScopeTuple("PLATE",batch,true)),
                new QueryBudget(Duration.ofDays(31),Duration.ofSeconds(3)));
        when(access.requireReadScope()).thenReturn(scope);
        when(access.requireAccess(anyString(),nullable(String.class))).thenReturn(authority);
    }
    private Map<String,Object> batchRow() {
        return Map.of("batchId", batch.toString(), "source","PLATE_A",
                "mappingVersion","map-v1","dedupVersion","dedup-v1",
                "status","SUCCESS","createdAt",now.toString(),
                "acceptedCount",0,"rejectedCount",0,"duplicateCount",0);
    }
    @Test void ingestionReadsUseSuppliedClientAndFreshScope() throws Exception {
        when(ingestion.listBatches(anyMap(),same(scope))).thenReturn(
                Map.of("items",List.of(batchRow()),"page",1,"pageSize",20,"total",1));
        var response = rest.getForEntity("/ingestion-batches",String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(response.getBody()).at("/data/items/0/batchId").asText()).isEqualTo(batch.toString());
        verify(ingestion).listBatches(anyMap(),same(scope));
        verify(access).requireReadScope();
    }
    @Test void detailAndErrorsUseRealBindings() {
        when(ingestion.getBatch(batch,scope)).thenReturn(batchRow());
        var page = new HashMap<String,Object>();
        page.put("items",List.of()); page.put("nextCursor",null); page.put("pageSize",20);
        when(ingestion.listErrors(batch,"opaque",20,scope)).thenReturn(page);
        assertThat(rest.getForEntity("/ingestion-batches/"+batch,String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/ingestion-batches/"+batch+"/errors?cursor=opaque",String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(ingestion).listErrors(batch,"opaque",20,scope);
    }
    @Test void qualityResolvesServerWindowBeforeCallingDataSide() {
        when(windows.resolve(null,null,scope)).thenReturn(new IngestionQueryAdapter.Window(now.minusDays(1),now));
        when(ingestion.qualitySummary(anyMap(),same(scope))).thenReturn(
                Map.of("asOf",now.toString(),"acceptedCount",0,"rejectedCount",0,
                        "duplicateCount",0,"unmappedDeviceCount",0,"invalidCoordinateCount",0));
        assertThat(rest.getForEntity("/ingestion/quality-summary",String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(ingestion).qualitySummary(argThat(f -> f.containsKey("from") && f.containsKey("to")),same(scope));
    }
    @Test void captureForwardsCursorBudgetAndTrustedAuthority() {
        when(plate.search(same(authority),anyMap(),eq("opaque"),eq(Duration.ofSeconds(3))))
                .thenReturn(new CaptureSearchResponse(List.of(),null,20));
        var body = "{\"dataType\":\"PLATE\",\"cursor\":\"opaque\",\"timeRange\":{\"from\":\"2026-10-24T10:00:00Z\",\"to\":\"2026-10-24T11:00:00Z\"}}";
        assertThat(rest.postForEntity("/captures/search",new HttpEntity<>(body,headers()),String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(access).requireAccess("capture:read","integration-csrf-token");
        verify(plate).search(same(authority),anyMap(),eq("opaque"),eq(Duration.ofSeconds(3)));
    }
    @Test void mapDelegatesToDataSide() {
        var collection = new GeoFeatureCollection();
        collection.setType(GeoFeatureCollection.TypeEnum.FEATURE_COLLECTION); collection.setFeatures(List.of());
        collection.setAggregated(false); collection.setTruncated(false); collection.setMaxFeatures(1000); collection.setAsOf(now);
        when(plate.map(same(authority),any(),eq(Duration.ofSeconds(3)))).thenReturn(collection);
        assertThat(rest.getForEntity("/map/features?bbox=116,39,117,40&zoom=12&dataType=PLATE",String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(access).requireAccess("map:read",null);
    }
    @Test void submissionCallsSharedAtomicBridge() throws Exception {
        when(access.requireWriteAccess("integration-csrf-token")).thenReturn(
                new IngestionBatchSubmissionService.AuthorizedActor(actor,"scope-v1"));
        when(submissions.submit(any(),eq("createIngestionBatch"),eq("integration-idempotency-key"),
                isNull(),any(),any())).thenReturn(new IngestionDomainGateway.Committed(batch,task,TaskStatus.QUEUED,now));
        String body = "{\"source\":\"PLATE_A\",\"sourceFileId\":\""+batch+"\",\"mappingVersion\":\"map-v1\",\"dedupVersion\":\"dedup-v1\"}";
        var response = rest.postForEntity("/ingestion-batches",new HttpEntity<>(body,headers()),String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(mapper.readTree(response.getBody()).at("/data/taskId").asText()).isEqualTo(task.toString());
        verify(submissions).submit(argThat(a -> a.principalId().equals(actor)),eq("createIngestionBatch"),
                eq("integration-idempotency-key"),isNull(),any(),any());
    }
    @Test void permissionFailureSurvivesBridgeAndKeepsContractEnvelope() throws Exception {
        when(access.requireReadScope()).thenThrow(new DataApiException(ErrorCode.PERMISSION_DENIED,null,"Denied"));
        var response = rest.getForEntity("/ingestion-batches",String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var json = mapper.readTree(response.getBody());
        assertThat(json.path("code").asText()).isEqualTo("PERMISSION_DENIED");
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(json.path("traceId").asText()).isEqualTo(response.getHeaders().getFirst("X-Trace-Id"));
        verifyNoInteractions(ingestion);
    }
    @Test void exportBeanConsumesSharedContextAndTargetLookupWithoutPaging() {
        var query = new CaptureQuery("PLATE",now.minusHours(1),now,null,null,null,
                "old-page",20,"captureTime","DESC",Map.of("plateNumber","SYNTHETIC"));
        when(contexts.load(batch,authority)).thenReturn(new CaptureExportSourceAdapter.FrozenContext(
                batch,"CAPTURE_SEARCH",now.plusHours(1),query));
        when(targets.resolve(authority,"SYNTHETIC")).thenReturn(Optional.of(task));
        var source = exports.prepare("integration-csrf-token",batch,"CSV",List.of("captureId"),"test");
        assertThat(source.query().filters()).containsEntry("targetIds",List.of(task))
                .doesNotContainKeys("pageSize","cursor","plateNumber");
        verify(access).requireAccess("capture:export","integration-csrf-token");
        verify(contexts).load(batch,authority);
        verify(targets).resolve(authority,"SYNTHETIC");
    }

    @Test void replayAndBackfillReachSharedSubmissionWithCorrectOperation() {
        when(access.requireReplayAccess("integration-csrf-token",batch)).thenReturn(
                new IngestionBatchReplayService.ReplayActor(actor,"scope-v1"));
        when(access.requireBackfillAccess("integration-csrf-token","PLATE_A")).thenReturn(
                new IngestionBackfillService.BackfillActor(actor,"scope-v1"));
        when(submissions.submit(any(),anyString(),anyString(),isNull(),any(),any()))
                .thenReturn(new IngestionDomainGateway.Committed(new UUID(0,10),task,TaskStatus.QUEUED,now));
        assertThat(rest.postForEntity("/ingestion-batches/"+batch+"/replay",new HttpEntity<>(
                "{\"reason\":\"integration replay test\"}",headers()),String.class)
                .getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(rest.postForEntity("/ingestion/backfills",new HttpEntity<>(
                "{\"source\":\"PLATE_A\",\"reason\":\"integration backfill test\",\"timeRange\":{\"from\":\"2026-10-24T10:00:00Z\",\"to\":\"2026-10-24T11:00:00Z\"}}",
                headers()),String.class).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(submissions).submit(any(),eq("replayIngestionBatch"),anyString(),isNull(),
                argThat(r -> batch.equals(r.sourceBatchId())),any());
        verify(submissions).submit(any(),eq("createIngestionBackfill"),anyString(),isNull(),any(),any());
    }
    @Test void upstreamCursorFailureIsNotWrappedAsAnInternalError() throws Exception {
        when(ingestion.getBatch(batch,scope)).thenReturn(batchRow());
        when(ingestion.listErrors(batch,"bad-cursor",20,scope))
                .thenThrow(new IngestionQueryAdapter.Failure("DATA_CURSOR_INVALID"));
        var response = rest.getForEntity("/ingestion-batches/"+batch+"/errors?cursor=bad-cursor",String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.readTree(response.getBody()).path("code").asText()).isEqualTo("DATA_CURSOR_INVALID");
    }
}
