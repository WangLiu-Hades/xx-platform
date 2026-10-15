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
     * <p>这里只放宽"未知字段"：DTO 由契约生成且 {@code additionalProperties:false}，
     * 前端多传字段时按忽略处理，避免直接 400 掩盖真实问题；严格性由生成 DTO 上的校验注解保证。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer contractJsonCustomizer() {
        return builder -> builder.failOnUnknownProperties(false);
    }

    /**
     * 开发期 CORS。
     *
     * <p>正常联调走同源代理（浏览器请求 {@code /api/v1} → Vite 代理 → 127.0.0.1:8080），
     * 不需要 CORS；这里只服务于"前端直连后端端口"的调试场景。
     * 生产必须替换为真实 Origin 白名单，且禁止
     * {@code allowedOriginPatterns("*") + allowCredentials(true)} 组合（契约禁止通配凭据 CORS）。
     */
    @Bean
    public WebMvcConfigurer developmentCorsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/v1/**")
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
