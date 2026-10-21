> 2026-10-19当前分工：DEV-04数据、DEV-03前端、DEV-05独立测试；DEV-01维护共享TaskStore/授权/迁移，DEV-02负责M03等领域公开API与查询编排。下文D3编号和v0协议为交付历史；业务与任务参数以[主分支PD方案](../../../docs/project-governance/暂定方案与修改记录.md)及[内部协议](../../../docs/project-governance/INTERNAL_TASK_PROTOCOL.md)为准，旧pending停做/实验租约数值不覆盖现行规则。

# DEV-04 阶段交付指南

本包已具备合成PLATE接入、离线回放、PG事务写入/查询及工程指标审计。D3-09～12补上有界准备/短事务发布、控制检查、批次列表/详情/错误分页/质量汇总及并发故障用例；本次整合[ID边界修复](identity-v2.md)，保留已有ID回放与冻结样本。正式代码位于workers/data，可随仓库克隆。

本次D3-12R新增[当前契约复验说明](d3-12r-current-contract.md)。统一执行python workers/data/run_d3_12r.py，当前套件118本地/34PG；历史入口和记录按下文日期理解，真实结论查validation/d3-12r-*。

## 复验

以下命令用于具备对应契约和环境的复验。本次交付以aca438e主线为合入基准；主线165表与历史163表验收摘要不同；已建立独立D3-12R基线，实际复验仍以新报告为准。未改写历史报告，未把文档整合视作主线验收。

从仓库根执行：

```powershell
python workers/data/run_stage1_local.py
python workers/data/run_pg_lab.py
python workers/data/run_stage1_local.py
```

当前套件含128项本地测试和3个CLI进程；PG/PostGIS套件含34项真实库测试；最后生成引用该次PG证据的阶段报告。环境与D3_ARTIFACT_ROOT输出设置见[模块README](../README.md)。本地PASS不代表PG已执行，必须核对postgresVerification及sourceHashes。

2026-10-17的ID修复历史验收加载原163张平台表，72条处理结果、16条基线事实、8组查询匹配冻结预期。故障/并发及ID探针会额外写入专用记录，最终库存取当次报告。旧67/12、m03的93/27及identity-v2的103/30JSON报告保留原字节，日志内容保持一致，当前主线复验须另产证据；详见[验证记录](../validation/README.md)。虚拟环境、完整数据库和个人路径只保留本地。

## 如何接收

| 接收方 | 接入要求 |
| --- | --- |
| DEV-01 | 提供共享TaskStore、授权/迁移、H04控制及同事务publisher；对齐任务/文件/参照、预算及授权重校 |
| DEV-02 | 提供M03公开API与查询编排；对齐合法批次、TrustedScope/TrustedBatchScope、质量/错误映射及游标会话 |
| DEV-05 | 在隔离共享环境独立执行当前套件，提供CI、实际数据规模、容量与恢复验收条件 |
| DEV-04 | 数据执行/查询、自测和缺陷修复；先完成D3-12R新契约复验，再接正式H04完成D3-13，配合D3-14 |
| DEV-03、业务/厂商 | DEV-03确认既有入口的字段/状态；业务与厂商提供获准首源协议/样本及规则签认 |

具体调用方式、预算、质量统计和游标边界见[M03执行与读取交接](m03-execution-and-reads-v0.md)。PgIngestor接口已要求显式control/budget，publisher使用同事务PublishSession；调用方不能用外层事务包装ingest。lab控制、成功发布及恢复仅为实验夹具，不能部署成正式任务服务。

D3-09方案仍待协作签认；D3-10/11为数据侧实现；D3-12当前覆盖实验故障场景。后端骨架已合入，但DEV-01正式TaskStore/H04及同事务publisher、DEV-02 M03领域API仍待实现，D3-13依赖其交付；D3-14需DEV-05独立复验。即使本轮全部测试通过，S1与M03整体仍未验收，真实四源、BACKFILL、设备状态和源计数仍待后续工作。

另见[字段草案](data-contract-v0.md)、[任务协议](worker-contract-v0.md)和[指标目录](metric-catalog-v0.md)。权威DDL/OpenAPI和P清单保持原样。
