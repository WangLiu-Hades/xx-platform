package com.xxplatform.api.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * 创建领域资源后返回的任务摘要（契约 {@code TaskCreated}）。
 *
 * <p>为什么单独一个类而不是直接返回 {@code AsyncTaskRepository.TaskRow}：TaskRow 是持久化
 * 形状，30 个字段里绝大多数不该出现在 202 响应里；而且字段名与契约的 camelCase 不完全一致
 * （如 {@code taskId} vs {@code id}）。让响应形状独立，持久化形状以后加列也不会改变对外契约。
 *
 * <p>{@code resourceId}/{@code resourceType} 都是可选字段：导出没有"领域资源 id"，
 * 只给 {@code resourceType="EXPORT"}；{@code @JsonInclude(NON_NULL)} 保证不会输出
 * {@code "resourceId": null}（契约只允许 nullable 字段为 null）。
 *
 * @param taskId       任务 id
 * @param status       创建时的状态，正常是 {@code PENDING}
 * @param createdAt    RFC 3339 时间串（UTC，带偏移）
 * @param resourceType 领域资源类型枚举，导出固定为 {@code EXPORT}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskCreatedView(
        UUID taskId,
        String status,
        String createdAt,
        String resourceType) {
}
