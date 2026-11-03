package com.xxplatform.api.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.xxplatform.api.service.plate.PlateQueryService.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static com.xxplatform.api.api.ApiInputValidator.*;

public final class DataQueryInputMapper {
    private DataQueryInputMapper() {}
    public static Set<String> captureFields(String type) {
        var result = new java.util.HashSet<>(Set.of("dataType", "timeRange", "siteIds", "cursor", "pageSize",
                "sortBy", "sortOrder", "targetIds", "deviceIds"));
        switch (type) {
            case "PLATE" -> result.addAll(Set.of("plateNumber", "plateColor"));
            case "FENCE" -> result.addAll(Set.of("imsi", "imei"));
            case "MAC" -> result.add("macAddress");
            case "FACE" -> { }
            default -> throw invalid("dataType", "Unsupported dataType");
        }
        return Set.copyOf(result);
    }
    /** Read the generated DTO directly; JSON type discriminators are not ordinary round-trip fields. */
    public static CaptureQuery capture(com.xxplatform.api.generated.model.CaptureSearchRequest request) {
        if (request instanceof com.xxplatform.api.generated.model.PlateCaptureSearchRequest value) {
            Map<String, String> exact = new LinkedHashMap<>();
            if (value.getPlateNumber() != null) exact.put("plateNumber", value.getPlateNumber());
            if (value.getPlateColor() != null) exact.put("plateColor", value.getPlateColor());
            return captureValues("PLATE", value.getDataType(), value.getTimeRange(),
                    value.getSiteIds(), value.getTargetIds(), value.getDeviceIds(), value.getCursor(),
                    value.getPageSize(), value.getSortBy(), value.getSortOrder(), exact);
        }
        if (request instanceof com.xxplatform.api.generated.model.FenceCaptureSearchRequest value) {
            Map<String, String> exact = new LinkedHashMap<>();
            if (value.getImsi() != null) exact.put("imsi", value.getImsi());
            if (value.getImei() != null) exact.put("imei", value.getImei());
            return captureValues("FENCE", value.getDataType(), value.getTimeRange(),
                    value.getSiteIds(), value.getTargetIds(), value.getDeviceIds(), value.getCursor(),
                    value.getPageSize(), value.getSortBy(), value.getSortOrder(), exact);
        }
        if (request instanceof com.xxplatform.api.generated.model.MacCaptureSearchRequest value) {
            Map<String, String> exact = new LinkedHashMap<>();
            if (value.getMacAddress() != null) exact.put("macAddress", value.getMacAddress());
            return captureValues("MAC", value.getDataType(), value.getTimeRange(),
                    value.getSiteIds(), value.getTargetIds(), value.getDeviceIds(), value.getCursor(),
                    value.getPageSize(), value.getSortBy(), value.getSortOrder(), exact);
        }
        if (request instanceof com.xxplatform.api.generated.model.FaceCaptureSearchRequest value) {
            Map<String, String> exact = new LinkedHashMap<>();
            return captureValues("FACE", value.getDataType(), value.getTimeRange(),
                    value.getSiteIds(), value.getTargetIds(), value.getDeviceIds(), value.getCursor(),
                    value.getPageSize(), value.getSortBy(), value.getSortOrder(), exact);
        }
        throw invalid("body", "A supported capture search request is required");
    }
    private static CaptureQuery captureValues(String type, String declaredType,
            com.xxplatform.api.generated.model.TimeRange range, List<UUID> siteIds,
            List<UUID> targetIds, List<UUID> deviceIds, String cursor, Integer pageSize,
            Object sortBy, Object sortOrder, Map<String, String> exact) {
        if (!type.equals(declaredType)) throw invalid("dataType", "dataType must match the request type");
        if (range == null || range.getFrom() == null || range.getTo() == null) {
            throw invalid("timeRange", "from and to are required");
        }
        var from = range.getFrom().withOffsetSameInstant(java.time.ZoneOffset.UTC);
        var to = range.getTo().withOffsetSameInstant(java.time.ZoneOffset.UTC);
        if (!from.isBefore(to)) throw invalid("timeRange.to", "to must be later than from");
        int size = pageSize == null ? 20 : pageSize;
        if (size < 1 || size > 100) throw invalid("pageSize", "Number is outside its allowed range");
        String field = sortBy == null ? "captureTime" : sortBy.toString();
        String order = sortOrder == null ? "DESC" : sortOrder.toString();
        if (!Set.of("captureTime", "captureId").contains(field)) throw invalid("sortBy", "Unsupported sort field");
        if (!Set.of("ASC", "DESC").contains(order)) throw invalid("sortOrder", "Unsupported sort order");
        checkIds(siteIds, "siteIds", false, Integer.MAX_VALUE);
        checkIds(targetIds, "targetIds", true, 100);
        checkIds(deviceIds, "deviceIds", false, 100);
        return new CaptureQuery(type, from, to, siteIds, targetIds, deviceIds,
                cursor, size, field, order, exact);
    }
    private static void checkIds(List<UUID> values, String field, boolean nonempty, int maximum) {
        if (values != null && (values.size() > maximum || (nonempty && values.isEmpty())
                || values.stream().anyMatch(java.util.Objects::isNull))) {
            throw invalid(field, "Invalid ID list");
        }
    }

