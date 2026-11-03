package com.xxplatform.api.ingestion;

import com.xxplatform.api.client.ingestion.QualityQueryWindowPolicy;
import com.xxplatform.api.client.ingestion.IngestionQueryAdapter.Window;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.service.ingestion.DataApiException;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ReadScope;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class QualityQueryWindowPolicyTests {
    private static OffsetDateTime t(String text) { return OffsetDateTime.parse(text); }
    private final OffsetDateTime start=t("2026-01-01T00:00:00Z");
    private final OffsetDateTime end=t("2026-10-25T00:00:00Z");
    private final ReadScope scope=new ReadScope(new UUID(0,99),"scope1",Set.of(new UUID(0,1)));
    private QualityQueryWindowPolicy policy(Duration budget) {
        return new QualityQueryWindowPolicy(current -> new Window(start,end),budget);
    }
    private void fails(ErrorCode code, Runnable work) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(DataApiException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }

    @Test void omittedTimesUseTheWholeRetainedWindow() {
        // 不传时间时应使用完整留存范围，不能悄悄改成最近几天。
        Window result=policy(Duration.ofDays(366)).resolve(null,null,scope);
        assertThat(result.from()).isEqualTo(start);
        assertThat(result.to()).isEqualTo(end);
    }
    @Test void omittedStartUsesTheRetainedLowerBoundary() {
        Window result=policy(Duration.ofDays(366)).resolve(null,end.minusDays(1),scope);
        assertThat(result.from()).isEqualTo(start);
        assertThat(result.to()).isEqualTo(end.minusDays(1));
    }
    @Test void omittedEndUsesTheRetainedUpperBoundary() {
        Window result=policy(Duration.ofDays(366)).resolve(end.minusDays(2),null,scope);
        assertThat(result.to()).isEqualTo(end);
    }
    @Test void explicitTimesKeepTheirInstantsAndReturnUtc() {
        Window result=policy(Duration.ofDays(366)).resolve(
                t("2026-10-24T18:00:00+08:00"),t("2026-10-24T19:00:00+08:00"),scope);
        assertThat(result.from()).isEqualTo(t("2026-10-24T10:00:00Z"));
        assertThat(result.to()).isEqualTo(t("2026-10-24T11:00:00Z"));
        assertThat(result.from().getOffset()).isEqualTo(ZoneOffset.UTC);
    }
    @Test void equalOrReversedTimesAreInvalid() {
        fails(ErrorCode.VALIDATION_ERROR,()->policy(Duration.ofDays(366)).resolve(end,end,scope));
        fails(ErrorCode.VALIDATION_ERROR,()->policy(Duration.ofDays(366)).resolve(end,start,scope));
    }
    @Test void olderThanRetentionIsRejected() {
        fails(ErrorCode.DATA_INVALID_FILTER,()->policy(Duration.ofDays(366))
                .resolve(start.minusSeconds(1),end,scope));
    }
    @Test void laterThanRetentionIsRejected() {
        fails(ErrorCode.DATA_INVALID_FILTER,()->policy(Duration.ofDays(366))
                .resolve(start,end.plusSeconds(1),scope));
    }
    @Test void exactBudgetBoundaryIsAllowed() {
        Window result=policy(Duration.ofHours(1)).resolve(end.minusHours(1),end,scope);
        assertThat(result.to()).isEqualTo(end);
    }
    @Test void overBudgetIsRejectedWithoutTruncatingTheWindow() {
        // 超预算应报错，不能只统计前面一小段后假装查询成功。
        fails(ErrorCode.DATA_QUERY_TOO_BROAD,()->policy(Duration.ofHours(1))
                .resolve(end.minusHours(1).minusSeconds(1),end,scope));
    }
    @Test void missingRetentionPolicyDoesNotInventADefault() {
        var policy=new QualityQueryWindowPolicy(current -> null,Duration.ofDays(366));
        fails(ErrorCode.SYSTEM_UNAVAILABLE,()->policy.resolve(null,null,scope));
    }
    @Test void scopeIsForwardedToTheCurrentPolicyLookup() {
        var policy=new QualityQueryWindowPolicy(current -> {
            assertThat(current).isSameAs(scope);
            return new Window(start,end);
        },Duration.ofDays(366));
        assertThat(policy.resolve(null,null,scope).to()).isEqualTo(end);
    }
}
