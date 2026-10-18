> 2026-10-18修订：当前暂定开发规则及分工以[PD-2026-10-18.v1](../../docs/project-governance/暂定方案与修改记录.md)为准。下文旧pending/NO/签认门禁和人员编号保留作历史追踪；本轮不声称后端或生产验收通过。

# 数据字典（DB-DESIGN-2）

与 schema.sql、关系图由同一表目录生成。字段类型、可空性、默认值、主键、外键和检查条件以下列明细为准。

## 通用约定

UUID由应用生成；时间戳使用 timestamptz，连接与持久化按 UTC，日/月报表按 Asia/Shanghai 计算边界。SQL snake_case 与 API camelCase 的投影详见 api-storage-map.md。可空表示未知或不适用，不能直接以0填充。JSONB只用于带 Schema 版本的过滤器、参数与快照；图片、报表和大清单存受控文件。

数据库业务规则未定时保留结构并关闭相关功能；不能由字段默认值推定权限、审批、算法阈值或厂商协议。

## auth.app_user — 用户账号

模块：M14。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `user_code` | varchar(200) | 否 | 无 | user_code |
| `login_name` | varchar(200) | 否 | 无 | login_name |
| `display_name` | varchar(200) | 否 | 无 | display_name |
| `password_hash` | varchar(255) | 否 | 无 | password_hash |
| `status` | varchar(200) | 否 | 'ACTIVE' | status |
| `secret_clearance` | varchar(200) | 否 | 'STANDARD' | secret_clearance |
| `authz_version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `last_login_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `password_changed_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |

唯一约束：`(user_code)`；`(login_name)`。

检查条件：
- `status IN ('ACTIVE','DISABLED')`
- `NOT deleted OR status='DISABLED'`
- `version >= 0`
- `authz_version >= 0`

## auth.role — 角色及范围标志

模块：M14。主键：`id`。

待确认：P-002：多角色合并、继承与范围组合须产品/安全签认；保存配置，不自行开放权限

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `role_code` | varchar(200) | 否 | 无 | role_code |
| `name` | varchar(200) | 否 | 无 | name |
| `description` | varchar(1000) | 是 | 无 | description |
| `management_all_sites` | boolean | 否 | false | management_all_sites |
| `data_all_sites` | boolean | 否 | false | data_all_sites |
| `own_only` | boolean | 否 | true | own_only |
| `scope_policy_version` | varchar(200) | 否 | 'PENDING-P002' | scope_policy_version |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |

唯一约束：`(role_code)`。

检查条件：
- `version >= 0`

## auth.permission — 与OpenAPI共用的权限目录

模块：M01。主键：`code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `code` | varchar(200) | 否 | 无 | code；PK |
| `name` | varchar(200) | 否 | 无 | name |
| `module_code` | varchar(200) | 否 | 无 | module_code |
| `description` | varchar(1000) | 否 | 无 | description |
| `risk_level` | varchar(200) | 否 | 'NORMAL' | risk_level |
| `assignable` | boolean | 否 | true | assignable |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `risk_level IN ('NORMAL','SENSITIVE','HIGH')`

## auth.menu — 菜单和应用入口目录

模块：M02。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `parent_id` | uuid | 是 | 无 | parent_id；FK auth.menu(id) |
| `code` | varchar(200) | 否 | 无 | code |
| `label` | varchar(200) | 否 | 无 | label |
| `route` | varchar(240) | 否 | 无 | route |
| `sort_order` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `required_permission_code` | varchar(200) | 是 | 无 | required_permission_code；FK auth.permission(code) |
| `enabled` | boolean | 否 | true | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |

唯一约束：`(code)`。

检查条件：
- `version >= 0`
- `sort_order >= 0`

## auth.user_role — 用户角色

模块：M14。主键：`user_id,role_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `user_id` | uuid | 否 | 无 | user_id；PK；FK auth.app_user(id) |
| `role_id` | uuid | 否 | 无 | role_id；PK；FK auth.role(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## auth.role_permission — 角色接口/按钮权限

模块：M14。主键：`role_id,permission_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `role_id` | uuid | 否 | 无 | role_id；PK；FK auth.role(id) |
| `permission_code` | varchar(200) | 否 | 无 | permission_code；PK；FK auth.permission(code) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## auth.role_menu — 角色菜单

模块：M14。主键：`role_id,menu_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `role_id` | uuid | 否 | 无 | role_id；PK；FK auth.role(id) |
| `menu_id` | uuid | 否 | 无 | menu_id；PK；FK auth.menu(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## auth.role_site — 角色管理/采集场所范围

模块：M14。主键：`role_id,site_id,scope_kind`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `role_id` | uuid | 否 | 无 | role_id；PK；FK auth.role(id) |
| `site_id` | uuid | 否 | 无 | site_id；PK；FK core.site(id) |
| `scope_kind` | varchar(200) | 否 | 无 | scope_kind；PK |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `scope_kind IN ('MANAGEMENT','DATA')`

## auth.role_data_type — 角色数据类型范围

模块：M14。主键：`role_id,data_type`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `role_id` | uuid | 否 | 无 | role_id；PK；FK auth.role(id) |
| `data_type` | varchar(200) | 否 | 无 | data_type；PK |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `data_type IN ('FENCE','MAC','PLATE','FACE')`

## auth.user_scope — 用户请求范围，保存输入而非猜测合并策略

模块：M14。主键：`user_id`。

待确认：P-002：与角色范围组合规则未签认；不得把客户端scopeVersion当服务端授权版本

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `user_id` | uuid | 否 | 无 | user_id；PK；FK auth.app_user(id) |
| `management_all_sites` | boolean | 否 | false | management_all_sites |
| `data_all_sites` | boolean | 否 | false | data_all_sites |
| `own_only` | boolean | 否 | true | own_only |
| `scope_version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `policy_version` | varchar(200) | 否 | 'PENDING-P002' | policy_version |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `scope_version >= 0`

## auth.user_scope_site — 用户场所范围条目

模块：M14。主键：`user_id,site_id,scope_kind`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `user_id` | uuid | 否 | 无 | user_id；PK；FK auth.user_scope(user_id) |
| `site_id` | uuid | 否 | 无 | site_id；PK；FK core.site(id) |
| `scope_kind` | varchar(200) | 否 | 无 | scope_kind；PK |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `scope_kind IN ('MANAGEMENT','DATA')`

## auth.user_scope_data_type — 用户数据类型范围

模块：M14。主键：`user_id,data_type`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `user_id` | uuid | 否 | 无 | user_id；PK；FK auth.user_scope(user_id) |
| `data_type` | varchar(200) | 否 | 无 | data_type；PK |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `data_type IN ('FENCE','MAC','PLATE','FACE')`

## auth.user_session — SID服务端会话

模块：M01。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `user_id` | uuid | 否 | 无 | user_id；FK auth.app_user(id) |
| `sid_hash` | bytea | 否 | 无 | sid_hash |
| `last_seen_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `idle_expires_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `absolute_expires_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `revoked_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `client_label` | varchar(200) | 是 | 无 | client_label |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(sid_hash)`。

检查条件：
- `idle_expires_at <= absolute_expires_at`
- `created_at < absolute_expires_at`

## auth.csrf_session — 匿名/登录CSRF绑定

模块：M01。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `session_id` | uuid | 是 | 无 | session_id；FK auth.user_session(id) |
| `cookie_hash` | bytea | 否 | 无 | cookie_hash |
| `token_hash` | bytea | 否 | 无 | token_hash |
| `expires_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(cookie_hash)`。

检查条件：
- `created_at < expires_at`

## core.dictionary — 字典版本

模块：M01。主键：`code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `code` | varchar(200) | 否 | 无 | code；PK |
| `name` | varchar(200) | 否 | 无 | name |
| `catalog_version` | varchar(200) | 否 | 无 | catalog_version |
| `enabled` | boolean | 否 | true | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## core.dictionary_item — 字典条目

模块：M01。主键：`dictionary_code,code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `dictionary_code` | varchar(200) | 否 | 无 | dictionary_code；PK；FK core.dictionary(code) |
| `code` | varchar(200) | 否 | 无 | code；PK |
| `label` | varchar(200) | 否 | 无 | label |
| `sort_order` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `enabled` | boolean | 否 | true | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `sort_order >= 0`

## core.dictionary_binding — API字段到字典的绑定

模块：M01。主键：`field_key`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `field_key` | varchar(200) | 否 | 无 | field_key；PK |
| `dictionary_code` | varchar(200) | 否 | 无 | dictionary_code；FK core.dictionary(code) |
| `label` | varchar(200) | 否 | 无 | label |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## core.config_item — 非机密前端配置

模块：M01。主键：`config_key`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `config_key` | varchar(200) | 否 | 无 | config_key；PK |
| `schema_code` | varchar(200) | 否 | 无 | schema_code |
| `schema_version` | varchar(200) | 否 | 无 | schema_version |
| `config_version` | bigint | 否 | 1 | 非负计数或版本；不使用32位整型计数 |
| `value` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `public_readable` | boolean | 否 | false | public_readable |
| `enabled` | boolean | 否 | true | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `jsonb_typeof(value)='object'`
- `jsonb_typeof(value) IN ('object','array')`
- `config_version >= 0`

## core.feature_config — 待确认能力开关

模块：M01。主键：`feature_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `feature_code` | varchar(200) | 否 | 无 | feature_code；PK |
| `enabled` | boolean | 否 | false | enabled |
| `pending_reason` | text | 是 | 无 | pending_reason |
| `policy_version` | varchar(200) | 否 | 无 | policy_version |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `enabled OR pending_reason IS NOT NULL`

## core.upload_policy — 上传白名单

模块：M01。主键：`category`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `category` | varchar(200) | 否 | 无 | category；PK |
| `max_bytes` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `allowed_mime_types` | text[] | 否 | 无 | allowed_mime_types |
| `allowed_extensions` | text[] | 否 | 无 | allowed_extensions |
| `enabled` | boolean | 否 | false | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `max_bytes > 0`
- `cardinality(allowed_mime_types)>0`
- `cardinality(allowed_extensions)>0`
- `max_bytes >= 0`

## core.import_template — 版本化导入模板

模块：M01。主键：`template_code,template_version`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `template_code` | varchar(200) | 否 | 无 | template_code；PK |
| `template_version` | varchar(200) | 否 | 无 | template_version；PK |
| `formats` | text[] | 否 | 无 | formats |
| `transaction_policy` | varchar(200) | 否 | 'ALL_OR_NOTHING' | transaction_policy |
| `encoding` | varchar(200) | 否 | 'UTF-8' | encoding |
| `description` | text | 否 | 无 | description |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `transaction_policy='ALL_OR_NOTHING'`
- `encoding='UTF-8'`

## core.import_column — 导入列定义

模块：M01。主键：`template_code,template_version,column_name`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `template_code` | varchar(200) | 否 | 无 | template_code；PK；FK core.import_template(template_code,template_version) |
| `template_version` | varchar(200) | 否 | 无 | template_version；PK；FK core.import_template(template_code,template_version) |
| `column_name` | varchar(200) | 否 | 无 | column_name；PK |
| `label` | varchar(200) | 否 | 无 | label |
| `sort_order` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `required` | boolean | 否 | false | required |
| `value_type` | varchar(200) | 否 | 无 | value_type |
| `description` | text | 否 | 无 | description |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `value_type IN ('STRING','UUID','INTEGER','BOOLEAN','DATE_TIME')`
- `sort_order >= 0`

## core.region — 行政区与地图边界

模块：M07。主键：`region_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `region_code` | varchar(200) | 否 | 无 | region_code；PK |
| `parent_region_code` | varchar(200) | 是 | 无 | parent_region_code；FK core.region(region_code) |
| `name` | varchar(200) | 否 | 无 | name |
| `boundary` | geometry(MultiPolygon,4326) | 是 | 无 | WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标 |
| `boundary_version` | varchar(200) | 否 | 无 | boundary_version |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `boundary IS NULL OR (NOT ST_IsEmpty(boundary) AND ST_IsValid(boundary) AND ST_CoveredBy(boundary,ST_MakeEnvelope(-180,-90,180,90,4326)))`

## core.site — 场所

模块：M12。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `site_code` | varchar(200) | 否 | 无 | site_code |
| `name` | varchar(200) | 否 | 无 | name |
| `region_code` | varchar(200) | 否 | 无 | region_code；FK core.region(region_code) |
| `address` | varchar(500) | 是 | 无 | address |
| `location` | geometry(Point,4326) | 是 | 无 | WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标 |
| `enabled` | boolean | 否 | true | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |

唯一约束：`(site_code)`。

检查条件：
- `location IS NULL OR (NOT ST_IsEmpty(location) AND ST_X(location) BETWEEN -180 AND 180 AND ST_Y(location) BETWEEN -90 AND 90)`
- `version >= 0`

## core.device — 设备及最新状态

模块：M12。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `serial_number` | varchar(200) | 否 | 无 | serial_number |
| `name` | varchar(200) | 否 | 无 | name |
| `site_id` | uuid | 否 | 无 | site_id；FK core.site(id) |
| `category_code` | varchar(200) | 否 | 无 | category_code |
| `power_class_code` | varchar(200) | 是 | 无 | power_class_code |
| `manufacturer` | varchar(200) | 是 | 无 | manufacturer |
| `model` | varchar(200) | 是 | 无 | model |
| `protocol_version` | varchar(200) | 是 | 无 | protocol_version |
| `location` | geometry(Point,4326) | 是 | 无 | WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标 |
| `installed_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `status` | varchar(200) | 否 | 'UNKNOWN' | status |
| `status_since` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `last_seen_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `parameter_version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `reset_count` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |

唯一约束：`(serial_number)`。

检查条件：
- `status IN ('ONLINE','OFFLINE','UNKNOWN','FAULT')`
- `location IS NULL OR (NOT ST_IsEmpty(location) AND ST_X(location) BETWEEN -180 AND 180 AND ST_Y(location) BETWEEN -90 AND 90)`
- `reset_count >= 0`
- `version >= 0`
- `parameter_version >= 0`

## core.device_group — 设备分组

模块：M12。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `site_id` | uuid | 否 | 无 | site_id；FK core.site(id) |
| `name` | varchar(200) | 否 | 无 | name |
| `description` | varchar(1000) | 是 | 无 | description |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |

唯一约束：`(site_id,name)`。

检查条件：
- `version >= 0`

## core.device_group_member — 设备分组成员

模块：M12。主键：`group_id,device_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `group_id` | uuid | 否 | 无 | group_id；PK；FK core.device_group(id) |
| `device_id` | uuid | 否 | 无 | device_id；PK；FK core.device(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## core.device_revision — 设备元信息历史，采集归属不随设备修改漂移

模块：M12。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `device_id` | uuid | 否 | 无 | device_id；FK core.device(id) |
| `revision_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `site_id` | uuid | 否 | 无 | site_id；FK core.site(id) |
| `metadata_snapshot` | jsonb | 否 | 无 | DeviceInput + 当前运维元信息契约快照；脱敏、版本化 |
| `effective_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `actor_user_id` | uuid | 是 | 无 | actor_user_id；FK auth.app_user(id) |
| `reason` | varchar(1000) | 否 | 无 | reason |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(device_id,revision_no)`。

检查条件：
- `revision_no >= 0`
- `jsonb_typeof(metadata_snapshot) IN ('object','array')`

## core.device_status_event — 在线/离线/故障状态变化

