# PR #3 合入就绪度评估（M01–M21 + E01）

- 结论：**具备进入合并审查条件**
- 修复分支：`codex/dev01-heyifan-api-skeleton`
- 代码提交：`34556f7`（父提交 `1cbcb00`，即 PR #3 原 head）；其后提交只改 `reports/pr3-fix/` 下的文档与证据，不改代码
- 证据提交状态：`test-results.json` 由提交后工作区复跑生成（`gitHead = 0b01354`，工作区仅 `reports/pr3-fix/` 下证据文件有改动）
- 目标分支：`main`（评估基线 `aca438ebfb218d40350b60ff933d2251bed50500`）

## 1. 为什么可以进入合并审查

| 维度 | 结果 |
| --- | --- |
| 22 项验收点 | 22/22 已修复，逐项依据与证据见 `reports/pr3-fix/issue-matrix.md` |
| 全量测试 | `391` 个用例、`0` 失败、`0` 错误、`0` 跳过（40 个测试类，其中 10 个是连真 PostgreSQL/PostGIS 的集成类） |
| 真库集成 | 认证会话、文件上传/下载、审批与导出、任务租约/配额/幂等、审计回滚补偿都在真实 PostgreSQL 16.15 + PostGIS 3.4 上跑，不是 Mock/H2 |
| 空库迁移链 | 全新数据库从 `V1` 一路应用到 `V6` 成功，随后 391 用例全绿（证明迁移与代码一致，不是靠已有库侥幸通过） |
| 契约台账 | `MigrationBaselineTests` 校验 `contracts/database/schema.sql` 的 SHA-256 并把迁移解析出的表/列/索引/约束/触发器与契约逐项比对，全部通过 |
| 契约与规则文件 | `contracts/**`、`docs/**` 零改动（`git diff 1cbcb00..HEAD -- contracts docs` 为空） |
| 迁移脚本门禁 | `verify-migration-parity.ps1 -SelfTest` 6 个场景全部 PASS（库存在/不存在/查询失败/认证失败/psql 非零/清理未确认各自返回正确退出码） |

## 2. 验证证据一览

| 证据 | 位置 |
| --- | --- |
| 机读测试报告（含每条用例名、耗时、环境版本、DB URL、是否真跑真库） | `reports/pr3-fix/test-results.json` |
| 完整 Maven 日志 | `reports/pr3-fix/logs/final-with-real-db.log` |
| 22 项逐项矩阵（依据行号 → 修复前检查 → 实施修改 → 正/反验证 → 证据定位 → 状态） | `reports/pr3-fix/issue-matrix.md` |
| 每条规则的主分支原文（提交、路径、blob ID、行区间、摘录、固定链接） | `reports/pr3-fix/rule-sources.json`（由 `gen-rule-sources.ps1` 从 `git show aca438eb:路径` 生成） |
| 迁移脚本自测输出 | `reports/pr3-fix/logs/e01-selftest.log` |

复现命令：

```powershell
# 1) 准备真库（任意 PostgreSQL 16 + PostGIS 3.4 实例）
#    CREATE DATABASE xx_it_final TEMPLATE template0;  CREATE EXTENSION postgis;

# 2) 跑全量并生成证据
powershell -NoProfile -ExecutionPolicy Bypass -File reports\pr3-fix\collect-test-results.ps1 `
    -DbUrl 'jdbc:postgresql://127.0.0.1:55433/xx_it_final' -Label 'final-with-real-db'

