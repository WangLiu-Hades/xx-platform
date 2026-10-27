package com.xxplatform.api.service.task;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.AuthenticatedSession;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 任务的写操作：取消与重试（{@code POST /tasks/{taskId}/cancel}、
 * {@code POST /tasks/{taskId}/retry}，W2 任务 8）。
 *
 * <p><b>为什么权限是 {@code task:manage} 而不是 {@code task:read}</b>：契约给这两个操作标了
 * {@code task:manage}。取消会停掉别人的执行、重试会再排一次队，两者都能改变任务对外的结果，
 * 不该和"能看自己的任务"共用一个码。开发种子里 OPERATOR/CLASSIFIED 只有 {@code task:read}，
 * 所以"操作员不能取消任务"是真实可见的行为，不是只在单测里成立。
 *
 * <p><b>为什么状态分支不写在这里</b>：取消的三个分支（排队即取消 / 运行中只登记 /
 * 终态 409）与重试的"仅 FAILED"都在 {@link TaskService} 里，与状态机、事务边界放在一起。
 * 这一层只做"授权 + 把结果映射成契约形状"，否则同一套分支会在 HTTP 与 worker 两条路径上各写一遍。
 *
 * <p><b>为什么都返回 202 而不是 200</b>：契约对 cancel/retry 统一声明 202。取消在排队态是同步完成的，
 * 但响应仍按契约给 202——调用方拿到的 {@code Task.status} 才是事实，不是 HTTP 码。
 */
@Service
public class TaskWriteService {

    /** 契约 {@code x-permission}。 */
    public static final String PERMISSION = "task:manage";

    private final RbacRepository rbac;
    private final TaskService tasks;
    private final TaskViewMapper mapper;

    public TaskWriteService(RbacRepository rbac, TaskService tasks, TaskViewMapper mapper) {
        this.rbac = rbac;
        this.tasks = tasks;
        this.mapper = mapper;
    }

    /** 取消；返回取消后的任务行（运行中时 {@code cancelRequestedAt} 已写、状态仍是 RUNNING）。 */
    public Map<String, Object> cancel(AuthenticatedSession session, UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        requirePermission(session.userId());
        return mapper.view(tasks.cancel(taskId, session.userId()).task());
    }

    /** 重试失败任务；返回重新排队后的行（{@code retryCount} 已加一，{@code attemptNo} 待下次领取递增）。 */
    public Map<String, Object> retry(AuthenticatedSession session, UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        requirePermission(session.userId());
        return mapper.view(tasks.retry(taskId, session.userId()));
    }

    private void requirePermission(UUID userId) {
        if (!rbac.permissionCodes(userId).contains(PERMISSION)) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "无权操作任务（缺少 " + PERMISSION + "）");
        }
    }
}
