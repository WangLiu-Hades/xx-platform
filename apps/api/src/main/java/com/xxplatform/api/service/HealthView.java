package com.xxplatform.api.service;

import java.util.List;

/**
 * 探针端点的响应数据（{@code HealthData} / {@code ReadinessData}）。
 *
 * <p>字段与契约逐项对齐：{@code HealthData} 只有 {@code status} + {@code checkedAt}，
 * {@code ReadinessData} 多一个 {@code dependencies}。两个 Schema 都是
 * {@code additionalProperties:false}，所以这里**不能**顺手多加字段
 * （例如想加 {@code version} 或 {@code uptime} 就必须先改契约）。
 *
 * <p>{@code checkedAt} 用 {@link String} 而不是 {@code OffsetDateTime}：格式统一由
 * {@code common.Instants#iso} 负责，避免 Jackson 的日期配置被改动后对外格式静默漂移。
 */
public final class HealthView {

    private HealthView() {
    }

    /**
     * 存活检查数据。
     *
     * @param status    契约枚举 {@code UP}/{@code DOWN}；本实现只产出 {@code UP}，理由见
     *                  {@code HealthService} 类注释
     * @param checkedAt 检查时刻，RFC 3339 带偏移，UTC 以 {@code Z} 结尾
     */
    public record HealthData(String status, String checkedAt) {
    }

    /**
     * 就绪检查数据。
     *
     * @param status       契约枚举 {@code READY}/{@code NOT_READY}
     * @param checkedAt    检查时刻，RFC 3339 带偏移，UTC 以 {@code Z} 结尾
     * @param dependencies 各依赖的检查结果，顺序固定（见 {@code HealthService}）
     */
    public record ReadinessData(String status, String checkedAt, List<DependencyStatus> dependencies) {
    }

    /**
     * 单个依赖的检查结果。
     *
     * <p>契约 {@code ReadinessDataDependenciesItem} 只有 {@code name} 与 {@code status}
     * 两个字段、没有 {@code reason}，所以"为什么没就绪"不能塞在这里，只能进 503 的
     * {@code details}（见 {@code HealthService#readiness()}）。
     *
     * @param name   依赖名，契约未枚举，本实现用 {@code database}/{@code schema}/{@code deployment-config}
     * @param status 契约枚举 {@code READY}/{@code NOT_READY}
     */
    public record DependencyStatus(String name, String status) {
    }
}
