package com.xxplatform.api.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xxplatform.api.api.*;
import com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery;
import com.xxplatform.api.common.*;
import com.xxplatform.api.generated.model.*;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.service.ingestion.*;
import com.xxplatform.api.service.ingestion.IngestionBackfillService.*;
import java.time.OffsetDateTime;
import java.util.UUID;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IngestionBackfillTests {
    private final BackfillAccess access = mock(BackfillAccess.class);
    private final BackfillGateway gateway = mock(BackfillGateway.class);
    private final BackfillActor actor = new BackfillActor(id(900), "synthetic-scope-v1");
    private static final String CSRF = "synthetic-backfill-csrf-test";
    private static final String KEY = "synthetic-backfill-key-001";
    private static final String PATH = "/ingestion/backfills";
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .registerModule(new SimpleModule().addDeserializer(BackfillInput.class, new IngestionBackfillInputDeserializer()))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final String BODY = "{\"source\":\"PLATE_TEST_A\",\"reason\":\"Synthetic backfill verification\","
            + "\"timeRange\":{\"from\":\"2026-10-24T19:00:00+08:00\",\"to\":\"2026-10-24T12:00:00Z\"}}";

    @BeforeEach void setup() {
        when(access.requireBackfillAccess(anyString(), anyString())).thenReturn(actor);
        when(gateway.createOrReplay(any(), anyString(), anyString(), nullable(UUID.class), any()))
                .thenReturn(new BackfillReceipt(id(250), TaskStatus.PENDING, OffsetDateTime.parse("2026-10-24T12:00:00Z")));
    }
    private MockMvc mvc(IngestionBackfillService service) {
        var reads = new IngestionQueryService(InMemoryIngestionBatchQuery::demoScope,
                new InMemoryIngestionBatchQuery(InMemoryIngestionBatchQuery.demoRows()));
        return MockMvcBuilders.standaloneSetup(new IngestionController(reads, mapper,
                IngestionBatchSubmissionService.unavailable(), IngestionBatchReplayService.unavailable(), service))
                .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }
    private MockMvc mvc() { return mvc(new IngestionBackfillService(access, gateway)); }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body) {
        return post(PATH).header("X-CSRF-Token", CSRF).header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
    @Test void forwardsUtcWindowAndApprovalAndReturnsTypedReceipt() throws Exception {
        var result = mvc().perform(request(BODY).header("X-Approval-Id", id(350).toString()))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskId").value(id(250).toString()))
                .andExpect(jsonPath("$.data.resourceId").doesNotExist())
                .andExpect(jsonPath("$.data.resourceType").doesNotExist()).andReturn();
        var command = new BackfillCommand("PLATE_TEST_A", OffsetDateTime.parse("2026-10-24T11:00:00Z"),
                OffsetDateTime.parse("2026-10-24T12:00:00Z"), "Synthetic backfill verification");
        verify(gateway).createOrReplay(actor, IngestionBackfillService.OPERATION_ID, KEY, id(350), command);
        var json = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.path("traceId").asText()).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        assertThat(json.path("data").size()).isEqualTo(3);
    }
    @Test void unavailableConfigurationReturns503WithoutAReceipt() throws Exception {
        mvc(IngestionBackfillService.unavailable()).perform(request(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("SYSTEM_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void repeatedCallsReauthorizeBeforeTheDurableGateway() throws Exception {
        var newer = new BackfillActor(actor.principalId(), "new-scope");
        when(access.requireBackfillAccess(CSRF, "PLATE_TEST_A")).thenReturn(actor, newer);
        var client = mvc();
        client.perform(request(BODY)).andExpect(status().isAccepted());
        client.perform(request(BODY)).andExpect(status().isAccepted());
        verify(access, times(2)).requireBackfillAccess(CSRF, "PLATE_TEST_A");
        verify(gateway).createOrReplay(eq(newer), eq(IngestionBackfillService.OPERATION_ID), eq(KEY), isNull(), any());
    }
    @ParameterizedTest @ValueSource(strings = {"source", "reason", "timeRange"})
    void requiredFieldsCannotBeOmitted(String field) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(BODY);
        node.remove(field);
        mvc().perform(request(node.toString())).andExpect(status().isBadRequest());
        verifyNoInteractions(access, gateway);
    }
    @ParameterizedTest @ValueSource(strings = {"source", "reason"})
    void blankTextIsRejected(String field) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(BODY);
        node.put(field, "          ");
        mvc().perform(request(node.toString())).andExpect(status().isBadRequest());
        verifyNoInteractions(access, gateway);
    }
    @ParameterizedTest @ValueSource(strings = {"2026-10-24T12:00:00", "1761307200", "invalid"})
    void timezoneIsMandatory(String value) throws Exception {
        mvc().perform(request(BODY.replace("2026-10-24T19:00:00+08:00", value)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }
    @ParameterizedTest @ValueSource(strings = {"2026-10-24T11:00:00Z", "2026-10-24T10:00:00Z"})
    void equalOrReversedInstantsAreRejected(String value) throws Exception {
        mvc().perform(request(BODY.replace("2026-10-24T12:00:00Z", value))).andExpect(status().isBadRequest());
        verifyNoInteractions(access, gateway);
    }
    @Test void unknownFieldsAndExplicitNullAreRejected() throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(BODY);
        node.put("clientPrincipalId", "forged");
        mvc().perform(request(node.toString())).andExpect(status().isBadRequest());
        node.remove("clientPrincipalId"); node.putNull("source");
        mvc().perform(request(node.toString())).andExpect(status().isBadRequest());
        verifyNoInteractions(access, gateway);
    }
    @Test void missingHeaderIs400AndMalformedApprovalIs400() throws Exception {
        mvc().perform(post(PATH).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        mvc().perform(request(BODY).header("X-Approval-Id", "not-a-uuid")).andExpect(status().isBadRequest());
        verifyNoInteractions(access, gateway);
    }
    @ParameterizedTest @EnumSource(value = ErrorCode.class, names = {"AUTH_REQUIRED", "AUTH_CSRF_INVALID", "PERMISSION_DENIED", "PERMISSION_DATA_SCOPE"})
    void accessFailuresStopCreation(ErrorCode code) throws Exception {
        when(access.requireBackfillAccess(anyString(), anyString())).thenThrow(new DataApiException(code, null, "Synthetic access failure"));
        mvc().perform(request(BODY)).andExpect(status().is(code.httpStatus())).andExpect(jsonPath("$.code").value(code.name()));
        verifyNoInteractions(gateway);
    }
    @ParameterizedTest @EnumSource(value = ErrorCode.class, names = {"REQUIREMENT_PENDING", "IDEMPOTENCY_CONFLICT", "TASK_CONFLICT", "RESOURCE_CONFLICT", "SYSTEM_UNAVAILABLE"})
    void sharedApprovalSourceAndTaskFailuresRemainErrors(ErrorCode code) throws Exception {
        when(gateway.createOrReplay(any(), anyString(), anyString(), nullable(UUID.class), any()))
                .thenThrow(new DataApiException(code, null, "Synthetic gateway failure"));
        mvc().perform(request(BODY)).andExpect(status().is(code.httpStatus())).andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }
}