模块：M12。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `device_id` | uuid | 否 | 无 | device_id；FK core.device(id) |
| `previous_status` | varchar(200) | 否 | 无 | previous_status |
| `status` | varchar(200) | 否 | 无 | status |
| `occurred_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `source` | varchar(200) | 否 | 无 | source |
| `reason` | varchar(1000) | 是 | 无 | reason |
| `policy_version` | varchar(200) | 是 | 无 | policy_version |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `previous_status IN ('ONLINE','OFFLINE','UNKNOWN','FAULT')`
- `status IN ('ONLINE','OFFLINE','UNKNOWN','FAULT')`

## core.device_parameter_definition — 厂商参数描述，不虚构协议

模块：M12。主键：`id`。

待确认：P-018/P-019：参数代码、范围、厂商协议、回执与高风险确认签认后启用

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `manufacturer` | varchar(200) | 否 | 无 | manufacturer |
| `model` | varchar(200) | 否 | 无 | model |
| `protocol_version` | varchar(200) | 否 | 无 | protocol_version |
| `parameter_code` | varchar(200) | 否 | 无 | parameter_code |
| `label` | varchar(200) | 否 | 无 | label |
| `value_type` | varchar(200) | 否 | 无 | value_type |
| `unit` | varchar(200) | 是 | 无 | unit |
| `minimum` | numeric | 是 | 无 | minimum |
| `maximum` | numeric | 是 | 无 | maximum |
| `allowed_values` | jsonb | 是 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `read_only` | boolean | 否 | true | read_only |
| `enabled` | boolean | 否 | false | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(manufacturer,model,protocol_version,parameter_code)`；`(id,value_type)`。

检查条件：
- `value_type IN ('TEXT','INTEGER','NUMBER','BOOLEAN')`
- `minimum IS NULL OR maximum IS NULL OR minimum<=maximum`
- `jsonb_typeof(allowed_values) IN ('object','array')`
- `allowed_values IS NULL OR jsonb_typeof(allowed_values)='array'`

## core.device_parameter_value — 期望/设备上报参数，未回执不覆盖实际值

模块：M12。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `device_id` | uuid | 否 | 无 | device_id；FK core.device(id) |
| `definition_id` | uuid | 否 | 无 | definition_id；FK core.device_parameter_definition(id,value_type) |
| `value_type` | varchar(200) | 否 | 无 | value_type；FK core.device_parameter_definition(id,value_type) |
| `parameter_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `value_kind` | varchar(200) | 否 | 无 | value_kind |
| `text_value` | text | 是 | 无 | text_value |
| `integer_value` | bigint | 是 | 无 | integer_value |
| `number_value` | numeric | 是 | 无 | number_value |
| `boolean_value` | boolean | 是 | 无 | boolean_value |
| `command_id` | uuid | 是 | 无 | command_id；FK operation.device_command(id) |
| `observed_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(device_id,definition_id,value_kind,parameter_version)`。

检查条件：
- `value_kind IN ('DESIRED','REPORTED')`
- `num_nonnulls(text_value,integer_value,number_value,boolean_value)=1 AND ((value_type='TEXT' AND text_value IS NOT NULL) OR (value_type IN ('INTEGER','DURATION_SECONDS') AND integer_value IS NOT NULL) OR (value_type IN ('NUMBER','DISTANCE_METERS') AND number_value IS NOT NULL) OR (value_type='BOOLEAN' AND boolean_value IS NOT NULL))`
- `parameter_version >= 0`

## core.file_asset — 受控文件元数据；内容不入库

模块：M01。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `storage_key` | varchar(500) | 否 | 无 | storage_key |
| `file_name` | varchar(255) | 否 | 无 | file_name |
| `category` | varchar(200) | 否 | 无 | category |
| `mime_type` | varchar(200) | 否 | 无 | mime_type |
| `byte_size` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `sha256` | char(64) | 否 | 无 | sha256 |
| `owner_user_id` | uuid | 是 | 无 | owner_user_id；FK auth.app_user(id) |
| `security_class` | varchar(200) | 否 | 'L3' | security_class |
| `status` | varchar(200) | 否 | 'PROCESSING' | status |
| `expires_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `error_code` | varchar(200) | 是 | 无 | error_code |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(storage_key)`。

检查条件：
- `byte_size>=0`
- `sha256 ~ '^[0-9a-f]{64}$'`
- `status IN ('PROCESSING','ACTIVE','QUARANTINED','DELETED')`
- `byte_size >= 0`

## core.idempotency_request — 请求级幂等账本

模块：M01。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `user_id` | uuid | 否 | 无 | user_id；FK auth.app_user(id) |
| `operation_id` | varchar(200) | 否 | 无 | operation_id |
| `idempotency_key` | varchar(200) | 否 | 无 | idempotency_key |
| `request_digest` | char(64) | 否 | 无 | request_digest |
| `status` | varchar(200) | 否 | 'PROCESSING' | status |
| `response_schema_version` | varchar(200) | 是 | 无 | response_schema_version |
| `response_status` | integer | 是 | 无 | response_status |
| `response_snapshot` | jsonb | 是 | 无 | 小型脱敏响应快照，仅ID/版本/状态；不缓存检索明细或明文 |
| `expires_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(user_id,operation_id,idempotency_key)`。

检查条件：
- `status IN ('PROCESSING','SUCCEEDED','FAILED')`
- `response_status IS NULL OR response_status BETWEEN 200 AND 599`
- `jsonb_typeof(response_snapshot) IN ('object','array')`

## core.outbox_event — 事务内业务事件，仅批次/告警等必要事件

模块：M01。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `event_type` | varchar(200) | 否 | 无 | event_type |
| `aggregate_type` | varchar(200) | 否 | 无 | aggregate_type |
| `aggregate_id` | uuid | 否 | 无 | aggregate_id |
| `schema_version` | varchar(200) | 否 | 无 | schema_version |
| `payload` | jsonb | 否 | 无 | 经事件Schema校验的小型引用载荷；大清单用受控manifest文件 |
| `manifest_file_id` | uuid | 是 | 无 | manifest_file_id；FK core.file_asset(id) |
| `status` | varchar(200) | 否 | 'PENDING' | status |
| `next_attempt_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `attempt_count` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `status IN ('PENDING','PROCESSING','SENT','FAILED')`
- `jsonb_typeof(payload) IN ('object','array')`
- `attempt_count >= 0`

## core.consumer_inbox — 事件消费去重

模块：M01。主键：`consumer_code,event_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `consumer_code` | varchar(200) | 否 | 无 | consumer_code；PK |
| `event_id` | uuid | 否 | 无 | event_id；PK；FK core.outbox_event(id) |
| `consumed_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.analysis_context — 跨页查询/目标上下文

模块：M06。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `owner_user_id` | uuid | 否 | 无 | owner_user_id；FK auth.app_user(id) |
| `source_module` | varchar(200) | 否 | 无 | source_module |
| `filter_type` | varchar(200) | 否 | 无 | filter_type |
| `filter_schema_version` | bigint | 否 | 1 | 非负计数或版本；不使用32位整型计数 |
| `filter_snapshot` | jsonb | 否 | 无 | ContextFilter五分支，敏感输入转目标引用或加密保存；不复制采集结果 |
| `spatial_selection_type` | varchar(200) | 是 | 无 | spatial_selection_type |
| `spatial_geometry` | geometry(Geometry,4326) | 是 | 无 | WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标 |
| `permission_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `expires_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `source_module IN ('M04','M05','M06','M07','M08','M09','M10','M11','M12','M13')`
- `filter_type IN ('GENERAL','CAPTURE_SEARCH','DATASET','ANALYSIS_RESULTS','PROFILE')`
- `spatial_selection_type IS NULL OR spatial_selection_type IN ('CAPTURE','DEVICE','AREA')`
- `expires_at > created_at`
- `jsonb_typeof(filter_snapshot) IN ('object','array')`
- `filter_schema_version >= 0`
- `permission_version >= 0`
- `spatial_geometry IS NULL OR (NOT ST_IsEmpty(spatial_geometry) AND ST_IsValid(spatial_geometry) AND ST_CoveredBy(spatial_geometry,ST_MakeEnvelope(-180,-90,180,90,4326)))`
- `spatial_geometry IS NULL OR GeometryType(spatial_geometry) IN ('POLYGON','MULTIPOLYGON')`
- `(spatial_geometry IS NULL)=(spatial_selection_type IS NULL)`

## analysis.context_target — 上下文选择目标

