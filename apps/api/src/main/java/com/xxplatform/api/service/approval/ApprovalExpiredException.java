package com.xxplatform.api.service.approval;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;

/**
 * 审批已过期时抛出。
 *
 * <p>单独一个类型只有一个用途：让 {@code ApprovalService#decide} 的
 * {@code @Transactional(noRollbackFor = ApprovalExpiredException.class)} 能精确地"只对这个异常
 * 不回滚"。过期分支必须在返回 409 之前把状态写成 {@code EXPIRED}，如果和普通冲突异常共用
 * {@link ApiException}，回滚规则就会把版本冲突之后的决定记录也一起提交，造成"决定了但不生效"。
 */
public class ApprovalExpiredException extends ApiException {

    public ApprovalExpiredException(String message) {
        super(ErrorCode.RESOURCE_CONFLICT, message);
    }
}
