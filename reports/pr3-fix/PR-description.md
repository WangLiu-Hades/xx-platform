> 历史描述，已由2026-11-02的[主分支规则修复与复核](final-main-rule-verification.md)取代；不得使用本页旧统计或分支内规则来源作最终合并判断。

> **本轮修复（M01–M21 + E01）**：代码提交 `34556f7`（此后分支上的提交只修改 `reports/pr3-fix/` 下的文档、脚本与证据，不改 `apps/`、`contracts/`、`docs/`）；目标 `main@aca438eb`；结论：**具备进入合并审查条件**。全量真库测试 391 用例 0 失败 0 错误 0 跳过，逐项依据与证据见 [`reports/pr3-fix/issue-matrix.md`](issue-matrix.md)，就绪度评估见 [`merge-readiness.md`](merge-readiness.md)。

## 任务与角色

- 实际作者 / DEV角色：DEV-01（后端 API 骨架）共同作者；本轮修复由 DEV-01 与协助 agent 完成，审查人待指定
- 需求编号 / operationId / 任务编号：PR #3 验收项 M01–M21 + E01（无新增 operationId，全部落在既有契约定义的端点上）
- 变更行为与影响目录：`apps/api`（认证会话、任务底座、审批与敏感导出、文件上传下载、审计、迁移脚本与迁移一致性脚本）；`contracts/**` 与 `docs/**` 零改动

本轮解决的问题（每条都有失败复现路径与修复后断言，见 `reports/pr3-fix/issue-matrix.md`）：

| 编号 | 问题 | 本轮处理 |
| --- | --- | --- |
| M01 | 后台轮询/探针/预加载会延长会话空闲期 | 只在主动业务请求或显式 renew 时滑动；路径白名单 + `X-Background-Request` + `Sec-Purpose` 判定；60 秒写节流不影响严格到期判断；续期被 `LEAST(?, absolute_expires_at)` 封顶 |
| M02 | 登录来源不校验、IP/UA 变化不重登 | 登录来源按部署 CIDR 拒绝并审计；解析可信代理链得到真实地址；UA 或 IP 变化 → 401 + `auth.session_rejected` 审计 |
| M03 | 登录不轮换 CSRF、凭据响应可缓存 | 登录/改密轮换并吊销旧 CSRF；凭据响应 `Cache-Control: no-store`；Origin 双向校验 |
| M04 | 初始密码无门禁、无本人改密 | 初始密码账号只放行改密/退出/续期/CSRF，其余 403；`POST /me/password` 校验旧口令、撤销其他会话、轮换 SID/CSRF、幂等 |
| M05 | 发布结果不复核授权与结果有效性 | `TaskScopeGuard`：发布前复核任务类型授权、Context 过期/缩权、结果产物归属与 ACTIVE；失败不写 `result_asset_id` |
| M06 | 租约 2 分钟、无心跳/时限/全局队列、并发可超发 | 租约 60s、心跳 10s、命令 TTL 120s、全局 1000、排队 5/15 分钟、执行 30 分钟/2 小时；窗口函数配额 + owner 级 advisory lock |
| M07 | 失租副作用任务被自动重发 | 副作用类型失租 → `FAILED` + `TASK_OUTCOME_UNKNOWN` 不再派发；UNKNOWN 拒绝 retry；命令 TTL 封顶 |
| M08 | 部分任务类型缺少 resultReference | 15 类任务逐一按契约返回；领域行缺失/归属不符拒绝发布 |
| M09 | 审批人不校验部署名单与当前范围 | 部署审批人身份 + 动作权限 + 当前数据范围 + 非自批 + 人数（1/2）+ 同人重复票不计数 + 设备组服务端解析 |
| M10 | 提案只查键名白名单 | 按 operation 严格校验 body/path/If-Match，服务端解析并冻结目标、版本、授权/策略版本；目标变化后拒绝执行 |
| M11 | reviewItems 截断/脱敏不全 | 递归脱敏（含嵌套 JSON 字符串），超 4096 字符/10000 项改为拆分或拒绝，不截断后继续批准 |
| M12 | 审批快照密钥可能内置仓库 | 仅从部署配置注入，未配置则 fail closed；AES-GCM + 随机 nonce + 认证加密；仓库无固定密钥 |
| M13 | 审批许可可重复消费 | 一次性原子消费 + 同事务 + 摘要绑定；同键重放原结果，异键复用已消费许可 409 |
| M14 | 旧 Context/发布/下载不复核当前范围 | 导出按当前 scope 重算；任务提交/发布复核；下载每次重鉴权（含 `authz_version`） |
| M15 | 幂等键只在任务创建生效 | 上传、创建审批、审批决定、改密、取消/重试统一落 `core.idempotency_request`（同键同内容重放、异内容 409、作用域按 actor+operation） |
| M16 | 并发同键创建任务 500 | `INSERT ... ON CONFLICT DO NOTHING` + 回读赢家，冲突事务不再回读 |
| M17 | 上传策略与 client-config 不一致 | `V6` 播种 `core.upload_policy`（图片 10 MiB、导入 100 MiB/10 万行），修正 V2 全局上限；`UploadPolicyProvider` 成为服务端与下发的唯一来源，`enabled` 生效 |
| M18 | 只查魔数不做真实内容校验 | 图片 ImageIO 解码、JSON Jackson 解析、CSV RFC4180 状态机、XLSX 结构解析；失败 → `QUARANTINED`，只有 ACTIVE 可读可下载 |
| M19 | 文件无 12 自然月到期、详情缺 expiresAt | 按 Asia/Shanghai 加 12 自然月；详情必带 `expiresAt`（可显式 null）；`V5` 幂等回填历史 ACTIVE |
| M20 | 拒绝/失败路径无正式审计 | 失败审计事务回滚后独立补偿补写；拒绝路径 `REQUIRES_NEW`；覆盖登录/退出/改密/上传/下载/导出/审批及拒绝 |
| M21 | 前端代理与跨源直连策略未定 | 默认同源代理（`vite` `/api`）+ 受信任跨源直连（白名单 Origin、允许头含 `X-Approval-Id`、禁止 `*`、带凭据） |
| E01 | 迁移一致性脚本把失败当成功 | 区分库存在/不存在/查询失败/认证失败/psql 非零/清理未确认，失败返回非零；`-SelfTest` 6 场景自检 |

