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
class IngestionBatchSubmissionProfileTests {
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper mapper;

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-CSRF-Token", "synthetic-token-not-a-real-session");
        headers.set("Idempotency-Key", "synthetic-idempotency-live-profile");
        return headers;
    }

    private String body() {
        return "{\"source\":\"PLATE_TEST_A\","
                + "\"sourceFileId\":\"00000000-0000-4000-8000-000000000101\","
                + "\"mappingVersion\":\"synthetic-map-v1\","
                + "\"dedupVersion\":\"synthetic-dedup-v1\"}";
    }

    @Test
    void actualDevConfigurationReturns503AndDoesNotAddABatch() throws Exception {
        var before = rest.getForEntity("/ingestion-batches", String.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
        var response = rest.postForEntity("/ingestion-batches",
                new HttpEntity<>(body(), headers()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        var json = mapper.readTree(response.getBody());
        assertThat(json.path("code").asText()).isEqualTo("SYSTEM_UNAVAILABLE");
        assertThat(json.has("data")).isTrue();
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(json.path("traceId").asText()).isNotBlank()
                .isEqualTo(response.getHeaders().getFirst("X-Trace-Id"));
        var after = rest.getForEntity("/ingestion-batches", String.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(after.getBody()).path("data"))
                .isEqualTo(mapper.readTree(before.getBody()).path("data"));
    }

    @Test
    void domainInputModuleIsActiveInTheRealSpringContext() throws Exception {
        String unknownField = body().substring(0, body().length() - 1)
                + ",\"clientPrincipalId\":\"forged\"}";
        var response = rest.postForEntity("/ingestion-batches",
                new HttpEntity<>(unknownField, headers()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.readTree(response.getBody()).path("code").asText())
                .isEqualTo("VALIDATION_ERROR");
    }
}
