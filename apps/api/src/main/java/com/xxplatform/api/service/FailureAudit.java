package com.xxplatform.api.service;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 拒绝/失败的正式审计（M20）。
 *
 * <p>为什么不能直接调用 {@link AuditService#failure}：它内部用 {@code PROPAGATION_NESTED}
 * （SAVEPOINT）复用调用方事务——这是为了避免自死锁，但也意味着**业务事务回滚时，刚写的失败
 * 事件会一起回滚**。而协议 P-021-04 要求"拒绝/失败"必须留痕：一次被拒的敏感导出如果因为
 * 业务事务回滚而连拒绝记录都没有，事后就无法回答"是谁在什么时候试图导出"。
 *
 * <p>所以失败事件走 {@code REQUIRES_NEW}：在独立事务里提交，与被拒的业务事务解耦。
 * {@link AuditService} 本身不改（任务书要求），它在这个新事务里仍然是 NESTED = 加入新事务，
 * 语义正确。
 */
@Component
public class FailureAudit {

    private static final Logger log = LoggerFactory.getLogger(FailureAudit.class);

    private final AuditService audit;
    private final TransactionTemplate requiresNew;

    public FailureAudit(AuditService audit, PlatformTransactionManager transactionManager) {
        this.audit = audit;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 在独立事务里写一条失败事件；写不进去只记日志，绝不因为审计故障改变业务结果。
     */
    public void failure(String action,
                        String category,
                        String resourceType,
                        UUID resourceId,
                        AuditService.Actor actor,
                        UUID businessTaskId,
                        String traceId,
                        String failureCode,
                        Map<String, Object> after) {
        try {
            requiresNew.executeWithoutResult(status ->
                    audit.failure(action, category, resourceType, resourceId, actor, null,
                            businessTaskId, traceId, failureCode, after));
        } catch (RuntimeException e) {
            log.error("失败审计写入异常 action={} resourceId={} code={}", action, resourceId,
                    failureCode, e);
        }
    }
}
