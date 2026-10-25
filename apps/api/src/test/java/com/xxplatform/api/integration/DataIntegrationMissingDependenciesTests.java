package com.xxplatform.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.service.plate.CaptureExportSourceAdapter;
import com.xxplatform.api.service.ingestion.DataApiException;
import com.xxplatform.api.common.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=integration-missing")
class DataIntegrationMissingDependenciesTests {
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper mapper;
    @Autowired CaptureExportSourceAdapter exports;

    @Test void normalProfileRegistersReadRoutesWithoutDemoFallback() throws Exception {
        for (String path : new String[]{"/ingestion-batches",
                "/ingestion-batches/00000000-0000-0000-0000-000000000001",
                "/ingestion-batches/00000000-0000-0000-0000-000000000001/errors",
                "/ingestion/quality-summary",
                "/captures/00000000-0000-0000-0000-000000000001",
                "/map/features?bbox=116,39,117,40&zoom=12&dataType=PLATE"}) {
            checkUnavailable(rest.getForEntity(path,String.class));
        }
    }
    @Test void writeRoutesDoNotCreateFakeReceipts() throws Exception {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-CSRF-Token","integration-csrf-token");
        headers.set("Idempotency-Key","integration-idempotency-key");
        String id = "00000000-0000-0000-0000-000000000001";
        String range = "\"timeRange\":{\"from\":\"2026-10-24T10:00:00Z\",\"to\":\"2026-10-24T11:00:00Z\"}";
        String[][] cases = {
                {"/ingestion-batches","{\"source\":\"PLATE_A\",\"sourceFileId\":\""+id+"\",\"mappingVersion\":\"v1\",\"dedupVersion\":\"v1\"}"},
                {"/ingestion-batches/"+id+"/replay","{\"reason\":\"integration replay test\"}"},
                {"/ingestion/backfills","{\"source\":\"PLATE_A\",\"reason\":\"integration backfill test\","+range+"}"},
                {"/captures/search","{\"dataType\":\"PLATE\","+range+"}"}
        };
        for (var entry : cases) checkUnavailable(rest.postForEntity(
                entry[0],new HttpEntity<>(entry[1],headers),String.class));
    }
    @Test void normalProfileUsesStrictDeserializers() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-CSRF-Token","integration-csrf-token");
        var response = rest.postForEntity("/captures/search",new HttpEntity<>(
                "{\"dataType\":\"PLATE\",\"timeRange\":{\"from\":\"2026-10-24T10:00:00\",\"to\":\"2026-10-24T11:00:00Z\"}}",headers),String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void exportBeanIsPresentButRequiresTrustedDependencies() {
        assertThatThrownBy(() -> exports.prepare("integration-csrf-token",new UUID(0,1),"CSV",null,"test"))
                .isInstanceOfSatisfying(DataApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.SYSTEM_UNAVAILABLE));
    }
    private void checkUnavailable(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        var json = mapper.readTree(response.getBody());
        assertThat(json.path("code").asText()).isEqualTo("SYSTEM_UNAVAILABLE");
        assertThat(json.has("data")).isTrue();
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(json.path("details").isArray()).isTrue();
        assertThat(json.path("traceId").asText()).isNotBlank()
                .isEqualTo(response.getHeaders().getFirst("X-Trace-Id"));
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }
}
