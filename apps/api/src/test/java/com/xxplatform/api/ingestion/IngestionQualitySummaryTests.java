package com.xxplatform.api.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xxplatform.api.api.IngestionController;
import com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery;
import com.xxplatform.api.client.ingestion.MockIngestionQualityQuery;
import com.xxplatform.api.client.ingestion.MockIngestionQualityQuery.Outcome;
import com.xxplatform.api.client.ingestion.MockIngestionQualityQuery.QualityItem;
import com.xxplatform.api.common.ApiExceptionHandler;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.service.ingestion.IngestionQueryService;
import com.xxplatform.api.service.ingestion.IngestionQueryService.QualityQuery;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ReadAccess;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ReadScope;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IngestionQualitySummaryTests {
    private static final String PATH = "/ingestion/quality-summary";
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockMvc mvc(ReadAccess access) {
        var query = new InMemoryIngestionBatchQuery(InMemoryIngestionBatchQuery.demoRows());
        var service = new IngestionQueryService(access, query);
        return MockMvcBuilders.standaloneSetup(new IngestionController(service, mapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .addFilters(new RequestIdFilter())
                .build();
    }

    private MockMvc mvc() {
        return mvc(InMemoryIngestionBatchQuery::demoScope);
    }

    @Test
    void defaultSummaryHasRequiredFieldsWithoutAnAbsentSource() throws Exception {
        var result = mvc().perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.asOf").value("2026-10-24T16:00:00Z"))
                .andExpect(jsonPath("$.data.acceptedCount").value(20))
                .andExpect(jsonPath("$.data.rejectedCount").value(0))
                .andExpect(jsonPath("$.data.duplicateCount").value(0))
                .andExpect(jsonPath("$.data.unmappedDeviceCount").value(0))
                .andExpect(jsonPath("$.data.invalidCoordinateCount").value(0))
                .andExpect(jsonPath("$.data.items").doesNotExist())
                .andExpect(jsonPath("$.data.total").doesNotExist()).andReturn();
        var body = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("data").has("source")).isFalse();
        assertThat(body.path("data").size()).isEqualTo(6);
        assertThat(body.path("traceId").asText()).isNotBlank()
                .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }

    @ParameterizedTest
    @CsvSource({"PLATE_TEST_A,10", "PLATE_TEST_B,10", "UNKNOWN_SOURCE,0"})
    void sourceFilterIsAppliedAndEchoed(String source, int expected) throws Exception {
        mvc().perform(get(PATH).param("source", source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value(source))
                .andExpect(jsonPath("$.data.acceptedCount").value(expected))
                .andExpect(jsonPath("$.data.rejectedCount").value(0));
    }

    @Test
    void sourceAndTimeFiltersAreCombined() throws Exception {
        mvc().perform(get(PATH).param("source", "PLATE_TEST_A")
                        .param("from", "2026-10-24T11:00:30Z")
                        .param("to", "2026-10-24T11:01:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(10));
    }

    @Test
    void startIsInclusive() throws Exception {
        mvc().perform(get(PATH).param("from", "2026-10-24T11:00:30Z")
                        .param("to", "2026-10-24T11:01:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(20));
    }

    @Test
    void endIsExclusiveAndUsesProcessedTime() throws Exception {
        mvc().perform(get(PATH).param("from", "2026-10-24T11:00:00Z")
                        .param("to", "2026-10-24T11:00:30Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(0));
    }

    @Test
    void omittedTimeBoundsDoNotAddImplicitFilters() throws Exception {
        MockMvc client = mvc();
        client.perform(get(PATH).param("from", "2026-10-24T11:00:30Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(20));
        client.perform(get(PATH).param("to", "2026-10-24T11:00:30Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(0));
    }

    @Test
    void equivalentOffsetTimesSelectTheSameRecords() throws Exception {
        mvc().perform(get(PATH).param("from", "2026-10-24T19:00:30+08:00")
                        .param("to", "2026-10-24T19:01:00+08:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(20));
    }

    @Test
    void emptyTimeRangeResultHasAllFiveZeroCounts() throws Exception {
        mvc().perform(get(PATH).param("from", "2026-10-25T00:00:00Z")
                        .param("to", "2026-10-26T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(0))
                .andExpect(jsonPath("$.data.rejectedCount").value(0))
                .andExpect(jsonPath("$.data.duplicateCount").value(0))
                .andExpect(jsonPath("$.data.unmappedDeviceCount").value(0))
                .andExpect(jsonPath("$.data.invalidCoordinateCount").value(0));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-24T11:00:00Z,2026-10-24T11:00:00Z",
            "2026-10-24T12:00:00Z,2026-10-24T11:00:00Z",
            "2026-10-24T19:00:00+08:00,2026-10-24T11:00:00Z"
    })
    void equalOrReversedInstantsAreRejected(String from, String to) throws Exception {
        mvc().perform(get(PATH).param("from", from).param("to", to))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("to"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-date", "2026-10-24T11:00:00"})
    void malformedOrUnzonedTimesAreRejected(String from) throws Exception {
        mvc().perform(get(PATH).param("from", from))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void scopeIsFreshForEveryRequestAndEmptyScopeReturnsZeros() throws Exception {
        var scope = new AtomicReference<>(InMemoryIngestionBatchQuery.demoScope());
        MockMvc client = mvc(scope::get);
        client.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(20));
        scope.set(new ReadScope(id(900), "only-one", Set.of(id(1))));
        client.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(10));
        scope.set(new ReadScope(id(900), "empty", Set.of()));
        client.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(0))
                .andExpect(jsonPath("$.data.rejectedCount").value(0))
                .andExpect(jsonPath("$.data.duplicateCount").value(0))
                .andExpect(jsonPath("$.data.unmappedDeviceCount").value(0))
                .andExpect(jsonPath("$.data.invalidCoordinateCount").value(0));
    }

    @Test
    void failedBatchDiagnosticsAreExcluded() throws Exception {
        mvc(() -> new ReadScope(id(900), "only-failed", Set.of(id(2))))
                .perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(0))
                .andExpect(jsonPath("$.data.rejectedCount").value(0))
                .andExpect(jsonPath("$.data.invalidCoordinateCount").value(0));
    }

    @Test
    void hiddenSuccessfulBatchContributesOnlyAfterARealScopeGrantInTheTest() throws Exception {
        mvc(() -> new ReadScope(id(900), "expanded-test-scope",
                        Set.of(id(1), id(3), id(7))))
                .perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(30));
    }

    @Test
    void summaryIsIndependentOfTheBatchListPageSize() throws Exception {
        MockMvc client = mvc();
        client.perform(get("/ingestion-batches").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
        client.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedCount").value(20));
    }

    @Test
    void allProcessingOutcomesAndQualityUnitsAreCounted() {
        OffsetDateTime processed = OffsetDateTime.parse("2026-10-24T11:00:30Z");
        List<QualityItem> items = List.of(
                new QualityItem(id(1), processed, Outcome.ACCEPTED, "same-device", true, true),
                new QualityItem(id(1), processed, Outcome.REJECTED, "same-device", true, true),
                new QualityItem(id(1), processed, Outcome.DUPLICATE, "other-device", true, false),
                new QualityItem(id(1), processed, Outcome.ACCEPTED, null, true, true),
                new QualityItem(id(3), processed, Outcome.ACCEPTED, "same-device", true, false));
        var query = new MockIngestionQualityQuery(InMemoryIngestionBatchQuery.demoRows(),
                items, MockIngestionQualityQuery.DEMO_AS_OF);
        var result = query.summary(new QualityQuery(null, null, null),
                InMemoryIngestionBatchQuery.demoScope());
        assertThat(result.acceptedCount()).isEqualTo(3);
        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(result.unmappedDeviceCount()).isEqualTo(3);
        assertThat(result.invalidCoordinateCount()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"AUTH_REQUIRED", "PERMISSION_DENIED"})
    void simulatedAccessFailuresUseSharedCodes(ErrorCode code) throws Exception {
        mvc(() -> { throw new DataApiException(code, null, "Synthetic access failure"); })
                .perform(get(PATH)).andExpect(status().is(code.httpStatus()))
                .andExpect(jsonPath("$.code").value(code.name()));
    }
}
