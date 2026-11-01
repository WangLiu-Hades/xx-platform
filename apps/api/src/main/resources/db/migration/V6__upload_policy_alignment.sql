-- V9__upload_policy_alignment.sql
--
-- M17：上传策略与 GET /client-config 下发的规则不一致。
--
-- 两个根因：
--   1) core.upload_policy 在基线里只有结构、没有任何种子行（见 V1 的 CREATE TABLE）。
--      ClientConfigService.uploadPolicies() 读出来是空集合，前端拿不到任何分类限制；
--      而上传端当时用的是 UploadPolicyCatalog 里的 Java 常量——两端不是同一数据源，
--      运维改库不会影响上传校验，改代码又不会影响下发，等于两套规则各自漂移。
--   2) V2 播种的全局上限 value.maxUploadBytes = 10485760（10 MiB）。按契约
--      api-conventions.md:53「旧 maxUploadBytes 仅全局上限，实际限制取全局与分类
--      maxBytes 的较小值」，10 MiB 的全局值会把普通导入压到 10 MiB，与 P-003-02
--      「图片 10 MiB、普通导入 100 MiB/10 万行」直接矛盾（11 MiB 的合法导入会被 413）。
--
-- 为什么新增这条增量而不是改 V2：V2 已经在各环境执行过，改内容会让 Flyway 报
-- checksum mismatch、整库起不来（与 V4 同一先例，见 V4 文件头说明）。
--
-- 为什么只写数据、不动结构：MigrationBaselineTests 会把迁移里的表/列/索引/具名约束/
-- 触发器集合与 contracts/database/schema.sql 逐项比对；本条只有 INSERT/UPDATE，
-- 不会引入任何契约外的 schema 形状。
--
-- 幂等性：
--   * INSERT 用 ON CONFLICT (category) DO NOTHING —— 不覆盖运维已经调过的分类策略。
--   * UPDATE 只在「值仍是 V2 旧种子 10485760」时生效 —— 运维有意设成别的值（更大或更小）
--     时不被本迁移改写；此时 413 的边界就是运维选的全局值，属于部署口径而非实现缺陷。
--   * 版本号与 V4 同样的处理：仍是 '2'（V4 升上来的值）则升 '3'，否则保持原样。
--
-- 数值口径（P-003-02）：
--   * 图片：10 MiB = 10485760 字节，仅 JPEG/PNG；
--   * 普通导入：100 MiB = 104857600 字节，CSV/XLSX/JSON；
--   * 10 万行上限不落库：core.upload_policy 没有行数列，行数在内容校验里按分类执行
--     （UploadPolicyCatalog.IMPORT_MAX_ROWS）。
BEGIN;

INSERT INTO core.upload_policy (category, max_bytes, allowed_mime_types, allowed_extensions, enabled)
VALUES
    ('FACE_SEARCH', 10485760,
     ARRAY['image/jpeg', 'image/png'], ARRAY['.jpg', '.jpeg', '.png'], true),
    ('PROFILE_PORTRAIT', 10485760,
     ARRAY['image/jpeg', 'image/png'], ARRAY['.jpg', '.jpeg', '.png'], true),
    ('DATASET_IMPORT', 104857600,
     ARRAY['text/csv', 'application/json',
           'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
     ARRAY['.csv', '.json', '.xlsx'], true),
    ('DEVICE_IMPORT', 104857600,
     ARRAY['text/csv', 'application/json',
           'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
     ARRAY['.csv', '.json', '.xlsx'], true),
    ('PERSON_LIBRARY_IMPORT', 104857600,
     ARRAY['text/csv', 'application/json',
           'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
     ARRAY['.csv', '.json', '.xlsx'], true),
    ('RAW_INGESTION', 104857600,
     ARRAY['text/csv', 'application/json',
           'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
     ARRAY['.csv', '.json', '.xlsx'], true)
ON CONFLICT (category) DO NOTHING;

UPDATE core.config_item
   SET value = jsonb_set(
                   jsonb_set(value, '{maxUploadBytes}', '104857600'::jsonb, true),
                   '{version}',
                   to_jsonb(CASE
                                WHEN value ->> 'version' = '2' THEN '3'
                                ELSE value ->> 'version'
                            END),
                   true),
       config_version = config_version + 1
 WHERE config_key = 'client-config'
   AND value ->> 'maxUploadBytes' = '10485760';

COMMIT;