## 契约与交接

- API / 数据库 / Worker变化及接收角色：
  - 数据库：只新增**纯数据**迁移 `V5__backfill_file_asset_retention.sql`（回填历史 ACTIVE 文件到期时间）与 `V6__upload_policy_alignment.sql`（播种上传分类并修正 V2 全局上限）。表/列/索引/约束/触发器零变化，`MigrationBaselineTests`（契约 SHA-256 + 结构逐项比对）通过。接收角色：运维/DEV-04（上线时由 Flyway 自动应用，历史库幂等）。
  - API：路径与请求/响应结构未变；行为收紧的部分需要在联调时知晓——初始密码账号除放行清单外一律 403；登录来源必须在 `xx.session.allowed-networks` 内；UA/IP 变化会 401 要求重登；所有导出都需要 `X-Approval-Id`；上传策略以 `GET /client-config` 的 `uploadPolicies` 为准（此前恒为空）。接收角色：前端、联调、DEV-04。
  - Worker：领取需支持 `supportedTypes` 与 `limit`，并每 10 秒调用 renew；失租副作用任务会得到 `TASK_OUTCOME_UNKNOWN` 且不会被自动重发。接收角色：DEV-04。
  - 新增配置项（均有安全默认，未配置时审批 fail closed）：`xx.session.allowed-networks`、`xx.session.trusted-proxies`、`xx.approval.payload-key`、`xx.approval.approver-logins`、`xx.web.allowed-origins`。
- P清单未决项、限制或待签认规则：
  1. 「敏感导出强制审批」比契约更严（`CreateExport.approvalId` 在契约里可选），需要产品/规则侧确认接受（issue-matrix M13 行已说明理由）。
  2. 下载「5 分钟许可」语义无契约落点，当前以每次下载实时重鉴权替代，需要规则侧确认（M14 行）。
  3. 审批 410 复用 `CONTEXT_EXPIRED`，`error-codes.md` 是否需要新增审批/许可专用错误码待签认。
  4. 历史 `expires_at IS NULL` 的 ACTIVE 文件当前按「不过期」处理，是否需要改为不可用待产品口径。
  5. 解密请求链路（`/decrypt-requests`）在本仓库没有既有实现，本轮未新建，属 PR #3 之前即缺失范围。

## 验证

- 实际执行的命令、环境与结果：
  - 环境：Windows 10.0.26100、JDK 17.0.12、Maven 3.9.16、Node 24.21.0、PostgreSQL 16.15 + PostGIS 3.4.1。
  - `powershell -NoProfile -ExecutionPolicy Bypass -File reports\pr3-fix\collect-test-results.ps1 -DbUrl 'jdbc:postgresql://127.0.0.1:55433/xx_it_final' -Label 'final-with-real-db'`
    → `Tests run: 391, Failures: 0, Errors: 0, Skipped: 0`，退出码 0（40 个测试类；10 个集成类连真库实跑，`skippedDueToDb=0`）。机读结果见 `reports/pr3-fix/test-results.json`。
  - 空库迁移链：全新数据库启动时 Flyway 按 `V1 → V6` 全部成功（`Successfully applied 6 migrations ... now at version v6`），随后 391 用例全绿。
  - 迁移一致性：`verify-migration-parity.ps1 -Port 55433 -SelfTest` → `SELF-TEST PASSED: all 6 situations reported with the required exit code and message`，退出码 0。
  - 契约：`MigrationBaselineTests` 5/5 通过；`git diff 1cbcb00..HEAD -- contracts docs` 为空。
  - 材料：`reports/pr3-fix/{issue-matrix.md, rule-sources.json, test-results.json, merge-readiness.md}`。
- 受影响浏览器流程 / 真实服务联调结果：
  - 跨源分支：`CorsContractTests` 用真实 OPTIONS 预检断言 Allow-Headers 含 `x-approval-id`、Allow-Credentials 为 true、非受信 Origin 403 且无 ACAO、实际响应回显 Origin 而非 `*`。
  - 同源代理分支：`AuthSessionIntegrationTests`、`FileHttpIntegrationTests` 通过真实 HTTP 验证 Cookie/CSRF/缓存头/幂等头与完整的下载、过期、越权拒绝链路。
  - **未执行**：真实浏览器走查。本沙箱内 Edge headless 与 `vite dev` 均因 `spawn EPERM` 无法启动（脚本见 `reports/pr3-fix/browser-e2e.cjs`），需在有桌面浏览器的环境补一次。
  - 未执行：`DROP DATABASE` 清理路径。本机 PostgreSQL 拒绝 checkpoint 信号，脚本按设计返回 3（清理未确认），残留 `parity_selftest_*` 库清单已写入日志。
