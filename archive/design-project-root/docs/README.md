# 工程文档导航

本目录保存平台的设计及开发交接资料，通过Markdown阅读器或编辑器打开即可，无需启动前端。

## 文件与目录

| 目录 | 内容与使用者 |
| --- | --- |
| [system-design/](<../../../docs/system-design/README.md>) | 需求追踪、架构、API、数据库与界面设计；供前端、后端、数据和测试工程师共同使用 |
| [project-governance/](<../../../docs/project-governance/README.md>) | 当前职责、逐接口清单、待确认事项、实际进度、后续计划及检查证据；供领任务与评审使用 |

## 怎么使用

首次接手先读治理目录的`ENGINEERING_ASSIGNMENTS.md`和`DEVELOPMENT_PLAN.md`，了解谁负责和先做什么，再按系统设计入口阅读对应专题。接口实现以`system-design/api/openapi.yaml`为唯一当前机器契约，数据库结构看`system-design/database/schema.sql`，运行前端看`system-design/ui/prototype/README.md`。

## 维护与检查

业务冲突按原始PDF核对；接口、数据库、页面和交接资料需要同步变更。历史审计不能代替当前测试，Mock展示不能作为后端或算法完成证据。返回[项目入口](<../README.md>)。
