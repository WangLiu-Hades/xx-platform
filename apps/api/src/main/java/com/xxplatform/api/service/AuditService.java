package com.xxplatform.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.infra.AuditEventRepository;
import com.xxplatform.api.infra.AuditEventRepository.AuditEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 安全审计的写入入口（M20）。
 *
 * <h2>写入哪些事件</h2>
 * <ul>
 *   <li>{@code auth.login}——登录成功与失败（失败带 {@code failureCode}）；</li>
 *   <li>{@code export.create}——敏感导出受理（含审批 id 与任务 id）；</li>
 *   <li>{@code approval.create} / {@code approval.decide}——审批申请与裁决；</li>
 *   <li>{@code user.password_change}——自助改密（M04）。</li>
 * </ul>
 * 任务取消/重试的审计属于任务底座（{@code TaskService}），由那一块自己写：本类只提供
 * 通用写入方法，不反向依赖任务模块。
 *
 * <h2>失败策略：审计写不进去也不影响业务响应</h2>
 * <p>审计插入失败（库不可用、约束冲突、字段超长）时只记 {@code ERROR} 日志，不向调用方抛异常。
 * 理由：审计的可用性不应成为业务的单点故障——一次"审计库抖动"把已经成功的导出/登录
 * 变成 500，会造成重复提交与更大的不一致。代价是"审计缺口"，所以错误日志必须带 action 与
 * traceId，便于运维按日志补偿。**登录失败也走同一策略**：登录失败已经有一条业务日志
 * （见 {@code LoginService}），审计缺一条不会让攻击变得不可见；反过来让失败登录因为审计
 * 故障返回 500，会泄露"审计子系统异常"这一内部状态。
 *
 * <h2>为什么加入调用方事务（而不是 REQUIRES_NEW）</h2>
 * <p>最初用独立事务，是为了避免"审计 INSERT 失败把业务事务标记为 aborted"。真库验证否掉了这个
 * 方案：登录/改密等调用方此时持有 {@code auth.app_user} 行的排他锁，而审计插入的子查询要读同一行，
 * 独立事务只能等调用方提交，调用方又在等审计返回——自死锁（HTTP 挂住，pg_stat_activity 里
 * 一个 idle in transaction、一个 Lock/transactionid）。因此改为加入调用方事务；防污染改由
 * "写失败只记日志、不再执行后续语句"来兜底（见 {@link #record}）。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** 摘要列宽（{@code varchar(2000)}），超出部分截断——审计宁可少一段，也不能写失败。 */
    private static final int MAX_SUMMARY = 2000;

    /** 摘要里被打码的键名片段；与审批展示的脱敏口径保持一致。 */
    private static final List<String> SENSITIVE_KEY_PARTS =
            List.of("password", "secret", "token", "ciphertext", "private", "credential", "otp", "key");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditEventRepository repository;
    private final TransactionTemplate transaction;
    /** 回滚补偿专用：在外层事务结束之后另起一个短事务补写。 */
    private final TransactionTemplate compensation;

    public AuditService(AuditEventRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
        // 用 NESTED（SAVEPOINT）：既复用调用方的连接/事务（避免 REQUIRES_NEW 的自死锁），
        // 又能在审计写失败时只回滚到保存点，不把调用方事务整体打成 aborted。
        // 没有外层事务时它等价于 REQUIRED。
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        this.compensation = new TransactionTemplate(transactionManager);
        this.compensation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 写一条成功事件。
     *
     * @param actor 操作者快照；账号不存在/系统动作时用 {@link Actor#unknown()}
     */
    public void success(String action, String category, String resourceType, UUID resourceId,
                        Actor actor, String sourceIp, UUID businessTaskId, String traceId,
                        Map<String, Object> after) {
        record(action, category, resourceType, resourceId, actor, sourceIp, businessTaskId, traceId,
                "SUCCESS", null, null, after);
    }

    /** 写一条失败事件；{@code failureCode} 用契约错误码名（如 {@code AUTH_INVALID_CREDENTIALS}）。 */
    public void failure(String action, String category, String resourceType, UUID resourceId,
                        Actor actor, String sourceIp, UUID businessTaskId, String traceId,
                        String failureCode, Map<String, Object> after) {
        record(action, category, resourceType, resourceId, actor, sourceIp, businessTaskId, traceId,
                "FAILURE", failureCode, null, after);
    }

    /**
     * 写一条事件（成功/失败由 {@code result} 决定）。
     *
     * <p>公开出来是为了让调用方在两个摘要都有意义时（例如"改密前/后"）不必拼两次事件。
     */
    public void record(String action, String category, String resourceType, UUID resourceId,
                       Actor actor, String sourceIp, UUID businessTaskId, String traceId,
                       String result, String failureCode,
                       Map<String, Object> before, Map<String, Object> after) {
        Actor safeActor = actor == null ? Actor.unknown() : actor;
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(),
                safeActor.userId(),
                safeActor.loginName(),
                safeActor.displayName(),
                action,
                category,
                resourceType,
                resourceId,
                Instants.nowUtc(),
                result,
                failureCode,
                summary(before),
                summary(after),
                sourceIp,
                businessTaskId,
                null,
                traceId,
                Instants.nowUtc());
        try {
            transaction.executeWithoutResult(status -> repository.insert(event));
        } catch (RuntimeException e) {
            // 只记录，不抛出：见类注释"失败策略"。日志里带 action/traceId 以便按日志补偿。
            log.error("审计事件写入失败（业务结果不受影响） action={} traceId={} error={}",
                    action, traceId, e.toString());
        }
        compensateIfRolledBack(event, action, traceId);
    }

    /**
     * 回滚补偿：失败路径的审计不能跟着业务事务一起消失（M20）。
     *
     * <p>典型场景：登录失败 → 写"登录失败"审计 → 抛异常 → {@code @Transactional} 回滚。
     * 审计是同一事务里的 SAVEPOINT，会一起被撤销，于是"攻击痕迹"只剩应用日志。
     * 因此登记一个事务同步：外层事务回滚（或结果未知）后，确认这条审计确实不在库里，
     * 再用独立事务补写一次。提交成功的事务跳过检查，避免每条审计都多一次 SELECT。
     */
    private void compensateIfRolledBack(AuditEvent event, String action, String traceId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    compensation.executeWithoutResult(inner -> {
                        if (!repository.existsById(event.id())) {
                            repository.insert(event);
                        }
                    });
                } catch (RuntimeException e) {
                    log.error("回滚补偿写审计失败 action={} traceId={} error={}",
                            action, traceId, e.toString());
                }
            }
        });
    }

    /**
     * 摘要：递归脱敏后序列化，超长截断到列宽。
     *
     * <p>脱敏必须递归：敏感值常常藏在 {@code requestBody} 这类嵌套对象里，只打码第一层
     * 等于没打码。列表按元素下标展开，保持"同一个键每次渲染顺序一致"。
     */
    static String summary(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        String text;
        try {
            text = MAPPER.writeValueAsString(mask(data));
        } catch (JsonProcessingException e) {
            // 序列化失败不能影响审计的"至少记下发生了什么"：退回 key 列表。
            text = String.valueOf(mask(data));
        }
        return text.length() <= MAX_SUMMARY ? text : text.substring(0, MAX_SUMMARY);
    }

    private static Object mask(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                masked.put(key, isSensitive(key) ? "***" : mask(entry.getValue()));
            }
            return masked;
        }
        if (value instanceof List<?> list) {
            List<Object> masked = new ArrayList<>(list.size());
            for (Object item : list) {
                masked.add(mask(item));
            }
            return masked;
        }
        return value;
    }

    private static boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        for (String part : SENSITIVE_KEY_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 操作者快照。
     *
     * @param userId      账号 id；账号不存在时为 null（审计表有外键，不能编造）
     * @param loginName   登录名
     * @param displayName 显示名
     */
    public record Actor(UUID userId, String loginName, String displayName) {

        /** 未知操作者（例如撞库时提交了一个不存在的登录名）。 */
        public static Actor unknown() {
            return new Actor(null, "", "");
        }

        /** 只有登录名的快照（口令校验失败、账号不存在时用）。 */
        public static Actor ofLoginName(String loginName) {
            return new Actor(null, loginName == null ? "" : loginName, "");
        }
    }
}
