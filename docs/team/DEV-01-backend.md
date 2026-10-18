> 历史角色文件，编号已调整。当前01/02后端、03前端、04数据、05测试，以[当前分工](../project-governance/ENGINEERING_ASSIGNMENTS.md)为准；不要按本文件旧角色领取。

# DEV-01 后端 开发入口

## 负责什么、代码放哪

公共HTTP入口、会话与权限、业务事务、Context、任务状态、受控文件、幂等、审计以及对外消息/设备命令。DEV-03不另开一套公共REST API；其查询与计算由你编排调用。

工作区：[主目录](../../apps/api/README.md)、[关联目录](../../workers/delivery/README.md)。未实现的目录只有README说明，不代表服务已具备。

## 开始工作

先落地健康检查、会话/权限、任务/文件/Context底座，再按计划实现检索和各业务域。数据域字段/索引与DEV-03协商，迁移由DEV-01统一合并。对外投递Worker放 workers/delivery，不能因按钮点过就假定短信或设备命令成功。

共同必读：[原始PDF](../requirements/source/平台功能设计（原始）.pdf)、[工程任务边界](../project-governance/ENGINEERING_ASSIGNMENTS.md)、[开发计划](../project-governance/DEVELOPMENT_PLAN.md)、[前端接口交接](../project-governance/FRONTEND_API_HANDOFF.md)、[待确认清单](../project-governance/PENDING_REGISTER.md)。

精确契约：[OpenAPI](../../contracts/api/openapi.yaml)、[API约定](../../contracts/api/api-conventions.md)、[DDL](../../contracts/database/schema.sql)、[数据库未决项](../../contracts/database/pending-design.md)。

## 交付与验收

输入：当前OpenAPI、数据库设计、DEV-03的查询/计算契约。输出：真实响应、授权过滤、事务、任务与错误语义，及Worker调用协议。验收：请求/响应符合契约，权限反例、幂等/重试、失败/取消、文件下载及审计链可被DEV-04复验。

每个PR注明实际作者、DEV角色、需求/接口编号、受影响契约、验证结果和未定事项。跨角色边界以工程分工中的交接表为准。返回 [角色总表](README.md)。
