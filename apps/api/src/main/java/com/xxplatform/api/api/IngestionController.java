package com.xxplatform.api.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.CreateIngestionBatchResponse;
import com.xxplatform.api.generated.model.ReplayIngestionBatchResponse;
import com.xxplatform.api.generated.model.ReplayInput;
import com.xxplatform.api.generated.model.BackfillInput;
import com.xxplatform.api.generated.model.CreateIngestionBackfillResponse;
import com.xxplatform.api.generated.model.TaskCreated;
import com.xxplatform.api.service.ingestion.IngestionBackfillService;
import com.xxplatform.api.service.ingestion.IngestionBackfillService.BackfillCommand;
import com.xxplatform.api.service.ingestion.IngestionBatchReplayService;
import com.xxplatform.api.service.ingestion.IngestionBatchReplayService.ReplayCommand;
import java.util.Collections;
import java.util.Map;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import com.xxplatform.api.generated.model.IngestionBatchInput;
import com.xxplatform.api.service.ingestion.IngestionBatchSubmissionService;
import com.xxplatform.api.service.ingestion.IngestionBatchSubmissionService.SubmissionInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.MissingRequestHeaderException;
import java.time.OffsetDateTime;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ApiResponses.FieldErrorDetail;
import com.xxplatform.api.common.ErrorResponse;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.generated.api.IngestionApi;
import com.xxplatform.api.generated.model.IngestionStatus;
import com.xxplatform.api.generated.model.GetIngestionBatchResponse;
import java.util.UUID;
import com.xxplatform.api.generated.model.ListIngestionBatchesResponse;
import com.xxplatform.api.generated.model.ListIngestionErrorsResponse;
import com.xxplatform.api.generated.model.GetIngestionQualitySummaryResponse;
import com.xxplatform.api.generated.model.IngestionQualitySummary;
import com.xxplatform.api.generated.model.SortOrder;
import com.xxplatform.api.service.ingestion.BatchListQuery;
import com.xxplatform.api.service.ingestion.IngestionQueryService;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ingestion-mock | dev")
public class IngestionController implements IngestionApi {
    private final IngestionQueryService service;
    private final ObjectMapper mapper;
    private final IngestionBatchSubmissionService submissions;
    private final IngestionBatchReplayService replays;
    private final IngestionBackfillService backfills;

    // Existing standalone read tests keep an unavailable write adapter.
    public IngestionController(IngestionQueryService service,
                                         ObjectMapper mapper) {
        this(service, mapper, IngestionBatchSubmissionService.unavailable());
    }

    public IngestionController(IngestionQueryService service,
                                       ObjectMapper mapper,
                                       IngestionBatchSubmissionService submissions) {
        this(service, mapper, submissions, IngestionBatchReplayService.unavailable());
    }

    public IngestionController(IngestionQueryService service,
                                       ObjectMapper mapper,
                                       IngestionBatchSubmissionService submissions,
                                       IngestionBatchReplayService replays) {
        this(service, mapper, submissions, replays, IngestionBackfillService.unavailable());
    }

    @Autowired
    public IngestionController(IngestionQueryService service,
                                       ObjectMapper mapper,
                                       IngestionBatchSubmissionService submissions,
                                       IngestionBatchReplayService replays,
                                       IngestionBackfillService backfills) {
        this.service = service;
        this.mapper = mapper;
        this.submissions = submissions;
        this.replays = replays;
        this.backfills = backfills;
    }

    @Override
    public ResponseEntity<ListIngestionBatchesResponse> listIngestionBatches(
            Integer page, Integer pageSize, String sortBy, SortOrder sortOrder,
            String source, IngestionStatus status) {
        var data = service.list(new BatchListQuery(
                page, pageSize, sortBy, sortOrder, source, status));
        var envelope = ApiResponses.success(
                data, RequestIdFilter.currentTraceIdOrFallback());
        // Keep the shared envelope, while returning the exact generated DTO.
        // Conversion also handles generated enum types such as code="0".
        return ResponseEntity.ok(mapper.convertValue(
                envelope, ListIngestionBatchesResponse.class));
    }

    @Override
    public ResponseEntity<GetIngestionBatchResponse> getIngestionBatch(UUID batchId) {
        var envelope = ApiResponses.success(
                service.get(batchId), RequestIdFilter.currentTraceIdOrFallback());
        return ResponseEntity.ok(mapper.convertValue(envelope, GetIngestionBatchResponse.class));
    }

    @Override
    public ResponseEntity<ListIngestionErrorsResponse> listIngestionErrors(
            UUID batchId, String cursor, Integer pageSize) {
        var envelope = ApiResponses.success(
                service.errors(batchId, cursor, pageSize),
                RequestIdFilter.currentTraceIdOrFallback());
        return ResponseEntity.ok(mapper.convertValue(envelope, ListIngestionErrorsResponse.class));
    }

