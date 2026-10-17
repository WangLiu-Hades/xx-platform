package com.xxplatform.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Web 层基础配置（平台侧，改动需走共享层评审，见 BACKEND_TWO_DEV_SPLIT.md §5）。
 *
 * <p>范围：JSON 解析口径、开发环境可信 Origin、跨源拒绝的响应形状。
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
     * CORS 匹配的是 <b>servlet 内路径</b>，而本服务
     * {@code server.servlet.context-path=/api/v1}，实际进入过滤链的路径是 {@code /health}、
     * {@code /auth/csrf} 这种，永远不会以 {@code /api/v1/} 开头。
     * 写成 {@code /api/v1/**} 会导致 CORS 配置一条都匹配不上 → 预检被
     * {@code DefaultCorsProcessor} 直接 403（现象：允许的 Origin 也 403，且响应没有任何
     * {@code Access-Control-*} 头）。综合测试报告 2026-10-16 报的 CORS 403 就是这个原因。
     *
     * <p><b>为什么从 {@code addCorsMappings} 改成注册 {@link CorsFilter} 这个 bean</b>：
     * 需要替换拒绝时的响应体（默认是纯文本 {@code Invalid CORS request}，违反统一响应壳）。
     * 只有自己构造 {@code CorsFilter} 才能 {@code setCorsProcessor}；MVC 的
     * {@code addCorsMappings} 路径不提供这个扩展点。既然只用一套，就不要再保留
     * {@code addCorsMappings}——两处配置会变成两份真相，改了一个忘了另一个时
     * 表现为"预检过了但实际请求被拒"这类难查的问题。
     *
     * <p><b>顺序</b>：排在 {@code RequestIdFilter} 之后（{@code HIGHEST_PRECEDENCE + 10}），
     * 否则拒绝响应里拿不到 traceId，会出现"响应头有 X-Trace-Id、响应体 traceId 是
     * unknown-trace"的不一致。
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> developmentCorsFilter(ObjectMapper objectMapper) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(java.util.List.of("http://127.0.0.1:5180", "http://localhost:5180"));
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(java.util.List.of(
                "Content-Type", "X-CSRF-Token", "Idempotency-Key", "If-Match", "X-Trace-Id"));
        config.setExposedHeaders(java.util.List.of(
                "X-Trace-Id", "ETag", "Retry-After", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        CorsFilter filter = new CorsFilter(source);
        filter.setCorsProcessor(new ContractCorsProcessor(objectMapper));

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
