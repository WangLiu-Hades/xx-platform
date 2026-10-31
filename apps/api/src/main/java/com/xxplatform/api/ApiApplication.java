package com.xxplatform.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 多维数据分析平台 · 正式 HTTP API 启动类（DEV-01）。
 *
 * <p>契约唯一来源 {@code contracts/api/openapi.yaml}，Base URL {@code /api/v1}。
 * 当前已实现 7 个操作：{@code GET /auth/csrf}、{@code POST /auth/login}、{@code POST /auth/logout}、
 * {@code GET /me}、{@code GET /client-config}、{@code GET /health}、{@code GET /ready}；
 * 其余 186 个按 {@code docs/project-governance/BACKEND_TWO_DEV_SPLIT.md} 的阶段计划补齐
 * （甲 113 个、乙 80 个）。
 *
 * <p>本地默认端口 8080，与前端 Vite 开发代理默认目标一致
 * （见 {@code contracts/api/FRONTEND_INTEGRATION.md} §3）。
 *
 * <p><b>启动必须带数据库</b>：数据源只在 {@code db} profile 下定义
 * （见 {@code application-db.yml}），而仓储层（{@code infra/}）从任务 4 起就依赖
 * {@code JdbcTemplate}，所以只带 {@code dev} 启动会在刷新上下文阶段失败
 * （{@code Failed to configure a DataSource: 'url' attribute is not specified}）。
 * 正确命令见 {@code apps/api/README.md} §1：
 * {@code mvn spring-boot:run "-Dspring-boot.run.profiles=dev,db"}。
 * 一次性的 {@code mvn compile} / {@code mvn test} 不受影响（测试给占位 URL，
 * 并把连接池的 {@code initialization-fail-timeout} 设为 -1）。
 *
 * <p>{@code @ConfigurationPropertiesScan} 用于装配 {@code config/} 下的策略参数类
 * （{@code CsrfProperties}、{@code SessionProperties}、{@code DevSeedProperties}、
 * {@code LoginThrottleProperties}）；没有它，{@code @ConfigurationProperties}
 * 只是一个注解、不会被绑定。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
