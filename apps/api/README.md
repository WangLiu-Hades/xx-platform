> 2026-10-18当前版本：[PD-2026-10-18.v1](../../docs/project-governance/暂定方案与修改记录.md)。当前分工01/02后端、03前端、04数据、05测试；下文旧数量/编号/NO及签认门禁保留作历史追踪，不覆盖本轮暂定设计，也不证明当前实现。旧治理生成脚本不得覆盖本轮policy/暂定文档。

# 正式 API 开发位置（待实现）

DEV-01 在此开发全部公共HTTP API、鉴权/事务、任务、Context与文件底座。读取 [OpenAPI](../../contracts/api/openapi.yaml) 和 [数据库设计](../../contracts/database/README.md)，调用DEV-03的内部数据/计算Worker。当前本目录只有说明，未创建可运行服务。任务与验收看 [DEV-01入口](../../docs/team/DEV-01-backend.md)。
