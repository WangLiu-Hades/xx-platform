package com.xxplatform.api.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xxplatform.api.api.IngestionController;
import com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery;
import com.xxplatform.api.client.ingestion.MockIngestionErrorPager;
import com.xxplatform.api.client.ingestion.MockIngestionErrorPager.StoredError;
import com.xxplatform.api.common.ApiExceptionHandler;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.service.ingestion.IngestionQueryService;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ErrorRow;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ReadAccess;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ReadScope;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.xxplatform.api.client.ingestion.InMemoryIngestionBatchQuery.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IngestionBatchErrorTests {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockMvc mvc(ReadAccess access, MockIngestionErrorPager pager) {
        var query = new InMemoryIngestionBatchQuery(InMemoryIngestionBatchQuery.demoRows(), pager);
        var service = new IngestionQueryService(access, query);
        return MockMvcBuilders.standaloneSetup(new IngestionController(service, mapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .addFilters(new RequestIdFilter())
                .build();
    }

    private MockMvc mvc() {
        return mvc(InMemoryIngestionBatchQuery::demoScope,
                new MockIngestionErrorPager(MockIngestionErrorPager.demoRows()));
    }

    private String path(int batchNumber) {
        return "/ingestion-batches/" + id(batchNumber) + "/errors";
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private String firstCursor(MockMvc client) throws Exception {
        var result = client.perform(get(path(2)).param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].rowNumber").value(3)).andReturn();
        String cursor = body(result).path("data").path("nextCursor").asText();
        assertThat(cursor).isNotBlank();
        return cursor;
    }

    private void expectBadCursor(MockMvc client, String cursor) throws Exception {
        client.perform(get(path(2)).param("pageSize", "1").param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATA_CURSOR_INVALID"))
                .andExpect(jsonPath("$.details[0].field").value("cursor"));
    }

    @Test
    void defaultResponseContainsPublicErrorsAndMatchingTrace() throws Exception {
        var result = mvc().perform(get(path(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].rowNumber").value(3))
                .andExpect(jsonPath("$.data.items[0].sourceRecordKey").value("synthetic-record-003"))
                .andExpect(jsonPath("$.data.items[0].field").value("captureTime"))
                .andExpect(jsonPath("$.data.items[0].code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.items[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].rawRecord").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].rowNumber").value(8))
                .andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.page").doesNotExist())
                .andExpect(jsonPath("$.data.total").doesNotExist()).andReturn();
        JsonNode json = body(result);
        assertThat(json.path("data").has("nextCursor")).isTrue();
        assertThat(json.path("traceId").asText()).isNotBlank()
                .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void nextCursorReturnsRemainingRowsAndCanBeReplayed() throws Exception {
        MockMvc client = mvc();
        String cursor = firstCursor(client);
        for (int repeat = 0; repeat < 2; repeat++) {
            client.perform(get(path(2)).param("pageSize", "1").param("cursor", cursor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(1))
                    .andExpect(jsonPath("$.data.items[0].rowNumber").value(8))
                    .andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.nullValue()));
        }
    }

    @Test
    void readableBatchWithoutErrorsHasAnEmptyPage() throws Exception {
        mvc().perform(get(path(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.nullValue()));
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 999})
    void hiddenAndMissingBatchesReturnTheSame404(int batchNumber) throws Exception {
        mvc().perform(get(path(batchNumber))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Batch not found"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void invalidUuidIsRejected() throws Exception {
        mvc().perform(get("/ingestion-batches/not-a-uuid/errors"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "abc"})
    void invalidPageSizesAreRejected(String value) throws Exception {
        mvc().perform(get(path(2)).param("pageSize", value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "unknown-cursor"})
    void unknownOrEmptyCursorIsRejected(String cursor) throws Exception {
        expectBadCursor(mvc(), cursor);
    }

    @Test
    void changedCursorCannotContinue() throws Exception {
        MockMvc client = mvc();
        expectBadCursor(client, firstCursor(client) + "x");
    }

    @Test
    void cursorCannotBeUsedForAnotherReadableBatch() throws Exception {
        MockMvc client = mvc();
        String cursor = firstCursor(client);
        client.perform(get(path(1)).param("pageSize", "1").param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATA_CURSOR_INVALID"));
    }

    @Test
    void cursorCannotChangePageSize() throws Exception {
        MockMvc client = mvc();
        String cursor = firstCursor(client);
        client.perform(get(path(2)).param("pageSize", "2").param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATA_CURSOR_INVALID"));
    }

    @Test
    void cursorCannotBeUsedByAnotherPrincipal() throws Exception {
        var scope = new AtomicReference<>(InMemoryIngestionBatchQuery.demoScope());
        MockMvc client = mvc(scope::get, new MockIngestionErrorPager(MockIngestionErrorPager.demoRows()));
        String cursor = firstCursor(client);
        scope.set(new ReadScope(id(901), scope.get().version(), scope.get().batchIds()));
        expectBadCursor(client, cursor);
    }

    @Test
    void cursorCannotUseAnOldScopeVersion() throws Exception {
        var scope = new AtomicReference<>(InMemoryIngestionBatchQuery.demoScope());
        MockMvc client = mvc(scope::get, new MockIngestionErrorPager(MockIngestionErrorPager.demoRows()));
        String cursor = firstCursor(client);
        scope.set(new ReadScope(id(900), "changed-version", scope.get().batchIds()));
        expectBadCursor(client, cursor);
    }

    @Test
    void changedScopeSetInvalidatesCursorEvenWhenBatchRemainsVisible() throws Exception {
        var scope = new AtomicReference<>(InMemoryIngestionBatchQuery.demoScope());
        MockMvc client = mvc(scope::get, new MockIngestionErrorPager(MockIngestionErrorPager.demoRows()));
        String cursor = firstCursor(client);
        scope.set(new ReadScope(id(900), scope.get().version(), Set.of(id(2))));
        expectBadCursor(client, cursor);
    }

    @Test
    void lostBatchAccessIsRecheckedBeforeContinuing() throws Exception {
        var scope = new AtomicReference<>(InMemoryIngestionBatchQuery.demoScope());
        MockMvc client = mvc(scope::get, new MockIngestionErrorPager(MockIngestionErrorPager.demoRows()));
        String cursor = firstCursor(client);
        scope.set(new ReadScope(id(900), "revoked", Set.of()));
        client.perform(get(path(2)).param("pageSize", "1").param("cursor", cursor))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_NOT_FOUND"));
    }

    @Test
    void expiredCursorIsRejected() throws Exception {
        MutableClock clock = new MutableClock();
        MockMvc client = mvc(InMemoryIngestionBatchQuery::demoScope,
                new MockIngestionErrorPager(MockIngestionErrorPager.demoRows(),
                        clock, Duration.ofMinutes(10), 8));
        String cursor = firstCursor(client);
        clock.advance(Duration.ofMinutes(10));
        expectBadCursor(client, cursor);
    }

    @Test
    void laterPagesDoNotExtendTheOriginalExpiry() {
        MutableClock clock = new MutableClock();
        var pager = new MockIngestionErrorPager(Map.of(id(2), List.of(
                error(1, 3), error(2, 8), error(3, 9))),
                clock, Duration.ofMinutes(10), 8);
        ReadScope scope = InMemoryIngestionBatchQuery.demoScope();
        String first = pager.page(id(2), null, 1, scope).nextCursor();
        clock.advance(Duration.ofMinutes(9));
        String second = pager.page(id(2), first, 1, scope).nextCursor();
        clock.advance(Duration.ofMinutes(1));
        assertCursorProblem(() -> pager.page(id(2), second, 1, scope));
    }

    @Test
    void aNewPagerDoesNotAcceptAnOldProcessCursor() throws Exception {
        String cursor = firstCursor(mvc());
        expectBadCursor(mvc(), cursor);
    }

    @Test
    void boundedCacheEvictsOldContinuations() {
        var pager = new MockIngestionErrorPager(MockIngestionErrorPager.demoRows(),
                Clock.systemUTC(), Duration.ofMinutes(10), 1);
        ReadScope scope = InMemoryIngestionBatchQuery.demoScope();
        String oldest = pager.page(id(2), null, 1, scope).nextCursor();
        String newest = pager.page(id(2), null, 1, scope).nextCursor();
        assertCursorProblem(() -> pager.page(id(2), oldest, 1, scope));
        assertThat(pager.page(id(2), newest, 1, scope).items()).hasSize(1);
    }

    @Test
    void errorsAreOrderedByRowThenInternalIdWithoutSkippingTies() {
        var pager = new MockIngestionErrorPager(Map.of(id(2), List.of(
                error(3, 3), error(2, 3), error(1, 2))));
        ReadScope scope = InMemoryIngestionBatchQuery.demoScope();
        var first = pager.page(id(2), null, 1, scope);
        var second = pager.page(id(2), first.nextCursor(), 1, scope);
        var third = pager.page(id(2), second.nextCursor(), 1, scope);
        assertThat(first.items().get(0).rowNumber()).isEqualTo(2);
        assertThat(second.items().get(0).sourceRecordKey()).isEqualTo("synthetic-2");
        assertThat(third.items().get(0).sourceRecordKey()).isEqualTo("synthetic-3");
        assertThat(third.nextCursor()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"AUTH_REQUIRED", "PERMISSION_DENIED"})
    void simulatedAccessFailuresKeepTheirHttpStatus(ErrorCode code) throws Exception {
        mvc(() -> { throw new DataApiException(code, null, "Synthetic access failure"); },
                new MockIngestionErrorPager(MockIngestionErrorPager.demoRows()))
                .perform(get(path(2))).andExpect(status().is(code.httpStatus()))
                .andExpect(jsonPath("$.code").value(code.name()));
    }

    private static StoredError error(int number, int rowNumber) {
        return new StoredError(id(8000 + number),
                new ErrorRow(rowNumber, "synthetic-" + number, "captureTime",
                        ErrorCode.VALIDATION_ERROR, "Synthetic format error"));
    }

    private static void assertCursorProblem(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(DataApiException.class)
                .satisfies(error -> assertThat(((DataApiException) error).code())
                        .isEqualTo(ErrorCode.DATA_CURSOR_INVALID));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-24T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
        void advance(Duration amount) { now = now.plus(amount); }
    }
}
