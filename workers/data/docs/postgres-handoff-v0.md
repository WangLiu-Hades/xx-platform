# PostgreSQL写入与查询交接

面向H02/H03内部接入；正式H04仍需DEV-01实现。原版本已在PG16.15/PostGIS3.4.1完成12项实测，仓库迁入版本必须重新验证，见[验证记录](../validation/README.md)。

仓库根运行python workers/data/run_pg_lab.py，默认输出到workers/data/.artifacts，可用D3_ARTIFACT_ROOT指定独立目录。原schema从仓库contracts/database/schema.sql读取，冻结摘要位于fixtures/contract-baseline.json。脚本新建本机实例、加载原DDL、测试后停止，既有服务不参与。

DEV-03负责数据与自测，DEV-01负责统一迁移、任务与授权，DEV-04负责共享环境和独立复验；步骤见[交付指南](DELIVERY_GUIDE.md)。

## D3-05 写入模块（H02 候选包）

代码：[ingest.py](../data_worker/pg/ingest.py)。入口为 `PgIngestor.ingest(batch, lease)`，依赖 DEV-01 已创建的来源、映射、任务、执行尝试、接入批次，以及场所、设备、目标、文件参照。运行模块不创建账户、领取任务或默认补造资源。

实际写入使用原契约的这些表：

| 内容 | 目标表 |
| --- | --- |
| 原始事件与稳定来源键 | capture.source_event |
| 标准事实与 WGS84 点 | capture.capture_event |
| 车牌类型载荷 | capture.plate_record |
| 本批逐行 ACCEPTED / DUPLICATE / REJECTED | capture.ingestion_item |
| 质量问题和告警 | capture.quality_issue |
| 可定位的任务错误行 | analysis.task_row_error |
| 批次状态和三类计数 | capture.ingestion_batch |

一批数据使用一个事务；源事件、事实、类型载荷、逐行结果、质量记录和成功发布一起提交。任何数据库异常或发布失败都回滚本批；原先已提交的批次保留。坏行属于行级拒绝，不终止其余行。

稳定去重键仍为 `(source_code, source_record_key)`，用唯一约束及 `ON CONFLICT ... DO NOTHING` 处理并发竞争。相同摘要保留首次事件、ID和入库时间；不同摘要拒绝覆盖；同目标的不同源事件继续保留。SQL数据值全部通过驱动参数绑定。

新增数据库约束检查：deviceKey、plateColor 长度不得超过现有 varchar(200)，写入文本不能含 NUL。拒绝行的过长/不可存储来源键不硬截断伪装为真实键，数据库错误行保留 null，并通过来源文件和物理行号定位。过长字段名记录为 `$`。这些是存储约束，不是厂商规则签认。

来源文件若有 fileAssetId，必须 ACTIVE 且 SHA-256 与冻结输入一致；映射版本、去重版本、协议和合成映射定义必须匹配。图片引用在使用前检查 ACTIVE 状态。输出中 plateColor 为数据库 null 时省略；当前表不能区分“输入省略”和“显式 null”，两种返回形式都符合可选可空契约。

相同已完成 batchId 再次执行返回已存逐行结果和批次计数，不重复插入。PostgreSQL 返回值中的 `factCountAfter` 是调用当时的来源事实总数；缓存重试不会伪造过去的库存快照。已有未完成批次若残留行数据则停止，交给任务编排方对账。

## 与 DEV-01 的任务接口

输入 `Lease(task_id, worker_id, attempt_no, generation)`；事务按“任务 → 批次”加锁，检查 RUNNING、当前执行尝试、租约所有者/代次、期限、取消标记及未删除状态。发布前再次检查。

任务的 parameter_snapshot 冻结 synthetic 标记、参考数据指纹、输入文件摘要、完整批次元数据和 signature，parameter_hash 为该快照的规范 JSON SHA-256。`frozen_input()` 可生成该合成输入结构。它是本包内部草案，不是已经签认的 H04 协议。

构造 PgIngestor 时必须提供 `publisher(connection, lease, batch)`。回调须在同一事务完成匹配执行尝试和任务的成功发布；无回调或返回失败就不发布。正式回调由 DEV-01 实现。本包 [lab/fixtures.py](../lab/fixtures.py) 中的 publish_for_lab 只用于独立测试库，明确不是平台任务服务。

DEV-01 后续还需确认：领取/续租、任务失败落盘、取消响应、锁顺序、旧租约与重试、批次输入版本和原始文件保留。当前没有新增公开 HTTP API，也没有替代权威任务状态机。内部 PG_/STORAGE_ 等错误标签由 DEV-01 在公开接口层映射为契约 ErrorCode。

## D3-06 查询模块（H03 候选包）

