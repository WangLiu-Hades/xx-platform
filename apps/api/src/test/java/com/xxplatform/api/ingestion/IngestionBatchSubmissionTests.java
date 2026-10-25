package com.xxplatform.api.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xxplatform.api.api.IngestionBatchInputDeserializer;
import com.xxplatform.api.api.IngestionController;
import com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery;
import com.xxplatform.api.common.ApiExceptionHandler;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.generated.model.IngestionBatchInput;
import com.xxplatform.api.generated.model.TaskStatus;
import com.xxplatform.api.service.ingestion.IngestionQueryService;
import com.xxplatform.api.service.ingestion.IngestionBatchSubmissionService;
import com.xxplatform.api.service.ingestion.IngestionBatchSubmissionService.AuthorizedActor;
import com.xxplatform.api.service.ingestion.IngestionBatchSubmissionService.SubmissionGateway;
import com.xxplatform.api.service.ingestion.IngestionBatchSubmissionService.SubmissionInput;
import com.xxplatform.api.service.ingestion.IngestionBatchSubmissionService.SubmissionReceipt;
import com.xxplatform.api.service.ingestion.IngestionBatchSubmissionService.WriteAccess;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery.id;
import static com.xxplatform.api.service.ingestion.IngestionBatchSubmissionService.OPERATION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IngestionBatchSubmissionTests {
    private static final String PATH = "/ingestion-batches";
    private static final String CSRF = "synthetic-csrf-for-unit-tests";
    private static final String KEY = "synthetic-idempotency-001";
    private final WriteAccess access = mock(WriteAccess.class);
    private final SubmissionGateway gateway = mock(SubmissionGateway.class);
    private final AuthorizedActor actor = new AuthorizedActor(id(900), "synthetic-scope-v1");
    private final SubmissionInput input =
            new SubmissionInput("PLATE_TEST_A", id(101), "synthetic-map-v1", "synthetic-dedup-v1");
    private final SubmissionReceipt receipt = new SubmissionReceipt(
            id(20), id(220), TaskStatus.PENDING, OffsetDateTime.parse("2026-10-24T16:00:00Z"));
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .registerModule(new SimpleModule().addDeserializer(
                    IngestionBatchInput.class, new IngestionBatchInputDeserializer()))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setupPorts() {
        when(access.requireWriteAccess(anyString())).thenReturn(actor);
        when(gateway.createOrReplay(any(), anyString(), anyString(), any())).thenReturn(receipt);
    }

    private MockMvc mvc(IngestionBatchSubmissionService service) {
        var query = new InMemoryIngestionBatchQuery(InMemoryIngestionBatchQuery.demoRows());
        var reads = new IngestionQueryService(InMemoryIngestionBatchQuery::demoScope, query);
        return MockMvcBuilders.standaloneSetup(new IngestionController(reads, mapper, service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .addFilters(new RequestIdFilter()).build();
    }

    private MockMvc mvc() {
        return mvc(new IngestionBatchSubmissionService(access, gateway));
    }

    private String validBody() throws Exception {
        return mapper.writeValueAsString(input);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body) {
        return post(PATH).header("X-CSRF-Token", CSRF).header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void successfulTrustedPortReceiptReturns202AndExactContract() throws Exception {
        var result = mvc().perform(request(validBody())).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.batchId").value(id(20).toString()))
                .andExpect(jsonPath("$.data.taskId").value(id(220).toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-24T16:00:00Z"))
                .andExpect(jsonPath("$.details").doesNotExist()).andReturn();
        var body = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("traceId").asText()).isNotBlank()
                .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        verify(access).requireWriteAccess(CSRF);
        verify(gateway).createOrReplay(actor, OPERATION_ID, KEY, input);
    }

    @Test
    void repeatedInputReauthorizesAndDelegatesReplayWithoutALocalCache() throws Exception {
        AuthorizedActor newer = new AuthorizedActor(actor.principalId(), "newer-scope");
        when(access.requireWriteAccess(CSRF)).thenReturn(actor, newer);
        MockMvc client = mvc();
        client.perform(request(validBody())).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").value(id(220).toString()));
        client.perform(request(validBody())).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").value(id(220).toString()));
        verify(access, times(2)).requireWriteAccess(CSRF);
        verify(gateway).createOrReplay(actor, OPERATION_ID, KEY, input);
        verify(gateway).createOrReplay(newer, OPERATION_ID, KEY, input);
    }

    @Test
    void configuredUnavailableSubmissionNeverReturnsAFakeReceipt() throws Exception {
        var result = mvc(IngestionBatchSubmissionService.unavailable())
                .perform(request(validBody())).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("SYSTEM_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.taskId").doesNotExist()).andReturn();
        assertThat(mapper.readTree(result.getResponse().getContentAsString())
                .path("traceId").asText()).isNotBlank();
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"X-CSRF-Token", "Idempotency-Key"})
    void missingHeadersAre400RatherThanAnUnhandled500(String missing) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        if (!"X-CSRF-Token".equals(missing)) headers.set("X-CSRF-Token", CSRF);
        if (!"Idempotency-Key".equals(missing)) headers.set("Idempotency-Key", KEY);
        mvc().perform(post(PATH).headers(headers).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @Test
    void shortCsrfHeaderIsRejectedBeforePorts() throws Exception {
        mvc().perform(post(PATH).header("X-CSRF-Token", "short").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "                "})
    void shortOrBlankIdempotencyKeyIsRejected(String value) throws Exception {
        mvc().perform(post(PATH).header("X-CSRF-Token", CSRF).header("Idempotency-Key", value)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @Test
    void overlongIdempotencyKeyIsRejected() throws Exception {
        mvc().perform(post(PATH).header("X-CSRF-Token", CSRF).header("Idempotency-Key", "k".repeat(161))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "sourceFileId", "mappingVersion", "dedupVersion"})
    void requiredBodyFieldsCannotBeOmitted(String field) throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(validBody());
        body.remove(field);
        mvc().perform(request(body.toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "mappingVersion", "dedupVersion"})
    void requiredTextCannotBeWhitespace(String field) throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(validBody());
        body.put(field, "   ");
        mvc().perform(request(body.toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @Test
    void overlongSourceIsRejected() throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(validBody());
        body.put("source", "s".repeat(81));
        mvc().perform(request(body.toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1"})
    void malformedAndAbbreviatedFileIdsAreRejected(String value) throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(validBody());
        body.put("sourceFileId", value);
        mvc().perform(request(body.toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @Test
    void additionalBodyFieldsAreRejected() throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(validBody());
        body.put("clientPrincipalId", id(999).toString());
        mvc().perform(request(body.toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @Test
    void numericStringsAreNotSilentlyCoerced() throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(validBody());
        body.put("mappingVersion", 1);
        mvc().perform(request(body.toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "{", "null"})
    void nonObjectOrInvalidJsonIsRejected(String value) throws Exception {
        mvc().perform(request(value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(access, gateway);
    }

    @Test
    void nullInputInDirectServiceCallsIsRejected() {
        var service = new IngestionBatchSubmissionService(access, gateway);
        assertThatThrownBy(() -> service.submit(CSRF, KEY, null))
                .isInstanceOf(DataApiException.class)
                .satisfies(error -> assertThat(((DataApiException) error).code())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(access, gateway);
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {
            "AUTH_REQUIRED", "AUTH_SESSION_EXPIRED", "AUTH_CSRF_INVALID",
            "PERMISSION_DENIED", "PERMISSION_DATA_SCOPE"})
    void failedTrustedAccessPreventsSubmission(ErrorCode code) throws Exception {
        when(access.requireWriteAccess(CSRF)).thenThrow(
                new DataApiException(code, null, "Synthetic access failure"));
        mvc().perform(request(validBody())).andExpect(status().is(code.httpStatus()))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {
            "FILE_INVALID", "REQUIREMENT_PENDING", "TASK_CONFLICT",
            "IDEMPOTENCY_CONFLICT", "RESOURCE_CONFLICT", "SYSTEM_UNAVAILABLE"})
    void sharedPortFailuresArePropagatedWithoutA202(ErrorCode code) throws Exception {
        when(gateway.createOrReplay(any(), anyString(), anyString(), any())).thenThrow(
                new DataApiException(code, null, "Synthetic shared dependency failure"));
        mvc().perform(request(validBody())).andExpect(status().is(code.httpStatus()))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }
}
