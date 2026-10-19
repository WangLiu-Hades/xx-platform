> 2026-10-18当前开发基线：[PD-2026-10-19.v1](../../project-governance/暂定方案与修改记录.md)。本文件原有未决值、四人编号及“未签认统一422/禁用”说明由本版暂定规则覆盖；甲方仍待确认，真实资源/实现就绪仍独立校验。原文保留作追踪。

# ADR 006 API 风格

**状态：拟议。** Context：固定后台资源、强数据权限、审计和任务状态主导。Decision：REST `/api/v1`、OpenAPI 3.0.3、统一 envelope 与错误码，敏感筛选走 POST body。Alternatives：GraphQL、非版本化 RPC。Consequences：页面契约和 Mock 易生成；跨页复杂目标通过服务端 AnalysisContext 解耦，避免 URL 拼接。
