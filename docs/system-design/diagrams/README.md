# 架构与流程图源文件

本目录保存Mermaid文本源文件（`.mmd`），用于讨论系统边界、模块依赖、数据与请求流向。用支持Mermaid的本地编辑器预览；无需运行前端或数据库。ER图较大，渲染器需支持文件内设置的文本/边数上限。

## 文件与目录

| 文件 | 图的用途 |
| --- | --- |
| [system-context.mmd](<system-context.mmd>) | 用户、设备、通道、地图与平台的外部边界 |
| [container-architecture.mmd](<container-architecture.mmd>) | 浏览器、静态Web、代理、API、Worker、数据库与文件容器关系 |
| [component-architecture.mmd](<component-architecture.mmd>) | API与Worker内部模块和公共任务/存储依赖 |
| [module-dependency.mmd](<module-dependency.mmd>) | M01–M15模块先后依赖及质量工作贯穿关系 |
| [data-flow.mmd](<data-flow.mmd>) | 接入、隔离、采集、检索、Context、分析、告警与指标数据流 |
| [deployment.mmd](<deployment.mmd>) | DEV/TEST/UAT/PROD和持久卷/备份部署示意 |
| [er-diagram.mmd](<er-diagram.mmd>) | DB-DESIGN-2表字段及关系；精确DDL查schema.sql |
| [auth-sequence.mmd](<auth-sequence.mmd>) | 登录、会话Cookie、当前权限和逐请求校验概览 |
| [search-sequence.mmd](<search-sequence.mmd>) | 检索、权限、游标、Context及地图跳转概览 |
| [analysis-task-sequence.mmd](<analysis-task-sequence.mmd>) | 分析提交、任务领取、租约/尝试、发布及前端轮询概览 |

## 怎么使用

按讨论主题打开对应文件，结合[总体架构](<../01_总体系统架构设计.md>)、[API契约](<../../../contracts/api/README.md>)及[数据库](<../../../contracts/database/README.md>)阅读。图中路径省略了公共前缀和部分错误分支，不能复制概览箭头作为完整实现或接口签名。

## 维护与检查

图示中Worker指向通知/设备适配器的概览箭头不作为实现归属依据：现行分工由DEV-01实现外部投递/命令及事务，DEV-03交计算候选和证据，详见[交接边界](<../../project-governance/ENGINEERING_ASSIGNMENTS.md>)。修改架构或DDL时同步对应图，检查方向、状态、字段和跨模块依赖；最终实现及规则状态看契约和治理清单。返回[系统设计入口](<../README.md>)。
