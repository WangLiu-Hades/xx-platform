# DEV-03 阶段交付指南

本分支交付D3-01～08：65条字段映射和合成协议、离线接入、PG事务写入/范围查询、H04草案、工程指标和复验脚本。代码位于workers/data，可随仓库克隆。

## 复验

从仓库根执行：

```powershell
python workers/data/run_stage1_local.py
python workers/data/run_pg_lab.py
python workers/data/run_stage1_local.py
```

先验证67项本地测试及实际重启/重试；再用本机PG/PostGIS执行12项真实库测试；最后将PG证据引用进新的阶段报告。环境和输出目录见[模块README](../README.md)。

163张平台表按原schema加载；72条处理结果、16条基线事实、8组查询应匹配预期，完整库另含2条并发探针。即使两组测试通过，正式H04与独立复验未完成时s1Status仍为INCOMPLETE。

[验证记录](../validation/README.md)只保存适合共享的摘要、测试日志和计划。完整实例、虚拟环境、SQLite状态和个人路径留在本地；新克隆应自行复验。

## 接收与责任

| 接收方 | 接入要求 |
| --- | --- |
| DEV-01 | 接收PgIngestor、CaptureQuerySession和协议；创建合法任务/批次/参照，注入同事务publisher，计算重校TrustedScope并包装公开API |
| DEV-04 | 按固定样本独立复验，负责共享环境、部署、容量与备份恢复 |
| DEV-03 | 数据模块、自测与缺陷修复；继续完成长任务控制及正式H04适配 |
| 业务/厂商 | 获准源协议/样本、指标公式与P清单签认 |

下一步先解决整批任务行锁阻塞续租/取消，明确短发布事务与大批暂存可见性，再完成实际任务领取/恢复/失败/重试联调。真实厂商与生产报表另行验收。

详见[字段草案](data-contract-v0.md)、[任务协议](worker-contract-v0.md)和[指标目录](metric-catalog-v0.md)。本分支不修改权威DDL/OpenAPI或关闭P清单。
