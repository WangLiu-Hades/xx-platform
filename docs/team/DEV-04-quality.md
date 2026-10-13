# DEV-04 测试与部署 开发入口

## 负责什么、代码放哪

CI、环境、集成测试、部署、可观测性、容量、安全反例、恢复与发布验收。负责复现和门禁；业务/API/算法代码缺陷回归对应主责修复。

工作区：[主目录](../../infra/README.md)、[关联目录](../../tests/README.md)。未实现的目录只有README说明，不代表服务已具备。

## 开始工作

当前可运行前端CI和目录检查；真实后端未实现，不能把契约样例回归写成端到端验收。联调环境与容器/部署脚本放 infra，跨服务用例放根 tests，前端测试继续在 apps/web/tests。

共同必读：[原始PDF](../requirements/source/平台功能设计（原始）.pdf)、[工程任务边界](../project-governance/ENGINEERING_ASSIGNMENTS.md)、[开发计划](../project-governance/DEVELOPMENT_PLAN.md)、[前端接口交接](../project-governance/FRONTEND_API_HANDOFF.md)、[待确认清单](../project-governance/PENDING_REGISTER.md)。

精确契约：[OpenAPI](../../contracts/api/openapi.yaml)、[API约定](../../contracts/api/api-conventions.md)、[DDL](../../contracts/database/schema.sql)、[数据库未决项](../../contracts/database/pending-design.md)。

## 交付与验收

输入：需求验收点、接口/数据库契约、各角色可部署服务与环境需求。输出：测试用例、可重复CI/部署、容量/恢复记录、缺陷与发布结论。验收：目录/契约一致，前端检查通过，真实环境下接口/权限/任务/数据库/恢复及跨服务业务链达标后才允许发布。

每个PR注明实际作者、DEV角色、需求/接口编号、受影响契约、验证结果和未定事项。跨角色边界以工程分工中的交接表为准。返回 [角色总表](README.md)。
