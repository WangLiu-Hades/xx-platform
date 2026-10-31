-- 部署级前端配置（非机密）。
--
-- 为什么落在数据库而不是代码/yml：
--   契约 ClientConfig 的注释写得很清楚——"数值由部署配置提供，不在契约中捏造默认值"。
--   这些数字（轮询间隔、上传上限、Context TTL、查询预算）会随部署规模和安全口径变化，
--   写进 Java 常量或 application.yml 就变成"改一个阈值要重新发版"。
--   放进 core.config_item 后，运维执行
--       UPDATE core.config_item
--          SET value = <新对象>, config_version = config_version + 1
--        WHERE config_key = 'client-config';
--   即可生效（不需要改代码、不需要重启；下一次请求就按新值返回）。
--
-- 为什么是 INSERT ... ON CONFLICT DO NOTHING 而不是 UPDATE：
--   迁移只在首次升级时执行一次；DO NOTHING 保证**不覆盖运维已经改过的值**。
--
-- 注意：contextTtlSeconds / maxOnlineQuerySeconds / maxOnlineWindowDays 三项属于
--   P-009（查询取消、限流、Context TTL 与导出限值）尚未签认的范围，
--   mapCoordinateSystem / institutionTimeZone 属于 P-010（坐标与部署口径）。
--   下面给出的是**可运行的部署默认值**，不是已签认的业务口径，
--   不得据此对外承诺配额或保留期。签认后改库即可，不需要改这条迁移。
INSERT INTO core.config_item (
    config_key,
    schema_code,
    schema_version,
    config_version,
    value,
    public_readable,
    enabled
) VALUES (
    'client-config',
    'ClientConfig',
    '1',
    1,
    '{
       "version": "1",
       "mapCoordinateSystem": "EPSG:4326",
       "taskPollIntervalSeconds": 3,
       "maxUploadBytes": 10485760,
       "institutionTimeZone": "Asia/Shanghai",
       "contextTtlSeconds": 1800,
       "maxOnlineQuerySeconds": 5,
       "maxOnlineWindowDays": 90
     }'::jsonb,
    true,
    true
)
ON CONFLICT (config_key) DO NOTHING;
