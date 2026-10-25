package com.xxplatform.api.plate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xxplatform.api.api.*;
import com.xxplatform.api.common.ApiExceptionHandler;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.*;
import com.xxplatform.api.service.ingestion.DataApiException;
import com.xxplatform.api.service.plate.PlateQueryService;
import com.xxplatform.api.service.plate.PlateQueryService.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlateQueryServiceTests {
    private static final String CSRF = "synthetic-plate-csrf-test";
    private static final String BODY = "{\"dataType\":\"PLATE\",\"timeRange\":{\"from\":\"2026-10-24T11:00:00Z\",\"to\":\"2026-10-24T12:00:00Z\"}}";
    private final ReadAccess access = mock(ReadAccess.class);
    private final QueryGateway gateway = mock(QueryGateway.class);
    private final Authority actor = new Authority(id(900), "synthetic-v1", List.of(new ScopeTuple("PLATE", id(1), false)),
            new QueryBudget(Duration.ofDays(31), Duration.ofSeconds(5)));
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .registerModule(new SimpleModule().addDeserializer(CaptureSearchRequest.class, new CaptureSearchInputDeserializer()))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach void setup() {
        when(access.requireAccess(anyString(), nullable(String.class))).thenReturn(actor);
        var empty = new CaptureSearchResponse(); empty.setItems(List.of()); empty.setNextCursor(null); empty.setPageSize(20);
        when(gateway.search(any(), any(), any())).thenReturn(empty);
        var collection = mapper.convertValue(java.util.Map.of("type", "FeatureCollection", "features", List.of(),
                "aggregated", false, "truncated", false, "maxFeatures", 2000, "asOf", "2026-10-24T12:00:00Z"), GeoFeatureCollection.class);
        when(gateway.map(any(), any(), any())).thenReturn(collection);
    }
    private MockMvc mvc(PlateQueryService service) {
        return MockMvcBuilders.standaloneSetup(new CaptureController(service, mapper), new MapController(service, mapper))
                .setControllerAdvice(new DataApiExceptionHandler(), new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).addFilters(new RequestIdFilter()).build();
    }
    private MockMvc mvc() { return mvc(new PlateQueryService(access, gateway)); }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body) {
        return post("/captures/search").header("X-CSRF-Token", CSRF).contentType(MediaType.APPLICATION_JSON).content(body);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder mapRequest() {
        return get("/map/features").param("bbox", "116,39,117,40").param("zoom", "12").param("dataType", "PLATE");
    }
    @Test void emptyAuthorizedPagePreservesRequiredNullCursorAndTrace() throws Exception {
        var result = mvc().perform(request(BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.pageSize").value(20)).andReturn();
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).path("traceId").asText())
                .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        verify(access).requireAccess("capture:read", CSRF);
        verify(gateway).search(eq(actor), argThat(q -> q.sortBy().equals("captureTime") && q.sortOrder().equals("DESC")), eq(Duration.ofSeconds(5)));
    }
    @Test void filterScopeAndOpaqueCursorReachTheGatewayWithoutReplacingTupleScope() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(BODY);
        body.put("cursor", "opaque-continuation"); body.putArray("siteIds").add(id(999).toString());
        body.putArray("targetIds").add(id(15).toString()); body.put("plateColor", "synthetic-blue");
        mvc().perform(request(body.toString())).andExpect(status().isOk());
        verify(gateway).search(eq(actor), argThat(q -> q.siteIds().equals(List.of(id(999)))
                && q.targetIds().equals(List.of(id(15))) && q.cursor().equals("opaque-continuation")
                && q.exactFilters().get("plateColor").equals("synthetic-blue")), any());
        assertThat(actor.grants()).containsExactly(new ScopeTuple("PLATE", id(1), false));
    }
    @Test void plateProjectionPreservesRequiredNullsAndOmitsUnprovidedOptionalFields() throws Exception {
        String rowJson = "{\"captureId\":\"" + id(50) + "\",\"dataType\":\"PLATE\",\"targetType\":\"PLATE\","
                + "\"targetId\":null,\"deviceId\":null,\"siteId\":null,\"longitude\":null,\"latitude\":null,"
                + "\"captureTime\":\"2026-10-24T11:00:00Z\",\"ingestedAt\":\"2026-10-24T11:01:00Z\","
                + "\"source\":\"SYNTHETIC_PLATE\",\"status\":\"VALID\",\"displayTarget\":\"SYNTH-****\","
                + "\"isMasked\":true,\"plate\":{\"plateMasked\":\"SYNTH-****\"}}";
        var row = mapper.readValue(rowJson, PlateCaptureRecord.class);
        var page = new CaptureSearchResponse(); page.setItems(List.of(row)); page.setPageSize(20); page.setNextCursor(null);
        when(gateway.search(any(), any(), any())).thenReturn(page);
        mvc().perform(request(BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].targetId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[0].longitude").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[0].plate.plateColor").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].plate.vehicleImageAssetId").doesNotExist());
        var detail = new CaptureDetail(); detail.setRecord(row); detail.setRelatedProfileIds(List.of());
        detail.setCanDownloadImage(false); detail.setAvailableActions(List.of());
        when(gateway.get(eq(actor), eq(id(50)), any())).thenReturn(detail);
        mvc().perform(get("/captures/" + id(50))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record.captureId").value(id(50).toString()))
                .andExpect(jsonPath("$.data.record.longitude").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.record.plate.plateColor").doesNotExist());
    }
    @Test void rawExactIdentifiersAreNotReplacedWithMaskedDisplayText() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(BODY);
        body.put("plateNumber", "SYNTHETIC-RAW-PLATE");
        mvc().perform(request(body.toString())).andExpect(status().isOk());
        verify(gateway).search(eq(actor), argThat(q -> q.exactFilters().get("plateNumber").equals("SYNTHETIC-RAW-PLATE")), any());
    }
    @Test void mapForwardsBoundingBoxAndIntersectionsAsTypedInputs() throws Exception {
        mvc().perform(mapRequest().param("siteId", id(1).toString()).param("siteIds", id(2).toString())
                        .param("aggregation", "AUTO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.type").value("FeatureCollection"));
        verify(access).requireAccess("map:read", null);
        verify(gateway).map(eq(actor), argThat(q -> q.bounds().minLon() == 116 && q.bounds().minLat() == 39
                && q.siteId().equals(id(1)) && q.siteIds().equals(List.of(id(2))) && q.limit() == 2000), any());
    }
    @Test void actualUnavailablePortsDoNotReturnInventedSearchOrMapResults() throws Exception {
        var client = mvc(PlateQueryService.unavailable());
        client.perform(request(BODY)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SYSTEM_UNAVAILABLE")).andExpect(header().string("Retry-After", "60"));
        client.perform(mapRequest()).andExpect(status().isServiceUnavailable());
        client.perform(get("/captures/" + id(50))).andExpect(status().isServiceUnavailable());
    }
    @Test void invisibleCaptureDetailsAre404UnderTheCurrentScope() throws Exception {
        when(gateway.get(eq(actor), eq(id(50)), any())).thenThrow(new DataApiException(
                ErrorCode.DATA_NOT_FOUND, null, "Capture not found"));
        mvc().perform(get("/captures/" + id(50))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_NOT_FOUND"));
        verify(access).requireAccess("capture:read", null);
    }
    @ParameterizedTest @ValueSource(strings = {"[]", "null", "{", "{}"})
    void invalidRootBodiesAre400(String body) throws Exception {
        mvc().perform(request(body)).andExpect(status().isBadRequest()); verifyNoInteractions(access, gateway);
    }
    @ParameterizedTest @ValueSource(strings = {"pageSize", "siteIds", "targetIds", "sortOrder", "plateNumber"})
    void explicitNullOptionalFieldsAreRejected(String field) throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(BODY); body.putNull(field);
        mvc().perform(request(body.toString())).andExpect(status().isBadRequest()); verifyNoInteractions(access, gateway);
    }
    @Test void wrongBranchFieldsOrClientIdentityAreRejected() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(BODY); body.put("imsi", "synthetic");
        mvc().perform(request(body.toString())).andExpect(status().isBadRequest());
        body.remove("imsi"); body.put("clientPrincipalId", id(999).toString());
        mvc().perform(request(body.toString())).andExpect(status().isBadRequest()); verifyNoInteractions(access, gateway);
    }
    @Test void invalidPageAndEmptyTargetListAreRejected() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(BODY); body.put("pageSize", 101);
        mvc().perform(request(body.toString())).andExpect(status().isBadRequest());
        body.remove("pageSize"); body.putArray("targetIds");
        mvc().perform(request(body.toString())).andExpect(status().isBadRequest()); verifyNoInteractions(access, gateway);
    }
    @Test void missingCsrfIs400() throws Exception {
        mvc().perform(post("/captures/search").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isBadRequest());
        verifyNoInteractions(access, gateway);
    }
    @Test void serverWindowBudgetIsAppliedAfterAuthorization() throws Exception {
        mvc().perform(request(BODY.replace("2026-10-24T11:00:00Z", "2026-08-24T11:00:00Z")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DATA_QUERY_TOO_BROAD"));
        verifyNoInteractions(gateway);
    }
    @ParameterizedTest @ValueSource(strings = {"181,39,182,40", "116,91,117,92", "117,39,116,40", "NaN,39,117,40", "116,39,116,40"})
    void invalidBoundingBoxesAreRejected(String bbox) throws Exception {
        mvc().perform(get("/map/features").param("bbox", bbox).param("zoom", "12").param("dataType", "PLATE"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(access, gateway);
    }
    @Test void mapDoesNotSilentlyTruncateOverLimitResults() throws Exception {
        var over = mapper.convertValue(java.util.Map.of("type", "FeatureCollection", "features", List.of(),
                "aggregated", false, "truncated", false, "maxFeatures", 2000, "asOf", "2026-10-24T12:00:00Z"), GeoFeatureCollection.class);
        over.setFeatures(java.util.Collections.nCopies(3, new GeoFeature()));
        when(gateway.map(any(), any(), any())).thenReturn(over);
        mvc().perform(mapRequest().param("limit", "2")).andExpect(status().isUnprocessableEntity());
    }
    @Test void scopeIsRefreshedForEveryContinuation() throws Exception {
        var newer = new Authority(actor.principalId(), "new-scope", List.of(), actor.budget());
        when(access.requireAccess("capture:read", CSRF)).thenReturn(actor, newer);
        var client = mvc(); client.perform(request(BODY)).andExpect(status().isOk()); client.perform(request(BODY)).andExpect(status().isOk());
        verify(gateway).search(eq(newer), any(), any());
    }
    @ParameterizedTest @EnumSource(value = ErrorCode.class, names = {"AUTH_REQUIRED", "AUTH_CSRF_INVALID", "PERMISSION_DENIED", "PERMISSION_DATA_SCOPE"})
    void accessFailuresNeverReachTheDataGateway(ErrorCode code) throws Exception {
        when(access.requireAccess(anyString(), nullable(String.class))).thenThrow(new DataApiException(code, null, "Synthetic access failure"));
        mvc().perform(request(BODY)).andExpect(status().is(code.httpStatus())); verifyNoInteractions(gateway);
    }
    @ParameterizedTest @EnumSource(value = ErrorCode.class, names = {"DATA_CURSOR_INVALID", "DATA_QUERY_TOO_BROAD", "RATE_LIMITED", "SYSTEM_UNAVAILABLE"})
    void dataFailuresAreErrorsInsteadOfSuccessfulPartialPages(ErrorCode code) throws Exception {
        when(gateway.search(any(), any(), any())).thenThrow(new DataApiException(code, null, "Synthetic query failure"));
        mvc().perform(request(BODY)).andExpect(status().is(code.httpStatus())).andExpect(jsonPath("$.code").value(code.name()));
    }
}
