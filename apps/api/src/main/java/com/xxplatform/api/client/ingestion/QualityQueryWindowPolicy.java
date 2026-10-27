package com.xxplatform.api.client.ingestion;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.service.ingestion.DataApiException;
import com.xxplatform.api.service.ingestion.IngestionQueryService.ReadScope;
import com.xxplatform.api.client.ingestion.IngestionQueryAdapter.Window;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.Function;

// 将接口中的可选时间转换为数据服务需要的明确查询范围。
public final class QualityQueryWindowPolicy implements IngestionQueryAdapter.WindowPolicy {
    private final Function<ReadScope, Window> retainedBounds;
    private final Duration maximumWindow;

    public QualityQueryWindowPolicy(Function<ReadScope, Window> retainedBounds, Duration maximumWindow) {
        this.retainedBounds = Objects.requireNonNull(retainedBounds);
        this.maximumWindow = Objects.requireNonNull(maximumWindow);
        if (maximumWindow.isZero() || maximumWindow.isNegative()) {
            throw new IllegalArgumentException("A positive server query budget is required");
        }
    }

    @Override public Window resolve(OffsetDateTime from, OffsetDateTime to, ReadScope scope) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw problem(ErrorCode.VALIDATION_ERROR, "to", "to must be later than from");
        }
        // 留存边界由当前服务端策略提供，不在这里写死日期或模拟数据。
        Window retained = retainedBounds.apply(Objects.requireNonNull(scope));
        if (retained == null || retained.from() == null || retained.to() == null
                || !retained.from().isBefore(retained.to())) {
            throw problem(ErrorCode.SYSTEM_UNAVAILABLE, "timeRange", "Retention policy is unavailable");
        }
        // 省略时间表示查询当前完整留存范围，不擅自缩小成最近几天。
        OffsetDateTime start = from == null ? retained.from() : from;
        OffsetDateTime end = to == null ? retained.to() : to;
        if (!start.isBefore(end)) {
            throw problem(ErrorCode.VALIDATION_ERROR, "to", "to must be later than from");
        }
        if (start.isBefore(retained.from()) || end.isAfter(retained.to())) {
            throw problem(ErrorCode.DATA_INVALID_FILTER, "timeRange",
                    "Requested range is outside available retention");
        }
        // 超过查询预算时明确报错，不截断时间后返回不完整结果。
        if (Duration.between(start.toInstant(), end.toInstant()).compareTo(maximumWindow) > 0) {
            throw problem(ErrorCode.DATA_QUERY_TOO_BROAD, "timeRange", "Query exceeds the server window budget");
        }
        return new Window(start.withOffsetSameInstant(ZoneOffset.UTC), end.withOffsetSameInstant(ZoneOffset.UTC));
    }

    private static DataApiException problem(ErrorCode code, String field, String message) {
        return new DataApiException(code, field, message);
    }
}
