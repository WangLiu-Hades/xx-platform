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

class IngestionBatchListTests {
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
    void defaultsAndEnvelopeMatchContract() throws Exception {
        var result = mvc().perform(get("/ingestion-batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.items.length()").value(6))
                .andExpect(jsonPath("$.data.items[0].batchId").value(id(4).toString()))
                .andExpect(jsonPath("$.data.items[0].source").value("PLATE_TEST_A"))
                .andExpect(jsonPath("$.data.items[0].mappingVersion").value("synthetic-map-v1"))
                .andExpect(jsonPath("$.data.items[0].dedupVersion").value("synthetic-dedup-v1"))
                .andExpect(jsonPath("$.data.items[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.data.items[0].acceptedCount").value(0))
                .andExpect(jsonPath("$.data.items[0].rejectedCount").value(0))
                .andExpect(jsonPath("$.data.items[0].duplicateCount").value(0))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-10-24T12:00:00Z"))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andReturn();
        var body = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertThat(body.get("traceId").asText()).isNotBlank()
                .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        assertThat(result.getResponse().getContentAsString()).doesNotContain(id(7).toString());
    }

    @Test
    void filteringUsesAndAndCountsBeforePaging() throws Exception {
        mvc().perform(get("/ingestion-batches")
                        .param("source", "PLATE_TEST_A").param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].batchId").value(id(1).toString()));
    }

    @Test
    void pageTwoUsesStableIdTieBreaker() throws Exception {
        mvc().perform(get("/ingestion-batches").param("page", "2").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].batchId").value(id(1).toString()))
                .andExpect(jsonPath("$.data.items[1].batchId").value(id(2).toString()));
    }

    @Test
    void emptyFilterReturnsZeroTotal() throws Exception {
        mvc().perform(get("/ingestion-batches").param("source", "NO_SUCH_SOURCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void hugePageReturnsEmptyItemsAndKeepsActualTotal() throws Exception {
        mvc().perform(get("/ingestion-batches")
                        .param("page", "2147483647").param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(6));
    }

    @Test
    void statusSortIsStableAndAscending() throws Exception {
        mvc().perform(get("/ingestion-batches")
                        .param("sortBy", "status").param("sortOrder", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].batchId").value(id(6).toString()))
                .andExpect(jsonPath("$.data.items[4].batchId").value(id(1).toString()))
                .andExpect(jsonPath("$.data.items[5].batchId").value(id(3).toString()));
    }

    @Test
    void emptyTrustedScopeMeansNoRows() {
        var scope = new ReadScope(id(900), "empty-scope", Set.of());
        var service = new IngestionQueryService(() -> scope, query);
        var result = service.list(new BatchListQuery(null, null, null, null, null, null));
        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void authorizationIsRefreshedForEachCall() {
        var current = new java.util.concurrent.atomic.AtomicReference<>(
                InMemoryIngestionBatchQuery.demoScope());
        var service = new IngestionQueryService(current::get, query);
        var filters = new BatchListQuery(1, 20, "createdAt", SortOrder.DESC, null, null);
        assertThat(service.list(filters).total()).isEqualTo(6);
        current.set(new ReadScope(id(900), "narrower-scope", Set.of(id(1))));
        assertThat(service.list(filters).total()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "?page=0", "?pageSize=0", "?pageSize=101", "?page=abc",
            "?sortBy=name", "?sortOrder=SIDEWAYS", "?status=UNKNOWN"})
    void invalidInputUsesContractError(String suffix) throws Exception {
        mvc().perform(get("/ingestion-batches" + suffix))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void simulatedMissingSessionReturns401() throws Exception {
        mvc(() -> { throw new DataApiException(
                        ErrorCode.AUTH_REQUIRED, null, "Synthetic missing session"); })
                .perform(get("/ingestion-batches"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void simulatedMissingPermissionReturns403() throws Exception {
        mvc(() -> { throw new DataApiException(
                        ErrorCode.PERMISSION_DENIED, null, "Synthetic missing permission"); })
                .perform(get("/ingestion-batches"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
