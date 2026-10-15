package com.xxplatform.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 */
@SpringBootApplication
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
