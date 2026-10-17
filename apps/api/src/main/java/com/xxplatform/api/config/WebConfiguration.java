package com.xxplatform.api.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层基础配置（平台侧，改动需走共享层评审，见 BACKEND_TWO_DEV_SPLIT.md §5）。
 *
 * <p>范围：JSON 解析口径与开发环境可信 Origin。
 * 会话/CSRF、权限与数据范围、幂等、ETag、游标等基础设施不在这里，按阶段计划单独落地。
 */
@Configuration
public class WebConfiguration {

    /**
     * JSON 口径。
     *
     * <p><b>不要</b>在这里设全局 {@code serializationInclusion = NON_NULL}：错误响应必须输出
     * {@code "data": null}，全局排除 null 会让它整个消失，违反契约。
     * 响应壳自身已用 {@code @JsonInclude(ALWAYS)} 固定该行为（见 {@code ApiResponses}）。
     *
     * <p><b>必须开启</b> {@code failOnUnknownProperties}：契约里所有业务 DTO 都是
     * {@code additionalProperties: false}，未知字段应被拒绝（400 {@code VALIDATION_ERROR}，
     * 由 {@code ApiExceptionHandler} 还原字段名）。之前这里设成 {@code false} 会静默吞掉拼错/
     * 多传的字段，让前端以为请求成功而业务字段从未生效——这是最难排查的一类联调问题。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer contractJsonCustomizer() {
        return builder -> builder.failOnUnknownProperties(true);
    }

    /**
     * 开发期 CORS。
     *
     * <p>正常联调走同源代理（浏览器请求 {@code /api/v1} → Vite 代理 → 127.0.0.1:8080），
     * 不需要 CORS；这里只服务于"前端直连后端端口"的调试场景。
     * 生产必须替换为真实 Origin 白名单，且禁止
     * {@code allowedOriginPatterns("*") + allowCredentials(true)} 组合（契约禁止通配凭据 CORS）。
     *
     * <p><b>路径必须写 {@code /**}，不能写 {@code /api/v1/**}</b>：
     * {@code addCorsMappings} 的模式是相对 <b>servlet context</b> 的，而本服务
     * {@code server.servlet.context-path=/api/v1}，实际进入 MVC 的路径是 {@code /smoke}、
     * {@code /auth/login} 这种，永远不会以 {@code /api/v1/} 开头。
     * 写成 {@code /api/v1/**} 会导致 CORS 配置一条都匹配不上 → 预检被
     * {@code DefaultCorsProcessor} 直接 403（现象：允许的 Origin 也 403，且响应没有任何
     * {@code Access-Control-*} 头）。综合测试报告 2026-10-16 报的 CORS 403 就是这个原因。
     */
    @Bean
    public WebMvcConfigurer developmentCorsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://127.0.0.1:5180", "http://localhost:5180")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("Content-Type", "X-CSRF-Token", "Idempotency-Key", "If-Match", "X-Trace-Id")
                        .exposedHeaders("X-Trace-Id", "ETag", "Retry-After", "Content-Disposition")
                        .allowCredentials(true)
                        .maxAge(600);
            }
        };
    }
}