    @Override
    public ResponseEntity<GetIngestionQualitySummaryResponse> getIngestionQualitySummary(
            String source, OffsetDateTime from, OffsetDateTime to) {
        var data = service.quality(source, from, to);
        var envelope = ApiResponses.success(data, RequestIdFilter.currentTraceIdOrFallback());
        var response = mapper.convertValue(envelope, GetIngestionQualitySummaryResponse.class);
        response.setData(mapper.convertValue(data, PublicQualitySummary.class));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<CreateIngestionBatchResponse> createIngestionBatch(
            String xCSRFToken, String idempotencyKey, IngestionBatchInput ingestionBatchInput) {
        SubmissionInput input = ingestionBatchInput == null ? null : new SubmissionInput(
                ingestionBatchInput.getSource(), ingestionBatchInput.getSourceFileId(),
                ingestionBatchInput.getMappingVersion(), ingestionBatchInput.getDedupVersion());
        var receipt = submissions.submit(xCSRFToken, idempotencyKey, input);
        var envelope = ApiResponses.success(receipt, RequestIdFilter.currentTraceIdOrFallback());
        return ResponseEntity.status(202).body(
                mapper.convertValue(envelope, CreateIngestionBatchResponse.class));
    }

    @Override
    public ResponseEntity<ReplayIngestionBatchResponse> replayIngestionBatch(
            UUID batchId, String xCSRFToken, String idempotencyKey, ReplayInput replayInput) {
        ReplayCommand command = replayInput == null ? null : new ReplayCommand(
                replayInput.getReason(), replayInput.getMappingVersion(), replayInput.getDedupVersion());
        var receipt = replays.replay(batchId, xCSRFToken, idempotencyKey,
                ingestionApprovalHeader(batchId), command);
        var envelope = ApiResponses.success(receipt, RequestIdFilter.currentTraceIdOrFallback());
        var response = mapper.convertValue(envelope, ReplayIngestionBatchResponse.class);
        response.setData(mapper.convertValue(receipt, PublicTaskCreated.class));
        return ResponseEntity.status(202).body(response);
    }

    @Override
    public ResponseEntity<CreateIngestionBackfillResponse> createIngestionBackfill(String xCSRFToken, String idempotencyKey, BackfillInput backfillInput) {
        var range = backfillInput == null ? null : backfillInput.getTimeRange();
        BackfillCommand command = backfillInput == null ? null : new BackfillCommand(
                backfillInput.getSource(), range == null ? null : range.getFrom(),
                range == null ? null : range.getTo(), backfillInput.getReason());
        var receipt = backfills.create(xCSRFToken, idempotencyKey, ingestionApprovalHeader(null), command);
        var envelope = ApiResponses.success(receipt, RequestIdFilter.currentTraceIdOrFallback());
        var response = mapper.convertValue(envelope, CreateIngestionBackfillResponse.class);
        response.setData(mapper.convertValue(receipt, PublicTaskCreated.class));
        return ResponseEntity.status(202).body(response);
    }

    // Optional non-nullable TaskCreated references are omitted when unavailable.
    // Keep error data:null and other required-nullable fields unchanged.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class PublicTaskCreated extends TaskCreated {}

    // This generator omitted the optional approval header from the method.
    // Read the contract header from the current request without changing generated code.
    private String ingestionApprovalHeader(UUID batchId) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) return null;
        var request = servlet.getRequest();
        Object pathParameters = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathParameters instanceof Map<?, ?> values
                && values.get("batchId") instanceof String rawId
                && (batchId == null || !batchId.toString().equalsIgnoreCase(rawId))) {
            throw new DataApiException(ErrorCode.VALIDATION_ERROR,
                    "batchId", "batchId must be a canonical UUID");
        }
        var approvals = Collections.list(request.getHeaders("X-Approval-Id"));
        if (approvals.size() > 1) {
            throw new DataApiException(ErrorCode.VALIDATION_ERROR,
                    "approvalId", "Only one X-Approval-Id header is allowed");
        }
        return approvals.isEmpty() ? null : approvals.get(0);
    }

    // source is optional and is not nullable in the contract. Keep this rule local
    // to this model instead of changing the shared Jackson configuration.
    public static final class PublicQualitySummary extends IngestionQualitySummary {
        @Override
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getSource() {
            return super.getSource();
        }
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> missingHeader(MissingRequestHeaderException error) {
        String field = "X-CSRF-Token".equals(error.getHeaderName())
                ? "xCSRFToken" : "idempotencyKey";
        return handleProblem(new DataApiException(
                ErrorCode.VALIDATION_ERROR, field, "Required request header is missing"));
    }

    // Domain-local validation; reuse the shared error envelope and codes.
    @ExceptionHandler(DataApiException.class)
    public ResponseEntity<ErrorResponse> handleProblem(DataApiException problem) {
        List<FieldErrorDetail> details = problem.field() == null ? List.of()
                : List.of(new FieldErrorDetail(problem.field(), problem.getMessage()));
        var response = ResponseEntity.status(problem.code().httpStatus());
        if (problem.code() == ErrorCode.SYSTEM_UNAVAILABLE
                || problem.code() == ErrorCode.RATE_LIMITED) {
            response.header("Retry-After", "60");
        }
        return response.body(ApiResponses.error(problem.code(), problem.getMessage(),
                RequestIdFilter.currentTraceIdOrFallback(), details));
    }
}
