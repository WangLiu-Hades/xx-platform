> 2026-10-19当前分工：DEV-04数据、DEV-03前端、DEV-05独立测试；DEV-01维护共享TaskStore/授权/迁移，DEV-02负责M03等领域公开API与查询编排。下文D3编号和v0协议为交付历史；业务与任务参数以[主分支PD方案](../../docs/project-governance/暂定方案与修改记录.md)及[内部协议](../../docs/project-governance/INTERNAL_TASK_PROTOCOL.md)为准，旧pending停做/实验租约数值不覆盖现行规则。

> 2026-10-18当前版本：[PD-2026-10-18.v1](../../docs/project-governance/暂定方案与修改记录.md)。当前分工01/02后端、03前端、04数据、05测试；下文旧数量/编号/NO及签认门禁保留作历史追踪，不覆盖本轮暂定设计，也不证明当前实现。旧治理生成脚本不得覆盖本轮policy/暂定文档。

# DEV-04 数据处理 Worker

本包实现合成PLATE数据的校验、标准化、来源内去重、错误定位、回放，以及PostgreSQL/PostGIS写入与查询；同时提供工程指标审计和H02/H03/H04交接材料。源端协议和业务规则仍待签认，正式HTTP API与任务服务由DEV-01提供。

当前包含D3-01～08基线及D3-09～12的数据侧执行、M03读取和故障测试。实现与接入要求见[M03交接](docs/m03-execution-and-reads-v0.md)，实际测试结果见[验证记录](validation/README.md)。正式H04/API及DEV-05独立复验未完成，S1仍为INCOMPLETE。

## 运行

从仓库根目录执行。本轮使用Python 3.9.5；离线程序只依赖标准库。

```powershell
python workers/data/run_stage1_local.py
python workers/data/run_worker.py
```

第一条执行93项测试及3个实际CLI进程，验证首次接入、重启回放和完整重试；第二条演示4批合成接入。72条结果应为接纳16、重复17、拒绝39，保留16条事实及43条质量记录。

默认状态、临时文件及日志写入本模块.artifacts，Git已忽略。启动前设置D3_ARTIFACT_ROOT可指定独立输出目录；配置中的statePath/outputRoot相对此目录，referenceData/batchesFile相对配置文件。CLI的--state/--output必须在输出根内。

真实数据库复验：

```powershell
python workers/data/run_pg_lab.py
```

本机需安装PostgreSQL 16和PostGIS。脚本查找PATH或Windows默认安装位置，也可通过--postgres-bin指定bin目录；首次联网安装psycopg[binary]==3.2.13到输出根的.venv-pg。它新建只监听127.0.0.1的独立实例，原样加载仓库schema.sql，执行27项测试后停止自己的实例，现有数据库服务不参与。

结果位于输出根的d3-05-06/postgres-checks.json；实际数据和日志保留在每次新建的pg-lab目录。应为PASS、testsRun=27、failures/errors/skipped=0、clusterStopExitCode=0。原schema共163张平台表；基线16条事实，故障/并发探针另外写入专用记录，最终数量以当次报告为准。数据库测试后再执行run_stage1_local.py，可让阶段报告引用新PG结果。

## 模块与交接

| 入口 | 用途 |
| --- | --- |
| [plate.py](data_worker/plate.py)、[pipeline.py](data_worker/pipeline.py) | 解析、行隔离、标准化、去重及回放 |
| [pg/ingest.py](data_worker/pg/ingest.py)、[execution.py](data_worker/pg/execution.py) | 有界准备、控制检查和短事务发布 |
| [ingestion_reads.py](data_worker/pg/ingestion_reads.py) | M03批次列表、详情、错误分页和质量汇总 |
| [pg/query.py](data_worker/pg/query.py)、[projection.py](data_worker/pg/projection.py) | 可信授权范围、参数化查询、DTO与签名分页 |
| [metrics.py](data_worker/metrics.py) | 完整合成导出的计数、摘要、引用与质量对账 |
| [固定样本](fixtures/plate-v0/README.md)、[字段草案](docs/data-contract-v0.md) | 合成输入、固定预期及字段讨论 |
| [数据库交接](docs/postgres-handoff-v0.md)、[交付指南](docs/DELIVERY_GUIDE.md) | DEV-01接入、DEV-05复验 |
| [任务协议](docs/worker-contract-v0.md)、[指标目录](docs/metric-catalog-v0.md) | H04草案、工程指标和业务缺口 |
| [阶段计划](docs/DEV3_PLAN.md)、[AGENTS.md](AGENTS.md) | 后续任务与跨对话交接 |

运行模块不读取expected预期文件；测试才使用独立预期。所有测试标识均为合成。

## 当前限制

- 真实厂商、四源全面接入和公开API尚未联通；原始车牌检索需已批准的目标解析服务。
- PgIngestor必须显式注入control、同事务publisher和IngestBudget；lab控制/发布/恢复仅为测试替身。准备阶段不持有任务行锁，最终发布仍使用有时限的原子事务，超大批次拒绝执行。
- M03读取要求DEV-01提供整批可读的TrustedBatchScope及质量/错误策略；混合范围批次和生产统计口径尚待签认。
- 游标只在同一限时只读数据库会话有效，跨HTTP请求的连接/快照归属待设计。
- 8项工程指标可对账；2项缺源业务指标为UNAVAILABLE；8类报表和P-004/005/006/020等未决规则未关闭。
- 小样本和EXPLAIN不代表生产容量或DEV-05独立验收。

正式分工见[DEV-04入口](../../docs/team/DEV-04-data.md)及[工程交接表](../../docs/project-governance/ENGINEERING_ASSIGNMENTS.md)。
