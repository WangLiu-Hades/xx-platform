# 团队协作与目录归属

管理员上传、成员完整/精简拉取、个人分支与PR提交，请按 [GitHub上传与分角色开发操作指南](GitHub上传与分角色开发操作指南.md) 逐步执行。

共同仓库是 <https://github.com/WangLiu-Hades/xx-platform>。仓库地址与个人 Git 身份是两个概念；当前按 DEV-01～04 分配任务，尚未提供各人的独立账号，因此没有编造 CODEOWNERS 审批人。使用共享登录时仍须在 PR/提交说明中注明开发角色与实际作者；以后提供个人账号后再启用账号级自动评审规则。

| 角色 | 每天从哪里开始 | 交付边界 |
| --- | --- | --- |
| DEV-01 后端 | [后端任务](DEV-01-backend.md) | 所有公共API、鉴权、事务、任务/文件底座、外部动作 |
| DEV-02 前端 | [前端任务](DEV-02-frontend.md) | Vue界面、状态、客户端校验、接口适配 |
| DEV-03 数据/算法 | [数据任务](DEV-03-data.md) | 接入/ETL、查询/投影、指标、分析、候选与模型 |
| DEV-04 测试/部署 | [测试部署任务](DEV-04-quality.md) | CI、环境、独立验收、容量、恢复与发布 |

完整15模块与10交接边界见 [工程分工](../project-governance/ENGINEERING_ASSIGNMENTS.md)，工期与依赖见 [开发计划](../project-governance/DEVELOPMENT_PLAN.md)。[ownership.json](ownership.json)供工具读取路径责任；[CODEOWNERS.example](../../.github/CODEOWNERS.example)仅模板，当前不生效。

## 提交和评审

一项任务一个分支与 PR，说明角色、需求/接口编号、变更路径、验证命令和未决规则。跨角色改契约时由 DEV-01 统一合并；DEV-03 提供字段/SQL/计算规则，DEV-02 同步生成客户端，DEV-04 核对用例。缺陷由对应代码主责修复，测试人员负责复现和复验。

首次上传先在根目录查看 `git status --short` 及 `git ls-files --others --exclude-standard`。应包含 apps、contracts、docs、必要历史源码与报告；不应包含 node_modules、缓存、个人.env、local-only中的材料（入口README除外）、静态dist和浏览器临时数据。本次未执行 git init、commit 或 push，不假设远程仓库为空；远程已有内容时先合并已有仓库历史。

历史合同、预算和原嵌套Git历史仍在 [local-only](../../local-only/README.md)，它们不会进入共享仓库。若需共享，先决定适当交接范围再单独处理。返回 [开发入口](../START_HERE.md)。
