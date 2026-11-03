-- 历史 ACTIVE 文件补 12 个自然月保留期（P-003-03 / M19）。
--
-- 背景：上传流程在刚落地时只写了 status，没有写 expires_at；这些行在详情里"没有到期时间"，
-- 前端无法区分"永不过期"与"还没算"。本迁移只补 expires_at 仍为 NULL 的 ACTIVE 行，
-- 已有值不覆盖（可能来自更严格的机构留存要求）。
--
-- 时区：规则要求按 Asia/Shanghai 的日历月计算，月末不存在则取该月最后一日。
-- PostgreSQL 的 timestamptz + interval '12 months' 在同一时区内正是这个语义
-- （2024-02-29 → 2025-02-28），因此先转换成上海本地时间再加 12 个月，再转回 timestamptz。
--
-- 幂等：WHERE expires_at IS NULL 保证重复执行不会把已有保留期往后推。
UPDATE core.file_asset
   SET expires_at = ((created_at AT TIME ZONE 'Asia/Shanghai') + INTERVAL '12 months')
                    AT TIME ZONE 'Asia/Shanghai'
 WHERE status = 'ACTIVE'
   AND expires_at IS NULL;