模块：M06。主键：`context_id,target_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `context_id` | uuid | 否 | 无 | context_id；PK；FK analysis.analysis_context(id) |
| `target_id` | uuid | 否 | 无 | target_id；PK；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 否 | 无 | target_type；FK capture.target(id,target_type) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.input_snapshot — 任务输入冻结，复用版本引用或文件manifest

模块：M01。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `owner_user_id` | uuid | 否 | 无 | owner_user_id；FK auth.app_user(id) |
| `source_kind` | varchar(200) | 否 | 无 | source_kind |
| `source_schema_version` | varchar(200) | 否 | 无 | source_schema_version |
| `filter_snapshot` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `snapshot_digest` | char(64) | 是 | 无 | snapshot_digest |
| `snapshot_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `status` | varchar(200) | 否 | 'PREPARING' | status |
| `storage_mode` | varchar(200) | 否 | 无 | storage_mode |
| `manifest_file_id` | uuid | 是 | 无 | manifest_file_id；FK core.file_asset(id) |
| `capture_count` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `target_count` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `permission_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `status IN ('PREPARING','READY','FAILED')`
- `storage_mode IN ('REFERENCES','MANIFEST')`
- `status!='READY' OR (snapshot_at IS NOT NULL AND snapshot_digest IS NOT NULL)`
- `status!='READY' OR storage_mode!='MANIFEST' OR manifest_file_id IS NOT NULL`
- `jsonb_typeof(filter_snapshot) IN ('object','array')`
- `capture_count >= 0`
- `target_count >= 0`
- `permission_version >= 0`

## analysis.input_snapshot_reference — 固定的版本/结果/显式采集输入；大结果不用逐条复制

模块：M01。主键：`snapshot_id,sequence_no`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `snapshot_id` | uuid | 否 | 无 | snapshot_id；PK；FK analysis.input_snapshot(id) |
| `sequence_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `source_kind` | varchar(200) | 否 | 无 | source_kind |
| `capture_id` | uuid | 是 | 无 | capture_id；FK capture.capture_event(id) |
| `target_id` | uuid | 是 | 无 | target_id；FK capture.target(id) |
| `dataset_id` | uuid | 是 | 无 | dataset_id；FK analysis.dataset_version(dataset_id,version_no) |
| `dataset_version` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数；FK analysis.dataset_version(dataset_id,version_no) |
| `analysis_result_id` | uuid | 是 | 无 | analysis_result_id；FK analysis.analysis_result(id) |
| `profile_id` | uuid | 是 | 无 | profile_id；FK analysis.profile(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `num_nonnulls(capture_id,target_id,dataset_id,analysis_result_id,profile_id)=1`
- `(dataset_id IS NULL)=(dataset_version IS NULL)`
- `sequence_no >= 0`
- `dataset_version >= 0`

## analysis.async_task — 统一一次执行任务

模块：M01。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `name` | varchar(200) | 否 | 无 | name |
| `task_type` | varchar(200) | 否 | 无 | task_type |
| `owner_user_id` | uuid | 否 | 无 | owner_user_id；FK auth.app_user(id) |
| `context_id` | uuid | 是 | 无 | context_id；FK analysis.analysis_context(id) |
| `input_snapshot_id` | uuid | 是 | 无 | input_snapshot_id；FK analysis.input_snapshot(id) |
| `status` | varchar(200) | 否 | 'PENDING' | status |
| `progress` | smallint | 否 | 0 | progress |
| `priority` | smallint | 否 | 5 | priority |
| `parameter_schema_version` | varchar(200) | 否 | 无 | parameter_schema_version |
| `parameter_snapshot` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `parameter_hash` | char(64) | 否 | 无 | parameter_hash |
| `idempotency_key` | varchar(200) | 否 | 无 | idempotency_key |
| `algorithm_version` | varchar(200) | 是 | 无 | algorithm_version |
| `resource_type` | varchar(200) | 是 | 无 | resource_type |
| `resource_id` | uuid | 是 | 无 | resource_id |
| `submitted_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `started_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `finished_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `lease_owner` | varchar(200) | 是 | 无 | lease_owner |
| `lease_until` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `lease_generation` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `next_run_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `cancel_requested_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `retry_count` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `current_attempt_no` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `error_code` | varchar(200) | 是 | 无 | error_code |
| `error_message` | varchar(2000) | 是 | 无 | error_message |
| `result_asset_id` | uuid | 是 | 无 | result_asset_id；FK core.file_asset(id) |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(owner_user_id,task_type,idempotency_key)`。

检查条件：
- `task_type IN ('INGESTION','BACKFILL','EXPORT','FACE_SEARCH','ANALYSIS','DATASET_IMPORT','SET_OPERATION','PROFILE_MERGE','PROFILE_SPLIT','MODEL_RUN','DEVICE_COMMAND','REPORT','NOTIFICATION','PERSON_LIBRARY_IMPORT','DEVICE_IMPORT')`
- `status IN ('PENDING','QUEUED','RUNNING','SUCCESS','FAILED','CANCELLED')`
- `progress BETWEEN 0 AND 100`
- `priority BETWEEN 0 AND 10`
- `finished_at IS NULL OR started_at IS NULL OR finished_at>=started_at`
- `jsonb_typeof(parameter_snapshot) IN ('object','array')`
- `lease_generation >= 0`
- `retry_count >= 0`
- `version >= 0`
- `current_attempt_no >= 0`

## analysis.task_attempt — 任务重试历史

模块：M01。主键：`task_id,attempt_no`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `task_id` | uuid | 否 | 无 | task_id；PK；FK analysis.async_task(id) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `started_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `finished_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `worker_id` | varchar(200) | 否 | 无 | worker_id |
| `lease_generation` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `result` | varchar(200) | 是 | 无 | result |
| `error_code` | varchar(200) | 是 | 无 | error_code |
| `error_message` | varchar(2000) | 是 | 无 | error_message |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `attempt_no>=1`
- `result IS NULL OR result IN ('SUCCESS','FAILED','CANCELLED')`
- `attempt_no >= 0`
- `lease_generation >= 0`

## analysis.task_event — 状态转换与取消/恢复记录

模块：M01。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `task_id` | uuid | 否 | 无 | task_id；FK analysis.async_task(id) |
| `attempt_no` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `previous_status` | varchar(200) | 是 | 无 | previous_status |
| `status` | varchar(200) | 否 | 无 | status |
| `occurred_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `reason` | varchar(1000) | 是 | 无 | reason |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `status IN ('PENDING','QUEUED','RUNNING','SUCCESS','FAILED','CANCELLED')`
- `attempt_no >= 0`

## analysis.import_job — 文件导入意图与目标

模块：M01。主键：`task_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `task_id` | uuid | 否 | 无 | task_id；PK；FK analysis.async_task(id) |
| `file_asset_id` | uuid | 否 | 无 | file_asset_id；FK core.file_asset(id) |
| `template_code` | varchar(200) | 否 | 无 | template_code；FK core.import_template(template_code,template_version) |
| `template_version` | varchar(200) | 否 | 无 | template_version；FK core.import_template(template_code,template_version) |
| `duplicate_policy` | varchar(200) | 否 | 无 | duplicate_policy |
| `dataset_id` | uuid | 是 | 无 | dataset_id；FK analysis.dataset(id) |
| `library_id` | uuid | 是 | 无 | library_id；FK analysis.person_library(id) |
| `target_device_id` | uuid | 是 | 无 | target_device_id；FK core.device(id) |
| `imported_count` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `skipped_count` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `duplicate_policy IN ('SKIP','FAIL','UPDATE')`
- `imported_count >= 0`
- `skipped_count >= 0`

## analysis.import_row_result — 成功导入逐行资源ID

模块：M01。主键：`task_id,attempt_no,row_number`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `task_id` | uuid | 否 | 无 | task_id；PK；FK analysis.task_attempt(task_id,attempt_no) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK；FK analysis.task_attempt(task_id,attempt_no) |
| `row_number` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `status` | varchar(200) | 否 | 无 | status |
| `library_id` | uuid | 是 | 无 | library_id；FK analysis.person_library(id) |
| `member_id` | uuid | 是 | 无 | member_id；FK analysis.person_library_member(id) |
| `profile_id` | uuid | 是 | 无 | profile_id；FK analysis.profile(id) |
| `device_id` | uuid | 是 | 无 | device_id；FK core.device(id) |
| `dataset_id` | uuid | 是 | 无 | dataset_id；FK analysis.dataset(id) |
| `item_id` | uuid | 是 | 无 | item_id；FK analysis.dataset_item(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `status IN ('CREATED','UPDATED','SKIPPED')`
- `row_number>=1`
- `attempt_no >= 0`
- `row_number >= 0`

## analysis.task_row_error — 导入逐行错误

模块：M01。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `task_id` | uuid | 否 | 无 | task_id；FK analysis.task_attempt(task_id,attempt_no) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；FK analysis.task_attempt(task_id,attempt_no) |
| `row_number` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `source_record_key` | varchar(200) | 是 | 无 | source_record_key |
| `field_name` | varchar(200) | 是 | 无 | field_name |
| `error_code` | varchar(200) | 否 | 无 | error_code |
| `reason` | varchar(2000) | 否 | 无 | reason |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(task_id,attempt_no,row_number,field_name)`。

检查条件：
- `row_number>=1`
- `attempt_no >= 0`
- `row_number >= 0`

## analysis.export_job — 统一导出意图及结果文件

模块：M01。主键：`task_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `task_id` | uuid | 否 | 无 | task_id；PK；FK analysis.async_task(id) |
| `source_type` | varchar(200) | 否 | 无 | source_type |
| `source_schema_version` | varchar(200) | 否 | 无 | source_schema_version |
| `source_snapshot` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `input_snapshot_id` | uuid | 是 | 无 | input_snapshot_id；FK analysis.input_snapshot(id) |
| `format` | varchar(200) | 否 | 无 | format |
| `columns` | text[] | 否 | '{}'::text[] | columns |
| `export_scope` | varchar(200) | 否 | 无 | export_scope |
| `output_file_id` | uuid | 是 | 无 | output_file_id；FK core.file_asset(id) |
| `purpose` | varchar(1000) | 是 | 无 | purpose |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `format IN ('XLSX','CSV','ZIP')`
- `jsonb_typeof(source_snapshot) IN ('object','array')`

## capture.ingestion_source — 接入源适配目录

模块：M03。主键：`source_code`。

待确认：P-004/P-005：源协议、字段映射、稳定去重键与回放范围未签认

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `source_code` | varchar(200) | 否 | 无 | source_code；PK |
| `name` | varchar(200) | 否 | 无 | name |
| `source_kind` | varchar(200) | 否 | 无 | source_kind |
| `adapter_code` | varchar(200) | 否 | 无 | adapter_code |
| `protocol_version` | varchar(200) | 否 | 无 | protocol_version |
| `enabled` | boolean | 否 | false | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## capture.source_mapping_version — 不可覆盖的字段映射/去重版本

模块：M03。主键：`source_code,mapping_version`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `source_code` | varchar(200) | 否 | 无 | source_code；PK；FK capture.ingestion_source(source_code) |
| `mapping_version` | varchar(200) | 否 | 无 | mapping_version；PK |
| `schema_version` | varchar(200) | 否 | 无 | schema_version |
| `mapping_definition` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `dedup_rule_version` | varchar(200) | 否 | 无 | dedup_rule_version |
| `effective_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `enabled` | boolean | 否 | false | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `jsonb_typeof(mapping_definition) IN ('object','array')`

## capture.ingestion_batch — 接入/回放/补数批次

模块：M03。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `task_id` | uuid | 否 | 无 | task_id；FK analysis.async_task(id) |
| `source_code` | varchar(200) | 否 | 无 | source_code；FK capture.source_mapping_version(source_code,mapping_version) |
| `source_file_id` | uuid | 是 | 无 | source_file_id；FK core.file_asset(id) |
| `mapping_version` | varchar(200) | 否 | 无 | mapping_version；FK capture.source_mapping_version(source_code,mapping_version) |
| `dedup_rule_version` | varchar(200) | 否 | 无 | dedup_rule_version |
| `mode` | varchar(200) | 否 | 'INGEST' | mode |
| `original_batch_id` | uuid | 是 | 无 | original_batch_id；FK capture.ingestion_batch(id) |
| `time_from` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `time_to` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `status` | varchar(200) | 否 | 'PENDING' | status |
| `accepted_count` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `rejected_count` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `duplicate_count` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `finished_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `error_summary` | varchar(2000) | 是 | 无 | error_summary |
| `reason` | varchar(1000) | 是 | 无 | reason |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(task_id)`。

检查条件：
- `mode IN ('INGEST','REPLAY','BACKFILL')`
- `status IN ('PENDING','RUNNING','SUCCESS','FAILED','CANCELLED')`
- `time_from IS NULL OR time_to IS NULL OR time_from<time_to`
- `accepted_count >= 0`
- `rejected_count >= 0`
- `duplicate_count >= 0`

## capture.source_event — 稳定源事件身份；重复接入不重复事实

模块：M03。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `source_code` | varchar(200) | 否 | 无 | source_code；FK capture.ingestion_source(source_code) |
| `source_record_key` | varchar(200) | 否 | 无 | source_record_key |
| `data_type` | varchar(200) | 否 | 无 | data_type |
| `source_device_key` | varchar(200) | 是 | 无 | source_device_key |
| `source_capture_time` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `raw_file_id` | uuid | 是 | 无 | raw_file_id；FK core.file_asset(id) |
| `raw_offset` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `payload_digest` | char(64) | 否 | 无 | payload_digest |
| `first_received_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(source_code,source_record_key)`。

检查条件：
- `data_type IN ('FENCE','MAC','PLATE','FACE','DEVICE_STATUS','DEVICE_COUNTER')`
- `raw_offset >= 0`

## capture.ingestion_item — 接入处理尝试，只保留追溯必需字段

模块：M03。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `batch_id` | uuid | 否 | 无 | batch_id；FK capture.ingestion_batch(id) |
| `row_number` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `source_event_id` | uuid | 是 | 无 | source_event_id；FK capture.source_event(id) |
| `capture_id` | uuid | 是 | 无 | capture_id；FK capture.capture_event(id) |
| `processing_status` | varchar(200) | 否 | 无 | processing_status |
| `processed_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `record_kind` | varchar(200) | 否 | 'CAPTURE' | record_kind |
| `device_status_event_id` | uuid | 是 | 无 | device_status_event_id；FK core.device_status_event(id) |
| `counter_sample_id` | uuid | 是 | 无 | counter_sample_id；FK capture.source_counter_sample(id) |

唯一约束：`(batch_id,row_number)`。

检查条件：
- `processing_status IN ('ACCEPTED','REJECTED','DUPLICATE')`
- `row_number>=1`
- `row_number >= 0`
- `record_kind IN ('CAPTURE','DEVICE_STATUS','DEVICE_COUNTER')`
- `processing_status!='ACCEPTED' OR (source_event_id IS NOT NULL AND ((record_kind='CAPTURE' AND capture_id IS NOT NULL AND device_status_event_id IS NULL AND counter_sample_id IS NULL) OR (record_kind='DEVICE_STATUS' AND capture_id IS NULL AND device_status_event_id IS NOT NULL AND counter_sample_id IS NULL) OR (record_kind='DEVICE_COUNTER' AND capture_id IS NULL AND device_status_event_id IS NULL AND counter_sample_id IS NOT NULL)))`

## capture.quality_issue — 校验错误与未知设备原键

模块：M03。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `ingestion_item_id` | uuid | 否 | 无 | ingestion_item_id；FK capture.ingestion_item(id) |
| `issue_code` | varchar(200) | 否 | 无 | issue_code |
| `field_name` | varchar(200) | 是 | 无 | field_name |
| `reason` | varchar(2000) | 否 | 无 | reason |
| `source_device_key` | varchar(200) | 是 | 无 | source_device_key |
| `device_id` | uuid | 是 | 无 | device_id；FK core.device(id) |
| `occurred_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `rule_version` | varchar(200) | 否 | 无 | rule_version |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## capture.source_counter_sample — 设备真实采集计数，不等于平台入库数

模块：M03。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `source_code` | varchar(200) | 否 | 无 | source_code；FK capture.ingestion_source(source_code) |
| `device_id` | uuid | 是 | 无 | device_id；FK core.device(id) |
| `source_device_key` | varchar(200) | 否 | 无 | source_device_key |
| `sample_key` | varchar(200) | 否 | 无 | sample_key |
| `period_from` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `period_to` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `reported_count` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `reported_cumulative_count` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `counter_epoch` | varchar(200) | 否 | 无 | counter_epoch |
| `as_of` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(source_code,source_device_key,sample_key)`。

检查条件：
- `period_from<period_to`
- `reported_count IS NOT NULL OR reported_cumulative_count IS NOT NULL`
- `reported_count >= 0`
- `reported_cumulative_count >= 0`

## capture.target — 带类型的标识代理，非自然人身份

模块：M03。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `target_type` | varchar(200) | 否 | 无 | target_type |
| `identifier_cipher` | bytea | 是 | 无 | identifier_cipher |
| `cipher_key_version` | varchar(200) | 是 | 无 | cipher_key_version |
| `normalization_version` | varchar(200) | 否 | 无 | normalization_version |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(id,target_type)`。

检查条件：
- `target_type IN ('IMSI','IMEI','MAC','PLATE','FACE')`
- `(identifier_cipher IS NULL)=(cipher_key_version IS NULL)`

## capture.target_lookup — 轮换密钥的HMAC精确索引

模块：M03。主键：`target_id,normalization_version,hmac_key_version`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `target_id` | uuid | 否 | 无 | target_id；PK；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 否 | 无 | target_type；FK capture.target(id,target_type) |
| `normalization_version` | varchar(200) | 否 | 无 | normalization_version；PK |
| `hmac_key_version` | varchar(200) | 否 | 无 | hmac_key_version；PK |
| `lookup_hash` | bytea | 否 | 无 | lookup_hash |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(target_type,normalization_version,hmac_key_version,lookup_hash)`。

## capture.target_attribution — 归属地及来源版本

模块：M03。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `target_id` | uuid | 否 | 无 | target_id；FK capture.target(id) |
| `country_code` | varchar(200) | 是 | 无 | country_code |
| `province_code` | varchar(200) | 是 | 无 | province_code |
| `region_code` | varchar(200) | 是 | 无 | region_code；FK core.region(region_code) |
| `source_code` | varchar(200) | 否 | 无 | source_code |
| `source_version` | varchar(200) | 否 | 无 | source_version |
| `valid_from` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `valid_until` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `valid_until IS NULL OR valid_from<valid_until`

## capture.capture_event — 公共采集事实，四源统一检索

模块：M03。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `source_event_id` | uuid | 否 | 无 | source_event_id；FK capture.source_event(id) |
| `batch_id` | uuid | 否 | 无 | batch_id；FK capture.ingestion_batch(id) |
| `data_type` | varchar(200) | 否 | 无 | data_type |
| `primary_target_id` | uuid | 是 | 无 | primary_target_id；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 否 | 无 | target_type；FK capture.target(id,target_type) |
| `device_id` | uuid | 是 | 无 | device_id；FK core.device(id) |
| `site_id` | uuid | 是 | 无 | site_id；FK core.site(id) |
| `region_code_at_capture` | varchar(200) | 是 | 无 | region_code_at_capture；FK core.region(region_code) |
| `device_category_at_capture` | varchar(200) | 是 | 无 | device_category_at_capture |
| `capture_time` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `ingested_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `location` | geometry(Point,4326) | 是 | 无 | WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标 |
| `location_source` | varchar(200) | 是 | 无 | location_source |
| `attribution_id` | uuid | 是 | 无 | attribution_id；FK capture.target_attribution(id) |
| `status` | varchar(200) | 否 | 'VALID' | status |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(source_event_id)`。

检查条件：
- `data_type IN ('FENCE','MAC','PLATE','FACE')`
- `target_type IN ('IMSI','IMEI','MAC','PLATE','FACE')`
- `status IN ('VALID','QUARANTINED','RETRACTED')`
- `(data_type='FENCE' AND target_type IN ('IMSI','IMEI')) OR (data_type=target_type AND data_type IN ('MAC','PLATE','FACE'))`
- `location IS NULL OR (NOT ST_IsEmpty(location) AND ST_X(location) BETWEEN -180 AND 180 AND ST_Y(location) BETWEEN -90 AND 90)`

## capture.capture_target — 采集的其他目标，避免主目标查询重复存储

模块：M03。主键：`capture_id,target_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `capture_id` | uuid | 否 | 无 | capture_id；PK；FK capture.capture_event(id) |
| `target_id` | uuid | 否 | 无 | target_id；PK；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 否 | 无 | target_type；FK capture.target(id,target_type) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `target_type IN ('IMSI','IMEI','MAC','PLATE','FACE')`

## capture.fence_record — 围栏属性与IMSI/IMEI双标识

模块：M03。主键：`capture_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `capture_id` | uuid | 否 | 无 | capture_id；PK；FK capture.capture_event(id) |
| `imsi_target_id` | uuid | 是 | 无 | imsi_target_id；FK capture.target(id) |
| `imei_target_id` | uuid | 是 | 无 | imei_target_id；FK capture.target(id) |
| `carrier_code` | varchar(200) | 是 | 无 | carrier_code |
| `radio_type` | varchar(200) | 是 | 无 | radio_type |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## capture.mac_record — MAC专属属性

模块：M03。主键：`capture_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `capture_id` | uuid | 否 | 无 | capture_id；PK；FK capture.capture_event(id) |
| `mac_target_id` | uuid | 是 | 无 | mac_target_id；FK capture.target(id) |
| `wireless_type` | varchar(200) | 是 | 无 | wireless_type |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## capture.plate_record — 车牌专属属性

模块：M03。主键：`capture_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `capture_id` | uuid | 否 | 无 | capture_id；PK；FK capture.capture_event(id) |
| `plate_target_id` | uuid | 是 | 无 | plate_target_id；FK capture.target(id) |
| `plate_color` | varchar(200) | 是 | 无 | plate_color |
| `vehicle_image_asset_id` | uuid | 是 | 无 | vehicle_image_asset_id；FK core.file_asset(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## capture.face_record — 人像专属属性

模块：M03。主键：`capture_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `capture_id` | uuid | 否 | 无 | capture_id；PK；FK capture.capture_event(id) |
| `image_asset_id` | uuid | 否 | 无 | image_asset_id；FK core.file_asset(id) |
| `model_version` | varchar(200) | 是 | 无 | model_version |
| `quality_score` | numeric(6,5) | 是 | 无 | quality_score |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `quality_score IS NULL OR quality_score BETWEEN 0 AND 1`

## capture.face_feature — 同一人像多模型特征引用

模块：M11。主键：`capture_id,model_version`。

待确认：P-007：人像特征格式、维度、模型许可与阈值未定，不预建向量索引

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `capture_id` | uuid | 否 | 无 | capture_id；PK；FK capture.face_record(capture_id) |
| `model_version` | varchar(200) | 否 | 无 | model_version；PK |
| `feature_asset_id` | uuid | 否 | 无 | feature_asset_id；FK core.file_asset(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## capture.capture_revision — 撤回/更正记录，原事实保留

模块：M03。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `capture_id` | uuid | 否 | 无 | capture_id；FK capture.capture_event(id) |
| `revision_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `previous_status` | varchar(200) | 否 | 无 | previous_status |
| `status` | varchar(200) | 否 | 无 | status |
| `actor_user_id` | uuid | 是 | 无 | actor_user_id；FK auth.app_user(id) |
| `reason` | varchar(1000) | 否 | 无 | reason |
| `occurred_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(capture_id,revision_no)`。

检查条件：
- `previous_status IN ('VALID','QUARANTINED','RETRACTED')`
- `status IN ('VALID','QUARANTINED','RETRACTED')`
- `revision_no >= 0`

## analysis.analysis_job — 分析任务领域资源

模块：M08。主键：`id`。

待确认：P-011/P-009/P-003：算法、Context生命周期和容量未签认；参数/结果分型固定

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `task_id` | uuid | 否 | 无 | task_id；FK analysis.async_task(id)；FK analysis.task_attempt(task_id,attempt_no) |
| `name` | varchar(200) | 否 | 无 | name |
| `kind` | varchar(200) | 否 | 无 | kind |
| `context_id` | uuid | 否 | 无 | context_id；FK analysis.analysis_context(id) |
| `input_snapshot_id` | uuid | 否 | 无 | input_snapshot_id；FK analysis.input_snapshot(id) |
| `algorithm_version` | varchar(200) | 是 | 无 | algorithm_version |
| `revised_from_id` | uuid | 是 | 无 | revised_from_id；FK analysis.analysis_job(id) |
| `revision_reason` | varchar(1000) | 是 | 无 | revision_reason |
| `result_count` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `published_attempt_no` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数；FK analysis.task_attempt(task_id,attempt_no) |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `deleted` | boolean | 否 | false | deleted |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(task_id)`；`(id,task_id)`。

检查条件：
- `kind IN ('COMPANION','COLLISION','REGION_STATISTICS')`
- `result_count >= 0`
- `version >= 0`
- `published_attempt_no >= 0`

## analysis.companion_parameter — 伴随参数

模块：M08。主键：`analysis_job_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `analysis_job_id` | uuid | 否 | 无 | analysis_job_id；PK；FK analysis.analysis_job(id) |
| `target_id` | uuid | 否 | 无 | target_id；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 否 | 无 | target_type；FK capture.target(id,target_type) |
| `time_from` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `time_to` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `time_window_seconds` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `spatial_window_meters` | numeric | 否 | 无 | spatial_window_meters |
| `min_result_score` | numeric(6,5) | 是 | 无 | min_result_score |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `time_from<time_to`
- `time_window_seconds>0`
- `spatial_window_meters>=0`
- `min_result_score IS NULL OR min_result_score BETWEEN 0 AND 1`
- `time_window_seconds >= 0`

## analysis.collision_parameter — 碰撞参数，规则引用不猜测算法

模块：M08。主键：`analysis_job_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `analysis_job_id` | uuid | 否 | 无 | analysis_job_id；PK；FK analysis.analysis_job(id) |
| `time_from` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `time_to` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `rule_code` | varchar(200) | 否 | 无 | rule_code |
| `rule_version` | varchar(200) | 是 | 无 | rule_version |
| `spatial_geometry` | geometry(Geometry,4326) | 是 | 无 | WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标 |
| `min_result_score` | numeric(6,5) | 是 | 无 | min_result_score |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `time_from<time_to`
- `min_result_score IS NULL OR min_result_score BETWEEN 0 AND 1`
- `spatial_geometry IS NULL OR (NOT ST_IsEmpty(spatial_geometry) AND ST_IsValid(spatial_geometry) AND ST_CoveredBy(spatial_geometry,ST_MakeEnvelope(-180,-90,180,90,4326)))`
- `spatial_geometry IS NULL OR GeometryType(spatial_geometry) IN ('POLYGON','MULTIPOLYGON')`

## analysis.region_parameter — 区域统计参数

模块：M08。主键：`analysis_job_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `analysis_job_id` | uuid | 否 | 无 | analysis_job_id；PK；FK analysis.analysis_job(id) |
| `time_from` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `time_to` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `data_types` | text[] | 否 | '{}'::text[] | 有限类型数组；不代替需要外键的业务对象关系 |
| `min_result_score` | numeric(6,5) | 是 | 无 | min_result_score |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `time_from<time_to`
- `data_types <@ ARRAY['FENCE','MAC','PLATE']::text[]`
- `cardinality(data_types)>0`
- `min_result_score IS NULL OR min_result_score BETWEEN 0 AND 1`

## analysis.analysis_scope_site — 分析限定场所

模块：M08。主键：`analysis_job_id,site_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `analysis_job_id` | uuid | 否 | 无 | analysis_job_id；PK；FK analysis.analysis_job(id) |
| `site_id` | uuid | 否 | 无 | site_id；PK；FK core.site(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.analysis_scope_region — 行政区筛选，与手绘选区区别

模块：M08。主键：`analysis_job_id,region_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `analysis_job_id` | uuid | 否 | 无 | analysis_job_id；PK；FK analysis.analysis_job(id) |
| `region_code` | varchar(200) | 否 | 无 | region_code；PK；FK core.region(region_code) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.analysis_selected_target — 碰撞选择目标

模块：M08。主键：`analysis_job_id,target_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `analysis_job_id` | uuid | 否 | 无 | analysis_job_id；PK；FK analysis.analysis_job(id) |
| `target_id` | uuid | 否 | 无 | target_id；PK；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 否 | 无 | target_type；FK capture.target(id,target_type) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.selected_region — 任务选区，不等同于行政区

模块：M08。主键：`analysis_job_id,region_no`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `analysis_job_id` | uuid | 否 | 无 | analysis_job_id；PK；FK analysis.analysis_job(id) |
| `region_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `geometry` | geometry(Geometry,4326) | 否 | 无 | WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `geometry IS NULL OR (NOT ST_IsEmpty(geometry) AND ST_IsValid(geometry) AND ST_CoveredBy(geometry,ST_MakeEnvelope(-180,-90,180,90,4326)))`
- `GeometryType(geometry) IN ('POLYGON','MULTIPOLYGON')`
- `region_no >= 0`

## analysis.analysis_result — 逐目标结果的公共字段

模块：M08。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `analysis_job_id` | uuid | 否 | 无 | analysis_job_id；FK analysis.analysis_job(id,task_id) |
| `task_id` | uuid | 否 | 无 | task_id；FK analysis.analysis_job(id,task_id)；FK analysis.task_attempt(task_id,attempt_no) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；FK analysis.task_attempt(task_id,attempt_no) |
| `target_id` | uuid | 否 | 无 | target_id；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 否 | 无 | target_type；FK capture.target(id,target_type) |
| `data_type` | varchar(200) | 否 | 无 | data_type |
| `kind` | varchar(200) | 否 | 无 | kind |
| `capture_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `site_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `region_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `first_seen_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `algorithm_version` | varchar(200) | 否 | 无 | algorithm_version |
| `generated_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(analysis_job_id,attempt_no,target_id,data_type)`；`(id,analysis_job_id)`。

检查条件：
- `data_type IN ('FENCE','MAC','PLATE','FACE')`
- `kind IN ('COMPANION','COLLISION','REGION_STATISTICS')`
- `attempt_no >= 0`
- `capture_count >= 0`
- `site_count >= 0`
- `region_count >= 0`

## analysis.companion_result — 伴随具体结果

模块：M08。主键：`result_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `result_id` | uuid | 否 | 无 | result_id；PK；FK analysis.analysis_result(id) |
| `common_occurrence_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `shared_site_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `trajectory_overlap_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `association_score` | numeric(6,5) | 是 | 无 | association_score |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `association_score IS NULL OR association_score BETWEEN 0 AND 1`
- `common_occurrence_count >= 0`
- `shared_site_count >= 0`
- `trajectory_overlap_count >= 0`

## analysis.collision_result — 碰撞具体结果

模块：M08。主键：`result_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `result_id` | uuid | 否 | 无 | result_id；PK；FK analysis.analysis_result(id) |
| `collision_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `match_rate` | numeric(6,5) | 是 | 无 | match_rate |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `match_rate IS NULL OR match_rate BETWEEN 0 AND 1`
- `collision_count >= 0`

## analysis.region_result — 选区覆盖率，不解释为嫌疑概率

模块：M08。主键：`result_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `result_id` | uuid | 否 | 无 | result_id；PK；FK analysis.analysis_result(id) |
| `visited_selected_region_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `total_selected_region_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `coverage_rate` | numeric(8,7) | 否 | 无 | coverage_rate |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `total_selected_region_count>0`
- `visited_selected_region_count<=total_selected_region_count`
- `coverage_rate BETWEEN 0 AND 1`
- `abs(coverage_rate-visited_selected_region_count::numeric/total_selected_region_count)<=0.0000001`
- `visited_selected_region_count >= 0`
- `total_selected_region_count >= 0`

## analysis.collision_event — 碰撞事件时间地点

模块：M08。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `result_id` | uuid | 否 | 无 | result_id；FK analysis.collision_result(result_id) |
| `occurred_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `location` | geometry(Point,4326) | 否 | 无 | WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `location IS NULL OR (NOT ST_IsEmpty(location) AND ST_X(location) BETWEEN -180 AND 180 AND ST_Y(location) BETWEEN -90 AND 90)`

## analysis.result_evidence — 结果到原始采集的证据

模块：M08。主键：`result_id,capture_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `result_id` | uuid | 否 | 无 | result_id；PK；FK analysis.analysis_result(id) |
| `capture_id` | uuid | 否 | 无 | capture_id；PK；FK capture.capture_event(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.result_region — 结果经过的行政区

模块：M08。主键：`result_id,region_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `result_id` | uuid | 否 | 无 | result_id；PK；FK analysis.analysis_result(id) |
| `region_code` | varchar(200) | 否 | 无 | region_code；PK；FK core.region(region_code) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.result_selected_region — 结果经过的手绘选区

模块：M08。主键：`result_id,region_no`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `result_id` | uuid | 否 | 无 | result_id；PK；FK analysis.region_result(result_id)；FK analysis.analysis_result(id,analysis_job_id) |
| `analysis_job_id` | uuid | 否 | 无 | analysis_job_id；FK analysis.selected_region(analysis_job_id,region_no)；FK analysis.analysis_result(id,analysis_job_id) |
| `region_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK；FK analysis.selected_region(analysis_job_id,region_no) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `region_no >= 0`

## analysis.dataset — 数据集容器

模块：M09。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK；FK analysis.dataset_version(dataset_id,version_no) |
| `name` | varchar(200) | 否 | 无 | name |
| `owner_user_id` | uuid | 否 | 无 | owner_user_id；FK auth.app_user(id) |
| `dataset_kind` | varchar(200) | 否 | 无 | dataset_kind |
| `element_type` | varchar(200) | 否 | 无 | element_type |
| `status` | varchar(200) | 否 | 'ACTIVE' | status |
| `current_version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数；FK analysis.dataset_version(dataset_id,version_no) |
| `source_context_id` | uuid | 是 | 无 | source_context_id；FK analysis.analysis_context(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |

唯一约束：`(id,element_type)`。

检查条件：
- `dataset_kind IN ('SINGLE','MIXED')`
- `element_type IN ('CAPTURE','TARGET')`
- `status IN ('ACTIVE','ARCHIVED')`
- `version >= 0`
- `current_version=version`
- `current_version >= 0`

## analysis.dataset_version — 数据集每次变更版本摘要

模块：M09。主键：`dataset_id,version_no`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `dataset_id` | uuid | 否 | 无 | dataset_id；PK；FK analysis.dataset(id) |
| `version_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `operation` | varchar(200) | 否 | 无 | operation |
| `item_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `source_snapshot_id` | uuid | 是 | 无 | source_snapshot_id；FK analysis.input_snapshot(id) |
| `created_by` | uuid | 否 | 无 | created_by；FK auth.app_user(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `name_snapshot` | varchar(200) | 否 | 无 | name_snapshot |

检查条件：
- `item_count >= 0`
- `version_no >= 0`

## analysis.dataset_item — 采集/目标集合元素及版本有效区间

模块：M09。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `dataset_id` | uuid | 否 | 无 | dataset_id；FK analysis.dataset(id,element_type)；FK analysis.dataset_version(dataset_id,version_no)；FK analysis.dataset_version(dataset_id,version_no) |
| `element_type` | varchar(200) | 否 | 无 | element_type；FK analysis.dataset(id,element_type) |
| `capture_id` | uuid | 是 | 无 | capture_id；FK capture.capture_event(id) |
| `target_id` | uuid | 是 | 无 | target_id；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 是 | 无 | target_type；FK capture.target(id,target_type) |
| `source_capture_id` | uuid | 是 | 无 | source_capture_id；FK capture.capture_event(id) |
| `valid_from_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；FK analysis.dataset_version(dataset_id,version_no) |
| `valid_to_version` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数；FK analysis.dataset_version(dataset_id,version_no) |
| `added_by` | uuid | 否 | 无 | added_by；FK auth.app_user(id) |
| `added_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `(element_type='CAPTURE' AND capture_id IS NOT NULL AND target_id IS NULL AND target_type IS NULL) OR (element_type='TARGET' AND capture_id IS NULL AND target_id IS NOT NULL AND target_type IS NOT NULL)`
- `valid_to_version IS NULL OR valid_from_version<valid_to_version`
- `valid_from_version >= 0`
- `valid_to_version >= 0`

## analysis.dataset_grant — 数据集共享上限，不能覆盖原始数据权限

模块：M09。主键：`id`。

待确认：P-002/P-013：共享主体、权限继承和生命周期未签认；P-012 ID集合语法已收敛

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `dataset_id` | uuid | 否 | 无 | dataset_id；FK analysis.dataset(id) |
| `user_id` | uuid | 是 | 无 | user_id；FK auth.app_user(id) |
| `role_id` | uuid | 是 | 无 | role_id；FK auth.role(id) |
| `access_level` | varchar(200) | 否 | 无 | access_level |
| `expires_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `num_nonnulls(user_id,role_id)=1`
- `access_level IN ('READ','WRITE','OWNER')`

## analysis.set_job — 大集合异步运算领域资源

模块：M09。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `task_id` | uuid | 否 | 无 | task_id；FK analysis.async_task(id) |
| `name` | varchar(200) | 否 | 无 | name |
| `operator` | varchar(200) | 否 | 无 | operator |
| `expression` | varchar(2000) | 是 | 无 | expression |
| `element_type` | varchar(200) | 否 | 无 | element_type |
| `dedup_rule_version` | varchar(200) | 否 | 无 | dedup_rule_version |
| `result_dataset_id` | uuid | 是 | 无 | result_dataset_id；FK analysis.dataset_version(dataset_id,version_no) |
| `result_dataset_version` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数；FK analysis.dataset_version(dataset_id,version_no) |
| `result_count` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(task_id)`。

检查条件：
- `operator IN ('INTERSECTION','UNION','DIFFERENCE','MIXED')`
- `element_type IN ('CAPTURE','TARGET')`
- `operator!='MIXED' OR expression IS NOT NULL`
- `(result_dataset_id IS NULL)=(result_dataset_version IS NULL)`
- `result_count >= 0`
- `result_dataset_version >= 0`

## analysis.set_operand — 集合输入的固定版本和符号

模块：M09。主键：`set_job_id,symbol`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `set_job_id` | uuid | 否 | 无 | set_job_id；PK；FK analysis.set_job(id) |
| `symbol` | varchar(200) | 否 | 无 | symbol；PK |
| `dataset_id` | uuid | 否 | 无 | dataset_id；FK analysis.dataset_version(dataset_id,version_no) |
| `dataset_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；FK analysis.dataset_version(dataset_id,version_no) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `dataset_version >= 0`

## analysis.profile — 人员/车辆档案公共信息

模块：M11。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `profile_type` | varchar(200) | 否 | 无 | profile_type |
| `display_label` | varchar(200) | 否 | 无 | display_label |
| `confidence` | numeric(6,5) | 是 | 无 | confidence |
| `source` | varchar(200) | 否 | 无 | source |
| `first_seen_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `last_seen_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |

唯一约束：`(id,profile_type)`。

检查条件：
- `profile_type IN ('PERSON','VEHICLE')`
- `confidence IS NULL OR confidence BETWEEN 0 AND 1`
- `first_seen_at IS NULL OR last_seen_at IS NULL OR first_seen_at<=last_seen_at`
- `version >= 0`

## analysis.person_profile — 图码/人员档案字段

模块：M11。主键：`profile_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `profile_id` | uuid | 否 | 无 | profile_id；PK；FK analysis.profile(id,profile_type) |
| `profile_type` | varchar(200) | 否 | 'PERSON' | profile_type；FK analysis.profile(id,profile_type) |
| `name_cipher` | bytea | 是 | 无 | name_cipher |
| `gender_code` | varchar(200) | 是 | 无 | gender_code |
| `ethnicity_code` | varchar(200) | 是 | 无 | ethnicity_code |
| `phone_cipher` | bytea | 是 | 无 | phone_cipher |
| `identity_number_cipher` | bytea | 是 | 无 | identity_number_cipher |
| `cipher_key_version` | varchar(200) | 是 | 无 | cipher_key_version |
| `residence_region_code` | varchar(200) | 是 | 无 | residence_region_code；FK core.region(region_code) |
| `suspected_site_id` | uuid | 是 | 无 | suspected_site_id；FK core.site(id) |
| `suspected_landing_text` | varchar(1000) | 是 | 无 | suspected_landing_text |
| `appearance_description` | varchar(2000) | 是 | 无 | appearance_description |
| `portrait_asset_id` | uuid | 是 | 无 | portrait_asset_id；FK core.file_asset(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `profile_type='PERSON'`

## analysis.vehicle_profile — 车码/车辆档案字段

模块：M11。主键：`profile_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `profile_id` | uuid | 否 | 无 | profile_id；PK；FK analysis.profile(id,profile_type) |
| `profile_type` | varchar(200) | 否 | 'VEHICLE' | profile_type；FK analysis.profile(id,profile_type) |
| `primary_plate_target_id` | uuid | 否 | 无 | primary_plate_target_id；FK capture.target(id)；FK capture.target(id,target_type) |
| `plate_color` | varchar(200) | 是 | 无 | plate_color |
| `vehicle_type_code` | varchar(200) | 是 | 无 | vehicle_type_code |
| `vehicle_image_asset_id` | uuid | 是 | 无 | vehicle_image_asset_id；FK core.file_asset(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `plate_target_type` | varchar(200) | 否 | 'PLATE' | plate_target_type；FK capture.target(id,target_type) |

检查条件：
- `profile_type='VEHICLE'`
- `plate_target_type='PLATE'`

## analysis.profile_identifier_index — 人员敏感字段精确HMAC检索

模块：M11。主键：`profile_id,field_code,hmac_key_version,normalization_version`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `profile_id` | uuid | 否 | 无 | profile_id；PK；FK analysis.profile(id) |
| `field_code` | varchar(200) | 否 | 无 | field_code；PK |
| `hmac_key_version` | varchar(200) | 否 | 无 | hmac_key_version；PK |
| `normalization_version` | varchar(200) | 否 | 无 | normalization_version；PK |
| `lookup_hash` | bytea | 否 | 无 | lookup_hash |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `field_code IN ('NAME','PHONE','IDENTITY_NUMBER')`

## analysis.profile_link — 带证据的目标关联

模块：M11。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `profile_id` | uuid | 否 | 无 | profile_id；FK analysis.profile(id) |
| `target_id` | uuid | 否 | 无 | target_id；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 否 | 无 | target_type；FK capture.target(id,target_type) |
| `source` | varchar(200) | 否 | 无 | source |
| `algorithm_version` | varchar(200) | 是 | 无 | algorithm_version |
| `confidence` | numeric(6,5) | 是 | 无 | confidence |
| `review_status` | varchar(200) | 否 | 'UNREVIEWED' | review_status |
| `first_seen_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `last_seen_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `valid_from` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `valid_until` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `review_status IN ('UNREVIEWED','CONFIRMED','REJECTED')`
- `confidence IS NULL OR confidence BETWEEN 0 AND 1`
- `valid_until IS NULL OR valid_from<valid_until`

## analysis.profile_evidence — 原始证据，不被模型或人工覆盖

模块：M11。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `profile_id` | uuid | 否 | 无 | profile_id；FK analysis.profile(id) |
| `capture_id` | uuid | 否 | 无 | capture_id；FK capture.capture_event(id) |
| `source` | varchar(200) | 否 | 无 | source |
| `source_version` | varchar(200) | 否 | 无 | source_version |
| `relation` | varchar(200) | 否 | 无 | relation |
| `confidence` | numeric(6,5) | 是 | 无 | confidence |
| `collected_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `review_status` | varchar(200) | 否 | 'UNREVIEWED' | review_status |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(profile_id,capture_id,source,source_version,relation)`。

检查条件：
- `relation IN ('PRIMARY','ASSOCIATED')`
- `review_status IN ('UNREVIEWED','CONFIRMED','REJECTED')`
- `confidence IS NULL OR confidence BETWEEN 0 AND 1`

## analysis.profile_link_evidence — 一个关联可有多份证据

模块：M11。主键：`link_id,evidence_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `link_id` | uuid | 否 | 无 | link_id；PK；FK analysis.profile_link(id) |
| `evidence_id` | uuid | 否 | 无 | evidence_id；PK；FK analysis.profile_evidence(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.profile_revision — 人工修订/合并/拆分/冲突处理记录

模块：M11。主键：`id`。

待确认：P-016：跨源拟合与合并拆分规则未定；人工动作保留证据及谱系

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `profile_id` | uuid | 否 | 无 | profile_id；FK analysis.profile(id) |
| `task_id` | uuid | 是 | 无 | task_id；FK analysis.async_task(id) |
| `revision_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `action` | varchar(200) | 否 | 无 | action |
| `reason` | varchar(1000) | 否 | 无 | reason |
| `actor_user_id` | uuid | 否 | 无 | actor_user_id；FK auth.app_user(id) |
| `before_snapshot` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `after_snapshot` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `occurred_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(profile_id,revision_no)`。

检查条件：
- `action IN ('CORRECT','MERGE','SPLIT','RESOLVE_CONFLICT')`
- `revision_no >= 0`
- `jsonb_typeof(before_snapshot) IN ('object','array')`
- `jsonb_typeof(after_snapshot) IN ('object','array')`

## analysis.profile_revision_member — 合并/拆分来源与结果谱系

模块：M11。主键：`revision_id,profile_id,direction`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `revision_id` | uuid | 否 | 无 | revision_id；PK；FK analysis.profile_revision(id) |
| `profile_id` | uuid | 否 | 无 | profile_id；PK；FK analysis.profile(id) |
| `direction` | varchar(200) | 否 | 无 | direction；PK |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `direction IN ('SOURCE','RESULT')`

## analysis.profile_revision_evidence — 修订所依据的证据

模块：M11。主键：`revision_id,evidence_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `revision_id` | uuid | 否 | 无 | revision_id；PK；FK analysis.profile_revision(id) |
| `evidence_id` | uuid | 否 | 无 | evidence_id；PK；FK analysis.profile_evidence(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.profile_conflict — 冲突字段及处理状态

模块：M11。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `profile_id` | uuid | 否 | 无 | profile_id；FK analysis.profile(id) |
| `field_code` | varchar(200) | 否 | 无 | field_code |
| `description` | varchar(2000) | 否 | 无 | description |
| `candidate_values` | jsonb | 否 | 无 | 候选字段值/来源/版本；敏感值加密或引用证据，不保存明文 |
| `status` | varchar(200) | 否 | 'OPEN' | status |
| `resolved_revision_id` | uuid | 是 | 无 | resolved_revision_id；FK analysis.profile_revision(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `status IN ('OPEN','RESOLVED')`
- `status!='RESOLVED' OR resolved_revision_id IS NOT NULL`
- `jsonb_typeof(candidate_values) IN ('object','array')`

## analysis.profile_conflict_evidence — 冲突证据明细

模块：M11。主键：`conflict_id,evidence_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `conflict_id` | uuid | 否 | 无 | conflict_id；PK；FK analysis.profile_conflict(id) |
| `evidence_id` | uuid | 否 | 无 | evidence_id；PK；FK analysis.profile_evidence(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.person_library — 人员库及权限范围

模块：M11。主键：`id`。

待确认：P-002：库范围不得扩大当前用户/原始数据权限

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `name` | varchar(200) | 否 | 无 | name |
| `category_code` | varchar(200) | 否 | 无 | category_code |
| `description` | varchar(1000) | 是 | 无 | description |
| `owner_user_id` | uuid | 否 | 无 | owner_user_id；FK auth.app_user(id) |
| `management_all_sites` | boolean | 否 | false | management_all_sites |
| `data_all_sites` | boolean | 否 | false | data_all_sites |
| `own_only` | boolean | 否 | true | own_only |
| `scope_version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `scope_policy_version` | varchar(200) | 否 | 'PENDING-P002' | scope_policy_version |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |

检查条件：
- `version >= 0`
- `scope_version >= 0`

## analysis.library_scope_site — 人员库场所范围

模块：M11。主键：`library_id,site_id,scope_kind`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `library_id` | uuid | 否 | 无 | library_id；PK；FK analysis.person_library(id) |
| `site_id` | uuid | 否 | 无 | site_id；PK；FK core.site(id) |
| `scope_kind` | varchar(200) | 否 | 无 | scope_kind；PK |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `scope_kind IN ('MANAGEMENT','DATA')`

## analysis.library_scope_data_type — 人员库数据类型范围

模块：M11。主键：`library_id,data_type`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `library_id` | uuid | 否 | 无 | library_id；PK；FK analysis.person_library(id) |
| `data_type` | varchar(200) | 否 | 无 | data_type；PK |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `data_type IN ('FENCE','MAC','PLATE','FACE')`

## analysis.person_library_member — 库成员，移除不删除全局档案

模块：M11。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `library_id` | uuid | 否 | 无 | library_id；FK analysis.person_library(id) |
| `profile_id` | uuid | 否 | 无 | profile_id；FK analysis.profile(id) |
| `primary_target_link_id` | uuid | 否 | 无 | primary_target_link_id；FK analysis.profile_link(id) |
| `source` | varchar(200) | 否 | 无 | source |
| `added_by` | uuid | 否 | 无 | added_by；FK auth.app_user(id) |
| `added_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |

检查条件：
- `version >= 0`

## analysis.algorithm_version — 算法版本登记，未签认不执行

模块：M11。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `algorithm_code` | varchar(200) | 否 | 无 | algorithm_code |
| `version_code` | varchar(200) | 否 | 无 | version_code |
| `algorithm_kind` | varchar(200) | 否 | 无 | algorithm_kind |
| `parameter_schema_version` | varchar(200) | 否 | 无 | parameter_schema_version |
| `status` | varchar(200) | 否 | 'PENDING' | status |
| `effective_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(algorithm_code,version_code)`。

检查条件：
- `status IN ('PENDING','APPROVED','RETIRED')`

## analysis.face_search_job — 人像搜图任务参数

模块：M05。主键：`task_id`。

待确认：P-007：人像模型、阈值、用途、候选与导出授权未签认

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `task_id` | uuid | 否 | 无 | task_id；PK；FK analysis.async_task(id)；FK analysis.task_attempt(task_id,attempt_no) |
| `image_asset_id` | uuid | 否 | 无 | image_asset_id；FK core.file_asset(id) |
| `input_snapshot_id` | uuid | 否 | 无 | input_snapshot_id；FK analysis.input_snapshot(id) |
| `time_from` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `time_to` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `min_similarity` | numeric(6,5) | 是 | 无 | min_similarity |
| `candidate_limit` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `model_version` | varchar(200) | 是 | 无 | model_version |
| `published_attempt_no` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数；FK analysis.task_attempt(task_id,attempt_no) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `time_from<time_to`
- `candidate_limit>0`
- `min_similarity IS NULL OR min_similarity BETWEEN 0 AND 1`
- `candidate_limit >= 0`
- `published_attempt_no >= 0`

## analysis.face_search_result — 有上限的人像候选结果

模块：M05。主键：`task_id,attempt_no,capture_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `task_id` | uuid | 否 | 无 | task_id；PK；FK analysis.task_attempt(task_id,attempt_no) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK；FK analysis.task_attempt(task_id,attempt_no) |
| `capture_id` | uuid | 否 | 无 | capture_id；PK；FK capture.face_record(capture_id) |
| `similarity` | numeric(6,5) | 否 | 无 | similarity |
| `model_version` | varchar(200) | 否 | 无 | model_version |
| `review_required` | boolean | 否 | true | review_required |
| `rank_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(task_id,attempt_no,rank_no)`。

检查条件：
- `similarity BETWEEN 0 AND 1`
- `rank_no>=1`
- `attempt_no >= 0`
- `rank_no >= 0`

## analysis.trend_model — 趋势模型，当前只启用常驻模型

模块：M11。主键：`id`。

待确认：P-017：阈值、规则与标签目录由算法负责人签认；扩展模型需新契约

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK；FK analysis.model_parameter_version(id,model_id) |
| `code` | varchar(200) | 否 | 无 | code |
| `name` | varchar(200) | 否 | 无 | name |
| `description` | varchar(1000) | 是 | 无 | description |
| `model_type` | varchar(200) | 否 | 无 | model_type |
| `enabled` | boolean | 否 | false | enabled |
| `current_parameter_version_id` | uuid | 是 | 无 | current_parameter_version_id；FK analysis.model_parameter_version(id,model_id) |
| `filter_schema_version` | varchar(200) | 否 | '1' | filter_schema_version |
| `effective_data_filter` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |

唯一约束：`(code)`。

检查条件：
- `model_type='RESIDENCE'`
- `jsonb_typeof(effective_data_filter) IN ('object','array')`
- `version >= 0`

## analysis.model_parameter_definition — 模型参数代码/范围/单位定义

模块：M11。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `model_id` | uuid | 否 | 无 | model_id；FK analysis.trend_model(id) |
| `parameter_code` | varchar(200) | 否 | 无 | parameter_code |
| `definition_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `label` | varchar(200) | 否 | 无 | label |
| `value_type` | varchar(200) | 否 | 无 | value_type |
| `unit` | varchar(200) | 是 | 无 | unit |
| `minimum` | numeric | 是 | 无 | minimum |
| `maximum` | numeric | 是 | 无 | maximum |
| `required` | boolean | 否 | false | required |
| `description` | text | 否 | 无 | description |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(model_id,parameter_code,definition_version)`；`(id,value_type)`。

检查条件：
- `value_type IN ('TEXT','INTEGER','NUMBER','BOOLEAN','DURATION_SECONDS','DISTANCE_METERS')`
- `minimum IS NULL OR maximum IS NULL OR minimum<=maximum`
- `definition_version >= 0`

## analysis.model_parameter_version — 不可覆盖的模型参数版本

模块：M11。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `model_id` | uuid | 否 | 无 | model_id；FK analysis.trend_model(id) |
| `version_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `algorithm_version` | varchar(200) | 是 | 无 | algorithm_version |
| `effective_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 否 | 无 | created_by；FK auth.app_user(id) |
| `reason` | varchar(1000) | 否 | 无 | reason |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(model_id,version_no)`；`(id,model_id)`。

检查条件：
- `version_no >= 0`

## analysis.model_parameter_value — 强类型模型参数值

模块：M11。主键：`parameter_version_id,definition_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `parameter_version_id` | uuid | 否 | 无 | parameter_version_id；PK；FK analysis.model_parameter_version(id) |
| `definition_id` | uuid | 否 | 无 | definition_id；PK；FK analysis.model_parameter_definition(id,value_type) |
| `value_type` | varchar(200) | 否 | 无 | value_type；FK analysis.model_parameter_definition(id,value_type) |
| `text_value` | text | 是 | 无 | text_value |
| `integer_value` | bigint | 是 | 无 | integer_value |
| `number_value` | numeric | 是 | 无 | number_value |
| `boolean_value` | boolean | 是 | 无 | boolean_value |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `num_nonnulls(text_value,integer_value,number_value,boolean_value)=1 AND ((value_type='TEXT' AND text_value IS NOT NULL) OR (value_type IN ('INTEGER','DURATION_SECONDS') AND integer_value IS NOT NULL) OR (value_type IN ('NUMBER','DISTANCE_METERS') AND number_value IS NOT NULL) OR (value_type='BOOLEAN' AND boolean_value IS NOT NULL))`

## analysis.model_matching_rule — 版本化匹配规则引用

模块：M11。主键：`parameter_version_id,rule_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `parameter_version_id` | uuid | 否 | 无 | parameter_version_id；PK；FK analysis.model_parameter_version(id) |
| `rule_code` | varchar(200) | 否 | 无 | rule_code；PK |
| `label` | varchar(200) | 否 | 无 | label |
| `algorithm_version` | varchar(200) | 否 | 无 | algorithm_version |
| `description` | text | 否 | 无 | description |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.model_tag — 标签目录

模块：M11。主键：`tag_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `tag_code` | varchar(200) | 否 | 无 | tag_code；PK |
| `label` | varchar(200) | 否 | 无 | label |
| `description` | varchar(1000) | 是 | 无 | description |
| `enabled` | boolean | 否 | false | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.model_tag_binding — 模型配置标签

模块：M11。主键：`model_id,tag_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `model_id` | uuid | 否 | 无 | model_id；PK；FK analysis.trend_model(id) |
| `tag_code` | varchar(200) | 否 | 无 | tag_code；PK；FK analysis.model_tag(tag_code) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.model_rule_output_tag — 规则的输出标签

模块：M11。主键：`parameter_version_id,rule_code,tag_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `parameter_version_id` | uuid | 否 | 无 | parameter_version_id；PK；FK analysis.model_matching_rule(parameter_version_id,rule_code) |
| `rule_code` | varchar(200) | 否 | 无 | rule_code；PK；FK analysis.model_matching_rule(parameter_version_id,rule_code) |
| `tag_code` | varchar(200) | 否 | 无 | tag_code；PK；FK analysis.model_tag(tag_code) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.model_execution — 固定输入与参数版本的一次模型执行

模块：M11。主键：`task_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `task_id` | uuid | 否 | 无 | task_id；PK；FK analysis.async_task(id)；FK analysis.task_attempt(task_id,attempt_no) |
| `model_id` | uuid | 否 | 无 | model_id；FK analysis.model_parameter_version(id,model_id) |
| `parameter_version_id` | uuid | 否 | 无 | parameter_version_id；FK analysis.model_parameter_version(id,model_id) |
| `input_snapshot_id` | uuid | 否 | 无 | input_snapshot_id；FK analysis.input_snapshot(id) |
| `published_attempt_no` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数；FK analysis.task_attempt(task_id,attempt_no) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `published_attempt_no >= 0`

## analysis.model_result — 逐目标模型结果

模块：M11。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `execution_task_id` | uuid | 否 | 无 | execution_task_id；FK analysis.model_execution(task_id)；FK analysis.task_attempt(task_id,attempt_no) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；FK analysis.task_attempt(task_id,attempt_no) |
| `target_id` | uuid | 否 | 无 | target_id；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 否 | 无 | target_type；FK capture.target(id,target_type) |
| `data_type` | varchar(200) | 否 | 无 | data_type |
| `confidence` | numeric(6,5) | 是 | 无 | confidence |
| `algorithm_version` | varchar(200) | 否 | 无 | algorithm_version |
| `generated_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `review_status` | varchar(200) | 否 | 'UNREVIEWED' | review_status |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(execution_task_id,attempt_no,target_id,data_type)`。

检查条件：
- `data_type IN ('FENCE','MAC','PLATE','FACE')`
- `review_status IN ('UNREVIEWED','CONFIRMED','REJECTED')`
- `confidence IS NULL OR confidence BETWEEN 0 AND 1`
- `attempt_no >= 0`

## analysis.model_result_tag — 结果标签

模块：M11。主键：`result_id,tag_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `result_id` | uuid | 否 | 无 | result_id；PK；FK analysis.model_result(id) |
| `tag_code` | varchar(200) | 否 | 无 | tag_code；PK；FK analysis.model_tag(tag_code) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## analysis.model_result_evidence — 模型证据与采集引用

模块：M11。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `result_id` | uuid | 否 | 无 | result_id；FK analysis.model_result(id) |
| `capture_id` | uuid | 是 | 无 | capture_id；FK capture.capture_event(id) |
| `profile_evidence_id` | uuid | 是 | 无 | profile_evidence_id；FK analysis.profile_evidence(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `num_nonnulls(capture_id,profile_evidence_id)=1`

## operation.watch_rule — 布控规则及当前版本

模块：M10。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK；FK operation.watch_rule_revision(rule_id,rule_version) |
| `name` | varchar(200) | 否 | 无 | name |
| `owner_user_id` | uuid | 否 | 无 | owner_user_id；FK auth.app_user(id) |
| `context_id` | uuid | 否 | 无 | context_id；FK analysis.analysis_context(id) |
| `status` | varchar(200) | 否 | 'DRAFT' | status |
| `effective_from` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `effective_until` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `current_rule_version` | bigint | 否 | 1 | 非负计数或版本；不使用32位整型计数；FK operation.watch_rule_revision(rule_id,rule_version) |
| `ended_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `hit_count` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 1 | 非负计数或版本；不使用32位整型计数 |

检查条件：
- `status IN ('DRAFT','ACTIVE','REVOKED','FINISHED')`
- `effective_from IS NULL OR effective_until IS NULL OR effective_from<effective_until`
- `status!='ACTIVE' OR (effective_from IS NOT NULL AND effective_until IS NOT NULL)`
- `hit_count >= 0`
- `version >= 0`
- `current_rule_version=version`
- `current_rule_version >= 0`

## operation.watch_rule_revision — 规则参数和通知快照

模块：M10。主键：`rule_id,rule_version`。

待确认：P-014/P-015：布控匹配、告警去重、频控与通知规则未签认

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `rule_id` | uuid | 否 | 无 | rule_id；PK；FK operation.watch_rule(id) |
| `rule_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `name_snapshot` | varchar(200) | 否 | 无 | name_snapshot |
| `effective_from` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `effective_until` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `match_rule_code` | varchar(200) | 是 | 无 | match_rule_code |
| `match_rule_version` | varchar(200) | 是 | 无 | match_rule_version |
| `data_types` | text[] | 否 | '{}'::text[] | 有限类型数组；不代替需要外键的业务对象关系 |
| `notification_template_code` | varchar(200) | 是 | 无 | notification_template_code；FK operation.notification_template(template_code,template_version) |
| `notification_template_version` | varchar(200) | 是 | 无 | notification_template_version；FK operation.notification_template(template_code,template_version) |
| `condition_schema_version` | varchar(200) | 否 | '1' | condition_schema_version |
| `condition_snapshot` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `notification_snapshot` | jsonb | 是 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `created_by` | uuid | 否 | 无 | created_by；FK auth.app_user(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `data_types <@ ARRAY['FENCE','MAC','PLATE','FACE']::text[]`
- `cardinality(data_types)>0`
- `effective_from IS NULL OR effective_until IS NULL OR effective_from<effective_until`
- `(notification_template_code IS NULL)=(notification_template_version IS NULL)`
- `jsonb_typeof(condition_snapshot) IN ('object','array')`
- `jsonb_typeof(notification_snapshot) IN ('object','array')`
- `rule_version >= 0`

## operation.watch_rule_target — 各版规则目标及来源档案

模块：M10。主键：`rule_id,rule_version,target_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `rule_id` | uuid | 否 | 无 | rule_id；PK；FK operation.watch_rule_revision(rule_id,rule_version) |
| `rule_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK；FK operation.watch_rule_revision(rule_id,rule_version) |
| `target_id` | uuid | 否 | 无 | target_id；PK；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 否 | 无 | target_type；FK capture.target(id,target_type) |
| `source_profile_id` | uuid | 是 | 无 | source_profile_id；FK analysis.profile(id) |
| `source_library_id` | uuid | 是 | 无 | source_library_id；FK analysis.person_library(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `rule_version >= 0`

## operation.watch_rule_site — 各版规则场所范围

模块：M10。主键：`rule_id,rule_version,site_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `rule_id` | uuid | 否 | 无 | rule_id；PK；FK operation.watch_rule_revision(rule_id,rule_version) |
| `rule_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK；FK operation.watch_rule_revision(rule_id,rule_version) |
| `site_id` | uuid | 否 | 无 | site_id；PK；FK core.site(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `rule_version >= 0`

## operation.alert_event — 命中事实及通知关联

模块：M10。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `rule_id` | uuid | 否 | 无 | rule_id；FK operation.watch_rule_revision(rule_id,rule_version) |
| `rule_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；FK operation.watch_rule_revision(rule_id,rule_version) |
| `capture_id` | uuid | 否 | 无 | capture_id；FK capture.capture_event(id) |
| `target_id` | uuid | 否 | 无 | target_id；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 否 | 无 | target_type；FK capture.target(id,target_type) |
| `site_id` | uuid | 是 | 无 | site_id；FK core.site(id) |
| `device_id` | uuid | 是 | 无 | device_id；FK core.device(id) |
| `occurred_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `severity` | varchar(200) | 否 | 无 | severity |
| `status` | varchar(200) | 否 | 'OPEN' | status |
| `dedup_key` | varchar(200) | 否 | 无 | dedup_key |
| `dedup_rule_version` | varchar(200) | 否 | 无 | dedup_rule_version |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(dedup_key)`；`(id,rule_id,rule_version)`。

检查条件：
- `severity IN ('LOW','MEDIUM','HIGH')`
- `status IN ('OPEN','ACKNOWLEDGED','CLOSED')`
- `rule_version >= 0`

## operation.watch_statistics_run — 规则结束后的统计发布元信息

模块：M10。主键：`rule_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `rule_id` | uuid | 否 | 无 | rule_id；PK；FK operation.watch_rule_revision(rule_id,rule_version) |
| `final_rule_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；FK operation.watch_rule_revision(rule_id,rule_version) |
| `final` | boolean | 否 | true | final |
| `hit_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `distinct_target_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `site_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `region_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `first_seen_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `as_of` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `metric_definition_version` | varchar(200) | 否 | 无 | metric_definition_version |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `final=true`
- `hit_count >= 0`
- `distinct_target_count >= 0`
- `site_count >= 0`
- `region_count >= 0`
- `final_rule_version >= 0`

## operation.watch_statistics — 结束后逐目标集群统计

模块：M10。主键：`rule_id,target_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `rule_id` | uuid | 否 | 无 | rule_id；PK；FK operation.watch_statistics_run(rule_id) |
| `target_id` | uuid | 否 | 无 | target_id；PK；FK capture.target(id,target_type) |
| `target_type` | varchar(200) | 否 | 无 | target_type；FK capture.target(id,target_type) |
| `capture_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `site_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `region_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `first_seen_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `most_frequent_site_id` | uuid | 是 | 无 | most_frequent_site_id；FK core.site(id) |
| `most_frequent_site_name_snapshot` | varchar(200) | 是 | 无 | most_frequent_site_name_snapshot |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `capture_count >= 0`
- `site_count >= 0`
- `region_count >= 0`

## operation.notification_channel — 通道适配目录与外部凭据引用

模块：M10。主键：`id`。

待确认：P-015：短信猫、邮件协议、模板、限额、凭据与回执未签认

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `channel_type` | varchar(200) | 否 | 无 | channel_type |
| `adapter_code` | varchar(200) | 否 | 无 | adapter_code |
| `adapter_version` | varchar(200) | 否 | 无 | adapter_version |
| `credential_reference` | varchar(500) | 是 | 无 | credential_reference |
| `enabled` | boolean | 否 | false | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `channel_type IN ('SMS','EMAIL','PLATFORM')`

## operation.notification_template — 已批准通知模板的不可变版本

模块：M10。主键：`template_code,template_version`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `template_code` | varchar(200) | 否 | 无 | template_code；PK |
| `template_version` | varchar(200) | 否 | 无 | template_version；PK |
| `name` | varchar(200) | 否 | 无 | name |
| `purpose` | varchar(200) | 否 | 无 | purpose |
| `channels` | text[] | 否 | '{}'::text[] | 有限类型数组；不代替需要外键的业务对象关系 |
| `title_template` | varchar(500) | 是 | 无 | title_template |
| `body_template` | text | 否 | 无 | body_template |
| `enabled` | boolean | 否 | false | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `purpose IN ('WATCH_ALERT','DEVICE_STATUS')`
- `channels <@ ARRAY['SMS','EMAIL','PLATFORM']::text[]`
- `cardinality(channels)>0`

## operation.notification_template_variable — 允许的模板变量

模块：M10。主键：`template_code,template_version,variable_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `template_code` | varchar(200) | 否 | 无 | template_code；PK；FK operation.notification_template(template_code,template_version) |
| `template_version` | varchar(200) | 否 | 无 | template_version；PK；FK operation.notification_template(template_code,template_version) |
| `variable_code` | varchar(200) | 否 | 无 | variable_code；PK |
| `description` | varchar(1000) | 否 | 无 | description |
| `required` | boolean | 否 | false | required |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## operation.notification_plan — ONCE/DAILY通知计划，同步创建

模块：M10。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK；FK operation.notification_plan_revision(plan_id,plan_version) |
| `name` | varchar(200) | 否 | 无 | name |
| `owner_user_id` | uuid | 否 | 无 | owner_user_id；FK auth.app_user(id) |
| `status` | varchar(200) | 否 | 'ACTIVE' | status |
| `schedule_type` | varchar(200) | 否 | 无 | schedule_type |
| `scheduled_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `local_time` | time without time zone | 是 | 无 | local_time |
| `time_zone` | varchar(200) | 是 | 无 | time_zone |
| `start_date` | date | 是 | 无 | start_date |
| `end_date` | date | 是 | 无 | end_date |
| `next_run_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `last_sent_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `last_execution_task_id` | uuid | 是 | 无 | last_execution_task_id；FK analysis.async_task(id) |
| `current_plan_version` | bigint | 否 | 1 | 非负计数或版本；不使用32位整型计数；FK operation.notification_plan_revision(plan_id,plan_version) |
| `cancelled_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_by` | uuid | 是 | 无 | created_by；FK auth.app_user(id) |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `updated_by` | uuid | 是 | 无 | updated_by；FK auth.app_user(id) |
| `deleted` | boolean | 否 | false | deleted |
| `version` | bigint | 否 | 1 | 非负计数或版本；不使用32位整型计数 |

检查条件：
- `status IN ('ACTIVE','CANCELLED','COMPLETED')`
- `(schedule_type='ONCE' AND scheduled_at IS NOT NULL AND num_nonnulls(local_time,time_zone,start_date,end_date)=0) OR (schedule_type='DAILY' AND scheduled_at IS NULL AND local_time IS NOT NULL AND time_zone='Asia/Shanghai' AND start_date IS NOT NULL AND (end_date IS NULL OR start_date<=end_date))`
- `version >= 0`
- `current_plan_version=version`
- `current_plan_version >= 0`

## operation.notification_plan_revision — 模板/定时规则版本快照

模块：M10。主键：`plan_id,plan_version`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `plan_id` | uuid | 否 | 无 | plan_id；PK；FK operation.notification_plan(id) |
| `plan_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `name_snapshot` | varchar(200) | 否 | 无 | name_snapshot |
| `template_code` | varchar(200) | 否 | 无 | template_code；FK operation.notification_template(template_code,template_version) |
| `template_version` | varchar(200) | 否 | 无 | template_version；FK operation.notification_template(template_code,template_version) |
| `schedule_snapshot` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `created_by` | uuid | 否 | 无 | created_by；FK auth.app_user(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `jsonb_typeof(schedule_snapshot) IN ('object','array')`
- `plan_version >= 0`

## operation.notification_recipient — 已授权收件人代理标识

模块：M10。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `channel_type` | varchar(200) | 否 | 无 | channel_type |
| `recipient_user_id` | uuid | 是 | 无 | recipient_user_id；FK auth.app_user(id) |
| `recipient_cipher` | bytea | 是 | 无 | recipient_cipher |
| `recipient_hash` | bytea | 是 | 无 | recipient_hash |
| `hmac_key_version` | varchar(200) | 是 | 无 | hmac_key_version |
| `cipher_key_version` | varchar(200) | 是 | 无 | cipher_key_version |
| `normalization_version` | varchar(200) | 是 | 无 | normalization_version |
| `owner_user_id` | uuid | 否 | 无 | owner_user_id；FK auth.app_user(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `(channel_type='PLATFORM' AND recipient_user_id IS NOT NULL AND num_nonnulls(recipient_cipher,recipient_hash,hmac_key_version,cipher_key_version,normalization_version)=0) OR (channel_type IN ('SMS','EMAIL') AND recipient_user_id IS NULL AND recipient_cipher IS NOT NULL AND recipient_hash IS NOT NULL AND hmac_key_version IS NOT NULL AND cipher_key_version IS NOT NULL AND normalization_version IS NOT NULL)`

## operation.notification_plan_recipient — 固定计划版本的收件人

模块：M10。主键：`plan_id,plan_version,recipient_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `plan_id` | uuid | 否 | 无 | plan_id；PK；FK operation.notification_plan_revision(plan_id,plan_version) |
| `plan_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK；FK operation.notification_plan_revision(plan_id,plan_version) |
| `recipient_id` | uuid | 否 | 无 | recipient_id；PK；FK operation.notification_recipient(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `plan_version >= 0`

## operation.watch_notification_recipient — 固定布控版本的收件人

模块：M10。主键：`rule_id,rule_version,recipient_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `rule_id` | uuid | 否 | 无 | rule_id；PK；FK operation.watch_rule_revision(rule_id,rule_version) |
| `rule_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK；FK operation.watch_rule_revision(rule_id,rule_version) |
| `recipient_id` | uuid | 否 | 无 | recipient_id；PK；FK operation.notification_recipient(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `rule_version >= 0`

## operation.notification_execution — 计划每一次执行；重试同taskId

模块：M10。主键：`task_id`。

待确认：P-015：错过执行补发、频控与去重窗口未定，不盲目补发

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `task_id` | uuid | 否 | 无 | task_id；PK；FK analysis.async_task(id) |
| `plan_id` | uuid | 否 | 无 | plan_id；FK operation.notification_plan_revision(plan_id,plan_version) |
| `plan_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；FK operation.notification_plan_revision(plan_id,plan_version) |
| `occurrence_key` | varchar(200) | 否 | 无 | occurrence_key |
| `scheduled_for` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(plan_id,occurrence_key)`；`(task_id,plan_id,plan_version)`。

检查条件：
- `plan_version >= 0`

## operation.delivery — 每个收件人/通道的一次投递事实，短信日志共用

模块：M10。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `execution_task_id` | uuid | 是 | 无 | execution_task_id；FK analysis.async_task(id)；FK operation.notification_execution(task_id,plan_id,plan_version) |
| `plan_id` | uuid | 是 | 无 | plan_id；FK operation.notification_plan_revision(plan_id,plan_version)；FK operation.notification_execution(task_id,plan_id,plan_version) |
| `plan_version` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数；FK operation.notification_plan_revision(plan_id,plan_version)；FK operation.notification_execution(task_id,plan_id,plan_version) |
| `alert_id` | uuid | 是 | 无 | alert_id；FK operation.alert_event(id)；FK operation.alert_event(id,rule_id,rule_version) |
| `rule_id` | uuid | 是 | 无 | rule_id；FK operation.watch_rule_revision(rule_id,rule_version)；FK operation.alert_event(id,rule_id,rule_version) |
| `rule_version` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数；FK operation.watch_rule_revision(rule_id,rule_version)；FK operation.alert_event(id,rule_id,rule_version) |
| `recipient_id` | uuid | 否 | 无 | recipient_id；FK operation.notification_recipient(id) |
| `channel_type` | varchar(200) | 否 | 无 | channel_type |
| `channel_id` | uuid | 是 | 无 | channel_id；FK operation.notification_channel(id) |
| `template_code` | varchar(200) | 否 | 无 | template_code；FK operation.notification_template(template_code,template_version) |
| `template_version` | varchar(200) | 否 | 无 | template_version；FK operation.notification_template(template_code,template_version) |
| `task_name_snapshot` | varchar(200) | 否 | 无 | task_name_snapshot |
| `content_summary` | varchar(4000) | 否 | 无 | content_summary |
| `content_cipher` | bytea | 是 | 无 | content_cipher |
| `cipher_key_version` | varchar(200) | 是 | 无 | cipher_key_version |
| `status` | varchar(200) | 否 | 'PENDING' | status |
| `dedup_key` | varchar(200) | 否 | 无 | dedup_key |
| `retry_count` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `sent_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `acknowledged_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `provider_message_id` | varchar(200) | 是 | 无 | provider_message_id |
| `provider_receipt_ref` | varchar(500) | 是 | 无 | provider_receipt_ref |
| `error_code` | varchar(200) | 是 | 无 | error_code |
| `error_reason` | varchar(2000) | 是 | 无 | error_reason |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(dedup_key)`。

检查条件：
- `status IN ('PENDING','SENT','ACKNOWLEDGED','FAILED','UNKNOWN')`
- `channel_type IN ('SMS','EMAIL','PLATFORM')`
- `(plan_id IS NOT NULL AND plan_version IS NOT NULL AND execution_task_id IS NOT NULL) OR (alert_id IS NOT NULL AND rule_id IS NOT NULL AND rule_version IS NOT NULL)`
- `(content_cipher IS NULL)=(cipher_key_version IS NULL)`
- `retry_count >= 0`
- `plan_version >= 0`
- `rule_version >= 0`
- `(plan_id IS NOT NULL AND alert_id IS NULL AND rule_id IS NULL AND rule_version IS NULL) OR (alert_id IS NOT NULL AND plan_id IS NULL AND plan_version IS NULL AND execution_task_id IS NULL)`

## operation.delivery_attempt — 发送尝试/回执/失败原因

模块：M10。主键：`delivery_id,attempt_no`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `delivery_id` | uuid | 否 | 无 | delivery_id；PK；FK operation.delivery(id) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `requested_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `responded_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `status` | varchar(200) | 否 | 无 | status |
| `provider_receipt_ref` | varchar(500) | 是 | 无 | provider_receipt_ref |
| `error_code` | varchar(200) | 是 | 无 | error_code |
| `error_reason` | varchar(2000) | 是 | 无 | error_reason |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `status IN ('PENDING','SENT','ACKNOWLEDGED','FAILED','UNKNOWN')`
- `attempt_no>=1`
- `attempt_no >= 0`

## operation.platform_message — 平台消息送达内容

模块：M10。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `delivery_id` | uuid | 否 | 无 | delivery_id；FK operation.delivery(id) |
| `recipient_user_id` | uuid | 否 | 无 | recipient_user_id；FK auth.app_user(id) |
| `alert_id` | uuid | 是 | 无 | alert_id；FK operation.alert_event(id) |
| `plan_id` | uuid | 是 | 无 | plan_id；FK operation.notification_plan(id) |
| `title` | varchar(500) | 否 | 无 | title |
| `content` | text | 否 | 无 | content |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(delivery_id)`。

## operation.sms_modem — 短信猫设备目录

模块：M10。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `channel_id` | uuid | 否 | 无 | channel_id；FK operation.notification_channel(id) |
| `name` | varchar(200) | 否 | 无 | name |
| `serial_number` | varchar(200) | 否 | 无 | serial_number |
| `carrier_code` | varchar(200) | 是 | 无 | carrier_code |
| `enabled` | boolean | 否 | false | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(serial_number)`。

## operation.sms_modem_status — 短信猫完整遥测快照

模块：M13。主键：`modem_id,as_of`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `modem_id` | uuid | 否 | 无 | modem_id；PK；FK operation.sms_modem(id) |
| `as_of` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移；PK |
| `online` | boolean | 是 | 无 | online |
| `uptime_seconds` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `signal_quality` | numeric | 是 | 无 | signal_quality |
| `signal_strength` | numeric | 是 | 无 | signal_strength |
| `network_registration_count` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `sim_status` | varchar(200) | 是 | 无 | sim_status |
| `sent_success_count` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `sent_total_count` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `sim_balance_display` | varchar(200) | 是 | 无 | sim_balance_display |
| `collection_status` | varchar(200) | 否 | 无 | collection_status |
| `error_code` | varchar(200) | 是 | 无 | error_code |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `collection_status IN ('READY','STALE','UNAVAILABLE')`
- `sent_success_count IS NULL OR sent_total_count IS NULL OR sent_success_count<=sent_total_count`
- `network_registration_count >= 0`
- `sent_success_count >= 0`
- `sent_total_count >= 0`
- `uptime_seconds >= 0`

## core.metric_definition — 统一指标口径及刷新计划

模块：M04。主键：`metric_code,definition_version`。

待确认：P-006：源采集计数、归属分类、去重窗口及在线率统计口径需签认

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `metric_code` | varchar(200) | 否 | 无 | metric_code；PK |
| `definition_version` | varchar(200) | 否 | 无 | definition_version；PK |
| `name` | varchar(200) | 否 | 无 | name |
| `calculation_code` | varchar(200) | 否 | 无 | calculation_code |
| `unit` | varchar(200) | 否 | 无 | unit |
| `time_zone` | varchar(200) | 否 | 'Asia/Shanghai' | time_zone |
| `refresh_policy` | varchar(200) | 否 | 无 | refresh_policy |
| `source_kind` | varchar(200) | 否 | 无 | source_kind |
| `additive` | boolean | 否 | false | additive |
| `enabled` | boolean | 否 | false | enabled |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `refresh_policy IN ('POLL','ON_DEMAND','SNAPSHOT')`

## core.metric_scope — 有限、规范化的指标维度

模块：M04。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `scope_kind` | varchar(200) | 否 | 无 | scope_kind |
| `region_code` | varchar(200) | 是 | 无 | region_code；FK core.region(region_code) |
| `site_id` | uuid | 是 | 无 | site_id；FK core.site(id) |
| `device_id` | uuid | 是 | 无 | device_id；FK core.device(id) |
| `data_type` | varchar(200) | 是 | 无 | data_type |
| `device_category_code` | varchar(200) | 是 | 无 | device_category_code |
| `power_class_code` | varchar(200) | 是 | 无 | power_class_code |
| `carrier_code` | varchar(200) | 是 | 无 | carrier_code |
| `radio_type` | varchar(200) | 是 | 无 | radio_type |
| `origin_class` | varchar(200) | 是 | 无 | origin_class |
| `attribution_code` | varchar(200) | 是 | 无 | attribution_code |
| `operation_category` | varchar(200) | 是 | 无 | operation_category |
| `canonical_key` | varchar(1000) | 否 | 无 | canonical_key |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(canonical_key)`。

检查条件：
- `data_type IS NULL OR data_type IN ('FENCE','MAC','PLATE','FACE')`
- `origin_class IS NULL OR origin_class IN ('ALL','OUT_OF_PROVINCE','OVERSEAS')`

## core.metric_snapshot — 共享首页/运维/报表指标快照

模块：M04。主键：`metric_code,definition_version,scope_id,granularity,bucket_start`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `metric_code` | varchar(200) | 否 | 无 | metric_code；PK；FK core.metric_definition(metric_code,definition_version) |
| `definition_version` | varchar(200) | 否 | 无 | definition_version；PK；FK core.metric_definition(metric_code,definition_version) |
| `scope_id` | uuid | 否 | 无 | scope_id；PK；FK core.metric_scope(id) |
| `granularity` | varchar(200) | 否 | 无 | granularity；PK |
| `bucket_start` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移；PK |
| `bucket_end` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `value` | numeric(30,7) | 是 | 无 | value |
| `numerator` | numeric(30,7) | 是 | 无 | numerator |
| `denominator` | numeric(30,7) | 是 | 无 | denominator |
| `as_of` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `status` | varchar(200) | 否 | 无 | status |
| `error_code` | varchar(200) | 是 | 无 | error_code |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `granularity IN ('HOUR','DAY','MONTH','ALL')`
- `status IN ('READY','STALE','UNAVAILABLE')`
- `bucket_start<bucket_end`
- `status NOT IN ('UNAVAILABLE','FAILED') OR value IS NULL`
- `denominator IS NULL OR denominator>=0`

## core.metric_refresh_checkpoint — 增量汇总游标及失败隔离

模块：M04。主键：`metric_code,definition_version,source_key`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `metric_code` | varchar(200) | 否 | 无 | metric_code；PK；FK core.metric_definition(metric_code,definition_version) |
| `definition_version` | varchar(200) | 否 | 无 | definition_version；PK；FK core.metric_definition(metric_code,definition_version) |
| `source_key` | varchar(200) | 否 | 无 | source_key；PK |
| `checkpoint` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `updated_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `lease_owner` | varchar(200) | 是 | 无 | lease_owner |
| `lease_until` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `jsonb_typeof(checkpoint) IN ('object','array')`

## core.anomaly_event — 异常设备/数据/数据量事件

模块：M03。主键：`id`。

待确认：P-006/P-020：异常识别/时间/模式/增减阈值未定，不虚构触发条件

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `category_code` | varchar(200) | 否 | 无 | category_code |
| `device_id` | uuid | 是 | 无 | device_id；FK core.device(id) |
| `source_device_key` | varchar(200) | 是 | 无 | source_device_key |
| `ingestion_item_id` | uuid | 是 | 无 | ingestion_item_id；FK capture.ingestion_item(id) |
| `period_from` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `period_to` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `observed_value` | numeric | 是 | 无 | observed_value |
| `baseline_value` | numeric | 是 | 无 | baseline_value |
| `rule_version` | varchar(200) | 否 | 无 | rule_version |
| `detected_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `category_code IN ('UNREGISTERED_DEVICE','DUPLICATE_DEVICE_DATA','DEVICE_TIME_ANOMALY','DEVICE_MODE_ANOMALY','UNREGISTERED_DATA','DUPLICATE_DATA','DATA_TIME_ANOMALY','DATA_MODE_ANOMALY','DATA_FORMAT_ANOMALY','ONLINE_NO_DATA','DATA_SPIKE','DATA_DROP')`
- `period_from<period_to`

## core.component_status_snapshot — 受控系统组件状态

模块：M15。主键：`component_code,instance_code,checked_at`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `component_code` | varchar(200) | 否 | 无 | component_code；PK |
| `instance_code` | varchar(200) | 否 | 无 | instance_code；PK |
| `label` | varchar(200) | 否 | 无 | label |
| `status` | varchar(200) | 否 | 无 | status |
| `checked_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移；PK |
| `version_label` | varchar(200) | 是 | 无 | version_label |
| `failure_summary` | varchar(1000) | 是 | 无 | failure_summary |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `status IN ('UP','DOWN','DEGRADED','UNKNOWN')`

## operation.device_command_job — 批量命令任务及选择快照

模块：M12。主键：`task_id`。

待确认：P-018/P-019：设备参数/协议、复位语义、回执与高风险审批/确认待签认

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `task_id` | uuid | 否 | 无 | task_id；PK；FK analysis.async_task(id) |
| `name` | varchar(200) | 否 | 无 | name |
| `command_type` | varchar(200) | 否 | 无 | command_type |
| `group_id` | uuid | 是 | 无 | group_id；FK core.device_group(id) |
| `group_name_snapshot` | varchar(200) | 是 | 无 | group_name_snapshot |
| `region_code` | varchar(200) | 是 | 无 | region_code；FK core.region(region_code) |
| `selection_schema_version` | varchar(200) | 否 | '1' | selection_schema_version |
| `selection_snapshot` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `reason` | varchar(1000) | 否 | 无 | reason |
| `revised_from_task_id` | uuid | 是 | 无 | revised_from_task_id；FK operation.device_command_job(task_id) |
| `confirmation_record_id` | uuid | 是 | 无 | confirmation_record_id；FK audit.sensitive_action_review(id) |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `deleted` | boolean | 否 | false | deleted |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `command_type IN ('RESET','APPLY_PARAMETERS')`
- `jsonb_typeof(selection_snapshot) IN ('object','array')`
- `version >= 0`

## operation.device_command — 每台设备执行与回执

模块：M12。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `job_task_id` | uuid | 否 | 无 | job_task_id；FK operation.device_command_job(task_id) |
| `device_id` | uuid | 否 | 无 | device_id；FK core.device(id) |
| `device_name_snapshot` | varchar(200) | 否 | 无 | device_name_snapshot |
| `command_type` | varchar(200) | 否 | 无 | command_type |
| `parameter_schema_version` | varchar(200) | 否 | '1' | parameter_schema_version |
| `parameter_snapshot` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `status` | varchar(200) | 否 | 'PENDING' | status |
| `delivery_state` | varchar(200) | 否 | 'NOT_SENT' | delivery_state |
| `idempotency_key` | varchar(200) | 否 | 无 | idempotency_key |
| `expires_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `current_attempt_no` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `started_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `finished_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `acknowledgement_id` | varchar(200) | 是 | 无 | acknowledgement_id |
| `error_code` | varchar(200) | 是 | 无 | error_code |
| `error_message` | varchar(2000) | 是 | 无 | error_message |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(job_task_id,device_id)`；`(idempotency_key)`。

检查条件：
- `status IN ('PENDING','QUEUED','RUNNING','SUCCESS','FAILED','CANCELLED')`
- `command_type IN ('RESET','APPLY_PARAMETERS')`
- `delivery_state IN ('NOT_SENT','SENT','ACKNOWLEDGED','UNKNOWN')`
- `expires_at>created_at`
- `jsonb_typeof(parameter_snapshot) IN ('object','array')`
- `current_attempt_no >= 0`

## operation.device_command_attempt — 逐台命令尝试，成功项不重发

模块：M12。主键：`command_id,attempt_no`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `command_id` | uuid | 否 | 无 | command_id；PK；FK operation.device_command(id) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `requested_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `responded_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `status` | varchar(200) | 否 | 无 | status |
| `acknowledgement_id` | varchar(200) | 是 | 无 | acknowledgement_id |
| `error_code` | varchar(200) | 是 | 无 | error_code |
| `error_message` | varchar(2000) | 是 | 无 | error_message |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `status IN ('PENDING','QUEUED','RUNNING','SUCCESS','FAILED','CANCELLED')`
- `attempt_no>=1`
- `attempt_no >= 0`

## operation.report_job — 单报表/多报表ZIP任务

模块：M12。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `task_id` | uuid | 否 | 无 | task_id；FK analysis.async_task(id)；FK analysis.task_attempt(task_id,attempt_no) |
| `name` | varchar(200) | 否 | 无 | name |
| `mode` | varchar(200) | 否 | 无 | mode |
| `input_snapshot_id` | uuid | 否 | 无 | input_snapshot_id；FK analysis.input_snapshot(id) |
| `output_format` | varchar(200) | 是 | 无 | output_format |
| `output_file_id` | uuid | 是 | 无 | output_file_id；FK core.file_asset(id) |
| `completed_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `published_attempt_no` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数；FK analysis.task_attempt(task_id,attempt_no) |
| `version` | bigint | 否 | 0 | 非负计数或版本；不使用32位整型计数 |
| `deleted` | boolean | 否 | false | deleted |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(task_id)`。

检查条件：
- `mode IN ('SINGLE','BUNDLE')`
- `output_format IS NULL OR (mode='SINGLE' AND output_format='XLSX') OR (mode='BUNDLE' AND output_format='ZIP')`
- `version >= 0`
- `published_attempt_no >= 0`

## operation.report_definition — 报表定义/统计范围/口径版本

模块：M12。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `report_id` | uuid | 否 | 无 | report_id；FK operation.report_job(id) |
| `definition_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `report_type` | varchar(200) | 否 | 无 | report_type |
| `granularity` | varchar(200) | 否 | 无 | granularity |
| `period_start` | date | 否 | 无 | period_start |
| `period_end` | date | 是 | 无 | period_end |
| `region_code` | varchar(200) | 否 | 无 | region_code；FK core.region(region_code) |
| `scope_schema_version` | varchar(200) | 否 | '1' | scope_schema_version |
| `scope_snapshot` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `metric_definition_versions` | jsonb | 否 | 无 | 版本化契约快照；按指定 Schema 校验，不存自由业务实体 |
| `as_of` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `device_count` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `capture_count` | bigint | 是 | 无 | 非负计数或版本；不使用32位整型计数 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(report_id,definition_no)`。

检查条件：
- `report_type IN ('DEVICE_REALTIME_ONLINE_RATE','DEVICE_REALTIME_STATUS','FENCE_OPERATOR_STATISTICS','FENCE_AREA_CAPTURE','FENCE_DEVICE_CAPTURE','MAC_CAPTURE','PLATE_CAPTURE','SYSTEM_COMPONENT_STATUS')`
- `granularity IN ('DAY','MONTH','YEAR')`
- `period_end IS NULL OR period_start<period_end`
- `jsonb_typeof(scope_snapshot) IN ('object','array')`
- `jsonb_typeof(metric_definition_versions) IN ('object','array')`
- `device_count >= 0`
- `capture_count >= 0`
- `definition_no >= 0`

## operation.report_definition_site — 定义中的固定场所范围

模块：M12。主键：`definition_id,site_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `definition_id` | uuid | 否 | 无 | definition_id；PK；FK operation.report_definition(id) |
| `site_id` | uuid | 否 | 无 | site_id；PK；FK core.site(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## operation.report_device_row — 历史设备预览字段快照

模块：M12。主键：`definition_id,attempt_no,row_no`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `definition_id` | uuid | 否 | 无 | definition_id；PK；FK operation.report_definition(id) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `row_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `device_id` | uuid | 否 | 无 | device_id；FK core.device(id) |
| `serial_number` | varchar(200) | 否 | 无 | serial_number |
| `name` | varchar(200) | 否 | 无 | name |
| `site_id` | uuid | 否 | 无 | site_id；FK core.site(id) |
| `site_name` | varchar(200) | 否 | 无 | site_name |
| `region_code` | varchar(200) | 否 | 无 | region_code；FK core.region(region_code) |
| `category_code` | varchar(200) | 否 | 无 | category_code |
| `status` | varchar(200) | 否 | 无 | status |
| `last_seen_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `location` | geometry(Point,4326) | 是 | 无 | WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标 |
| `device_updated_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `device_version` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `group_ids_snapshot` | uuid[] | 否 | '{}'::uuid[] | group_ids_snapshot |
| `reset_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `capture_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `as_of` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `status IN ('ONLINE','OFFLINE','UNKNOWN','FAULT')`
- `location IS NULL OR (NOT ST_IsEmpty(location) AND ST_X(location) BETWEEN -180 AND 180 AND ST_Y(location) BETWEEN -90 AND 90)`
- `attempt_no >= 0`
- `reset_count >= 0`
- `capture_count >= 0`
- `row_no >= 0`
- `device_version >= 0`

## operation.report_region_row — 区域在线率预览快照

模块：M12。主键：`definition_id,attempt_no,region_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `definition_id` | uuid | 否 | 无 | definition_id；PK；FK operation.report_definition(id) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `region_code` | varchar(200) | 否 | 无 | region_code；PK；FK core.region(region_code) |
| `region_name` | varchar(200) | 否 | 无 | region_name |
| `device_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `online_count` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `online_rate` | numeric(8,7) | 否 | 无 | online_rate |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `online_count<=device_count`
- `online_rate BETWEEN 0 AND 1`
- `attempt_no >= 0`
- `device_count >= 0`
- `online_count >= 0`

## operation.report_metric_row — 报表摘要指标快照

模块：M12。主键：`definition_id,attempt_no,row_no`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `definition_id` | uuid | 否 | 无 | definition_id；PK；FK operation.report_definition(id) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `row_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `metric_code` | varchar(200) | 否 | 无 | metric_code；FK core.metric_definition(metric_code,definition_version) |
| `definition_version` | varchar(200) | 否 | 无 | definition_version；FK core.metric_definition(metric_code,definition_version) |
| `label` | varchar(200) | 否 | 无 | label |
| `value` | numeric(30,7) | 是 | 无 | value |
| `unit` | varchar(200) | 否 | 无 | unit |
| `status` | varchar(200) | 否 | 无 | status |
| `as_of` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `status IN ('READY','STALE','UNAVAILABLE')`
- `attempt_no >= 0`
- `row_no >= 0`

## operation.report_series — 报表序列维度，避免NULL复合主键

模块：M12。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `definition_id` | uuid | 否 | 无 | definition_id；FK operation.report_definition(id) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `metric_code` | varchar(200) | 否 | 无 | metric_code；FK core.metric_definition(metric_code,definition_version) |
| `definition_version` | varchar(200) | 否 | 无 | definition_version；FK core.metric_definition(metric_code,definition_version) |
| `carrier_code` | varchar(200) | 是 | 无 | carrier_code |
| `carrier_label` | varchar(200) | 是 | 无 | carrier_label |
| `device_id` | uuid | 是 | 无 | device_id；FK core.device(id) |
| `device_name_snapshot` | varchar(200) | 是 | 无 | device_name_snapshot |
| `region_code` | varchar(200) | 是 | 无 | region_code；FK core.region(region_code) |
| `series_key` | varchar(1000) | 否 | 无 | series_key |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(definition_id,attempt_no,series_key)`。

检查条件：
- `attempt_no >= 0`

## operation.report_series_point — 按时/日/月展开的报表数据点

模块：M12。主键：`series_id,bucket_start`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `series_id` | uuid | 否 | 无 | series_id；PK；FK operation.report_series(id) |
| `bucket_start` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移；PK |
| `bucket_end` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `value` | numeric(30,7) | 是 | 无 | value |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `bucket_start<bucket_end`

## operation.report_component_row — 报表组件状态快照

模块：M12。主键：`definition_id,attempt_no,component_code,instance_code`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `definition_id` | uuid | 否 | 无 | definition_id；PK；FK operation.report_definition(id) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数；PK |
| `component_code` | varchar(200) | 否 | 无 | component_code；PK |
| `instance_code` | varchar(200) | 否 | 无 | instance_code；PK |
| `label` | varchar(200) | 否 | 无 | label |
| `status` | varchar(200) | 否 | 无 | status |
| `checked_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `status IN ('UP','DOWN','DEGRADED','UNKNOWN')`
- `attempt_no >= 0`

## operation.report_output_file — 单文件/ZIP及成员文件引用

模块：M12。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `report_id` | uuid | 否 | 无 | report_id；FK operation.report_job(id) |
| `definition_id` | uuid | 是 | 无 | definition_id；FK operation.report_definition(id) |
| `attempt_no` | bigint | 否 | 无 | 非负计数或版本；不使用32位整型计数 |
| `file_asset_id` | uuid | 否 | 无 | file_asset_id；FK core.file_asset(id) |
| `output_role` | varchar(200) | 否 | 无 | output_role |
| `file_name` | varchar(255) | 否 | 无 | file_name |
| `format` | varchar(200) | 否 | 无 | format |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(report_id,attempt_no,file_asset_id)`。

检查条件：
- `output_role IN ('SINGLE','BUNDLE','MEMBER')`
- `format IN ('XLSX','ZIP')`
- `attempt_no >= 0`

## audit.audit_event — 操作/安全审计，主体快照保留

模块：M14。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `actor_user_id` | uuid | 是 | 无 | actor_user_id；FK auth.app_user(id) |
| `actor_login_name_snapshot` | varchar(200) | 否 | 无 | actor_login_name_snapshot |
| `actor_display_name_snapshot` | varchar(200) | 否 | 无 | actor_display_name_snapshot |
| `action` | varchar(200) | 否 | 无 | action |
| `action_category` | varchar(200) | 否 | 无 | action_category |
| `resource_type` | varchar(200) | 否 | 无 | resource_type |
| `resource_id` | uuid | 是 | 无 | resource_id |
| `occurred_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |
| `result` | varchar(200) | 否 | 无 | result |
| `failure_code` | varchar(200) | 是 | 无 | failure_code |
| `before_summary` | varchar(2000) | 是 | 无 | before_summary |
| `after_summary` | varchar(2000) | 是 | 无 | after_summary |
| `source_ip` | inet | 是 | 无 | source_ip |
| `business_task_id` | uuid | 是 | 无 | business_task_id；FK analysis.async_task(id) |
| `request_id` | varchar(200) | 是 | 无 | request_id |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `result IN ('SUCCESS','FAILURE')`

## operation.decryption_request — 解密申请与安全策略版本

模块：M06。主键：`id`。

待确认：P-008：涉密移交、审批、密钥、用途与明文生命周期未签认，能力默认关闭

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `requester_user_id` | uuid | 否 | 无 | requester_user_id；FK auth.app_user(id) |
| `recipient_user_id` | uuid | 是 | 无 | recipient_user_id；FK auth.app_user(id) |
| `context_id` | uuid | 否 | 无 | context_id；FK analysis.analysis_context(id) |
| `input_snapshot_id` | uuid | 否 | 无 | input_snapshot_id；FK analysis.input_snapshot(id) |
| `input_file_id` | uuid | 是 | 无 | input_file_id；FK core.file_asset(id) |
| `purpose` | varchar(1000) | 否 | 无 | purpose |
| `status` | varchar(200) | 否 | 'PENDING' | status |
| `policy_version` | varchar(200) | 否 | 无 | policy_version |
| `expires_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `status IN ('PENDING','APPROVED','REJECTED','EXPIRED')`
- `expires_at>created_at`

## operation.decryption_capture — 申请的原始采集范围

模块：M06。主键：`request_id,capture_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `request_id` | uuid | 否 | 无 | request_id；PK；FK operation.decryption_request(id) |
| `capture_id` | uuid | 否 | 无 | capture_id；PK；FK capture.capture_event(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## audit.sensitive_action_review — 配置策略产生的确认/审批事实

模块：M01。主键：`id`。

待确认：P-008/P-019：只记录已签认策略产生的实际确认/审批，不预设审批层级

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `action_type` | varchar(200) | 否 | 无 | action_type |
| `decryption_request_id` | uuid | 是 | 无 | decryption_request_id；FK operation.decryption_request(id) |
| `device_command_task_id` | uuid | 是 | 无 | device_command_task_id；FK operation.device_command_job(task_id) |
| `export_task_id` | uuid | 是 | 无 | export_task_id；FK analysis.export_job(task_id) |
| `actor_user_id` | uuid | 否 | 无 | actor_user_id；FK auth.app_user(id) |
| `decision` | varchar(200) | 否 | 无 | decision |
| `reason` | varchar(1000) | 否 | 无 | reason |
| `policy_version` | varchar(200) | 否 | 无 | policy_version |
| `occurred_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `trace_id` | varchar(200) | 否 | 无 | trace_id |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

检查条件：
- `num_nonnulls(decryption_request_id,device_command_task_id,export_task_id)=1`
- `decision IN ('CONFIRMED','APPROVED','REJECTED')`

## operation.decryption_result_grant — 仅指定涉密用户可短时取结果

模块：M06。主键：`id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | uuid | 否 | 无 | 应用生成的UUID业务主键；PK |
| `request_id` | uuid | 否 | 无 | request_id；FK operation.decryption_request(id) |
| `authorized_user_id` | uuid | 否 | 无 | authorized_user_id；FK auth.app_user(id) |
| `result_file_id` | uuid | 否 | 无 | result_file_id；FK core.file_asset(id) |
| `valid_from` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `expires_at` | timestamptz | 否 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `revoked_at` | timestamptz | 是 | 无 | UTC 时刻；API 输出 RFC3339 带偏移 |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

唯一约束：`(request_id,authorized_user_id,result_file_id)`。

检查条件：
- `valid_from<expires_at`

## analysis.collision_event_evidence — 每次碰撞自己的证据列表

模块：M08。主键：`collision_event_id,capture_id`。

| 字段 | SQL类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `collision_event_id` | uuid | 否 | 无 | collision_event_id；PK；FK analysis.collision_event(id) |
| `capture_id` | uuid | 否 | 无 | capture_id；PK；FK capture.capture_event(id) |
| `created_at` | timestamptz | 否 | now() | UTC 时刻；API 输出 RFC3339 带偏移 |

## PD-2026-10-18.v1增量

当前快照新增operation.action_approval、audit.action_approval_decision两表；字段/类型/约束以schema.sql末尾PD块为准。分别保存加密提案/摘要/状态/许可与独立复核事实，详细映射见SENSITIVE_ACTION_PROTOCOL。

operation.decryption_request新增approval_id UUID nullable/UNIQUE；operation.decryption_result_grant新增approval_id UUID nullable、consumed_at timestamptz nullable，result_file_id由非空改可空。新受控模式approval_id非空、result_file_id为空，不生成持久明文文件；旧记录迁移需人工审查。不是已经在服务器创建的表。
