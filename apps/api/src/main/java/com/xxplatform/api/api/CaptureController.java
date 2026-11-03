package com.xxplatform.api.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.generated.api.CaptureApi;
import com.xxplatform.api.generated.model.*;
import com.xxplatform.api.service.plate.PlateQueryService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ingestion-mock | dev")
public class CaptureController implements CaptureApi {
    private final PlateQueryService service;
    private final ObjectMapper mapper;
    public CaptureController(PlateQueryService service, ObjectMapper mapper) { this.service = service; this.mapper = mapper; }
    @Override public ResponseEntity<SearchCapturesResponse> searchCaptures(String xCSRFToken, CaptureSearchRequest captureSearchRequest) {
        var query = DataQueryInputMapper.capture(captureSearchRequest);
        var data = service.search(xCSRFToken, query);
        // Only PLATE is connected by this domain port. Preserve nullable required IDs/coordinates.
        var page = mapper.convertValue(data, CaptureSearchResponse.class);
        List<CaptureRecord> items = new ArrayList<>();
        for (CaptureRecord item : page.getItems()) {
            var row = mapper.convertValue(item, PlateCaptureRecord.class);
            row.setPlate(mapper.convertValue(row.getPlate(), PublicPlateFields.class));
            items.add(row);
        }
        page.setItems(items);
        var response = mapper.convertValue(ApiResponses.success(page,
                RequestIdFilter.currentTraceIdOrFallback()), SearchCapturesResponse.class);
        // Retain the projected nested subtype rather than converting it back to the generated base.
        response.setData(page);
        return ResponseEntity.ok(response);
    }
    @Override public ResponseEntity<GetCaptureResponse> getCapture(UUID captureId) {
        ApiInputValidator.currentPathUuid("captureId", captureId);
        var data = service.get(captureId);
        var projected = mapper.convertValue(data, CaptureDetail.class);
        var row = mapper.convertValue(projected.getRecord(), PlateCaptureRecord.class);
        row.setPlate(mapper.convertValue(row.getPlate(), PublicPlateFields.class));
        projected.setRecord(row);
        var response = mapper.convertValue(ApiResponses.success(projected,
                RequestIdFilter.currentTraceIdOrFallback()), GetCaptureResponse.class);
        response.setData(projected);
        return ResponseEntity.ok(response);
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class PublicPlateFields extends PlateRecord {}
}
