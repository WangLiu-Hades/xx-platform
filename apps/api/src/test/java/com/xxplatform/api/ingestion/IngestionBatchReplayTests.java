package com.xxplatform.api.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xxplatform.api.api.IngestionController;
import com.xxplatform.api.api.IngestionReplayInputDeserializer;
import com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery;
import com.xxplatform.api.common.ApiExceptionHandler;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.generated.model.ReplayInput;
import com.xxplatform.api.generated.model.TaskStatus;
import com.xxplatform.api.service.ingestion.IngestionQueryService;
import com.xxplatform.api.service.ingestion.IngestionBatchReplayService;
import com.xxplatform.api.service.ingestion.IngestionBatchReplayService.ReplayAccess;
import com.xxplatform.api.service.ingestion.IngestionBatchReplayService.ReplayActor;
import com.xxplatform.api.service.ingestion.IngestionBatchReplayService.ReplayCommand;
import com.xxplatform.api.service.ingestion.IngestionBatchReplayService.ReplayGateway;
import com.xxplatform.api.service.ingestion.IngestionBatchReplayService.ReplayReceipt;
import com.xxplatform.api.service.ingestion.IngestionBatchSubmissionService;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery.id;
import static com.xxplatform.api.service.ingestion.IngestionBatchReplayService.OPERATION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IngestionBatchReplayTests {
    private static final UUID SOURCE_BATCH = id(1);
    private static final UUID APPROVAL = id(350);
    private static final String PATH = "/ingestion-batches/" + SOURCE_BATCH + "/replay";
    private static final String CSRF = "synthetic-replay-csrf-unit-test";
    private static final String KEY = "synthetic-replay-idempotency-001";
    private static final String REASON = "Synthetic replay verification";
    private final ReplayAccess access = mock(ReplayAccess.class);
    private final ReplayGateway gateway = mock(ReplayGateway.class);
    private final ReplayActor actor = new ReplayActor(id(900), "synthetic-scope-v1");
    private final ReplayCommand command = new ReplayCommand(
            REASON, "synthetic-map-v2", "synthetic-dedup-v2");
    private final ReplayReceipt receipt = new ReplayReceipt(id(230), TaskStatus.PENDING,
            OffsetDateTime.parse("2026-10-24T20:00:00+08:00"));
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .registerModule(new SimpleModule().addDeserializer(
                    ReplayInput.class, new IngestionReplayInputDeserializer()))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setupPorts() {
        when(access.requireReplayAccess(anyString(), any())).thenReturn(actor);
        when(gateway.createOrReplay(any(), anyString(), any(), anyString(), nullable(UUID.class), any()))
                .thenReturn(receipt);
    }

    private MockMvc mvc(IngestionBatchReplayService service) {
        var query = new InMemoryIngestionBatchQuery(InMemoryIngestionBatchQuery.demoRows());
        var reads = new IngestionQueryService(InMemoryIngestionBatchQuery::demoScope, query);
        var controller = new IngestionController(
                reads, mapper, IngestionBatchSubmissionService.unavailable(), service);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .addFilters(new RequestIdFilter()).build();
    }

    private MockMvc mvc() {
        return mvc(new IngestionBatchReplayService(access, gateway));
    }

    private String validBody() throws Exception {
        return mapper.writeValueAsString(command);
    }

    private MockHttpServletRequestBuilder request(String body) {
        return post(PATH).header("X-CSRF-Token", CSRF).header("Idempotency-Key", KEY)
                .header("X-Approval-Id", APPROVAL.toString())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void trustedGatewayReceiptUsesTaskCreatedAndForwardsSourceAndApproval() throws Exception {
        var result = mvc().perform(request(validBody())).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskId").value(id(230).toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-24T12:00:00Z"))
                .andExpect(jsonPath("$.data.batchId").doesNotExist()).andReturn();
        var json = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.path("traceId").asText()).isNotBlank()
                .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        assertThat(json.path("data").size()).isEqualTo(3);
        verify(access).requireReplayAccess(CSRF, SOURCE_BATCH);
        verify(gateway).createOrReplay(actor, OPERATION_ID, SOURCE_BATCH, KEY, APPROVAL, command);
    }

    @Test
    void newBatchReferenceIsReturnedWhenTheTrustedGatewayProvidesIt() throws Exception {
        when(gateway.createOrReplay(any(), anyString(), any(), anyString(), nullable(UUID.class), any()))
                .thenReturn(new ReplayReceipt(id(230), TaskStatus.PENDING,
                        OffsetDateTime.parse("2026-10-24T12:00:00Z"), id(30)));
        mvc().perform(request(validBody())).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.resourceId").value(id(30).toString()))
                .andExpect(jsonPath("$.data.resourceType").value("INGESTION"));
    }

    @Test
    void omittedVersionsRemainOmittedForTrustedSourceVersionResolution() throws Exception {
        mvc().perform(request("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isAccepted());
        verify(gateway).createOrReplay(actor, OPERATION_ID, SOURCE_BATCH, KEY, APPROVAL,
                new ReplayCommand(REASON, null, null));
    }

    @Test
    void missingApprovalIsDelegatedToTheTrustedPolicyAndCannotBeSilentlyApproved() throws Exception {
        when(gateway.createOrReplay(any(), anyString(), any(), anyString(), isNull(), any()))
                .thenThrow(new DataApiException(ErrorCode.PERMISSION_DENIED, null,
                        "Synthetic independent approval is required"));
        mvc().perform(post(PATH).header("X-CSRF-Token", CSRF).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verify(gateway).createOrReplay(actor, OPERATION_ID, SOURCE_BATCH, KEY, null, command);
    }

    @Test
    void repeatedCallsResolveFreshAccessAndDelegateWithoutLocalIdempotency() throws Exception {
        ReplayActor newer = new ReplayActor(actor.principalId(), "new-scope-version");
        when(access.requireReplayAccess(CSRF, SOURCE_BATCH)).thenReturn(actor, newer);
        MockMvc client = mvc();
        client.perform(request(validBody())).andExpect(status().isAccepted());
        client.perform(request(validBody())).andExpect(status().isAccepted());
        verify(access, times(2)).requireReplayAccess(CSRF, SOURCE_BATCH);
        verify(gateway).createOrReplay(actor, OPERATION_ID, SOURCE_BATCH, KEY, APPROVAL, command);
        verify(gateway).createOrReplay(newer, OPERATION_ID, SOURCE_BATCH, KEY, APPROVAL, command);
    }

    @Test
    void unavailableServiceReturns503WithNoFakeTaskReference() throws Exception {
        mvc(IngestionBatchReplayService.unavailable()).perform(request(validBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("SYSTEM_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"X-CSRF-Token", "Idempotency-Key"})
    void missingHeadersAreValidationErrors(String missing) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        if (!"X-CSRF-Token".equals(missing)) headers.set("X-CSRF-Token", CSRF);
        if (!"Idempotency-Key".equals(missing)) headers.set("Idempotency-Key", KEY);
        mvc().perform(post(PATH).headers(headers).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "                "})
    void invalidCsrfShapeIsRejected(String value) throws Exception {
        mvc().perform(post(PATH).header("X-CSRF-Token", value).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 161})
    void idempotencyKeyLengthIsChecked(int length) throws Exception {
        mvc().perform(post(PATH).header("X-CSRF-Token", CSRF).header("Idempotency-Key", "k".repeat(length))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @Test
    void blankIdempotencyKeyIsRejected() throws Exception {
        mvc().perform(post(PATH).header("X-CSRF-Token", CSRF).header("Idempotency-Key", " ".repeat(16))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1", " "})
    void invalidApprovalHeaderIsRejectedBeforeTrustedPorts(String value) throws Exception {
        mvc().perform(post(PATH).header("X-CSRF-Token", CSRF).header("Idempotency-Key", KEY)
                        .header("X-Approval-Id", value).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @Test
    void duplicateApprovalHeadersAreRejected() throws Exception {
        mvc().perform(request(validBody()).header("X-Approval-Id", id(351).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1"})
    void invalidOrAbbreviatedBatchIdIsRejected(String value) throws Exception {
        mvc().perform(post("/ingestion-batches/" + value + "/replay")
                        .header("X-CSRF-Token", CSRF).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "{\"reason\":null}", "{\"reason\":\"123456789\"}",
        "{\"reason\":\"          \"}", "{\"reason\":1234567890}",
        "{\"reason\":\"Synthetic replay verification\",\"mappingVersion\":null}",
        "{\"reason\":\"Synthetic replay verification\",\"dedupVersion\":null}",
        "{\"reason\":\"Synthetic replay verification\",\"mappingVersion\":\"\"}",
        "{\"reason\":\"Synthetic replay verification\",\"dedupVersion\":\"   \"}",
        "{\"reason\":\"Synthetic replay verification\",\"mappingVersion\":1}",
        "{\"reason\":\"Synthetic replay verification\",\"dedupVersion\":{}}",
        "{\"reason\":\"Synthetic replay verification\",\"clientPrincipalId\":\"forged\"}",
        "[]", "{", "null"
    })
    void invalidBodyCannotReachTrustedPorts(String body) throws Exception {
        mvc().perform(request(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @Test
    void missingBodyIsRejected() throws Exception {
        mvc().perform(post(PATH).header("X-CSRF-Token", CSRF).header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(access, gateway);
    }

    @Test
    void directServiceCallsAlsoRejectMissingBatchOrBody() {
        var service = new IngestionBatchReplayService(access, gateway);
        assertThatThrownBy(() -> service.replay(null, CSRF, KEY, null, command))
                .isInstanceOf(DataApiException.class);
        assertThatThrownBy(() -> service.replay(SOURCE_BATCH, CSRF, KEY, null, null))
                .isInstanceOf(DataApiException.class);
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {
        "AUTH_REQUIRED", "AUTH_SESSION_EXPIRED", "AUTH_CSRF_INVALID",
        "PERMISSION_DENIED", "PERMISSION_DATA_SCOPE", "DATA_NOT_FOUND"
    })
    void trustedAccessFailureStopsReplay(ErrorCode code) throws Exception {
        when(access.requireReplayAccess(CSRF, SOURCE_BATCH))
                .thenThrow(new DataApiException(code, null, "Synthetic replay access failure"));
        mvc().perform(request(validBody())).andExpect(status().is(code.httpStatus()))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {
        "REQUIREMENT_PENDING", "TASK_CONFLICT", "IDEMPOTENCY_CONFLICT", "RESOURCE_CONFLICT",
        "RESOURCE_VERSION_CONFLICT", "FILE_INVALID", "SYSTEM_UNAVAILABLE", "RATE_LIMITED"
    })
    void gatewayApprovalVersionOrSubmissionFailuresArePreserved(ErrorCode code) throws Exception {
        when(gateway.createOrReplay(any(), anyString(), any(), anyString(), nullable(UUID.class), any()))
                .thenThrow(new DataApiException(code, null, "Synthetic replay gateway failure"));
        var result = mvc().perform(request(validBody())).andExpect(status().is(code.httpStatus()))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue())).andReturn();
        if (code == ErrorCode.SYSTEM_UNAVAILABLE || code == ErrorCode.RATE_LIMITED) {
            assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("60");
        }
    }
}
