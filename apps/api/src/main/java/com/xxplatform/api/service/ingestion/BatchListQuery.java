package com.xxplatform.api.service.ingestion;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.IngestionStatus;
import com.xxplatform.api.generated.model.SortOrder;
import java.util.Set;

public record BatchListQuery(
        Integer page, Integer pageSize, String sortBy,
        SortOrder sortOrder, String source, IngestionStatus status) {

    public BatchListQuery {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        sortBy = sortBy == null ? "createdAt" : sortBy;
        // Match the existing DEV-04 BatchListSpec default.
        sortOrder = sortOrder == null ? SortOrder.DESC : sortOrder;
        if (page < 1) invalid("page", "page must be at least 1");
        if (pageSize < 1 || pageSize > 100) {
            invalid("pageSize", "pageSize must be between 1 and 100");
        }
        if (!Set.of("createdAt", "status").contains(sortBy)) {
            invalid("sortBy", "sortBy must be createdAt or status");
        }
    }

    private static void invalid(String field, String message) {
        throw new DataApiException(ErrorCode.VALIDATION_ERROR, field, message);
    }
}
