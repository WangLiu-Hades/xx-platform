package com.xxplatform.api.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 一次已认证请求的身份上下文。
 *
 * <p>由 {@link SessionAuthenticator} 产出，供需要"当前用户"的端点使用。
 * 它是<b>不可变快照</b>：构造时点上的会话与账号状态，不持有数据库连接。
 *
 * <p>刻意不放进 {@code SecurityContextHolder} 之类的线程局部变量：本项目的认证是
 * "Cookie → 摘要 → 查表"三步，显式传参比隐式上下文更容易在评审时确认
 * "这个端点到底有没有做认证"。等端点数量上来后再评估是否引入拦截器统一注入。
 */
public record AuthenticatedSession(
        UUID userId,
        UUID sessionId,
        OffsetDateTime idleExpiresAt,
        OffsetDateTime absoluteExpiresAt,
        UserAccountRepository.UserAccountRow user) {
}
