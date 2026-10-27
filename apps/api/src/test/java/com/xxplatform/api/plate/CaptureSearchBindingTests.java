package com.xxplatform.api.plate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xxplatform.api.api.CaptureSearchInputDeserializer;
import com.xxplatform.api.api.DataQueryInputMapper;
import com.xxplatform.api.generated.model.CaptureSearchRequest;
import com.xxplatform.api.generated.model.PlateCaptureSearchRequest;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HTTP body -> generated oneOf DTO -> domain query; independent of DTO JSON serialization. */
class CaptureSearchBindingTests {
    private ObjectMapper mapper(boolean timestamps) {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .registerModule(new SimpleModule().addDeserializer(
                        CaptureSearchRequest.class, new CaptureSearchInputDeserializer()))
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, timestamps);
    }
    private String minimal(String type) {
        return "{\"dataType\":\"" + type + "\",\"timeRange\":{\"from\":\"2026-10-24T19:00:00+08:00\","
                + "\"to\":\"2026-10-24T20:00:00+08:00\"}}";
    }
    @ParameterizedTest
    @CsvSource({"PLATE,true", "PLATE,false", "FENCE,true", "FENCE,false", "MAC,true", "MAC,false", "FACE,true", "FACE,false"})
    void minimalBodiesKeepTheirTypeTimesAndDefaults(String type, boolean timestamps) throws Exception {
        var input = mapper(timestamps).readValue(minimal(type), CaptureSearchRequest.class);
        var query = DataQueryInputMapper.capture(input);
        assertThat(query.dataType()).isEqualTo(type);
        assertThat(query.from()).isEqualTo(OffsetDateTime.parse("2026-10-24T11:00:00Z"));
        assertThat(query.to()).isEqualTo(OffsetDateTime.parse("2026-10-24T12:00:00Z"));
        assertThat(query.pageSize()).isEqualTo(20);
        assertThat(query.sortBy()).isEqualTo("captureTime");
        assertThat(query.sortOrder()).isEqualTo("DESC");
        assertThat(query.cursor()).isNull();
        assertThat(query.targetIds()).isNull();
        assertThat(query.siteIds()).isNull();
        assertThat(query.deviceIds()).isNull();
    }
    @Test void completePlateBodyKeepsExactFiltersIdsAndOpaqueCursor() throws Exception {
        var mapper = mapper(true);
        var body = (ObjectNode) mapper.readTree(minimal("PLATE"));
        String site = "00000000-0000-4000-8000-000000000001";
        String target = "00000000-0000-4000-8000-000000000002";
        String device = "00000000-0000-4000-8000-000000000003";
        body.putArray("siteIds").add(site); body.putArray("targetIds").add(target);
        body.putArray("deviceIds").add(device); body.put("pageSize", 7);
        body.put("sortBy", "captureId"); body.put("sortOrder", "ASC");
        body.put("cursor", "opaque-cursor-not-for-client-parsing");
        body.put("plateNumber", "SYNTHETIC-EXACT-01"); body.put("plateColor", "SYNTHETIC-COLOR");
        var input = mapper.readValue(body.toString(), CaptureSearchRequest.class);
        var query = DataQueryInputMapper.capture(input);
        assertThat(query.exactFilters()).containsEntry("plateNumber", "SYNTHETIC-EXACT-01")
                .containsEntry("plateColor", "SYNTHETIC-COLOR");
        assertThat(query.siteIds()).containsExactly(UUID.fromString(site));
        assertThat(query.targetIds()).containsExactly(UUID.fromString(target));
        assertThat(query.deviceIds()).containsExactly(UUID.fromString(device));
        assertThat(query.cursor()).isEqualTo("opaque-cursor-not-for-client-parsing");
        assertThat(query.pageSize()).isEqualTo(7);
        assertThat(query.sortBy()).isEqualTo("captureId"); assertThat(query.sortOrder()).isEqualTo("ASC");
    }
    @ParameterizedTest @CsvSource({"FENCE,imsi,SYNTHETIC-IMSI", "FENCE,imei,SYNTHETIC-IMEI", "MAC,macAddress,SYNTHETIC-MAC"})
    void otherSubtypesKeepTheirOwnExactFilters(String type, String field, String value) throws Exception {
        var mapper = mapper(false); var body = (ObjectNode) mapper.readTree(minimal(type));
        body.put(field, value);
        assertThat(DataQueryInputMapper.capture(mapper.readValue(body.toString(), CaptureSearchRequest.class)).exactFilters())
                .containsEntry(field, value);
    }
    @ParameterizedTest @ValueSource(strings = {"cursor", "pageSize", "siteIds", "targetIds", "deviceIds", "plateNumber", "sortBy", "sortOrder"})
    void explicitlyNullOptionalFieldsRemainInvalid(String field) throws Exception {
        var mapper = mapper(false); var body = (ObjectNode) mapper.readTree(minimal("PLATE")); body.putNull(field);
        assertThatThrownBy(() -> mapper.readValue(body.toString(), CaptureSearchRequest.class)).isInstanceOf(Exception.class);
    }
    @ParameterizedTest @ValueSource(strings = {"clientPrincipalId", "imsi", "displayTarget", "unexpected"})
    void unknownOrWrongSubtypeFieldsRemainInvalid(String field) throws Exception {
        var mapper = mapper(false); var body = (ObjectNode) mapper.readTree(minimal("PLATE")); body.put(field, "synthetic");
        assertThatThrownBy(() -> mapper.readValue(body.toString(), CaptureSearchRequest.class)).isInstanceOf(Exception.class);
    }
    @ParameterizedTest @ValueSource(strings = {"from", "to"})
    void missingTimezonesRemainInvalid(String field) throws Exception {
        var mapper = mapper(false); var body = (ObjectNode) mapper.readTree(minimal("PLATE"));
        ((ObjectNode) body.get("timeRange")).put(field, "2026-10-24T11:00:00");
        assertThatThrownBy(() -> mapper.readValue(body.toString(), CaptureSearchRequest.class)).isInstanceOf(Exception.class);
    }
    @ParameterizedTest @ValueSource(strings = {"PLATE", "FENCE", "MAC", "FACE"})
    void suppliedEmptySiteAndDeviceListsStayPresent(String type) throws Exception {
        var mapper = mapper(false); var body = (ObjectNode) mapper.readTree(minimal(type));
        body.putArray("siteIds"); body.putArray("deviceIds");
        var query = DataQueryInputMapper.capture(mapper.readValue(body.toString(), CaptureSearchRequest.class));
        assertThat(query.siteIds()).isNotNull().isEmpty();
        assertThat(query.deviceIds()).isNotNull().isEmpty();
        assertThat(query.targetIds()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"PLATE", "FENCE", "MAC", "FACE"})
    void suppliedNonemptyTargetListIsNeverCleared(String type) throws Exception {
        var mapper = mapper(false); var body = (ObjectNode) mapper.readTree(minimal(type));
        var id = UUID.fromString("00000000-0000-4000-8000-000000000015");
        body.putArray("targetIds").add(id.toString());
        var query = DataQueryInputMapper.capture(mapper.readValue(body.toString(), CaptureSearchRequest.class));
        assertThat(query.targetIds()).containsExactly(id);
        assertThat(query.siteIds()).isNull(); assertThat(query.deviceIds()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"PLATE", "FENCE", "MAC", "FACE"})
    void suppliedEmptyTargetListStillFailsStrictRawValidation(String type) throws Exception {
        var mapper = mapper(false); var body = (ObjectNode) mapper.readTree(minimal(type)); body.putArray("targetIds");
        assertThatThrownBy(() -> mapper.readValue(body.toString(), CaptureSearchRequest.class))
                .satisfies(error -> {
                    Throwable root = error;
                    while (root.getCause() != null) root = root.getCause();
                    assertThat(root).isInstanceOf(DataApiException.class).hasMessage("Invalid ID list");
                });
    }
    @Test void directDtoCallsStillValidateTypeRangeAndLists() throws Exception {
        var input = (PlateCaptureSearchRequest) mapper(false).readValue(minimal("PLATE"), CaptureSearchRequest.class);
        input.setPageSize(0);
        assertThatThrownBy(() -> DataQueryInputMapper.capture(input)).isInstanceOf(DataApiException.class);
        input.setPageSize(20); input.setTargetIds(List.of());
        assertThatThrownBy(() -> DataQueryInputMapper.capture(input)).isInstanceOf(DataApiException.class);
        input.setTargetIds(null); input.setDataType("FACE");
        assertThatThrownBy(() -> DataQueryInputMapper.capture(input)).isInstanceOf(DataApiException.class);
        input.setDataType("PLATE"); input.getTimeRange().setTo(input.getTimeRange().getFrom());
        assertThatThrownBy(() -> DataQueryInputMapper.capture(input)).isInstanceOf(DataApiException.class);
    }
}
