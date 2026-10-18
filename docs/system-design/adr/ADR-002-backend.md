> 2026-10-18当前开发基线：[PD-2026-10-18.v1](../../project-governance/暂定方案与修改记录.md)。本文件原有未决值、四人编号及“未签认统一422/禁用”说明由本版暂定规则覆盖；甲方仍待确认，真实资源/实现就绪仍独立校验。原文保留作追踪。

# ADR 002 后端与分析语言

**状态：拟议。** Context：大量 CRUD、RBAC、设备/通知编排及 Python 适合的 ETL/算法工作并存。Decision：Spring Boot 承担唯一业务 API；Python Worker 只负责接入、分析、导出和报告，不复制用户/权限/系统 CRUD。Alternatives：全 Go；全 Python/FastAPI；Java 内全部任务。Consequences：需要 OpenAPI、SQL schema 与任务参数双语言契约测试；开发语言熟练度 Q-008 未确认。
