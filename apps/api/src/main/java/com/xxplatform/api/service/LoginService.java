package com.xxplatform.api.service;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.LoginThrottle;
import com.xxplatform.api.infra.PasswordHasher;
import com.xxplatform.api.infra.UserAccountRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 登录用例（{@code POST /auth/login}）。
 *
 * <h2>失败语义：所有失败都长一个样</h2>
 * 契约 description 原文要求"失败统一提示，避免账号枚举"。所以以下三种情况
 * <b>对外完全一致</b>（401 {@code AUTH_INVALID_CREDENTIALS} + 同一句文案）：
 * <ul>
 *   <li>登录名不存在</li>
 *   <li>口令不匹配</li>
 *   <li>账号存在但 {@code status != 'ACTIVE'}（停用/软删）</li>
 * </ul>
 * 真实原因只进服务端日志（带 traceId）。若给停用账号单独一句"账号已停用"，
 * 攻击者就能用它做账号枚举——这正是契约要避免的。
 *
 * <h2>两条防侧信道措施</h2>
 * <ol>
 *   <li><b>时序</b>：账号不存在时也跑一次等价成本的密钥派生
 *       （{@link PasswordHasher#burnEquivalentTime}）。否则"不存在"分支少 619 ms，
 *       响应时间本身就是账号存在性的信号。</li>
 *   <li><b>频控</b>：按"登录名 + 来源 IP"做失败计数（{@link LoginThrottle}），
 *       超限返回 429 + {@code Retry-After}，让在线爆破变得不划算。</li>
 * </ol>
 *
 * <h2>为什么本类没有 {@code @Transactional}</h2>
 * 口令校验要 ~619 ms。若整段包在事务里，每个登录请求都会占用数据库连接近 1 秒，
 * 连接池（10）会被打满。所以只有真正读写数据库的部分（撤销旧会话、插入新会话、
 * 绑定 CSRF）在 {@link SessionService#begin} 的事务里。
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    /**
     * 唯一的失败文案。
     *
     * <p>不含"账号/口令"哪个错、也不含"用户不存在"，因为这两句话本身就是枚举工具。
     * 它是可展示文本（前端可以直接弹给用户）。
     */
    /**
     * 统一的登录失败文案。
     *
     * <p>包私有（而不是 {@code private}）是刻意的：{@link SessionService#begin} 在签发阶段发现
     * 账号已被停用时也要用**同一句**话回 401，否则错误文案本身会泄露“账号存在但被停用”。
     * 单一常量避免两处文案以后改出差异。
     */
    static final String FAILURE_MESSAGE = "登录名或口令不正确";

    private static final int CLIENT_LABEL_MAX = 200;

    private final UserAccountRepository userAccountRepository;
    private final PasswordHasher passwordHasher;
    private final SessionService sessionService;
    private final LoginThrottle throttle;

    public LoginService(UserAccountRepository userAccountRepository,
                        PasswordHasher passwordHasher,
                        SessionService sessionService,
                        LoginThrottle throttle) {
        this.userAccountRepository = userAccountRepository;
        this.passwordHasher = passwordHasher;
        this.sessionService = sessionService;
        this.throttle = throttle;
    }

    /**
     * 执行登录。
     *
     * @param loginName    登录名（已通过 Bean Validation：非空、≤80）
     * @param password     口令原文（不落日志）
     * @param presentedSid 客户端带来的旧 SID（可能为 null）
     * @param csrfId       本次请求校验通过的 CSRF 会话 id（可能为 null，见 AuthController）
     * @param clientIp     来源 IP（用于频控分桶与审计）
     * @param clientLabel  User-Agent（用于"我的会话"展示，截断到 200）
     * @param traceId      请求 traceId
     */
    public LoginResult login(String loginName, String password, String presentedSid,
                             UUID csrfId, String clientIp, String clientLabel, String traceId) {
        String throttleKey = LoginThrottle.key(loginName, clientIp);

        Optional<Duration> wait = throttle.retryAfter(throttleKey);
        if (wait.isPresent()) {
            long seconds = Math.max(1L, wait.get().toSeconds());
            log.warn("登录被限流 loginName={} ip={} retryAfter={}s traceId={}",
                    sanitize(loginName), clientIp, seconds, traceId);
            throw ApiException.rateLimited("尝试次数过多，请在 " + seconds + " 秒后重试", wait.get());
        }

        Optional<UserAccountRepository.UserAccountRow> found = userAccountRepository.findByLoginName(loginName);
        if (found.isEmpty()) {
            // 先消耗等成本 CPU，再失败：让"不存在"与"口令错"的响应时间不可区分
            passwordHasher.burnEquivalentTime(password);
            throttle.recordFailure(throttleKey);
            log.info("登录失败：账号不存在 loginName={} ip={} traceId={}",
                    sanitize(loginName), clientIp, traceId);
            throw invalidCredentials();
        }

        UserAccountRepository.UserAccountRow user = found.get();
        if (!user.isActive()) {
            passwordHasher.burnEquivalentTime(password);
            throttle.recordFailure(throttleKey);
            log.warn("登录失败：账号状态为 {}（对外仍为统一失败提示） userId={} loginName={} ip={} traceId={}",
                    user.status(), user.id(), sanitize(loginName), clientIp, traceId);
            throw invalidCredentials();
        }

        if (!passwordHasher.verify(password, user.passwordHash())) {
            throttle.recordFailure(throttleKey);
            log.info("登录失败：口令不匹配 userId={} loginName={} ip={} traceId={}",
                    user.id(), sanitize(loginName), clientIp, traceId);
            throw invalidCredentials();
        }

        // 成功：清掉失败历史，避免"偶发手误 + 正常登录"累积到下次被误限流
        throttle.clear(throttleKey);

        SessionService.IssuedSession session =
                sessionService.begin(user.id(), traceId, clientLabel, presentedSid, csrfId);

        int touched = userAccountRepository.touchLastLogin(
                user.id(), OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
        if (touched != 1) {
            log.warn("更新 last_login_at 影响 {} 行（用户可能已被并发删除） userId={} traceId={}",
                    touched, user.id(), traceId);
        }

        log.info("登录成功 userId={} sessionId={} ip={} traceId={}",
                user.id(), session.sessionId(), clientIp, traceId);
        return new LoginResult(user.id(), user.displayName(), session);
    }

    /**
     * 截断 User-Agent 到列宽。
     *
     * <p>放在这里而不是 Controller：列宽是持久化约束，属于这一层要知道的事。
     */
    public static String clientLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String trimmed = userAgent.strip();
        return trimmed.length() <= CLIENT_LABEL_MAX ? trimmed : trimmed.substring(0, CLIENT_LABEL_MAX);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, FAILURE_MESSAGE);
    }

    /**
     * 去掉日志里的控制字符。
     *
     * <p>登录名来自请求体，可能含 {@code \n}/{@code \r}。原样落日志可以让攻击者伪造出
     * 一条假的"登录成功"记录（日志注入）。制表与换行之外，其余不可见字符一并替换掉。
     */
    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length() && sb.length() < 80; i++) {
            char c = value.charAt(i);
            sb.append(Character.isISOControl(c) ? '?' : c);
        }
        return sb.toString();
    }

    /**
     * 登录结果（内部传递用；不直接序列化成响应体）。
     *
     * @param userId      用户 id（响应体 {@code SessionData.userId}）
     * @param displayName 显示名（预留给 {@code /me}，本轮响应体不含）
     * @param session     已签发的会话
     */
    public record LoginResult(UUID userId, String displayName, SessionService.IssuedSession session) {
    }
}
