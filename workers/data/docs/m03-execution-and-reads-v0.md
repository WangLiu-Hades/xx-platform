> 2026-10-19当前分工：DEV-04数据、DEV-03前端、DEV-05独立测试；DEV-01维护共享TaskStore/授权/迁移，DEV-02负责M03等领域公开API与查询编排。下文D3编号和v0协议为交付历史；业务与任务参数以[主分支PD方案](../../../docs/project-governance/暂定方案与修改记录.md)及[内部协议](../../../docs/project-governance/INTERNAL_TASK_PROTOCOL.md)为准，旧pending停做/实验租约数值不覆盖现行规则。

# M03 数据侧执行与读取交接

更新：2026-10-16。D3-09方案提案、D3-10有界执行、D3-11四项读取和D3-12数据侧故障用例已实现；正式H04/API和DEV-05独立复验仍待完成。当前代码的真实运行结果以[验证记录](../validation/README.md)中m03系列记录为准，历史67项/12项结果不能证明本轮通过。

## D3-09：采用有界批次

解析、校验和标准化先在内存中完成；此阶段不持有任务行锁。输入字节、物理行数、准备时长、发布时长和锁等待时间均须显式配置。超限拒绝整批，不偷偷截断或分批改变原子性。当前没有新增暂存表、迁移或无限大批次支持。

准备阶段调用`control(lease, point)`，检查点为PREPARE_START、PREPARING、PREPARED及BEFORE_PUBLISH。回调必须确认当前尝试、代次、未过期租约及取消状态，按需续租；只有字面值True表示可以继续。失败、异常或状态不明停止执行。回调自身必须有网络/数据库超时，返回后仍检查准备预算；同步回调或进程停顿没有硬实时保证。

发布阶段独占一个顶层事务，按任务→批次锁顺序重查冻结输入、映射、来源文件、租约及取消状态。开始写入前，剩余租约须大于发布预算加安全余量。事实、行结果、质量、批次计数和成功发布一起提交或回滚。SQL超时预算随事务耗时递减，锁等待另有上限；提交、连接故障和进程调度仍需DEV-01做未知结果对账。

`IngestBudget`字段及实验值：

| 字段 | 实验配置 | 含义 |
| --- | --- | --- |
| max_input_bytes | 8 MiB | 实际输入字节上限 |
| max_rows | 1000 | 包括错误行的物理行数上限 |
| prepare_seconds | 60 | 解析、校验和控制检查的准备预算 |
| publish_timeout_ms | 3000 | 最终写入及发布的累计预算 |
| lock_timeout_ms | 500 | 每条SQL锁等待上限 |
| lease_margin_ms | 1000 | 发布预算外的租约余量 |
| checkpoint_rows / checkpoint_seconds | 32 / 0.25 | 行数或时间到达阈值时检查控制 |

这些数值用于小样本实验，不是生产吞吐、取消时延或P-003承诺。DEV-01/DEV-05提供实际文件规模、租约和性能要求后共同调整；需要更大批次时另行设计暂存可见性与统一迁移。

## D3-10：写入接入方式

模块：[execution.py](../data_worker/pg/execution.py)、[ingest.py](../data_worker/pg/ingest.py)。

```python
from data_worker.pg.execution import IngestBudget
from data_worker.pg.ingest import PgIngestor

# 这些值由服务端配置；control和publisher由DEV-01提供并审查。
budget = IngestBudget(
    max_input_bytes=8 * 1024 * 1024, max_rows=1000, prepare_seconds=60,
    publish_timeout_ms=3000, lock_timeout_ms=500, lease_margin_ms=1000)
writer = PgIngestor(connection, adapter, settings, publisher,
                    control=control, budget=budget)
summary, rows, cached = writer.ingest(batch, lease)
```

连接须空闲且autocommit=True，调用方不能用外层事务包住ingest。准备阶段控制可使用独立连接或短事务；不可让准备过程持有发布事务。已成功且冻结输入相同的批次可返回缓存结果，无须复活旧租约。

`publisher(session, lease, batch)`收到的是同一发布事务的PublishSession，只提供带剩余预算的execute。必须通过它完成任务、当前尝试及事件的成功发布；不得另开连接或自行BEGIN/COMMIT。回调返回True且任务/尝试已一致成功才允许提交。这个接口约束可信实现，不是阻止任意代码访问底层连接的安全沙箱。

跨Java/Python时，普通HTTP成功回调不能保证这一事务原子性。DEV-01需要交付经审查的SQL/数据库函数或其他真正共用事务的方案；如要改为其他一致性模型，须连同领域可见性与迁移重新设计，不能把实验publisher直接部署。

错误行ID现在包含attemptNo，后续尝试不会与保留的旧错误记录冲突。取消/失租只让数据侧停止；FAILED/CANCELLED终态、任务事件、重试和恢复仍由DEV-01控制。失败诊断需在数据事务回滚之后由正式任务服务落盘。

## D3-11：四项内部读取

