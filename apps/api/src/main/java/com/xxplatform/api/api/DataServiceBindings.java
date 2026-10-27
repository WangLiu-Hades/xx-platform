package com.xxplatform.api.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.service.ingestion.*;
import com.xxplatform.api.service.plate.PlateQueryService;
import com.xxplatform.api.client.ingestion.IngestionQueryAdapter;
import com.xxplatform.api.client.plate.PlateQueryAdapter;
import java.util.Objects;

/**
 * Register one bean only after the trusted DEV-01/DEV-04 bridges are available.
 * Construction is explicit; no application fake, SQL, authentication or new RPC.
 */
public final class DataServiceBindings {
    public interface SharedAccess extends IngestionBatchSubmissionService.WriteAccess,
            IngestionBatchReplayService.ReplayAccess, IngestionBackfillService.BackfillAccess,
            IngestionQueryService.ReadAccess, PlateQueryService.ReadAccess {}
    private final SharedAccess access;
    private final IngestionDomainGateway writes;
    private final IngestionQueryAdapter reads;
    private final PlateQueryAdapter plate;
    public DataServiceBindings(SharedAccess access, IngestionDomainGateway.AtomicSubmission submission,
            IngestionQueryAdapter.Client m03, IngestionQueryAdapter.WindowPolicy windows,
            PlateQueryAdapter.Client plateClient, PlateQueryAdapter.TargetLookup targets,
            ObjectMapper mapper) {
        this.access = Objects.requireNonNull(access);
        this.writes = new IngestionDomainGateway(submission);
        this.reads = new IngestionQueryAdapter(m03, windows, mapper);
        this.plate = new PlateQueryAdapter(plateClient, targets);
    }
    public IngestionBatchSubmissionService submission() { return new IngestionBatchSubmissionService(access, writes); }
    public IngestionBatchReplayService replay() { return new IngestionBatchReplayService(access, writes); }
    public IngestionBackfillService backfill() { return new IngestionBackfillService(access, writes); }
    public IngestionQueryService.ReadAccess access() { return access; }
    public IngestionQueryAdapter reads() { return reads; }
    public PlateQueryService plate() { return new PlateQueryService(access, plate); }
    public com.xxplatform.api.service.plate.CaptureExportSourceAdapter exports(
            com.xxplatform.api.service.plate.CaptureExportSourceAdapter.ContextLookup contexts,
            java.time.Clock clock) {
        return new com.xxplatform.api.service.plate.CaptureExportSourceAdapter(access, contexts, plate, clock);
    }
}