    public static CaptureQuery capture(JsonNode body, boolean rawInput) {
        if (body == null || !body.isObject()) throw invalid("body", "An object is required");
        String type = text(body, "dataType", true);
        var allowed = captureFields(type);
        if (rawInput) object(body, "body", allowed);
        var range = body.get("timeRange");
        object(range, "timeRange", Set.of("from", "to"));
        var from = instant(text(range, "from", true), "timeRange.from");
        var to = instant(text(range, "to", true), "timeRange.to");
        if (!from.isBefore(to)) throw invalid("timeRange.to", "to must be later than from");
        int size = integer(body, "pageSize", 20, 1, 100);
        String sortBy = text(body, "sortBy", false);
        String order = text(body, "sortOrder", false);
        if (sortBy == null) sortBy = "captureTime";
        if (order == null) order = "DESC";
        if (!Set.of("captureTime", "captureId").contains(sortBy)) throw invalid("sortBy", "Unsupported sort field");
        if (!Set.of("ASC", "DESC").contains(order)) throw invalid("sortOrder", "Unsupported sort order");
        Map<String, String> exact = new LinkedHashMap<>();
        for (String field : List.of("plateNumber", "plateColor", "imsi", "imei", "macAddress")) {
            String value = text(body, field, false);
            if (value != null) exact.put(field, value);
        }
        return new CaptureQuery(type, from, to, ids(body, "siteIds", false, Integer.MAX_VALUE),
                ids(body, "targetIds", true, 100), ids(body, "deviceIds", false, 100),
                text(body, "cursor", false), size, sortBy, order, exact);
    }
    private static List<UUID> ids(JsonNode body, String field, boolean nonempty, int maximum) {
        JsonNode list = body.get(field);
        if (list == null || list.isNull()) return null;
        if (!list.isArray() || list.size() > maximum || (nonempty && list.isEmpty())) throw invalid(field, "Invalid ID list");
        List<UUID> result = new ArrayList<>();
        for (JsonNode item : list) {
            if (!item.isTextual()) throw invalid(field, "UUID strings are required");
            result.add(uuid(item.textValue(), field));
        }
        return List.copyOf(result);
    }
    public static MapQuery map(String bbox, Integer zoom, Integer limit, UUID contextId,
            OffsetDateTime from, OffsetDateTime to, String dataType, UUID siteId, String layer,
            String aggregation, List<UUID> siteIds, List<UUID> deviceIds, String regionCode) {
        if (bbox == null || !bbox.matches("-?\\d+(?:\\.\\d+)?,-?\\d+(?:\\.\\d+)?,-?\\d+(?:\\.\\d+)?,-?\\d+(?:\\.\\d+)?")) throw invalid("bbox", "Four comma-separated coordinates are required");
        String[] parts = bbox.split(",");
        double[] n = new double[4];
        for (int i = 0; i < 4; i++) {
            try { n[i] = Double.parseDouble(parts[i]); }
            catch (NumberFormatException error) { throw invalid("bbox", "Coordinates are invalid"); }
            if (!Double.isFinite(n[i])) throw invalid("bbox", "Coordinates must be finite");
        }
        if (n[0] < -180 || n[2] > 180 || n[1] < -90 || n[3] > 90 || n[0] >= n[2] || n[1] >= n[3]) throw invalid("bbox", "Coordinate range or order is invalid");
        if (zoom == null || zoom < 3 || zoom > 20) throw invalid("zoom", "zoom must be 3 to 20");
        int max = limit == null ? 2000 : limit;
        if (max < 1 || max > 2000) throw invalid("limit", "limit must be 1 to 2000");
        if (from != null && to != null && !from.isBefore(to)) throw invalid("to", "to must be later than from");
        if (dataType != null && !Set.of("PLATE", "FENCE", "MAC", "FACE").contains(dataType)) throw invalid("dataType", "Unsupported dataType");
        if (layer != null && !Set.of("CAPTURE", "SITE").contains(layer)) throw invalid("layer", "Unsupported map layer");
        if (aggregation != null && !Set.of("AUTO", "POINT", "CLUSTER").contains(aggregation)) throw invalid("aggregation", "Unsupported aggregation");
        if (siteIds != null && (siteIds.size() > 100 || siteIds.stream().anyMatch(java.util.Objects::isNull))) throw invalid("siteIds", "Invalid site list");
        if (deviceIds != null && (deviceIds.size() > 100 || deviceIds.stream().anyMatch(java.util.Objects::isNull))) throw invalid("deviceIds", "Invalid device list");
        if (regionCode != null && (regionCode.isEmpty() || regionCode.length() > 200)) throw invalid("regionCode", "Invalid region code");
        return new MapQuery(new Bounds(n[0], n[1], n[2], n[3]), zoom, max, contextId, from, to,
                dataType, siteId, layer, aggregation, siteIds, deviceIds, regionCode);
    }
}
