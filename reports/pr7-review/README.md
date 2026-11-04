# PR #7 合并验收与 BUG-007 关闭证据

日期：2026-11-04。#7 已实际合入 main；合并提交为 [`5b0a216`](https://github.com/WangLiu-Hades/xx-platform/commit/5b0a216ec085c368c5778a40176ec1d2ffda16b7)，GitHub PR 状态为 merged / closed。

修复提交：[`400b1b6`](https://github.com/WangLiu-Hades/xx-platform/commit/400b1b650d729d1976e4e8ac2a8d5eefb250a48a)。合并前主分支为 `aed331046f0f2fb221cc634eb594b15eb5a0cb62`，已经包含 #5。此次保留 #5 功能与全部原有历史，只修正配额测试的收尾，不撤销 #5。

## 修复与依据

`TaskQuotaIntegrationTests.clearQueue` 改为 `@AfterEach`，用例退出时释放未终态任务；公共父类的 `@BeforeEach` 仍在，保留开始前清场。没有放宽生产并发预算、删除权限断言或跳过测试。

核心规则只取自合并前远程 main 的 Git 原件。相关业务依据是 [TaskStore 协议](https://github.com/WangLiu-Hades/xx-platform/blob/aed331046f0f2fb221cc634eb594b15eb5a0cb62/docs/project-governance/INTERNAL_TASK_PROTOCOL.md) 和[暂定规则明细](https://github.com/WangLiu-Hades/xx-platform/blob/aed331046f0f2fb221cc634eb594b15eb5a0cb62/docs/project-governance/暂定规则明细.md)。160 项原件 blob 全部一致，覆盖 OpenAPI、平台功能原始 PDF、数据库字典/DDL、治理和系统设计。此目录保存验收证据，问题台账仍只有原来的 [BUG-007 条目](../pr3-fix/known-bugs-2026-11-03.md#bug-007)。

## 实际回归

- 本机从原 OpenAPI 重新生成 655 个 Java 文件，JDK 17 实际编译 140 个生产源码和 65 个测试源码。
- 同一 JVM、同一个独立测试库，实际按“配额领取 → 导出许可 → 导出许可 → 配额领取”运行；4 次方法执行全部成功。两次配额用例退出后，全库 PENDING/QUEUED/RUNNING 数均为 0。
- 另用本轮新建的 PostgreSQL/PostGIS 空库执行完整回归：63 个套件，785 项成功，失败 0、错误 0、跳过 0，JAR 打包通过。
- Windows 使用 Compiler API 辅助程序处理本机依赖 JAR 句柄关闭限制；临时 POM 只覆盖 build.directory，Maven verify 仅跳过已实际执行的重复生成，未跳过测试。没有把此路径说成本机未经调整的 clean verify。
- GitHub 对修复提交实际运行原始 `mvn -B -Pgenerate-api clean verify`：[后端 CI](https://github.com/WangLiu-Hades/xx-platform/actions/runs/41611646241) 的 XML 统计同样为 785/0/0/0；[前端 CI](https://github.com/WangLiu-Hades/xx-platform/actions/runs/41611646379) 成功。前端生产源码与 main 完全相同。
- 目录/文档/映射检查 14 项通过。使用独立测试实例，验证后停止本轮 PostgreSQL 进程；未修改 #6 演示数据库。

满足原台账要求的实际复验后，将 BUG-007 更新为 CLOSED。BUG-001—006 的条目、编号、规则依据及 OPEN 状态保留。本次不声称其他未接入的业务能力已经完成。

## 可核对的证据

- [本地全量结果与源码摘要](local-validation.json)
- [160 项主分支原件及合并完整性](candidate-integrity.json)
- [正向与反向顺序原始日志](ordered-and-reverse.log)
- [GitHub 实际测试 XML 统计与原始产物摘要](github-test-summary.json)
- [远程代码合并确认](remote-merge-confirmation.json)
- [台账文档更新后的目录检查](closeout-layout-checks.json)

GitHub CI 原始产物由 nightly.link 公共下载代理取得，并以 GitHub 官方运行/作业 API 核对提交及状态；摘要保留产物 SHA256。完整 Maven 日志与原始 ZIP 另存于本机下载目录的本轮审查包，不把旧报告数量当成本次复验。
