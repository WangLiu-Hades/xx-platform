package com.xxplatform.api.service.task;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses.FieldErrorDetail;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.AsyncTaskRepository;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.AuthenticatedSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 任务的只读查询（{@code GET /tasks}、{@code GET /tasks/{taskId}}，W2 任务 7）。
 *
 * <p>为什么读操作也要查权限码：契约给这两个操作标了 {@code x-permission: task:read}，
 * 而"谁能看任务"与"谁的任务"是两件事——前者是角色授权（被停权的人不该继续读），
 * 后者是归属过滤（读不到别人的）。两者都在会返回 403，只做后者会让被停权的账号照样翻自己的历史。
 *
 * <p>为什么越权读返回 404 而不是 403：契约对这两处的要求是"越权对象按 404 DATA_NOT_FOUND
 * 隐藏存在性"。用 403 会告诉调用方"这个 id 是存在的、只是不给你看"，可以拿来探测别人的
 * 任务 id。
 */
@Service
public class TaskReadService {

    /** 契约 {@code x-permission}。 */
    public static final String PERMISSION = "task:read";

    private final AsyncTaskRepository repository;
    private final RbacRepository rbac;
    private final TaskService tasks;
    private final TaskViewMapper mapper;

    public TaskReadService(AsyncTaskRepository repository, RbacRepository rbac,
                           TaskService tasks, TaskViewMapper mapper) {
        this.repository = repository;
        this.rbac = rbac;
        this.tasks = tasks;
        this.mapper = mapper;
    }

    /** 列出当前用户自己的任务；筛选条件之间是 AND，同名多值之间是 OR。 */
    public TaskPageView list(AuthenticatedSession session, TaskListQuery query) {
        requirePermission(session.userId());
        validate(query);
        List<TaskRow> rows = repository.findPageByOwner(
                session.userId(), query.taskTypes(), query.status(),
                query.createdFrom(), query.createdTo(),
                query.sortBy(), query.descending(),
                query.pageSize(), (long) (query.page() - 1) * query.pageSize());
        long total = repository.countByOwner(
                session.userId(), query.taskTypes(), query.status(), query.createdFrom(), query.createdTo());
        return mapper.page(rows, query.page(), query.pageSize(), total);
    }

    /** 读单个任务；不属于当前用户的任务按"不存在"处理。 */
    public Map<String, Object> get(AuthenticatedSession session, UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        requirePermission(session.userId());
        return mapper.view(tasks.get(taskId, session.userId()));
    }

    /**
     * 读一个任务的尝试历史（{@code GET /tasks/{taskId}/attempts}，W2 任务 9）。
     *
     * <p>先过 {@link TaskService#get} 再查 attempt：归属判定与"任务存在"是同一件事，
     * 先查 attempt 再判归属会让"别人的任务恰好没有 attempt"返回空数组、而"别人的任务有 attempt"
     * 返回 404——同一个越权 id 两种结果，等于变相泄露存在性。
     */
    public List<Map<String, Object>> attempts(AuthenticatedSession session, UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        requirePermission(session.userId());
        tasks.get(taskId, session.userId());
        return mapper.attempts(repository.listAttempts(taskId));
    }

    /** 提交端的权限判定；把码表放进配置之前，它只能在这里查一次。 */
    private void requirePermission(UUID userId) {
        if (!rbac.permissionCodes(userId).contains(PERMISSION)) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "无权查看任务（缺少 " + PERMISSION + "）");
        }
    }

    /**
     * 校验分页与时间窗口。
     *
     * <p>为什么这里还要再校验一次（控制层已经校验过）：{@link TaskReadService} 是公开的服务方法，
     * 以后别的入口（比如内部报表）也会调用；把"page=0 会得到负 offset"这种错误留在服务层，
     * 比在每个调用方复制一遍校验更可靠。
     */
    private static void validate(TaskListQuery query) {
        if (query.page() < 1) {
            throw validation("page", "页码必须从 1 开始");
        }
        if (query.pageSize() < 1 || query.pageSize() > 100) {
            throw validation("pageSize", "每页条数必须在 1–100 之间");
        }
        if (query.createdFrom() != null && query.createdTo() != null
                && query.createdFrom().isAfter(query.createdTo())) {
            throw validation("createdFrom", "起始时间不能晚于结束时间");
        }
    }

    private static ApiException validation(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "参数校验失败",
                List.of(new FieldErrorDetail(field, reason)), null);
    }
}
