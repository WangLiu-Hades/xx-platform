package com.xxplatform.api.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=dev,ingestion-mock", "server.address=127.0.0.1"})
class IngestionBatchReplayProfileTests {
    private static final String BATCH = "/ingestion-batches/00000000-0000-4000-8000-000000000001";
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper mapper;

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-CSRF-Token", "synthetic-replay-csrf-not-a-real-session");
        headers.set("Idempotency-Key", "synthetic-replay-profile-key");
        return headers;
    }

    @Test
    void actualConfigurationReturns503AndKeepsSourceBatchAndListUnchanged() throws Exception {
        var beforeList = rest.getForEntity("/ingestion-batches", String.class);
        var beforeSource = rest.getForEntity(BATCH, String.class);
        assertThat(beforeList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(beforeSource.getStatusCode()).isEqualTo(HttpStatus.OK);
        var response = rest.postForEntity(BATCH + "/replay", new HttpEntity<>(
                "{\"reason\":\"Synthetic replay verification\"}", headers()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        var json = mapper.readTree(response.getBody());
        assertThat(json.path("code").asText()).isEqualTo("SYSTEM_UNAVAILABLE");
        assertThat(json.has("data")).isTrue();
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(json.path("traceId").asText()).isNotBlank()
                .isEqualTo(response.getHeaders().getFirst("X-Trace-Id"));
        var afterList = rest.getForEntity("/ingestion-batches", String.class);
        var afterSource = rest.getForEntity(BATCH, String.class);
        assertThat(afterList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterSource.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(afterList.getBody()).path("data"))
                .isEqualTo(mapper.readTree(beforeList.getBody()).path("data"));
        assertThat(mapper.readTree(afterSource.getBody()).path("data"))
                .isEqualTo(mapper.readTree(beforeSource.getBody()).path("data"));
    }

    @Test
    void actualConfigurationRejectsExplicitNullVersion() throws Exception {
        var response = rest.postForEntity(BATCH + "/replay", new HttpEntity<>(
                "{\"reason\":\"Synthetic replay verification\",\"mappingVersion\":null}", headers()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.readTree(response.getBody()).path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void actualConfigurationRejectsShortReason() throws Exception {
        var response = rest.postForEntity(BATCH + "/replay", new HttpEntity<>(
                "{\"reason\":\"short\"}", headers()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.readTree(response.getBody()).path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void optionalApprovalHeaderIsReadDespiteGeneratorOmission() throws Exception {
        HttpHeaders headers = headers();
        headers.set("X-Approval-Id", "not-a-uuid");
        var response = rest.postForEntity(BATCH + "/replay", new HttpEntity<>(
                "{\"reason\":\"Synthetic replay verification\"}", headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var json = mapper.readTree(response.getBody());
        assertThat(json.path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(json.path("details").get(0).path("field").asText()).isEqualTo("approvalId");
    }
}
