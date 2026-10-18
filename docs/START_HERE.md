> 2026-10-18修订：当前暂定开发规则及分工以[PD-2026-10-18.v1](project-governance/暂定方案与修改记录.md)为准。下文旧pending/NO/签认门禁和人员编号保留作历史追踪；本轮不声称后端或生产验收通过。

# 从这里开始开发

需要把项目上传GitHub或拉到自己的电脑，先按 [GitHub逐步操作指南](team/GitHub上传与分角色开发操作指南.md) 操作，再继续下面的任务入口。

1. 读 [原始功能 PDF](requirements/source/平台功能设计（原始）.pdf) 和 [需求追踪](system-design/00_需求追踪与设计说明.md)。业务冲突按原始 PDF 修正；无法从 PDF 得出结论的规则登记待确认，不能臆造。
2. 选择角色：[DEV-01 后端](team/DEV-01-backend.md)、[DEV-02 前端](team/DEV-02-frontend.md)、[DEV-03 数据/算法](team/DEV-03-data.md)、[DEV-04 测试/部署](team/DEV-04-quality.md)。每个入口均有代码位置、输入输出和验收责任。
3. 领任务前查 [工程任务边界](project-governance/ENGINEERING_ASSIGNMENTS.md)、[开发顺序与依赖](project-governance/DEVELOPMENT_PLAN.md) 和 [P清单](project-governance/PENDING_REGISTER.md)。P-016 的问题主记录在数据库 pending-design 和 OpenAPI pending 中，关闭需要业务规则及证据签认。
4. 对接口只改 [当前 OpenAPI](../contracts/api/openapi.yaml)，对数据库只以 [schema.sql](../contracts/database/schema.sql)为当前设计入口，再同步实现、生成类型、测试和说明。
5. 按 [前端接口交接](project-governance/FRONTEND_API_HANDOFF.md)完成 API→任务→Worker→结果→前端联调。静态引用不代表真实后端已经接通。

## 现在可以直接使用什么

Vue 展示原型、193项 OpenAPI 操作契约、数据库设计、任务边界及需求追踪可直接查阅。`apps/api`、`workers`、`infra` 与根 `tests` 当前只有职责说明；后端、真实数据接入、算法、可靠投递与生产环境尚待开发。前端已有 tests 是实际代码，位置为 [apps/web/tests](../apps/web/tests/README.md)。

不要从 `archive/legacy-sites` 启动当前 Vue。找旧文件用 [路径对照](../reports/path-optimization/path-map.csv)，查本次结果看 [优化报告](../reports/path-optimization/RESULT.md)。