代码：[query.py](../data_worker/pg/query.py)、[projection.py](../data_worker/pg/projection.py)。[SQL与参数示例](query-example-v0.json) 可供审查，[实际查询与计划](../validation/queries-and-plans.json) 已生成。8组冻结查询均匹配预期，其中6组包含EXPLAIN计划，2组因空场所/无PLATE授权直接返回空结果。计划观察到ix_capture_type_time及相关主键索引，目标附加引用使用ix_capture_other_target；该小样本EXPLAIN不是容量或耗时验收。

- `TrustedScope` 由 DEV-01 根据当前会话和权限计算：账号 UUID、scopeVersion、明确的场所列表与数据类型列表。不能从客户端请求体直接反序列化成授权范围。
- 查询条件与授权场所取交集；空授权或没有 PLATE 权限返回空结果。省略 siteIds/deviceIds 表示不额外过滤；显式空数组表示空结果，不扩大范围。
- 支持 `[from,to)` UTC 时间、场所、目标 UUID、设备 UUID、车牌颜色，以及 captureTime/captureId 的 ASC/DESC 排序。目标匹配覆盖主目标及明确记录的 capture_target，不推断身份。
- 分页按时间和 UUID 排序，或按 UUID 排序。每次多取一条判断是否有下一页，最后一页 nextCursor=null。分页大小限定 1～100。
- 游标用 HMAC-SHA256 签名，绑定账号、范围版本、完整筛选条件、排序、页大小和当前数据库快照/会话；修改范围、篡改、过期或跨会话使用会拒绝。
- `CaptureQuerySession` 在一个有时限的 REPEATABLE READ 只读事务中完成多页读取；并发迟到数据不进入已打开的快照。调用方必须提供独占、空闲的 autocommit 连接，并用 with 及时关闭。
- 查询预算由服务端显式传入。测试脚本的31天窗口、5秒语句预算和60秒会话只是试验配置，不代表业务默认策略。
- 原始 plateNumber 精确检索返回 REQUIREMENT_PENDING，需接入已批准的目标规范化/解析服务后再支持；不能把脱敏展示值用于匹配。

当前游标是同一次内部查询会话的游标。DEV-01 若要跨多个 HTTP 请求消费，仍需设计有时限的快照/连接归属或结果物化机制；不承诺任意新连接都能继续使用旧游标。请求解析器只收查询条件，cursor 单独传给 session.page，避免把客户端授权字段混入条件。

## 真实数据库验收脚本包含什么

脚本会在新建的 d3_lab_ 隔离库中原样执行完整 schema.sql，核对163张平台表，保留全部外键、检查约束、索引和触发器。额外的 dev3_test.marker 只标记测试库，不属于业务迁移。

测试种子显式创建禁用登录的测试账户、来源/映射、场所/设备/目标、任务/尝试/批次及受控文件。图片是程序生成的1像素合成PNG；原始 JSONL 复制到本轮 assets 目录，文件大小和 SHA-256 对应实际字节。所有资源都只在该隔离实例内。

12项真实PostgreSQL测试已全部通过，覆盖72条结果/16条事实、8组冻结查询、排序与权限范围、同批重试、来源文件摘要、数据库回滚、预设取消/过期/旧租约、并发同键去重和多页快照。基线结果为接纳16、重复17、拒绝39，质量问题43条；两个并发探针各新增一条专用记录，测试库最终18条事实。两批并发同键仅1条接纳、1条重复；分页快照保持原11条，新会话能读到第12条迟到记录。

预设取消通过仍不能证明运行中取消。当前整批锁住async_task，其他连接续租/取消会被阻塞；正式H04接入前仍需改造数据执行并补对应测试。

进入数据库后，artifacts/integration-progress.json记录当前阶段；失败报告包含failedPhase及可取得的SQLSTATE、表或约束标识。本次运行的artifacts已包含逐行结果、标准记录、质量记录、批次汇总、查询与EXPLAIN计划、并发/快照探针、集成测试日志及integration-checks.json。后续复验若返回NOT_RUN或FAIL，应记录该次失败并排查，保留本次已通过的历史证据。

## 技术依据

采用 Psycopg 的参数绑定和事务接口，避免拼接用户数据；使用 PostgreSQL 的 REPEATABLE READ 固定分页会话快照。依据：[Psycopg 参数](https://www.psycopg.org/psycopg3/docs/basic/params.html)、[事务](https://www.psycopg.org/psycopg3/docs/basic/transactions.html)、[PostgreSQL 16 隔离级别](https://www.postgresql.org/docs/16/transaction-iso.html)。

驱动固定为 psycopg[binary] 3.2.13，以兼容本机 Python 3.9；官方说明3.3起不再支持Python 3.9，见 [安装支持范围](https://www.psycopg.org/psycopg3/docs/basic/install.html)。这些技术资料不替代项目本身的 OpenAPI、DDL 与未决业务规则。