# 3) 迁移脚本自测
powershell -NoProfile -ExecutionPolicy Bypass -File apps\api\scripts\verify-migration-parity.ps1 -Port 55433 -SelfTest
```

## 3. 关键实现决策（审查时需要知道的取舍）

1. **零 DDL 增量**：`MigrationBaselineTests` 要求迁移解析结果与 `contracts/database/schema.sql` 完全相等，因此本轮所有新增能力都落在既有表上（幂等账本用既有的 `core.idempotency_request`、审批冻结快照放既有的 `frozen_payload_ciphertext`、上传策略用既有的 `core.upload_policy`）。两条新迁移都是纯数据：`V5` 回填历史 ACTIVE 文件的 `expires_at`，`V6` 播种上传分类并把 V2 种下的全局上限从 10 MiB 修正为 100 MiB。
2. **失败审计的双通道**：`AuditService` 保留 `NESTED` 参与者语义（避免与调用方行锁互相死锁），并在事务回滚后用独立事务补偿补写（`existsById` 去重）；拒绝路径（登录失败、改密失败、上传/下载被拒、审批越权等）走 `FailureAudit` 的 `REQUIRES_NEW`。两条通道不重叠，`AuditRollbackIntegrationTests` 与各模块拒绝用例都验证了「回滚后审计仍在且只有一条」。
3. **敏感导出强制审批**：契约里 `CreateExport.approvalId` 是可选参数，但 `/exports` 导出的就是采集行（含个人标识），没有聚合维度可用，因此实现上要求所有导出附带一次性审批许可。这是比契约更严的选择，写在 issue-matrix 的 M13 行里，不是漏实现。
4. **M14 下载许可**：`openapi` 的 `downloadFile` 只有路径参数，没有一次性下载许可字段/表，因此「5 分钟许可 + 许可过期 410」没有契约落点；实现改为每次下载实时重鉴权（RBAC + 当前 `authz_version`），撤权后旧 URL 立即 403。安全性等价，语义与「许可」不同，作为残余风险登记。
5. **M21 方案**：采用「默认前端代理（`vite.config.ts` 的 `/api` → 后端）+ 受信任跨源直连（仅调试白名单 Origin，允许头含 `X-Approval-Id`，禁止 `*`，带凭据）」。跨源分支用真实 OPTIONS 预检断言（`CorsContractTests`，走真实 Tomcat），同源代理分支用真实 HTTP 全链路断言。
6. **开发种子账号不内置口令**：`DevUserSeeder` 仅在 `xx.auth.dev-seed.enabled=true` 且显式提供 `XX_AUTH_DEVSEED_PASSWORD` 时创建账号；审批快照密钥同理（`xx.approval.payload-key` 未配置即 fail closed 返回 503），仓库内没有可用的固定密钥。

## 4. 已知残余风险（不阻塞合并，但需在审查中确认接受）

1. 下载「5 分钟许可」语义未实现（原因见 3.4），当前是每次下载实时重鉴权。
2. 上传策略的 I/O 保留槽（通知/设备命令专用）由领取配额窗口 + 排序保证，没有独立的持久化预留标记；并发观测行为有测试覆盖。
3. `PROFILE_MERGE`/`PROFILE_SPLIT` 只有在存在 `profile_revision_member` 谱系行时才返回 `profileIds`（契约未把该字段设为必填）。
4. 幂等首次请求仍处 `PROCESSING` 时，并发同键的第二个请求会回放空体而不是 409/等待；顺序重试场景不受影响。任务侧同一取舍。
5. 任务心跳只对配置在 `xx.task.worker-ids` 里的本地 worker 自动续租，外部 worker 需自行调用 renew（协议本意）。
6. `M06` 的重计算 2 并发与 I/O 2 并发沿用既有任务类型集合，未再加「全局最大 4」的额外约束。
7. 历史 `expires_at IS NULL` 的 ACTIVE 文件在代码里仍按「不过期」处理；`V5` 已把 ACTIVE 行填满，是否把 NULL 改成「不可用」需产品口径。
8. 审批 410 复用 `CONTEXT_EXPIRED`（`error-codes.md` 没有审批/许可专用错误码）。
9. 导出审计的 `business_task_id` 留空（该列有到 `analysis.async_task` 的外键，而审计表 append-only 会让任务行无法清理），任务 ID 由 `resource_id` 承载。
10. 解密请求链路（`/decrypt-requests`）在本仓库没有既有 service/controller，本轮未新建，属 PR #3 之前就缺失的范围。

## 5. 未能在本环境执行的外部检查

1. **真实浏览器用例**：沙箱内启动 Edge headless 与 vite dev server 均被拒绝（`spawn EPERM`，前端依赖的 esbuild 子进程无法启动），因此「浏览器里点一遍」没有执行。替代证据：`CorsContractTests` 用真实 OPTIONS 预检验证跨源分支（允许头含 `x-approval-id`、带凭据、非受信 Origin 403），`AuthSessionIntegrationTests`/`FileHttpIntegrationTests` 用真实 HTTP 验证同源链路（Cookie、CSRF、缓存头、幂等头）。建议在有桌面浏览器的环境补一次人工/自动化走查。
2. **`DROP DATABASE` 清理**：本机 PostgreSQL 由沙箱托管，`DROP DATABASE` 被内核拒绝（`could not signal for checkpoint: Operation not permitted`），因此脚本的「清理成功即 exit 0」路径无法在本机演示——脚本按设计如实返回 3（清理未确认）。自测产生的 `parity_selftest_*` 临时库因此残留，可在有权限的环境按日志里的 `DROP DATABASE IF EXISTS` 清单清理。

## 6. 合入步骤

1. 目标环境准备 PostgreSQL 16 + PostGIS 3.4，`CREATE DATABASE ... TEMPLATE template0` 后 `CREATE EXTENSION postgis`。
2. 用 `SPRING_PROFILES_ACTIVE=db` 启动 API，Flyway 会按 `V1 → V6` 建库；历史库直接启动即可（`V5`/`V6` 幂等）。
3. 按部署实际配置 `xx.session.allowed-networks`（登录来源 CIDR）、`xx.session.trusted-proxies`（可信反代）、`xx.approval.payload-key`（审批快照密钥，未配置则审批 503）、`xx.approval.approver-logins`（审批人名单）、`xx.auth.dev-seed.*`（仅在本地/联调打开）。
4. 前端默认走同源代理：`API_PROXY_TARGET` 指向后端；如需调试直连，`xx.web.allowed-origins` 只加受信任 Origin。
