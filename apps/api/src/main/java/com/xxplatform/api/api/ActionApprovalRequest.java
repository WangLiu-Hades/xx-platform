package com.xxplatform.api.api;

import java.util.Map;

/**
 * {@code ActionApprovalInput}。
 *
 * <p>{@code proposal} 故意保持为通用 Map：契约里它是 30 选一的 discriminated union，用 Java 类型
 * 逐一手写会把"新增一个操作"变成改三处。真正的结构校验在
 * {@link com.xxplatform.api.service.approval.ApprovalProposals}，那里按允许的键集合拒绝未知字段。
 */
public record ActionApprovalRequest(Map<String, Object> proposal, String reason) {
}
