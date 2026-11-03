package com.xxplatform.api.api;

import com.xxplatform.api.common.*;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/** Advice is restricted to the two week-2 controllers; shared handlers stay intact. */
@RestControllerAdvice(assignableTypes = {CaptureController.class, MapController.class})
@Profile("!ingestion-mock | dev")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataApiExceptionHandler {
    @ExceptionHandler(DataApiException.class)
    public ResponseEntity<ErrorResponse> problem(DataApiException error) {
        var response = ResponseEntity.status(error.code().httpStatus());
        if (error.code() == ErrorCode.SYSTEM_UNAVAILABLE || error.code() == ErrorCode.RATE_LIMITED) response.header("Retry-After", "60");
        List<ApiResponses.FieldErrorDetail> details = error.field() == null ? List.of()
                : List.of(new ApiResponses.FieldErrorDetail(error.field(), error.getMessage()));
        return response.body(ApiResponses.error(error.code(), error.getMessage(), RequestIdFilter.currentTraceIdOrFallback(), details));
    }
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> header(MissingRequestHeaderException error) {
        return problem(new DataApiException(ErrorCode.VALIDATION_ERROR, "xCSRFToken", "Required request header is missing"));
    }
}
