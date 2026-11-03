package com.xxplatform.api.service;

/**
 * 导出的目标格式（契约 {@code ExportRequest.format} 的三个枚举值）。
 *
 * <p>单独成枚举而不是散落的字符串：格式会出现在任务参数指纹里，一旦某次把
 * {@code "xlsx"} 和 {@code "XLSX"} 混用，同一个幂等键就会被判成"参数不同"并报 409，
 * 而用户明明没有改任何东西。枚举 + {@link #parse(String)} 把大小写与空值收敛在入口。
 */
public enum ExportFormat {

    /** Excel 工作簿，适合给人看。 */
    XLSX,
    /** 逗号分隔，适合下游脚本；UTF-8 且带 BOM 的问题由 worker 统一处理。 */
    CSV,
    /** 打包多个分片（每 5 万行一片），适合大结果集。 */
    ZIP;

    /**
     * 解析客户端传入的格式。
     *
     * <p>大小写不敏感、忽略首尾空白；不在枚举内就抛 {@link IllegalArgumentException}，
     * 由调用方翻译成 400 {@code VALIDATION_ERROR}。这里不直接抛 {@code ApiException}，
     * 是为了让枚举保持"不知道 HTTP"的纯粹，错误消息由控制层决定。
     */
    public static ExportFormat parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("format 不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("format 只能是 XLSX、CSV 或 ZIP，收到: " + raw);
        }
    }
}
