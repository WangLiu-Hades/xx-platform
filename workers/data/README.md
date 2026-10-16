# DEV-03 数据处理 Worker

本包实现合成PLATE数据的校验、标准化、来源内去重、错误定位、回放，以及PostgreSQL/PostGIS写入与查询；同时提供工程指标审计和H02/H03/H04交接材料。源端协议和业务规则仍待签认，正式HTTP API与任务服务由DEV-01提供。

当前阶段包含D3-01～08的代码和草案。原工作区版本已通过67项本地测试及12项真实PG测试；本包迁入后重新复验，实际结果见[验证记录](validation/README.md)。S1仍待正式H04及独立复验。

## 运行

从仓库根目录执行。本轮使用Python 3.9.5；离线程序只依赖标准库。

```powershell
python workers/data/run_stage1_local.py
python workers/data/run_worker.py
```

第一条执行67项测试及3个实际CLI进程，验证首次接入、重启回放和完整重试；第二条演示4批合成接入。72条结果应为接纳16、重复17、拒绝39，保留16条事实及43条质量记录。

默认状态、临时文件及日志写入本模块.artifacts，Git已忽略。启动前设置D3_ARTIFACT_ROOT可指定独立输出目录；配置中的statePath/outputRoot相对此目录，referenceData/batchesFile相对配置文件。CLI的--state/--output必须在输出根内。

真实数据库复验：

```powershell
python workers/data/run_pg_lab.py
```

本机需安装PostgreSQL 16和PostGIS。脚本查找PATH或Windows默认安装位置，也可通过--postgres-bin指定bin目录；首次联网安装psycopg[binary]==3.2.13到输出根的.venv-pg。它新建只监听127.0.0.1的独立实例，原样加载仓库schema.sql，执行12项测试后停止自己的实例，现有数据库服务不参与。

结果位于输出根的d3-05-06/postgres-checks.json；实际数据和日志保留在每次新建的pg-lab目录。应为PASS、testsRun=12、failures/errors/skipped=0、clusterStopExitCode=0。原schema共163张平台表；基线16条事实，两个并发探针另增2条。数据库测试后再执行run_stage1_local.py，可让阶段报告引用新PG结果。

## 模块与交接

| 入口 | 用途 |
| --- | --- |
| [plate.py](data_worker/plate.py)、[pipeline.py](data_worker/pipeline.py) | 解析、行隔离、标准化、去重及回放 |
| [pg/ingest.py](data_worker/pg/ingest.py) | 标准事实、载荷、质量及批次结果事务写入 |
| [pg/query.py](data_worker/pg/query.py)、[projection.py](data_worker/pg/projection.py) | 可信授权范围、参数化查询、DTO与签名分页 |
| [metrics.py](data_worker/metrics.py) | 完整合成导出的计数、摘要、引用与质量对账 |
| [固定样本](fixtures/plate-v0/README.md)、[字段草案](docs/data-contract-v0.md) | 合成输入、固定预期及字段讨论 |
| [数据库交接](docs/postgres-handoff-v0.md)、[交付指南](docs/DELIVERY_GUIDE.md) | DEV-01接入、DEV-04复验 |
| [任务协议](docs/worker-contract-v0.md)、[指标目录](docs/metric-catalog-v0.md) | H04草案、工程指标和业务缺口 |
| [阶段计划](docs/DEV3_PLAN.md)、[AGENTS.md](AGENTS.md) | 后续任务与跨对话交接 |

运行模块不读取expected预期文件；测试才使用独立预期。所有测试标识均为合成。

## 当前限制

- 真实厂商、四源全面接入和公开API尚未联通；原始车牌检索需已批准的目标解析服务。
- PgIngestor必须注入DEV-01同事务publisher；lab/fixtures.py仅为测试替身。
- 整批持有async_task行锁会阻塞其他连接续租/取消。预设取消负例通过不代表运行中取消通过。
- 游标只在同一限时只读数据库会话有效，跨HTTP请求的连接/快照归属待设计。
- 8项工程指标可对账；2项缺源业务指标为UNAVAILABLE；8类报表和P-004/005/006/020等未决规则未关闭。
- 小样本和EXPLAIN不代表生产容量或DEV-04独立验收。

正式分工见[DEV-03入口](../../docs/team/DEV-03-data.md)及[工程交接表](../../docs/project-governance/ENGINEERING_ASSIGNMENTS.md)。
