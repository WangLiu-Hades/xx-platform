package com.xxplatform.api.service.ingestion;

import com.xxplatform.api.common.ErrorCode;

public final class DataApiException extends RuntimeException {
    private final ErrorCode code;
    private final String field;

    public DataApiException(ErrorCode code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public ErrorCode code() { return code; }
    public String field() { return field; }
}
