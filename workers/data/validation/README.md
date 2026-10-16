# 验证记录

只保存适合共享的验收摘要和成功日志，不含实例、虚拟环境、SQLite状态或个人路径。实现及验收边界见[M03交接](../docs/m03-execution-and-reads-v0.md)。

## 当前M03版本

2026-10-16本轮已实际通过：93项本地测试（原67+新增26）、27项真实PG测试（原12+新增15），失败/错误/跳过均为0。PG16.15/PostGIS3.4.1，独立实例正常停止；同源码共享证据及强制包核对均PASS。第一轮发现的并发唯一键冲突和数组校验器缺口已修复，失败日志保留在个人过程目录，不覆盖历史成功记录。

- m03-local.json / m03-local-tests.txt：当前源码本地回归与3个CLI进程。
- m03-postgres.json / m03-postgres-tests.txt：当前源码真实PG、控制/崩溃/读取实验；报告的sourceHashes必须与源码一致。
- m03-runtime-cancel.json、m03-runtime-renew.json、m03-crash-recovery.json、m03-batch-reads.json、m03-error-reads.json、m03-quality-reads.json：该次实验观察结果。
- m03-package-checks.json：语法、链接、固定样本/DDL摘要，以及使用--require-validation时的两组报告/源码对齐。validationRequired=false只代表静态检查。
- m03-queries-and-plans.json、m03-concurrency-probe.json、m03-snapshot-probe.json：本轮重跑的原PG查询/并发证据。

真实运行必须保持163张表、72行=16接纳+17重复+39拒绝、16条基线事实、43条质量记录及8组查询预期。故障/并发探针额外写记录，完整库最终库存见当次报告；停止实例要求clusterStopExitCode=0。当前控制/恢复是lab替身；正式H04、DEV-04独立验收、真实厂商及生产容量未完成，s1Status=INCOMPLETE。

## 历史基线

以下保留aa8c975交付时的原始记录，不能用来证明本轮源码通过：

- local.json / local-tests.txt：67项本地测试及3个CLI进程。
- postgres.json / postgres-tests.txt：12项真实PG测试。
- queries-and-plans.json、concurrency-probe.json、snapshot-probe.json：原查询/并发证据，两项探针新增2条，完整库18条事实。
- package-checks.json：原交付包静态与摘要核对。
- origin-validation.json：迁入仓库前原型的历史记录。

新克隆复验见[模块README](../README.md)，完整本地报告默认写.artifacts或D3_ARTIFACT_ROOT。
