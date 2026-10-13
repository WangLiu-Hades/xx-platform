# ADR 003 主数据库与DB-DESIGN-2

状态：数据库选型与本次结构采用，2026-10-07；业务规则pending与生产容量尚未签认。

Context：旧43表骨架不能覆盖当前API的版本、逐目标结果、档案证据、周期通知、逐设备回执和历史报表；用户指定PG14+及PostGIS3.0+，要求覆盖功能并减少负担。

Decision：PostgreSQL14+唯一业务库，按auth/core/capture/analysis/operation/audit划域。PG14使用至少3.1.4或更高兼容PostGIS构建，3.0.x不支持14；仅用3.0已有空间功能。保留公共采集+4payload，补齐typed结果、版本有效区间、参数/证据/尝试和通知计划/执行。受控文件存图片/报表/大清单，共享增量metric，不为每条采集更新多个指标。PENDING能力按当前OpenAPI关闭。

Alternatives：保留43表并让JSON承担全部结果，查询/字段/历史/权限不可控；每任务/版本复制完整明细，存储和写入重复；立即引入多库/MQ，规模证据不足。新方案关系多、迁移与约束成本上升，但把复杂度限制在需要的版本/证据/执行边界。

Consequences：163表、1460列、363FK与75显式查询索引；在线按域/窗口访问，不全表关联。真实PG14.24/PostGIS3.6.1和2万合成事实通过门禁，不能据此承诺生产容量最低。schema.sql只空库；旧库升级、分区全局身份、并发索引、影子验证和回滚见migration-plan。

参考：[PostGIS官方PG14支持说明](https://postgis.net/2021/09/PostGIS-3.1.4-3.0.4-Released/)、[PG14分区唯一约束](https://www.postgresql.org/docs/14/ddl-partitioning.html)。
