package com.xxplatform.api.client.ingestion;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.service.ingestion.BatchListQuery;
import com.xxplatform.api.service.ingestion.IngestionQueryService.*;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.time.format.DateTimeFormatter;
import java.util.*;

// 统一转换接入查询参数，并检查数据服务的返回结构。
public final class IngestionContractAdapter {
    private final ObjectMapper mapper;

    public IngestionContractAdapter(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper).copy()
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
    }

    public Map<String, Object> listSpec(BatchListQuery query) {
        // 未提供的可选条件直接省略，不向数据服务传递 null。
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("page", query.page());
        out.put("pageSize", query.pageSize());
        out.put("sortBy", query.sortBy());
        out.put("sortOrder", query.sortOrder().toString());
        if (query.source() != null) out.put("source", query.source());
        if (query.status() != null) out.put("status", query.status().toString());
        return Collections.unmodifiableMap(out);
    }

    public Map<String, Object> trustedScope(ReadScope scope) {
        // 授权范围只能来自服务端，不能使用前端自报的批次集合。
        if (scope == null || scope.principalId() == null
                || scope.version() == null || scope.version().isBlank()) throw invalidReply();
        List<String> ids = new ArrayList<>();
        scope.batchIds().forEach(id -> ids.add(id.toString()));
        Collections.sort(ids);
        return Map.of("principal_id", scope.principalId().toString(),
                "version", scope.version(), "batch_ids", List.copyOf(ids));
    }

    public Map<String, Object> qualitySpec(QualityQuery query) {
        if (query.from() == null || query.to() == null) {
            throw new DataApiException(ErrorCode.SYSTEM_UNAVAILABLE, "timeRange",
                    "An explicit quality window or a shared default-window policy is required");
        }
        if (!query.from().isBefore(query.to())) {
            throw new DataApiException(ErrorCode.VALIDATION_ERROR, "to", "to must be later than from");
        }
        // 将带偏移的时间统一转换为 UTC，查询仍按左闭右开区间执行。
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", DateTimeFormatter.ISO_INSTANT.format(query.from().toInstant()));
        out.put("to", DateTimeFormatter.ISO_INSTANT.format(query.to().toInstant()));
        if (query.source() != null) out.put("source", query.source());
        return Collections.unmodifiableMap(out);
    }

    public BatchPage readPage(Map<String, Object> raw, BatchListQuery request, ReadScope scope) {
        int page = integer(raw, "page", 1), size = integer(raw, "pageSize", 1);
        int total = integer(raw, "total", 0);
        List<?> items = items(raw);
        if (page != request.page() || size != request.pageSize()
                || items.size() > size || items.size() > total || total > scope.batchIds().size()) {
            throw invalidReply();
        }
        List<BatchRow> rows = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Object item : items) {
            BatchRow row = readBatch(object(item), null, scope);
            // 同时检查批次权限、重复记录和查询条件，避免返回其他批次的数据。
            if (!seen.add(row.batchId())
                    || (request.source() != null && !request.source().equals(row.source()))
                    || (request.status() != null && request.status() != row.status())) throw invalidReply();
            rows.add(row);
        }
        return new BatchPage(rows, page, size, total);
    }

    public BatchRow readBatch(Map<String, Object> raw, UUID requestedId, ReadScope scope) {
        required(raw, "batchId", "status", "createdAt");
        for (String field : List.of("source", "mappingVersion", "dedupVersion")) text(raw, field);
        for (String field : List.of("acceptedCount", "rejectedCount", "duplicateCount")) integer(raw, field, 0);
        BatchRow row = decode(raw, BatchRow.class);
        if (row.batchId() == null || row.status() == null || row.createdAt() == null
                || !scope.batchIds().contains(row.batchId())
                || (requestedId != null && !requestedId.equals(row.batchId()))) throw invalidReply();
        // 不把内部数据库诊断原文作为公开的错误摘要返回。
        return new BatchRow(row.batchId(), row.source(), row.mappingVersion(), row.dedupVersion(),
                row.status(), row.acceptedCount(), row.rejectedCount(), row.duplicateCount(),
                row.createdAt(), row.sourceFileId(), row.taskId(), row.finishedAt(),
                row.errorSummary() == null ? null : "Batch processing reported an error.");
    }

    public ErrorPage readErrors(Map<String, Object> raw, int requestedSize) {
        int size = integer(raw, "pageSize", 1);
        List<?> values = items(raw);
        Object cursor = raw.get("nextCursor");
        if (!raw.containsKey("nextCursor") || size != requestedSize || values.size() > size
                || (cursor != null && (!(cursor instanceof String) || ((String) cursor).isBlank()))
                || (cursor != null && values.isEmpty())) throw invalidReply();
        List<ErrorRow> rows = new ArrayList<>();
        int previousRow = 0;
        for (Object value : values) {
            Map<String, Object> item = object(value);
            required(item, "code");
            text(item, "reason");
            int rowNumber = integer(item, "rowNumber", 1);
            if (rowNumber < previousRow) throw invalidReply();
            previousRow = rowNumber;
            ErrorRow row = decode(item, ErrorRow.class);
            if (row.code() == null || row.reason() == null || row.reason().isBlank()) throw invalidReply();
            rows.add(row);
        }
        // 游标由数据服务生成和验证，这里不解析、不重建。
        return new ErrorPage(rows, (String) cursor, size);
    }

    public QualitySummary readQuality(Map<String, Object> raw, String requestedSource) {
        required(raw, "asOf");
        for (String field : List.of("acceptedCount", "rejectedCount", "duplicateCount",
                "unmappedDeviceCount", "invalidCoordinateCount")) integer(raw, field, 0);
        QualitySummary result = decode(raw, QualitySummary.class);
        if (result.asOf() == null || !Objects.equals(requestedSource, result.source())) throw invalidReply();
        return result;
    }

    public DataApiException workerFailure(String label) {
        return toPublicFailure(label);
    }

    public static DataApiException toPublicFailure(String label) {
        // 只转换公开错误码，不向前端暴露 SQL、堆栈或原始标识。
        ErrorCode code;
        if (label == null) code = ErrorCode.SYSTEM_UNAVAILABLE;
        else switch (label) {
            case "DATA_QUERY_INVALID":
            case "DATA_INVALID_FILTER": code = ErrorCode.DATA_INVALID_FILTER; break;
            case "DATA_CURSOR_INVALID": code = ErrorCode.DATA_CURSOR_INVALID; break;
            case "DATA_NOT_FOUND": code = ErrorCode.DATA_NOT_FOUND; break;
            case "DATA_QUERY_TOO_BROAD": code = ErrorCode.DATA_QUERY_TOO_BROAD; break;
            case "CONTEXT_EXPIRED": code = ErrorCode.CONTEXT_EXPIRED; break;
            case "PERMISSION_DATA_SCOPE": code = ErrorCode.PERMISSION_DATA_SCOPE; break;
            case "PERMISSION_DENIED": code = ErrorCode.PERMISSION_DENIED; break;
            case "REQUIREMENT_PENDING": code = ErrorCode.REQUIREMENT_PENDING; break;
            default: code = ErrorCode.SYSTEM_UNAVAILABLE;
        }
        return new DataApiException(code, code == ErrorCode.DATA_CURSOR_INVALID ? "cursor" : null,
                "Data query could not be completed");
    }

    private static int integer(Map<String, Object> raw, String name, int minimum) {
        if (raw == null) throw invalidReply();
        Object value = raw.get(name);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw invalidReply();
        }
        long number = ((Number) value).longValue();
        if (number < minimum || number > Integer.MAX_VALUE) throw invalidReply();
        return (int) number;
    }
    private static void text(Map<String, Object> raw, String name) {
        if (raw == null || !(raw.get(name) instanceof String value) || value.isBlank()) throw invalidReply();
    }
    private static List<?> items(Map<String, Object> raw) {
        if (raw == null || !(raw.get("items") instanceof List<?> values)) throw invalidReply();
        return values;
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?>)) throw invalidReply();
        return (Map<String, Object>) value;
    }
    private static void required(Map<String, Object> raw, String... names) {
        if (raw == null) throw invalidReply();
        for (String name : names) if (raw.get(name) == null) throw invalidReply();
    }
    private <T> T decode(Map<String, Object> raw, Class<T> type) {
        try { return mapper.convertValue(raw, type); }
        catch (IllegalArgumentException failure) { throw invalidReply(); }
    }
    private static DataApiException invalidReply() {
        return new DataApiException(ErrorCode.SYSTEM_UNAVAILABLE, null,
                "Data provider returned an invalid contract result");
    }
}