模块：[ingestion_reads.py](../data_worker/pg/ingestion_reads.py)。返回值按现有IngestionBatch、IngestionBatchPage、IngestionErrorPage和IngestionQualitySummary DTO验证；没有增加公开路由或更改OpenAPI。

| 方法 | 条件和结果 |
| --- | --- |
| list_batches | page/pageSize、source/status、createdAt/status排序；同排序值以批次UUID稳定打破并列，返回授权内total |
| get_batch | 明确批次UUID；无权限与不存在统一DATA_NOT_FOUND |
| list_errors | 当前快照中任务的currentAttemptNo；按rowNumber+错误UUID分页，旧尝试诊断保留但不混入当前视图 |
| quality_summary | 显式UTC[from,to)与可选source；只聚合可见SUCCESS批次，返回asOf及五类计数 |

服务端传入`TrustedBatchScope(principal_id, version, batch_ids)`，batch_ids必须是DEV-01授权器给出的**整批可读**UUID列表。不能将客户端传来的批次ID当授权，也不能仅按source推导权限。空授权返回空列表/零计数；每次调用传最新授权对象，变化后拒绝继续使用旧快照。混合场所批次、未知设备行和部分批次可见性须由DEV-01签认；当前不支持行级部分授权批次。

```python
from data_worker.pg.ingestion_reads import (
    IngestionReadSession, IngestionReadBudget, BatchListSpec, QualitySpec)

# scope、quality_policy、error_projection由后端按已批准规则提供。
limits = IngestionReadBudget(statement_timeout_ms=3000, session_seconds=30,
                            max_window_seconds=7 * 86400, max_authorized_batches=1000)
with IngestionReadSession(connection, scope, limits,
        quality_policy=quality_policy, error_projection=error_projection) as reader:
    batches = reader.list_batches(BatchListSpec(page=1, page_size=20), current_scope)
    quality = reader.quality_summary(
        QualitySpec("2026-10-16T00:00:00Z", "2026-10-18T00:00:00Z"), current_scope)
```

调用方必须及时关闭with会话、独占空闲autocommit连接，并提供新鲜授权。内部读取使用REPEATABLE READ只读快照及语句预算。错误游标签名绑定账号、授权版本/批次范围、批次、attemptNo、页大小和快照，篡改、过期或跨会话使用都拒绝。**目前不是跨HTTP新连接可直接续页的API**；会话归属、超时清理或物化结果由DEV-01决定。

质量口径为显式注入的版本化策略。当前唯一实验策略synthetic-m03-quality-v1采用ingestion_item.processed_at、仅SUCCESS批次；接纳/重复/拒绝计处理行，因此REPLAY会增加处理量但不增加新事实。未知设备计不同(source, source_device_key)，无效坐标按出现指定坐标问题的处理行计数；先按行归并质量问题，再聚合，避免一行多个问题放大计数。processed_at当前取合成receivedAt，不能据此算真实提交时延或生产入库趋势。正式口径待DEV-01/业务确认。

实验错误投影只将已知行级标签映射为既有VALIDATION_ERROR和通用说明，字段名白名单，省略sourceRecordKey和内部诊断。未知标签返回REQUIREMENT_PENDING，等待补充公开映射；DEV-01需交付正式错误投影。错误码、授权与时间口径仍是提案，不能据此关闭P清单。

## D3-12：验证覆盖与边界

本地套件新增26项执行/读取契约用例，加原67项，共93项。真实数据库套件原12项加M03的15项，共27项。实际是否通过须查看带本轮sourceHashes的报告。

新增数据库场景包括：另一连接在准备期间取消/续租、发布边界取消、旧代次拒绝、新尝试接替、子进程写入后以17退出导致事务回滚、已提交响应被丢弃后重试不重复、发布SQL超时回滚、剩余租约不足、批次超限、外层事务拒绝，以及四项读取的DTO/授权/分页/历史错误/质量计数。响应丢失测试只是丢弃已提交返回值，未做真实断网注入。

测试控制器[LabControl](../lab/control.py)、publisher和恢复夹具只操作独立测试库。数据侧测试不能替代真实H04的领取竞争、续租失败网络语义、失败落盘、取消终态确认、事件、退避与恢复时限，也不证明生产容量或DEV-05独立验收。

## 后续提供物

| 接收/提供方 | 下一项需要交付的内容 |
| --- | --- |
| DEV-01 | 可调用H04与取消/恢复语义；同事务成功发布实现；批次权限、质量口径、公开错误映射、游标会话方案；文件/参考数据解析与API包装 |
| DEV-05 | 隔离共享环境与CI入口，独立执行93项本地/27项PG套件；真实规模及性能/恢复验收条件 |
| DEV-03 | 现有入口需要的批次字段、状态、空态和分页交互，与DEV-01核对；不擅自新增专门M03页面 |
| 业务/厂商、DEV-04协同 | 第一条真实来源的获准协议/样本、P-004/P-005规则；为后续真实适配提供依据 |

D3-13待正式H04/API交付后接入并重跑故障场景；D3-14由DEV-05独立复验。真实四源、设备状态/源计数、BACKFILL与版本化回放仍属后续阶段。
