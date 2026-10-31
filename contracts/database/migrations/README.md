# 暂定增量迁移

PD_20261018_approval.sql用于已核验DB-DESIGN-2的隔离升级，新增审批表/一次性许可；本轮不连接服务器。空库直接使用schema.sql，不能再叠加本脚本。已有解密许可会主动中止，需另做历史安全迁移。

字段/流程见docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md。部署前分别授予真实API/Worker/维护账号权限，应用不能使用owner。暂定方案不自动启用未实现能力。

本脚本与 apps/api/src/main/resources/db/migration/V3__approval_aggregate_and_policy_v1.sql 内容重叠：V3 是它的 Flyway 版本化超集（额外含 12 条 core.feature_config 策略文案对齐与头注释），供空库与版本化迁移链使用；本目录脚本只用于已核验 DB-DESIGN-2 的存量库手工隔离升级。两者不可对同一库先后执行——跑过本脚本的库再执行 V3 会撞已存在对象，须先确认 `to_regclass('operation.action_approval')` 为空。

> 判定为空之前的"重建该库"**只适用于明确可丢弃的隔离测试库**。共享或有数据的环境必须先盘点结构、迁移历史和数据，再另写受审查的迁移（见 `../migration-plan.md` 的「从哪种库升级」一节）；不能靠重建、`baseline`/`repair` 或改 V3 的 DDL 让校验变绿。

V4__policy_parameters_20261019.sql 是**纯数据修正**，与本目录脚本无关：只把 core.config_item 里 client-config 的 maxOnlineWindowDays 从 90 改成 31（PD-2026-10-19.v1 的在线查询窗口），并把 value.version 从 1 提到 2、config_version 加一；不新增表/列/索引，重复执行无副作用，空库照样只用 schema.sql + 迁移链。
