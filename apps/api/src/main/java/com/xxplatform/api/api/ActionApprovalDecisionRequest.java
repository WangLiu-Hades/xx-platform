package com.xxplatform.api.api;

/**
 * {@code ActionApprovalDecisionInput}。
 *
 * <p>{@code currentPassword} 只在本请求内做一次哈希校验（见
 * {@link com.xxplatform.api.service.approval.ApprovalService}），不进日志、不回显、不落库；
 * 因此这里的 record 没有 {@code toString} 覆写以外的特殊处理——调用方也不要把它塞进日志。
 */
public record ActionApprovalDecisionRequest(String decision, String reason, String currentPassword) {
}
