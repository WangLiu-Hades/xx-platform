> 2026-10-18修订：当前暂定开发规则及分工以[PD-2026-10-18.v1](../../docs/project-governance/暂定方案与修改记录.md)为准。下文旧pending/NO/签认门禁和人员编号保留作历史追踪；本轮不声称后端或生产验收通过。

# 数据库设计与使用说明

本目录是DB-DESIGN-2的PostgreSQL/PostGIS设计源文件，供后端、数据、数据库及测试工程师使用。当前基线为163表、1460列、363外键；设计和隔离验证不代表已有生产数据库或真实业务服务。

## 文件与目录

| 文件 | 功能 |
| --- | --- |
| [schema.sql](<schema.sql>) | 空库建表、约束、索引、注释及基础种子；包含事务和版本检查，不是旧库升级脚本 |
| [data-dictionary.md](<data-dictionary.md>) | 逐表逐字段类型、可空、默认值、主外键及业务说明；按表名查字段，P-016影响profile_revision等结构 |
| [api-storage-map.md](<api-storage-map.md>) | API/模块与存储投影、复用及pending对应关系 |
| [index-design.md](<index-design.md>) | 75个显式查询索引、访问路径、分区及容量边界；不等于全部物理索引数 |
| [query-examples.sql](<query-examples.sql>) | 参数化检索、GIS、任务领取/发布、集合和汇总等PREPARE示例；应用需绑定经校验的参数 |
| [migration-plan.md](<migration-plan.md>) | 实际存在旧库时的备份、增量迁移、回填、影子验证、切换与回滚要求 |
| [pending-design.md](<pending-design.md>) | API P清单的数据库投影，指出未定业务规则，不能单独删pending来开启功能 |
| [design-changelog.md](<design-changelog.md>) | 旧43表方案到DB-DESIGN-2的结构差异与原因 |
| [design-validation.md](<design-validation.md>) | 2026-10-07隔离PostgreSQL验证方法、环境、63正反例及结果范围 |

## 怎么使用

先读映射、pending与迁移方案，再查SQL和字典。当前要求PostgreSQL14+与兼容PostGIS构建；PG14不能搭配PostGIS3.0.x。以下命令只适用于已由你准备的**空白测试库**，从本目录执行；`psql`需另行安装，连接信息由测试环境提供：

```powershell
psql -X -v ON_ERROR_STOP=1 -d platform_design_test -f schema.sql
psql -X -v ON_ERROR_STOP=1 -d platform_design_test -f query-examples.sql
```

第二条在连接中注册PREPARE语句，不自动执行其中的业务变更；后续EXECUTE须使用测试数据和正确权限。应用负责UUID、UTC时间、权限范围、租约和版本条件，不能把客户端范围直接当授权。旧库要按迁移方案另写递增迁移，不能重复执行空库DDL升级已有库。

## 维护与检查

变更后同步字典、[ER图](<../../docs/system-design/diagrams/er-diagram.mmd>)、索引、API存储映射和迁移记录，在隔离库核对约束及正反例。既有测试日期不改成本次执行。P-016等决策按[统一待确认事项](<../../docs/project-governance/PENDING_REGISTER.md>)关闭，不能由字段默认值推断算法或权限。返回[系统设计入口](<../../docs/system-design/README.md>)。
