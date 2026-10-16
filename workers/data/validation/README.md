# 验证记录

只保存适合共享的验收摘要和成功日志，不含实例、虚拟环境、SQLite状态或个人路径。

- local.json / local-tests.txt：仓库布局下67项本地测试与3个CLI进程的结果。
- postgres.json / postgres-tests.txt：仓库布局下12项真实PG结果，发布前必须重新执行通过。
- queries-and-plans.json、concurrency-probe.json、snapshot-probe.json：该次真实库证据。
- package-checks.json：文件、文档链接、冻结样本及代码摘要核对。
- origin-validation.json：迁入前原版本的历史记录，不能替代当前复验。

PG基线：163张表，72行=16接纳+17重复+39拒绝，16条事实、43条质量问题、8组查询匹配；6组含EXPLAIN、2组空授权直接为空。两项并发探针共新增2条，最终18条事实。

正式H04、运行中取消/续租、DEV-04独立复验、真实厂商及容量仍待完成；s1Status=INCOMPLETE。新克隆复验见[模块README](../README.md)。
