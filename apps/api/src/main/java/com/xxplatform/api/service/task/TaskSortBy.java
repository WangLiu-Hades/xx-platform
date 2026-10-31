package com.xxplatform.api.service.task;

/**
 * {@code GET /tasks} 允许的排序字段（契约 {@code sortBy} 枚举：{@code createdAt} / {@code status}）。
 *
 * <p>为什么不用字符串直接拼进 SQL：排序字段是用户可控输入，拼字符串就是注入面。
 * 用枚举把"可排序的列"固定成一组白名单，SQL 里只出现枚举映射出的常量列名。
 */
public enum TaskSortBy {

    /** 默认：按创建时间。 */
    CREATED_AT("createdAt"),
    /** 按执行状态；契约要求同值追加资源 ID 作次键。 */
    STATUS("status");

    private final String contractValue;

    TaskSortBy(String contractValue) {
        this.contractValue = contractValue;
    }

    /** 契约里出现的英文值。 */
    public String contractValue() {
        return contractValue;
    }

    /**
     * 解析契约值，大小写不敏感（{@code status} / {@code Status} 都接受）。
     *
     * @throws IllegalArgumentException 未知值，由控制层翻成 400 {@code VALIDATION_ERROR}
     */
    public static TaskSortBy parse(String value) {
        for (TaskSortBy candidate : values()) {
            if (candidate.contractValue.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("不支持的排序字段: " + value);
    }
}
