# D3-07：Worker 内部任务协议草案

更新：2026-10-16。状态：DEV-03 草案已交付，等待 DEV-01 确认并实现 H04；尚未运行正式任务服务。当前只覆盖合成 PLATE 接入，不能视为全部 Worker 任务的定稿。

依据：[工程交接 H04](../../../docs/project-governance/ENGINEERING_ASSIGNMENTS.md)、[API 约定第6节](../../../contracts/api/api-conventions.md)、[权威 DDL](../../../contracts/database/schema.sql)、[任务 SQL 示例](../../../contracts/database/query-examples.sql)。现有实现见 [PostgreSQL 交接](postgres-handoff-v0.md)，可核对的输入示例见 [worker-handoff-example-v0.json](worker-handoff-example-v0.json)。示例不是已经发生的任务领取或发布。

## 职责与输入

DEV-01 拥有唯一公开 API、授权、任务状态、领取/续租/取消/重试/恢复、领域发布和迁移。DEV-03 接收冻结输入与租约，解析、校验、去重和计算，产生可发布结果。下面的名称是内部调用语义，不定义新 HTTP 路由，不部署第二套任务服务。

| 字段 | 来源/约束 | 当前对应位置 |
| --- | --- | --- |
| taskId、batchId | DEV-01 创建，两个不同 UUID；任务类型 INGESTION | async_task.id、ingestion_batch.id/task_id |
| workerId、attemptNo、leaseGeneration | DEV-01 领取时返回；不能由 Worker 自增或猜测 | lease_owner、current_attempt_no、lease_generation、task_attempt |
| leaseUntil、serverNow | 服务端提供控制信息；服务端时钟判断是否过期 | lease_until；建议 H04 返回数据库时间 |
| parameterSchemaVersion | 当前测试值 synthetic-ingestion-v0，正式版本待签认 | parameter_schema_version |
| parameterSnapshot、parameterHash | 冻结对象与规范 JSON 的 SHA-256；重试不能换参数 | parameter_snapshot、parameter_hash |
| traceId | 贯穿任务、尝试、日志、行错误；公共 DTO 上限64字符 | trace_id；不要把原始标识或凭据放进去 |
| 文件与参考数据 | 已授权且 ACTIVE 的文件、摘要；固定映射/去重/协议及参考数据版本 | source_file_id、source_mapping_version 与输入快照 |

现有 frozen_input() 固定 synthetic=true、参考指纹、输入字节 SHA-256、完整批次元数据和 signature。规范序列化由 Python common.canonical 定义：UTF-8、键排序、紧凑 JSON、禁止非有限数；此规则只用于当前合成输入，跨语言数值规范化还要由 DEV-01 签认。该摘要用于一致性检查，不是授权签名。

公开 TaskParameterSnapshot 只允许 schemaVersion/submittedAt/parameterHash，以及可选 contextId。内部批次参数、原文件定位和参考数据不能原样回填到通用任务列表。示例明确分开 internalEnvelope 和 publicTaskProjection。SUCCESS 后 INGESTION 的 resultReference.resultId=batchId；不是 taskId、targetId 或 fileAssetId。

## 内部操作与最小事务

| 操作 | 前提与事务行为 | 返回/Worker 行为 |
| --- | --- | --- |
| claim | DEV-01 从可运行 QUEUED 中领取；同事务更新任务、递增 attemptNo/generation、插入 task_attempt 和事件 | 返回冻结输入、完整租约；没有任务是正常空结果 |
| renew | RUNNING、未删除、四元组匹配、当前租约未过期、未请求取消；使用服务端时间更新 | 返回新到期时间和控制信息；失败即停止使用该租约，不自行复活 |
| readControl | 检查任务、当前尝试、租约和 cancelRequestedAt | 返回继续/取消/租约失效；网络状态不明时不发布 |
| publishSuccess | 在同一事务按任务→批次加锁，重校租约/取消/输入；写事实及结果、批次计数、成功尝试、任务 SUCCESS/100 和事件 | 全部提交后才允许公开读取；零行匹配或任一失败回滚整次发布 |
| failAttempt | 工作事务回滚之后，另开短事务；只有仍有有效匹配租约者可结束该尝试 | 保留错误码、原因及必要行错误；不让旧 Worker 覆盖新尝试 |
| acknowledgeCancel | DEV-01 已请求取消；当前有效匹配尝试确认工作停止、无可见半成品 | 同事务更新尝试/任务/批次为 CANCELLED；重复确认由服务端幂等返回 |
| retry | 只有 FAILED 的可重试任务；校验权限、冻结输入与幂等；重新 QUEUED，保留原历史 | 仍为同 taskId；下一次 claim 才创建新 attempt，避免 retry 和 claim 各加一次 |
| recoverExpired | DEV-01 恢复器确认 RUNNING 租约已过期，锁定任务，封存旧尝试并清理旧租约后重新排队 | 后续 claim 使用更高 generation；旧 Worker 永远不能发布或覆盖新状态 |

