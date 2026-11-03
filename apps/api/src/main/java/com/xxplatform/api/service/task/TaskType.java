package com.xxplatform.api.service.task;

/**
 * 统一执行任务的类型（契约 {@code Task.taskType} 与 {@code analysis.async_task.task_type} 的取值集）。
 *
 * <p>{@code task_type} 决定"这条任务归哪个领域解释"：参数快照、结果引用、审计归类都按它分派。
 * 它是**有限目录**而不是自由字符串——迁移里的 {@code CHECK} 约束了 15 个值，新增类型必须先改迁移，
 * 否则插入会在库里失败。
 *
 * <p>{@link #EXPORT} 与 {@link #DATASET_IMPORT}／{@link #PERSON_LIBRARY_IMPORT}／{@link #DEVICE_IMPORT}
 * 是 W2 起最先用到的那几个；其余类型先声明出来，避免各领域实现时各自加一份"自己的枚举"。
 */
public enum TaskType {

    INGESTION,
    BACKFILL,
    EXPORT,
    FACE_SEARCH,
    ANALYSIS,
    DATASET_IMPORT,
    SET_OPERATION,
    PROFILE_MERGE,
    PROFILE_SPLIT,
    MODEL_RUN,
    DEVICE_COMMAND,
    REPORT,
    NOTIFICATION,
    PERSON_LIBRARY_IMPORT,
    DEVICE_IMPORT
}
