package com.xxplatform.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 多维数据分析平台 · 正式 HTTP API 启动类（DEV-01）。
 *
 * <p>契约唯一来源 {@code contracts/api/openapi.yaml}，Base URL {@code /api/v1}。
 * 本工程当前只包含基础骨架：统一响应壳、异常兜底、配置与目录分层。
 * 会话/CSRF、权限与数据范围、幂等、ETag、游标、任务底座、文件底座等基础设施
 * 按 {@code docs/project-governance/BACKEND_TWO_DEV_SPLIT.md} 的阶段计划补齐。
 *
 * <p>本地默认端口 8080，与前端 Vite 开发代理默认目标一致
 * （见 {@code contracts/api/FRONTEND_INTEGRATION.md} §3）。
 *
 * <p><b>数据库可选</b>：数据源只在 {@code db} profile 下启用
 * （见 {@code application-db.yml}）。不带该 profile 启动时应用**不连接数据库**，
 * 因此 {@code /health}、{@code /smoke} 这类无库端点可以在没有 PostgreSQL 的机器上
 * 直接运行；需要数据库的端点（当前是 {@code /auth/csrf}）在没有 profile 时
 * 依赖注入失败会快速暴露，而不是运行到一半才报错。
 *
 * <p>{@code @ConfigurationPropertiesScan} 用于装配 {@code common/CsrfProperties}
 * 这类策略参数类；没有它，{@code @ConfigurationProperties} 只是一个注解、不会被绑定。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
