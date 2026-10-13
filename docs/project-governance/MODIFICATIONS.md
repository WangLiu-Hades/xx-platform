# 本轮修改与交付文件

日期：2026-10-10。下面记录本轮审查和交接任务的修改，不把项目中原有未提交文件都归为本轮新增。

## 新增交付

- `README.md`：交付导航。
- `PENDING_REGISTER.md`：21项P编号的定位、提案/决策责任和关闭证据。
- `FRONTEND_API_HANDOFF.md`：193个操作的盘点、前端调用链、HTTP配置和真实联调标准。
- `ENGINEERING_ASSIGNMENTS.md`：四角色、15模块、10项交接和首批任务。
- `多维数据分析平台开发模块与人员分工（修订版）.docx`：保留原模块卡片，在副本中修正22段原文及相关表格，补充实现边界、交接、待确认、进度与计划。
- `DEVELOPMENT_PLAN.md`：逐层完成状态、12工作包、人日、依赖和阶段验收。
- `PROJECT_AUDIT.md`：全目录覆盖、修复、检查证据及限制。
- `scripts/`及`audit/`：可重复盘点/核对脚本、逐接口CSV/JSON、全文件哈希清单、当前测试证据、Word渲染与最终交付校验。

## 修改现有文件

| 文件（相对项目根目录） | 修改目的 |
| --- | --- |
| `apps/web/src/api/mutations.ts` | 修正Context的sourceModule旧编号，检索/研判/数据集/布控/档案来源与现行M01–M15对应 |
| `apps/web/tests/http-forms.test.ts` | 纠正碰撞模块期望，增加14个模块来源回归场景 |
| `apps/web/CHANGES.md` | 记录上述修复和283项当前单测 |
| `docs/system-design/README.md` | 增加现行工程交接入口 |
| `docs/system-design/00_需求追踪与设计说明.md` | 说明M11历史主责与实际API/算法责任 |
| `docs/system-design/01_总体系统架构设计.md` | 区分模块协调主责、实现责任和目标目录/已实现代码 |
| `docs/system-design/09_部署架构设计.md` | 移除“真实43表旧库”已存在的无证据断言 |
| `docs/system-design/10_开发联调与集成方案.md` | 明确公开API、计算Worker、外部副作用和迁移的交接边界 |
| `docs/system-design/11_架构风险与待确认事项.md` | 同步已解决的工程分工和PDF优先规则，保留真正缺失输入 |
| `contracts/api/README.md` | 引用193/127/66逐项接口清单 |
| `contracts/api/OPENAPI_PENDING.md` | 同步PDF优先和责任/关闭入口，不擅自关闭未决业务规则 |
| `contracts/database/pending-design.md` | 指向统一P清单和关闭证据 |

前端`dist`已重新构建；`audit/vue-migration`中对应单测、28页冒烟、3组HTTP研判及11项地图框选报告是本次运行结果。其他旧审计保留原日期与范围。

原始PDF、原人员分工DOCX、`openapi.yaml`和`schema.sql`未修改，最终检查核对其SHA256。本轮没有实现正式后端或真实算法服务；修订版是可以直接分发评审的工程任务基线。

## 用户授权后的依赖漏洞补检

2026-10-10，经用户明确授权发送依赖名称、版本及依赖关系后，补做公共npm registry联网检查。npm audit成功返回0项已知漏洞，依赖总数245，范围包含生产、开发、可选及peer依赖。`package.json`与`package-lock.json`前后SHA256一致，无需更新依赖或重建前端。

- 新增`DEPENDENCY_AUDIT.md`、`scripts/audit_dependencies.cjs`及`audit/dependency-current-run.json`，保存结果说明、可重复执行脚本、时间、命令、退出码和哈希。
- 刷新`audit/dependency-current.json`；先前DNS失败结果保留为`audit/dependency-pre-authorization.json`。
- 更新`PROJECT_AUDIT.md`和`README.md`，将尚未完成的依赖检查状态改为本次实际结果。
- `scripts/verify_deliverables.py`改为核对本次扫描、当前输入和结果证据；重新生成`audit/checks.json`及`audit/output-manifest.json`。
- 最终核对发现项目内Word修订版已移至上级分工目录；从该文件补回内容完全相同的项目副本。SHA256仍为`184991c0ed19639ffe8a705281092983d519f2b37d254cfe4e99cb27453ad445`，上级文件未改动，既有逐页验收仍适用。

先前浏览器、类型和单测结果保留实际执行日期；历史迁移审计未重新运行或改写为本次扫描。此次只补做依赖检查和交付一致性核对，未改动前端业务代码、Word修订版或接口契约。
