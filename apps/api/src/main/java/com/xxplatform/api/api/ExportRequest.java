package com.xxplatform.api.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * 导出提交请求体（契约 {@code ExportRequest}）。
 *
 * <p>字段与 {@code openapi.yaml} 对齐：
 * <ul>
 *   <li>{@code contextId}：required，uuid —— 导出范围来自 Context，不接受"直接传筛选条件"，
 *       否则筛选条件的解释权会被拆到两个端点，权限与审计也各查一套</li>
 *   <li>{@code format}：required，枚举 XLSX/CSV/ZIP；这里收成 String 再在控制层解析，
 *       是为了让非法值返回带 {@code details.field} 的 400，而不是 Jackson 的笼统报文错误</li>
 *   <li>{@code columns}：可选；不传表示按数据类型导出全部列（见 {@code ExportService}）</li>
 *   <li>{@code purpose}：可选，落库 {@code varchar(1000)}，同时进审计</li>
 * </ul>
 *
 * <p>{@code additionalProperties:false} 由全局 {@code failOnUnknownProperties(true)} 落实。
 */
public record ExportRequest(

        @NotNull(message = "contextId 不能为空")
        UUID contextId,

        @NotBlank(message = "format 不能为空")
        String format,

        List<String> columns,

        @Size(max = 1000, message = "用途说明不能超过 1000 字符")
        String purpose) {
}
