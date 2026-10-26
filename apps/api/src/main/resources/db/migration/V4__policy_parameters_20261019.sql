-- V4__policy_parameters_20261019.sql
-- 目的：把 PD-2026-10-19.v1 暂定政策里与"查询时间窗口"有关的部署默认值落到已有的库里。
--
-- 背景（评审 A04）：V2__seed_deployment_client_config.sql 播的 maxOnlineWindowDays 是 90，
-- 而暂定政策（docs/project-governance/provisional-policy.json 的
-- settings.resources.ordinaryQueryMaxDays）是 31 天。空库按 V2 建出来、以及所有已执行过
-- V2 的库，下发的 client-config 都还是 90，与政策不符。
--
-- 为什么新增 V4 而不是改 V2：V2 已经在各环境执行过，改它的内容会让 Flyway 报
-- checksum mismatch、整库起不来（见 docs/project-governance/修改与检查记录.md 的规矩 2）。
-- 历史只能靠"再追加一条增量"纠正。
--
-- 为什么这个数字和"留存 12 个月"不冲突：留存期讲的是数据放多久，查询窗口讲的是
-- 普通在线查询最多回看多久（31 天）。两者是不同的口径，不能互相替代——
-- 年度报表走的是汇总与冻结快照，不是放开在线查询窗口。
--
-- 幂等性说明：不写 IF NOT EXISTS / 不写 DO 块兜底。Flyway 保证本条在同一个库上只执行一次；
-- 迁移必须"要么整体成功、要么报错停下"。这里故意**不带** ON CONFLICT 之类的宽容写法：
-- 行不存在（例如运维手工删过 client-config）时 UPDATE 影响 0 行，属部署配置缺失，
-- 由 GET /client-config 的 500 SYSTEM_INTERNAL 暴露出来，比迁移里悄悄补一行更安全。
--
-- 只改两个字段：
--   value.maxOnlineWindowDays  90 -> 31（政策值）
--   value.version              若仍是 '1' 则升到 '2'，让前端知道本地缓存要刷；
--                              运维已经自己升过版本号时保持原样，不覆盖。
-- 其余 11 个字段（坐标、时区、轮询、上传、Context TTL、查询预算…）一律不动。

BEGIN;

UPDATE core.config_item
   SET value = jsonb_set(
                   jsonb_set(value, '{maxOnlineWindowDays}', '31'::jsonb, true),
                   '{version}',
                   to_jsonb(CASE
                                WHEN value ->> 'version' = '1' THEN '2'
                                ELSE value ->> 'version'
                            END),
                   true),
       config_version = config_version + 1
 WHERE config_key = 'client-config'
   AND value ->> 'maxOnlineWindowDays' IS DISTINCT FROM '31';

COMMIT;
