> 2026-10-18当前版本：[PD-2026-10-19.v1](../../docs/project-governance/暂定方案与修改记录.md)。当前分工01/02后端、03前端、04数据、05测试；下文旧数量/编号/NO及签认门禁保留作历史追踪，不覆盖本轮暂定设计，也不证明当前实现。旧治理生成脚本不得覆盖本轮policy/暂定文档。

# DB-DESIGN-1 → DB-DESIGN-2迁移方案

schema.sql是空库基线，不能作为43表旧库的直接升级脚本。本次只修改设计文件和隔离验证环境；没有读取或迁移生产业务库。

## 从哪种库升级：先识别来源，再选路径（2026-10-26 补充）

`apps/api/src/main/resources/db/migration/` 现在有四条版本化迁移：V1 基线（163 表的历史快照，已冻结）、V2 客户端配置种子、
V3 审批聚合与 PD-2026-10-18.v1 策略文案、V4 在线查询窗口参数（纯数据修正）。**同一套命令不能套到所有库上**，先按下表判断来源：

| 库的来源 | 判定方法 | 该走的路径 |
| --- | --- | --- |
| 空库 | `flyway_schema_history` 不存在，`pg_tables` 里也没有 `auth.app_user` | 由应用启动让 Flyway 跑 V1→V4；或者对空库直接 `schema.sql`。两条路**不要叠加**在同一库上 |
| 已有 Flyway V1/V2 的库 | `flyway_schema_history` 里有 1 条或 1、2 条 `success = true` 记录 | 继续由 Flyway 向前补 V3/V4；先 `flyway validate` 确认既有 checksum 没被改过 |
| 按完整 `schema.sql` 建的库（无迁移历史） | 165 表齐全，但没有 `flyway_schema_history` | 不要直接启动 Flyway（V1 会撞已存在对象）：先盘点结构与字典版本，再决定是 `baseline` 到当前版本，还是在隔离库重放并比对指纹 |
| 手工执行过 `contracts/database/migrations/PD_20261018_approval.sql` 的库 | `to_regclass('operation.action_approval')` 非空，但历史里没有 V3 | 结构与 V3 目标重叠：**禁止**直接跑 V3，也不要改 V3 的 DDL 绕开；先盘点结构/历史/数据，另写受审查的迁移 |
| 其他历史库（DB-DESIGN-1 或更早） | 表数、列、字典与 R08 对不上 | 按本文件后文第 1–5 节做递增迁移与回填，不套用空库脚本 |

三条硬规则：

1. 新迁移不写 `IF NOT EXISTS`、不用 `DO` 块兜底、不靠 `baseline`/`repair` 掩盖漂移、不删 `flyway_schema_history` 让检查变绿——
   这些都会让各环境结构悄悄分叉（V1 里的 `CREATE SCHEMA IF NOT EXISTS` 是契约快照自带，不是新迁移的写法）。
2. 空库的 `schema.sql` 与审批增量脚本不可对同一库先后叠加；判定依据是 `to_regclass('operation.action_approval')` 加迁移历史，
   不是"看起来能跑通"。
3. **"重建该库"只适用于明确可丢弃的隔离测试库。** 共享环境、有数据的环境必须走"盘点 → 方案 → 评审 → 执行"，
   重建既不是通用建议，也不能当作让校验变绿的手段；迁移失败一律向前修复（见第 5 节）。

## 顺序

1. 固定旧DDL/API/字典版本与数据hash，备份数据库、文件、密钥；测试恢复后生成Flyway/Liquibase递增迁移，owner与app/Worker账号隔离。
2. 扩展新增目录/版本/证据/结果/计划/尝试表和可空新列；保持旧服务可读。对真实已有数据统计NULL/冲突/外键悬空；不能直接用合成测试值填必填字段。
3. 回填与核对，再NOT VALID添加适用外键/CHECK并VALIDATE CONSTRAINT；大索引用CREATE INDEX CONCURRENTLY且在事务块外独立迁移。空库脚本不用CONCURRENTLY。
4. 应用双读比较或影子导入（对外副作用保持单路）；验证页/详情/报表/权限/去重口径和行数hash。应用切换后保留兼容视图/旧列至确认回滚窗口。
5. 验证无旧服务/Worker引用再删兼容层；数据迁移失败以向前修复处理，不能假定镜像回退会恢复已改变的字段/ID/密钥。

