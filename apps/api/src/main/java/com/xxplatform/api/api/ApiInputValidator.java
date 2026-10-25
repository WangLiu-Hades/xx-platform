package com.xxplatform.api.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/** Domain input checks only. Does not modify global Jackson or authentication. */
public final class ApiInputValidator {
    private ApiInputValidator() {}
    public static DataApiException invalid(String field, String reason) {
        return new DataApiException(ErrorCode.VALIDATION_ERROR, field, reason);
    }
    public static void object(JsonNode node, String field, Set<String> fields) {
        if (node == null || !node.isObject()) throw invalid(field, "An object is required");
        node.fieldNames().forEachRemaining(key -> {
            if (!fields.contains(key)) throw invalid(field + "." + key, "Unexpected field");
            if (node.get(key).isNull()) throw invalid(field + "." + key, "Explicit null is not allowed");
        });
    }
    public static String text(JsonNode node, String key, boolean required) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            if (required) throw invalid(key, "Field is required");
            return null;
        }
        if (!value.isTextual()) throw invalid(key, "A string is required");
        return value.textValue();
    }
    public static OffsetDateTime instant(String value, String field) {
        if (value == null || !value.matches("(?i)\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})")) {
            throw invalid(field, "RFC3339 timestamp with timezone is required");
        }
        try { return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC); }
        catch (java.time.DateTimeException error) { throw invalid(field, "Timestamp is invalid"); }
    }
    public static UUID uuid(String value, String field) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException | NullPointerException error) { throw invalid(field, "UUID is invalid"); }
    }
    public static void currentPathUuid(String field, UUID id) {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet)) return;
        Object variables = servlet.getRequest().getAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof java.util.Map<?, ?> values && values.get(field) instanceof String raw) {
            if (!uuid(raw, field).equals(id)) throw invalid(field, "Path UUID does not match");
        }
    }
    public static void currentQueryUuids(String... fields) {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet)) return;
        for (String field : fields) {
            String[] values = servlet.getRequest().getParameterValues(field);
            if (values != null) for (String value : values) uuid(value, field);
        }
    }
    public static int integer(JsonNode node, String field, int fallback, int min, int max) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw invalid(field, "An integer is required");
        int number = value.intValue();
        if (number < min || number > max) throw invalid(field, "Number is outside its allowed range");
        return number;
    }
}
