package com.xxplatform.api.service.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 创建一条统一执行任务的输入（由各领域端点组装，再交给 {@link TaskService#create}）。
 *
 * <p>为什么要有这个中间记录，而不是让每个端点自己拼 SQL：创建路径上有三件必须**一起**做对、
 * 少一件就会留下脏数据的动作——写 {@code async_task}、算参数指纹、按 {@code Idempotency-Key}
 * 去重。把它们绑在一个值对象上，端点只负责"业务上要不要建这条任务"，其余交给服务层。
 *
 * @param ownerUserId            任务归属账号（幂等键的作用域之一，也是越权判定的依据）
 * @param taskType               任务类型，决定参数与结果的解释方式
 * @param name                   人可读名称（8–200 字符，落库 {@code name varchar(200)}）
 * @param parameterSchemaVersion 参数契约版本，参与参数指纹
 * @param parameters             参数快照（必须可 JSON 序列化）
 * @param idempotencyKey         幂等键（作用域 = 账号 + 任务类型 + 键）
 * @param traceId                创建请求的 traceId，写进任务行便于跨系统串联
 * @param priority               0–10，越小越先执行；null 由库默认 5
 * @param resourceType           可选的领域资源类型（如 {@code EXPORT_JOB}），写进任务行便于反查
 * @param resourceId             可选的领域资源 id
 */
public record TaskCommand(
        UUID ownerUserId,
        TaskType taskType,
        String name,
        String parameterSchemaVersion,
        Map<String, Object> parameters,
        String idempotencyKey,
        String traceId,
        Integer priority,
        String resourceType,
        UUID resourceId) {

    /** 与迁移里 {@code name varchar(200)}／{@code idempotency_key varchar(200)} 对齐的上限。 */
    private static final int MAX_LENGTH_200 = 200;

    public TaskCommand {
        require(ownerUserId, "ownerUserId");
        require(taskType, "taskType");
        require(parameterSchemaVersion, "parameterSchemaVersion");
        require(traceId, "traceId");
        // 不用 Map.copyOf：参数里允许出现 null（"字段未提供"与"显式 null"在部分契约里同义），
        // 而 Map.copyOf 遇到 null 值直接 NPE。
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(require(parameters, "parameters")));
        name = trimmed(name, "name", MAX_LENGTH_200);
        idempotencyKey = trimmed(idempotencyKey, "idempotencyKey", MAX_LENGTH_200);
        if (priority != null && (priority < 0 || priority > 10)) {
            throw new IllegalArgumentException("priority 必须在 0..10");
        }
        if ((resourceType == null) != (resourceId == null)) {
            throw new IllegalArgumentException("resourceType 与 resourceId 必须同时给出或同时省略");
        }
    }

    /** 供测试与日志使用：参数指纹（与落库的 {@code parameter_hash} 同一算法）。 */
    public String parameterHash() {
        return TaskParameters.hash(parameterSchemaVersion, parameters);
    }

    /** 落库用的确定性 JSON。 */
    public String parameterJson() {
        return TaskParameters.canonicalJson(parameters);
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    private static String trimmed(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String result = value.trim();
        if (result.length() > max) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + max);
        }
        return result;
    }
}
