# ADR 008 部署

**状态：拟议。** Context：S0 控制复杂度，S2 只有四类主责，S5 的工具是示例。Decision：DEV/TEST/UAT/PROD 隔离的 Docker Compose 栈、迁移和备份演练。Alternatives：裸机手工部署、Kubernetes。Consequences：本地启动与发布路径直观；多机调度、HA、对象存储和零停机升级在部署目标明确后重新设计。
