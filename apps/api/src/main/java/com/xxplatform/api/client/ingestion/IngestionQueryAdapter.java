package com.xxplatform.api.client.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.service.ingestion.BatchListQuery;
import com.xxplatform.api.service.ingestion.DataApiException;
import com.xxplatform.api.service.ingestion.IngestionQueryService.*;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Function;

// 组织接入查询调用，参数和返回值的检查统一交给契约适配器。
public final class IngestionQueryAdapter implements BatchQuery {
    private final Client client;
    private final WindowPolicy windowPolicy;
    private final IngestionContractAdapter contract;

    public IngestionQueryAdapter(Client client, WindowPolicy windowPolicy, ObjectMapper mapper) {
        this.client = Objects.requireNonNull(client);
        this.windowPolicy = Objects.requireNonNull(windowPolicy);
        this.contract = new IngestionContractAdapter(mapper);
    }

    // 可直接使用服务端提供的留存边界和查询预算构造完整时间策略。
    public IngestionQueryAdapter(Client client, Function<ReadScope, Window> retainedBounds,
                                 Duration maximumWindow, ObjectMapper mapper) {
        this(client, new QualityQueryWindowPolicy(retainedBounds, maximumWindow), mapper);
    }

    @Override public BatchPage findPage(BatchListQuery query, ReadScope scope) {
        contract.trustedScope(scope);
        Map<String,Object> raw = call(() -> client.listBatches(contract.listSpec(query), scope));
        return contract.readPage(raw, query, scope);
    }

    @Override public Optional<BatchRow> findById(UUID id, ReadScope scope) {
        contract.trustedScope(scope);
        // 没有查看权限时，不调用数据服务查询该批次。
        if (!scope.batchIds().contains(id)) return Optional.empty();
        Map<String,Object> raw = call(() -> client.getBatch(id, scope));
        if (raw == null) return Optional.empty();
        return Optional.of(contract.readBatch(raw, id, scope));
    }

    @Override public ErrorPage findErrors(UUID id, String cursor, int pageSize, ReadScope scope) {
        contract.trustedScope(scope);
        if (!scope.batchIds().contains(id)) {
            throw new DataApiException(ErrorCode.DATA_NOT_FOUND, null, "Batch not found");
        }
        Map<String,Object> raw = call(() -> client.listErrors(id, cursor, pageSize, scope));
        return contract.readErrors(raw, pageSize);
    }

    @Override public QualitySummary findQuality(QualityQuery query, ReadScope scope) {
        contract.trustedScope(scope);
        // 先补齐允许省略的时间，再按数据服务要求传递明确的时间范围。
        Window window = windowPolicy.resolve(query.from(), query.to(), scope);
        if (window == null || window.from() == null || window.to() == null
                || !window.from().isBefore(window.to())
                || (query.from() != null && !query.from().isEqual(window.from()))
                || (query.to() != null && !query.to().isEqual(window.to()))) {
            throw new DataApiException(ErrorCode.SYSTEM_UNAVAILABLE, "timeRange",
                    "Quality window policy returned an invalid range");
        }
        var resolved = new QualityQuery(query.source(), window.from(), window.to());
        Map<String,Object> raw = call(() -> client.qualitySummary(contract.qualitySpec(resolved), scope));
        return contract.readQuality(raw, query.source());
    }

    private <T> T call(Supplier<T> action) {
        try { return action.get(); }
        catch (Failure failure) { throw contract.workerFailure(failure.code()); }
    }

    // 保留车牌适配器正在使用的错误转换入口。
    public static DataApiException mapFailure(Failure failure) {
        return IngestionContractAdapter.toPublicFailure(failure.code());
    }

    public record Window(OffsetDateTime from, OffsetDateTime to) {}
    @FunctionalInterface public interface WindowPolicy {
        Window resolve(OffsetDateTime from, OffsetDateTime to, ReadScope scope);
    }

    // 真实传输实现由对接代码提供，此处不新增公开接口或假造连接地址。
    public interface Client {
        Map<String,Object> listBatches(Map<String,Object> filters, ReadScope scope);
        Map<String,Object> getBatch(UUID batchId, ReadScope scope);
        Map<String,Object> listErrors(UUID batchId, String cursor, int pageSize, ReadScope scope);
        Map<String,Object> qualitySummary(Map<String,Object> filters, ReadScope scope);
    }

    public static final class Failure extends RuntimeException {
        private final String code;
        public Failure(String code) {
            super("Data operation failed");
            this.code = Objects.requireNonNull(code);
        }
        public String code() { return code; }
    }
}
