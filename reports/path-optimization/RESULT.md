# 路径优化结果与最终检查（2026-10-13）

已在用户指定的“平台 - 路径优化”副本完成整理。原“平台/分工/数据分析平台”工作区未修改；未执行远程Git初始化、提交、推送或部署。共享仓库地址记录为 <https://github.com/WangLiu-Hades/xx-platform>。

## 文件保留与职责结果

迁移前共 **76,211 个文件，1,730,399,024 字节**。全部建立逐文件旧→新映射并完成SHA-256复核：**删除0、遗漏0、不可恢复内容变化0**。其中 76,148 个文件在新位置与原字节完全一致，63 个原文件因路径说明、脚本或实际测试输出而更新，其迁移前原字节均有校验一致的备份。

没有按人员复制四套源码。代码按应用/Worker放置，共用契约只保留一个当前生成入口，人员按角色导航到同一份代码和要求。新增正式后端、Worker、部署和跨服务测试位置均只有职责README，明确待实现。

## 以前与现在

下表旧路径均相对迁移前根目录；具体到每个文件的对照见 [path-map.csv](path-map.csv)。

| 整理前 | 整理后 | 效果 |
| --- | --- | --- |
| 根React/Vinext项目混合分工/资料目录 | apps、contracts、docs、workers等分层 | 当前开发入口清晰 |
| 分工/数据分析平台/docs/system-design/ui/prototype | apps/web | 当前Vue开发目录从41字符缩为8字符 |
| 分工/数据分析平台/docs/system-design/api | contracts/api | API契约集中共享 |
| 分工/数据分析平台/docs/system-design/database | contracts/database | 字典/DDL/查询/迁移设计集中 |
| 分工/数据分析平台/docs/system-design其余部分 | docs/system-design | 保留00–11、ADR、UI设计和图示 |
| 分工/数据分析平台/docs/project-governance | docs/project-governance | 完整分工、交接、计划和历史审查集中 |
| 分工下PDF/Word原始资料 | docs/requirements/source | 原始业务PDF有固定入口 |
| 分工/图表材料 | docs/reference/diagrams | 图表原件集中；预算单独本机保留 |
| 原根React/Vinext、旧静态界面与评估 | archive/legacy-sites、archive/ui | 历史参考与当前Vue区分 |
| 原合同/修订/预算/Office锁文件/嵌套.git | local-only按用途归档 | 完整本机保留，除入口README外Git排除 |
| 分工和任务需要跨多层寻找 | docs/START_HERE、docs/team | 四角色固定入口及交付验收说明 |

完整树见 [DIRECTORY_TREE.md](DIRECTORY_TREE.md)。历史依赖/运行时仍在各归档位置并被Git排除；所有原文件均保留，没有以“清理缓存”为由删除文件。

## 四个角色怎样开始

| 角色 | 代码与任务入口 | 明确边界 |
| --- | --- | --- |
| DEV-01 后端 | [任务](../../docs/team/DEV-01-backend.md)、apps/api、workers/delivery | 所有公共API、鉴权/事务/任务/文件、迁移合并、外部动作 |
| DEV-02 前端 | [任务](../../docs/team/DEV-02-frontend.md)、apps/web | Vue界面/状态/客户端适配/交互与前端测试 |
| DEV-03 数据/算法 | [任务](../../docs/team/DEV-03-data.md)、workers/data、contracts/database | ETL/查询/投影/指标/算法；向DEV-01交付内部协议，不另开公共REST |
| DEV-04 测试/部署 | [任务](../../docs/team/DEV-04-quality.md)、infra、tests、.github | 环境/CI/独立集成验收/容量/恢复；缺陷按代码归属修复 |

现有15模块、12工作包、10交接边界继续以治理分工/开发计划为准。原始PDF、OpenAPI及DDL未改变业务内容；P-016等未签认规则没有因目录整理被关闭。提供的是共同仓库地址，未提供四个个人账号，因此CODEOWNERS仅有不生效的示例，角色归属写入ownership.json，未编造自动评审人。

## 修复与维护

- 修复Markdown链接与当前操作说明中的旧前端/API/数据库路径；同时修复前端generate-api、audit汇总和治理脚本读取路径。
- 重新生成前端客户端后，4个生成源码文件与迁移前内容一致；193项操作、581个Schema一致，无重复operationId、未解析引用或未分配模块。静态引用127项、仅契约66项、操作级pending65项，均不等于后端已实现。
- 保存旧的一次性固定快照检查脚本原件；当前verify_readmes/verify_deliverables兼容入口转发tools/layout/verify_layout.py，不覆盖历史审查日期或伪造新业务验收。
- 新增根/角色/预留开发区README、目录树、路径责任JSON、Git忽略规则、属性、PR模板及前端CI配置。CI在本地做了YAML/命令/路径检查，尚未在远程GitHub运行。
- Git实际排除依赖、缓存、dist、个人环境、本机历史/合同/预算和全文提取物。审计README中本机材料及空临时目录改为明确说明，克隆后不再生成失效导航。

## 最终检查证据

| 检查 | 实际结果 | 证据 |
| --- | --- | --- |
| 全量文件SHA-256与原件备份 | 17项通过，全部76,211个原文件可核对，0删除/遗漏 | [完整性](integrity-checks.json) |
| 活动文档和目录导航 | 14项通过；101份Markdown、629个链接，0失效 | [目录检查](layout-checks.json) |
| OpenAPI生成与静态调用盘点 | 193操作/581Schema一致，4个生成源码无业务变化 | [API盘点](api-inventory/api-inventory.json)、[前端检查](validation.json) |
| 前端单元、类型、生产构建 | 283单元通过；vue-tsc与Vite构建成功 | [实际命令](validation.json) |
| 新构建的浏览器回归 | 28页无页面/控制台错误；11项地图选择回归通过 | [浏览器检查](browser-validation.json) |
| Git共享清单 | 依赖/缓存/本机材料误入0；缺少必需文件0；100MiB超限文件0；导航失效0 | [Git检查](git-readiness.json) |
| 仅共享文件的独立目录副本 | 500个文件副本，14项导航检查通过，不依赖本机旧路径 | [共享副本检查](shared-copy-checks.json) |

检查时Git候选文件约 86.8 MiB，远小于包含本机依赖和缓存的原目录。本次报告也会进入候选清单，新增报告后的最终精确候选数量以git-readiness.json时间为准；独立副本数字是该次实际复制数量。

该结论覆盖目录优化、文件保留、共享导航和迁移引起的前端回归。真实后端/算法/外部服务及生产部署未在本次开发或验收；原有业务未决项、历史漏洞审计及测试结果保留各自实际时间。

## 使用与复验

团队先读 [根README](../../README.md) → [开发入口](../../docs/START_HERE.md) → 自己角色任务。只展示时发送整个apps/web/dist；开发协作按根README提交源码/契约/需求，依赖用npm ci安装。

在仓库根目录执行 `python tools/layout/verify_layout.py` 做可移植导航检查。完整本机历史核对执行 `python tools/layout/verify_layout.py --integrity`，需要保留local-only及原依赖/缓存；克隆后不运行该本机历史门禁。后续正式契约变化用生成/类型/测试/构建验证，不用迁移前哈希永久冻结业务契约。

迁移前清单和SHA见 [before-and-mapping.json](before-and-mapping.json)；更新说明与原件位置见 [modified-files.json](modified-files.json)；汇总见 [summary.json](summary.json)。历史Word/JSON内记录的旧来源路径保留，通过路径对照查询，不将历史文件伪装为本次新验证。
