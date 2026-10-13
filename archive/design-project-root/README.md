# 数据分析平台：项目使用入口

本目录包含可运行的 Vue 前端原型、系统设计、接口与数据库契约、工程分工和历史验证资料。**当前正式后端、数据处理 Worker、真实算法和外部设备服务尚未在此目录实现。** 业务要求以用户指定的《平台功能设计（原始）.pdf》为准；规则未确认的部分见待确认清单。

## 文件与目录

| 文件或目录 | 功能与使用入口 |
| --- | --- |
| [docs/](<docs/README.md>) | 所有工程文档的总导航，按阅读者角色选择资料 |
| [docs/system-design/](<../../docs/system-design/README.md>) | 架构、API、数据库、界面、ADR及图示源文件 |
| [docs/project-governance/](<../../docs/project-governance/README.md>) | 当前工程师任务、P-016等未决事项、接口盘点、进度与审查结果 |
| [合同修订工作/](<../../local-only/contract-revisions/README.md>) | 合同副本、修订脚本和排版证据；供合同评审，不是运行程序 |
| [prototype-audit-report.md](<prototype-audit-report.md>) | 旧静态原型审计记录；其结论有历史范围，不能代替当前Vue验证 |
| [prototype-vue-migration-assessment.md](<prototype-vue-migration-assessment.md>) | Vue迁移前的工作量评估；迁移已经完成，后续平台计划看治理目录 |
| `.git/` | Git历史及内部元数据；由Git维护，不是业务源码或部署内容 |

## 怎么使用

展示原型：进入[当前Vue工程](<../../apps/web/README.md>)，按照运行步骤安装依赖并启动。开发前先读[工程分工](<../../docs/project-governance/ENGINEERING_ASSIGNMENTS.md>)、[前端接口交接](<../../docs/project-governance/FRONTEND_API_HANDOFF.md>)和[系统设计](<../../docs/system-design/README.md>)。查P-016从[统一待确认清单](<../../docs/project-governance/PENDING_REGISTER.md>)进入，再定位API与数据库影响点。

向其他电脑发送演示：发送当前工程的整个`dist/`目录（包括`assets/`），并附[前端README](<../../apps/web/README.md>)。接收方用HTTP静态服务打开，不能只发`index.html`。向开发者交接：发送整个`prototype/`源码目录，排除`node_modules/`、`.npm-cache/`及个人`.env.local`，另附`api/`、`database/`与工程分工。开发者需安装依赖；HTTP模式需要后端和同源代理。完整项目资料归档可保留本目录，按需要限制合同和审计原文的分发范围。

## 维护与检查

各关键子目录的README说明其中每个文件或同类生成文件的作用、入口、输入输出和限制。第三方包、缓存、Git对象及渲染临时树不逐个添加说明，由上级README解释。当前目录说明的覆盖和链接检查见[README检查报告](<../../docs/project-governance/README_REVIEW.md>)。本次更新核对日期为2026-10-10，历史证据保留原执行时间。
