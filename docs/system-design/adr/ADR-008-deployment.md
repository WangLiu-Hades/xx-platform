> 2026-10-18当前开发基线：[PD-2026-10-19.v1](../../project-governance/暂定方案与修改记录.md)。本文件原有未决值、四人编号及“未签认统一422/禁用”说明由本版暂定规则覆盖；甲方仍待确认，真实资源/实现就绪仍独立校验。原文保留作追踪。

# ADR 008 部署

**状态：拟议。** Context：S0 控制复杂度，S2 只有四类主责，S5 的工具是示例。Decision：DEV/TEST/UAT/PROD 隔离的 Docker Compose 栈、迁移和备份演练。Alternatives：裸机手工部署、Kubernetes。Consequences：本地启动与发布路径直观；多机调度、HA、对象存储和零停机升级在部署目标明确后重新设计。
