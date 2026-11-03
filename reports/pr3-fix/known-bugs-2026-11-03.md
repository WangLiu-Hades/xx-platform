# 已知 Bug 台账（PR #3 / #5）

发现日期：2026-11-03（Asia/Shanghai）。来源：[PR #3](https://github.com/WangLiu-Hades/xx-platform/pull/3) 合并前复审。

受审查代码：`7e5f131704c784fc61657d8c2f5999bc83e44d44`。规则基线：`aca438ebfb218d40350b60ff933d2251bed50500`。PR #3 已按用户指示于 2026-11-03 16:34（Asia/Shanghai）合入 main，合并提交为 [`ea741a1`](https://github.com/WangLiu-Hades/xx-platform/commit/ea741a181fd7a481cb228cf843b09b1c0ac8e2b1)。本台账只登记问题，不修改业务实现；合并不代表以下问题已经修复。

所有条目初始状态均为 **OPEN / 待修复**。修复后需登记修复提交、实际回归命令及结果，才能改为 CLOSED；历史修复报告和旧的通过数量不自动关闭本台账条目。后续编号从 BUG-008 递增，保留原编号和发现日期。

2026-11-03：根据用户提供的最新 PR #5（`d11265c17f728acc3a90aee79677c6d87a7030c9`）复审结果，补记 BUG-007。用户明确要求先登记该问题，再合并 #5；本次不修复业务或测试源码，不将合并视为 CI 通过或问题关闭。BUG-001 至 BUG-006 的既有状态保留。

## 问题索引

| 编号 | 优先级 | 状态 | 问题 | 建议主责 |
| --- | --- | --- | --- | --- |
| [BUG-001](#bug-001) | P1 | OPEN | 重试缺少当前领域权限、数据范围和 Context 复核 | DEV-01，相关领域由 DEV-02 协作 |
| [BUG-002](#bug-002) | P2 | OPEN | 重试绕过每用户等待额度和全局队列限制 | DEV-01 |
| [BUG-003](#bug-003) | P1 | OPEN | 可选本地心跳绕过设备命令 TTL，不能证明 Worker 存活 | DEV-01，DEV-04 协作 |
| [BUG-004](#bug-004) | P2 | OPEN | 实际启动租约为两分钟，覆盖协议的 60 秒 | DEV-01 |
| [BUG-005](#bug-005) | P2 | OPEN | 请求取消后，Worker 无法确认 CANCELLED | DEV-01，DEV-04 协作 |
| [BUG-006](#bug-006) | P2 | OPEN | 任务取消、重试及拒绝路径缺少正式操作审计 | DEV-01 |
| [BUG-007](#bug-007) | P1 | OPEN | 配额集成测试遗留 RUNNING 任务，污染后续导出权限测试并使完整后端 CI 失败 | DEV-01，DEV-05 协作 |

建议主责依据现行开发分工，不表示已分配具体执行人；验证由 DEV-05 与修复角色协作。

<a id="bug-001"></a>
## BUG-001

**重试缺少当前领域权限、数据范围和 Context 复核；P1；OPEN。**

位置：[TaskWriteService.java:101][retry-api]、[TaskWriteService.java:171][permission]、[TaskService.java:326][retry]。

重试 API 只校验 `task:manage`。`TaskService.retry` 检查所有者、FAILED 状态及 `TASK_OUTCOME_UNKNOWN` 后直接排队，没有复用新建任务的 `requireSubmission` 或等效的当前领域权限、Context 有效期及数据范围判断。领取路径同样不在领取前调用该提交检查。

触发：先产生失败导出任务，保留 `task:manage` 但收回 `capture:export`；或使原 Context 过期、缩小数据范围，再重试。当前路径仍可将旧任务置为 QUEUED。发布前已有复核，但不能代替重试时鉴权；本条不表示旧结果必然能发布成功。

依据：[API 开发约定][api] 第 4 节要求任务每次重校范围，任务管理权限不能替代领域许可。

修复方向：按原任务固定输入，在重试事务内重新校验当前业务许可、范围和 Context，适用的幂等回放仍重校权限。

回归要求：撤回领域许可、缩权、Context 过期后重试均应拒绝且保持原状态；有效授权下仍按固定输入重试。

<a id="bug-002"></a>
## BUG-002

**重试绕过每用户等待额度和全局队列限制；P2；OPEN。**

位置：[TaskService.java:326][retry]、[TaskService.java:496][admission]、[AsyncTaskRepository.java:330][requeue-sql]。

新建任务的 `enforceAdmission` 在事务锁内检查每用户最多 5 个 PENDING/QUEUED 任务及全局未终态上限。重试没有执行该检查、没有取得相应入队锁；`REQUEUE_FOR_RETRY` 仅检查任务 ID、FAILED 和未删除。

触发：同一用户已有 5 个等待任务，再重试另一个历史 FAILED 任务，等待数可变为 6。继续重试历史失败任务可增加队列，全局上限也可被绕过。数据库状态触发器只检查状态迁移，不补做配额检查。

依据：[暂定规则][rules] P-003-02，以及现有统一入队限制。

复现证据：抽取分支原始 `REQUEUE_FOR_RETRY` SQL，在 SQLite 的可移植 UPDATE/WHERE 语义下执行，更新 1 行，等待数从 5 变为 6；同时核对 Java 调用链。此复现不是 PostgreSQL 集成测试。

修复方向及回归要求：重试重新占用额度时，复用与创建/领取一致的事务锁及限额判断。验证第 6 个任务被拒、全局满时返回 429 和 Retry-After，以及并发重试不突破额度。

<a id="bug-003"></a>
## BUG-003

**可选本地心跳绕过设备命令 TTL，不能证明 Worker 存活；P1；OPEN。**

位置：[TaskReaper.java:58][heartbeat-job]、[TaskService.java:474][heartbeat]、[AsyncTaskRepository.java:443][heartbeat-sql]。

触发前提：启用 `xx.task.worker-ids` 对应的同进程心跳；默认空名单不触发本问题。

单条 `renew` 将 DEVICE_COMMAND 租约限制到 `startedAt + 120s`，定时心跳却走独立批量路径，统一写入 `now + lease`。SQL 没有命令 TTL 条件，也没有逐个当前 Lease 的 attempt/generation 或工作线程存活确认，仅按 owner 名单续租。

API 进程仍活着但 Worker 卡住时，该定时器可持续给旧任务续租；设备命令也可在超过 120 秒后仍持有有效租约。普通执行时限不能替代设备命令 TTL。

依据：[暂定规则][rules] P-018-02 的 120 秒命令 TTL / UNKNOWN 处理，以及[内部任务协议][task]要求的持有者心跳、代次及失租保护。

修复方向：由真实工作单元携带当前 Lease 调用统一续租；保留批量路径时，必须同时验证实际存活、代次及各类任务 TTL。

回归要求：设备命令在 TTL 到达后不能再续租或发布成功；停止实际 Worker 后不能被 API 定时器无限续租；旧代次不能更新新租约。

<a id="bug-004"></a>
## BUG-004

**实际启动租约为两分钟，覆盖协议的 60 秒；P2；OPEN。**

位置：[application.yml:128][lease-config]、[TaskIntegrationTestSupport.java:70][test-override]。

`TaskProperties` 类默认值已改为 60 秒，但启动配置仍为 `xx.task.lease: 2m`。Spring 绑定后领取和续租实际读取 120 秒，违反[内部任务协议][task]的“每 10 秒心跳，延长至当前时刻 + 60 秒”。已独立解析 YAML 确认配置为 `2m`。

现有集成测试显式传入 `--xx.task.lease=60s`，旁边注释注明启动配置待改，因此相关测试通过不能证明正常启动配置正确。

修复方向及回归要求：统一实际启动配置和协议；增加使用正常配置的绑定验证，确保领取/续租为 60 秒，避免通过测试参数掩盖偏差。

<a id="bug-005"></a>
## BUG-005

**请求取消后，Worker 无法确认 CANCELLED；P2；OPEN。**

位置：[TaskService.java:238][finish]、[AsyncTaskRepository.java:301][finish-sql]。

运行中取消先设置 `cancel_requested_at`。但所有终态回报共用 FINISH SQL，无论 outcome 是 SUCCESS 还是 CANCELLED，均要求 `cancel_requested_at IS NULL`。因此 Worker 响应取消并调用 `finish(..., CANCELLED, ...)` 时更新 0 行，服务层返回 409 TASK_CONFLICT，只能等待租约过期由 reaper 收尾，运行额度不能及时释放。

依据：[API 开发约定][api]第 6 节和[内部任务协议][task]的 Worker 确认取消语义。取消后的 SUCCESS 应被拒绝，取消确认应被允许。

复现证据：抽取原始 FINISH SQL 做可移植语义复现，存在取消时间时更新 0 行并保留 RUNNING；仅去除取消时间的对照记录更新 1 行。此复现不是 Java/PostgreSQL 集成测试。

修复方向及回归要求：区分成功发布与取消确认条件；在验证 owner/generation/租约后允许取消确认，仍拒绝取消后的 SUCCESS 和旧代次回报。

<a id="bug-006"></a>
## BUG-006

**任务取消、重试及拒绝路径缺少正式操作审计；P2；OPEN。**

位置：[TaskWriteService.java:85][cancel-api]、[TaskWriteService.java:101][retry-api]、[TaskService.java:296][cancel]。

已追踪控制器、写服务、任务服务、仓储及通用 Filter：取消/重试成功只写 `analysis.task_event` 和幂等记录，没有接入 `AuditService` / `FailureAudit` 或等效的 `audit.audit_event` 写入；权限拒绝、状态冲突等失败路径同样缺少正式审计。数据库没有将任务流水自动转为正式审计的触发器。

`task_event` 没有操作主体快照、操作结果和拒绝原因等完整审计字段，不能替代正式操作审计。

依据：[暂定规则][rules] P-021-04 对资源写入及拒绝/失败的服务端审计要求。

修复方向及回归要求：接入统一审计，验证成功与业务状态一致、拒绝/事务回滚后失败审计仍存在；幂等回放不重复产生业务成功事件，审计不得含敏感明文。

<a id="bug-007"></a>
## BUG-007

**配额集成测试缺少完整收尾，遗留 RUNNING 任务占用全局 I/O 槽位；P1；OPEN。**

登记日期：2026-11-03。暴露于 [PR #5](https://github.com/WangLiu-Hades/xx-platform/pull/5) 的 `d11265c17f728acc3a90aee79677c6d87a7030c9`；按用户提供的独立复审结论，这是 PR #3 遗留的测试隔离问题，由 #5 的后端 CI 暴露。

### 现象与影响

用户提供的审查结果：后端 CI 两次均为 **784 项通过、1 项报错**。`TaskQuotaIntegrationTests` 留下 RUNNING 任务占满 I/O 槽位，后续导出权限测试领取任务时得到空集合，随后按第 0 项取值触发越界异常。独立真库复现后，清理队列再运行同一测试可以通过。完整测试结果受前序用例状态和执行顺序影响，不能用单独运行该导出测试通过代替全量 CI 通过。

本次已另行核对远程：下列两次后端检查均为 failure，对应前端检查为 success。上述通过数量和真库清理前后对照结论来自用户提供的报告，本次未重复执行真库复现。

- [后端 CI：41602607380](https://github.com/WangLiu-Hades/xx-platform/actions/runs/41602607380/job/125804677113)
- [后端 CI：41602600064](https://github.com/WangLiu-Hades/xx-platform/actions/runs/41602600064/job/125804653532)
- [对应前端 CI：41602607397](https://github.com/WangLiu-Hades/xx-platform/actions/runs/41602607397)
- [对应前端 CI：41602599965](https://github.com/WangLiu-Hades/xx-platform/actions/runs/41602599965)

### 代码定位与复现步骤

- [TaskQuotaIntegrationTests.java][pr5-quota]：`clearQueue` 是 BeforeEach 清理；`workerOnlyClaimsSupportedTypesWithinLimit` 等用例会实际领取任务，需要在用例或测试类退出时释放其遗留的非终态任务。类注释声明结束前收成终态，但类中没有 AfterEach/AfterAll 保证收尾。
- [TaskIntegrationTestSupport.java][pr5-support]：共享真实数据库和全局任务队列，需统一保障测试清理；仅清理当前类下一条测试开始前的状态，不能保证跨类退出后无残留。
- [ApprovalExportIntegrationTests.java:1010][pr5-export]：`publishedExportFileRequiresPermitAndCurrentSourcePermission` 调用 `claimNext(...EXPORT..., 1)`，紧接着使用 `claimed.get(0)`；槽位被占时领取为空并越界。该类自己的导出任务清理不能清除其他类遗留的配额任务。

复现顺序：使用相同测试真库先执行任务配额用例并保留其 RUNNING 任务，再执行上述导出权限测试；检查全局非终态任务和 I/O 槽位占用。然后只清理测试所属队列和依赖，再运行同一导出测试，对照结果。完整命令和原始真库证据以用户提供的 `PR5合并复核报告.md` 为准；报告原位置是 `C:\Users\Administrator\Documents\pr5-merge-review-20261103\PR5合并复核报告.md`，本次运行主机无法读取该路径，未把它作为仓库有效附件或已独立读取的证据。

### 修复与关闭要求

建议由 DEV-01 修复共享测试底座和配额测试收尾，DEV-05 验证跨类顺序及 CI。可在可靠的 AfterEach/AfterAll/finally 中清理测试所属任务与依赖，或按测试类使用隔离数据库；失败退出也必须释放状态，清理范围只能覆盖测试环境。不得通过跳过用例、放宽生产配额、删除权限断言或强行访问空集合来声称修复。

关闭前需记录修复提交，并完成以下实际验证：配额用例结束后无其所属 RUNNING/等待任务残留；原顺序和反向顺序均可运行；原失败导出测试通过；使用真实 PostgreSQL/PostGIS 跑完整后端 CI，失败和错误均为 0，未新增跳过。登记及合并本身不关闭本条目。

## PR #3 发现时的验证范围

2026-11-03 独立执行：前端 8 个测试文件、296 项通过；类型检查和生产构建通过；API 生成一致性检查通过（198 操作、623 Schema）；目录/链接/映射检查 14 项通过。Git 文本合并无冲突，核心规则和契约没有改写。

分支旧报告声称 425 项后端测试通过；本次核对其 193 个文件哈希，统一 LF 后内容一致。本次环境未配置 Java/Maven/PostgreSQL，没有独立重跑该后端全量、真实数据库迁移或浏览器到后端全链路。SQL 定向复现只支持相应可移植条件的结论。上述通过结果不关闭这 6 个条目。

## 后续修复登记

| 编号 | 修复提交 | 实际回归与结果 | 状态更新日期 |
| --- | --- | --- | --- |
| BUG-001 | 待修复 | 待验证 | — |
| BUG-002 | 待修复 | 待验证 | — |
| BUG-003 | 待修复 | 待验证 | — |
| BUG-004 | 待修复 | 待验证 | — |
| BUG-005 | 待修复 | 待验证 | — |
| BUG-006 | 待修复 | 待验证 | — |
| BUG-007 | 待修复 | 待完整真库 CI 验证 | 2026-11-03 |

返回[本目录入口](README.md)或[报告入口](../README.md)。更早的主分支问题见[PR #1 审查报告](../pr1-integration/AUDIT.md)，其中 F08 后续已另行处理，状态应以相应后续证据为准。

[rules]: https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/docs/project-governance/暂定规则明细.md
[api]: https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/contracts/api/api-conventions.md
[task]: https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/docs/project-governance/INTERNAL_TASK_PROTOCOL.md
[retry-api]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/java/com/xxplatform/api/service/task/TaskWriteService.java#L101
[permission]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/java/com/xxplatform/api/service/task/TaskWriteService.java#L171
[retry]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/java/com/xxplatform/api/service/task/TaskService.java#L326
[admission]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/java/com/xxplatform/api/service/task/TaskService.java#L496
[requeue-sql]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/java/com/xxplatform/api/infra/AsyncTaskRepository.java#L330
[heartbeat-job]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/java/com/xxplatform/api/service/task/TaskReaper.java#L58
[heartbeat]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/java/com/xxplatform/api/service/task/TaskService.java#L474
[heartbeat-sql]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/java/com/xxplatform/api/infra/AsyncTaskRepository.java#L443
[lease-config]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/resources/application.yml#L128
[test-override]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/test/java/com/xxplatform/api/service/task/TaskIntegrationTestSupport.java#L70
[finish]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/java/com/xxplatform/api/service/task/TaskService.java#L238
[finish-sql]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/java/com/xxplatform/api/infra/AsyncTaskRepository.java#L301
[cancel-api]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/java/com/xxplatform/api/service/task/TaskWriteService.java#L85
[cancel]: https://github.com/WangLiu-Hades/xx-platform/blob/7e5f131704c784fc61657d8c2f5999bc83e44d44/apps/api/src/main/java/com/xxplatform/api/service/task/TaskService.java#L296
[pr5-quota]: https://github.com/WangLiu-Hades/xx-platform/blob/d11265c17f728acc3a90aee79677c6d87a7030c9/apps/api/src/test/java/com/xxplatform/api/service/task/TaskQuotaIntegrationTests.java#L79
[pr5-support]: https://github.com/WangLiu-Hades/xx-platform/blob/d11265c17f728acc3a90aee79677c6d87a7030c9/apps/api/src/test/java/com/xxplatform/api/service/task/TaskIntegrationTestSupport.java#L93
[pr5-export]: https://github.com/WangLiu-Hades/xx-platform/blob/d11265c17f728acc3a90aee79677c6d87a7030c9/apps/api/src/test/java/com/xxplatform/api/service/approval/ApprovalExportIntegrationTests.java#L1010
