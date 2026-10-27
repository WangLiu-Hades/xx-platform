package com.xxplatform.api.plate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=dev,ingestion-mock", "server.address=127.0.0.1"})
class DataApiProfileTests {
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper mapper;
    private HttpHeaders headers() {
        var h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-CSRF-Token", "synthetic-week2-csrf-not-a-real-session");
        h.set("Idempotency-Key", "synthetic-week2-profile-key"); return h;
    }
    private void error(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(status);
        var json = mapper.readTree(response.getBody()); assertThat(json.path("code").asText()).isEqualTo(code);
        assertThat(json.has("data")).isTrue(); assertThat(json.path("data").isNull()).isTrue();
        assertThat(json.path("traceId").asText()).isNotBlank().isEqualTo(response.getHeaders().getFirst("X-Trace-Id"));
    }
    @Test void backfillIsUnavailableAndDoesNotAddAVisibleBatch() throws Exception {
        var before = rest.getForEntity("/ingestion-batches", String.class);
        var response = rest.postForEntity("/ingestion/backfills", new HttpEntity<>(
                "{\"source\":\"PLATE_TEST_A\",\"reason\":\"Synthetic backfill verification\",\"timeRange\":{\"from\":\"2026-10-24T11:00:00Z\",\"to\":\"2026-10-24T12:00:00Z\"}}", headers()), String.class);
        error(response, HttpStatus.SERVICE_UNAVAILABLE, "SYSTEM_UNAVAILABLE");
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        var after = rest.getForEntity("/ingestion-batches", String.class);
        assertThat(mapper.readTree(after.getBody()).path("data")).isEqualTo(mapper.readTree(before.getBody()).path("data"));
    }
    @Test void strictBackfillTimesAreActiveInBootContext() throws Exception {
        error(rest.postForEntity("/ingestion/backfills", new HttpEntity<>(
                "{\"source\":\"PLATE_TEST_A\",\"reason\":\"Synthetic backfill verification\",\"timeRange\":{\"from\":\"2026-10-24T11:00:00\",\"to\":\"2026-10-24T12:00:00Z\"}}", headers()), String.class), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }
    @Test void actualSearchAndMapDoNotUseTheFixedIngestionReadIdentity() throws Exception {
        error(rest.postForEntity("/captures/search", new HttpEntity<>(
                "{\"dataType\":\"PLATE\",\"timeRange\":{\"from\":\"2026-10-24T11:00:00Z\",\"to\":\"2026-10-24T12:00:00Z\"}}", headers()), String.class), HttpStatus.SERVICE_UNAVAILABLE, "SYSTEM_UNAVAILABLE");
        error(rest.getForEntity("/map/features?bbox=116,39,117,40&zoom=12&dataType=PLATE", String.class), HttpStatus.SERVICE_UNAVAILABLE, "SYSTEM_UNAVAILABLE");
        error(rest.getForEntity("/captures/00000000-0000-4000-8000-000000000050", String.class), HttpStatus.SERVICE_UNAVAILABLE, "SYSTEM_UNAVAILABLE");
    }
    @Test void malformedMapAndUnknownCaptureFieldsAre400() throws Exception {
        error(rest.getForEntity("/map/features?bbox=181,39,182,40&zoom=12&dataType=PLATE", String.class), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        error(rest.postForEntity("/captures/search", new HttpEntity<>(
                "{\"dataType\":\"PLATE\",\"clientPrincipalId\":\"forged\",\"timeRange\":{\"from\":\"2026-10-24T11:00:00Z\",\"to\":\"2026-10-24T12:00:00Z\"}}", headers()), String.class), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }
}
