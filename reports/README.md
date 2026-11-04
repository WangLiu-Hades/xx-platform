# 报告

[path-optimization](path-optimization/README.md)记录2026-10-13目录整理。既有前端测试/历史迁移证据在 [apps/web/audit](../apps/web/audit/README.md)，工程交接证据在 [docs/project-governance/audit](../docs/project-governance/audit/README.md)。保留各自日期，不能据历史报告推断本次或生产环境已通过。

[PR #1合并与全面检查](pr1-integration/README.md)记录2026-10-19后端骨架合并、当前分工和全仓库一致性问题；实际验证范围与环境限制见报告。

[保存期限一年](retention-20261019/README.md)记录F08的用户决定、配置/契约同步、验证及Java/PG环境失败的具体证据。

[已知 Bug 台账（PR #3 / #5）](pr3-fix/known-bugs-2026-11-03.md)记录 PR #3 的6项待修问题，以及 PR #5 后端 CI 暴露的测试隔离问题 [BUG-007](pr3-fix/known-bugs-2026-11-03.md#bug-007)，包含优先级、触发条件、代码及规则依据、回归要求和修复登记。沿用现有 `reports/pr3-fix/` 目录，不另建重复问题目录。

2026-11-04：#7 已合入 main，BUG-007 经正反顺序及完整真库回归验收后更新为 CLOSED；原6项问题仍为 OPEN。[本次验收证据](pr7-review/README.md)。
