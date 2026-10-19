> 2026-10-19当前分工：DEV-04数据、DEV-03前端、DEV-05独立测试；DEV-01维护共享TaskStore/授权/迁移，DEV-02负责M03等领域公开API与查询编排。下文D3编号和v0协议为交付历史；业务与任务参数以[主分支PD方案](../../../docs/project-governance/暂定方案与修改记录.md)及[内部协议](../../../docs/project-governance/INTERNAL_TASK_PROTOCOL.md)为准，旧pending停做/实验租约数值不覆盖现行规则。

# DEV-04 阶段交付指南

本包已具备合成PLATE接入、离线回放、PG事务写入/查询及工程指标审计。本轮补上有界准备/短事务发布、控制检查、批次列表/详情/错误分页/质量汇总，以及并发故障用例。正式代码位于workers/data，可随仓库克隆。

## 复验

从仓库根执行：

```powershell
python workers/data/run_stage1_local.py
python workers/data/run_pg_lab.py
python workers/data/run_stage1_local.py
```

先跑93项本地测试和3个CLI进程；再用本机PG/PostGIS执行27项真实库测试；最后生成引用该次PG证据的阶段报告。环境与D3_ARTIFACT_ROOT输出设置见[模块README](../README.md)。本地PASS不代表PG已执行，必须核对postgresVerification及sourceHashes。

163张平台表按原schema加载；72条处理结果、16条基线事实、8组查询匹配冻结预期。故障/并发探针会额外写入专用记录，最终库存取当次报告。旧67项/12项报告保留为历史；新版本结果查[验证记录](../validation/README.md)中的m03系列文件。虚拟环境、完整数据库和个人路径只保留本地。

## 如何接收

| 接收方 | 接入要求 |
| --- | --- |
| DEV-01 | 提供H04、同事务publisher、control及预算；创建合法任务/批次/文件/参照；计算并重校TrustedScope/TrustedBatchScope；签认质量口径与公开错误映射；包装API及游标会话 |
| DEV-05 | 在隔离共享环境独立执行当前套件，提供CI、实际数据规模、容量与恢复验收条件 |
| DEV-04 | 数据执行/查询、自测和缺陷修复；接到正式H04后完成D3-13联调，配合D3-14 |
| DEV-03、业务/厂商 | DEV-03确认既有入口的字段/状态；业务与厂商提供获准首源协议/样本及规则签认 |

具体调用方式、预算、质量统计和游标边界见[M03执行与读取交接](m03-execution-and-reads-v0.md)。PgIngestor接口已要求显式control/budget，publisher使用同事务PublishSession；调用方不能用外层事务包装ingest。lab控制、成功发布及恢复仅为实验夹具，不能部署成正式任务服务。

D3-09方案仍待协作签认；D3-10/11为数据侧实现；D3-12当前覆盖实验故障场景。DEV-01暂无H04/API实现，D3-13依赖其交付；D3-14需DEV-05独立复验。即使本轮全部测试通过，S1与M03整体仍未验收，真实四源、BACKFILL、设备状态和源计数仍待后续工作。

另见[字段草案](data-contract-v0.md)、[任务协议](worker-contract-v0.md)和[指标目录](metric-catalog-v0.md)。权威DDL/OpenAPI和P清单保持原样。
