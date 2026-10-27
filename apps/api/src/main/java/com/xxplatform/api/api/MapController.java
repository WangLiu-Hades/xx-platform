package com.xxplatform.api.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.generated.api.MapApi;
import com.xxplatform.api.generated.model.*;
import com.xxplatform.api.service.plate.PlateQueryService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ingestion-mock | dev")
public class MapController implements MapApi {
    private final PlateQueryService service;
    private final ObjectMapper mapper;
    public MapController(PlateQueryService service, ObjectMapper mapper) { this.service = service; this.mapper = mapper; }
    @Override public ResponseEntity<GetMapFeaturesResponse> getMapFeatures(String bbox, Integer zoom, Integer limit, UUID contextId, OffsetDateTime from, OffsetDateTime to, DataType dataType, UUID siteId, String layer, String aggregation, List<UUID> siteIds, List<UUID> deviceIds, String regionCode) {
        ApiInputValidator.currentQueryUuids("contextId", "siteId", "siteIds", "deviceIds");
        var query = DataQueryInputMapper.map(bbox, zoom, limit, contextId, from, to,
                dataType == null ? null : dataType.toString(), siteId,
                layer == null ? null : layer.toString(), aggregation == null ? null : aggregation.toString(),
                siteIds, deviceIds, regionCode);
        var data = service.map(query);
        var projected = mapper.convertValue(data, GeoFeatureCollection.class);
        for (var feature : projected.getFeatures()) {
            feature.setProperties(mapper.convertValue(feature.getProperties(), PublicFeatureProperties.class));
        }
        var response = mapper.convertValue(ApiResponses.success(projected,
                RequestIdFilter.currentTraceIdOrFallback()), GetMapFeaturesResponse.class);
        response.setData(projected);
        return ResponseEntity.ok(response);
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class PublicFeatureProperties extends MapFeatureProperties {}
}
