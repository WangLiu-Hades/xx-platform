# 内部任务与四源交接协议

版本PD-2026-10-19.v1，暂定、待甲方确认。内部接口不添加到公共Web API。DEV-01主维护公共TaskStore、租约与发布底座，DEV-02负责其领域事务/API编排并调用共享接口；DEV-04负责数据Worker，DEV-03前端，DEV-05验证。内部操作语义保持不变，个人模块见[双人分工](BACKEND_TWO_DEV_SPLIT.md)。

## 四源交接

FENCE/PLATE/MAC/FACE各提交一个参数化查询入口，输入typed filter、scope tuples、snapshot watermark、cursor、limit，返回captureId、targetType/targetId、siteId、deviceId、capturedAt/ingestedAt、sourceCode、nullable WGS84坐标、脱敏展示和nextCursor。精确列名/SQL按contracts/database/query-examples.sql及实际SourceProfile映射，未给真实协议不虚构源表。

scope谓词必须注入每一源SQL；以`(capturedAt,id)`稳定排序，绑定快照水位与当前授权版本。游标不得把前100个Context.targets当全集。交付包含EXPLAIN、授权正反例、重复/缺坐标/无目标/迟到记录样本；样本标合成或真实脱敏。

## TaskStore v1

| 调用 | 输入 | 结果/约束 |
| --- | --- | --- |
| claim | workerId, supportedTypes, limit | 按priority高值优先、submittedAt/id升序；FOR UPDATE SKIP LOCKED；领取并发预算内任务，attempt+1/generation+1，建task_attempt；返回Lease+固定输入 |
| heartbeat | Lease | 每10秒；有效且未过期generation匹配时延长至当前时刻+60秒；返回cancelRequestedAt；0行即LEASE_LOST |
| requestCancel | taskId, actorId, reason | 公共API鉴权后事务置cancel_requested_at；QUEUED可直接CANCELLED；RUNNING待Worker确认；202不代表终止完成 |
| finish | Lease, typed resultManifest | 同事务锁task、校验租约未过期/无取消/当前授权及结果引用，挂发布指针并更新终态/attempt/event/outbox；失败不得公开部分结果 |
| fail | Lease, ErrorCode, sanitized message | 有效租约才写FAILED/attempt/event；消息不得含敏感标识。失租不能覆盖新Worker |
| retry | taskId, actorId, idempotencyKey | 仅FAILED且同输入快照，重新QUEUED；领取时递增attempt；UNKNOWN外部副作用先对账，不能直接重试 |


Lease固定字段：taskId UUID、attemptNo int64、leaseOwner string、leaseGeneration int64、leaseUntil RFC3339；所有写操作条件都含taskId/owner/generation/leaseUntil>now。公开任务保留六态，不增加CLAIMED状态。数据库已有lease_generation，不能只凭workerId回写。

结果按(taskId,attemptNo,leaseGeneration)隔离暂存；发布事务成功才可读。数据库提交与文件系统不作跨系统原子承诺：先写受控临时文件并验证hash，再事务挂指针；孤儿由受控清理器回收。外部发送以outbox+独立幂等键/receipt对账，不能因DB事务回滚宣称外部动作撤回。

租约失效由reaper锁定后递增generation。纯计算可按统一预算重新排队；外部动作处于UNKNOWN时先交对账器，不自动重发。取消/超时后同样禁止旧generation发布。

每次发布记录公式/算法、映射、权限及policyVersion。并发失租、取消与finish竞争、重复outbox、部分文件、缩权场景必须在真实后端集成测试验证；本轮合同静态检查不能代替这些测试。