## 重点转换

| 旧结构 | 新结构 | 回填/风险 |
| --- | --- | --- |
| capture_event的source/源键、target_id、lon/lat | source_event、primary_target_id、geometry(Point,4326)、采集时归属 | 源去重键签认；合法坐标才转Point；历史归属缺失从同期记录/备份核对，禁止使用当前设备场所假装历史 |
| 混合接入 | source_event与ingestion_item的CAPTURE/DEVICE_STATUS/DEVICE_COUNTER | 真实采集计数没有来源不能以入库数填；质量错误保留源设备键 |
| target单表HMAC | target + target_lookup的规范化/密钥版本 | 必须拿到旧密钥与版本，否则标pending待恢复，禁止通过新hash伪装旧标识 |
| analysis_result每任务JSON | 逐目标analysis_result + typed结果/证据/事件/尝试 | 旧JSON有明确字段才拆；缺算法/选区/证据的数据标历史不可重放，不捏造置信度/匹配率 |
| dataset/member复合ID与TARGET-only | UUID item、element_type、dataset_version、有效区间 | 保存旧键→新UUID映射；旧版本没有记录只能定义迁移基准版本，不声称恢复不存在的历史 |
| profile_link/revision、库成员 | 稳定UUID、person/vehicle子表、证据/谱系/冲突 | 保留目标/档案UUID与来源；缺人工操作者/证据标迁移质量项，不能替成当前管理员 |
| model_version | trend_model、definition、parameter_version/value、execution/result | 对照当前RESIDENCE参数Schema；未知阈值不启用模型，不自动生成厂商算法字段 |
| notification_task混用执行状态 | notification_plan ACTIVE等 + notification_execution TaskStatus | 能判定ONCE/DAILY与发送尝试才转换；历史已发送不能重新调度；未知送达状态按UNKNOWN待对账 |
| commands统一JSON结果 | command_job、逐设备command/attempt、DESIRED/REPORTED | 未确认回执不得回填参数已生效；原幂等键保留，成功设备不重发 |
| 旧报表文件/动态预览 | report_definition +历史预览行/口径/尝试 +输出文件 | 原文件是历史证据；不能从当前设备名称伪造原报表预览。缺字段可标历史预览不可用，留下载引用 |
| token_hash/expires_at | sid_hash、idle_expires_at、absolute_expires_at | 不确定旧凭据类型时撤销旧会话并重新登录；不能把JWT复制为SID哈希 |
| role_site无范围种类 | MANAGEMENT/DATA +单独user_scope/library_scope | 原配置按有证据的范围转换；P-002未签认不合并/扩大权限 |
| metric_snapshot旧NULL复合维度 | metric_definition、canonical metric_scope、版本化bucket | PG14 NULL唯一行为必须显式规范scope；不混入源采集/入库/去重口径；缺asOf不能伪造统计时间 |
| audit记录只有用户引用 | actor当时名字快照、请求/任务/trace | 有历史日志才回填快照；缺历史显示信息不从现名声称原名。审计原记录保留不可改写，迁移可建带溯源的转换副本 |

## 必须通过的门禁

- 全部ID映射、源事件唯一、四payload完整、无新孤儿FK；正常/无权/错误字段三组API比对。
- 历史数据集成员、分析证据/尝试、报表口径/文件、通知已执行边界和逐设备结果一致。
- 角色删除409、旧SID失效、权限缩小后旧Context/结果/文件不可越权；P-002关闭前保持保守有效范围。
- 生产副作用适配器在迁移期冻结/单路；通知/命令重复提交及UNKNOWN对账演练。
- 大表索引构建、锁等待、查询P95、磁盘/WAL、备份恢复和向前修复实测；配置保留期后再设计清理。