领取 SQL 示例已包含 claim 时 attemptNo+1 与 generation+1。失败重试的 retryCount、恢复时旧尝试原因、退避、最大次数、心跳周期和租约长度都需要 DEV-01 配置与 P-003 约束，本文不把测试1800秒当作生产默认值。

任务状态遵守现有触发器：PENDING→QUEUED/FAILED/CANCELLED；QUEUED→RUNNING/FAILED/CANCELLED；RUNNING→SUCCESS/FAILED/CANCELLED/QUEUED；FAILED→QUEUED。RUNNING→QUEUED 只用于受控恢复。SUCCESS、CANCELLED 没有本草案定义的再次运行入口。

## 取消、失败、重试和未知提交结果

- RUNNING 的 cancel 返回202仅表示已记录 cancelRequestedAt；Worker/恢复器确认后才成为 CANCELLED。取消与成功发布同时发生时，以受控事务锁下先完成的合法状态为准。
- 确定的行级错误继续记 REJECTED；批次可在存在拒绝行时 SUCCESS，并显示拒绝计数。这是当前原始接入协议，与标准设备/人员库导入的 ALL_OR_NOTHING/SKIP 规则分开。
- SQL异常、输入摘要变化、来源配置变化、租约失效或发布拒绝应回滚事实、逐行结果及本事务内的质量记录；失败状态/错误诊断必须由 DEV-01 在回滚后持久化。当前程序没有实现这一步。
- 取消或失租后停止计算/写入；旧租约不得写失败、重试或成功状态。错误行按 taskId+attemptNo+rowNumber 定位；新尝试不能覆盖历史错误。
- 网络断开导致提交结果不明时，先由 DEV-01 查询 task+batch+attempt 的一致状态，再决定重试。已经 SUCCESS 返回原结果；仍 RUNNING/不一致则对账，不能凭客户端异常宣称数据库已回滚。
- 相同 batchId 和冻结输入的成功重试不追加结果；REPLAY 使用新 batchId，引用原成功批次，保持来源和输入摘要。BACKFILL 的真实语义尚未在本合成 Worker 实现。

## 现有实现的明确缺口

1. PgIngestor 当前在整个批次事务起点锁定 async_task，直到本批写完才释放。另一连接的续租/取消会等待这把锁；重复 assert_lease 无法读取尚未写入的取消请求。已通过的真实PG取消负例只是预先设置取消，不能证明运行中取消及时响应。
2. 正式接入建议将耗时解析/计算放在无任务行长锁的阶段，按 DEV-01 控制接口检查取消和续租；仅在最终有限事务内加锁、重验冻结输入、原子发布。大批量需明确暂存/分块发布方案及可见性，不能直接把当前小样本长事务作为生产循环。
3. 当前 publish_for_lab 只更新测试任务/尝试，未实现完整 task_event、领取、续租、恢复、失败落盘和对外授权。PgIngestor 的 publisher 回调仍是 DEV-01 的正式接入点。
4. 内部 FIXTURE_/PG_/STORAGE_/TASK_LEASE_INVALID 标签不全是公开 ErrorCode；DEV-01 应保留诊断原因并映射为既有公开错误枚举。不能擅自扩展 OpenAPI。
5. D3-05/06的12项真实PG测试已通过，使用的是lab任务回调；本协议审查、67项本地回归和数据库测试均不能替代正式H04联调。

## DEV-01 / DEV-04 联调验收表

| 场景 | 必须观察的结果 | 当前状态 |
| --- | --- | --- |
| 两 Worker 竞争同任务 | 只有一方拿到当前租约，attempt 插入与领取同事务 | 待实现/实测 |
| 续租成功、续租失败、时钟偏差 | 以服务端时间裁决；失租后不发布 | 待实现/实测 |
| 计算期间取消、发布边界取消 | 不公开半成品；不会靠批次长锁一直拖延取消 | 待改造/实测 |
| Worker 崩溃与过期回收 | 新 generation 可恢复；旧者所有终态写入失败 | 待实现/实测 |
| 失败重试 | 同 taskId、新 attempt，冻结输入不变，历史不丢失 | 待实现/实测 |
| 提交响应丢失 | 先对账，已成功不重复发布 | 待实现/实测 |
| 参数/原始文件被更换 | 摘要不一致，拒绝执行，不生成事实 | 纯逻辑及真实PG负例已通过；正式H04待联调 |
| 事务发布失败 | 无半条事实、无提前可见结果、正确失败诊断 | 真实PG回滚及发布过期负例通过；正式H04待实测 |
| 任务/文件/结果范围收缩 | 每次读取仍重校授权，内部输入不泄露到公开列表 | DEV-01 待联调 |

交付物已具备审查条件；DEV-01 未确认前保持“协议草案”，不把项目 H04 或 P-003 标为完成。
