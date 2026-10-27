package com.xxplatform.api.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xxplatform.api.api.IngestionController;
import com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery;
import com.xxplatform.api.common.ApiExceptionHandler;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.generated.model.SortOrder;
import com.xxplatform.api.service.ingestion.BatchListQuery;
import com.xxplatform.api.service.ingestion.IngestionQueryService;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ReadAccess;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ReadScope;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IngestionBatchDetailTests {
    private final InMemoryIngestionBatchQuery query =
            new InMemoryIngestionBatchQuery(InMemoryIngestionBatchQuery.demoRows());

    private MockMvc mvc(ReadAccess access) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var service = new IngestionQueryService(access, query);
        return MockMvcBuilders.standaloneSetup(
                        new IngestionController(service, mapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .addFilters(new RequestIdFilter())
                .build();
    }

    private MockMvc mvc() {
        return mvc(InMemoryIngestionBatchQuery::demoScope);
    }

    @Test
    void visibleBatchReturnsContractDetailAndTrace() throws Exception {
        var result = mvc().perform(get("/ingestion-batches/" + id(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.batchId").value(id(1).toString()))
                .andExpect(jsonPath("$.data.source").value("PLATE_TEST_A"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.acceptedCount").value(10))
                .andExpect(jsonPath("$.data.items").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andReturn();
        var body = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertThat(body.get("traceId").asText()).isNotBlank()
                .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void listEntryAndDetailAreTheSameBatch() throws Exception {
        MockMvc client = mvc();
        var list = client.perform(get("/ingestion-batches"))
                .andExpect(status().isOk()).andReturn();
        ObjectMapper json = new ObjectMapper();
        var row = json.readTree(list.getResponse().getContentAsString())
                .path("data").path("items").get(0);
        var detail = client.perform(get("/ingestion-batches/" + row.path("batchId").asText()))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(detail.getResponse().getContentAsString()).path("data"))
                .isEqualTo(row);
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 999})
    void hiddenAndMissingBatchesUseTheSame404(int number) throws Exception {
        mvc().perform(get("/ingestion-batches/" + id(number)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Batch not found"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void malformedBatchIdIsRejected() throws Exception {
        mvc().perform(get("/ingestion-batches/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void detailRechecksTheCurrentScope() {
        var current = new java.util.concurrent.atomic.AtomicReference<>(
                InMemoryIngestionBatchQuery.demoScope());
        var service = new IngestionQueryService(current::get, query);
        assertThat(service.get(id(1)).batchId()).isEqualTo(id(1));
        current.set(new ReadScope(id(900), "narrower-scope", Set.of()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.get(id(1)))
                .isInstanceOf(DataApiException.class)
                .satisfies(error -> assertThat(((DataApiException) error).code())
                        .isEqualTo(ErrorCode.DATA_NOT_FOUND));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = ErrorCode.class, names = {"AUTH_REQUIRED", "PERMISSION_DENIED"})
    void simulatedAccessFailuresKeepTheirStatus(ErrorCode code) throws Exception {
        mvc(() -> { throw new DataApiException(code, null, "Synthetic access failure"); })
                .perform(get("/ingestion-batches/" + id(1)))
                .andExpect(status().is(code.httpStatus()))
                .andExpect(jsonPath("$.code").value(code.name()));
    }
}
