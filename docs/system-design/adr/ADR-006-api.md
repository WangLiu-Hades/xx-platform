# ADR 006 API 风格

**状态：拟议。** Context：固定后台资源、强数据权限、审计和任务状态主导。Decision：REST `/api/v1`、OpenAPI 3.0.3、统一 envelope 与错误码，敏感筛选走 POST body。Alternatives：GraphQL、非版本化 RPC。Consequences：页面契约和 Mock 易生成；跨页复杂目标通过服务端 AnalysisContext 解耦，避免 URL 拼接。
