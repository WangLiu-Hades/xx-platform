package com.xxplatform.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求体（契约 {@code LoginRequest}）。
 *
 * <p>字段与约束严格对齐 {@code openapi.yaml}：
 * <ul>
 *   <li>{@code loginName}：required，{@code maxLength: 80} —— 比库列 {@code varchar(200)}
 *       窄，按契约取 80，宽松的库列不是放松校验的理由</li>
 *   <li>{@code password}：required，契约**未声明** maxLength，因此这里也不限制上限。
 *       这不是疏忽：口令长度上限会鼓励用户选短口令，且 PBKDF2 对任意长度都正确
 *       （BCrypt 才需要 72 字节截断处理，见 {@link PasswordHasher}）</li>
 * </ul>
 *
 * <p>{@code additionalProperties:false} 由全局 {@code failOnUnknownProperties(true)} +
 * {@code HttpMessageNotReadableException} handler 落实，不在这里重复声明。
 *
 * <p>为什么是手写 record 而不是直接用生成器产物：控制器当前还未实现生成的接口
 * （那样会把所有 193 个操作一次性拖进来）。等开放式接口迁移时，本类会被
 * {@code generated.model.LoginRequest} 取代——两者字段完全一致，替换是机械的。
 */
public record LoginRequest(

        @NotBlank(message = "登录名不能为空")
        @Size(max = 80, message = "登录名长度不能超过 80")
        String loginName,

        @NotBlank(message = "口令不能为空")
        String password) {
}
