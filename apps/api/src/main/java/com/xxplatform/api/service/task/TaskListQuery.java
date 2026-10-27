package com.xxplatform.api.service.task;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code GET /tasks} 的查询条件，已经过控制层解析与校验。
 *
 * @param page        页码，从 1 开始
 * @param pageSize    每页条数 1–100
 * @param sortBy      排序字段，默认 {@link TaskSortBy#CREATED_AT}
 * @param descending  {@code true} 为 {@code DESC}
 * @param taskTypes   任务类型筛选（同值 OR，与其它条件是 AND）；空集合视为不筛选
 * @param status      状态筛选，{@code null} 表示不筛选
 * @param createdFrom 创建时间下界（含），{@code null} 表示不限
 * @param createdTo   创建时间上界（含），{@code null} 表示不限
 */
public record TaskListQuery(int page, int pageSize, TaskSortBy sortBy, boolean descending,
                            List<TaskType> taskTypes, TaskStatus status,
                            OffsetDateTime createdFrom, OffsetDateTime createdTo) {
}
