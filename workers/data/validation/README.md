# 验证记录

## 2026-10-21 D3-12R：当前契约真实验收PASS

当前89份源码已实际通过 **128项本地、34项真实PG测试和3个Worker CLI**，失败/错误/跳过为0。用户普通终端运行d3-12r/runs/20261021T014235-a70ac2ec，全部子命令退出0；代理随后核对实际证据，未重复运行测试。固定main aca438e/PD-2026-10-19.v1，198操作/623 Schema/165表。

[本地报告](d3-12r-local.json)与[日志](d3-12r-local-tests.txt)记录128项：原103项、15项当前契约和10项入口回归；118为审计修复前首轮数量。3次CLI继续匹配72处理行、16条基线事实及缓存重试结果。

[PG报告](d3-12r-postgres.json)与[真实日志](d3-12r-postgres-tests.txt)记录34项：12平台、15M03、3标识兼容、4当前DDL。PostgreSQL 16.15/PostGIS 3.4.1，schemaApplied=true、165张表；clusterStopExitCode=0，关停日志和postmaster.pid消失已核对。[审批约束探针](d3-12r-approval-constraints.json)和[追加记录探针](d3-12r-decision-append-only.json)仅覆盖存储约束，不替代审批授权或加密服务。

[静态检查](d3-12r-static-package-checks.json)与[强制包检查](d3-12r-package-checks.json)均PASS；validationRequired=true，当前源码、三份机器契约和执行报告一致。此前原生工具0xC0000142/NOT_RUN的候选包与日志另行保留，不改旧报告。本轮69份原始证据已登记摘要。

此前独立子agent审计发现1项P2状态判断问题，已修复并复核关闭；当前89份源码与其审计版本相同。真实PG随后执行通过，本次证据复核由主代理完成，不称为DEV-05共享环境独立验收。源代码及原始JSON/成功日志在本次保存过程中保持不变。

统一入口及边界见[D3-12R说明](../docs/d3-12r-current-contract.md)。旧25份样本、31份历史验证产物保留；下文按各自历史日期理解。正式H04、公开M03 API、审批服务、真实源和容量、DEV-05独立复验仍待完成，S1/M03整体继续INCOMPLETE。

## 2026-10-21 主线整合边界

本次为ID修复的交付准备，业务测试运行数为0。受测Python源码与2026-10-17历史报告逐字节对应；主线文档已整合，AGENTS.md此前仅删除环境排查说明，历史82份sourceHashes不再与整包完全一致。原始JSON/日志未改写；交付补丁中的文本日志按仓库属性使用LF换行，不能将其中PASS作为本次整合包的强制验收结论。

当前main aca438e使用PD-2026-10-19.v1与165表DDL；冻结contract-baseline仍指向旧163表schema（F04）。D3-12R需完成契约兼容并另产identity-v2-main-*证据，再按实际源码执行--require-validation。DEV-01正式TaskStore/H04、DEV-02 M03 API和DEV-05独立复验仍待完成。


## 2026-10-17 标识修复版

当时 82 份源码已实际通过 **103 项本地、30 项真实 PG**，失败/错误/跳过均为 0；3 个 CLI 进程通过，72 行与 16 条基线事实继续匹配冻结预期。PG 16.15 / PostGIS 3.4.1，163 张表按原 schema 加载，独立实例已正常停止。

identity-v2-local.json / identity-v2-local-tests.txt 与 identity-v2-postgres.json / identity-v2-postgres-tests.txt 保存本次成功记录；identity-v2-fresh.json、identity-v2-legacy_record.json、identity-v2-legacy_source.json 保存新键及两种旧键回放结果。identity-v2-package-checks.json 已以 validationRequired=true 通过，核对当时本地/PG证据与源码；该报告不是本次主线整合包的验收。

首次最终包检查仅因 PostGIS 完整环境字符串含本机用户目录失败。仓库外导出脚本已改为仅输出数据库版本，原始日志保留；收尾只重做证据和包检查，没有重跑业务测试或改变受测源码。

原套件 finalFactsIncludingConcurrencyProbes=28 只统计原适配器来源；新增标识探针在独立来源各保存 2 条，共 6 条，不能将 28 称为包含这些新来源的全库库存。旧 m03 系列 93/27 仅证明 a74f38d。已收到同事对旧版的报告级复验反馈及 ID 缺陷；修复版仍待独立复测。兼容与盘点说明见[ID 修复](../docs/identity-v2.md)。正式 H04 仍 NOT_IMPLEMENTED，S1/M03 仍 INCOMPLETE。

只保存适合共享的验收摘要和成功日志，不含实例、虚拟环境、SQLite状态或个人路径。实现及验收边界见[M03交接](../docs/m03-execution-and-reads-v0.md)。

## 历史 M03 版本（a74f38d）

2026-10-16本轮已实际通过：93项本地测试（原67+新增26）、27项真实PG测试（原12+新增15），失败/错误/跳过均为0。PG16.15/PostGIS3.4.1，独立实例正常停止；同源码共享证据及强制包核对均PASS。第一轮发现的并发唯一键冲突和数组校验器缺口已修复，失败日志保留在个人过程目录，不覆盖历史成功记录。

- m03-local.json / m03-local-tests.txt：该历史版本本地回归与3个CLI进程。
- m03-postgres.json / m03-postgres-tests.txt：该历史版本真实PG、控制/崩溃/读取实验；报告的sourceHashes必须与源码一致。
- m03-runtime-cancel.json、m03-runtime-renew.json、m03-crash-recovery.json、m03-batch-reads.json、m03-error-reads.json、m03-quality-reads.json：该次实验观察结果。
- m03-package-checks.json：语法、链接、固定样本/DDL摘要，以及使用--require-validation时的两组报告/源码对齐。validationRequired=false只代表静态检查。
- m03-queries-and-plans.json、m03-concurrency-probe.json、m03-snapshot-probe.json：本轮重跑的原PG查询/并发证据。

上述历史验收使用163张表、72行=16接纳+17重复+39拒绝、16条基线事实、43条质量记录及8组查询预期。故障/并发探针额外写记录，完整库最终库存见当次报告；停止实例要求clusterStopExitCode=0。当前控制/恢复是lab替身；正式H04、独立验收（当前由DEV-05负责）、真实厂商及生产容量未完成，s1Status=INCOMPLETE。

## 历史基线

以下保留aa8c975交付时的原始记录，不能用来证明本轮源码通过：

- local.json / local-tests.txt：67项本地测试及3个CLI进程。
- postgres.json / postgres-tests.txt：12项真实PG测试。
- queries-and-plans.json、concurrency-probe.json、snapshot-probe.json：原查询/并发证据，两项探针新增2条，完整库18条事实。
- package-checks.json：原交付包静态与摘要核对。
- origin-validation.json：迁入仓库前原型的历史记录。

新克隆复验见[模块README](../README.md)，完整本地报告默认写.artifacts或D3_ARTIFACT_ROOT。
