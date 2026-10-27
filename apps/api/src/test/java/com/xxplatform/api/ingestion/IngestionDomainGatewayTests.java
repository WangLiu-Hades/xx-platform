package com.xxplatform.api.ingestion;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.TaskStatus;
import com.xxplatform.api.service.ingestion.*;
import com.xxplatform.api.service.ingestion.IngestionDomainGateway.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class IngestionDomainGatewayTests {
    private static UUID id(long n) { return new UUID(0, n); }
    private static OffsetDateTime time(String value) { return OffsetDateTime.parse(value); }
    private static final UUID FILE=id(1), OLD=id(2), NEW=id(3), APPROVAL=id(4);
    private final OffsetDateTime now=time("2026-10-24T12:00:00Z");
    private Source source = new Source("PLATE_A",true,Map.of("map1","dedup1","map2","dedup2"),
            "map2","dedup2",time("2026-01-01T00:00:00Z"),now,"policy1","source-v1");
    private FileInfo file = new FileInfo(FILE,"ACTIVE","RAW_INGESTION","PLATE_A",
            now.plusDays(1),true,"file-v1");
    private BatchInfo original = new BatchInfo(OLD,"PLATE_A",FILE,"map1","dedup1",null,null,true,"batch-v1");
    private FrozenInput frozen;
    private PublicRequest request;
    private int commits;
    private final LockedCatalog catalog = new LockedCatalog() {
        public OffsetDateTime now() { return now; }
        public Source source(String code) { return source; }
        public FileInfo file(UUID value) { return file; }
        public BatchInfo batch(UUID value) { return original; }
        public long nextReplayGeneration(UUID value) { return 2; }
    };
    private final IngestionDomainGateway gateway = new IngestionDomainGateway(
            (actor, operation, key, approval, publicRequest, planner) -> {
                request = publicRequest;
                FrozenInput candidate = planner.plan(catalog);
                if (candidate.approvalRequired() && approval == null) {
                    throw new DataApiException(ErrorCode.PERMISSION_DENIED,null,"Approval required");
                }
                frozen=candidate; commits++;
                return new Committed(NEW,id(30),TaskStatus.PENDING,now);
            });
    private IngestionBatchSubmissionService.SubmissionReceipt create() {
        var service = new IngestionBatchSubmissionService(
                csrf -> new IngestionBatchSubmissionService.AuthorizedActor(id(99),"scope1"), gateway);
        return service.submit("test-csrf-token-at-least-16","test-idempotency-key-001",
                new IngestionBatchSubmissionService.SubmissionInput("PLATE_A",FILE,"map1","dedup1"));
    }
    private void replay(String mapping, String dedup, UUID approval) {
        gateway.createOrReplay(new IngestionBatchReplayService.ReplayActor(id(99),"scope1"),
                "replayIngestionBatch",OLD,"test-key-001",approval,
                new IngestionBatchReplayService.ReplayCommand("Corrected mapping rules",mapping,dedup));
    }
    private void backfill(String from, String to) {
        gateway.createOrReplay(new IngestionBackfillService.BackfillActor(id(99),"scope1"),
                "createIngestionBackfill","test-key-001",APPROVAL,
                new IngestionBackfillService.BackfillCommand("PLATE_A",time(from),time(to),"Recover missing interval"));
    }
    private void fails(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DataApiException.class,
                failure -> assertThat(failure.code()).isEqualTo(code));
        assertThat(commits).isZero();
    }
    @Test void createFreezesVersionsAndReturnsCommittedIds() {
        assertThat(create().batchId()).isEqualTo(NEW);
        assertThat(frozen.mode()).isEqualTo("INGEST");
        assertThat(frozen.mappingVersion()).isEqualTo("map1");
        assertThat(frozen.fileVersion()).isEqualTo("file-v1");
        assertThat(frozen.approvalRequired()).isFalse();
        assertThat(commits).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"PROCESSING","QUARANTINED","DELETED"})
    void inactiveFileCannotCreateTask(String status) {
        file=new FileInfo(FILE,status,"RAW_INGESTION","PLATE_A",now.plusDays(1),true,"v1");
        fails(ErrorCode.FILE_INVALID,this::create);
    }
    @Test void wrongFileCategoryCannotCreateTask() {
        file=new FileInfo(FILE,"ACTIVE","FACE_SEARCH","PLATE_A",now.plusDays(1),true,"v1");
        fails(ErrorCode.FILE_INVALID,this::create);
    }
    @Test void wrongFileSourceCannotCreateTask() {
        file=new FileInfo(FILE,"ACTIVE","RAW_INGESTION","OTHER",now.plusDays(1),true,"v1");
        fails(ErrorCode.FILE_INVALID,this::create);
    }
    @Test void expiredFileCannotCreateTask() {
        file=new FileInfo(FILE,"ACTIVE","RAW_INGESTION","PLATE_A",now,true,"v1");
        fails(ErrorCode.FILE_EXPIRED,this::create);
    }
    @Test void absentSourceIsUnavailableForBusiness() {
        source=null; fails(ErrorCode.REQUIREMENT_PENDING,this::create);
    }
    @Test void replayInheritsOmittedVersionsAndPreservesOriginal() {
        BatchInfo before=original;
        replay(null,null,APPROVAL);
        assertThat(frozen.originalBatchId()).isEqualTo(OLD);
        assertThat(frozen.replayGeneration()).isEqualTo(2L);
        assertThat(frozen.mappingVersion()).isEqualTo("map1");
        assertThat(frozen.dedupVersion()).isEqualTo("dedup1");
        assertThat(frozen.approvalRequired()).isTrue();
        assertThat(request.sourceBatchId()).isEqualTo(OLD);
        assertThat(original).isEqualTo(before);
    }
    @Test void replayAcceptsAnExplicitCompatiblePair() {
        replay("map2","dedup2",APPROVAL);
        assertThat(frozen.mappingVersion()).isEqualTo("map2");
    }
    @Test void replayRejectsIncompatibleVersions() {
        fails(ErrorCode.DATA_INVALID_FILTER,()->replay("map2",null,APPROVAL));
    }
    @Test void replayRejectsMissingOriginal() {
        original=null; fails(ErrorCode.DATA_NOT_FOUND,()->replay(null,null,APPROVAL));
    }
    @Test void replayDoesNotRestoreExpiredData() {
        original=new BatchInfo(OLD,"PLATE_A",FILE,"map1","dedup1",null,null,false,"v1");
        fails(ErrorCode.FILE_EXPIRED,()->replay(null,null,APPROVAL));
    }
    @Test void replayCannotSkipSharedApprovalCheck() {
        fails(ErrorCode.PERMISSION_DENIED,()->replay(null,null,null));
    }
    @Test void backfillFreezesUtcAndApprovedDefaultVersions() {
        backfill("2026-10-24T18:00:00+08:00","2026-10-24T19:00:00+08:00");
        assertThat(frozen.from()).isEqualTo(time("2026-10-24T10:00:00Z"));
        assertThat(frozen.to()).isEqualTo(time("2026-10-24T11:00:00Z"));
        assertThat(frozen.mappingVersion()).isEqualTo("map2");
        assertThat(frozen.dedupVersion()).isEqualTo("dedup2");
        assertThat(frozen.approvalRequired()).isTrue();
    }
    @Test void backfillRejectsExpiredRange() {
        fails(ErrorCode.DATA_INVALID_FILTER,()->backfill("2025-01-01T00:00:00Z","2025-01-02T00:00:00Z"));
    }
    @Test void backfillRejectsFutureRange() {
        fails(ErrorCode.DATA_INVALID_FILTER,()->backfill("2026-10-24T11:00:00Z","2026-10-24T13:00:00Z"));
    }
    @Test void sharedSubmissionFailureIsNotReportedAsSuccess() {
        var unavailable = new IngestionDomainGateway((a,o,k,p,r,plan)-> {
            throw new DataApiException(ErrorCode.SYSTEM_UNAVAILABLE,null,"Shared transaction unavailable");
        });
        fails(ErrorCode.SYSTEM_UNAVAILABLE,()->unavailable.createOrReplay(
                new IngestionBatchSubmissionService.AuthorizedActor(id(99),"scope1"),
                "createIngestionBatch","test-key-001",
                new IngestionBatchSubmissionService.SubmissionInput("PLATE_A",FILE,"map1","dedup1")));
    }
}
