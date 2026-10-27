-- 多维数据分析平台：DB-DESIGN-2 + PD-2026-10-18.v1；空库快照，不是原库升级脚本。

-- PostgreSQL 14+ / compatible PostGIS >=3.1.4 on PG14; use only PostGIS 3.0-era functions.

-- 不连接/修改生产数据；应用生成UUID；timestamptz存UTC；字典/区域/模板不虚构业务值。

BEGIN;

DO $$ BEGIN IF current_setting('server_version_num')::int<140000 THEN RAISE EXCEPTION 'PostgreSQL 14+ required'; END IF; END $$;

CREATE EXTENSION IF NOT EXISTS postgis;

DO $$ BEGIN IF split_part(postgis_lib_version(),'.',1)::int<3 OR postgis_lib_version() ~ '^3[.]0[.]' THEN RAISE EXCEPTION 'Use a PostGIS build compatible with PostgreSQL 14+, not PostGIS 3.0.x'; END IF; END $$;

CREATE SCHEMA IF NOT EXISTS auth;

CREATE SCHEMA IF NOT EXISTS core;

CREATE SCHEMA IF NOT EXISTS capture;

CREATE SCHEMA IF NOT EXISTS analysis;

CREATE SCHEMA IF NOT EXISTS operation;

CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE auth.app_user (
  id uuid NOT NULL,
  user_code varchar(200) NOT NULL,
  login_name varchar(200) NOT NULL,
  display_name varchar(200) NOT NULL,
  password_hash varchar(255) NOT NULL,
  status varchar(200) NOT NULL DEFAULT 'ACTIVE',
  secret_clearance varchar(200) NOT NULL DEFAULT 'STANDARD',
  authz_version bigint NOT NULL DEFAULT 0,
  last_login_at timestamptz,
  password_changed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE (user_code),
  UNIQUE (login_name),
  CHECK (status IN ('ACTIVE','DISABLED')),
  CHECK (NOT deleted OR status='DISABLED'),
  CHECK (version >= 0),
  CHECK (authz_version >= 0)
);

COMMENT ON TABLE auth.app_user IS 'M14 用户账号';

COMMENT ON COLUMN auth.app_user.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN auth.app_user.user_code IS 'user_code';

COMMENT ON COLUMN auth.app_user.login_name IS 'login_name';

COMMENT ON COLUMN auth.app_user.display_name IS 'display_name';

COMMENT ON COLUMN auth.app_user.password_hash IS 'password_hash';

COMMENT ON COLUMN auth.app_user.status IS 'status';

COMMENT ON COLUMN auth.app_user.secret_clearance IS 'secret_clearance';

COMMENT ON COLUMN auth.app_user.authz_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN auth.app_user.last_login_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.app_user.password_changed_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.app_user.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.app_user.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN auth.app_user.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.app_user.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN auth.app_user.deleted IS 'deleted';

COMMENT ON COLUMN auth.app_user.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE auth.role (
  id uuid NOT NULL,
  role_code varchar(200) NOT NULL,
  name varchar(200) NOT NULL,
  description varchar(1000),
  management_all_sites boolean NOT NULL DEFAULT false,
  data_all_sites boolean NOT NULL DEFAULT false,
  own_only boolean NOT NULL DEFAULT true,
  scope_policy_version varchar(200) NOT NULL DEFAULT 'PENDING-P002',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE (role_code),
  CHECK (version >= 0)
);

COMMENT ON TABLE auth.role IS 'M14 角色及范围标志；PENDING: P-002：多角色合并、继承与范围组合须产品/安全签认；保存配置，不自行开放权限';

COMMENT ON COLUMN auth.role.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN auth.role.role_code IS 'role_code';

COMMENT ON COLUMN auth.role.name IS 'name';

COMMENT ON COLUMN auth.role.description IS 'description';

COMMENT ON COLUMN auth.role.management_all_sites IS 'management_all_sites';

COMMENT ON COLUMN auth.role.data_all_sites IS 'data_all_sites';

COMMENT ON COLUMN auth.role.own_only IS 'own_only';

COMMENT ON COLUMN auth.role.scope_policy_version IS 'scope_policy_version';

COMMENT ON COLUMN auth.role.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.role.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN auth.role.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.role.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN auth.role.deleted IS 'deleted';

COMMENT ON COLUMN auth.role.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE auth.permission (
  code varchar(200) NOT NULL,
  name varchar(200) NOT NULL,
  module_code varchar(200) NOT NULL,
  description varchar(1000) NOT NULL,
  risk_level varchar(200) NOT NULL DEFAULT 'NORMAL',
  assignable boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (code),
  CHECK (risk_level IN ('NORMAL','SENSITIVE','HIGH'))
);

COMMENT ON TABLE auth.permission IS 'M01 与OpenAPI共用的权限目录';

COMMENT ON COLUMN auth.permission.code IS 'code';

COMMENT ON COLUMN auth.permission.name IS 'name';

COMMENT ON COLUMN auth.permission.module_code IS 'module_code';

COMMENT ON COLUMN auth.permission.description IS 'description';

COMMENT ON COLUMN auth.permission.risk_level IS 'risk_level';

COMMENT ON COLUMN auth.permission.assignable IS 'assignable';

COMMENT ON COLUMN auth.permission.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE auth.menu (
  id uuid NOT NULL,
  parent_id uuid,
  code varchar(200) NOT NULL,
  label varchar(200) NOT NULL,
  route varchar(240) NOT NULL,
  sort_order bigint NOT NULL DEFAULT 0,
  required_permission_code varchar(200),
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE (code),
  CHECK (version >= 0),
  CHECK (sort_order >= 0)
);

COMMENT ON TABLE auth.menu IS 'M02 菜单和应用入口目录';

COMMENT ON COLUMN auth.menu.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN auth.menu.parent_id IS 'parent_id；外键 auth.menu(id)；历史引用保护';

COMMENT ON COLUMN auth.menu.code IS 'code';

COMMENT ON COLUMN auth.menu.label IS 'label';

COMMENT ON COLUMN auth.menu.route IS 'route';

COMMENT ON COLUMN auth.menu.sort_order IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN auth.menu.required_permission_code IS 'required_permission_code；外键 auth.permission(code)；历史引用保护';

COMMENT ON COLUMN auth.menu.enabled IS 'enabled';

COMMENT ON COLUMN auth.menu.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.menu.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN auth.menu.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.menu.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN auth.menu.deleted IS 'deleted';

COMMENT ON COLUMN auth.menu.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE auth.user_role (
  user_id uuid NOT NULL,
  role_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id,role_id)
);

COMMENT ON TABLE auth.user_role IS 'M14 用户角色';

COMMENT ON COLUMN auth.user_role.user_id IS 'user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN auth.user_role.role_id IS 'role_id；外键 auth.role(id)；历史引用保护';

COMMENT ON COLUMN auth.user_role.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE auth.role_permission (
  role_id uuid NOT NULL,
  permission_code varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (role_id,permission_code)
);

COMMENT ON TABLE auth.role_permission IS 'M14 角色接口/按钮权限';

COMMENT ON COLUMN auth.role_permission.role_id IS 'role_id；外键 auth.role(id)；历史引用保护';

COMMENT ON COLUMN auth.role_permission.permission_code IS 'permission_code；外键 auth.permission(code)；历史引用保护';

COMMENT ON COLUMN auth.role_permission.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE auth.role_menu (
  role_id uuid NOT NULL,
  menu_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (role_id,menu_id)
);

COMMENT ON TABLE auth.role_menu IS 'M14 角色菜单';

COMMENT ON COLUMN auth.role_menu.role_id IS 'role_id；外键 auth.role(id)；历史引用保护';

COMMENT ON COLUMN auth.role_menu.menu_id IS 'menu_id；外键 auth.menu(id)；历史引用保护';

COMMENT ON COLUMN auth.role_menu.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE auth.role_site (
  role_id uuid NOT NULL,
  site_id uuid NOT NULL,
  scope_kind varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (role_id,site_id,scope_kind),
  CHECK (scope_kind IN ('MANAGEMENT','DATA'))
);

COMMENT ON TABLE auth.role_site IS 'M14 角色管理/采集场所范围';

COMMENT ON COLUMN auth.role_site.role_id IS 'role_id；外键 auth.role(id)；历史引用保护';

COMMENT ON COLUMN auth.role_site.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN auth.role_site.scope_kind IS 'scope_kind';

COMMENT ON COLUMN auth.role_site.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE auth.role_data_type (
  role_id uuid NOT NULL,
  data_type varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (role_id,data_type),
  CHECK (data_type IN ('FENCE','MAC','PLATE','FACE'))
);

COMMENT ON TABLE auth.role_data_type IS 'M14 角色数据类型范围';

COMMENT ON COLUMN auth.role_data_type.role_id IS 'role_id；外键 auth.role(id)；历史引用保护';

COMMENT ON COLUMN auth.role_data_type.data_type IS 'data_type';

COMMENT ON COLUMN auth.role_data_type.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE auth.user_scope (
  user_id uuid NOT NULL,
  management_all_sites boolean NOT NULL DEFAULT false,
  data_all_sites boolean NOT NULL DEFAULT false,
  own_only boolean NOT NULL DEFAULT true,
  scope_version bigint NOT NULL DEFAULT 0,
  policy_version varchar(200) NOT NULL DEFAULT 'PENDING-P002',
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id),
  CHECK (scope_version >= 0)
);

COMMENT ON TABLE auth.user_scope IS 'M14 用户请求范围，保存输入而非猜测合并策略；PENDING: P-002：与角色范围组合规则未签认；不得把客户端scopeVersion当服务端授权版本';

COMMENT ON COLUMN auth.user_scope.user_id IS 'user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN auth.user_scope.management_all_sites IS 'management_all_sites';

COMMENT ON COLUMN auth.user_scope.data_all_sites IS 'data_all_sites';

COMMENT ON COLUMN auth.user_scope.own_only IS 'own_only';

COMMENT ON COLUMN auth.user_scope.scope_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN auth.user_scope.policy_version IS 'policy_version';

COMMENT ON COLUMN auth.user_scope.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE auth.user_scope_site (
  user_id uuid NOT NULL,
  site_id uuid NOT NULL,
  scope_kind varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id,site_id,scope_kind),
  CHECK (scope_kind IN ('MANAGEMENT','DATA'))
);

COMMENT ON TABLE auth.user_scope_site IS 'M14 用户场所范围条目';

COMMENT ON COLUMN auth.user_scope_site.user_id IS 'user_id；外键 auth.user_scope(user_id)；历史引用保护';

COMMENT ON COLUMN auth.user_scope_site.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN auth.user_scope_site.scope_kind IS 'scope_kind';

COMMENT ON COLUMN auth.user_scope_site.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE auth.user_scope_data_type (
  user_id uuid NOT NULL,
  data_type varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id,data_type),
  CHECK (data_type IN ('FENCE','MAC','PLATE','FACE'))
);

COMMENT ON TABLE auth.user_scope_data_type IS 'M14 用户数据类型范围';

COMMENT ON COLUMN auth.user_scope_data_type.user_id IS 'user_id；外键 auth.user_scope(user_id)；历史引用保护';

COMMENT ON COLUMN auth.user_scope_data_type.data_type IS 'data_type';

COMMENT ON COLUMN auth.user_scope_data_type.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE auth.user_session (
  id uuid NOT NULL,
  user_id uuid NOT NULL,
  sid_hash bytea NOT NULL,
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  idle_expires_at timestamptz NOT NULL,
  absolute_expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  client_label varchar(200),
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (sid_hash),
  CHECK (idle_expires_at <= absolute_expires_at),
  CHECK (created_at < absolute_expires_at)
);

COMMENT ON TABLE auth.user_session IS 'M01 SID服务端会话';

COMMENT ON COLUMN auth.user_session.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN auth.user_session.user_id IS 'user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN auth.user_session.sid_hash IS 'sid_hash';

COMMENT ON COLUMN auth.user_session.last_seen_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.user_session.idle_expires_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.user_session.absolute_expires_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.user_session.revoked_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.user_session.client_label IS 'client_label';

COMMENT ON COLUMN auth.user_session.trace_id IS 'trace_id';

COMMENT ON COLUMN auth.user_session.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE auth.csrf_session (
  id uuid NOT NULL,
  session_id uuid,
  cookie_hash bytea NOT NULL,
  token_hash bytea NOT NULL,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (cookie_hash),
  CHECK (created_at < expires_at)
);

COMMENT ON TABLE auth.csrf_session IS 'M01 匿名/登录CSRF绑定';

COMMENT ON COLUMN auth.csrf_session.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN auth.csrf_session.session_id IS 'session_id；外键 auth.user_session(id)；历史引用保护';

COMMENT ON COLUMN auth.csrf_session.cookie_hash IS 'cookie_hash';

COMMENT ON COLUMN auth.csrf_session.token_hash IS 'token_hash';

COMMENT ON COLUMN auth.csrf_session.expires_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN auth.csrf_session.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.dictionary (
  code varchar(200) NOT NULL,
  name varchar(200) NOT NULL,
  catalog_version varchar(200) NOT NULL,
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (code)
);

COMMENT ON TABLE core.dictionary IS 'M01 字典版本';

COMMENT ON COLUMN core.dictionary.code IS 'code';

COMMENT ON COLUMN core.dictionary.name IS 'name';

COMMENT ON COLUMN core.dictionary.catalog_version IS 'catalog_version';

COMMENT ON COLUMN core.dictionary.enabled IS 'enabled';

COMMENT ON COLUMN core.dictionary.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.dictionary_item (
  dictionary_code varchar(200) NOT NULL,
  code varchar(200) NOT NULL,
  label varchar(200) NOT NULL,
  sort_order bigint NOT NULL DEFAULT 0,
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (dictionary_code,code),
  CHECK (sort_order >= 0)
);

COMMENT ON TABLE core.dictionary_item IS 'M01 字典条目';

COMMENT ON COLUMN core.dictionary_item.dictionary_code IS 'dictionary_code；外键 core.dictionary(code)；历史引用保护';

COMMENT ON COLUMN core.dictionary_item.code IS 'code';

COMMENT ON COLUMN core.dictionary_item.label IS 'label';

COMMENT ON COLUMN core.dictionary_item.sort_order IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN core.dictionary_item.enabled IS 'enabled';

COMMENT ON COLUMN core.dictionary_item.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.dictionary_binding (
  field_key varchar(200) NOT NULL,
  dictionary_code varchar(200) NOT NULL,
  label varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (field_key)
);

COMMENT ON TABLE core.dictionary_binding IS 'M01 API字段到字典的绑定';

COMMENT ON COLUMN core.dictionary_binding.field_key IS 'field_key';

COMMENT ON COLUMN core.dictionary_binding.dictionary_code IS 'dictionary_code；外键 core.dictionary(code)；历史引用保护';

COMMENT ON COLUMN core.dictionary_binding.label IS 'label';

COMMENT ON COLUMN core.dictionary_binding.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.config_item (
  config_key varchar(200) NOT NULL,
  schema_code varchar(200) NOT NULL,
  schema_version varchar(200) NOT NULL,
  config_version bigint NOT NULL DEFAULT 1,
  value jsonb NOT NULL,
  public_readable boolean NOT NULL DEFAULT false,
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (config_key),
  CHECK (jsonb_typeof(value)='object'),
  CHECK (jsonb_typeof(value) IN ('object','array')),
  CHECK (config_version >= 0)
);

COMMENT ON TABLE core.config_item IS 'M01 非机密前端配置';

COMMENT ON COLUMN core.config_item.config_key IS 'config_key';

COMMENT ON COLUMN core.config_item.schema_code IS 'schema_code';

COMMENT ON COLUMN core.config_item.schema_version IS 'schema_version';

COMMENT ON COLUMN core.config_item.config_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN core.config_item.value IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN core.config_item.public_readable IS 'public_readable';

COMMENT ON COLUMN core.config_item.enabled IS 'enabled';

COMMENT ON COLUMN core.config_item.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.feature_config (
  feature_code varchar(200) NOT NULL,
  enabled boolean NOT NULL DEFAULT false,
  pending_reason text,
  policy_version varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (feature_code),
  CHECK (enabled OR pending_reason IS NOT NULL)
);

COMMENT ON TABLE core.feature_config IS 'M01 待确认能力开关';

COMMENT ON COLUMN core.feature_config.feature_code IS 'feature_code';

COMMENT ON COLUMN core.feature_config.enabled IS 'enabled';

COMMENT ON COLUMN core.feature_config.pending_reason IS 'pending_reason';

COMMENT ON COLUMN core.feature_config.policy_version IS 'policy_version';

COMMENT ON COLUMN core.feature_config.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.upload_policy (
  category varchar(200) NOT NULL,
  max_bytes bigint NOT NULL,
  allowed_mime_types text[] NOT NULL,
  allowed_extensions text[] NOT NULL,
  enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (category),
  CHECK (max_bytes > 0),
  CHECK (cardinality(allowed_mime_types)>0),
  CHECK (cardinality(allowed_extensions)>0),
  CHECK (max_bytes >= 0)
);

COMMENT ON TABLE core.upload_policy IS 'M01 上传白名单';

COMMENT ON COLUMN core.upload_policy.category IS 'category';

COMMENT ON COLUMN core.upload_policy.max_bytes IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN core.upload_policy.allowed_mime_types IS 'allowed_mime_types';

COMMENT ON COLUMN core.upload_policy.allowed_extensions IS 'allowed_extensions';

COMMENT ON COLUMN core.upload_policy.enabled IS 'enabled';

COMMENT ON COLUMN core.upload_policy.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.import_template (
  template_code varchar(200) NOT NULL,
  template_version varchar(200) NOT NULL,
  formats text[] NOT NULL,
  transaction_policy varchar(200) NOT NULL DEFAULT 'ALL_OR_NOTHING',
  encoding varchar(200) NOT NULL DEFAULT 'UTF-8',
  description text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (template_code,template_version),
  CHECK (transaction_policy='ALL_OR_NOTHING'),
  CHECK (encoding='UTF-8')
);

COMMENT ON TABLE core.import_template IS 'M01 版本化导入模板';

COMMENT ON COLUMN core.import_template.template_code IS 'template_code';

COMMENT ON COLUMN core.import_template.template_version IS 'template_version';

COMMENT ON COLUMN core.import_template.formats IS 'formats';

COMMENT ON COLUMN core.import_template.transaction_policy IS 'transaction_policy';

COMMENT ON COLUMN core.import_template.encoding IS 'encoding';

COMMENT ON COLUMN core.import_template.description IS 'description';

COMMENT ON COLUMN core.import_template.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.import_column (
  template_code varchar(200) NOT NULL,
  template_version varchar(200) NOT NULL,
  column_name varchar(200) NOT NULL,
  label varchar(200) NOT NULL,
  sort_order bigint NOT NULL DEFAULT 0,
  required boolean NOT NULL DEFAULT false,
  value_type varchar(200) NOT NULL,
  description text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (template_code,template_version,column_name),
  CHECK (value_type IN ('STRING','UUID','INTEGER','BOOLEAN','DATE_TIME')),
  CHECK (sort_order >= 0)
);

COMMENT ON TABLE core.import_column IS 'M01 导入列定义';

COMMENT ON COLUMN core.import_column.template_code IS 'template_code；外键 core.import_template(template_code,template_version)；历史引用保护';

COMMENT ON COLUMN core.import_column.template_version IS 'template_version；外键 core.import_template(template_code,template_version)；历史引用保护';

COMMENT ON COLUMN core.import_column.column_name IS 'column_name';

COMMENT ON COLUMN core.import_column.label IS 'label';

COMMENT ON COLUMN core.import_column.sort_order IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN core.import_column.required IS 'required';

COMMENT ON COLUMN core.import_column.value_type IS 'value_type';

COMMENT ON COLUMN core.import_column.description IS 'description';

COMMENT ON COLUMN core.import_column.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.region (
  region_code varchar(200) NOT NULL,
  parent_region_code varchar(200),
  name varchar(200) NOT NULL,
  boundary geometry(MultiPolygon,4326),
  boundary_version varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (region_code),
  CHECK (boundary IS NULL OR (NOT ST_IsEmpty(boundary) AND ST_IsValid(boundary) AND ST_CoveredBy(boundary,ST_MakeEnvelope(-180,-90,180,90,4326))))
);

COMMENT ON TABLE core.region IS 'M07 行政区与地图边界';

COMMENT ON COLUMN core.region.region_code IS 'region_code';

COMMENT ON COLUMN core.region.parent_region_code IS 'parent_region_code；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN core.region.name IS 'name';

COMMENT ON COLUMN core.region.boundary IS 'WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标';

COMMENT ON COLUMN core.region.boundary_version IS 'boundary_version';

COMMENT ON COLUMN core.region.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.site (
  id uuid NOT NULL,
  site_code varchar(200) NOT NULL,
  name varchar(200) NOT NULL,
  region_code varchar(200) NOT NULL,
  address varchar(500),
  location geometry(Point,4326),
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE (site_code),
  CHECK (location IS NULL OR (NOT ST_IsEmpty(location) AND ST_X(location) BETWEEN -180 AND 180 AND ST_Y(location) BETWEEN -90 AND 90)),
  CHECK (version >= 0)
);

COMMENT ON TABLE core.site IS 'M12 场所';

COMMENT ON COLUMN core.site.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN core.site.site_code IS 'site_code';

COMMENT ON COLUMN core.site.name IS 'name';

COMMENT ON COLUMN core.site.region_code IS 'region_code；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN core.site.address IS 'address';

COMMENT ON COLUMN core.site.location IS 'WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标';

COMMENT ON COLUMN core.site.enabled IS 'enabled';

COMMENT ON COLUMN core.site.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.site.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN core.site.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.site.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN core.site.deleted IS 'deleted';

COMMENT ON COLUMN core.site.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE core.device (
  id uuid NOT NULL,
  serial_number varchar(200) NOT NULL,
  name varchar(200) NOT NULL,
  site_id uuid NOT NULL,
  category_code varchar(200) NOT NULL,
  power_class_code varchar(200),
  manufacturer varchar(200),
  model varchar(200),
  protocol_version varchar(200),
  location geometry(Point,4326),
  installed_at timestamptz,
  status varchar(200) NOT NULL DEFAULT 'UNKNOWN',
  status_since timestamptz,
  last_seen_at timestamptz,
  parameter_version bigint NOT NULL DEFAULT 0,
  reset_count bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE (serial_number),
  CHECK (status IN ('ONLINE','OFFLINE','UNKNOWN','FAULT')),
  CHECK (location IS NULL OR (NOT ST_IsEmpty(location) AND ST_X(location) BETWEEN -180 AND 180 AND ST_Y(location) BETWEEN -90 AND 90)),
  CHECK (reset_count >= 0),
  CHECK (version >= 0),
  CHECK (parameter_version >= 0)
);

COMMENT ON TABLE core.device IS 'M12 设备及最新状态';

COMMENT ON COLUMN core.device.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN core.device.serial_number IS 'serial_number';

COMMENT ON COLUMN core.device.name IS 'name';

COMMENT ON COLUMN core.device.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN core.device.category_code IS 'category_code';

COMMENT ON COLUMN core.device.power_class_code IS 'power_class_code';

COMMENT ON COLUMN core.device.manufacturer IS 'manufacturer';

COMMENT ON COLUMN core.device.model IS 'model';

COMMENT ON COLUMN core.device.protocol_version IS 'protocol_version';

COMMENT ON COLUMN core.device.location IS 'WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标';

COMMENT ON COLUMN core.device.installed_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.device.status IS 'status';

COMMENT ON COLUMN core.device.status_since IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.device.last_seen_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.device.parameter_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN core.device.reset_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN core.device.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.device.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN core.device.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.device.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN core.device.deleted IS 'deleted';

COMMENT ON COLUMN core.device.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE core.device_group (
  id uuid NOT NULL,
  site_id uuid NOT NULL,
  name varchar(200) NOT NULL,
  description varchar(1000),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE (site_id,name),
  CHECK (version >= 0)
);

COMMENT ON TABLE core.device_group IS 'M12 设备分组';

COMMENT ON COLUMN core.device_group.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN core.device_group.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN core.device_group.name IS 'name';

COMMENT ON COLUMN core.device_group.description IS 'description';

COMMENT ON COLUMN core.device_group.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.device_group.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN core.device_group.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.device_group.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN core.device_group.deleted IS 'deleted';

COMMENT ON COLUMN core.device_group.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE core.device_group_member (
  group_id uuid NOT NULL,
  device_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (group_id,device_id)
);

COMMENT ON TABLE core.device_group_member IS 'M12 设备分组成员';

COMMENT ON COLUMN core.device_group_member.group_id IS 'group_id；外键 core.device_group(id)；历史引用保护';

COMMENT ON COLUMN core.device_group_member.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN core.device_group_member.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.device_revision (
  id uuid NOT NULL,
  device_id uuid NOT NULL,
  revision_no bigint NOT NULL,
  site_id uuid NOT NULL,
  metadata_snapshot jsonb NOT NULL,
  effective_at timestamptz NOT NULL,
  actor_user_id uuid,
  reason varchar(1000) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (device_id,revision_no),
  CHECK (revision_no >= 0),
  CHECK (jsonb_typeof(metadata_snapshot) IN ('object','array'))
);

COMMENT ON TABLE core.device_revision IS 'M12 设备元信息历史，采集归属不随设备修改漂移';

COMMENT ON COLUMN core.device_revision.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN core.device_revision.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN core.device_revision.revision_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN core.device_revision.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN core.device_revision.metadata_snapshot IS 'DeviceInput + 当前运维元信息契约快照；脱敏、版本化';

COMMENT ON COLUMN core.device_revision.effective_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.device_revision.actor_user_id IS 'actor_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN core.device_revision.reason IS 'reason';

COMMENT ON COLUMN core.device_revision.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.device_status_event (
  id uuid NOT NULL,
  device_id uuid NOT NULL,
  previous_status varchar(200) NOT NULL,
  status varchar(200) NOT NULL,
  occurred_at timestamptz NOT NULL,
  source varchar(200) NOT NULL,
  reason varchar(1000),
  policy_version varchar(200),
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (previous_status IN ('ONLINE','OFFLINE','UNKNOWN','FAULT')),
  CHECK (status IN ('ONLINE','OFFLINE','UNKNOWN','FAULT'))
);

COMMENT ON TABLE core.device_status_event IS 'M12 在线/离线/故障状态变化';

COMMENT ON COLUMN core.device_status_event.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN core.device_status_event.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN core.device_status_event.previous_status IS 'previous_status';

COMMENT ON COLUMN core.device_status_event.status IS 'status';

COMMENT ON COLUMN core.device_status_event.occurred_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.device_status_event.source IS 'source';

COMMENT ON COLUMN core.device_status_event.reason IS 'reason';

COMMENT ON COLUMN core.device_status_event.policy_version IS 'policy_version';

COMMENT ON COLUMN core.device_status_event.trace_id IS 'trace_id';

COMMENT ON COLUMN core.device_status_event.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.device_parameter_definition (
  id uuid NOT NULL,
  manufacturer varchar(200) NOT NULL,
  model varchar(200) NOT NULL,
  protocol_version varchar(200) NOT NULL,
  parameter_code varchar(200) NOT NULL,
  label varchar(200) NOT NULL,
  value_type varchar(200) NOT NULL,
  unit varchar(200),
  minimum numeric,
  maximum numeric,
  allowed_values jsonb,
  read_only boolean NOT NULL DEFAULT true,
  enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (manufacturer,model,protocol_version,parameter_code),
  UNIQUE (id,value_type),
  CHECK (value_type IN ('TEXT','INTEGER','NUMBER','BOOLEAN')),
  CHECK (minimum IS NULL OR maximum IS NULL OR minimum<=maximum),
  CHECK (jsonb_typeof(allowed_values) IN ('object','array')),
  CHECK (allowed_values IS NULL OR jsonb_typeof(allowed_values)='array')
);

COMMENT ON TABLE core.device_parameter_definition IS 'M12 厂商参数描述，不虚构协议；PENDING: P-018/P-019：参数代码、范围、厂商协议、回执与高风险确认签认后启用';

COMMENT ON COLUMN core.device_parameter_definition.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN core.device_parameter_definition.manufacturer IS 'manufacturer';

COMMENT ON COLUMN core.device_parameter_definition.model IS 'model';

COMMENT ON COLUMN core.device_parameter_definition.protocol_version IS 'protocol_version';

COMMENT ON COLUMN core.device_parameter_definition.parameter_code IS 'parameter_code';

COMMENT ON COLUMN core.device_parameter_definition.label IS 'label';

COMMENT ON COLUMN core.device_parameter_definition.value_type IS 'value_type';

COMMENT ON COLUMN core.device_parameter_definition.unit IS 'unit';

COMMENT ON COLUMN core.device_parameter_definition.minimum IS 'minimum';

COMMENT ON COLUMN core.device_parameter_definition.maximum IS 'maximum';

COMMENT ON COLUMN core.device_parameter_definition.allowed_values IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN core.device_parameter_definition.read_only IS 'read_only';

COMMENT ON COLUMN core.device_parameter_definition.enabled IS 'enabled';

COMMENT ON COLUMN core.device_parameter_definition.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.device_parameter_value (
  id uuid NOT NULL,
  device_id uuid NOT NULL,
  definition_id uuid NOT NULL,
  value_type varchar(200) NOT NULL,
  parameter_version bigint NOT NULL,
  value_kind varchar(200) NOT NULL,
  text_value text,
  integer_value bigint,
  number_value numeric,
  boolean_value boolean,
  command_id uuid,
  observed_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (device_id,definition_id,value_kind,parameter_version),
  CHECK (value_kind IN ('DESIRED','REPORTED')),
  CHECK (num_nonnulls(text_value,integer_value,number_value,boolean_value)=1 AND ((value_type='TEXT' AND text_value IS NOT NULL) OR (value_type IN ('INTEGER','DURATION_SECONDS') AND integer_value IS NOT NULL) OR (value_type IN ('NUMBER','DISTANCE_METERS') AND number_value IS NOT NULL) OR (value_type='BOOLEAN' AND boolean_value IS NOT NULL))),
  CHECK (parameter_version >= 0)
);

COMMENT ON TABLE core.device_parameter_value IS 'M12 期望/设备上报参数，未回执不覆盖实际值';

COMMENT ON COLUMN core.device_parameter_value.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN core.device_parameter_value.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN core.device_parameter_value.definition_id IS 'definition_id；外键 core.device_parameter_definition(id,value_type)；历史引用保护';

COMMENT ON COLUMN core.device_parameter_value.value_type IS 'value_type；外键 core.device_parameter_definition(id,value_type)；历史引用保护';

COMMENT ON COLUMN core.device_parameter_value.parameter_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN core.device_parameter_value.value_kind IS 'value_kind';

COMMENT ON COLUMN core.device_parameter_value.text_value IS 'text_value';

COMMENT ON COLUMN core.device_parameter_value.integer_value IS 'integer_value';

COMMENT ON COLUMN core.device_parameter_value.number_value IS 'number_value';

COMMENT ON COLUMN core.device_parameter_value.boolean_value IS 'boolean_value';

COMMENT ON COLUMN core.device_parameter_value.command_id IS 'command_id；外键 operation.device_command(id)；历史引用保护';

COMMENT ON COLUMN core.device_parameter_value.observed_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.device_parameter_value.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.file_asset (
  id uuid NOT NULL,
  storage_key varchar(500) NOT NULL,
  file_name varchar(255) NOT NULL,
  category varchar(200) NOT NULL,
  mime_type varchar(200) NOT NULL,
  byte_size bigint NOT NULL,
  sha256 char(64) NOT NULL,
  owner_user_id uuid,
  security_class varchar(200) NOT NULL DEFAULT 'L3',
  status varchar(200) NOT NULL DEFAULT 'PROCESSING',
  expires_at timestamptz,
  error_code varchar(200),
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (storage_key),
  CHECK (byte_size>=0),
  CHECK (sha256 ~ '^[0-9a-f]{64}$'),
  CHECK (status IN ('PROCESSING','ACTIVE','QUARANTINED','DELETED')),
  CHECK (byte_size >= 0)
);

COMMENT ON TABLE core.file_asset IS 'M01 受控文件元数据；内容不入库';

COMMENT ON COLUMN core.file_asset.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN core.file_asset.storage_key IS 'storage_key';

COMMENT ON COLUMN core.file_asset.file_name IS 'file_name';

COMMENT ON COLUMN core.file_asset.category IS 'category';

COMMENT ON COLUMN core.file_asset.mime_type IS 'mime_type';

COMMENT ON COLUMN core.file_asset.byte_size IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN core.file_asset.sha256 IS 'sha256';

COMMENT ON COLUMN core.file_asset.owner_user_id IS 'owner_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN core.file_asset.security_class IS 'security_class';

COMMENT ON COLUMN core.file_asset.status IS 'status';

COMMENT ON COLUMN core.file_asset.expires_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.file_asset.error_code IS 'error_code';

COMMENT ON COLUMN core.file_asset.trace_id IS 'trace_id';

COMMENT ON COLUMN core.file_asset.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.idempotency_request (
  id uuid NOT NULL,
  user_id uuid NOT NULL,
  operation_id varchar(200) NOT NULL,
  idempotency_key varchar(200) NOT NULL,
  request_digest char(64) NOT NULL,
  status varchar(200) NOT NULL DEFAULT 'PROCESSING',
  response_schema_version varchar(200),
  response_status integer,
  response_snapshot jsonb,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (user_id,operation_id,idempotency_key),
  CHECK (status IN ('PROCESSING','SUCCEEDED','FAILED')),
  CHECK (response_status IS NULL OR response_status BETWEEN 200 AND 599),
  CHECK (jsonb_typeof(response_snapshot) IN ('object','array'))
);

COMMENT ON TABLE core.idempotency_request IS 'M01 请求级幂等账本';

COMMENT ON COLUMN core.idempotency_request.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN core.idempotency_request.user_id IS 'user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN core.idempotency_request.operation_id IS 'operation_id';

COMMENT ON COLUMN core.idempotency_request.idempotency_key IS 'idempotency_key';

COMMENT ON COLUMN core.idempotency_request.request_digest IS 'request_digest';

COMMENT ON COLUMN core.idempotency_request.status IS 'status';

COMMENT ON COLUMN core.idempotency_request.response_schema_version IS 'response_schema_version';

COMMENT ON COLUMN core.idempotency_request.response_status IS 'response_status';

COMMENT ON COLUMN core.idempotency_request.response_snapshot IS '小型脱敏响应快照，仅ID/版本/状态；不缓存检索明细或明文';

COMMENT ON COLUMN core.idempotency_request.expires_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.idempotency_request.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.outbox_event (
  id uuid NOT NULL,
  event_type varchar(200) NOT NULL,
  aggregate_type varchar(200) NOT NULL,
  aggregate_id uuid NOT NULL,
  schema_version varchar(200) NOT NULL,
  payload jsonb NOT NULL,
  manifest_file_id uuid,
  status varchar(200) NOT NULL DEFAULT 'PENDING',
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  attempt_count bigint NOT NULL DEFAULT 0,
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED')),
  CHECK (jsonb_typeof(payload) IN ('object','array')),
  CHECK (attempt_count >= 0)
);

COMMENT ON TABLE core.outbox_event IS 'M01 事务内业务事件，仅批次/告警等必要事件';

COMMENT ON COLUMN core.outbox_event.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN core.outbox_event.event_type IS 'event_type';

COMMENT ON COLUMN core.outbox_event.aggregate_type IS 'aggregate_type';

COMMENT ON COLUMN core.outbox_event.aggregate_id IS 'aggregate_id';

COMMENT ON COLUMN core.outbox_event.schema_version IS 'schema_version';

COMMENT ON COLUMN core.outbox_event.payload IS '经事件Schema校验的小型引用载荷；大清单用受控manifest文件';

COMMENT ON COLUMN core.outbox_event.manifest_file_id IS 'manifest_file_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN core.outbox_event.status IS 'status';

COMMENT ON COLUMN core.outbox_event.next_attempt_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.outbox_event.attempt_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN core.outbox_event.trace_id IS 'trace_id';

COMMENT ON COLUMN core.outbox_event.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.consumer_inbox (
  consumer_code varchar(200) NOT NULL,
  event_id uuid NOT NULL,
  consumed_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer_code,event_id)
);

COMMENT ON TABLE core.consumer_inbox IS 'M01 事件消费去重';

COMMENT ON COLUMN core.consumer_inbox.consumer_code IS 'consumer_code';

COMMENT ON COLUMN core.consumer_inbox.event_id IS 'event_id；外键 core.outbox_event(id)；历史引用保护';

COMMENT ON COLUMN core.consumer_inbox.consumed_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.consumer_inbox.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.analysis_context (
  id uuid NOT NULL,
  owner_user_id uuid NOT NULL,
  source_module varchar(200) NOT NULL,
  filter_type varchar(200) NOT NULL,
  filter_schema_version bigint NOT NULL DEFAULT 1,
  filter_snapshot jsonb NOT NULL,
  spatial_selection_type varchar(200),
  spatial_geometry geometry(Geometry,4326),
  permission_version bigint NOT NULL,
  expires_at timestamptz NOT NULL,
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (source_module IN ('M04','M05','M06','M07','M08','M09','M10','M11','M12','M13')),
  CHECK (filter_type IN ('GENERAL','CAPTURE_SEARCH','DATASET','ANALYSIS_RESULTS','PROFILE')),
  CHECK (spatial_selection_type IS NULL OR spatial_selection_type IN ('CAPTURE','DEVICE','AREA')),
  CHECK (expires_at > created_at),
  CHECK (jsonb_typeof(filter_snapshot) IN ('object','array')),
  CHECK (filter_schema_version >= 0),
  CHECK (permission_version >= 0),
  CHECK (spatial_geometry IS NULL OR (NOT ST_IsEmpty(spatial_geometry) AND ST_IsValid(spatial_geometry) AND ST_CoveredBy(spatial_geometry,ST_MakeEnvelope(-180,-90,180,90,4326)))),
  CHECK (spatial_geometry IS NULL OR GeometryType(spatial_geometry) IN ('POLYGON','MULTIPOLYGON')),
  CHECK ((spatial_geometry IS NULL)=(spatial_selection_type IS NULL))
);

COMMENT ON TABLE analysis.analysis_context IS 'M06 跨页查询/目标上下文';

COMMENT ON COLUMN analysis.analysis_context.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.analysis_context.owner_user_id IS 'owner_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_context.source_module IS 'source_module';

COMMENT ON COLUMN analysis.analysis_context.filter_type IS 'filter_type';

COMMENT ON COLUMN analysis.analysis_context.filter_schema_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.analysis_context.filter_snapshot IS 'ContextFilter五分支，敏感输入转目标引用或加密保存；不复制采集结果';

COMMENT ON COLUMN analysis.analysis_context.spatial_selection_type IS 'spatial_selection_type';

COMMENT ON COLUMN analysis.analysis_context.spatial_geometry IS 'WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标';

COMMENT ON COLUMN analysis.analysis_context.permission_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.analysis_context.expires_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.analysis_context.trace_id IS 'trace_id';

COMMENT ON COLUMN analysis.analysis_context.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.context_target (
  context_id uuid NOT NULL,
  target_id uuid NOT NULL,
  target_type varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (context_id,target_id)
);

COMMENT ON TABLE analysis.context_target IS 'M06 上下文选择目标';

COMMENT ON COLUMN analysis.context_target.context_id IS 'context_id；外键 analysis.analysis_context(id)；历史引用保护';

COMMENT ON COLUMN analysis.context_target.target_id IS 'target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.context_target.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.context_target.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.input_snapshot (
  id uuid NOT NULL,
  owner_user_id uuid NOT NULL,
  source_kind varchar(200) NOT NULL,
  source_schema_version varchar(200) NOT NULL,
  filter_snapshot jsonb NOT NULL,
  snapshot_digest char(64),
  snapshot_at timestamptz,
  status varchar(200) NOT NULL DEFAULT 'PREPARING',
  storage_mode varchar(200) NOT NULL,
  manifest_file_id uuid,
  capture_count bigint,
  target_count bigint,
  permission_version bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (status IN ('PREPARING','READY','FAILED')),
  CHECK (storage_mode IN ('REFERENCES','MANIFEST')),
  CHECK (status!='READY' OR (snapshot_at IS NOT NULL AND snapshot_digest IS NOT NULL)),
  CHECK (status!='READY' OR storage_mode!='MANIFEST' OR manifest_file_id IS NOT NULL),
  CHECK (jsonb_typeof(filter_snapshot) IN ('object','array')),
  CHECK (capture_count >= 0),
  CHECK (target_count >= 0),
  CHECK (permission_version >= 0)
);

COMMENT ON TABLE analysis.input_snapshot IS 'M01 任务输入冻结，复用版本引用或文件manifest';

COMMENT ON COLUMN analysis.input_snapshot.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.input_snapshot.owner_user_id IS 'owner_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.input_snapshot.source_kind IS 'source_kind';

COMMENT ON COLUMN analysis.input_snapshot.source_schema_version IS 'source_schema_version';

COMMENT ON COLUMN analysis.input_snapshot.filter_snapshot IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN analysis.input_snapshot.snapshot_digest IS 'snapshot_digest';

COMMENT ON COLUMN analysis.input_snapshot.snapshot_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.input_snapshot.status IS 'status';

COMMENT ON COLUMN analysis.input_snapshot.storage_mode IS 'storage_mode';

COMMENT ON COLUMN analysis.input_snapshot.manifest_file_id IS 'manifest_file_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN analysis.input_snapshot.capture_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.input_snapshot.target_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.input_snapshot.permission_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.input_snapshot.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.input_snapshot_reference (
  snapshot_id uuid NOT NULL,
  sequence_no bigint NOT NULL,
  source_kind varchar(200) NOT NULL,
  capture_id uuid,
  target_id uuid,
  dataset_id uuid,
  dataset_version bigint,
  analysis_result_id uuid,
  profile_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (snapshot_id,sequence_no),
  CHECK (num_nonnulls(capture_id,target_id,dataset_id,analysis_result_id,profile_id)=1),
  CHECK ((dataset_id IS NULL)=(dataset_version IS NULL)),
  CHECK (sequence_no >= 0),
  CHECK (dataset_version >= 0)
);

COMMENT ON TABLE analysis.input_snapshot_reference IS 'M01 固定的版本/结果/显式采集输入；大结果不用逐条复制';

COMMENT ON COLUMN analysis.input_snapshot_reference.snapshot_id IS 'snapshot_id；外键 analysis.input_snapshot(id)；历史引用保护';

COMMENT ON COLUMN analysis.input_snapshot_reference.sequence_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.input_snapshot_reference.source_kind IS 'source_kind';

COMMENT ON COLUMN analysis.input_snapshot_reference.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN analysis.input_snapshot_reference.target_id IS 'target_id；外键 capture.target(id)；历史引用保护';

COMMENT ON COLUMN analysis.input_snapshot_reference.dataset_id IS 'dataset_id；外键 analysis.dataset_version(dataset_id,version_no)；历史引用保护';

COMMENT ON COLUMN analysis.input_snapshot_reference.dataset_version IS '非负计数或版本；不使用32位整型计数；外键 analysis.dataset_version(dataset_id,version_no)；历史引用保护';

COMMENT ON COLUMN analysis.input_snapshot_reference.analysis_result_id IS 'analysis_result_id；外键 analysis.analysis_result(id)；历史引用保护';

COMMENT ON COLUMN analysis.input_snapshot_reference.profile_id IS 'profile_id；外键 analysis.profile(id)；历史引用保护';

COMMENT ON COLUMN analysis.input_snapshot_reference.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.async_task (
  id uuid NOT NULL,
  name varchar(200) NOT NULL,
  task_type varchar(200) NOT NULL,
  owner_user_id uuid NOT NULL,
  context_id uuid,
  input_snapshot_id uuid,
  status varchar(200) NOT NULL DEFAULT 'PENDING',
  progress smallint NOT NULL DEFAULT 0,
  priority smallint NOT NULL DEFAULT 5,
  parameter_schema_version varchar(200) NOT NULL,
  parameter_snapshot jsonb NOT NULL,
  parameter_hash char(64) NOT NULL,
  idempotency_key varchar(200) NOT NULL,
  algorithm_version varchar(200),
  resource_type varchar(200),
  resource_id uuid,
  submitted_at timestamptz NOT NULL DEFAULT now(),
  started_at timestamptz,
  finished_at timestamptz,
  lease_owner varchar(200),
  lease_until timestamptz,
  lease_generation bigint NOT NULL DEFAULT 0,
  next_run_at timestamptz NOT NULL DEFAULT now(),
  cancel_requested_at timestamptz,
  retry_count bigint NOT NULL DEFAULT 0,
  current_attempt_no bigint NOT NULL DEFAULT 0,
  error_code varchar(200),
  error_message varchar(2000),
  result_asset_id uuid,
  trace_id varchar(200) NOT NULL,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (owner_user_id,task_type,idempotency_key),
  CHECK (task_type IN ('INGESTION','BACKFILL','EXPORT','FACE_SEARCH','ANALYSIS','DATASET_IMPORT','SET_OPERATION','PROFILE_MERGE','PROFILE_SPLIT','MODEL_RUN','DEVICE_COMMAND','REPORT','NOTIFICATION','PERSON_LIBRARY_IMPORT','DEVICE_IMPORT')),
  CHECK (status IN ('PENDING','QUEUED','RUNNING','SUCCESS','FAILED','CANCELLED')),
  CHECK (progress BETWEEN 0 AND 100),
  CHECK (priority BETWEEN 0 AND 10),
  CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at>=started_at),
  CHECK (jsonb_typeof(parameter_snapshot) IN ('object','array')),
  CHECK (lease_generation >= 0),
  CHECK (retry_count >= 0),
  CHECK (version >= 0),
  CHECK (current_attempt_no >= 0)
);

COMMENT ON TABLE analysis.async_task IS 'M01 统一一次执行任务';

COMMENT ON COLUMN analysis.async_task.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.async_task.name IS 'name';

COMMENT ON COLUMN analysis.async_task.task_type IS 'task_type';

COMMENT ON COLUMN analysis.async_task.owner_user_id IS 'owner_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.async_task.context_id IS 'context_id；外键 analysis.analysis_context(id)；历史引用保护';

COMMENT ON COLUMN analysis.async_task.input_snapshot_id IS 'input_snapshot_id；外键 analysis.input_snapshot(id)；历史引用保护';

COMMENT ON COLUMN analysis.async_task.status IS 'status';

COMMENT ON COLUMN analysis.async_task.progress IS 'progress';

COMMENT ON COLUMN analysis.async_task.priority IS 'priority';

COMMENT ON COLUMN analysis.async_task.parameter_schema_version IS 'parameter_schema_version';

COMMENT ON COLUMN analysis.async_task.parameter_snapshot IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN analysis.async_task.parameter_hash IS 'parameter_hash';

COMMENT ON COLUMN analysis.async_task.idempotency_key IS 'idempotency_key';

COMMENT ON COLUMN analysis.async_task.algorithm_version IS 'algorithm_version';

COMMENT ON COLUMN analysis.async_task.resource_type IS 'resource_type';

COMMENT ON COLUMN analysis.async_task.resource_id IS 'resource_id';

COMMENT ON COLUMN analysis.async_task.submitted_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.async_task.started_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.async_task.finished_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.async_task.lease_owner IS 'lease_owner';

COMMENT ON COLUMN analysis.async_task.lease_until IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.async_task.lease_generation IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.async_task.next_run_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.async_task.cancel_requested_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.async_task.retry_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.async_task.current_attempt_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.async_task.error_code IS 'error_code';

COMMENT ON COLUMN analysis.async_task.error_message IS 'error_message';

COMMENT ON COLUMN analysis.async_task.result_asset_id IS 'result_asset_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN analysis.async_task.trace_id IS 'trace_id';

COMMENT ON COLUMN analysis.async_task.deleted IS 'deleted';

COMMENT ON COLUMN analysis.async_task.version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.async_task.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.task_attempt (
  task_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  started_at timestamptz NOT NULL,
  finished_at timestamptz,
  worker_id varchar(200) NOT NULL,
  lease_generation bigint NOT NULL,
  result varchar(200),
  error_code varchar(200),
  error_message varchar(2000),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (task_id,attempt_no),
  CHECK (attempt_no>=1),
  CHECK (result IS NULL OR result IN ('SUCCESS','FAILED','CANCELLED')),
  CHECK (attempt_no >= 0),
  CHECK (lease_generation >= 0)
);

COMMENT ON TABLE analysis.task_attempt IS 'M01 任务重试历史';

COMMENT ON COLUMN analysis.task_attempt.task_id IS 'task_id；外键 analysis.async_task(id)；历史引用保护';

COMMENT ON COLUMN analysis.task_attempt.attempt_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.task_attempt.started_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.task_attempt.finished_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.task_attempt.worker_id IS 'worker_id';

COMMENT ON COLUMN analysis.task_attempt.lease_generation IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.task_attempt.result IS 'result';

COMMENT ON COLUMN analysis.task_attempt.error_code IS 'error_code';

COMMENT ON COLUMN analysis.task_attempt.error_message IS 'error_message';

COMMENT ON COLUMN analysis.task_attempt.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.task_event (
  id uuid NOT NULL,
  task_id uuid NOT NULL,
  attempt_no bigint NOT NULL DEFAULT 0,
  previous_status varchar(200),
  status varchar(200) NOT NULL,
  occurred_at timestamptz NOT NULL,
  reason varchar(1000),
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (status IN ('PENDING','QUEUED','RUNNING','SUCCESS','FAILED','CANCELLED')),
  CHECK (attempt_no >= 0)
);

COMMENT ON TABLE analysis.task_event IS 'M01 状态转换与取消/恢复记录';

COMMENT ON COLUMN analysis.task_event.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.task_event.task_id IS 'task_id；外键 analysis.async_task(id)；历史引用保护';

COMMENT ON COLUMN analysis.task_event.attempt_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.task_event.previous_status IS 'previous_status';

COMMENT ON COLUMN analysis.task_event.status IS 'status';

COMMENT ON COLUMN analysis.task_event.occurred_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.task_event.reason IS 'reason';

COMMENT ON COLUMN analysis.task_event.trace_id IS 'trace_id';

COMMENT ON COLUMN analysis.task_event.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.import_job (
  task_id uuid NOT NULL,
  file_asset_id uuid NOT NULL,
  template_code varchar(200) NOT NULL,
  template_version varchar(200) NOT NULL,
  duplicate_policy varchar(200) NOT NULL,
  dataset_id uuid,
  library_id uuid,
  target_device_id uuid,
  imported_count bigint NOT NULL DEFAULT 0,
  skipped_count bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (task_id),
  CHECK (duplicate_policy IN ('SKIP','FAIL','UPDATE')),
  CHECK (imported_count >= 0),
  CHECK (skipped_count >= 0)
);

COMMENT ON TABLE analysis.import_job IS 'M01 文件导入意图与目标';

COMMENT ON COLUMN analysis.import_job.task_id IS 'task_id；外键 analysis.async_task(id)；历史引用保护';

COMMENT ON COLUMN analysis.import_job.file_asset_id IS 'file_asset_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN analysis.import_job.template_code IS 'template_code；外键 core.import_template(template_code,template_version)；历史引用保护';

COMMENT ON COLUMN analysis.import_job.template_version IS 'template_version；外键 core.import_template(template_code,template_version)；历史引用保护';

COMMENT ON COLUMN analysis.import_job.duplicate_policy IS 'duplicate_policy';

COMMENT ON COLUMN analysis.import_job.dataset_id IS 'dataset_id；外键 analysis.dataset(id)；历史引用保护';

COMMENT ON COLUMN analysis.import_job.library_id IS 'library_id；外键 analysis.person_library(id)；历史引用保护';

COMMENT ON COLUMN analysis.import_job.target_device_id IS 'target_device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN analysis.import_job.imported_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.import_job.skipped_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.import_job.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.import_row_result (
  task_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  row_number bigint NOT NULL,
  status varchar(200) NOT NULL,
  library_id uuid,
  member_id uuid,
  profile_id uuid,
  device_id uuid,
  dataset_id uuid,
  item_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (task_id,attempt_no,row_number),
  CHECK (status IN ('CREATED','UPDATED','SKIPPED')),
  CHECK (row_number>=1),
  CHECK (attempt_no >= 0),
  CHECK (row_number >= 0)
);

COMMENT ON TABLE analysis.import_row_result IS 'M01 成功导入逐行资源ID';

COMMENT ON COLUMN analysis.import_row_result.task_id IS 'task_id；外键 analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.import_row_result.attempt_no IS '非负计数或版本；不使用32位整型计数；外键 analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.import_row_result.row_number IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.import_row_result.status IS 'status';

COMMENT ON COLUMN analysis.import_row_result.library_id IS 'library_id；外键 analysis.person_library(id)；历史引用保护';

COMMENT ON COLUMN analysis.import_row_result.member_id IS 'member_id；外键 analysis.person_library_member(id)；历史引用保护';

COMMENT ON COLUMN analysis.import_row_result.profile_id IS 'profile_id；外键 analysis.profile(id)；历史引用保护';

COMMENT ON COLUMN analysis.import_row_result.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN analysis.import_row_result.dataset_id IS 'dataset_id；外键 analysis.dataset(id)；历史引用保护';

COMMENT ON COLUMN analysis.import_row_result.item_id IS 'item_id；外键 analysis.dataset_item(id)；历史引用保护';

COMMENT ON COLUMN analysis.import_row_result.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.task_row_error (
  id uuid NOT NULL,
  task_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  row_number bigint NOT NULL,
  source_record_key varchar(200),
  field_name varchar(200),
  error_code varchar(200) NOT NULL,
  reason varchar(2000) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (task_id,attempt_no,row_number,field_name),
  CHECK (row_number>=1),
  CHECK (attempt_no >= 0),
  CHECK (row_number >= 0)
);

COMMENT ON TABLE analysis.task_row_error IS 'M01 导入逐行错误';

COMMENT ON COLUMN analysis.task_row_error.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.task_row_error.task_id IS 'task_id；外键 analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.task_row_error.attempt_no IS '非负计数或版本；不使用32位整型计数；外键 analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.task_row_error.row_number IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.task_row_error.source_record_key IS 'source_record_key';

COMMENT ON COLUMN analysis.task_row_error.field_name IS 'field_name';

COMMENT ON COLUMN analysis.task_row_error.error_code IS 'error_code';

COMMENT ON COLUMN analysis.task_row_error.reason IS 'reason';

COMMENT ON COLUMN analysis.task_row_error.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.export_job (
  task_id uuid NOT NULL,
  source_type varchar(200) NOT NULL,
  source_schema_version varchar(200) NOT NULL,
  source_snapshot jsonb NOT NULL,
  input_snapshot_id uuid,
  format varchar(200) NOT NULL,
  columns text[] NOT NULL DEFAULT '{}'::text[],
  export_scope varchar(200) NOT NULL,
  output_file_id uuid,
  purpose varchar(1000),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (task_id),
  CHECK (format IN ('XLSX','CSV','ZIP')),
  CHECK (jsonb_typeof(source_snapshot) IN ('object','array'))
);

COMMENT ON TABLE analysis.export_job IS 'M01 统一导出意图及结果文件';

COMMENT ON COLUMN analysis.export_job.task_id IS 'task_id；外键 analysis.async_task(id)；历史引用保护';

COMMENT ON COLUMN analysis.export_job.source_type IS 'source_type';

COMMENT ON COLUMN analysis.export_job.source_schema_version IS 'source_schema_version';

COMMENT ON COLUMN analysis.export_job.source_snapshot IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN analysis.export_job.input_snapshot_id IS 'input_snapshot_id；外键 analysis.input_snapshot(id)；历史引用保护';

COMMENT ON COLUMN analysis.export_job.format IS 'format';

COMMENT ON COLUMN analysis.export_job.columns IS 'columns';

COMMENT ON COLUMN analysis.export_job.export_scope IS 'export_scope';

COMMENT ON COLUMN analysis.export_job.output_file_id IS 'output_file_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN analysis.export_job.purpose IS 'purpose';

COMMENT ON COLUMN analysis.export_job.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.ingestion_source (
  source_code varchar(200) NOT NULL,
  name varchar(200) NOT NULL,
  source_kind varchar(200) NOT NULL,
  adapter_code varchar(200) NOT NULL,
  protocol_version varchar(200) NOT NULL,
  enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (source_code)
);

COMMENT ON TABLE capture.ingestion_source IS 'M03 接入源适配目录；PENDING: P-004/P-005：源协议、字段映射、稳定去重键与回放范围未签认';

COMMENT ON COLUMN capture.ingestion_source.source_code IS 'source_code';

COMMENT ON COLUMN capture.ingestion_source.name IS 'name';

COMMENT ON COLUMN capture.ingestion_source.source_kind IS 'source_kind';

COMMENT ON COLUMN capture.ingestion_source.adapter_code IS 'adapter_code';

COMMENT ON COLUMN capture.ingestion_source.protocol_version IS 'protocol_version';

COMMENT ON COLUMN capture.ingestion_source.enabled IS 'enabled';

COMMENT ON COLUMN capture.ingestion_source.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.source_mapping_version (
  source_code varchar(200) NOT NULL,
  mapping_version varchar(200) NOT NULL,
  schema_version varchar(200) NOT NULL,
  mapping_definition jsonb NOT NULL,
  dedup_rule_version varchar(200) NOT NULL,
  effective_at timestamptz NOT NULL,
  enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (source_code,mapping_version),
  CHECK (jsonb_typeof(mapping_definition) IN ('object','array'))
);

COMMENT ON TABLE capture.source_mapping_version IS 'M03 不可覆盖的字段映射/去重版本';

COMMENT ON COLUMN capture.source_mapping_version.source_code IS 'source_code；外键 capture.ingestion_source(source_code)；历史引用保护';

COMMENT ON COLUMN capture.source_mapping_version.mapping_version IS 'mapping_version';

COMMENT ON COLUMN capture.source_mapping_version.schema_version IS 'schema_version';

COMMENT ON COLUMN capture.source_mapping_version.mapping_definition IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN capture.source_mapping_version.dedup_rule_version IS 'dedup_rule_version';

COMMENT ON COLUMN capture.source_mapping_version.effective_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.source_mapping_version.enabled IS 'enabled';

COMMENT ON COLUMN capture.source_mapping_version.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.ingestion_batch (
  id uuid NOT NULL,
  task_id uuid NOT NULL,
  source_code varchar(200) NOT NULL,
  source_file_id uuid,
  mapping_version varchar(200) NOT NULL,
  dedup_rule_version varchar(200) NOT NULL,
  mode varchar(200) NOT NULL DEFAULT 'INGEST',
  original_batch_id uuid,
  time_from timestamptz,
  time_to timestamptz,
  status varchar(200) NOT NULL DEFAULT 'PENDING',
  accepted_count bigint NOT NULL DEFAULT 0,
  rejected_count bigint NOT NULL DEFAULT 0,
  duplicate_count bigint NOT NULL DEFAULT 0,
  finished_at timestamptz,
  error_summary varchar(2000),
  reason varchar(1000),
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (task_id),
  CHECK (mode IN ('INGEST','REPLAY','BACKFILL')),
  CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','CANCELLED')),
  CHECK (time_from IS NULL OR time_to IS NULL OR time_from<time_to),
  CHECK (accepted_count >= 0),
  CHECK (rejected_count >= 0),
  CHECK (duplicate_count >= 0)
);

COMMENT ON TABLE capture.ingestion_batch IS 'M03 接入/回放/补数批次';

COMMENT ON COLUMN capture.ingestion_batch.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN capture.ingestion_batch.task_id IS 'task_id；外键 analysis.async_task(id)；历史引用保护';

COMMENT ON COLUMN capture.ingestion_batch.source_code IS 'source_code；外键 capture.source_mapping_version(source_code,mapping_version)；历史引用保护';

COMMENT ON COLUMN capture.ingestion_batch.source_file_id IS 'source_file_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN capture.ingestion_batch.mapping_version IS 'mapping_version；外键 capture.source_mapping_version(source_code,mapping_version)；历史引用保护';

COMMENT ON COLUMN capture.ingestion_batch.dedup_rule_version IS 'dedup_rule_version';

COMMENT ON COLUMN capture.ingestion_batch.mode IS 'mode';

COMMENT ON COLUMN capture.ingestion_batch.original_batch_id IS 'original_batch_id；外键 capture.ingestion_batch(id)；历史引用保护';

COMMENT ON COLUMN capture.ingestion_batch.time_from IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.ingestion_batch.time_to IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.ingestion_batch.status IS 'status';

COMMENT ON COLUMN capture.ingestion_batch.accepted_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN capture.ingestion_batch.rejected_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN capture.ingestion_batch.duplicate_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN capture.ingestion_batch.finished_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.ingestion_batch.error_summary IS 'error_summary';

COMMENT ON COLUMN capture.ingestion_batch.reason IS 'reason';

COMMENT ON COLUMN capture.ingestion_batch.trace_id IS 'trace_id';

COMMENT ON COLUMN capture.ingestion_batch.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.source_event (
  id uuid NOT NULL,
  source_code varchar(200) NOT NULL,
  source_record_key varchar(200) NOT NULL,
  data_type varchar(200) NOT NULL,
  source_device_key varchar(200),
  source_capture_time timestamptz,
  raw_file_id uuid,
  raw_offset bigint,
  payload_digest char(64) NOT NULL,
  first_received_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (source_code,source_record_key),
  CHECK (data_type IN ('FENCE','MAC','PLATE','FACE','DEVICE_STATUS','DEVICE_COUNTER')),
  CHECK (raw_offset >= 0)
);

COMMENT ON TABLE capture.source_event IS 'M03 稳定源事件身份；重复接入不重复事实';

COMMENT ON COLUMN capture.source_event.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN capture.source_event.source_code IS 'source_code；外键 capture.ingestion_source(source_code)；历史引用保护';

COMMENT ON COLUMN capture.source_event.source_record_key IS 'source_record_key';

COMMENT ON COLUMN capture.source_event.data_type IS 'data_type';

COMMENT ON COLUMN capture.source_event.source_device_key IS 'source_device_key';

COMMENT ON COLUMN capture.source_event.source_capture_time IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.source_event.raw_file_id IS 'raw_file_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN capture.source_event.raw_offset IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN capture.source_event.payload_digest IS 'payload_digest';

COMMENT ON COLUMN capture.source_event.first_received_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.source_event.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.ingestion_item (
  id uuid NOT NULL,
  batch_id uuid NOT NULL,
  row_number bigint NOT NULL,
  source_event_id uuid,
  capture_id uuid,
  processing_status varchar(200) NOT NULL,
  processed_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  record_kind varchar(200) NOT NULL DEFAULT 'CAPTURE',
  device_status_event_id uuid,
  counter_sample_id uuid,
  PRIMARY KEY (id),
  UNIQUE (batch_id,row_number),
  CHECK (processing_status IN ('ACCEPTED','REJECTED','DUPLICATE')),
  CHECK (row_number>=1),
  CHECK (row_number >= 0),
  CHECK (record_kind IN ('CAPTURE','DEVICE_STATUS','DEVICE_COUNTER')),
  CHECK (processing_status!='ACCEPTED' OR (source_event_id IS NOT NULL AND ((record_kind='CAPTURE' AND capture_id IS NOT NULL AND device_status_event_id IS NULL AND counter_sample_id IS NULL) OR (record_kind='DEVICE_STATUS' AND capture_id IS NULL AND device_status_event_id IS NOT NULL AND counter_sample_id IS NULL) OR (record_kind='DEVICE_COUNTER' AND capture_id IS NULL AND device_status_event_id IS NULL AND counter_sample_id IS NOT NULL))))
);

COMMENT ON TABLE capture.ingestion_item IS 'M03 接入处理尝试，只保留追溯必需字段';

COMMENT ON COLUMN capture.ingestion_item.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN capture.ingestion_item.batch_id IS 'batch_id；外键 capture.ingestion_batch(id)；历史引用保护';

COMMENT ON COLUMN capture.ingestion_item.row_number IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN capture.ingestion_item.source_event_id IS 'source_event_id；外键 capture.source_event(id)；历史引用保护';

COMMENT ON COLUMN capture.ingestion_item.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN capture.ingestion_item.processing_status IS 'processing_status';

COMMENT ON COLUMN capture.ingestion_item.processed_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.ingestion_item.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.ingestion_item.record_kind IS 'record_kind';

COMMENT ON COLUMN capture.ingestion_item.device_status_event_id IS 'device_status_event_id；外键 core.device_status_event(id)；历史引用保护';

COMMENT ON COLUMN capture.ingestion_item.counter_sample_id IS 'counter_sample_id；外键 capture.source_counter_sample(id)；历史引用保护';

CREATE TABLE capture.quality_issue (
  id uuid NOT NULL,
  ingestion_item_id uuid NOT NULL,
  issue_code varchar(200) NOT NULL,
  field_name varchar(200),
  reason varchar(2000) NOT NULL,
  source_device_key varchar(200),
  device_id uuid,
  occurred_at timestamptz NOT NULL,
  rule_version varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id)
);

COMMENT ON TABLE capture.quality_issue IS 'M03 校验错误与未知设备原键';

COMMENT ON COLUMN capture.quality_issue.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN capture.quality_issue.ingestion_item_id IS 'ingestion_item_id；外键 capture.ingestion_item(id)；历史引用保护';

COMMENT ON COLUMN capture.quality_issue.issue_code IS 'issue_code';

COMMENT ON COLUMN capture.quality_issue.field_name IS 'field_name';

COMMENT ON COLUMN capture.quality_issue.reason IS 'reason';

COMMENT ON COLUMN capture.quality_issue.source_device_key IS 'source_device_key';

COMMENT ON COLUMN capture.quality_issue.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN capture.quality_issue.occurred_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.quality_issue.rule_version IS 'rule_version';

COMMENT ON COLUMN capture.quality_issue.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.source_counter_sample (
  id uuid NOT NULL,
  source_code varchar(200) NOT NULL,
  device_id uuid,
  source_device_key varchar(200) NOT NULL,
  sample_key varchar(200) NOT NULL,
  period_from timestamptz NOT NULL,
  period_to timestamptz NOT NULL,
  reported_count bigint,
  reported_cumulative_count bigint,
  counter_epoch varchar(200) NOT NULL,
  as_of timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (source_code,source_device_key,sample_key),
  CHECK (period_from<period_to),
  CHECK (reported_count IS NOT NULL OR reported_cumulative_count IS NOT NULL),
  CHECK (reported_count >= 0),
  CHECK (reported_cumulative_count >= 0)
);

COMMENT ON TABLE capture.source_counter_sample IS 'M03 设备真实采集计数，不等于平台入库数';

COMMENT ON COLUMN capture.source_counter_sample.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN capture.source_counter_sample.source_code IS 'source_code；外键 capture.ingestion_source(source_code)；历史引用保护';

COMMENT ON COLUMN capture.source_counter_sample.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN capture.source_counter_sample.source_device_key IS 'source_device_key';

COMMENT ON COLUMN capture.source_counter_sample.sample_key IS 'sample_key';

COMMENT ON COLUMN capture.source_counter_sample.period_from IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.source_counter_sample.period_to IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.source_counter_sample.reported_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN capture.source_counter_sample.reported_cumulative_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN capture.source_counter_sample.counter_epoch IS 'counter_epoch';

COMMENT ON COLUMN capture.source_counter_sample.as_of IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.source_counter_sample.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.target (
  id uuid NOT NULL,
  target_type varchar(200) NOT NULL,
  identifier_cipher bytea,
  cipher_key_version varchar(200),
  normalization_version varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (id,target_type),
  CHECK (target_type IN ('IMSI','IMEI','MAC','PLATE','FACE')),
  CHECK ((identifier_cipher IS NULL)=(cipher_key_version IS NULL))
);

COMMENT ON TABLE capture.target IS 'M03 带类型的标识代理，非自然人身份';

COMMENT ON COLUMN capture.target.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN capture.target.target_type IS 'target_type';

COMMENT ON COLUMN capture.target.identifier_cipher IS 'identifier_cipher';

COMMENT ON COLUMN capture.target.cipher_key_version IS 'cipher_key_version';

COMMENT ON COLUMN capture.target.normalization_version IS 'normalization_version';

COMMENT ON COLUMN capture.target.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.target_lookup (
  target_id uuid NOT NULL,
  target_type varchar(200) NOT NULL,
  normalization_version varchar(200) NOT NULL,
  hmac_key_version varchar(200) NOT NULL,
  lookup_hash bytea NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (target_id,normalization_version,hmac_key_version),
  UNIQUE (target_type,normalization_version,hmac_key_version,lookup_hash)
);

COMMENT ON TABLE capture.target_lookup IS 'M03 轮换密钥的HMAC精确索引';

COMMENT ON COLUMN capture.target_lookup.target_id IS 'target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN capture.target_lookup.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN capture.target_lookup.normalization_version IS 'normalization_version';

COMMENT ON COLUMN capture.target_lookup.hmac_key_version IS 'hmac_key_version';

COMMENT ON COLUMN capture.target_lookup.lookup_hash IS 'lookup_hash';

COMMENT ON COLUMN capture.target_lookup.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.target_attribution (
  id uuid NOT NULL,
  target_id uuid NOT NULL,
  country_code varchar(200),
  province_code varchar(200),
  region_code varchar(200),
  source_code varchar(200) NOT NULL,
  source_version varchar(200) NOT NULL,
  valid_from timestamptz NOT NULL,
  valid_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (valid_until IS NULL OR valid_from<valid_until)
);

COMMENT ON TABLE capture.target_attribution IS 'M03 归属地及来源版本';

COMMENT ON COLUMN capture.target_attribution.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN capture.target_attribution.target_id IS 'target_id；外键 capture.target(id)；历史引用保护';

COMMENT ON COLUMN capture.target_attribution.country_code IS 'country_code';

COMMENT ON COLUMN capture.target_attribution.province_code IS 'province_code';

COMMENT ON COLUMN capture.target_attribution.region_code IS 'region_code；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN capture.target_attribution.source_code IS 'source_code';

COMMENT ON COLUMN capture.target_attribution.source_version IS 'source_version';

COMMENT ON COLUMN capture.target_attribution.valid_from IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.target_attribution.valid_until IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.target_attribution.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.capture_event (
  id uuid NOT NULL,
  source_event_id uuid NOT NULL,
  batch_id uuid NOT NULL,
  data_type varchar(200) NOT NULL,
  primary_target_id uuid,
  target_type varchar(200) NOT NULL,
  device_id uuid,
  site_id uuid,
  region_code_at_capture varchar(200),
  device_category_at_capture varchar(200),
  capture_time timestamptz NOT NULL,
  ingested_at timestamptz NOT NULL DEFAULT now(),
  location geometry(Point,4326),
  location_source varchar(200),
  attribution_id uuid,
  status varchar(200) NOT NULL DEFAULT 'VALID',
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (source_event_id),
  CHECK (data_type IN ('FENCE','MAC','PLATE','FACE')),
  CHECK (target_type IN ('IMSI','IMEI','MAC','PLATE','FACE')),
  CHECK (status IN ('VALID','QUARANTINED','RETRACTED')),
  CHECK ((data_type='FENCE' AND target_type IN ('IMSI','IMEI')) OR (data_type=target_type AND data_type IN ('MAC','PLATE','FACE'))),
  CHECK (location IS NULL OR (NOT ST_IsEmpty(location) AND ST_X(location) BETWEEN -180 AND 180 AND ST_Y(location) BETWEEN -90 AND 90))
);

COMMENT ON TABLE capture.capture_event IS 'M03 公共采集事实，四源统一检索';

COMMENT ON COLUMN capture.capture_event.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN capture.capture_event.source_event_id IS 'source_event_id；外键 capture.source_event(id)；历史引用保护';

COMMENT ON COLUMN capture.capture_event.batch_id IS 'batch_id；外键 capture.ingestion_batch(id)；历史引用保护';

COMMENT ON COLUMN capture.capture_event.data_type IS 'data_type';

COMMENT ON COLUMN capture.capture_event.primary_target_id IS 'primary_target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN capture.capture_event.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN capture.capture_event.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN capture.capture_event.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN capture.capture_event.region_code_at_capture IS 'region_code_at_capture；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN capture.capture_event.device_category_at_capture IS 'device_category_at_capture';

COMMENT ON COLUMN capture.capture_event.capture_time IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.capture_event.ingested_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.capture_event.location IS 'WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标';

COMMENT ON COLUMN capture.capture_event.location_source IS 'location_source';

COMMENT ON COLUMN capture.capture_event.attribution_id IS 'attribution_id；外键 capture.target_attribution(id)；历史引用保护';

COMMENT ON COLUMN capture.capture_event.status IS 'status';

COMMENT ON COLUMN capture.capture_event.trace_id IS 'trace_id';

COMMENT ON COLUMN capture.capture_event.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.capture_target (
  capture_id uuid NOT NULL,
  target_id uuid NOT NULL,
  target_type varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (capture_id,target_id),
  CHECK (target_type IN ('IMSI','IMEI','MAC','PLATE','FACE'))
);

COMMENT ON TABLE capture.capture_target IS 'M03 采集的其他目标，避免主目标查询重复存储';

COMMENT ON COLUMN capture.capture_target.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN capture.capture_target.target_id IS 'target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN capture.capture_target.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN capture.capture_target.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.fence_record (
  capture_id uuid NOT NULL,
  imsi_target_id uuid,
  imei_target_id uuid,
  carrier_code varchar(200),
  radio_type varchar(200),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (capture_id)
);

COMMENT ON TABLE capture.fence_record IS 'M03 围栏属性与IMSI/IMEI双标识';

COMMENT ON COLUMN capture.fence_record.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN capture.fence_record.imsi_target_id IS 'imsi_target_id；外键 capture.target(id)；历史引用保护';

COMMENT ON COLUMN capture.fence_record.imei_target_id IS 'imei_target_id；外键 capture.target(id)；历史引用保护';

COMMENT ON COLUMN capture.fence_record.carrier_code IS 'carrier_code';

COMMENT ON COLUMN capture.fence_record.radio_type IS 'radio_type';

COMMENT ON COLUMN capture.fence_record.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.mac_record (
  capture_id uuid NOT NULL,
  mac_target_id uuid,
  wireless_type varchar(200),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (capture_id)
);

COMMENT ON TABLE capture.mac_record IS 'M03 MAC专属属性';

COMMENT ON COLUMN capture.mac_record.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN capture.mac_record.mac_target_id IS 'mac_target_id；外键 capture.target(id)；历史引用保护';

COMMENT ON COLUMN capture.mac_record.wireless_type IS 'wireless_type';

COMMENT ON COLUMN capture.mac_record.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.plate_record (
  capture_id uuid NOT NULL,
  plate_target_id uuid,
  plate_color varchar(200),
  vehicle_image_asset_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (capture_id)
);

COMMENT ON TABLE capture.plate_record IS 'M03 车牌专属属性';

COMMENT ON COLUMN capture.plate_record.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN capture.plate_record.plate_target_id IS 'plate_target_id；外键 capture.target(id)；历史引用保护';

COMMENT ON COLUMN capture.plate_record.plate_color IS 'plate_color';

COMMENT ON COLUMN capture.plate_record.vehicle_image_asset_id IS 'vehicle_image_asset_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN capture.plate_record.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.face_record (
  capture_id uuid NOT NULL,
  image_asset_id uuid NOT NULL,
  model_version varchar(200),
  quality_score numeric(6,5),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (capture_id),
  CHECK (quality_score IS NULL OR quality_score BETWEEN 0 AND 1)
);

COMMENT ON TABLE capture.face_record IS 'M03 人像专属属性';

COMMENT ON COLUMN capture.face_record.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN capture.face_record.image_asset_id IS 'image_asset_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN capture.face_record.model_version IS 'model_version';

COMMENT ON COLUMN capture.face_record.quality_score IS 'quality_score';

COMMENT ON COLUMN capture.face_record.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.face_feature (
  capture_id uuid NOT NULL,
  model_version varchar(200) NOT NULL,
  feature_asset_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (capture_id,model_version)
);

COMMENT ON TABLE capture.face_feature IS 'M11 同一人像多模型特征引用；PENDING: P-007：人像特征格式、维度、模型许可与阈值未定，不预建向量索引';

COMMENT ON COLUMN capture.face_feature.capture_id IS 'capture_id；外键 capture.face_record(capture_id)；历史引用保护';

COMMENT ON COLUMN capture.face_feature.model_version IS 'model_version';

COMMENT ON COLUMN capture.face_feature.feature_asset_id IS 'feature_asset_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN capture.face_feature.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE capture.capture_revision (
  id uuid NOT NULL,
  capture_id uuid NOT NULL,
  revision_no bigint NOT NULL,
  previous_status varchar(200) NOT NULL,
  status varchar(200) NOT NULL,
  actor_user_id uuid,
  reason varchar(1000) NOT NULL,
  occurred_at timestamptz NOT NULL,
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (capture_id,revision_no),
  CHECK (previous_status IN ('VALID','QUARANTINED','RETRACTED')),
  CHECK (status IN ('VALID','QUARANTINED','RETRACTED')),
  CHECK (revision_no >= 0)
);

COMMENT ON TABLE capture.capture_revision IS 'M03 撤回/更正记录，原事实保留';

COMMENT ON COLUMN capture.capture_revision.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN capture.capture_revision.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN capture.capture_revision.revision_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN capture.capture_revision.previous_status IS 'previous_status';

COMMENT ON COLUMN capture.capture_revision.status IS 'status';

COMMENT ON COLUMN capture.capture_revision.actor_user_id IS 'actor_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN capture.capture_revision.reason IS 'reason';

COMMENT ON COLUMN capture.capture_revision.occurred_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN capture.capture_revision.trace_id IS 'trace_id';

COMMENT ON COLUMN capture.capture_revision.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.analysis_job (
  id uuid NOT NULL,
  task_id uuid NOT NULL,
  name varchar(200) NOT NULL,
  kind varchar(200) NOT NULL,
  context_id uuid NOT NULL,
  input_snapshot_id uuid NOT NULL,
  algorithm_version varchar(200),
  revised_from_id uuid,
  revision_reason varchar(1000),
  result_count bigint,
  published_attempt_no bigint,
  version bigint NOT NULL DEFAULT 0,
  deleted boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (task_id),
  UNIQUE (id,task_id),
  CHECK (kind IN ('COMPANION','COLLISION','REGION_STATISTICS')),
  CHECK (result_count >= 0),
  CHECK (version >= 0),
  CHECK (published_attempt_no >= 0)
);

COMMENT ON TABLE analysis.analysis_job IS 'M08 分析任务领域资源；PENDING: P-011/P-009/P-003：算法、Context生命周期和容量未签认；参数/结果分型固定';

COMMENT ON COLUMN analysis.analysis_job.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.analysis_job.task_id IS 'task_id；外键 analysis.async_task(id),analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_job.name IS 'name';

COMMENT ON COLUMN analysis.analysis_job.kind IS 'kind';

COMMENT ON COLUMN analysis.analysis_job.context_id IS 'context_id；外键 analysis.analysis_context(id)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_job.input_snapshot_id IS 'input_snapshot_id；外键 analysis.input_snapshot(id)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_job.algorithm_version IS 'algorithm_version';

COMMENT ON COLUMN analysis.analysis_job.revised_from_id IS 'revised_from_id；外键 analysis.analysis_job(id)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_job.revision_reason IS 'revision_reason';

COMMENT ON COLUMN analysis.analysis_job.result_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.analysis_job.published_attempt_no IS '非负计数或版本；不使用32位整型计数；外键 analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_job.version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.analysis_job.deleted IS 'deleted';

COMMENT ON COLUMN analysis.analysis_job.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.companion_parameter (
  analysis_job_id uuid NOT NULL,
  target_id uuid NOT NULL,
  target_type varchar(200) NOT NULL,
  time_from timestamptz NOT NULL,
  time_to timestamptz NOT NULL,
  time_window_seconds bigint NOT NULL,
  spatial_window_meters numeric NOT NULL,
  min_result_score numeric(6,5),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (analysis_job_id),
  CHECK (time_from<time_to),
  CHECK (time_window_seconds>0),
  CHECK (spatial_window_meters>=0),
  CHECK (min_result_score IS NULL OR min_result_score BETWEEN 0 AND 1),
  CHECK (time_window_seconds >= 0)
);

COMMENT ON TABLE analysis.companion_parameter IS 'M08 伴随参数';

COMMENT ON COLUMN analysis.companion_parameter.analysis_job_id IS 'analysis_job_id；外键 analysis.analysis_job(id)；历史引用保护';

COMMENT ON COLUMN analysis.companion_parameter.target_id IS 'target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.companion_parameter.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.companion_parameter.time_from IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.companion_parameter.time_to IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.companion_parameter.time_window_seconds IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.companion_parameter.spatial_window_meters IS 'spatial_window_meters';

COMMENT ON COLUMN analysis.companion_parameter.min_result_score IS 'min_result_score';

COMMENT ON COLUMN analysis.companion_parameter.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.collision_parameter (
  analysis_job_id uuid NOT NULL,
  time_from timestamptz NOT NULL,
  time_to timestamptz NOT NULL,
  rule_code varchar(200) NOT NULL,
  rule_version varchar(200),
  spatial_geometry geometry(Geometry,4326),
  min_result_score numeric(6,5),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (analysis_job_id),
  CHECK (time_from<time_to),
  CHECK (min_result_score IS NULL OR min_result_score BETWEEN 0 AND 1),
  CHECK (spatial_geometry IS NULL OR (NOT ST_IsEmpty(spatial_geometry) AND ST_IsValid(spatial_geometry) AND ST_CoveredBy(spatial_geometry,ST_MakeEnvelope(-180,-90,180,90,4326)))),
  CHECK (spatial_geometry IS NULL OR GeometryType(spatial_geometry) IN ('POLYGON','MULTIPOLYGON'))
);

COMMENT ON TABLE analysis.collision_parameter IS 'M08 碰撞参数，规则引用不猜测算法';

COMMENT ON COLUMN analysis.collision_parameter.analysis_job_id IS 'analysis_job_id；外键 analysis.analysis_job(id)；历史引用保护';

COMMENT ON COLUMN analysis.collision_parameter.time_from IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.collision_parameter.time_to IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.collision_parameter.rule_code IS 'rule_code';

COMMENT ON COLUMN analysis.collision_parameter.rule_version IS 'rule_version';

COMMENT ON COLUMN analysis.collision_parameter.spatial_geometry IS 'WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标';

COMMENT ON COLUMN analysis.collision_parameter.min_result_score IS 'min_result_score';

COMMENT ON COLUMN analysis.collision_parameter.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.region_parameter (
  analysis_job_id uuid NOT NULL,
  time_from timestamptz NOT NULL,
  time_to timestamptz NOT NULL,
  data_types text[] NOT NULL DEFAULT '{}'::text[],
  min_result_score numeric(6,5),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (analysis_job_id),
  CHECK (time_from<time_to),
  CHECK (data_types <@ ARRAY['FENCE','MAC','PLATE']::text[]),
  CHECK (cardinality(data_types)>0),
  CHECK (min_result_score IS NULL OR min_result_score BETWEEN 0 AND 1)
);

COMMENT ON TABLE analysis.region_parameter IS 'M08 区域统计参数';

COMMENT ON COLUMN analysis.region_parameter.analysis_job_id IS 'analysis_job_id；外键 analysis.analysis_job(id)；历史引用保护';

COMMENT ON COLUMN analysis.region_parameter.time_from IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.region_parameter.time_to IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.region_parameter.data_types IS '有限类型数组；不代替需要外键的业务对象关系';

COMMENT ON COLUMN analysis.region_parameter.min_result_score IS 'min_result_score';

COMMENT ON COLUMN analysis.region_parameter.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.analysis_scope_site (
  analysis_job_id uuid NOT NULL,
  site_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (analysis_job_id,site_id)
);

COMMENT ON TABLE analysis.analysis_scope_site IS 'M08 分析限定场所';

COMMENT ON COLUMN analysis.analysis_scope_site.analysis_job_id IS 'analysis_job_id；外键 analysis.analysis_job(id)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_scope_site.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_scope_site.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.analysis_scope_region (
  analysis_job_id uuid NOT NULL,
  region_code varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (analysis_job_id,region_code)
);

COMMENT ON TABLE analysis.analysis_scope_region IS 'M08 行政区筛选，与手绘选区区别';

COMMENT ON COLUMN analysis.analysis_scope_region.analysis_job_id IS 'analysis_job_id；外键 analysis.analysis_job(id)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_scope_region.region_code IS 'region_code；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_scope_region.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.analysis_selected_target (
  analysis_job_id uuid NOT NULL,
  target_id uuid NOT NULL,
  target_type varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (analysis_job_id,target_id)
);

COMMENT ON TABLE analysis.analysis_selected_target IS 'M08 碰撞选择目标';

COMMENT ON COLUMN analysis.analysis_selected_target.analysis_job_id IS 'analysis_job_id；外键 analysis.analysis_job(id)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_selected_target.target_id IS 'target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_selected_target.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_selected_target.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.selected_region (
  analysis_job_id uuid NOT NULL,
  region_no bigint NOT NULL,
  geometry geometry(Geometry,4326) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (analysis_job_id,region_no),
  CHECK (geometry IS NULL OR (NOT ST_IsEmpty(geometry) AND ST_IsValid(geometry) AND ST_CoveredBy(geometry,ST_MakeEnvelope(-180,-90,180,90,4326)))),
  CHECK (GeometryType(geometry) IN ('POLYGON','MULTIPOLYGON')),
  CHECK (region_no >= 0)
);

COMMENT ON TABLE analysis.selected_region IS 'M08 任务选区，不等同于行政区';

COMMENT ON COLUMN analysis.selected_region.analysis_job_id IS 'analysis_job_id；外键 analysis.analysis_job(id)；历史引用保护';

COMMENT ON COLUMN analysis.selected_region.region_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.selected_region.geometry IS 'WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标';

COMMENT ON COLUMN analysis.selected_region.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.analysis_result (
  id uuid NOT NULL,
  analysis_job_id uuid NOT NULL,
  task_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  target_id uuid NOT NULL,
  target_type varchar(200) NOT NULL,
  data_type varchar(200) NOT NULL,
  kind varchar(200) NOT NULL,
  capture_count bigint NOT NULL,
  site_count bigint NOT NULL,
  region_count bigint NOT NULL,
  first_seen_at timestamptz,
  algorithm_version varchar(200) NOT NULL,
  generated_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (analysis_job_id,attempt_no,target_id,data_type),
  UNIQUE (id,analysis_job_id),
  CHECK (data_type IN ('FENCE','MAC','PLATE','FACE')),
  CHECK (kind IN ('COMPANION','COLLISION','REGION_STATISTICS')),
  CHECK (attempt_no >= 0),
  CHECK (capture_count >= 0),
  CHECK (site_count >= 0),
  CHECK (region_count >= 0)
);

COMMENT ON TABLE analysis.analysis_result IS 'M08 逐目标结果的公共字段';

COMMENT ON COLUMN analysis.analysis_result.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.analysis_result.analysis_job_id IS 'analysis_job_id；外键 analysis.analysis_job(id,task_id)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_result.task_id IS 'task_id；外键 analysis.analysis_job(id,task_id),analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_result.attempt_no IS '非负计数或版本；不使用32位整型计数；外键 analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_result.target_id IS 'target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_result.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.analysis_result.data_type IS 'data_type';

COMMENT ON COLUMN analysis.analysis_result.kind IS 'kind';

COMMENT ON COLUMN analysis.analysis_result.capture_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.analysis_result.site_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.analysis_result.region_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.analysis_result.first_seen_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.analysis_result.algorithm_version IS 'algorithm_version';

COMMENT ON COLUMN analysis.analysis_result.generated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.analysis_result.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.companion_result (
  result_id uuid NOT NULL,
  common_occurrence_count bigint NOT NULL,
  shared_site_count bigint NOT NULL,
  trajectory_overlap_count bigint NOT NULL,
  association_score numeric(6,5),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (result_id),
  CHECK (association_score IS NULL OR association_score BETWEEN 0 AND 1),
  CHECK (common_occurrence_count >= 0),
  CHECK (shared_site_count >= 0),
  CHECK (trajectory_overlap_count >= 0)
);

COMMENT ON TABLE analysis.companion_result IS 'M08 伴随具体结果';

COMMENT ON COLUMN analysis.companion_result.result_id IS 'result_id；外键 analysis.analysis_result(id)；历史引用保护';

COMMENT ON COLUMN analysis.companion_result.common_occurrence_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.companion_result.shared_site_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.companion_result.trajectory_overlap_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.companion_result.association_score IS 'association_score';

COMMENT ON COLUMN analysis.companion_result.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.collision_result (
  result_id uuid NOT NULL,
  collision_count bigint NOT NULL,
  match_rate numeric(6,5),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (result_id),
  CHECK (match_rate IS NULL OR match_rate BETWEEN 0 AND 1),
  CHECK (collision_count >= 0)
);

COMMENT ON TABLE analysis.collision_result IS 'M08 碰撞具体结果';

COMMENT ON COLUMN analysis.collision_result.result_id IS 'result_id；外键 analysis.analysis_result(id)；历史引用保护';

COMMENT ON COLUMN analysis.collision_result.collision_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.collision_result.match_rate IS 'match_rate';

COMMENT ON COLUMN analysis.collision_result.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.region_result (
  result_id uuid NOT NULL,
  visited_selected_region_count bigint NOT NULL,
  total_selected_region_count bigint NOT NULL,
  coverage_rate numeric(8,7) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (result_id),
  CHECK (total_selected_region_count>0),
  CHECK (visited_selected_region_count<=total_selected_region_count),
  CHECK (coverage_rate BETWEEN 0 AND 1),
  CHECK (abs(coverage_rate-visited_selected_region_count::numeric/total_selected_region_count)<=0.0000001),
  CHECK (visited_selected_region_count >= 0),
  CHECK (total_selected_region_count >= 0)
);

COMMENT ON TABLE analysis.region_result IS 'M08 选区覆盖率，不解释为嫌疑概率';

COMMENT ON COLUMN analysis.region_result.result_id IS 'result_id；外键 analysis.analysis_result(id)；历史引用保护';

COMMENT ON COLUMN analysis.region_result.visited_selected_region_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.region_result.total_selected_region_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.region_result.coverage_rate IS 'coverage_rate';

COMMENT ON COLUMN analysis.region_result.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.collision_event (
  id uuid NOT NULL,
  result_id uuid NOT NULL,
  occurred_at timestamptz NOT NULL,
  location geometry(Point,4326) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (location IS NULL OR (NOT ST_IsEmpty(location) AND ST_X(location) BETWEEN -180 AND 180 AND ST_Y(location) BETWEEN -90 AND 90))
);

COMMENT ON TABLE analysis.collision_event IS 'M08 碰撞事件时间地点';

COMMENT ON COLUMN analysis.collision_event.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.collision_event.result_id IS 'result_id；外键 analysis.collision_result(result_id)；历史引用保护';

COMMENT ON COLUMN analysis.collision_event.occurred_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.collision_event.location IS 'WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标';

COMMENT ON COLUMN analysis.collision_event.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.result_evidence (
  result_id uuid NOT NULL,
  capture_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (result_id,capture_id)
);

COMMENT ON TABLE analysis.result_evidence IS 'M08 结果到原始采集的证据';

COMMENT ON COLUMN analysis.result_evidence.result_id IS 'result_id；外键 analysis.analysis_result(id)；历史引用保护';

COMMENT ON COLUMN analysis.result_evidence.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN analysis.result_evidence.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.result_region (
  result_id uuid NOT NULL,
  region_code varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (result_id,region_code)
);

COMMENT ON TABLE analysis.result_region IS 'M08 结果经过的行政区';

COMMENT ON COLUMN analysis.result_region.result_id IS 'result_id；外键 analysis.analysis_result(id)；历史引用保护';

COMMENT ON COLUMN analysis.result_region.region_code IS 'region_code；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN analysis.result_region.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.result_selected_region (
  result_id uuid NOT NULL,
  analysis_job_id uuid NOT NULL,
  region_no bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (result_id,region_no),
  CHECK (region_no >= 0)
);

COMMENT ON TABLE analysis.result_selected_region IS 'M08 结果经过的手绘选区';

COMMENT ON COLUMN analysis.result_selected_region.result_id IS 'result_id；外键 analysis.region_result(result_id),analysis.analysis_result(id,analysis_job_id)；历史引用保护';

COMMENT ON COLUMN analysis.result_selected_region.analysis_job_id IS 'analysis_job_id；外键 analysis.selected_region(analysis_job_id,region_no),analysis.analysis_result(id,analysis_job_id)；历史引用保护';

COMMENT ON COLUMN analysis.result_selected_region.region_no IS '非负计数或版本；不使用32位整型计数；外键 analysis.selected_region(analysis_job_id,region_no)；历史引用保护';

COMMENT ON COLUMN analysis.result_selected_region.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.dataset (
  id uuid NOT NULL,
  name varchar(200) NOT NULL,
  owner_user_id uuid NOT NULL,
  dataset_kind varchar(200) NOT NULL,
  element_type varchar(200) NOT NULL,
  status varchar(200) NOT NULL DEFAULT 'ACTIVE',
  current_version bigint NOT NULL DEFAULT 0,
  source_context_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE (id,element_type),
  CHECK (dataset_kind IN ('SINGLE','MIXED')),
  CHECK (element_type IN ('CAPTURE','TARGET')),
  CHECK (status IN ('ACTIVE','ARCHIVED')),
  CHECK (version >= 0),
  CHECK (current_version=version),
  CHECK (current_version >= 0)
);

COMMENT ON TABLE analysis.dataset IS 'M09 数据集容器';

COMMENT ON COLUMN analysis.dataset.id IS '应用生成的UUID业务主键；外键 analysis.dataset_version(dataset_id,version_no)；历史引用保护';

COMMENT ON COLUMN analysis.dataset.name IS 'name';

COMMENT ON COLUMN analysis.dataset.owner_user_id IS 'owner_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset.dataset_kind IS 'dataset_kind';

COMMENT ON COLUMN analysis.dataset.element_type IS 'element_type';

COMMENT ON COLUMN analysis.dataset.status IS 'status';

COMMENT ON COLUMN analysis.dataset.current_version IS '非负计数或版本；不使用32位整型计数；外键 analysis.dataset_version(dataset_id,version_no)；历史引用保护';

COMMENT ON COLUMN analysis.dataset.source_context_id IS 'source_context_id；外键 analysis.analysis_context(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.dataset.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.dataset.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset.deleted IS 'deleted';

COMMENT ON COLUMN analysis.dataset.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE analysis.dataset_version (
  dataset_id uuid NOT NULL,
  version_no bigint NOT NULL,
  operation varchar(200) NOT NULL,
  item_count bigint NOT NULL,
  source_snapshot_id uuid,
  created_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  name_snapshot varchar(200) NOT NULL,
  PRIMARY KEY (dataset_id,version_no),
  CHECK (item_count >= 0),
  CHECK (version_no >= 0)
);

COMMENT ON TABLE analysis.dataset_version IS 'M09 数据集每次变更版本摘要';

COMMENT ON COLUMN analysis.dataset_version.dataset_id IS 'dataset_id；外键 analysis.dataset(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_version.version_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.dataset_version.operation IS 'operation';

COMMENT ON COLUMN analysis.dataset_version.item_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.dataset_version.source_snapshot_id IS 'source_snapshot_id；外键 analysis.input_snapshot(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_version.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_version.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.dataset_version.name_snapshot IS 'name_snapshot';

CREATE TABLE analysis.dataset_item (
  id uuid NOT NULL,
  dataset_id uuid NOT NULL,
  element_type varchar(200) NOT NULL,
  capture_id uuid,
  target_id uuid,
  target_type varchar(200),
  source_capture_id uuid,
  valid_from_version bigint NOT NULL,
  valid_to_version bigint,
  added_by uuid NOT NULL,
  added_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK ((element_type='CAPTURE' AND capture_id IS NOT NULL AND target_id IS NULL AND target_type IS NULL) OR (element_type='TARGET' AND capture_id IS NULL AND target_id IS NOT NULL AND target_type IS NOT NULL)),
  CHECK (valid_to_version IS NULL OR valid_from_version<valid_to_version),
  CHECK (valid_from_version >= 0),
  CHECK (valid_to_version >= 0)
);

COMMENT ON TABLE analysis.dataset_item IS 'M09 采集/目标集合元素及版本有效区间';

COMMENT ON COLUMN analysis.dataset_item.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.dataset_item.dataset_id IS 'dataset_id；外键 analysis.dataset(id,element_type),analysis.dataset_version(dataset_id,version_no),analysis.dataset_version(dataset_id,version_no)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_item.element_type IS 'element_type；外键 analysis.dataset(id,element_type)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_item.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_item.target_id IS 'target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_item.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_item.source_capture_id IS 'source_capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_item.valid_from_version IS '非负计数或版本；不使用32位整型计数；外键 analysis.dataset_version(dataset_id,version_no)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_item.valid_to_version IS '非负计数或版本；不使用32位整型计数；外键 analysis.dataset_version(dataset_id,version_no)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_item.added_by IS 'added_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_item.added_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.dataset_item.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.dataset_grant (
  id uuid NOT NULL,
  dataset_id uuid NOT NULL,
  user_id uuid,
  role_id uuid,
  access_level varchar(200) NOT NULL,
  expires_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (num_nonnulls(user_id,role_id)=1),
  CHECK (access_level IN ('READ','WRITE','OWNER'))
);

COMMENT ON TABLE analysis.dataset_grant IS 'M09 数据集共享上限，不能覆盖原始数据权限；PENDING: P-002/P-013：共享主体、权限继承和生命周期未签认；P-012 ID集合语法已收敛';

COMMENT ON COLUMN analysis.dataset_grant.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.dataset_grant.dataset_id IS 'dataset_id；外键 analysis.dataset(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_grant.user_id IS 'user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_grant.role_id IS 'role_id；外键 auth.role(id)；历史引用保护';

COMMENT ON COLUMN analysis.dataset_grant.access_level IS 'access_level';

COMMENT ON COLUMN analysis.dataset_grant.expires_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.dataset_grant.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.set_job (
  id uuid NOT NULL,
  task_id uuid NOT NULL,
  name varchar(200) NOT NULL,
  operator varchar(200) NOT NULL,
  expression varchar(2000),
  element_type varchar(200) NOT NULL,
  dedup_rule_version varchar(200) NOT NULL,
  result_dataset_id uuid,
  result_dataset_version bigint,
  result_count bigint,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (task_id),
  CHECK (operator IN ('INTERSECTION','UNION','DIFFERENCE','MIXED')),
  CHECK (element_type IN ('CAPTURE','TARGET')),
  CHECK (operator!='MIXED' OR expression IS NOT NULL),
  CHECK ((result_dataset_id IS NULL)=(result_dataset_version IS NULL)),
  CHECK (result_count >= 0),
  CHECK (result_dataset_version >= 0)
);

COMMENT ON TABLE analysis.set_job IS 'M09 大集合异步运算领域资源';

COMMENT ON COLUMN analysis.set_job.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.set_job.task_id IS 'task_id；外键 analysis.async_task(id)；历史引用保护';

COMMENT ON COLUMN analysis.set_job.name IS 'name';

COMMENT ON COLUMN analysis.set_job.operator IS 'operator';

COMMENT ON COLUMN analysis.set_job.expression IS 'expression';

COMMENT ON COLUMN analysis.set_job.element_type IS 'element_type';

COMMENT ON COLUMN analysis.set_job.dedup_rule_version IS 'dedup_rule_version';

COMMENT ON COLUMN analysis.set_job.result_dataset_id IS 'result_dataset_id；外键 analysis.dataset_version(dataset_id,version_no)；历史引用保护';

COMMENT ON COLUMN analysis.set_job.result_dataset_version IS '非负计数或版本；不使用32位整型计数；外键 analysis.dataset_version(dataset_id,version_no)；历史引用保护';

COMMENT ON COLUMN analysis.set_job.result_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.set_job.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.set_operand (
  set_job_id uuid NOT NULL,
  symbol varchar(200) NOT NULL,
  dataset_id uuid NOT NULL,
  dataset_version bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (set_job_id,symbol),
  CHECK (dataset_version >= 0)
);

COMMENT ON TABLE analysis.set_operand IS 'M09 集合输入的固定版本和符号';

COMMENT ON COLUMN analysis.set_operand.set_job_id IS 'set_job_id；外键 analysis.set_job(id)；历史引用保护';

COMMENT ON COLUMN analysis.set_operand.symbol IS 'symbol';

COMMENT ON COLUMN analysis.set_operand.dataset_id IS 'dataset_id；外键 analysis.dataset_version(dataset_id,version_no)；历史引用保护';

COMMENT ON COLUMN analysis.set_operand.dataset_version IS '非负计数或版本；不使用32位整型计数；外键 analysis.dataset_version(dataset_id,version_no)；历史引用保护';

COMMENT ON COLUMN analysis.set_operand.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.profile (
  id uuid NOT NULL,
  profile_type varchar(200) NOT NULL,
  display_label varchar(200) NOT NULL,
  confidence numeric(6,5),
  source varchar(200) NOT NULL,
  first_seen_at timestamptz,
  last_seen_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE (id,profile_type),
  CHECK (profile_type IN ('PERSON','VEHICLE')),
  CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  CHECK (first_seen_at IS NULL OR last_seen_at IS NULL OR first_seen_at<=last_seen_at),
  CHECK (version >= 0)
);

COMMENT ON TABLE analysis.profile IS 'M11 人员/车辆档案公共信息';

COMMENT ON COLUMN analysis.profile.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.profile.profile_type IS 'profile_type';

COMMENT ON COLUMN analysis.profile.display_label IS 'display_label';

COMMENT ON COLUMN analysis.profile.confidence IS 'confidence';

COMMENT ON COLUMN analysis.profile.source IS 'source';

COMMENT ON COLUMN analysis.profile.first_seen_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.profile.last_seen_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.profile.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.profile.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.profile.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile.deleted IS 'deleted';

COMMENT ON COLUMN analysis.profile.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE analysis.person_profile (
  profile_id uuid NOT NULL,
  profile_type varchar(200) NOT NULL DEFAULT 'PERSON',
  name_cipher bytea,
  gender_code varchar(200),
  ethnicity_code varchar(200),
  phone_cipher bytea,
  identity_number_cipher bytea,
  cipher_key_version varchar(200),
  residence_region_code varchar(200),
  suspected_site_id uuid,
  suspected_landing_text varchar(1000),
  appearance_description varchar(2000),
  portrait_asset_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (profile_id),
  CHECK (profile_type='PERSON')
);

COMMENT ON TABLE analysis.person_profile IS 'M11 图码/人员档案字段';

COMMENT ON COLUMN analysis.person_profile.profile_id IS 'profile_id；外键 analysis.profile(id,profile_type)；历史引用保护';

COMMENT ON COLUMN analysis.person_profile.profile_type IS 'profile_type；外键 analysis.profile(id,profile_type)；历史引用保护';

COMMENT ON COLUMN analysis.person_profile.name_cipher IS 'name_cipher';

COMMENT ON COLUMN analysis.person_profile.gender_code IS 'gender_code';

COMMENT ON COLUMN analysis.person_profile.ethnicity_code IS 'ethnicity_code';

COMMENT ON COLUMN analysis.person_profile.phone_cipher IS 'phone_cipher';

COMMENT ON COLUMN analysis.person_profile.identity_number_cipher IS 'identity_number_cipher';

COMMENT ON COLUMN analysis.person_profile.cipher_key_version IS 'cipher_key_version';

COMMENT ON COLUMN analysis.person_profile.residence_region_code IS 'residence_region_code；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN analysis.person_profile.suspected_site_id IS 'suspected_site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN analysis.person_profile.suspected_landing_text IS 'suspected_landing_text';

COMMENT ON COLUMN analysis.person_profile.appearance_description IS 'appearance_description';

COMMENT ON COLUMN analysis.person_profile.portrait_asset_id IS 'portrait_asset_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN analysis.person_profile.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.vehicle_profile (
  profile_id uuid NOT NULL,
  profile_type varchar(200) NOT NULL DEFAULT 'VEHICLE',
  primary_plate_target_id uuid NOT NULL,
  plate_color varchar(200),
  vehicle_type_code varchar(200),
  vehicle_image_asset_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  plate_target_type varchar(200) NOT NULL DEFAULT 'PLATE',
  PRIMARY KEY (profile_id),
  CHECK (profile_type='VEHICLE'),
  CHECK (plate_target_type='PLATE')
);

COMMENT ON TABLE analysis.vehicle_profile IS 'M11 车码/车辆档案字段';

COMMENT ON COLUMN analysis.vehicle_profile.profile_id IS 'profile_id；外键 analysis.profile(id,profile_type)；历史引用保护';

COMMENT ON COLUMN analysis.vehicle_profile.profile_type IS 'profile_type；外键 analysis.profile(id,profile_type)；历史引用保护';

COMMENT ON COLUMN analysis.vehicle_profile.primary_plate_target_id IS 'primary_plate_target_id；外键 capture.target(id),capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.vehicle_profile.plate_color IS 'plate_color';

COMMENT ON COLUMN analysis.vehicle_profile.vehicle_type_code IS 'vehicle_type_code';

COMMENT ON COLUMN analysis.vehicle_profile.vehicle_image_asset_id IS 'vehicle_image_asset_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN analysis.vehicle_profile.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.vehicle_profile.plate_target_type IS 'plate_target_type；外键 capture.target(id,target_type)；历史引用保护';

CREATE TABLE analysis.profile_identifier_index (
  profile_id uuid NOT NULL,
  field_code varchar(200) NOT NULL,
  hmac_key_version varchar(200) NOT NULL,
  normalization_version varchar(200) NOT NULL,
  lookup_hash bytea NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (profile_id,field_code,hmac_key_version,normalization_version),
  CHECK (field_code IN ('NAME','PHONE','IDENTITY_NUMBER'))
);

COMMENT ON TABLE analysis.profile_identifier_index IS 'M11 人员敏感字段精确HMAC检索';

COMMENT ON COLUMN analysis.profile_identifier_index.profile_id IS 'profile_id；外键 analysis.profile(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_identifier_index.field_code IS 'field_code';

COMMENT ON COLUMN analysis.profile_identifier_index.hmac_key_version IS 'hmac_key_version';

COMMENT ON COLUMN analysis.profile_identifier_index.normalization_version IS 'normalization_version';

COMMENT ON COLUMN analysis.profile_identifier_index.lookup_hash IS 'lookup_hash';

COMMENT ON COLUMN analysis.profile_identifier_index.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.profile_link (
  id uuid NOT NULL,
  profile_id uuid NOT NULL,
  target_id uuid NOT NULL,
  target_type varchar(200) NOT NULL,
  source varchar(200) NOT NULL,
  algorithm_version varchar(200),
  confidence numeric(6,5),
  review_status varchar(200) NOT NULL DEFAULT 'UNREVIEWED',
  first_seen_at timestamptz,
  last_seen_at timestamptz,
  valid_from timestamptz NOT NULL,
  valid_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (review_status IN ('UNREVIEWED','CONFIRMED','REJECTED')),
  CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  CHECK (valid_until IS NULL OR valid_from<valid_until)
);

COMMENT ON TABLE analysis.profile_link IS 'M11 带证据的目标关联';

COMMENT ON COLUMN analysis.profile_link.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.profile_link.profile_id IS 'profile_id；外键 analysis.profile(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_link.target_id IS 'target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.profile_link.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.profile_link.source IS 'source';

COMMENT ON COLUMN analysis.profile_link.algorithm_version IS 'algorithm_version';

COMMENT ON COLUMN analysis.profile_link.confidence IS 'confidence';

COMMENT ON COLUMN analysis.profile_link.review_status IS 'review_status';

COMMENT ON COLUMN analysis.profile_link.first_seen_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.profile_link.last_seen_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.profile_link.valid_from IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.profile_link.valid_until IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.profile_link.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.profile_evidence (
  id uuid NOT NULL,
  profile_id uuid NOT NULL,
  capture_id uuid NOT NULL,
  source varchar(200) NOT NULL,
  source_version varchar(200) NOT NULL,
  relation varchar(200) NOT NULL,
  confidence numeric(6,5),
  collected_at timestamptz NOT NULL,
  review_status varchar(200) NOT NULL DEFAULT 'UNREVIEWED',
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (profile_id,capture_id,source,source_version,relation),
  CHECK (relation IN ('PRIMARY','ASSOCIATED')),
  CHECK (review_status IN ('UNREVIEWED','CONFIRMED','REJECTED')),
  CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1)
);

COMMENT ON TABLE analysis.profile_evidence IS 'M11 原始证据，不被模型或人工覆盖';

COMMENT ON COLUMN analysis.profile_evidence.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.profile_evidence.profile_id IS 'profile_id；外键 analysis.profile(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_evidence.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_evidence.source IS 'source';

COMMENT ON COLUMN analysis.profile_evidence.source_version IS 'source_version';

COMMENT ON COLUMN analysis.profile_evidence.relation IS 'relation';

COMMENT ON COLUMN analysis.profile_evidence.confidence IS 'confidence';

COMMENT ON COLUMN analysis.profile_evidence.collected_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.profile_evidence.review_status IS 'review_status';

COMMENT ON COLUMN analysis.profile_evidence.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.profile_link_evidence (
  link_id uuid NOT NULL,
  evidence_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (link_id,evidence_id)
);

COMMENT ON TABLE analysis.profile_link_evidence IS 'M11 一个关联可有多份证据';

COMMENT ON COLUMN analysis.profile_link_evidence.link_id IS 'link_id；外键 analysis.profile_link(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_link_evidence.evidence_id IS 'evidence_id；外键 analysis.profile_evidence(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_link_evidence.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.profile_revision (
  id uuid NOT NULL,
  profile_id uuid NOT NULL,
  task_id uuid,
  revision_no bigint NOT NULL,
  action varchar(200) NOT NULL,
  reason varchar(1000) NOT NULL,
  actor_user_id uuid NOT NULL,
  before_snapshot jsonb NOT NULL,
  after_snapshot jsonb NOT NULL,
  occurred_at timestamptz NOT NULL,
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (profile_id,revision_no),
  CHECK (action IN ('CORRECT','MERGE','SPLIT','RESOLVE_CONFLICT')),
  CHECK (revision_no >= 0),
  CHECK (jsonb_typeof(before_snapshot) IN ('object','array')),
  CHECK (jsonb_typeof(after_snapshot) IN ('object','array'))
);

COMMENT ON TABLE analysis.profile_revision IS 'M11 人工修订/合并/拆分/冲突处理记录；PENDING: P-016：跨源拟合与合并拆分规则未定；人工动作保留证据及谱系';

COMMENT ON COLUMN analysis.profile_revision.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.profile_revision.profile_id IS 'profile_id；外键 analysis.profile(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_revision.task_id IS 'task_id；外键 analysis.async_task(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_revision.revision_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.profile_revision.action IS 'action';

COMMENT ON COLUMN analysis.profile_revision.reason IS 'reason';

COMMENT ON COLUMN analysis.profile_revision.actor_user_id IS 'actor_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_revision.before_snapshot IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN analysis.profile_revision.after_snapshot IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN analysis.profile_revision.occurred_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.profile_revision.trace_id IS 'trace_id';

COMMENT ON COLUMN analysis.profile_revision.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.profile_revision_member (
  revision_id uuid NOT NULL,
  profile_id uuid NOT NULL,
  direction varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (revision_id,profile_id,direction),
  CHECK (direction IN ('SOURCE','RESULT'))
);

COMMENT ON TABLE analysis.profile_revision_member IS 'M11 合并/拆分来源与结果谱系';

COMMENT ON COLUMN analysis.profile_revision_member.revision_id IS 'revision_id；外键 analysis.profile_revision(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_revision_member.profile_id IS 'profile_id；外键 analysis.profile(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_revision_member.direction IS 'direction';

COMMENT ON COLUMN analysis.profile_revision_member.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.profile_revision_evidence (
  revision_id uuid NOT NULL,
  evidence_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (revision_id,evidence_id)
);

COMMENT ON TABLE analysis.profile_revision_evidence IS 'M11 修订所依据的证据';

COMMENT ON COLUMN analysis.profile_revision_evidence.revision_id IS 'revision_id；外键 analysis.profile_revision(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_revision_evidence.evidence_id IS 'evidence_id；外键 analysis.profile_evidence(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_revision_evidence.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.profile_conflict (
  id uuid NOT NULL,
  profile_id uuid NOT NULL,
  field_code varchar(200) NOT NULL,
  description varchar(2000) NOT NULL,
  candidate_values jsonb NOT NULL,
  status varchar(200) NOT NULL DEFAULT 'OPEN',
  resolved_revision_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (status IN ('OPEN','RESOLVED')),
  CHECK (status!='RESOLVED' OR resolved_revision_id IS NOT NULL),
  CHECK (jsonb_typeof(candidate_values) IN ('object','array'))
);

COMMENT ON TABLE analysis.profile_conflict IS 'M11 冲突字段及处理状态';

COMMENT ON COLUMN analysis.profile_conflict.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.profile_conflict.profile_id IS 'profile_id；外键 analysis.profile(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_conflict.field_code IS 'field_code';

COMMENT ON COLUMN analysis.profile_conflict.description IS 'description';

COMMENT ON COLUMN analysis.profile_conflict.candidate_values IS '候选字段值/来源/版本；敏感值加密或引用证据，不保存明文';

COMMENT ON COLUMN analysis.profile_conflict.status IS 'status';

COMMENT ON COLUMN analysis.profile_conflict.resolved_revision_id IS 'resolved_revision_id；外键 analysis.profile_revision(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_conflict.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.profile_conflict_evidence (
  conflict_id uuid NOT NULL,
  evidence_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (conflict_id,evidence_id)
);

COMMENT ON TABLE analysis.profile_conflict_evidence IS 'M11 冲突证据明细';

COMMENT ON COLUMN analysis.profile_conflict_evidence.conflict_id IS 'conflict_id；外键 analysis.profile_conflict(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_conflict_evidence.evidence_id IS 'evidence_id；外键 analysis.profile_evidence(id)；历史引用保护';

COMMENT ON COLUMN analysis.profile_conflict_evidence.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.person_library (
  id uuid NOT NULL,
  name varchar(200) NOT NULL,
  category_code varchar(200) NOT NULL,
  description varchar(1000),
  owner_user_id uuid NOT NULL,
  management_all_sites boolean NOT NULL DEFAULT false,
  data_all_sites boolean NOT NULL DEFAULT false,
  own_only boolean NOT NULL DEFAULT true,
  scope_version bigint NOT NULL DEFAULT 0,
  scope_policy_version varchar(200) NOT NULL DEFAULT 'PENDING-P002',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CHECK (version >= 0),
  CHECK (scope_version >= 0)
);

COMMENT ON TABLE analysis.person_library IS 'M11 人员库及权限范围；PENDING: P-002：库范围不得扩大当前用户/原始数据权限';

COMMENT ON COLUMN analysis.person_library.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.person_library.name IS 'name';

COMMENT ON COLUMN analysis.person_library.category_code IS 'category_code';

COMMENT ON COLUMN analysis.person_library.description IS 'description';

COMMENT ON COLUMN analysis.person_library.owner_user_id IS 'owner_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.person_library.management_all_sites IS 'management_all_sites';

COMMENT ON COLUMN analysis.person_library.data_all_sites IS 'data_all_sites';

COMMENT ON COLUMN analysis.person_library.own_only IS 'own_only';

COMMENT ON COLUMN analysis.person_library.scope_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.person_library.scope_policy_version IS 'scope_policy_version';

COMMENT ON COLUMN analysis.person_library.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.person_library.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.person_library.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.person_library.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.person_library.deleted IS 'deleted';

COMMENT ON COLUMN analysis.person_library.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE analysis.library_scope_site (
  library_id uuid NOT NULL,
  site_id uuid NOT NULL,
  scope_kind varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (library_id,site_id,scope_kind),
  CHECK (scope_kind IN ('MANAGEMENT','DATA'))
);

COMMENT ON TABLE analysis.library_scope_site IS 'M11 人员库场所范围';

COMMENT ON COLUMN analysis.library_scope_site.library_id IS 'library_id；外键 analysis.person_library(id)；历史引用保护';

COMMENT ON COLUMN analysis.library_scope_site.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN analysis.library_scope_site.scope_kind IS 'scope_kind';

COMMENT ON COLUMN analysis.library_scope_site.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.library_scope_data_type (
  library_id uuid NOT NULL,
  data_type varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (library_id,data_type),
  CHECK (data_type IN ('FENCE','MAC','PLATE','FACE'))
);

COMMENT ON TABLE analysis.library_scope_data_type IS 'M11 人员库数据类型范围';

COMMENT ON COLUMN analysis.library_scope_data_type.library_id IS 'library_id；外键 analysis.person_library(id)；历史引用保护';

COMMENT ON COLUMN analysis.library_scope_data_type.data_type IS 'data_type';

COMMENT ON COLUMN analysis.library_scope_data_type.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.person_library_member (
  id uuid NOT NULL,
  library_id uuid NOT NULL,
  profile_id uuid NOT NULL,
  primary_target_link_id uuid NOT NULL,
  source varchar(200) NOT NULL,
  added_by uuid NOT NULL,
  added_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CHECK (version >= 0)
);

COMMENT ON TABLE analysis.person_library_member IS 'M11 库成员，移除不删除全局档案';

COMMENT ON COLUMN analysis.person_library_member.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.person_library_member.library_id IS 'library_id；外键 analysis.person_library(id)；历史引用保护';

COMMENT ON COLUMN analysis.person_library_member.profile_id IS 'profile_id；外键 analysis.profile(id)；历史引用保护';

COMMENT ON COLUMN analysis.person_library_member.primary_target_link_id IS 'primary_target_link_id；外键 analysis.profile_link(id)；历史引用保护';

COMMENT ON COLUMN analysis.person_library_member.source IS 'source';

COMMENT ON COLUMN analysis.person_library_member.added_by IS 'added_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.person_library_member.added_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.person_library_member.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.person_library_member.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.person_library_member.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.person_library_member.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.person_library_member.deleted IS 'deleted';

COMMENT ON COLUMN analysis.person_library_member.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE analysis.algorithm_version (
  id uuid NOT NULL,
  algorithm_code varchar(200) NOT NULL,
  version_code varchar(200) NOT NULL,
  algorithm_kind varchar(200) NOT NULL,
  parameter_schema_version varchar(200) NOT NULL,
  status varchar(200) NOT NULL DEFAULT 'PENDING',
  effective_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (algorithm_code,version_code),
  CHECK (status IN ('PENDING','APPROVED','RETIRED'))
);

COMMENT ON TABLE analysis.algorithm_version IS 'M11 算法版本登记，未签认不执行';

COMMENT ON COLUMN analysis.algorithm_version.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.algorithm_version.algorithm_code IS 'algorithm_code';

COMMENT ON COLUMN analysis.algorithm_version.version_code IS 'version_code';

COMMENT ON COLUMN analysis.algorithm_version.algorithm_kind IS 'algorithm_kind';

COMMENT ON COLUMN analysis.algorithm_version.parameter_schema_version IS 'parameter_schema_version';

COMMENT ON COLUMN analysis.algorithm_version.status IS 'status';

COMMENT ON COLUMN analysis.algorithm_version.effective_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.algorithm_version.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.face_search_job (
  task_id uuid NOT NULL,
  image_asset_id uuid NOT NULL,
  input_snapshot_id uuid NOT NULL,
  time_from timestamptz NOT NULL,
  time_to timestamptz NOT NULL,
  min_similarity numeric(6,5),
  candidate_limit bigint NOT NULL,
  model_version varchar(200),
  published_attempt_no bigint,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (task_id),
  CHECK (time_from<time_to),
  CHECK (candidate_limit>0),
  CHECK (min_similarity IS NULL OR min_similarity BETWEEN 0 AND 1),
  CHECK (candidate_limit >= 0),
  CHECK (published_attempt_no >= 0)
);

COMMENT ON TABLE analysis.face_search_job IS 'M05 人像搜图任务参数；PENDING: P-007：人像模型、阈值、用途、候选与导出授权未签认';

COMMENT ON COLUMN analysis.face_search_job.task_id IS 'task_id；外键 analysis.async_task(id),analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.face_search_job.image_asset_id IS 'image_asset_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN analysis.face_search_job.input_snapshot_id IS 'input_snapshot_id；外键 analysis.input_snapshot(id)；历史引用保护';

COMMENT ON COLUMN analysis.face_search_job.time_from IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.face_search_job.time_to IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.face_search_job.min_similarity IS 'min_similarity';

COMMENT ON COLUMN analysis.face_search_job.candidate_limit IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.face_search_job.model_version IS 'model_version';

COMMENT ON COLUMN analysis.face_search_job.published_attempt_no IS '非负计数或版本；不使用32位整型计数；外键 analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.face_search_job.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.face_search_result (
  task_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  capture_id uuid NOT NULL,
  similarity numeric(6,5) NOT NULL,
  model_version varchar(200) NOT NULL,
  review_required boolean NOT NULL DEFAULT true,
  rank_no bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (task_id,attempt_no,capture_id),
  UNIQUE (task_id,attempt_no,rank_no),
  CHECK (similarity BETWEEN 0 AND 1),
  CHECK (rank_no>=1),
  CHECK (attempt_no >= 0),
  CHECK (rank_no >= 0)
);

COMMENT ON TABLE analysis.face_search_result IS 'M05 有上限的人像候选结果';

COMMENT ON COLUMN analysis.face_search_result.task_id IS 'task_id；外键 analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.face_search_result.attempt_no IS '非负计数或版本；不使用32位整型计数；外键 analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.face_search_result.capture_id IS 'capture_id；外键 capture.face_record(capture_id)；历史引用保护';

COMMENT ON COLUMN analysis.face_search_result.similarity IS 'similarity';

COMMENT ON COLUMN analysis.face_search_result.model_version IS 'model_version';

COMMENT ON COLUMN analysis.face_search_result.review_required IS 'review_required';

COMMENT ON COLUMN analysis.face_search_result.rank_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.face_search_result.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.trend_model (
  id uuid NOT NULL,
  code varchar(200) NOT NULL,
  name varchar(200) NOT NULL,
  description varchar(1000),
  model_type varchar(200) NOT NULL,
  enabled boolean NOT NULL DEFAULT false,
  current_parameter_version_id uuid,
  filter_schema_version varchar(200) NOT NULL DEFAULT '1',
  effective_data_filter jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE (code),
  CHECK (model_type='RESIDENCE'),
  CHECK (jsonb_typeof(effective_data_filter) IN ('object','array')),
  CHECK (version >= 0)
);

COMMENT ON TABLE analysis.trend_model IS 'M11 趋势模型，当前只启用常驻模型；PENDING: P-017：阈值、规则与标签目录由算法负责人签认；扩展模型需新契约';

COMMENT ON COLUMN analysis.trend_model.id IS '应用生成的UUID业务主键；外键 analysis.model_parameter_version(id,model_id)；历史引用保护';

COMMENT ON COLUMN analysis.trend_model.code IS 'code';

COMMENT ON COLUMN analysis.trend_model.name IS 'name';

COMMENT ON COLUMN analysis.trend_model.description IS 'description';

COMMENT ON COLUMN analysis.trend_model.model_type IS 'model_type';

COMMENT ON COLUMN analysis.trend_model.enabled IS 'enabled';

COMMENT ON COLUMN analysis.trend_model.current_parameter_version_id IS 'current_parameter_version_id；外键 analysis.model_parameter_version(id,model_id)；历史引用保护';

COMMENT ON COLUMN analysis.trend_model.filter_schema_version IS 'filter_schema_version';

COMMENT ON COLUMN analysis.trend_model.effective_data_filter IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN analysis.trend_model.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.trend_model.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.trend_model.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.trend_model.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.trend_model.deleted IS 'deleted';

COMMENT ON COLUMN analysis.trend_model.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE analysis.model_parameter_definition (
  id uuid NOT NULL,
  model_id uuid NOT NULL,
  parameter_code varchar(200) NOT NULL,
  definition_version bigint NOT NULL,
  label varchar(200) NOT NULL,
  value_type varchar(200) NOT NULL,
  unit varchar(200),
  minimum numeric,
  maximum numeric,
  required boolean NOT NULL DEFAULT false,
  description text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (model_id,parameter_code,definition_version),
  UNIQUE (id,value_type),
  CHECK (value_type IN ('TEXT','INTEGER','NUMBER','BOOLEAN','DURATION_SECONDS','DISTANCE_METERS')),
  CHECK (minimum IS NULL OR maximum IS NULL OR minimum<=maximum),
  CHECK (definition_version >= 0)
);

COMMENT ON TABLE analysis.model_parameter_definition IS 'M11 模型参数代码/范围/单位定义';

COMMENT ON COLUMN analysis.model_parameter_definition.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.model_parameter_definition.model_id IS 'model_id；外键 analysis.trend_model(id)；历史引用保护';

COMMENT ON COLUMN analysis.model_parameter_definition.parameter_code IS 'parameter_code';

COMMENT ON COLUMN analysis.model_parameter_definition.definition_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.model_parameter_definition.label IS 'label';

COMMENT ON COLUMN analysis.model_parameter_definition.value_type IS 'value_type';

COMMENT ON COLUMN analysis.model_parameter_definition.unit IS 'unit';

COMMENT ON COLUMN analysis.model_parameter_definition.minimum IS 'minimum';

COMMENT ON COLUMN analysis.model_parameter_definition.maximum IS 'maximum';

COMMENT ON COLUMN analysis.model_parameter_definition.required IS 'required';

COMMENT ON COLUMN analysis.model_parameter_definition.description IS 'description';

COMMENT ON COLUMN analysis.model_parameter_definition.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.model_parameter_version (
  id uuid NOT NULL,
  model_id uuid NOT NULL,
  version_no bigint NOT NULL,
  algorithm_version varchar(200),
  effective_at timestamptz NOT NULL,
  created_by uuid NOT NULL,
  reason varchar(1000) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (model_id,version_no),
  UNIQUE (id,model_id),
  CHECK (version_no >= 0)
);

COMMENT ON TABLE analysis.model_parameter_version IS 'M11 不可覆盖的模型参数版本';

COMMENT ON COLUMN analysis.model_parameter_version.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.model_parameter_version.model_id IS 'model_id；外键 analysis.trend_model(id)；历史引用保护';

COMMENT ON COLUMN analysis.model_parameter_version.version_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN analysis.model_parameter_version.algorithm_version IS 'algorithm_version';

COMMENT ON COLUMN analysis.model_parameter_version.effective_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.model_parameter_version.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN analysis.model_parameter_version.reason IS 'reason';

COMMENT ON COLUMN analysis.model_parameter_version.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.model_parameter_value (
  parameter_version_id uuid NOT NULL,
  definition_id uuid NOT NULL,
  value_type varchar(200) NOT NULL,
  text_value text,
  integer_value bigint,
  number_value numeric,
  boolean_value boolean,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (parameter_version_id,definition_id),
  CHECK (num_nonnulls(text_value,integer_value,number_value,boolean_value)=1 AND ((value_type='TEXT' AND text_value IS NOT NULL) OR (value_type IN ('INTEGER','DURATION_SECONDS') AND integer_value IS NOT NULL) OR (value_type IN ('NUMBER','DISTANCE_METERS') AND number_value IS NOT NULL) OR (value_type='BOOLEAN' AND boolean_value IS NOT NULL)))
);

COMMENT ON TABLE analysis.model_parameter_value IS 'M11 强类型模型参数值';

COMMENT ON COLUMN analysis.model_parameter_value.parameter_version_id IS 'parameter_version_id；外键 analysis.model_parameter_version(id)；历史引用保护';

COMMENT ON COLUMN analysis.model_parameter_value.definition_id IS 'definition_id；外键 analysis.model_parameter_definition(id,value_type)；历史引用保护';

COMMENT ON COLUMN analysis.model_parameter_value.value_type IS 'value_type；外键 analysis.model_parameter_definition(id,value_type)；历史引用保护';

COMMENT ON COLUMN analysis.model_parameter_value.text_value IS 'text_value';

COMMENT ON COLUMN analysis.model_parameter_value.integer_value IS 'integer_value';

COMMENT ON COLUMN analysis.model_parameter_value.number_value IS 'number_value';

COMMENT ON COLUMN analysis.model_parameter_value.boolean_value IS 'boolean_value';

COMMENT ON COLUMN analysis.model_parameter_value.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.model_matching_rule (
  parameter_version_id uuid NOT NULL,
  rule_code varchar(200) NOT NULL,
  label varchar(200) NOT NULL,
  algorithm_version varchar(200) NOT NULL,
  description text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (parameter_version_id,rule_code)
);

COMMENT ON TABLE analysis.model_matching_rule IS 'M11 版本化匹配规则引用';

COMMENT ON COLUMN analysis.model_matching_rule.parameter_version_id IS 'parameter_version_id；外键 analysis.model_parameter_version(id)；历史引用保护';

COMMENT ON COLUMN analysis.model_matching_rule.rule_code IS 'rule_code';

COMMENT ON COLUMN analysis.model_matching_rule.label IS 'label';

COMMENT ON COLUMN analysis.model_matching_rule.algorithm_version IS 'algorithm_version';

COMMENT ON COLUMN analysis.model_matching_rule.description IS 'description';

COMMENT ON COLUMN analysis.model_matching_rule.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.model_tag (
  tag_code varchar(200) NOT NULL,
  label varchar(200) NOT NULL,
  description varchar(1000),
  enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (tag_code)
);

COMMENT ON TABLE analysis.model_tag IS 'M11 标签目录';

COMMENT ON COLUMN analysis.model_tag.tag_code IS 'tag_code';

COMMENT ON COLUMN analysis.model_tag.label IS 'label';

COMMENT ON COLUMN analysis.model_tag.description IS 'description';

COMMENT ON COLUMN analysis.model_tag.enabled IS 'enabled';

COMMENT ON COLUMN analysis.model_tag.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.model_tag_binding (
  model_id uuid NOT NULL,
  tag_code varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (model_id,tag_code)
);

COMMENT ON TABLE analysis.model_tag_binding IS 'M11 模型配置标签';

COMMENT ON COLUMN analysis.model_tag_binding.model_id IS 'model_id；外键 analysis.trend_model(id)；历史引用保护';

COMMENT ON COLUMN analysis.model_tag_binding.tag_code IS 'tag_code；外键 analysis.model_tag(tag_code)；历史引用保护';

COMMENT ON COLUMN analysis.model_tag_binding.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.model_rule_output_tag (
  parameter_version_id uuid NOT NULL,
  rule_code varchar(200) NOT NULL,
  tag_code varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (parameter_version_id,rule_code,tag_code)
);

COMMENT ON TABLE analysis.model_rule_output_tag IS 'M11 规则的输出标签';

COMMENT ON COLUMN analysis.model_rule_output_tag.parameter_version_id IS 'parameter_version_id；外键 analysis.model_matching_rule(parameter_version_id,rule_code)；历史引用保护';

COMMENT ON COLUMN analysis.model_rule_output_tag.rule_code IS 'rule_code；外键 analysis.model_matching_rule(parameter_version_id,rule_code)；历史引用保护';

COMMENT ON COLUMN analysis.model_rule_output_tag.tag_code IS 'tag_code；外键 analysis.model_tag(tag_code)；历史引用保护';

COMMENT ON COLUMN analysis.model_rule_output_tag.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.model_execution (
  task_id uuid NOT NULL,
  model_id uuid NOT NULL,
  parameter_version_id uuid NOT NULL,
  input_snapshot_id uuid NOT NULL,
  published_attempt_no bigint,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (task_id),
  CHECK (published_attempt_no >= 0)
);

COMMENT ON TABLE analysis.model_execution IS 'M11 固定输入与参数版本的一次模型执行';

COMMENT ON COLUMN analysis.model_execution.task_id IS 'task_id；外键 analysis.async_task(id),analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.model_execution.model_id IS 'model_id；外键 analysis.model_parameter_version(id,model_id)；历史引用保护';

COMMENT ON COLUMN analysis.model_execution.parameter_version_id IS 'parameter_version_id；外键 analysis.model_parameter_version(id,model_id)；历史引用保护';

COMMENT ON COLUMN analysis.model_execution.input_snapshot_id IS 'input_snapshot_id；外键 analysis.input_snapshot(id)；历史引用保护';

COMMENT ON COLUMN analysis.model_execution.published_attempt_no IS '非负计数或版本；不使用32位整型计数；外键 analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.model_execution.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.model_result (
  id uuid NOT NULL,
  execution_task_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  target_id uuid NOT NULL,
  target_type varchar(200) NOT NULL,
  data_type varchar(200) NOT NULL,
  confidence numeric(6,5),
  algorithm_version varchar(200) NOT NULL,
  generated_at timestamptz NOT NULL,
  review_status varchar(200) NOT NULL DEFAULT 'UNREVIEWED',
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (execution_task_id,attempt_no,target_id,data_type),
  CHECK (data_type IN ('FENCE','MAC','PLATE','FACE')),
  CHECK (review_status IN ('UNREVIEWED','CONFIRMED','REJECTED')),
  CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  CHECK (attempt_no >= 0)
);

COMMENT ON TABLE analysis.model_result IS 'M11 逐目标模型结果';

COMMENT ON COLUMN analysis.model_result.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.model_result.execution_task_id IS 'execution_task_id；外键 analysis.model_execution(task_id),analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.model_result.attempt_no IS '非负计数或版本；不使用32位整型计数；外键 analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN analysis.model_result.target_id IS 'target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.model_result.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN analysis.model_result.data_type IS 'data_type';

COMMENT ON COLUMN analysis.model_result.confidence IS 'confidence';

COMMENT ON COLUMN analysis.model_result.algorithm_version IS 'algorithm_version';

COMMENT ON COLUMN analysis.model_result.generated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN analysis.model_result.review_status IS 'review_status';

COMMENT ON COLUMN analysis.model_result.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.model_result_tag (
  result_id uuid NOT NULL,
  tag_code varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (result_id,tag_code)
);

COMMENT ON TABLE analysis.model_result_tag IS 'M11 结果标签';

COMMENT ON COLUMN analysis.model_result_tag.result_id IS 'result_id；外键 analysis.model_result(id)；历史引用保护';

COMMENT ON COLUMN analysis.model_result_tag.tag_code IS 'tag_code；外键 analysis.model_tag(tag_code)；历史引用保护';

COMMENT ON COLUMN analysis.model_result_tag.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.model_result_evidence (
  id uuid NOT NULL,
  result_id uuid NOT NULL,
  capture_id uuid,
  profile_evidence_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (num_nonnulls(capture_id,profile_evidence_id)=1)
);

COMMENT ON TABLE analysis.model_result_evidence IS 'M11 模型证据与采集引用';

COMMENT ON COLUMN analysis.model_result_evidence.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN analysis.model_result_evidence.result_id IS 'result_id；外键 analysis.model_result(id)；历史引用保护';

COMMENT ON COLUMN analysis.model_result_evidence.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN analysis.model_result_evidence.profile_evidence_id IS 'profile_evidence_id；外键 analysis.profile_evidence(id)；历史引用保护';

COMMENT ON COLUMN analysis.model_result_evidence.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.watch_rule (
  id uuid NOT NULL,
  name varchar(200) NOT NULL,
  owner_user_id uuid NOT NULL,
  context_id uuid NOT NULL,
  status varchar(200) NOT NULL DEFAULT 'DRAFT',
  effective_from timestamptz,
  effective_until timestamptz,
  current_rule_version bigint NOT NULL DEFAULT 1,
  ended_at timestamptz,
  hit_count bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CHECK (status IN ('DRAFT','ACTIVE','REVOKED','FINISHED')),
  CHECK (effective_from IS NULL OR effective_until IS NULL OR effective_from<effective_until),
  CHECK (status!='ACTIVE' OR (effective_from IS NOT NULL AND effective_until IS NOT NULL)),
  CHECK (hit_count >= 0),
  CHECK (version >= 0),
  CHECK (current_rule_version=version),
  CHECK (current_rule_version >= 0)
);

COMMENT ON TABLE operation.watch_rule IS 'M10 布控规则及当前版本';

COMMENT ON COLUMN operation.watch_rule.id IS '应用生成的UUID业务主键；外键 operation.watch_rule_revision(rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule.name IS 'name';

COMMENT ON COLUMN operation.watch_rule.owner_user_id IS 'owner_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule.context_id IS 'context_id；外键 analysis.analysis_context(id)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule.status IS 'status';

COMMENT ON COLUMN operation.watch_rule.effective_from IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.watch_rule.effective_until IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.watch_rule.current_rule_version IS '非负计数或版本；不使用32位整型计数；外键 operation.watch_rule_revision(rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule.ended_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.watch_rule.hit_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.watch_rule.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.watch_rule.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.watch_rule.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule.deleted IS 'deleted';

COMMENT ON COLUMN operation.watch_rule.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE operation.watch_rule_revision (
  rule_id uuid NOT NULL,
  rule_version bigint NOT NULL,
  name_snapshot varchar(200) NOT NULL,
  effective_from timestamptz,
  effective_until timestamptz,
  match_rule_code varchar(200),
  match_rule_version varchar(200),
  data_types text[] NOT NULL DEFAULT '{}'::text[],
  notification_template_code varchar(200),
  notification_template_version varchar(200),
  condition_schema_version varchar(200) NOT NULL DEFAULT '1',
  condition_snapshot jsonb NOT NULL,
  notification_snapshot jsonb,
  created_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (rule_id,rule_version),
  CHECK (data_types <@ ARRAY['FENCE','MAC','PLATE','FACE']::text[]),
  CHECK (cardinality(data_types)>0),
  CHECK (effective_from IS NULL OR effective_until IS NULL OR effective_from<effective_until),
  CHECK ((notification_template_code IS NULL)=(notification_template_version IS NULL)),
  CHECK (jsonb_typeof(condition_snapshot) IN ('object','array')),
  CHECK (jsonb_typeof(notification_snapshot) IN ('object','array')),
  CHECK (rule_version >= 0)
);

COMMENT ON TABLE operation.watch_rule_revision IS 'M10 规则参数和通知快照；PENDING: P-014/P-015：布控匹配、告警去重、频控与通知规则未签认';

COMMENT ON COLUMN operation.watch_rule_revision.rule_id IS 'rule_id；外键 operation.watch_rule(id)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_revision.rule_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.watch_rule_revision.name_snapshot IS 'name_snapshot';

COMMENT ON COLUMN operation.watch_rule_revision.effective_from IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.watch_rule_revision.effective_until IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.watch_rule_revision.match_rule_code IS 'match_rule_code';

COMMENT ON COLUMN operation.watch_rule_revision.match_rule_version IS 'match_rule_version';

COMMENT ON COLUMN operation.watch_rule_revision.data_types IS '有限类型数组；不代替需要外键的业务对象关系';

COMMENT ON COLUMN operation.watch_rule_revision.notification_template_code IS 'notification_template_code；外键 operation.notification_template(template_code,template_version)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_revision.notification_template_version IS 'notification_template_version；外键 operation.notification_template(template_code,template_version)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_revision.condition_schema_version IS 'condition_schema_version';

COMMENT ON COLUMN operation.watch_rule_revision.condition_snapshot IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN operation.watch_rule_revision.notification_snapshot IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN operation.watch_rule_revision.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_revision.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.watch_rule_target (
  rule_id uuid NOT NULL,
  rule_version bigint NOT NULL,
  target_id uuid NOT NULL,
  target_type varchar(200) NOT NULL,
  source_profile_id uuid,
  source_library_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (rule_id,rule_version,target_id),
  CHECK (rule_version >= 0)
);

COMMENT ON TABLE operation.watch_rule_target IS 'M10 各版规则目标及来源档案';

COMMENT ON COLUMN operation.watch_rule_target.rule_id IS 'rule_id；外键 operation.watch_rule_revision(rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_target.rule_version IS '非负计数或版本；不使用32位整型计数；外键 operation.watch_rule_revision(rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_target.target_id IS 'target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_target.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_target.source_profile_id IS 'source_profile_id；外键 analysis.profile(id)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_target.source_library_id IS 'source_library_id；外键 analysis.person_library(id)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_target.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.watch_rule_site (
  rule_id uuid NOT NULL,
  rule_version bigint NOT NULL,
  site_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (rule_id,rule_version,site_id),
  CHECK (rule_version >= 0)
);

COMMENT ON TABLE operation.watch_rule_site IS 'M10 各版规则场所范围';

COMMENT ON COLUMN operation.watch_rule_site.rule_id IS 'rule_id；外键 operation.watch_rule_revision(rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_site.rule_version IS '非负计数或版本；不使用32位整型计数；外键 operation.watch_rule_revision(rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_site.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN operation.watch_rule_site.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.alert_event (
  id uuid NOT NULL,
  rule_id uuid NOT NULL,
  rule_version bigint NOT NULL,
  capture_id uuid NOT NULL,
  target_id uuid NOT NULL,
  target_type varchar(200) NOT NULL,
  site_id uuid,
  device_id uuid,
  occurred_at timestamptz NOT NULL,
  severity varchar(200) NOT NULL,
  status varchar(200) NOT NULL DEFAULT 'OPEN',
  dedup_key varchar(200) NOT NULL,
  dedup_rule_version varchar(200) NOT NULL,
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (dedup_key),
  UNIQUE (id,rule_id,rule_version),
  CHECK (severity IN ('LOW','MEDIUM','HIGH')),
  CHECK (status IN ('OPEN','ACKNOWLEDGED','CLOSED')),
  CHECK (rule_version >= 0)
);

COMMENT ON TABLE operation.alert_event IS 'M10 命中事实及通知关联';

COMMENT ON COLUMN operation.alert_event.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.alert_event.rule_id IS 'rule_id；外键 operation.watch_rule_revision(rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.alert_event.rule_version IS '非负计数或版本；不使用32位整型计数；外键 operation.watch_rule_revision(rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.alert_event.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN operation.alert_event.target_id IS 'target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN operation.alert_event.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN operation.alert_event.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN operation.alert_event.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN operation.alert_event.occurred_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.alert_event.severity IS 'severity';

COMMENT ON COLUMN operation.alert_event.status IS 'status';

COMMENT ON COLUMN operation.alert_event.dedup_key IS 'dedup_key';

COMMENT ON COLUMN operation.alert_event.dedup_rule_version IS 'dedup_rule_version';

COMMENT ON COLUMN operation.alert_event.trace_id IS 'trace_id';

COMMENT ON COLUMN operation.alert_event.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.watch_statistics_run (
  rule_id uuid NOT NULL,
  final_rule_version bigint NOT NULL,
  final boolean NOT NULL DEFAULT true,
  hit_count bigint NOT NULL,
  distinct_target_count bigint NOT NULL,
  site_count bigint NOT NULL,
  region_count bigint NOT NULL,
  first_seen_at timestamptz,
  as_of timestamptz NOT NULL,
  metric_definition_version varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (rule_id),
  CHECK (final=true),
  CHECK (hit_count >= 0),
  CHECK (distinct_target_count >= 0),
  CHECK (site_count >= 0),
  CHECK (region_count >= 0),
  CHECK (final_rule_version >= 0)
);

COMMENT ON TABLE operation.watch_statistics_run IS 'M10 规则结束后的统计发布元信息';

COMMENT ON COLUMN operation.watch_statistics_run.rule_id IS 'rule_id；外键 operation.watch_rule_revision(rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.watch_statistics_run.final_rule_version IS '非负计数或版本；不使用32位整型计数；外键 operation.watch_rule_revision(rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.watch_statistics_run.final IS 'final';

COMMENT ON COLUMN operation.watch_statistics_run.hit_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.watch_statistics_run.distinct_target_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.watch_statistics_run.site_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.watch_statistics_run.region_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.watch_statistics_run.first_seen_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.watch_statistics_run.as_of IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.watch_statistics_run.metric_definition_version IS 'metric_definition_version';

COMMENT ON COLUMN operation.watch_statistics_run.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.watch_statistics (
  rule_id uuid NOT NULL,
  target_id uuid NOT NULL,
  target_type varchar(200) NOT NULL,
  capture_count bigint NOT NULL,
  site_count bigint NOT NULL,
  region_count bigint NOT NULL,
  first_seen_at timestamptz,
  most_frequent_site_id uuid,
  most_frequent_site_name_snapshot varchar(200),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (rule_id,target_id),
  CHECK (capture_count >= 0),
  CHECK (site_count >= 0),
  CHECK (region_count >= 0)
);

COMMENT ON TABLE operation.watch_statistics IS 'M10 结束后逐目标集群统计';

COMMENT ON COLUMN operation.watch_statistics.rule_id IS 'rule_id；外键 operation.watch_statistics_run(rule_id)；历史引用保护';

COMMENT ON COLUMN operation.watch_statistics.target_id IS 'target_id；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN operation.watch_statistics.target_type IS 'target_type；外键 capture.target(id,target_type)；历史引用保护';

COMMENT ON COLUMN operation.watch_statistics.capture_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.watch_statistics.site_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.watch_statistics.region_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.watch_statistics.first_seen_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.watch_statistics.most_frequent_site_id IS 'most_frequent_site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN operation.watch_statistics.most_frequent_site_name_snapshot IS 'most_frequent_site_name_snapshot';

COMMENT ON COLUMN operation.watch_statistics.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.notification_channel (
  id uuid NOT NULL,
  channel_type varchar(200) NOT NULL,
  adapter_code varchar(200) NOT NULL,
  adapter_version varchar(200) NOT NULL,
  credential_reference varchar(500),
  enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (channel_type IN ('SMS','EMAIL','PLATFORM'))
);

COMMENT ON TABLE operation.notification_channel IS 'M10 通道适配目录与外部凭据引用；PENDING: P-015：短信猫、邮件协议、模板、限额、凭据与回执未签认';

COMMENT ON COLUMN operation.notification_channel.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.notification_channel.channel_type IS 'channel_type';

COMMENT ON COLUMN operation.notification_channel.adapter_code IS 'adapter_code';

COMMENT ON COLUMN operation.notification_channel.adapter_version IS 'adapter_version';

COMMENT ON COLUMN operation.notification_channel.credential_reference IS 'credential_reference';

COMMENT ON COLUMN operation.notification_channel.enabled IS 'enabled';

COMMENT ON COLUMN operation.notification_channel.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.notification_template (
  template_code varchar(200) NOT NULL,
  template_version varchar(200) NOT NULL,
  name varchar(200) NOT NULL,
  purpose varchar(200) NOT NULL,
  channels text[] NOT NULL DEFAULT '{}'::text[],
  title_template varchar(500),
  body_template text NOT NULL,
  enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (template_code,template_version),
  CHECK (purpose IN ('WATCH_ALERT','DEVICE_STATUS')),
  CHECK (channels <@ ARRAY['SMS','EMAIL','PLATFORM']::text[]),
  CHECK (cardinality(channels)>0)
);

COMMENT ON TABLE operation.notification_template IS 'M10 已批准通知模板的不可变版本';

COMMENT ON COLUMN operation.notification_template.template_code IS 'template_code';

COMMENT ON COLUMN operation.notification_template.template_version IS 'template_version';

COMMENT ON COLUMN operation.notification_template.name IS 'name';

COMMENT ON COLUMN operation.notification_template.purpose IS 'purpose';

COMMENT ON COLUMN operation.notification_template.channels IS '有限类型数组；不代替需要外键的业务对象关系';

COMMENT ON COLUMN operation.notification_template.title_template IS 'title_template';

COMMENT ON COLUMN operation.notification_template.body_template IS 'body_template';

COMMENT ON COLUMN operation.notification_template.enabled IS 'enabled';

COMMENT ON COLUMN operation.notification_template.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.notification_template_variable (
  template_code varchar(200) NOT NULL,
  template_version varchar(200) NOT NULL,
  variable_code varchar(200) NOT NULL,
  description varchar(1000) NOT NULL,
  required boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (template_code,template_version,variable_code)
);

COMMENT ON TABLE operation.notification_template_variable IS 'M10 允许的模板变量';

COMMENT ON COLUMN operation.notification_template_variable.template_code IS 'template_code；外键 operation.notification_template(template_code,template_version)；历史引用保护';

COMMENT ON COLUMN operation.notification_template_variable.template_version IS 'template_version；外键 operation.notification_template(template_code,template_version)；历史引用保护';

COMMENT ON COLUMN operation.notification_template_variable.variable_code IS 'variable_code';

COMMENT ON COLUMN operation.notification_template_variable.description IS 'description';

COMMENT ON COLUMN operation.notification_template_variable.required IS 'required';

COMMENT ON COLUMN operation.notification_template_variable.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.notification_plan (
  id uuid NOT NULL,
  name varchar(200) NOT NULL,
  owner_user_id uuid NOT NULL,
  status varchar(200) NOT NULL DEFAULT 'ACTIVE',
  schedule_type varchar(200) NOT NULL,
  scheduled_at timestamptz,
  local_time time without time zone,
  time_zone varchar(200),
  start_date date,
  end_date date,
  next_run_at timestamptz,
  last_sent_at timestamptz,
  last_execution_task_id uuid,
  current_plan_version bigint NOT NULL DEFAULT 1,
  cancelled_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid,
  deleted boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CHECK (status IN ('ACTIVE','CANCELLED','COMPLETED')),
  CHECK ((schedule_type='ONCE' AND scheduled_at IS NOT NULL AND num_nonnulls(local_time,time_zone,start_date,end_date)=0) OR (schedule_type='DAILY' AND scheduled_at IS NULL AND local_time IS NOT NULL AND time_zone='Asia/Shanghai' AND start_date IS NOT NULL AND (end_date IS NULL OR start_date<=end_date))),
  CHECK (version >= 0),
  CHECK (current_plan_version=version),
  CHECK (current_plan_version >= 0)
);

COMMENT ON TABLE operation.notification_plan IS 'M10 ONCE/DAILY通知计划，同步创建';

COMMENT ON COLUMN operation.notification_plan.id IS '应用生成的UUID业务主键；外键 operation.notification_plan_revision(plan_id,plan_version)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan.name IS 'name';

COMMENT ON COLUMN operation.notification_plan.owner_user_id IS 'owner_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan.status IS 'status';

COMMENT ON COLUMN operation.notification_plan.schedule_type IS 'schedule_type';

COMMENT ON COLUMN operation.notification_plan.scheduled_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.notification_plan.local_time IS 'local_time';

COMMENT ON COLUMN operation.notification_plan.time_zone IS 'time_zone';

COMMENT ON COLUMN operation.notification_plan.start_date IS 'start_date';

COMMENT ON COLUMN operation.notification_plan.end_date IS 'end_date';

COMMENT ON COLUMN operation.notification_plan.next_run_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.notification_plan.last_sent_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.notification_plan.last_execution_task_id IS 'last_execution_task_id；外键 analysis.async_task(id)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan.current_plan_version IS '非负计数或版本；不使用32位整型计数；外键 operation.notification_plan_revision(plan_id,plan_version)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan.cancelled_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.notification_plan.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.notification_plan.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.notification_plan.updated_by IS 'updated_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan.deleted IS 'deleted';

COMMENT ON COLUMN operation.notification_plan.version IS '非负计数或版本；不使用32位整型计数';

CREATE TABLE operation.notification_plan_revision (
  plan_id uuid NOT NULL,
  plan_version bigint NOT NULL,
  name_snapshot varchar(200) NOT NULL,
  template_code varchar(200) NOT NULL,
  template_version varchar(200) NOT NULL,
  schedule_snapshot jsonb NOT NULL,
  created_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (plan_id,plan_version),
  CHECK (jsonb_typeof(schedule_snapshot) IN ('object','array')),
  CHECK (plan_version >= 0)
);

COMMENT ON TABLE operation.notification_plan_revision IS 'M10 模板/定时规则版本快照';

COMMENT ON COLUMN operation.notification_plan_revision.plan_id IS 'plan_id；外键 operation.notification_plan(id)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan_revision.plan_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.notification_plan_revision.name_snapshot IS 'name_snapshot';

COMMENT ON COLUMN operation.notification_plan_revision.template_code IS 'template_code；外键 operation.notification_template(template_code,template_version)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan_revision.template_version IS 'template_version；外键 operation.notification_template(template_code,template_version)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan_revision.schedule_snapshot IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN operation.notification_plan_revision.created_by IS 'created_by；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan_revision.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.notification_recipient (
  id uuid NOT NULL,
  channel_type varchar(200) NOT NULL,
  recipient_user_id uuid,
  recipient_cipher bytea,
  recipient_hash bytea,
  hmac_key_version varchar(200),
  cipher_key_version varchar(200),
  normalization_version varchar(200),
  owner_user_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK ((channel_type='PLATFORM' AND recipient_user_id IS NOT NULL AND num_nonnulls(recipient_cipher,recipient_hash,hmac_key_version,cipher_key_version,normalization_version)=0) OR (channel_type IN ('SMS','EMAIL') AND recipient_user_id IS NULL AND recipient_cipher IS NOT NULL AND recipient_hash IS NOT NULL AND hmac_key_version IS NOT NULL AND cipher_key_version IS NOT NULL AND normalization_version IS NOT NULL))
);

COMMENT ON TABLE operation.notification_recipient IS 'M10 已授权收件人代理标识';

COMMENT ON COLUMN operation.notification_recipient.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.notification_recipient.channel_type IS 'channel_type';

COMMENT ON COLUMN operation.notification_recipient.recipient_user_id IS 'recipient_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.notification_recipient.recipient_cipher IS 'recipient_cipher';

COMMENT ON COLUMN operation.notification_recipient.recipient_hash IS 'recipient_hash';

COMMENT ON COLUMN operation.notification_recipient.hmac_key_version IS 'hmac_key_version';

COMMENT ON COLUMN operation.notification_recipient.cipher_key_version IS 'cipher_key_version';

COMMENT ON COLUMN operation.notification_recipient.normalization_version IS 'normalization_version';

COMMENT ON COLUMN operation.notification_recipient.owner_user_id IS 'owner_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.notification_recipient.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.notification_plan_recipient (
  plan_id uuid NOT NULL,
  plan_version bigint NOT NULL,
  recipient_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (plan_id,plan_version,recipient_id),
  CHECK (plan_version >= 0)
);

COMMENT ON TABLE operation.notification_plan_recipient IS 'M10 固定计划版本的收件人';

COMMENT ON COLUMN operation.notification_plan_recipient.plan_id IS 'plan_id；外键 operation.notification_plan_revision(plan_id,plan_version)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan_recipient.plan_version IS '非负计数或版本；不使用32位整型计数；外键 operation.notification_plan_revision(plan_id,plan_version)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan_recipient.recipient_id IS 'recipient_id；外键 operation.notification_recipient(id)；历史引用保护';

COMMENT ON COLUMN operation.notification_plan_recipient.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.watch_notification_recipient (
  rule_id uuid NOT NULL,
  rule_version bigint NOT NULL,
  recipient_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (rule_id,rule_version,recipient_id),
  CHECK (rule_version >= 0)
);

COMMENT ON TABLE operation.watch_notification_recipient IS 'M10 固定布控版本的收件人';

COMMENT ON COLUMN operation.watch_notification_recipient.rule_id IS 'rule_id；外键 operation.watch_rule_revision(rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.watch_notification_recipient.rule_version IS '非负计数或版本；不使用32位整型计数；外键 operation.watch_rule_revision(rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.watch_notification_recipient.recipient_id IS 'recipient_id；外键 operation.notification_recipient(id)；历史引用保护';

COMMENT ON COLUMN operation.watch_notification_recipient.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.notification_execution (
  task_id uuid NOT NULL,
  plan_id uuid NOT NULL,
  plan_version bigint NOT NULL,
  occurrence_key varchar(200) NOT NULL,
  scheduled_for timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (task_id),
  UNIQUE (plan_id,occurrence_key),
  UNIQUE (task_id,plan_id,plan_version),
  CHECK (plan_version >= 0)
);

COMMENT ON TABLE operation.notification_execution IS 'M10 计划每一次执行；重试同taskId；PENDING: P-015：错过执行补发、频控与去重窗口未定，不盲目补发';

COMMENT ON COLUMN operation.notification_execution.task_id IS 'task_id；外键 analysis.async_task(id)；历史引用保护';

COMMENT ON COLUMN operation.notification_execution.plan_id IS 'plan_id；外键 operation.notification_plan_revision(plan_id,plan_version)；历史引用保护';

COMMENT ON COLUMN operation.notification_execution.plan_version IS '非负计数或版本；不使用32位整型计数；外键 operation.notification_plan_revision(plan_id,plan_version)；历史引用保护';

COMMENT ON COLUMN operation.notification_execution.occurrence_key IS 'occurrence_key';

COMMENT ON COLUMN operation.notification_execution.scheduled_for IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.notification_execution.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.delivery (
  id uuid NOT NULL,
  execution_task_id uuid,
  plan_id uuid,
  plan_version bigint,
  alert_id uuid,
  rule_id uuid,
  rule_version bigint,
  recipient_id uuid NOT NULL,
  channel_type varchar(200) NOT NULL,
  channel_id uuid,
  template_code varchar(200) NOT NULL,
  template_version varchar(200) NOT NULL,
  task_name_snapshot varchar(200) NOT NULL,
  content_summary varchar(4000) NOT NULL,
  content_cipher bytea,
  cipher_key_version varchar(200),
  status varchar(200) NOT NULL DEFAULT 'PENDING',
  dedup_key varchar(200) NOT NULL,
  retry_count bigint NOT NULL DEFAULT 0,
  sent_at timestamptz,
  acknowledged_at timestamptz,
  provider_message_id varchar(200),
  provider_receipt_ref varchar(500),
  error_code varchar(200),
  error_reason varchar(2000),
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (dedup_key),
  CHECK (status IN ('PENDING','SENT','ACKNOWLEDGED','FAILED','UNKNOWN')),
  CHECK (channel_type IN ('SMS','EMAIL','PLATFORM')),
  CHECK ((plan_id IS NOT NULL AND plan_version IS NOT NULL AND execution_task_id IS NOT NULL) OR (alert_id IS NOT NULL AND rule_id IS NOT NULL AND rule_version IS NOT NULL)),
  CHECK ((content_cipher IS NULL)=(cipher_key_version IS NULL)),
  CHECK (retry_count >= 0),
  CHECK (plan_version >= 0),
  CHECK (rule_version >= 0),
  CHECK ((plan_id IS NOT NULL AND alert_id IS NULL AND rule_id IS NULL AND rule_version IS NULL) OR (alert_id IS NOT NULL AND plan_id IS NULL AND plan_version IS NULL AND execution_task_id IS NULL))
);

COMMENT ON TABLE operation.delivery IS 'M10 每个收件人/通道的一次投递事实，短信日志共用';

COMMENT ON COLUMN operation.delivery.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.delivery.execution_task_id IS 'execution_task_id；外键 analysis.async_task(id),operation.notification_execution(task_id,plan_id,plan_version)；历史引用保护';

COMMENT ON COLUMN operation.delivery.plan_id IS 'plan_id；外键 operation.notification_plan_revision(plan_id,plan_version),operation.notification_execution(task_id,plan_id,plan_version)；历史引用保护';

COMMENT ON COLUMN operation.delivery.plan_version IS '非负计数或版本；不使用32位整型计数；外键 operation.notification_plan_revision(plan_id,plan_version),operation.notification_execution(task_id,plan_id,plan_version)；历史引用保护';

COMMENT ON COLUMN operation.delivery.alert_id IS 'alert_id；外键 operation.alert_event(id),operation.alert_event(id,rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.delivery.rule_id IS 'rule_id；外键 operation.watch_rule_revision(rule_id,rule_version),operation.alert_event(id,rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.delivery.rule_version IS '非负计数或版本；不使用32位整型计数；外键 operation.watch_rule_revision(rule_id,rule_version),operation.alert_event(id,rule_id,rule_version)；历史引用保护';

COMMENT ON COLUMN operation.delivery.recipient_id IS 'recipient_id；外键 operation.notification_recipient(id)；历史引用保护';

COMMENT ON COLUMN operation.delivery.channel_type IS 'channel_type';

COMMENT ON COLUMN operation.delivery.channel_id IS 'channel_id；外键 operation.notification_channel(id)；历史引用保护';

COMMENT ON COLUMN operation.delivery.template_code IS 'template_code；外键 operation.notification_template(template_code,template_version)；历史引用保护';

COMMENT ON COLUMN operation.delivery.template_version IS 'template_version；外键 operation.notification_template(template_code,template_version)；历史引用保护';

COMMENT ON COLUMN operation.delivery.task_name_snapshot IS 'task_name_snapshot';

COMMENT ON COLUMN operation.delivery.content_summary IS 'content_summary';

COMMENT ON COLUMN operation.delivery.content_cipher IS 'content_cipher';

COMMENT ON COLUMN operation.delivery.cipher_key_version IS 'cipher_key_version';

COMMENT ON COLUMN operation.delivery.status IS 'status';

COMMENT ON COLUMN operation.delivery.dedup_key IS 'dedup_key';

COMMENT ON COLUMN operation.delivery.retry_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.delivery.sent_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.delivery.acknowledged_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.delivery.provider_message_id IS 'provider_message_id';

COMMENT ON COLUMN operation.delivery.provider_receipt_ref IS 'provider_receipt_ref';

COMMENT ON COLUMN operation.delivery.error_code IS 'error_code';

COMMENT ON COLUMN operation.delivery.error_reason IS 'error_reason';

COMMENT ON COLUMN operation.delivery.trace_id IS 'trace_id';

COMMENT ON COLUMN operation.delivery.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.delivery_attempt (
  delivery_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  requested_at timestamptz NOT NULL,
  responded_at timestamptz,
  status varchar(200) NOT NULL,
  provider_receipt_ref varchar(500),
  error_code varchar(200),
  error_reason varchar(2000),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (delivery_id,attempt_no),
  CHECK (status IN ('PENDING','SENT','ACKNOWLEDGED','FAILED','UNKNOWN')),
  CHECK (attempt_no>=1),
  CHECK (attempt_no >= 0)
);

COMMENT ON TABLE operation.delivery_attempt IS 'M10 发送尝试/回执/失败原因';

COMMENT ON COLUMN operation.delivery_attempt.delivery_id IS 'delivery_id；外键 operation.delivery(id)；历史引用保护';

COMMENT ON COLUMN operation.delivery_attempt.attempt_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.delivery_attempt.requested_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.delivery_attempt.responded_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.delivery_attempt.status IS 'status';

COMMENT ON COLUMN operation.delivery_attempt.provider_receipt_ref IS 'provider_receipt_ref';

COMMENT ON COLUMN operation.delivery_attempt.error_code IS 'error_code';

COMMENT ON COLUMN operation.delivery_attempt.error_reason IS 'error_reason';

COMMENT ON COLUMN operation.delivery_attempt.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.platform_message (
  id uuid NOT NULL,
  delivery_id uuid NOT NULL,
  recipient_user_id uuid NOT NULL,
  alert_id uuid,
  plan_id uuid,
  title varchar(500) NOT NULL,
  content text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (delivery_id)
);

COMMENT ON TABLE operation.platform_message IS 'M10 平台消息送达内容';

COMMENT ON COLUMN operation.platform_message.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.platform_message.delivery_id IS 'delivery_id；外键 operation.delivery(id)；历史引用保护';

COMMENT ON COLUMN operation.platform_message.recipient_user_id IS 'recipient_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.platform_message.alert_id IS 'alert_id；外键 operation.alert_event(id)；历史引用保护';

COMMENT ON COLUMN operation.platform_message.plan_id IS 'plan_id；外键 operation.notification_plan(id)；历史引用保护';

COMMENT ON COLUMN operation.platform_message.title IS 'title';

COMMENT ON COLUMN operation.platform_message.content IS 'content';

COMMENT ON COLUMN operation.platform_message.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.sms_modem (
  id uuid NOT NULL,
  channel_id uuid NOT NULL,
  name varchar(200) NOT NULL,
  serial_number varchar(200) NOT NULL,
  carrier_code varchar(200),
  enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (serial_number)
);

COMMENT ON TABLE operation.sms_modem IS 'M10 短信猫设备目录';

COMMENT ON COLUMN operation.sms_modem.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.sms_modem.channel_id IS 'channel_id；外键 operation.notification_channel(id)；历史引用保护';

COMMENT ON COLUMN operation.sms_modem.name IS 'name';

COMMENT ON COLUMN operation.sms_modem.serial_number IS 'serial_number';

COMMENT ON COLUMN operation.sms_modem.carrier_code IS 'carrier_code';

COMMENT ON COLUMN operation.sms_modem.enabled IS 'enabled';

COMMENT ON COLUMN operation.sms_modem.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.sms_modem_status (
  modem_id uuid NOT NULL,
  as_of timestamptz NOT NULL,
  online boolean,
  uptime_seconds bigint,
  signal_quality numeric,
  signal_strength numeric,
  network_registration_count bigint,
  sim_status varchar(200),
  sent_success_count bigint,
  sent_total_count bigint,
  sim_balance_display varchar(200),
  collection_status varchar(200) NOT NULL,
  error_code varchar(200),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (modem_id,as_of),
  CHECK (collection_status IN ('READY','STALE','UNAVAILABLE')),
  CHECK (sent_success_count IS NULL OR sent_total_count IS NULL OR sent_success_count<=sent_total_count),
  CHECK (network_registration_count >= 0),
  CHECK (sent_success_count >= 0),
  CHECK (sent_total_count >= 0),
  CHECK (uptime_seconds >= 0)
);

COMMENT ON TABLE operation.sms_modem_status IS 'M13 短信猫完整遥测快照';

COMMENT ON COLUMN operation.sms_modem_status.modem_id IS 'modem_id；外键 operation.sms_modem(id)；历史引用保护';

COMMENT ON COLUMN operation.sms_modem_status.as_of IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.sms_modem_status.online IS 'online';

COMMENT ON COLUMN operation.sms_modem_status.uptime_seconds IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.sms_modem_status.signal_quality IS 'signal_quality';

COMMENT ON COLUMN operation.sms_modem_status.signal_strength IS 'signal_strength';

COMMENT ON COLUMN operation.sms_modem_status.network_registration_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.sms_modem_status.sim_status IS 'sim_status';

COMMENT ON COLUMN operation.sms_modem_status.sent_success_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.sms_modem_status.sent_total_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.sms_modem_status.sim_balance_display IS 'sim_balance_display';

COMMENT ON COLUMN operation.sms_modem_status.collection_status IS 'collection_status';

COMMENT ON COLUMN operation.sms_modem_status.error_code IS 'error_code';

COMMENT ON COLUMN operation.sms_modem_status.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.metric_definition (
  metric_code varchar(200) NOT NULL,
  definition_version varchar(200) NOT NULL,
  name varchar(200) NOT NULL,
  calculation_code varchar(200) NOT NULL,
  unit varchar(200) NOT NULL,
  time_zone varchar(200) NOT NULL DEFAULT 'Asia/Shanghai',
  refresh_policy varchar(200) NOT NULL,
  source_kind varchar(200) NOT NULL,
  additive boolean NOT NULL DEFAULT false,
  enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (metric_code,definition_version),
  CHECK (refresh_policy IN ('POLL','ON_DEMAND','SNAPSHOT'))
);

COMMENT ON TABLE core.metric_definition IS 'M04 统一指标口径及刷新计划；PENDING: P-006：源采集计数、归属分类、去重窗口及在线率统计口径需签认';

COMMENT ON COLUMN core.metric_definition.metric_code IS 'metric_code';

COMMENT ON COLUMN core.metric_definition.definition_version IS 'definition_version';

COMMENT ON COLUMN core.metric_definition.name IS 'name';

COMMENT ON COLUMN core.metric_definition.calculation_code IS 'calculation_code';

COMMENT ON COLUMN core.metric_definition.unit IS 'unit';

COMMENT ON COLUMN core.metric_definition.time_zone IS 'time_zone';

COMMENT ON COLUMN core.metric_definition.refresh_policy IS 'refresh_policy';

COMMENT ON COLUMN core.metric_definition.source_kind IS 'source_kind';

COMMENT ON COLUMN core.metric_definition.additive IS 'additive';

COMMENT ON COLUMN core.metric_definition.enabled IS 'enabled';

COMMENT ON COLUMN core.metric_definition.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.metric_scope (
  id uuid NOT NULL,
  scope_kind varchar(200) NOT NULL,
  region_code varchar(200),
  site_id uuid,
  device_id uuid,
  data_type varchar(200),
  device_category_code varchar(200),
  power_class_code varchar(200),
  carrier_code varchar(200),
  radio_type varchar(200),
  origin_class varchar(200),
  attribution_code varchar(200),
  operation_category varchar(200),
  canonical_key varchar(1000) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (canonical_key),
  CHECK (data_type IS NULL OR data_type IN ('FENCE','MAC','PLATE','FACE')),
  CHECK (origin_class IS NULL OR origin_class IN ('ALL','OUT_OF_PROVINCE','OVERSEAS'))
);

COMMENT ON TABLE core.metric_scope IS 'M04 有限、规范化的指标维度';

COMMENT ON COLUMN core.metric_scope.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN core.metric_scope.scope_kind IS 'scope_kind';

COMMENT ON COLUMN core.metric_scope.region_code IS 'region_code；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN core.metric_scope.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN core.metric_scope.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN core.metric_scope.data_type IS 'data_type';

COMMENT ON COLUMN core.metric_scope.device_category_code IS 'device_category_code';

COMMENT ON COLUMN core.metric_scope.power_class_code IS 'power_class_code';

COMMENT ON COLUMN core.metric_scope.carrier_code IS 'carrier_code';

COMMENT ON COLUMN core.metric_scope.radio_type IS 'radio_type';

COMMENT ON COLUMN core.metric_scope.origin_class IS 'origin_class';

COMMENT ON COLUMN core.metric_scope.attribution_code IS 'attribution_code';

COMMENT ON COLUMN core.metric_scope.operation_category IS 'operation_category';

COMMENT ON COLUMN core.metric_scope.canonical_key IS 'canonical_key';

COMMENT ON COLUMN core.metric_scope.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.metric_snapshot (
  metric_code varchar(200) NOT NULL,
  definition_version varchar(200) NOT NULL,
  scope_id uuid NOT NULL,
  granularity varchar(200) NOT NULL,
  bucket_start timestamptz NOT NULL,
  bucket_end timestamptz NOT NULL,
  value numeric(30,7),
  numerator numeric(30,7),
  denominator numeric(30,7),
  as_of timestamptz NOT NULL,
  status varchar(200) NOT NULL,
  error_code varchar(200),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (metric_code,definition_version,scope_id,granularity,bucket_start),
  CHECK (granularity IN ('HOUR','DAY','MONTH','ALL')),
  CHECK (status IN ('READY','STALE','UNAVAILABLE')),
  CHECK (bucket_start<bucket_end),
  CHECK (status NOT IN ('UNAVAILABLE','FAILED') OR value IS NULL),
  CHECK (denominator IS NULL OR denominator>=0)
);

COMMENT ON TABLE core.metric_snapshot IS 'M04 共享首页/运维/报表指标快照';

COMMENT ON COLUMN core.metric_snapshot.metric_code IS 'metric_code；外键 core.metric_definition(metric_code,definition_version)；历史引用保护';

COMMENT ON COLUMN core.metric_snapshot.definition_version IS 'definition_version；外键 core.metric_definition(metric_code,definition_version)；历史引用保护';

COMMENT ON COLUMN core.metric_snapshot.scope_id IS 'scope_id；外键 core.metric_scope(id)；历史引用保护';

COMMENT ON COLUMN core.metric_snapshot.granularity IS 'granularity';

COMMENT ON COLUMN core.metric_snapshot.bucket_start IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.metric_snapshot.bucket_end IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.metric_snapshot.value IS 'value';

COMMENT ON COLUMN core.metric_snapshot.numerator IS 'numerator';

COMMENT ON COLUMN core.metric_snapshot.denominator IS 'denominator';

COMMENT ON COLUMN core.metric_snapshot.as_of IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.metric_snapshot.status IS 'status';

COMMENT ON COLUMN core.metric_snapshot.error_code IS 'error_code';

COMMENT ON COLUMN core.metric_snapshot.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.metric_refresh_checkpoint (
  metric_code varchar(200) NOT NULL,
  definition_version varchar(200) NOT NULL,
  source_key varchar(200) NOT NULL,
  checkpoint jsonb NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  lease_owner varchar(200),
  lease_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (metric_code,definition_version,source_key),
  CHECK (jsonb_typeof(checkpoint) IN ('object','array'))
);

COMMENT ON TABLE core.metric_refresh_checkpoint IS 'M04 增量汇总游标及失败隔离';

COMMENT ON COLUMN core.metric_refresh_checkpoint.metric_code IS 'metric_code；外键 core.metric_definition(metric_code,definition_version)；历史引用保护';

COMMENT ON COLUMN core.metric_refresh_checkpoint.definition_version IS 'definition_version；外键 core.metric_definition(metric_code,definition_version)；历史引用保护';

COMMENT ON COLUMN core.metric_refresh_checkpoint.source_key IS 'source_key';

COMMENT ON COLUMN core.metric_refresh_checkpoint.checkpoint IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN core.metric_refresh_checkpoint.updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.metric_refresh_checkpoint.lease_owner IS 'lease_owner';

COMMENT ON COLUMN core.metric_refresh_checkpoint.lease_until IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.metric_refresh_checkpoint.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.anomaly_event (
  id uuid NOT NULL,
  category_code varchar(200) NOT NULL,
  device_id uuid,
  source_device_key varchar(200),
  ingestion_item_id uuid,
  period_from timestamptz NOT NULL,
  period_to timestamptz NOT NULL,
  observed_value numeric,
  baseline_value numeric,
  rule_version varchar(200) NOT NULL,
  detected_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (category_code IN ('UNREGISTERED_DEVICE','DUPLICATE_DEVICE_DATA','DEVICE_TIME_ANOMALY','DEVICE_MODE_ANOMALY','UNREGISTERED_DATA','DUPLICATE_DATA','DATA_TIME_ANOMALY','DATA_MODE_ANOMALY','DATA_FORMAT_ANOMALY','ONLINE_NO_DATA','DATA_SPIKE','DATA_DROP')),
  CHECK (period_from<period_to)
);

COMMENT ON TABLE core.anomaly_event IS 'M03 异常设备/数据/数据量事件；PENDING: P-006/P-020：异常识别/时间/模式/增减阈值未定，不虚构触发条件';

COMMENT ON COLUMN core.anomaly_event.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN core.anomaly_event.category_code IS 'category_code';

COMMENT ON COLUMN core.anomaly_event.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN core.anomaly_event.source_device_key IS 'source_device_key';

COMMENT ON COLUMN core.anomaly_event.ingestion_item_id IS 'ingestion_item_id；外键 capture.ingestion_item(id)；历史引用保护';

COMMENT ON COLUMN core.anomaly_event.period_from IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.anomaly_event.period_to IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.anomaly_event.observed_value IS 'observed_value';

COMMENT ON COLUMN core.anomaly_event.baseline_value IS 'baseline_value';

COMMENT ON COLUMN core.anomaly_event.rule_version IS 'rule_version';

COMMENT ON COLUMN core.anomaly_event.detected_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.anomaly_event.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE core.component_status_snapshot (
  component_code varchar(200) NOT NULL,
  instance_code varchar(200) NOT NULL,
  label varchar(200) NOT NULL,
  status varchar(200) NOT NULL,
  checked_at timestamptz NOT NULL,
  version_label varchar(200),
  failure_summary varchar(1000),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (component_code,instance_code,checked_at),
  CHECK (status IN ('UP','DOWN','DEGRADED','UNKNOWN'))
);

COMMENT ON TABLE core.component_status_snapshot IS 'M15 受控系统组件状态';

COMMENT ON COLUMN core.component_status_snapshot.component_code IS 'component_code';

COMMENT ON COLUMN core.component_status_snapshot.instance_code IS 'instance_code';

COMMENT ON COLUMN core.component_status_snapshot.label IS 'label';

COMMENT ON COLUMN core.component_status_snapshot.status IS 'status';

COMMENT ON COLUMN core.component_status_snapshot.checked_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN core.component_status_snapshot.version_label IS 'version_label';

COMMENT ON COLUMN core.component_status_snapshot.failure_summary IS 'failure_summary';

COMMENT ON COLUMN core.component_status_snapshot.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.device_command_job (
  task_id uuid NOT NULL,
  name varchar(200) NOT NULL,
  command_type varchar(200) NOT NULL,
  group_id uuid,
  group_name_snapshot varchar(200),
  region_code varchar(200),
  selection_schema_version varchar(200) NOT NULL DEFAULT '1',
  selection_snapshot jsonb NOT NULL,
  reason varchar(1000) NOT NULL,
  revised_from_task_id uuid,
  confirmation_record_id uuid,
  version bigint NOT NULL DEFAULT 0,
  deleted boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (task_id),
  CHECK (command_type IN ('RESET','APPLY_PARAMETERS')),
  CHECK (jsonb_typeof(selection_snapshot) IN ('object','array')),
  CHECK (version >= 0)
);

COMMENT ON TABLE operation.device_command_job IS 'M12 批量命令任务及选择快照；PENDING: P-018/P-019：设备参数/协议、复位语义、回执与高风险审批/确认待签认';

COMMENT ON COLUMN operation.device_command_job.task_id IS 'task_id；外键 analysis.async_task(id)；历史引用保护';

COMMENT ON COLUMN operation.device_command_job.name IS 'name';

COMMENT ON COLUMN operation.device_command_job.command_type IS 'command_type';

COMMENT ON COLUMN operation.device_command_job.group_id IS 'group_id；外键 core.device_group(id)；历史引用保护';

COMMENT ON COLUMN operation.device_command_job.group_name_snapshot IS 'group_name_snapshot';

COMMENT ON COLUMN operation.device_command_job.region_code IS 'region_code；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN operation.device_command_job.selection_schema_version IS 'selection_schema_version';

COMMENT ON COLUMN operation.device_command_job.selection_snapshot IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN operation.device_command_job.reason IS 'reason';

COMMENT ON COLUMN operation.device_command_job.revised_from_task_id IS 'revised_from_task_id；外键 operation.device_command_job(task_id)；历史引用保护';

COMMENT ON COLUMN operation.device_command_job.confirmation_record_id IS 'confirmation_record_id；外键 audit.sensitive_action_review(id)；历史引用保护';

COMMENT ON COLUMN operation.device_command_job.version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.device_command_job.deleted IS 'deleted';

COMMENT ON COLUMN operation.device_command_job.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.device_command (
  id uuid NOT NULL,
  job_task_id uuid NOT NULL,
  device_id uuid NOT NULL,
  device_name_snapshot varchar(200) NOT NULL,
  command_type varchar(200) NOT NULL,
  parameter_schema_version varchar(200) NOT NULL DEFAULT '1',
  parameter_snapshot jsonb NOT NULL,
  status varchar(200) NOT NULL DEFAULT 'PENDING',
  delivery_state varchar(200) NOT NULL DEFAULT 'NOT_SENT',
  idempotency_key varchar(200) NOT NULL,
  expires_at timestamptz NOT NULL,
  current_attempt_no bigint NOT NULL DEFAULT 0,
  started_at timestamptz,
  finished_at timestamptz,
  acknowledgement_id varchar(200),
  error_code varchar(200),
  error_message varchar(2000),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (job_task_id,device_id),
  UNIQUE (idempotency_key),
  CHECK (status IN ('PENDING','QUEUED','RUNNING','SUCCESS','FAILED','CANCELLED')),
  CHECK (command_type IN ('RESET','APPLY_PARAMETERS')),
  CHECK (delivery_state IN ('NOT_SENT','SENT','ACKNOWLEDGED','UNKNOWN')),
  CHECK (expires_at>created_at),
  CHECK (jsonb_typeof(parameter_snapshot) IN ('object','array')),
  CHECK (current_attempt_no >= 0)
);

COMMENT ON TABLE operation.device_command IS 'M12 每台设备执行与回执';

COMMENT ON COLUMN operation.device_command.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.device_command.job_task_id IS 'job_task_id；外键 operation.device_command_job(task_id)；历史引用保护';

COMMENT ON COLUMN operation.device_command.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN operation.device_command.device_name_snapshot IS 'device_name_snapshot';

COMMENT ON COLUMN operation.device_command.command_type IS 'command_type';

COMMENT ON COLUMN operation.device_command.parameter_schema_version IS 'parameter_schema_version';

COMMENT ON COLUMN operation.device_command.parameter_snapshot IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN operation.device_command.status IS 'status';

COMMENT ON COLUMN operation.device_command.delivery_state IS 'delivery_state';

COMMENT ON COLUMN operation.device_command.idempotency_key IS 'idempotency_key';

COMMENT ON COLUMN operation.device_command.expires_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.device_command.current_attempt_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.device_command.started_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.device_command.finished_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.device_command.acknowledgement_id IS 'acknowledgement_id';

COMMENT ON COLUMN operation.device_command.error_code IS 'error_code';

COMMENT ON COLUMN operation.device_command.error_message IS 'error_message';

COMMENT ON COLUMN operation.device_command.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.device_command_attempt (
  command_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  requested_at timestamptz NOT NULL,
  responded_at timestamptz,
  status varchar(200) NOT NULL,
  acknowledgement_id varchar(200),
  error_code varchar(200),
  error_message varchar(2000),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (command_id,attempt_no),
  CHECK (status IN ('PENDING','QUEUED','RUNNING','SUCCESS','FAILED','CANCELLED')),
  CHECK (attempt_no>=1),
  CHECK (attempt_no >= 0)
);

COMMENT ON TABLE operation.device_command_attempt IS 'M12 逐台命令尝试，成功项不重发';

COMMENT ON COLUMN operation.device_command_attempt.command_id IS 'command_id；外键 operation.device_command(id)；历史引用保护';

COMMENT ON COLUMN operation.device_command_attempt.attempt_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.device_command_attempt.requested_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.device_command_attempt.responded_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.device_command_attempt.status IS 'status';

COMMENT ON COLUMN operation.device_command_attempt.acknowledgement_id IS 'acknowledgement_id';

COMMENT ON COLUMN operation.device_command_attempt.error_code IS 'error_code';

COMMENT ON COLUMN operation.device_command_attempt.error_message IS 'error_message';

COMMENT ON COLUMN operation.device_command_attempt.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.report_job (
  id uuid NOT NULL,
  task_id uuid NOT NULL,
  name varchar(200) NOT NULL,
  mode varchar(200) NOT NULL,
  input_snapshot_id uuid NOT NULL,
  output_format varchar(200),
  output_file_id uuid,
  completed_at timestamptz,
  published_attempt_no bigint,
  version bigint NOT NULL DEFAULT 0,
  deleted boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (task_id),
  CHECK (mode IN ('SINGLE','BUNDLE')),
  CHECK (output_format IS NULL OR (mode='SINGLE' AND output_format='XLSX') OR (mode='BUNDLE' AND output_format='ZIP')),
  CHECK (version >= 0),
  CHECK (published_attempt_no >= 0)
);

COMMENT ON TABLE operation.report_job IS 'M12 单报表/多报表ZIP任务';

COMMENT ON COLUMN operation.report_job.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.report_job.task_id IS 'task_id；外键 analysis.async_task(id),analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN operation.report_job.name IS 'name';

COMMENT ON COLUMN operation.report_job.mode IS 'mode';

COMMENT ON COLUMN operation.report_job.input_snapshot_id IS 'input_snapshot_id；外键 analysis.input_snapshot(id)；历史引用保护';

COMMENT ON COLUMN operation.report_job.output_format IS 'output_format';

COMMENT ON COLUMN operation.report_job.output_file_id IS 'output_file_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN operation.report_job.completed_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.report_job.published_attempt_no IS '非负计数或版本；不使用32位整型计数；外键 analysis.task_attempt(task_id,attempt_no)；历史引用保护';

COMMENT ON COLUMN operation.report_job.version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_job.deleted IS 'deleted';

COMMENT ON COLUMN operation.report_job.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.report_definition (
  id uuid NOT NULL,
  report_id uuid NOT NULL,
  definition_no bigint NOT NULL,
  report_type varchar(200) NOT NULL,
  granularity varchar(200) NOT NULL,
  period_start date NOT NULL,
  period_end date,
  region_code varchar(200) NOT NULL,
  scope_schema_version varchar(200) NOT NULL DEFAULT '1',
  scope_snapshot jsonb NOT NULL,
  metric_definition_versions jsonb NOT NULL,
  as_of timestamptz,
  device_count bigint,
  capture_count bigint,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (report_id,definition_no),
  CHECK (report_type IN ('DEVICE_REALTIME_ONLINE_RATE','DEVICE_REALTIME_STATUS','FENCE_OPERATOR_STATISTICS','FENCE_AREA_CAPTURE','FENCE_DEVICE_CAPTURE','MAC_CAPTURE','PLATE_CAPTURE','SYSTEM_COMPONENT_STATUS')),
  CHECK (granularity IN ('DAY','MONTH','YEAR')),
  CHECK (period_end IS NULL OR period_start<period_end),
  CHECK (jsonb_typeof(scope_snapshot) IN ('object','array')),
  CHECK (jsonb_typeof(metric_definition_versions) IN ('object','array')),
  CHECK (device_count >= 0),
  CHECK (capture_count >= 0),
  CHECK (definition_no >= 0)
);

COMMENT ON TABLE operation.report_definition IS 'M12 报表定义/统计范围/口径版本';

COMMENT ON COLUMN operation.report_definition.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.report_definition.report_id IS 'report_id；外键 operation.report_job(id)；历史引用保护';

COMMENT ON COLUMN operation.report_definition.definition_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_definition.report_type IS 'report_type';

COMMENT ON COLUMN operation.report_definition.granularity IS 'granularity';

COMMENT ON COLUMN operation.report_definition.period_start IS 'period_start';

COMMENT ON COLUMN operation.report_definition.period_end IS 'period_end';

COMMENT ON COLUMN operation.report_definition.region_code IS 'region_code；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN operation.report_definition.scope_schema_version IS 'scope_schema_version';

COMMENT ON COLUMN operation.report_definition.scope_snapshot IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN operation.report_definition.metric_definition_versions IS '版本化契约快照；按指定 Schema 校验，不存自由业务实体';

COMMENT ON COLUMN operation.report_definition.as_of IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.report_definition.device_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_definition.capture_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_definition.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.report_definition_site (
  definition_id uuid NOT NULL,
  site_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (definition_id,site_id)
);

COMMENT ON TABLE operation.report_definition_site IS 'M12 定义中的固定场所范围';

COMMENT ON COLUMN operation.report_definition_site.definition_id IS 'definition_id；外键 operation.report_definition(id)；历史引用保护';

COMMENT ON COLUMN operation.report_definition_site.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN operation.report_definition_site.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.report_device_row (
  definition_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  row_no bigint NOT NULL,
  device_id uuid NOT NULL,
  serial_number varchar(200) NOT NULL,
  name varchar(200) NOT NULL,
  site_id uuid NOT NULL,
  site_name varchar(200) NOT NULL,
  region_code varchar(200) NOT NULL,
  category_code varchar(200) NOT NULL,
  status varchar(200) NOT NULL,
  last_seen_at timestamptz,
  location geometry(Point,4326),
  device_updated_at timestamptz NOT NULL,
  device_version bigint NOT NULL,
  group_ids_snapshot uuid[] NOT NULL DEFAULT '{}'::uuid[],
  reset_count bigint NOT NULL,
  capture_count bigint NOT NULL,
  as_of timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (definition_id,attempt_no,row_no),
  CHECK (status IN ('ONLINE','OFFLINE','UNKNOWN','FAULT')),
  CHECK (location IS NULL OR (NOT ST_IsEmpty(location) AND ST_X(location) BETWEEN -180 AND 180 AND ST_Y(location) BETWEEN -90 AND 90)),
  CHECK (attempt_no >= 0),
  CHECK (reset_count >= 0),
  CHECK (capture_count >= 0),
  CHECK (row_no >= 0),
  CHECK (device_version >= 0)
);

COMMENT ON TABLE operation.report_device_row IS 'M12 历史设备预览字段快照';

COMMENT ON COLUMN operation.report_device_row.definition_id IS 'definition_id；外键 operation.report_definition(id)；历史引用保护';

COMMENT ON COLUMN operation.report_device_row.attempt_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_device_row.row_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_device_row.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN operation.report_device_row.serial_number IS 'serial_number';

COMMENT ON COLUMN operation.report_device_row.name IS 'name';

COMMENT ON COLUMN operation.report_device_row.site_id IS 'site_id；外键 core.site(id)；历史引用保护';

COMMENT ON COLUMN operation.report_device_row.site_name IS 'site_name';

COMMENT ON COLUMN operation.report_device_row.region_code IS 'region_code；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN operation.report_device_row.category_code IS 'category_code';

COMMENT ON COLUMN operation.report_device_row.status IS 'status';

COMMENT ON COLUMN operation.report_device_row.last_seen_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.report_device_row.location IS 'WGS84空间字段；API经纬度由ST_X/ST_Y生成；不重复存坐标';

COMMENT ON COLUMN operation.report_device_row.device_updated_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.report_device_row.device_version IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_device_row.group_ids_snapshot IS 'group_ids_snapshot';

COMMENT ON COLUMN operation.report_device_row.reset_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_device_row.capture_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_device_row.as_of IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.report_device_row.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.report_region_row (
  definition_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  region_code varchar(200) NOT NULL,
  region_name varchar(200) NOT NULL,
  device_count bigint NOT NULL,
  online_count bigint NOT NULL,
  online_rate numeric(8,7) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (definition_id,attempt_no,region_code),
  CHECK (online_count<=device_count),
  CHECK (online_rate BETWEEN 0 AND 1),
  CHECK (attempt_no >= 0),
  CHECK (device_count >= 0),
  CHECK (online_count >= 0)
);

COMMENT ON TABLE operation.report_region_row IS 'M12 区域在线率预览快照';

COMMENT ON COLUMN operation.report_region_row.definition_id IS 'definition_id；外键 operation.report_definition(id)；历史引用保护';

COMMENT ON COLUMN operation.report_region_row.attempt_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_region_row.region_code IS 'region_code；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN operation.report_region_row.region_name IS 'region_name';

COMMENT ON COLUMN operation.report_region_row.device_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_region_row.online_count IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_region_row.online_rate IS 'online_rate';

COMMENT ON COLUMN operation.report_region_row.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.report_metric_row (
  definition_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  row_no bigint NOT NULL,
  metric_code varchar(200) NOT NULL,
  definition_version varchar(200) NOT NULL,
  label varchar(200) NOT NULL,
  value numeric(30,7),
  unit varchar(200) NOT NULL,
  status varchar(200) NOT NULL,
  as_of timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (definition_id,attempt_no,row_no),
  CHECK (status IN ('READY','STALE','UNAVAILABLE')),
  CHECK (attempt_no >= 0),
  CHECK (row_no >= 0)
);

COMMENT ON TABLE operation.report_metric_row IS 'M12 报表摘要指标快照';

COMMENT ON COLUMN operation.report_metric_row.definition_id IS 'definition_id；外键 operation.report_definition(id)；历史引用保护';

COMMENT ON COLUMN operation.report_metric_row.attempt_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_metric_row.row_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_metric_row.metric_code IS 'metric_code；外键 core.metric_definition(metric_code,definition_version)；历史引用保护';

COMMENT ON COLUMN operation.report_metric_row.definition_version IS 'definition_version；外键 core.metric_definition(metric_code,definition_version)；历史引用保护';

COMMENT ON COLUMN operation.report_metric_row.label IS 'label';

COMMENT ON COLUMN operation.report_metric_row.value IS 'value';

COMMENT ON COLUMN operation.report_metric_row.unit IS 'unit';

COMMENT ON COLUMN operation.report_metric_row.status IS 'status';

COMMENT ON COLUMN operation.report_metric_row.as_of IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.report_metric_row.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.report_series (
  id uuid NOT NULL,
  definition_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  metric_code varchar(200) NOT NULL,
  definition_version varchar(200) NOT NULL,
  carrier_code varchar(200),
  carrier_label varchar(200),
  device_id uuid,
  device_name_snapshot varchar(200),
  region_code varchar(200),
  series_key varchar(1000) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (definition_id,attempt_no,series_key),
  CHECK (attempt_no >= 0)
);

COMMENT ON TABLE operation.report_series IS 'M12 报表序列维度，避免NULL复合主键';

COMMENT ON COLUMN operation.report_series.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.report_series.definition_id IS 'definition_id；外键 operation.report_definition(id)；历史引用保护';

COMMENT ON COLUMN operation.report_series.attempt_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_series.metric_code IS 'metric_code；外键 core.metric_definition(metric_code,definition_version)；历史引用保护';

COMMENT ON COLUMN operation.report_series.definition_version IS 'definition_version；外键 core.metric_definition(metric_code,definition_version)；历史引用保护';

COMMENT ON COLUMN operation.report_series.carrier_code IS 'carrier_code';

COMMENT ON COLUMN operation.report_series.carrier_label IS 'carrier_label';

COMMENT ON COLUMN operation.report_series.device_id IS 'device_id；外键 core.device(id)；历史引用保护';

COMMENT ON COLUMN operation.report_series.device_name_snapshot IS 'device_name_snapshot';

COMMENT ON COLUMN operation.report_series.region_code IS 'region_code；外键 core.region(region_code)；历史引用保护';

COMMENT ON COLUMN operation.report_series.series_key IS 'series_key';

COMMENT ON COLUMN operation.report_series.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.report_series_point (
  series_id uuid NOT NULL,
  bucket_start timestamptz NOT NULL,
  bucket_end timestamptz NOT NULL,
  value numeric(30,7),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (series_id,bucket_start),
  CHECK (bucket_start<bucket_end)
);

COMMENT ON TABLE operation.report_series_point IS 'M12 按时/日/月展开的报表数据点';

COMMENT ON COLUMN operation.report_series_point.series_id IS 'series_id；外键 operation.report_series(id)；历史引用保护';

COMMENT ON COLUMN operation.report_series_point.bucket_start IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.report_series_point.bucket_end IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.report_series_point.value IS 'value';

COMMENT ON COLUMN operation.report_series_point.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.report_component_row (
  definition_id uuid NOT NULL,
  attempt_no bigint NOT NULL,
  component_code varchar(200) NOT NULL,
  instance_code varchar(200) NOT NULL,
  label varchar(200) NOT NULL,
  status varchar(200) NOT NULL,
  checked_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (definition_id,attempt_no,component_code,instance_code),
  CHECK (status IN ('UP','DOWN','DEGRADED','UNKNOWN')),
  CHECK (attempt_no >= 0)
);

COMMENT ON TABLE operation.report_component_row IS 'M12 报表组件状态快照';

COMMENT ON COLUMN operation.report_component_row.definition_id IS 'definition_id；外键 operation.report_definition(id)；历史引用保护';

COMMENT ON COLUMN operation.report_component_row.attempt_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_component_row.component_code IS 'component_code';

COMMENT ON COLUMN operation.report_component_row.instance_code IS 'instance_code';

COMMENT ON COLUMN operation.report_component_row.label IS 'label';

COMMENT ON COLUMN operation.report_component_row.status IS 'status';

COMMENT ON COLUMN operation.report_component_row.checked_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.report_component_row.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.report_output_file (
  id uuid NOT NULL,
  report_id uuid NOT NULL,
  definition_id uuid,
  attempt_no bigint NOT NULL,
  file_asset_id uuid NOT NULL,
  output_role varchar(200) NOT NULL,
  file_name varchar(255) NOT NULL,
  format varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (report_id,attempt_no,file_asset_id),
  CHECK (output_role IN ('SINGLE','BUNDLE','MEMBER')),
  CHECK (format IN ('XLSX','ZIP')),
  CHECK (attempt_no >= 0)
);

COMMENT ON TABLE operation.report_output_file IS 'M12 单文件/ZIP及成员文件引用';

COMMENT ON COLUMN operation.report_output_file.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.report_output_file.report_id IS 'report_id；外键 operation.report_job(id)；历史引用保护';

COMMENT ON COLUMN operation.report_output_file.definition_id IS 'definition_id；外键 operation.report_definition(id)；历史引用保护';

COMMENT ON COLUMN operation.report_output_file.attempt_no IS '非负计数或版本；不使用32位整型计数';

COMMENT ON COLUMN operation.report_output_file.file_asset_id IS 'file_asset_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN operation.report_output_file.output_role IS 'output_role';

COMMENT ON COLUMN operation.report_output_file.file_name IS 'file_name';

COMMENT ON COLUMN operation.report_output_file.format IS 'format';

COMMENT ON COLUMN operation.report_output_file.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE audit.audit_event (
  id uuid NOT NULL,
  actor_user_id uuid,
  actor_login_name_snapshot varchar(200) NOT NULL,
  actor_display_name_snapshot varchar(200) NOT NULL,
  action varchar(200) NOT NULL,
  action_category varchar(200) NOT NULL,
  resource_type varchar(200) NOT NULL,
  resource_id uuid,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  result varchar(200) NOT NULL,
  failure_code varchar(200),
  before_summary varchar(2000),
  after_summary varchar(2000),
  source_ip inet,
  business_task_id uuid,
  request_id varchar(200),
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (result IN ('SUCCESS','FAILURE'))
);

COMMENT ON TABLE audit.audit_event IS 'M14 操作/安全审计，主体快照保留';

COMMENT ON COLUMN audit.audit_event.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN audit.audit_event.actor_user_id IS 'actor_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN audit.audit_event.actor_login_name_snapshot IS 'actor_login_name_snapshot';

COMMENT ON COLUMN audit.audit_event.actor_display_name_snapshot IS 'actor_display_name_snapshot';

COMMENT ON COLUMN audit.audit_event.action IS 'action';

COMMENT ON COLUMN audit.audit_event.action_category IS 'action_category';

COMMENT ON COLUMN audit.audit_event.resource_type IS 'resource_type';

COMMENT ON COLUMN audit.audit_event.resource_id IS 'resource_id';

COMMENT ON COLUMN audit.audit_event.occurred_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN audit.audit_event.result IS 'result';

COMMENT ON COLUMN audit.audit_event.failure_code IS 'failure_code';

COMMENT ON COLUMN audit.audit_event.before_summary IS 'before_summary';

COMMENT ON COLUMN audit.audit_event.after_summary IS 'after_summary';

COMMENT ON COLUMN audit.audit_event.source_ip IS 'source_ip';

COMMENT ON COLUMN audit.audit_event.business_task_id IS 'business_task_id；外键 analysis.async_task(id)；历史引用保护';

COMMENT ON COLUMN audit.audit_event.request_id IS 'request_id';

COMMENT ON COLUMN audit.audit_event.trace_id IS 'trace_id';

COMMENT ON COLUMN audit.audit_event.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.decryption_request (
  id uuid NOT NULL,
  requester_user_id uuid NOT NULL,
  recipient_user_id uuid,
  context_id uuid NOT NULL,
  input_snapshot_id uuid NOT NULL,
  input_file_id uuid,
  purpose varchar(1000) NOT NULL,
  status varchar(200) NOT NULL DEFAULT 'PENDING',
  policy_version varchar(200) NOT NULL,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
  CHECK (expires_at>created_at)
);

COMMENT ON TABLE operation.decryption_request IS 'M06 解密申请与安全策略版本；PENDING: P-008：涉密移交、审批、密钥、用途与明文生命周期未签认，能力默认关闭';

COMMENT ON COLUMN operation.decryption_request.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.decryption_request.requester_user_id IS 'requester_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.decryption_request.recipient_user_id IS 'recipient_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.decryption_request.context_id IS 'context_id；外键 analysis.analysis_context(id)；历史引用保护';

COMMENT ON COLUMN operation.decryption_request.input_snapshot_id IS 'input_snapshot_id；外键 analysis.input_snapshot(id)；历史引用保护';

COMMENT ON COLUMN operation.decryption_request.input_file_id IS 'input_file_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN operation.decryption_request.purpose IS 'purpose';

COMMENT ON COLUMN operation.decryption_request.status IS 'status';

COMMENT ON COLUMN operation.decryption_request.policy_version IS 'policy_version';

COMMENT ON COLUMN operation.decryption_request.expires_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.decryption_request.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.decryption_capture (
  request_id uuid NOT NULL,
  capture_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (request_id,capture_id)
);

COMMENT ON TABLE operation.decryption_capture IS 'M06 申请的原始采集范围';

COMMENT ON COLUMN operation.decryption_capture.request_id IS 'request_id；外键 operation.decryption_request(id)；历史引用保护';

COMMENT ON COLUMN operation.decryption_capture.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN operation.decryption_capture.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE audit.sensitive_action_review (
  id uuid NOT NULL,
  action_type varchar(200) NOT NULL,
  decryption_request_id uuid,
  device_command_task_id uuid,
  export_task_id uuid,
  actor_user_id uuid NOT NULL,
  decision varchar(200) NOT NULL,
  reason varchar(1000) NOT NULL,
  policy_version varchar(200) NOT NULL,
  occurred_at timestamptz NOT NULL,
  trace_id varchar(200) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  CHECK (num_nonnulls(decryption_request_id,device_command_task_id,export_task_id)=1),
  CHECK (decision IN ('CONFIRMED','APPROVED','REJECTED'))
);

COMMENT ON TABLE audit.sensitive_action_review IS 'M01 配置策略产生的确认/审批事实；PENDING: P-008/P-019：只记录已签认策略产生的实际确认/审批，不预设审批层级';

COMMENT ON COLUMN audit.sensitive_action_review.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN audit.sensitive_action_review.action_type IS 'action_type';

COMMENT ON COLUMN audit.sensitive_action_review.decryption_request_id IS 'decryption_request_id；外键 operation.decryption_request(id)；历史引用保护';

COMMENT ON COLUMN audit.sensitive_action_review.device_command_task_id IS 'device_command_task_id；外键 operation.device_command_job(task_id)；历史引用保护';

COMMENT ON COLUMN audit.sensitive_action_review.export_task_id IS 'export_task_id；外键 analysis.export_job(task_id)；历史引用保护';

COMMENT ON COLUMN audit.sensitive_action_review.actor_user_id IS 'actor_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN audit.sensitive_action_review.decision IS 'decision';

COMMENT ON COLUMN audit.sensitive_action_review.reason IS 'reason';

COMMENT ON COLUMN audit.sensitive_action_review.policy_version IS 'policy_version';

COMMENT ON COLUMN audit.sensitive_action_review.occurred_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN audit.sensitive_action_review.trace_id IS 'trace_id';

COMMENT ON COLUMN audit.sensitive_action_review.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE operation.decryption_result_grant (
  id uuid NOT NULL,
  request_id uuid NOT NULL,
  authorized_user_id uuid NOT NULL,
  result_file_id uuid NOT NULL,
  valid_from timestamptz NOT NULL,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id),
  UNIQUE (request_id,authorized_user_id,result_file_id),
  CHECK (valid_from<expires_at)
);

COMMENT ON TABLE operation.decryption_result_grant IS 'M06 仅指定涉密用户可短时取结果';

COMMENT ON COLUMN operation.decryption_result_grant.id IS '应用生成的UUID业务主键';

COMMENT ON COLUMN operation.decryption_result_grant.request_id IS 'request_id；外键 operation.decryption_request(id)；历史引用保护';

COMMENT ON COLUMN operation.decryption_result_grant.authorized_user_id IS 'authorized_user_id；外键 auth.app_user(id)；历史引用保护';

COMMENT ON COLUMN operation.decryption_result_grant.result_file_id IS 'result_file_id；外键 core.file_asset(id)；历史引用保护';

COMMENT ON COLUMN operation.decryption_result_grant.valid_from IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.decryption_result_grant.expires_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.decryption_result_grant.revoked_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

COMMENT ON COLUMN operation.decryption_result_grant.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

CREATE TABLE analysis.collision_event_evidence (
  collision_event_id uuid NOT NULL,
  capture_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (collision_event_id,capture_id)
);

COMMENT ON TABLE analysis.collision_event_evidence IS 'M08 每次碰撞自己的证据列表';

COMMENT ON COLUMN analysis.collision_event_evidence.collision_event_id IS 'collision_event_id；外键 analysis.collision_event(id)；历史引用保护';

COMMENT ON COLUMN analysis.collision_event_evidence.capture_id IS 'capture_id；外键 capture.capture_event(id)；历史引用保护';

COMMENT ON COLUMN analysis.collision_event_evidence.created_at IS 'UTC 时刻；API 输出 RFC3339 带偏移';

ALTER TABLE auth.app_user ADD CONSTRAINT fk_auth_app_user_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.app_user ADD CONSTRAINT fk_auth_app_user_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.role ADD CONSTRAINT fk_auth_role_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.role ADD CONSTRAINT fk_auth_role_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.menu ADD CONSTRAINT fk_auth_menu_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.menu ADD CONSTRAINT fk_auth_menu_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.menu ADD CONSTRAINT fk_auth_menu_parent_id FOREIGN KEY (parent_id) REFERENCES auth.menu (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.menu ADD CONSTRAINT fk_auth_menu_required_permission_code FOREIGN KEY (required_permission_code) REFERENCES auth.permission (code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.user_role ADD CONSTRAINT fk_auth_user_role_user_id FOREIGN KEY (user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.user_role ADD CONSTRAINT fk_auth_user_role_role_id FOREIGN KEY (role_id) REFERENCES auth.role (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.role_permission ADD CONSTRAINT fk_auth_role_permission_role_id FOREIGN KEY (role_id) REFERENCES auth.role (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.role_permission ADD CONSTRAINT fk_auth_role_permission_permission_code FOREIGN KEY (permission_code) REFERENCES auth.permission (code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.role_menu ADD CONSTRAINT fk_auth_role_menu_role_id FOREIGN KEY (role_id) REFERENCES auth.role (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.role_menu ADD CONSTRAINT fk_auth_role_menu_menu_id FOREIGN KEY (menu_id) REFERENCES auth.menu (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.role_site ADD CONSTRAINT fk_auth_role_site_role_id FOREIGN KEY (role_id) REFERENCES auth.role (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.role_site ADD CONSTRAINT fk_auth_role_site_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.role_data_type ADD CONSTRAINT fk_auth_role_data_type_role_id FOREIGN KEY (role_id) REFERENCES auth.role (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.user_scope ADD CONSTRAINT fk_auth_user_scope_user_id FOREIGN KEY (user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.user_scope_site ADD CONSTRAINT fk_auth_user_scope_site_user_id FOREIGN KEY (user_id) REFERENCES auth.user_scope (user_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.user_scope_site ADD CONSTRAINT fk_auth_user_scope_site_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.user_scope_data_type ADD CONSTRAINT fk_auth_user_scope_data_type_user_id FOREIGN KEY (user_id) REFERENCES auth.user_scope (user_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.user_session ADD CONSTRAINT fk_auth_user_session_user_id FOREIGN KEY (user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE auth.csrf_session ADD CONSTRAINT fk_auth_csrf_session_session_id FOREIGN KEY (session_id) REFERENCES auth.user_session (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.dictionary_item ADD CONSTRAINT fk_core_dictionary_item_dictionary_code FOREIGN KEY (dictionary_code) REFERENCES core.dictionary (code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.dictionary_binding ADD CONSTRAINT fk_core_dictionary_binding_dictionary_code FOREIGN KEY (dictionary_code) REFERENCES core.dictionary (code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.import_column ADD CONSTRAINT fk_core_import_column_template_code_template_version FOREIGN KEY (template_code,template_version) REFERENCES core.import_template (template_code,template_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.region ADD CONSTRAINT fk_core_region_parent_region_code FOREIGN KEY (parent_region_code) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.site ADD CONSTRAINT fk_core_site_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.site ADD CONSTRAINT fk_core_site_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.site ADD CONSTRAINT fk_core_site_region_code FOREIGN KEY (region_code) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device ADD CONSTRAINT fk_core_device_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device ADD CONSTRAINT fk_core_device_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device ADD CONSTRAINT fk_core_device_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device_group ADD CONSTRAINT fk_core_device_group_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device_group ADD CONSTRAINT fk_core_device_group_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device_group ADD CONSTRAINT fk_core_device_group_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device_group_member ADD CONSTRAINT fk_core_device_group_member_group_id FOREIGN KEY (group_id) REFERENCES core.device_group (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device_group_member ADD CONSTRAINT fk_core_device_group_member_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device_revision ADD CONSTRAINT fk_core_device_revision_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device_revision ADD CONSTRAINT fk_core_device_revision_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device_revision ADD CONSTRAINT fk_core_device_revision_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device_status_event ADD CONSTRAINT fk_core_device_status_event_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device_parameter_value ADD CONSTRAINT fk_core_device_parameter_value_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device_parameter_value ADD CONSTRAINT fk_core_device_parameter_value_definition_id_value_type FOREIGN KEY (definition_id,value_type) REFERENCES core.device_parameter_definition (id,value_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.device_parameter_value ADD CONSTRAINT fk_core_device_parameter_value_command_id FOREIGN KEY (command_id) REFERENCES operation.device_command (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.file_asset ADD CONSTRAINT fk_core_file_asset_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.idempotency_request ADD CONSTRAINT fk_core_idempotency_request_user_id FOREIGN KEY (user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.outbox_event ADD CONSTRAINT fk_core_outbox_event_manifest_file_id FOREIGN KEY (manifest_file_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.consumer_inbox ADD CONSTRAINT fk_core_consumer_inbox_event_id FOREIGN KEY (event_id) REFERENCES core.outbox_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_context ADD CONSTRAINT fk_analysis_analysis_context_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.context_target ADD CONSTRAINT fk_analysis_context_target_context_id FOREIGN KEY (context_id) REFERENCES analysis.analysis_context (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.context_target ADD CONSTRAINT fk_analysis_context_target_target_id_target_type FOREIGN KEY (target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.input_snapshot ADD CONSTRAINT fk_analysis_input_snapshot_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.input_snapshot ADD CONSTRAINT fk_analysis_input_snapshot_manifest_file_id FOREIGN KEY (manifest_file_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.input_snapshot_reference ADD CONSTRAINT fk_analysis_input_snapshot_reference_snapshot_id FOREIGN KEY (snapshot_id) REFERENCES analysis.input_snapshot (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.input_snapshot_reference ADD CONSTRAINT fk_analysis_input_snapshot_reference_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.input_snapshot_reference ADD CONSTRAINT fk_analysis_input_snapshot_reference_target_id FOREIGN KEY (target_id) REFERENCES capture.target (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.input_snapshot_reference ADD CONSTRAINT fk_analysis_input_snapshot_reference_analysis_result_id FOREIGN KEY (analysis_result_id) REFERENCES analysis.analysis_result (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.input_snapshot_reference ADD CONSTRAINT fk_analysis_input_snapshot_reference_profile_id FOREIGN KEY (profile_id) REFERENCES analysis.profile (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.input_snapshot_reference ADD CONSTRAINT fk_analysis_input_snapshot_reference_dataset__ebed4d5927f4 FOREIGN KEY (dataset_id,dataset_version) REFERENCES analysis.dataset_version (dataset_id,version_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.async_task ADD CONSTRAINT fk_analysis_async_task_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.async_task ADD CONSTRAINT fk_analysis_async_task_context_id FOREIGN KEY (context_id) REFERENCES analysis.analysis_context (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.async_task ADD CONSTRAINT fk_analysis_async_task_input_snapshot_id FOREIGN KEY (input_snapshot_id) REFERENCES analysis.input_snapshot (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.async_task ADD CONSTRAINT fk_analysis_async_task_result_asset_id FOREIGN KEY (result_asset_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.task_attempt ADD CONSTRAINT fk_analysis_task_attempt_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.task_event ADD CONSTRAINT fk_analysis_task_event_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_job ADD CONSTRAINT fk_analysis_import_job_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_job ADD CONSTRAINT fk_analysis_import_job_file_asset_id FOREIGN KEY (file_asset_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_job ADD CONSTRAINT fk_analysis_import_job_dataset_id FOREIGN KEY (dataset_id) REFERENCES analysis.dataset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_job ADD CONSTRAINT fk_analysis_import_job_library_id FOREIGN KEY (library_id) REFERENCES analysis.person_library (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_job ADD CONSTRAINT fk_analysis_import_job_target_device_id FOREIGN KEY (target_device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_job ADD CONSTRAINT fk_analysis_import_job_template_code_template_version FOREIGN KEY (template_code,template_version) REFERENCES core.import_template (template_code,template_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_row_result ADD CONSTRAINT fk_analysis_import_row_result_task_id_attempt_no FOREIGN KEY (task_id,attempt_no) REFERENCES analysis.task_attempt (task_id,attempt_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_row_result ADD CONSTRAINT fk_analysis_import_row_result_library_id FOREIGN KEY (library_id) REFERENCES analysis.person_library (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_row_result ADD CONSTRAINT fk_analysis_import_row_result_member_id FOREIGN KEY (member_id) REFERENCES analysis.person_library_member (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_row_result ADD CONSTRAINT fk_analysis_import_row_result_profile_id FOREIGN KEY (profile_id) REFERENCES analysis.profile (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_row_result ADD CONSTRAINT fk_analysis_import_row_result_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_row_result ADD CONSTRAINT fk_analysis_import_row_result_dataset_id FOREIGN KEY (dataset_id) REFERENCES analysis.dataset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.import_row_result ADD CONSTRAINT fk_analysis_import_row_result_item_id FOREIGN KEY (item_id) REFERENCES analysis.dataset_item (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.task_row_error ADD CONSTRAINT fk_analysis_task_row_error_task_id_attempt_no FOREIGN KEY (task_id,attempt_no) REFERENCES analysis.task_attempt (task_id,attempt_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.export_job ADD CONSTRAINT fk_analysis_export_job_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.export_job ADD CONSTRAINT fk_analysis_export_job_input_snapshot_id FOREIGN KEY (input_snapshot_id) REFERENCES analysis.input_snapshot (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.export_job ADD CONSTRAINT fk_analysis_export_job_output_file_id FOREIGN KEY (output_file_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.source_mapping_version ADD CONSTRAINT fk_capture_source_mapping_version_source_code FOREIGN KEY (source_code) REFERENCES capture.ingestion_source (source_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.ingestion_batch ADD CONSTRAINT fk_capture_ingestion_batch_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.ingestion_batch ADD CONSTRAINT fk_capture_ingestion_batch_source_file_id FOREIGN KEY (source_file_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.ingestion_batch ADD CONSTRAINT fk_capture_ingestion_batch_original_batch_id FOREIGN KEY (original_batch_id) REFERENCES capture.ingestion_batch (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.ingestion_batch ADD CONSTRAINT fk_capture_ingestion_batch_source_code_mapping_version FOREIGN KEY (source_code,mapping_version) REFERENCES capture.source_mapping_version (source_code,mapping_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.source_event ADD CONSTRAINT fk_capture_source_event_source_code FOREIGN KEY (source_code) REFERENCES capture.ingestion_source (source_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.source_event ADD CONSTRAINT fk_capture_source_event_raw_file_id FOREIGN KEY (raw_file_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.ingestion_item ADD CONSTRAINT fk_capture_ingestion_item_batch_id FOREIGN KEY (batch_id) REFERENCES capture.ingestion_batch (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.ingestion_item ADD CONSTRAINT fk_capture_ingestion_item_source_event_id FOREIGN KEY (source_event_id) REFERENCES capture.source_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.ingestion_item ADD CONSTRAINT fk_capture_ingestion_item_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.quality_issue ADD CONSTRAINT fk_capture_quality_issue_ingestion_item_id FOREIGN KEY (ingestion_item_id) REFERENCES capture.ingestion_item (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.quality_issue ADD CONSTRAINT fk_capture_quality_issue_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.source_counter_sample ADD CONSTRAINT fk_capture_source_counter_sample_source_code FOREIGN KEY (source_code) REFERENCES capture.ingestion_source (source_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.source_counter_sample ADD CONSTRAINT fk_capture_source_counter_sample_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.target_lookup ADD CONSTRAINT fk_capture_target_lookup_target_id_target_type FOREIGN KEY (target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.target_attribution ADD CONSTRAINT fk_capture_target_attribution_target_id FOREIGN KEY (target_id) REFERENCES capture.target (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.target_attribution ADD CONSTRAINT fk_capture_target_attribution_region_code FOREIGN KEY (region_code) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.capture_event ADD CONSTRAINT fk_capture_capture_event_source_event_id FOREIGN KEY (source_event_id) REFERENCES capture.source_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.capture_event ADD CONSTRAINT fk_capture_capture_event_batch_id FOREIGN KEY (batch_id) REFERENCES capture.ingestion_batch (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.capture_event ADD CONSTRAINT fk_capture_capture_event_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.capture_event ADD CONSTRAINT fk_capture_capture_event_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.capture_event ADD CONSTRAINT fk_capture_capture_event_attribution_id FOREIGN KEY (attribution_id) REFERENCES capture.target_attribution (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.capture_event ADD CONSTRAINT fk_capture_capture_event_region_code_at_capture FOREIGN KEY (region_code_at_capture) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.capture_event ADD CONSTRAINT fk_capture_capture_event_primary_target_id_target_type FOREIGN KEY (primary_target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.capture_target ADD CONSTRAINT fk_capture_capture_target_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.capture_target ADD CONSTRAINT fk_capture_capture_target_target_id_target_type FOREIGN KEY (target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.fence_record ADD CONSTRAINT fk_capture_fence_record_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.mac_record ADD CONSTRAINT fk_capture_mac_record_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.plate_record ADD CONSTRAINT fk_capture_plate_record_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.face_record ADD CONSTRAINT fk_capture_face_record_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.fence_record ADD CONSTRAINT fk_capture_fence_record_imsi_target_id FOREIGN KEY (imsi_target_id) REFERENCES capture.target (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.fence_record ADD CONSTRAINT fk_capture_fence_record_imei_target_id FOREIGN KEY (imei_target_id) REFERENCES capture.target (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.mac_record ADD CONSTRAINT fk_capture_mac_record_mac_target_id FOREIGN KEY (mac_target_id) REFERENCES capture.target (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.plate_record ADD CONSTRAINT fk_capture_plate_record_plate_target_id FOREIGN KEY (plate_target_id) REFERENCES capture.target (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.plate_record ADD CONSTRAINT fk_capture_plate_record_vehicle_image_asset_id FOREIGN KEY (vehicle_image_asset_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.face_record ADD CONSTRAINT fk_capture_face_record_image_asset_id FOREIGN KEY (image_asset_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.face_feature ADD CONSTRAINT fk_capture_face_feature_capture_id FOREIGN KEY (capture_id) REFERENCES capture.face_record (capture_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.face_feature ADD CONSTRAINT fk_capture_face_feature_feature_asset_id FOREIGN KEY (feature_asset_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.capture_revision ADD CONSTRAINT fk_capture_capture_revision_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.capture_revision ADD CONSTRAINT fk_capture_capture_revision_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_job ADD CONSTRAINT fk_analysis_analysis_job_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_job ADD CONSTRAINT fk_analysis_analysis_job_context_id FOREIGN KEY (context_id) REFERENCES analysis.analysis_context (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_job ADD CONSTRAINT fk_analysis_analysis_job_input_snapshot_id FOREIGN KEY (input_snapshot_id) REFERENCES analysis.input_snapshot (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_job ADD CONSTRAINT fk_analysis_analysis_job_revised_from_id FOREIGN KEY (revised_from_id) REFERENCES analysis.analysis_job (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_job ADD CONSTRAINT fk_analysis_analysis_job_task_id_published_attempt_no FOREIGN KEY (task_id,published_attempt_no) REFERENCES analysis.task_attempt (task_id,attempt_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.companion_parameter ADD CONSTRAINT fk_analysis_companion_parameter_analysis_job_id FOREIGN KEY (analysis_job_id) REFERENCES analysis.analysis_job (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.companion_parameter ADD CONSTRAINT fk_analysis_companion_parameter_target_id_target_type FOREIGN KEY (target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.collision_parameter ADD CONSTRAINT fk_analysis_collision_parameter_analysis_job_id FOREIGN KEY (analysis_job_id) REFERENCES analysis.analysis_job (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.region_parameter ADD CONSTRAINT fk_analysis_region_parameter_analysis_job_id FOREIGN KEY (analysis_job_id) REFERENCES analysis.analysis_job (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_scope_site ADD CONSTRAINT fk_analysis_analysis_scope_site_analysis_job_id FOREIGN KEY (analysis_job_id) REFERENCES analysis.analysis_job (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_scope_site ADD CONSTRAINT fk_analysis_analysis_scope_site_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_scope_region ADD CONSTRAINT fk_analysis_analysis_scope_region_analysis_job_id FOREIGN KEY (analysis_job_id) REFERENCES analysis.analysis_job (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_scope_region ADD CONSTRAINT fk_analysis_analysis_scope_region_region_code FOREIGN KEY (region_code) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_selected_target ADD CONSTRAINT fk_analysis_analysis_selected_target_analysis_job_id FOREIGN KEY (analysis_job_id) REFERENCES analysis.analysis_job (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_selected_target ADD CONSTRAINT fk_analysis_analysis_selected_target_target_id_target_type FOREIGN KEY (target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.selected_region ADD CONSTRAINT fk_analysis_selected_region_analysis_job_id FOREIGN KEY (analysis_job_id) REFERENCES analysis.analysis_job (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_result ADD CONSTRAINT fk_analysis_analysis_result_analysis_job_id_task_id FOREIGN KEY (analysis_job_id,task_id) REFERENCES analysis.analysis_job (id,task_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_result ADD CONSTRAINT fk_analysis_analysis_result_task_id_attempt_no FOREIGN KEY (task_id,attempt_no) REFERENCES analysis.task_attempt (task_id,attempt_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.analysis_result ADD CONSTRAINT fk_analysis_analysis_result_target_id_target_type FOREIGN KEY (target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.companion_result ADD CONSTRAINT fk_analysis_companion_result_result_id FOREIGN KEY (result_id) REFERENCES analysis.analysis_result (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.collision_result ADD CONSTRAINT fk_analysis_collision_result_result_id FOREIGN KEY (result_id) REFERENCES analysis.analysis_result (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.region_result ADD CONSTRAINT fk_analysis_region_result_result_id FOREIGN KEY (result_id) REFERENCES analysis.analysis_result (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.collision_event ADD CONSTRAINT fk_analysis_collision_event_result_id FOREIGN KEY (result_id) REFERENCES analysis.collision_result (result_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.result_evidence ADD CONSTRAINT fk_analysis_result_evidence_result_id FOREIGN KEY (result_id) REFERENCES analysis.analysis_result (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.result_evidence ADD CONSTRAINT fk_analysis_result_evidence_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.result_region ADD CONSTRAINT fk_analysis_result_region_result_id FOREIGN KEY (result_id) REFERENCES analysis.analysis_result (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.result_region ADD CONSTRAINT fk_analysis_result_region_region_code FOREIGN KEY (region_code) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.result_selected_region ADD CONSTRAINT fk_analysis_result_selected_region_result_id FOREIGN KEY (result_id) REFERENCES analysis.region_result (result_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.result_selected_region ADD CONSTRAINT fk_analysis_result_selected_region_analysis_job_id_region_no FOREIGN KEY (analysis_job_id,region_no) REFERENCES analysis.selected_region (analysis_job_id,region_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset ADD CONSTRAINT fk_analysis_dataset_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset ADD CONSTRAINT fk_analysis_dataset_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset ADD CONSTRAINT fk_analysis_dataset_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset ADD CONSTRAINT fk_analysis_dataset_source_context_id FOREIGN KEY (source_context_id) REFERENCES analysis.analysis_context (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset_version ADD CONSTRAINT fk_analysis_dataset_version_dataset_id FOREIGN KEY (dataset_id) REFERENCES analysis.dataset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset_version ADD CONSTRAINT fk_analysis_dataset_version_source_snapshot_id FOREIGN KEY (source_snapshot_id) REFERENCES analysis.input_snapshot (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset_version ADD CONSTRAINT fk_analysis_dataset_version_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset ADD CONSTRAINT fk_analysis_dataset_id_current_version FOREIGN KEY (id,current_version) REFERENCES analysis.dataset_version (dataset_id,version_no) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE analysis.dataset_item ADD CONSTRAINT fk_analysis_dataset_item_dataset_id_element_type FOREIGN KEY (dataset_id,element_type) REFERENCES analysis.dataset (id,element_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset_item ADD CONSTRAINT fk_analysis_dataset_item_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset_item ADD CONSTRAINT fk_analysis_dataset_item_target_id_target_type FOREIGN KEY (target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset_item ADD CONSTRAINT fk_analysis_dataset_item_source_capture_id FOREIGN KEY (source_capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset_item ADD CONSTRAINT fk_analysis_dataset_item_added_by FOREIGN KEY (added_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset_item ADD CONSTRAINT fk_analysis_dataset_item_dataset_id_valid_from_version FOREIGN KEY (dataset_id,valid_from_version) REFERENCES analysis.dataset_version (dataset_id,version_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset_item ADD CONSTRAINT fk_analysis_dataset_item_dataset_id_valid_to_version FOREIGN KEY (dataset_id,valid_to_version) REFERENCES analysis.dataset_version (dataset_id,version_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset_grant ADD CONSTRAINT fk_analysis_dataset_grant_dataset_id FOREIGN KEY (dataset_id) REFERENCES analysis.dataset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset_grant ADD CONSTRAINT fk_analysis_dataset_grant_user_id FOREIGN KEY (user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.dataset_grant ADD CONSTRAINT fk_analysis_dataset_grant_role_id FOREIGN KEY (role_id) REFERENCES auth.role (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.set_job ADD CONSTRAINT fk_analysis_set_job_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.set_job ADD CONSTRAINT fk_analysis_set_job_result_dataset_id_result_dataset_version FOREIGN KEY (result_dataset_id,result_dataset_version) REFERENCES analysis.dataset_version (dataset_id,version_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.set_operand ADD CONSTRAINT fk_analysis_set_operand_set_job_id FOREIGN KEY (set_job_id) REFERENCES analysis.set_job (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.set_operand ADD CONSTRAINT fk_analysis_set_operand_dataset_id_dataset_version FOREIGN KEY (dataset_id,dataset_version) REFERENCES analysis.dataset_version (dataset_id,version_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile ADD CONSTRAINT fk_analysis_profile_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile ADD CONSTRAINT fk_analysis_profile_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_profile ADD CONSTRAINT fk_analysis_person_profile_profile_id_profile_type FOREIGN KEY (profile_id,profile_type) REFERENCES analysis.profile (id,profile_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_profile ADD CONSTRAINT fk_analysis_person_profile_residence_region_code FOREIGN KEY (residence_region_code) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_profile ADD CONSTRAINT fk_analysis_person_profile_suspected_site_id FOREIGN KEY (suspected_site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_profile ADD CONSTRAINT fk_analysis_person_profile_portrait_asset_id FOREIGN KEY (portrait_asset_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.vehicle_profile ADD CONSTRAINT fk_analysis_vehicle_profile_profile_id_profile_type FOREIGN KEY (profile_id,profile_type) REFERENCES analysis.profile (id,profile_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.vehicle_profile ADD CONSTRAINT fk_analysis_vehicle_profile_primary_plate_target_id FOREIGN KEY (primary_plate_target_id) REFERENCES capture.target (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.vehicle_profile ADD CONSTRAINT fk_analysis_vehicle_profile_vehicle_image_asset_id FOREIGN KEY (vehicle_image_asset_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_identifier_index ADD CONSTRAINT fk_analysis_profile_identifier_index_profile_id FOREIGN KEY (profile_id) REFERENCES analysis.profile (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_link ADD CONSTRAINT fk_analysis_profile_link_profile_id FOREIGN KEY (profile_id) REFERENCES analysis.profile (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_link ADD CONSTRAINT fk_analysis_profile_link_target_id_target_type FOREIGN KEY (target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_evidence ADD CONSTRAINT fk_analysis_profile_evidence_profile_id FOREIGN KEY (profile_id) REFERENCES analysis.profile (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_evidence ADD CONSTRAINT fk_analysis_profile_evidence_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_link_evidence ADD CONSTRAINT fk_analysis_profile_link_evidence_link_id FOREIGN KEY (link_id) REFERENCES analysis.profile_link (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_link_evidence ADD CONSTRAINT fk_analysis_profile_link_evidence_evidence_id FOREIGN KEY (evidence_id) REFERENCES analysis.profile_evidence (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_revision ADD CONSTRAINT fk_analysis_profile_revision_profile_id FOREIGN KEY (profile_id) REFERENCES analysis.profile (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_revision ADD CONSTRAINT fk_analysis_profile_revision_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_revision ADD CONSTRAINT fk_analysis_profile_revision_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_revision_member ADD CONSTRAINT fk_analysis_profile_revision_member_revision_id FOREIGN KEY (revision_id) REFERENCES analysis.profile_revision (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_revision_member ADD CONSTRAINT fk_analysis_profile_revision_member_profile_id FOREIGN KEY (profile_id) REFERENCES analysis.profile (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_revision_evidence ADD CONSTRAINT fk_analysis_profile_revision_evidence_revision_id FOREIGN KEY (revision_id) REFERENCES analysis.profile_revision (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_revision_evidence ADD CONSTRAINT fk_analysis_profile_revision_evidence_evidence_id FOREIGN KEY (evidence_id) REFERENCES analysis.profile_evidence (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_conflict ADD CONSTRAINT fk_analysis_profile_conflict_profile_id FOREIGN KEY (profile_id) REFERENCES analysis.profile (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_conflict ADD CONSTRAINT fk_analysis_profile_conflict_resolved_revision_id FOREIGN KEY (resolved_revision_id) REFERENCES analysis.profile_revision (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_conflict_evidence ADD CONSTRAINT fk_analysis_profile_conflict_evidence_conflict_id FOREIGN KEY (conflict_id) REFERENCES analysis.profile_conflict (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.profile_conflict_evidence ADD CONSTRAINT fk_analysis_profile_conflict_evidence_evidence_id FOREIGN KEY (evidence_id) REFERENCES analysis.profile_evidence (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_library ADD CONSTRAINT fk_analysis_person_library_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_library ADD CONSTRAINT fk_analysis_person_library_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_library ADD CONSTRAINT fk_analysis_person_library_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.library_scope_site ADD CONSTRAINT fk_analysis_library_scope_site_library_id FOREIGN KEY (library_id) REFERENCES analysis.person_library (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.library_scope_site ADD CONSTRAINT fk_analysis_library_scope_site_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.library_scope_data_type ADD CONSTRAINT fk_analysis_library_scope_data_type_library_id FOREIGN KEY (library_id) REFERENCES analysis.person_library (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_library_member ADD CONSTRAINT fk_analysis_person_library_member_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_library_member ADD CONSTRAINT fk_analysis_person_library_member_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_library_member ADD CONSTRAINT fk_analysis_person_library_member_library_id FOREIGN KEY (library_id) REFERENCES analysis.person_library (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_library_member ADD CONSTRAINT fk_analysis_person_library_member_profile_id FOREIGN KEY (profile_id) REFERENCES analysis.profile (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_library_member ADD CONSTRAINT fk_analysis_person_library_member_primary_target_link_id FOREIGN KEY (primary_target_link_id) REFERENCES analysis.profile_link (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.person_library_member ADD CONSTRAINT fk_analysis_person_library_member_added_by FOREIGN KEY (added_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.face_search_job ADD CONSTRAINT fk_analysis_face_search_job_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.face_search_job ADD CONSTRAINT fk_analysis_face_search_job_image_asset_id FOREIGN KEY (image_asset_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.face_search_job ADD CONSTRAINT fk_analysis_face_search_job_input_snapshot_id FOREIGN KEY (input_snapshot_id) REFERENCES analysis.input_snapshot (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.face_search_job ADD CONSTRAINT fk_analysis_face_search_job_task_id_published_attempt_no FOREIGN KEY (task_id,published_attempt_no) REFERENCES analysis.task_attempt (task_id,attempt_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.face_search_result ADD CONSTRAINT fk_analysis_face_search_result_task_id_attempt_no FOREIGN KEY (task_id,attempt_no) REFERENCES analysis.task_attempt (task_id,attempt_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.face_search_result ADD CONSTRAINT fk_analysis_face_search_result_capture_id FOREIGN KEY (capture_id) REFERENCES capture.face_record (capture_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.trend_model ADD CONSTRAINT fk_analysis_trend_model_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.trend_model ADD CONSTRAINT fk_analysis_trend_model_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_parameter_definition ADD CONSTRAINT fk_analysis_model_parameter_definition_model_id FOREIGN KEY (model_id) REFERENCES analysis.trend_model (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_parameter_version ADD CONSTRAINT fk_analysis_model_parameter_version_model_id FOREIGN KEY (model_id) REFERENCES analysis.trend_model (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_parameter_version ADD CONSTRAINT fk_analysis_model_parameter_version_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_parameter_value ADD CONSTRAINT fk_analysis_model_parameter_value_parameter_version_id FOREIGN KEY (parameter_version_id) REFERENCES analysis.model_parameter_version (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_parameter_value ADD CONSTRAINT fk_analysis_model_parameter_value_definition_id_value_type FOREIGN KEY (definition_id,value_type) REFERENCES analysis.model_parameter_definition (id,value_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_matching_rule ADD CONSTRAINT fk_analysis_model_matching_rule_parameter_version_id FOREIGN KEY (parameter_version_id) REFERENCES analysis.model_parameter_version (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_tag_binding ADD CONSTRAINT fk_analysis_model_tag_binding_model_id FOREIGN KEY (model_id) REFERENCES analysis.trend_model (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_tag_binding ADD CONSTRAINT fk_analysis_model_tag_binding_tag_code FOREIGN KEY (tag_code) REFERENCES analysis.model_tag (tag_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_rule_output_tag ADD CONSTRAINT fk_analysis_model_rule_output_tag_parameter_v_c46009be0783 FOREIGN KEY (parameter_version_id,rule_code) REFERENCES analysis.model_matching_rule (parameter_version_id,rule_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_rule_output_tag ADD CONSTRAINT fk_analysis_model_rule_output_tag_tag_code FOREIGN KEY (tag_code) REFERENCES analysis.model_tag (tag_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_execution ADD CONSTRAINT fk_analysis_model_execution_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_execution ADD CONSTRAINT fk_analysis_model_execution_parameter_version_id_model_id FOREIGN KEY (parameter_version_id,model_id) REFERENCES analysis.model_parameter_version (id,model_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_execution ADD CONSTRAINT fk_analysis_model_execution_input_snapshot_id FOREIGN KEY (input_snapshot_id) REFERENCES analysis.input_snapshot (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_execution ADD CONSTRAINT fk_analysis_model_execution_task_id_published_attempt_no FOREIGN KEY (task_id,published_attempt_no) REFERENCES analysis.task_attempt (task_id,attempt_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_result ADD CONSTRAINT fk_analysis_model_result_execution_task_id FOREIGN KEY (execution_task_id) REFERENCES analysis.model_execution (task_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_result ADD CONSTRAINT fk_analysis_model_result_execution_task_id_attempt_no FOREIGN KEY (execution_task_id,attempt_no) REFERENCES analysis.task_attempt (task_id,attempt_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_result ADD CONSTRAINT fk_analysis_model_result_target_id_target_type FOREIGN KEY (target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_result_tag ADD CONSTRAINT fk_analysis_model_result_tag_result_id FOREIGN KEY (result_id) REFERENCES analysis.model_result (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_result_tag ADD CONSTRAINT fk_analysis_model_result_tag_tag_code FOREIGN KEY (tag_code) REFERENCES analysis.model_tag (tag_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_result_evidence ADD CONSTRAINT fk_analysis_model_result_evidence_result_id FOREIGN KEY (result_id) REFERENCES analysis.model_result (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_result_evidence ADD CONSTRAINT fk_analysis_model_result_evidence_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.model_result_evidence ADD CONSTRAINT fk_analysis_model_result_evidence_profile_evidence_id FOREIGN KEY (profile_evidence_id) REFERENCES analysis.profile_evidence (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule ADD CONSTRAINT fk_operation_watch_rule_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule ADD CONSTRAINT fk_operation_watch_rule_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule ADD CONSTRAINT fk_operation_watch_rule_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule ADD CONSTRAINT fk_operation_watch_rule_context_id FOREIGN KEY (context_id) REFERENCES analysis.analysis_context (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule_revision ADD CONSTRAINT fk_operation_watch_rule_revision_rule_id FOREIGN KEY (rule_id) REFERENCES operation.watch_rule (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule_revision ADD CONSTRAINT fk_operation_watch_rule_revision_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule ADD CONSTRAINT fk_operation_watch_rule_id_current_rule_version FOREIGN KEY (id,current_rule_version) REFERENCES operation.watch_rule_revision (rule_id,rule_version) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE operation.watch_rule_revision ADD CONSTRAINT fk_operation_watch_rule_revision_notification_e7b677257a23 FOREIGN KEY (notification_template_code,notification_template_version) REFERENCES operation.notification_template (template_code,template_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule_target ADD CONSTRAINT fk_operation_watch_rule_target_rule_id_rule_version FOREIGN KEY (rule_id,rule_version) REFERENCES operation.watch_rule_revision (rule_id,rule_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule_target ADD CONSTRAINT fk_operation_watch_rule_target_target_id_target_type FOREIGN KEY (target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule_target ADD CONSTRAINT fk_operation_watch_rule_target_source_profile_id FOREIGN KEY (source_profile_id) REFERENCES analysis.profile (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule_target ADD CONSTRAINT fk_operation_watch_rule_target_source_library_id FOREIGN KEY (source_library_id) REFERENCES analysis.person_library (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule_site ADD CONSTRAINT fk_operation_watch_rule_site_rule_id_rule_version FOREIGN KEY (rule_id,rule_version) REFERENCES operation.watch_rule_revision (rule_id,rule_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_rule_site ADD CONSTRAINT fk_operation_watch_rule_site_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.alert_event ADD CONSTRAINT fk_operation_alert_event_rule_id_rule_version FOREIGN KEY (rule_id,rule_version) REFERENCES operation.watch_rule_revision (rule_id,rule_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.alert_event ADD CONSTRAINT fk_operation_alert_event_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.alert_event ADD CONSTRAINT fk_operation_alert_event_target_id_target_type FOREIGN KEY (target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.alert_event ADD CONSTRAINT fk_operation_alert_event_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.alert_event ADD CONSTRAINT fk_operation_alert_event_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_statistics_run ADD CONSTRAINT fk_operation_watch_statistics_run_rule_id_final_rule_version FOREIGN KEY (rule_id,final_rule_version) REFERENCES operation.watch_rule_revision (rule_id,rule_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_statistics ADD CONSTRAINT fk_operation_watch_statistics_rule_id FOREIGN KEY (rule_id) REFERENCES operation.watch_statistics_run (rule_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_statistics ADD CONSTRAINT fk_operation_watch_statistics_target_id_target_type FOREIGN KEY (target_id,target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_statistics ADD CONSTRAINT fk_operation_watch_statistics_most_frequent_site_id FOREIGN KEY (most_frequent_site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_template_variable ADD CONSTRAINT fk_operation_notification_template_variable_t_2c16fd6bed05 FOREIGN KEY (template_code,template_version) REFERENCES operation.notification_template (template_code,template_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_plan ADD CONSTRAINT fk_operation_notification_plan_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_plan ADD CONSTRAINT fk_operation_notification_plan_updated_by FOREIGN KEY (updated_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_plan ADD CONSTRAINT fk_operation_notification_plan_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_plan ADD CONSTRAINT fk_operation_notification_plan_last_execution_task_id FOREIGN KEY (last_execution_task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_plan_revision ADD CONSTRAINT fk_operation_notification_plan_revision_plan_id FOREIGN KEY (plan_id) REFERENCES operation.notification_plan (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_plan_revision ADD CONSTRAINT fk_operation_notification_plan_revision_templ_fc15a562df26 FOREIGN KEY (template_code,template_version) REFERENCES operation.notification_template (template_code,template_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_plan_revision ADD CONSTRAINT fk_operation_notification_plan_revision_created_by FOREIGN KEY (created_by) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_plan ADD CONSTRAINT fk_operation_notification_plan_id_current_plan_version FOREIGN KEY (id,current_plan_version) REFERENCES operation.notification_plan_revision (plan_id,plan_version) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE operation.notification_recipient ADD CONSTRAINT fk_operation_notification_recipient_recipient_user_id FOREIGN KEY (recipient_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_recipient ADD CONSTRAINT fk_operation_notification_recipient_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_plan_recipient ADD CONSTRAINT fk_operation_notification_plan_recipient_plan_1f99bee371dd FOREIGN KEY (plan_id,plan_version) REFERENCES operation.notification_plan_revision (plan_id,plan_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_plan_recipient ADD CONSTRAINT fk_operation_notification_plan_recipient_recipient_id FOREIGN KEY (recipient_id) REFERENCES operation.notification_recipient (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_notification_recipient ADD CONSTRAINT fk_operation_watch_notification_recipient_rul_d831b22528f9 FOREIGN KEY (rule_id,rule_version) REFERENCES operation.watch_rule_revision (rule_id,rule_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.watch_notification_recipient ADD CONSTRAINT fk_operation_watch_notification_recipient_recipient_id FOREIGN KEY (recipient_id) REFERENCES operation.notification_recipient (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_execution ADD CONSTRAINT fk_operation_notification_execution_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.notification_execution ADD CONSTRAINT fk_operation_notification_execution_plan_id_plan_version FOREIGN KEY (plan_id,plan_version) REFERENCES operation.notification_plan_revision (plan_id,plan_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.delivery ADD CONSTRAINT fk_operation_delivery_execution_task_id FOREIGN KEY (execution_task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.delivery ADD CONSTRAINT fk_operation_delivery_alert_id FOREIGN KEY (alert_id) REFERENCES operation.alert_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.delivery ADD CONSTRAINT fk_operation_delivery_recipient_id FOREIGN KEY (recipient_id) REFERENCES operation.notification_recipient (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.delivery ADD CONSTRAINT fk_operation_delivery_channel_id FOREIGN KEY (channel_id) REFERENCES operation.notification_channel (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.delivery ADD CONSTRAINT fk_operation_delivery_plan_id_plan_version FOREIGN KEY (plan_id,plan_version) REFERENCES operation.notification_plan_revision (plan_id,plan_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.delivery ADD CONSTRAINT fk_operation_delivery_rule_id_rule_version FOREIGN KEY (rule_id,rule_version) REFERENCES operation.watch_rule_revision (rule_id,rule_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.delivery ADD CONSTRAINT fk_operation_delivery_template_code_template_version FOREIGN KEY (template_code,template_version) REFERENCES operation.notification_template (template_code,template_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.delivery_attempt ADD CONSTRAINT fk_operation_delivery_attempt_delivery_id FOREIGN KEY (delivery_id) REFERENCES operation.delivery (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.platform_message ADD CONSTRAINT fk_operation_platform_message_delivery_id FOREIGN KEY (delivery_id) REFERENCES operation.delivery (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.platform_message ADD CONSTRAINT fk_operation_platform_message_recipient_user_id FOREIGN KEY (recipient_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.platform_message ADD CONSTRAINT fk_operation_platform_message_alert_id FOREIGN KEY (alert_id) REFERENCES operation.alert_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.platform_message ADD CONSTRAINT fk_operation_platform_message_plan_id FOREIGN KEY (plan_id) REFERENCES operation.notification_plan (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.sms_modem ADD CONSTRAINT fk_operation_sms_modem_channel_id FOREIGN KEY (channel_id) REFERENCES operation.notification_channel (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.sms_modem_status ADD CONSTRAINT fk_operation_sms_modem_status_modem_id FOREIGN KEY (modem_id) REFERENCES operation.sms_modem (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.metric_scope ADD CONSTRAINT fk_core_metric_scope_region_code FOREIGN KEY (region_code) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.metric_scope ADD CONSTRAINT fk_core_metric_scope_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.metric_scope ADD CONSTRAINT fk_core_metric_scope_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.metric_snapshot ADD CONSTRAINT fk_core_metric_snapshot_metric_code_definition_version FOREIGN KEY (metric_code,definition_version) REFERENCES core.metric_definition (metric_code,definition_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.metric_snapshot ADD CONSTRAINT fk_core_metric_snapshot_scope_id FOREIGN KEY (scope_id) REFERENCES core.metric_scope (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.metric_refresh_checkpoint ADD CONSTRAINT fk_core_metric_refresh_checkpoint_metric_code_e5048d51f108 FOREIGN KEY (metric_code,definition_version) REFERENCES core.metric_definition (metric_code,definition_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.anomaly_event ADD CONSTRAINT fk_core_anomaly_event_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE core.anomaly_event ADD CONSTRAINT fk_core_anomaly_event_ingestion_item_id FOREIGN KEY (ingestion_item_id) REFERENCES capture.ingestion_item (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.device_command_job ADD CONSTRAINT fk_operation_device_command_job_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.device_command_job ADD CONSTRAINT fk_operation_device_command_job_group_id FOREIGN KEY (group_id) REFERENCES core.device_group (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.device_command_job ADD CONSTRAINT fk_operation_device_command_job_region_code FOREIGN KEY (region_code) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.device_command_job ADD CONSTRAINT fk_operation_device_command_job_revised_from_task_id FOREIGN KEY (revised_from_task_id) REFERENCES operation.device_command_job (task_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.device_command_job ADD CONSTRAINT fk_operation_device_command_job_confirmation_record_id FOREIGN KEY (confirmation_record_id) REFERENCES audit.sensitive_action_review (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.device_command ADD CONSTRAINT fk_operation_device_command_job_task_id FOREIGN KEY (job_task_id) REFERENCES operation.device_command_job (task_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.device_command ADD CONSTRAINT fk_operation_device_command_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.device_command_attempt ADD CONSTRAINT fk_operation_device_command_attempt_command_id FOREIGN KEY (command_id) REFERENCES operation.device_command (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_job ADD CONSTRAINT fk_operation_report_job_task_id FOREIGN KEY (task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_job ADD CONSTRAINT fk_operation_report_job_input_snapshot_id FOREIGN KEY (input_snapshot_id) REFERENCES analysis.input_snapshot (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_job ADD CONSTRAINT fk_operation_report_job_output_file_id FOREIGN KEY (output_file_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_job ADD CONSTRAINT fk_operation_report_job_task_id_published_attempt_no FOREIGN KEY (task_id,published_attempt_no) REFERENCES analysis.task_attempt (task_id,attempt_no) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_definition ADD CONSTRAINT fk_operation_report_definition_report_id FOREIGN KEY (report_id) REFERENCES operation.report_job (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_definition ADD CONSTRAINT fk_operation_report_definition_region_code FOREIGN KEY (region_code) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_definition_site ADD CONSTRAINT fk_operation_report_definition_site_definition_id FOREIGN KEY (definition_id) REFERENCES operation.report_definition (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_definition_site ADD CONSTRAINT fk_operation_report_definition_site_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_device_row ADD CONSTRAINT fk_operation_report_device_row_definition_id FOREIGN KEY (definition_id) REFERENCES operation.report_definition (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_device_row ADD CONSTRAINT fk_operation_report_device_row_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_device_row ADD CONSTRAINT fk_operation_report_device_row_site_id FOREIGN KEY (site_id) REFERENCES core.site (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_device_row ADD CONSTRAINT fk_operation_report_device_row_region_code FOREIGN KEY (region_code) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_region_row ADD CONSTRAINT fk_operation_report_region_row_definition_id FOREIGN KEY (definition_id) REFERENCES operation.report_definition (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_region_row ADD CONSTRAINT fk_operation_report_region_row_region_code FOREIGN KEY (region_code) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_metric_row ADD CONSTRAINT fk_operation_report_metric_row_definition_id FOREIGN KEY (definition_id) REFERENCES operation.report_definition (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_metric_row ADD CONSTRAINT fk_operation_report_metric_row_metric_code_de_77e3e7199da8 FOREIGN KEY (metric_code,definition_version) REFERENCES core.metric_definition (metric_code,definition_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_series ADD CONSTRAINT fk_operation_report_series_definition_id FOREIGN KEY (definition_id) REFERENCES operation.report_definition (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_series ADD CONSTRAINT fk_operation_report_series_metric_code_definition_version FOREIGN KEY (metric_code,definition_version) REFERENCES core.metric_definition (metric_code,definition_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_series ADD CONSTRAINT fk_operation_report_series_device_id FOREIGN KEY (device_id) REFERENCES core.device (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_series ADD CONSTRAINT fk_operation_report_series_region_code FOREIGN KEY (region_code) REFERENCES core.region (region_code) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_series_point ADD CONSTRAINT fk_operation_report_series_point_series_id FOREIGN KEY (series_id) REFERENCES operation.report_series (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_component_row ADD CONSTRAINT fk_operation_report_component_row_definition_id FOREIGN KEY (definition_id) REFERENCES operation.report_definition (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_output_file ADD CONSTRAINT fk_operation_report_output_file_report_id FOREIGN KEY (report_id) REFERENCES operation.report_job (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_output_file ADD CONSTRAINT fk_operation_report_output_file_definition_id FOREIGN KEY (definition_id) REFERENCES operation.report_definition (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.report_output_file ADD CONSTRAINT fk_operation_report_output_file_file_asset_id FOREIGN KEY (file_asset_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE audit.audit_event ADD CONSTRAINT fk_audit_audit_event_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE audit.audit_event ADD CONSTRAINT fk_audit_audit_event_business_task_id FOREIGN KEY (business_task_id) REFERENCES analysis.async_task (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.decryption_request ADD CONSTRAINT fk_operation_decryption_request_requester_user_id FOREIGN KEY (requester_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.decryption_request ADD CONSTRAINT fk_operation_decryption_request_recipient_user_id FOREIGN KEY (recipient_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.decryption_request ADD CONSTRAINT fk_operation_decryption_request_context_id FOREIGN KEY (context_id) REFERENCES analysis.analysis_context (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.decryption_request ADD CONSTRAINT fk_operation_decryption_request_input_snapshot_id FOREIGN KEY (input_snapshot_id) REFERENCES analysis.input_snapshot (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.decryption_request ADD CONSTRAINT fk_operation_decryption_request_input_file_id FOREIGN KEY (input_file_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.decryption_capture ADD CONSTRAINT fk_operation_decryption_capture_request_id FOREIGN KEY (request_id) REFERENCES operation.decryption_request (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.decryption_capture ADD CONSTRAINT fk_operation_decryption_capture_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE audit.sensitive_action_review ADD CONSTRAINT fk_audit_sensitive_action_review_decryption_request_id FOREIGN KEY (decryption_request_id) REFERENCES operation.decryption_request (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE audit.sensitive_action_review ADD CONSTRAINT fk_audit_sensitive_action_review_device_command_task_id FOREIGN KEY (device_command_task_id) REFERENCES operation.device_command_job (task_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE audit.sensitive_action_review ADD CONSTRAINT fk_audit_sensitive_action_review_export_task_id FOREIGN KEY (export_task_id) REFERENCES analysis.export_job (task_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE audit.sensitive_action_review ADD CONSTRAINT fk_audit_sensitive_action_review_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.decryption_result_grant ADD CONSTRAINT fk_operation_decryption_result_grant_request_id FOREIGN KEY (request_id) REFERENCES operation.decryption_request (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.decryption_result_grant ADD CONSTRAINT fk_operation_decryption_result_grant_authorized_user_id FOREIGN KEY (authorized_user_id) REFERENCES auth.app_user (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.decryption_result_grant ADD CONSTRAINT fk_operation_decryption_result_grant_result_file_id FOREIGN KEY (result_file_id) REFERENCES core.file_asset (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.ingestion_item ADD CONSTRAINT fk_capture_ingestion_item_device_status_event_id FOREIGN KEY (device_status_event_id) REFERENCES core.device_status_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE capture.ingestion_item ADD CONSTRAINT fk_capture_ingestion_item_counter_sample_id FOREIGN KEY (counter_sample_id) REFERENCES capture.source_counter_sample (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.collision_event_evidence ADD CONSTRAINT fk_analysis_collision_event_evidence_collision_event_id FOREIGN KEY (collision_event_id) REFERENCES analysis.collision_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.collision_event_evidence ADD CONSTRAINT fk_analysis_collision_event_evidence_capture_id FOREIGN KEY (capture_id) REFERENCES capture.capture_event (id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.trend_model ADD CONSTRAINT fk_analysis_trend_model_current_parameter_version_id_id FOREIGN KEY (current_parameter_version_id,id) REFERENCES analysis.model_parameter_version (id,model_id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE analysis.result_selected_region ADD CONSTRAINT fk_analysis_result_selected_region_result_id_analysis_job_id FOREIGN KEY (result_id,analysis_job_id) REFERENCES analysis.analysis_result (id,analysis_job_id) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.delivery ADD CONSTRAINT fk_operation_delivery_execution_task_id_plan_id_plan_version FOREIGN KEY (execution_task_id,plan_id,plan_version) REFERENCES operation.notification_execution (task_id,plan_id,plan_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation.delivery ADD CONSTRAINT fk_operation_delivery_alert_id_rule_id_rule_version FOREIGN KEY (alert_id,rule_id,rule_version) REFERENCES operation.alert_event (id,rule_id,rule_version) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE analysis.vehicle_profile ADD CONSTRAINT fk_analysis_vehicle_profile_primary_plate_tar_9975f2f92acc FOREIGN KEY (primary_plate_target_id,plate_target_type) REFERENCES capture.target (id,target_type) ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE;

CREATE INDEX ix_session_user_active ON auth.user_session USING btree (user_id,idle_expires_at) WHERE revoked_at IS NULL;

CREATE INDEX ix_csrf_expiry ON auth.csrf_session USING btree (expires_at);

CREATE INDEX ix_region_parent ON core.region USING btree (parent_region_code);

CREATE INDEX ix_region_geometry ON core.region USING gist (boundary) WHERE boundary IS NOT NULL;

CREATE INDEX ix_site_region_name ON core.site USING btree (region_code,name,id) WHERE deleted=false;

CREATE INDEX ix_site_geo ON core.site USING gist (location) WHERE location IS NOT NULL AND deleted=false;

CREATE INDEX ix_device_site_status ON core.device USING btree (site_id,status,id) WHERE deleted=false;

CREATE INDEX ix_device_category ON core.device USING btree (category_code,id) WHERE deleted=false;

CREATE INDEX ix_device_geo ON core.device USING gist (location) WHERE location IS NOT NULL AND deleted=false;

CREATE INDEX ix_group_member_device ON core.device_group_member USING btree (device_id,group_id);

CREATE INDEX ix_device_status_history ON core.device_status_event USING btree (device_id,occurred_at DESC,id DESC);

CREATE INDEX ix_device_parameters ON core.device_parameter_value USING btree (device_id,value_kind,parameter_version DESC);

CREATE INDEX ix_file_expiry ON core.file_asset USING btree (expires_at) WHERE expires_at IS NOT NULL;

CREATE INDEX ix_idempotency_expiry ON core.idempotency_request USING btree (expires_at);

CREATE INDEX ix_outbox_pending ON core.outbox_event USING btree (next_attempt_at,id) WHERE status IN ('PENDING','FAILED');

CREATE INDEX ix_context_owner_expiry ON analysis.analysis_context USING btree (owner_user_id,expires_at);

CREATE INDEX ix_task_claim ON analysis.async_task USING btree (priority,next_run_at,created_at,id) WHERE status IN ('PENDING','QUEUED') AND deleted=false;

CREATE INDEX ix_task_expired_lease ON analysis.async_task USING btree (lease_until,id) WHERE status='RUNNING';

CREATE INDEX ix_task_owner_list ON analysis.async_task USING btree (owner_user_id,created_at DESC,id DESC) WHERE deleted=false;

CREATE INDEX ix_task_event_history ON analysis.task_event USING btree (task_id,occurred_at,id);

CREATE INDEX ix_task_row_error ON analysis.task_row_error USING btree (task_id,attempt_no,row_number,id);

CREATE INDEX ix_ingestion_source_list ON capture.ingestion_batch USING btree (source_code,created_at DESC,id DESC);

CREATE INDEX ix_quality_time ON capture.quality_issue USING btree (occurred_at DESC,id DESC);

CREATE INDEX ix_counter_device_time ON capture.source_counter_sample USING btree (device_id,period_from DESC,id);

CREATE INDEX ix_target_attribution ON capture.target_attribution USING btree (target_id,valid_from DESC,id);

CREATE INDEX ix_capture_type_time ON capture.capture_event USING btree (data_type,capture_time DESC,id DESC) WHERE status='VALID';

CREATE INDEX ix_capture_target_time ON capture.capture_event USING btree (primary_target_id,capture_time DESC,id DESC) WHERE status='VALID' AND primary_target_id IS NOT NULL;

CREATE INDEX ix_capture_site_time ON capture.capture_event USING btree (site_id,capture_time DESC,id DESC) WHERE status='VALID' AND site_id IS NOT NULL;

CREATE INDEX ix_capture_device_time ON capture.capture_event USING btree (device_id,capture_time DESC,id DESC) WHERE status='VALID' AND device_id IS NOT NULL;

CREATE INDEX ix_capture_geo ON capture.capture_event USING gist (location) WHERE location IS NOT NULL AND status='VALID';

CREATE INDEX ix_capture_batch ON capture.capture_event USING btree (batch_id,id);

CREATE INDEX ix_capture_other_target ON capture.capture_target USING btree (target_id,capture_id);

CREATE INDEX ix_analysis_kind_created ON analysis.analysis_job USING btree (kind,created_at DESC,id DESC) WHERE deleted=false;

CREATE INDEX ix_analysis_result_page ON analysis.analysis_result USING btree (analysis_job_id,attempt_no,data_type,id);

CREATE INDEX ix_collision_events ON analysis.collision_event USING btree (result_id,occurred_at,id);

CREATE UNIQUE INDEX ux_dataset_active_capture ON analysis.dataset_item USING btree (dataset_id,capture_id) WHERE element_type='CAPTURE' AND valid_to_version IS NULL;

CREATE UNIQUE INDEX ux_dataset_active_target ON analysis.dataset_item USING btree (dataset_id,target_id) WHERE element_type='TARGET' AND valid_to_version IS NULL;

CREATE INDEX ix_dataset_history ON analysis.dataset_item USING btree (dataset_id,valid_from_version,id);

CREATE UNIQUE INDEX ux_dataset_user_grant ON analysis.dataset_grant USING btree (dataset_id,user_id) WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX ux_dataset_role_grant ON analysis.dataset_grant USING btree (dataset_id,role_id) WHERE role_id IS NOT NULL;

CREATE INDEX ix_profile_search ON analysis.profile USING btree (profile_type,updated_at DESC,id DESC) WHERE deleted=false;

CREATE INDEX ix_profile_identifier ON analysis.profile_identifier_index USING btree (field_code,hmac_key_version,normalization_version,lookup_hash);

CREATE UNIQUE INDEX ux_profile_active_link ON analysis.profile_link USING btree (profile_id,target_id) WHERE valid_until IS NULL;

CREATE INDEX ix_profile_target ON analysis.profile_link USING btree (target_id,profile_id) WHERE valid_until IS NULL;

CREATE INDEX ix_evidence_capture ON analysis.profile_evidence USING btree (capture_id,profile_id);

CREATE INDEX ix_library_category_name ON analysis.person_library USING btree (category_code,name,id) WHERE deleted=false;

CREATE UNIQUE INDEX ux_library_active_member ON analysis.person_library_member USING btree (library_id,profile_id) WHERE deleted=false;

CREATE INDEX ix_library_member_profile ON analysis.person_library_member USING btree (profile_id,library_id) WHERE deleted=false;

CREATE INDEX ix_face_results ON analysis.face_search_result USING btree (task_id,attempt_no,similarity DESC,capture_id);

CREATE INDEX ix_model_result_page ON analysis.model_result USING btree (execution_task_id,attempt_no,id);

CREATE INDEX ix_model_evidence ON analysis.model_result_evidence USING btree (result_id,id);

CREATE INDEX ix_watch_owner_list ON operation.watch_rule USING btree (owner_user_id,created_at DESC,id) WHERE deleted=false;

CREATE INDEX ix_watch_active_window ON operation.watch_rule USING btree (effective_from,effective_until,id) WHERE status='ACTIVE' AND deleted=false;

CREATE INDEX ix_watch_target_candidates ON operation.watch_rule_target USING btree (target_id,rule_id,rule_version);

CREATE INDEX ix_watch_site ON operation.watch_rule_site USING btree (site_id,rule_id,rule_version);

CREATE INDEX ix_alert_rule_time ON operation.alert_event USING btree (rule_id,occurred_at DESC,id DESC);

CREATE INDEX ix_alert_time ON operation.alert_event USING btree (occurred_at DESC,id DESC);

CREATE INDEX ix_notification_due ON operation.notification_plan USING btree (next_run_at,id) WHERE status='ACTIVE' AND deleted=false;

CREATE INDEX ix_notification_owner_list ON operation.notification_plan USING btree (owner_user_id,created_at DESC,id) WHERE deleted=false;

CREATE UNIQUE INDEX ux_recipient_address ON operation.notification_recipient USING btree (owner_user_id,channel_type,hmac_key_version,normalization_version,recipient_hash) WHERE channel_type IN ('SMS','EMAIL');

CREATE UNIQUE INDEX ux_recipient_platform ON operation.notification_recipient USING btree (owner_user_id,recipient_user_id) WHERE channel_type='PLATFORM';

CREATE INDEX ix_delivery_sms_time ON operation.delivery USING btree (sent_at DESC,id DESC) WHERE channel_type='SMS';

CREATE INDEX ix_delivery_recipient_time ON operation.delivery USING btree (recipient_id,sent_at DESC,id DESC);

CREATE INDEX ix_delivery_alert ON operation.delivery USING btree (alert_id,id) WHERE alert_id IS NOT NULL;

CREATE INDEX ix_delivery_plan ON operation.delivery USING btree (plan_id,id) WHERE plan_id IS NOT NULL;

CREATE INDEX ix_platform_message_user ON operation.platform_message USING btree (recipient_user_id,created_at DESC,id DESC);

CREATE INDEX ix_anomaly_series ON core.anomaly_event USING btree (category_code,detected_at DESC,id DESC);

CREATE INDEX ix_device_command_history ON operation.device_command USING btree (device_id,created_at DESC,id DESC);

CREATE INDEX ix_audit_actor_time ON audit.audit_event USING btree (actor_user_id,occurred_at DESC,id DESC);

CREATE INDEX ix_audit_time ON audit.audit_event USING btree (occurred_at DESC,id DESC);

CREATE INDEX ix_audit_resource ON audit.audit_event USING btree (resource_type,resource_id,occurred_at DESC,id DESC) WHERE resource_id IS NOT NULL;

CREATE INDEX ix_audit_trace ON audit.audit_event USING btree (trace_id);

CREATE INDEX ix_analysis_result_capture_sort ON analysis.analysis_result USING btree (analysis_job_id,attempt_no,capture_count DESC,id);

CREATE INDEX ix_profile_link_history ON analysis.profile_link USING btree (profile_id,valid_from DESC,id);

CREATE INDEX ix_profile_conflicts ON analysis.profile_conflict USING btree (profile_id,status,created_at,id);


CREATE FUNCTION core.guard_parameter_value() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE d core.device_parameter_definition%ROWTYPE; dv core.device%ROWTYPE; v numeric; j jsonb;
BEGIN
 SELECT * INTO d FROM core.device_parameter_definition WHERE id=NEW.definition_id;
 IF NEW.value_kind='DESIRED' THEN
  SELECT * INTO dv FROM core.device WHERE id=NEW.device_id;
  IF NOT d.enabled OR d.read_only OR dv.manufacturer IS DISTINCT FROM d.manufacturer
  OR dv.model IS DISTINCT FROM d.model OR dv.protocol_version IS DISTINCT FROM d.protocol_version THEN
   RAISE EXCEPTION 'desired parameter requires an approved writable definition for this device' USING ERRCODE='23514'; END IF;
 END IF;
 v=COALESCE(NEW.integer_value::numeric,NEW.number_value);
 IF (d.minimum IS NOT NULL AND v<d.minimum) OR (d.maximum IS NOT NULL AND v>d.maximum) THEN
  RAISE EXCEPTION 'device parameter outside approved bounds' USING ERRCODE='23514'; END IF;
 j=COALESCE(to_jsonb(NEW.text_value),to_jsonb(NEW.integer_value),to_jsonb(NEW.number_value),to_jsonb(NEW.boolean_value));
 IF d.allowed_values IS NOT NULL AND NOT (d.allowed_values @> jsonb_build_array(j)) THEN
  RAISE EXCEPTION 'device parameter outside approved finite values' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tr_parameter_value BEFORE INSERT OR UPDATE ON core.device_parameter_value FOR EACH ROW EXECUTE FUNCTION core.guard_parameter_value();



CREATE FUNCTION analysis.guard_model_parameter() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE vm uuid; dm uuid; lo numeric; hi numeric; v numeric;
BEGIN
 SELECT model_id INTO vm FROM analysis.model_parameter_version WHERE id=NEW.parameter_version_id;
 SELECT model_id,minimum,maximum INTO dm,lo,hi FROM analysis.model_parameter_definition WHERE id=NEW.definition_id;
 IF vm IS DISTINCT FROM dm THEN RAISE EXCEPTION 'parameter definition belongs to another model' USING ERRCODE='23514'; END IF;
 v=COALESCE(NEW.integer_value::numeric,NEW.number_value);
 IF (lo IS NOT NULL AND v<lo) OR (hi IS NOT NULL AND v>hi) THEN RAISE EXCEPTION 'parameter outside approved bounds' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tr_model_parameter BEFORE INSERT OR UPDATE ON analysis.model_parameter_value FOR EACH ROW EXECUTE FUNCTION analysis.guard_model_parameter();
CREATE FUNCTION operation.guard_delivery_channel() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rc text; cc text; allowed text[];
BEGIN
 SELECT channel_type INTO rc FROM operation.notification_recipient WHERE id=NEW.recipient_id;
 SELECT channels INTO allowed FROM operation.notification_template WHERE template_code=NEW.template_code AND template_version=NEW.template_version;
 IF rc IS DISTINCT FROM NEW.channel_type OR NOT (NEW.channel_type=ANY(allowed)) THEN RAISE EXCEPTION 'recipient/template channel mismatch' USING ERRCODE='23514'; END IF;
 IF NEW.channel_id IS NOT NULL THEN
 SELECT channel_type INTO cc FROM operation.notification_channel WHERE id=NEW.channel_id;
 IF cc IS DISTINCT FROM NEW.channel_type THEN RAISE EXCEPTION 'adapter channel mismatch' USING ERRCODE='23514'; END IF;
 END IF;RETURN NEW;
END $$;
CREATE TRIGGER tr_delivery_channel BEFORE INSERT OR UPDATE OF recipient_id,channel_type,channel_id,template_code,template_version ON operation.delivery FOR EACH ROW EXECUTE FUNCTION operation.guard_delivery_channel();
CREATE FUNCTION capture.guard_capture_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.source_event_id,NEW.batch_id,NEW.capture_time,NEW.primary_target_id,NEW.target_type,NEW.data_type,NEW.device_id,NEW.site_id,NEW.region_code_at_capture,NEW.location)
 IS DISTINCT FROM (OLD.source_event_id,OLD.batch_id,OLD.capture_time,OLD.primary_target_id,OLD.target_type,OLD.data_type,OLD.device_id,OLD.site_id,OLD.region_code_at_capture,OLD.location)
 THEN RAISE EXCEPTION 'capture identity/ownership/time immutable; append a revision instead' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tr_capture_identity BEFORE UPDATE ON capture.capture_event FOR EACH ROW EXECUTE FUNCTION capture.guard_capture_identity();



CREATE FUNCTION audit.reject_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'append-only record cannot be updated or deleted' USING ERRCODE='23514'; END $$;
CREATE TRIGGER tr_audit_append_only BEFORE UPDATE OR DELETE ON audit.audit_event FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER tr_review_append_only BEFORE UPDATE OR DELETE ON audit.sensitive_action_review FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();

CREATE FUNCTION analysis.guard_task_transition() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status=OLD.status THEN RETURN NEW; END IF;
 IF (OLD.status='PENDING' AND NEW.status IN ('QUEUED','FAILED','CANCELLED'))
 OR (OLD.status='QUEUED' AND NEW.status IN ('RUNNING','FAILED','CANCELLED'))
 OR (OLD.status='RUNNING' AND NEW.status IN ('SUCCESS','FAILED','CANCELLED','QUEUED'))
 OR (OLD.status='FAILED' AND NEW.status='QUEUED')
 THEN RETURN NEW; END IF;
 RAISE EXCEPTION 'invalid task transition: % -> %',OLD.status,NEW.status USING ERRCODE='23514';
END $$;
CREATE TRIGGER tr_task_transition BEFORE UPDATE OF status ON analysis.async_task FOR EACH ROW EXECUTE FUNCTION analysis.guard_task_transition();

CREATE FUNCTION core.guard_device_group_member() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE group_site uuid; device_site uuid;
BEGIN
 SELECT site_id INTO group_site FROM core.device_group WHERE id=NEW.group_id AND NOT deleted FOR SHARE;
 SELECT site_id INTO device_site FROM core.device WHERE id=NEW.device_id AND NOT deleted FOR SHARE;
 IF group_site IS NULL OR device_site IS NULL OR group_site<>device_site THEN
  RAISE EXCEPTION 'device and group must belong to the same active site' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER tr_group_site BEFORE INSERT OR UPDATE ON core.device_group_member FOR EACH ROW EXECUTE FUNCTION core.guard_device_group_member();

CREATE FUNCTION core.guard_device_site_move() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.site_id<>OLD.site_id AND EXISTS(
  SELECT 1 FROM core.device_group_member m JOIN core.device_group g ON g.id=m.group_id
  WHERE m.device_id=OLD.id AND g.site_id<>NEW.site_id
 ) THEN RAISE EXCEPTION 'remove or migrate incompatible group memberships before moving device' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tr_device_site_move BEFORE UPDATE OF site_id ON core.device FOR EACH ROW EXECUTE FUNCTION core.guard_device_site_move();

CREATE FUNCTION capture.guard_capture_payload() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actual text; expected text := TG_ARGV[0]; actual_target text;
BEGIN
 SELECT data_type INTO actual FROM capture.capture_event WHERE id=NEW.capture_id;
 IF actual IS DISTINCT FROM expected THEN RAISE EXCEPTION 'capture payload type mismatch' USING ERRCODE='23514'; END IF;
 IF expected='FENCE' THEN
   IF NEW.imsi_target_id IS NOT NULL THEN SELECT target_type INTO actual_target FROM capture.target WHERE id=NEW.imsi_target_id; IF actual_target IS DISTINCT FROM 'IMSI' THEN RAISE EXCEPTION 'IMSI reference type mismatch' USING ERRCODE='23514'; END IF; END IF;
   IF NEW.imei_target_id IS NOT NULL THEN SELECT target_type INTO actual_target FROM capture.target WHERE id=NEW.imei_target_id; IF actual_target IS DISTINCT FROM 'IMEI' THEN RAISE EXCEPTION 'IMEI reference type mismatch' USING ERRCODE='23514'; END IF; END IF;
 ELSIF expected='MAC' THEN
   IF NEW.mac_target_id IS NOT NULL THEN
   SELECT target_type INTO actual_target FROM capture.target WHERE id=NEW.mac_target_id;
   IF actual_target IS DISTINCT FROM 'MAC' THEN RAISE EXCEPTION 'MAC reference type mismatch' USING ERRCODE='23514'; END IF;
   END IF;
 ELSIF expected='PLATE' THEN
   IF NEW.plate_target_id IS NOT NULL THEN
   SELECT target_type INTO actual_target FROM capture.target WHERE id=NEW.plate_target_id;
   IF actual_target IS DISTINCT FROM 'PLATE' THEN RAISE EXCEPTION 'plate reference type mismatch' USING ERRCODE='23514'; END IF;
   END IF;
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER tr_fence_type BEFORE INSERT OR UPDATE ON capture.fence_record FOR EACH ROW EXECUTE FUNCTION capture.guard_capture_payload('FENCE');
CREATE TRIGGER tr_mac_type BEFORE INSERT OR UPDATE ON capture.mac_record FOR EACH ROW EXECUTE FUNCTION capture.guard_capture_payload('MAC');
CREATE TRIGGER tr_plate_type BEFORE INSERT OR UPDATE ON capture.plate_record FOR EACH ROW EXECUTE FUNCTION capture.guard_capture_payload('PLATE');
CREATE TRIGGER tr_face_type BEFORE INSERT OR UPDATE ON capture.face_record FOR EACH ROW EXECUTE FUNCTION capture.guard_capture_payload('FACE');

CREATE FUNCTION capture.guard_batch_completion() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a bigint; r bigint; d bigint;
BEGIN
 IF NEW.status<>'SUCCESS' OR OLD.status='SUCCESS' THEN RETURN NEW; END IF;
 SELECT count(*) FILTER(WHERE processing_status='ACCEPTED'),count(*) FILTER(WHERE processing_status='REJECTED'),count(*) FILTER(WHERE processing_status='DUPLICATE')
 INTO a,r,d FROM capture.ingestion_item WHERE batch_id=NEW.id;
 IF (a,r,d) IS DISTINCT FROM (NEW.accepted_count,NEW.rejected_count,NEW.duplicate_count) THEN
  RAISE EXCEPTION 'batch totals do not match disjoint processing results' USING ERRCODE='23514';
 END IF;
 IF EXISTS (
  SELECT 1 FROM capture.capture_event e
  LEFT JOIN capture.fence_record f ON f.capture_id=e.id
  LEFT JOIN capture.mac_record m ON m.capture_id=e.id
  LEFT JOIN capture.plate_record p ON p.capture_id=e.id
  LEFT JOIN capture.face_record x ON x.capture_id=e.id
  WHERE e.batch_id=NEW.id AND e.status='VALID' AND (
   (e.data_type='FENCE' AND f.capture_id IS NULL) OR (e.data_type='MAC' AND m.capture_id IS NULL)
   OR (e.data_type='PLATE' AND p.capture_id IS NULL) OR (e.data_type='FACE' AND x.capture_id IS NULL))
 ) THEN RAISE EXCEPTION 'valid captures require their typed payload before publication' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tr_batch_completion BEFORE UPDATE OF status ON capture.ingestion_batch FOR EACH ROW EXECUTE FUNCTION capture.guard_batch_completion();

CREATE FUNCTION analysis.guard_person_member() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE pt text; lp uuid;
BEGIN
 SELECT profile_type INTO pt FROM analysis.profile WHERE id=NEW.profile_id;
 SELECT profile_id INTO lp FROM analysis.profile_link WHERE id=NEW.primary_target_link_id;
 IF pt IS DISTINCT FROM 'PERSON' OR lp IS DISTINCT FROM NEW.profile_id THEN
 RAISE EXCEPTION 'person library requires a person profile and its own target link' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tr_person_member BEFORE INSERT OR UPDATE OF profile_id,primary_target_link_id ON analysis.person_library_member FOR EACH ROW EXECUTE FUNCTION analysis.guard_person_member();

CREATE FUNCTION auth.guard_soft_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.deleted AND NOT OLD.deleted THEN
  IF TG_TABLE_NAME='role' THEN
   IF EXISTS(SELECT 1 FROM auth.user_role WHERE role_id=OLD.id) THEN RAISE EXCEPTION 'role is assigned to users' USING ERRCODE='23503'; END IF;
  ELSE
   IF NEW.status<>'DISABLED' OR EXISTS(SELECT 1 FROM analysis.async_task WHERE owner_user_id=OLD.id)
     OR EXISTS(SELECT 1 FROM audit.audit_event WHERE actor_user_id=OLD.id) THEN
    RAISE EXCEPTION 'disable user first; task/audit references protect deletion' USING ERRCODE='23503';
   END IF;
  END IF;
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER tr_role_delete_guard BEFORE UPDATE OF deleted ON auth.role FOR EACH ROW EXECUTE FUNCTION auth.guard_soft_delete();
CREATE TRIGGER tr_user_delete_guard BEFORE UPDATE OF deleted ON auth.app_user FOR EACH ROW EXECUTE FUNCTION auth.guard_soft_delete();


REVOKE UPDATE,DELETE,TRUNCATE ON audit.audit_event,audit.sensitive_action_review FROM PUBLIC;

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('dashboard:read','dashboard:read','M04','OpenAPI PermissionCode dashboard:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('capture:read','capture:read','M05','OpenAPI PermissionCode capture:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('capture:export','capture:export','M01','OpenAPI PermissionCode capture:export','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('capture:decrypt','capture:decrypt','M06','OpenAPI PermissionCode capture:decrypt','HIGH',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('face:search','face:search','M05','OpenAPI PermissionCode face:search','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('face:download','face:download','M01','OpenAPI PermissionCode face:download','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('context:use','context:use','M06','OpenAPI PermissionCode context:use','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('map:read','map:read','M07','OpenAPI PermissionCode map:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('analysis:read','analysis:read','M08','OpenAPI PermissionCode analysis:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('analysis:create','analysis:create','M08','OpenAPI PermissionCode analysis:create','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('analysis:update','analysis:update','M08','OpenAPI PermissionCode analysis:update','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('analysis:delete','analysis:delete','M08','OpenAPI PermissionCode analysis:delete','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('dataset:read','dataset:read','M09','OpenAPI PermissionCode dataset:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('dataset:write','dataset:write','M09','OpenAPI PermissionCode dataset:write','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('dataset:export','dataset:export','M09','OpenAPI PermissionCode dataset:export','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('watch:read','watch:read','M10','OpenAPI PermissionCode watch:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('watch:write','watch:write','M10','OpenAPI PermissionCode watch:write','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('watch:revoke','watch:revoke','M10','OpenAPI PermissionCode watch:revoke','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('watch:delete','watch:delete','M10','OpenAPI PermissionCode watch:delete','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('notification:read','notification:read','M10','OpenAPI PermissionCode notification:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('notification:send','notification:send','M10','OpenAPI PermissionCode notification:send','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('notification:cancel','notification:cancel','M10','OpenAPI PermissionCode notification:cancel','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('device:read','device:read','M12','OpenAPI PermissionCode device:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('device:write','device:write','M12','OpenAPI PermissionCode device:write','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('device:command','device:command','M12','OpenAPI PermissionCode device:command','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('device:reset','device:reset','M01','OpenAPI PermissionCode device:reset','HIGH',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('report:read','report:read','M12','OpenAPI PermissionCode report:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('report:create','report:create','M12','OpenAPI PermissionCode report:create','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('user:read','user:read','M14','OpenAPI PermissionCode user:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('user:write','user:write','M14','OpenAPI PermissionCode user:write','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('user:disable','user:disable','M14','OpenAPI PermissionCode user:disable','HIGH',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('role:read','role:read','M14','OpenAPI PermissionCode role:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('role:grant','role:grant','M01','OpenAPI PermissionCode role:grant','HIGH',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('audit:read','audit:read','M14','OpenAPI PermissionCode audit:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('audit:export','audit:export','M14','OpenAPI PermissionCode audit:export','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('delivery:read','delivery:read','M10','OpenAPI PermissionCode delivery:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('delivery:export','delivery:export','M14','OpenAPI PermissionCode delivery:export','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('ingestion:read','ingestion:read','M03','OpenAPI PermissionCode ingestion:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('ingestion:write','ingestion:write','M03','OpenAPI PermissionCode ingestion:write','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('ingestion:replay','ingestion:replay','M03','OpenAPI PermissionCode ingestion:replay','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('file:download','file:download','M01','OpenAPI PermissionCode file:download','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('task:read','task:read','M01','OpenAPI PermissionCode task:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('task:manage','task:manage','M01','OpenAPI PermissionCode task:manage','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('person-library:read','person-library:read','M11','OpenAPI PermissionCode person-library:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('person-library:write','person-library:write','M11','OpenAPI PermissionCode person-library:write','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('person-library:export','person-library:export','M11','OpenAPI PermissionCode person-library:export','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('profile:read','profile:read','M11','OpenAPI PermissionCode profile:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('profile:correct','profile:correct','M11','OpenAPI PermissionCode profile:correct','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('profile:merge','profile:merge','M11','OpenAPI PermissionCode profile:merge','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('profile:export','profile:export','M11','OpenAPI PermissionCode profile:export','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('model:read','model:read','M11','OpenAPI PermissionCode model:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('model:write','model:write','M11','OpenAPI PermissionCode model:write','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('model:execute','model:execute','M11','OpenAPI PermissionCode model:execute','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('site:read','site:read','M12','OpenAPI PermissionCode site:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('site:write','site:write','M12','OpenAPI PermissionCode site:write','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('device-group:read','device-group:read','M12','OpenAPI PermissionCode device-group:read','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('device-group:write','device-group:write','M12','OpenAPI PermissionCode device-group:write','NORMAL',true);

INSERT INTO auth.permission(code,name,module_code,description,risk_level,assignable) VALUES('report:delete','report:delete','M12','OpenAPI PermissionCode report:delete','NORMAL',true);

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('FACE_SEARCH',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1');

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('DECRYPTION',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1');

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('COMPANION_ANALYSIS',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1');

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('COLLISION_ANALYSIS',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1');

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('REGION_STATISTICS',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1');

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('PROFILE_ASSOCIATION',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1');

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('RESIDENCE_MODEL',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1');

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('WATCH_ACTIVATION',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1');

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('NOTIFICATION_SEND',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1');

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('DEVICE_COMMAND',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1');

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('REPORT_GENERATION',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1');

INSERT INTO core.feature_config(feature_code,enabled,pending_reason,policy_version) VALUES('RAW_INGESTION',false,'实现/真实资源未就绪；依PD-2026-10-18.v1暂定规则开发','PD-2026-10-18.v1');


-- PD-2026-10-18.v1：暂定存储设计，尚待甲方确认；不含实际密钥/审批人员。
CREATE TABLE operation.action_approval (
  id uuid PRIMARY KEY,
  requester_user_id uuid NOT NULL REFERENCES auth.app_user(id),
  executor_user_id uuid NOT NULL REFERENCES auth.app_user(id),
  operation_id varchar(200) NOT NULL,
  policy_version varchar(200) NOT NULL,
  payload_digest char(64) NOT NULL CHECK (payload_digest ~ '^[a-f0-9]{64}$'),
  frozen_payload_ciphertext bytea NOT NULL CHECK (octet_length(frozen_payload_ciphertext)>0),
  encryption_key_id varchar(200) NOT NULL,
  summary varchar(2000) NOT NULL,
  status varchar(200) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED','CONSUMED')),
  required_approvals smallint NOT NULL CHECK (required_approvals IN (1,2)),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  execution_grant_expires_at timestamptz,
  consumed_at timestamptz,
  version bigint NOT NULL DEFAULT 1 CHECK (version>=1),
  CHECK (expires_at>created_at),
  CHECK (execution_grant_expires_at IS NULL OR execution_grant_expires_at<=expires_at),
  CHECK (status NOT IN ('APPROVED','CONSUMED') OR execution_grant_expires_at IS NOT NULL),
  CHECK ((status='CONSUMED')=(consumed_at IS NOT NULL))
);
CREATE INDEX ix_action_approval_requester ON operation.action_approval(requester_user_id,created_at,id);
CREATE INDEX ix_action_approval_pending ON operation.action_approval(status,expires_at,id);
COMMENT ON TABLE operation.action_approval IS 'PD-2026-10-18.v1暂定审批聚合；冻结快照认证加密，摘要不代替鉴权；APPROVED转换仅受控服务事务可做';
COMMENT ON COLUMN operation.action_approval.frozen_payload_ciphertext IS '加密封装含随机nonce/tag；不存密码、密钥或标识明文；部署secret注入key';

CREATE TABLE audit.action_approval_decision (
  id uuid PRIMARY KEY,
  approval_id uuid NOT NULL REFERENCES operation.action_approval(id),
  reviewer_user_id uuid NOT NULL REFERENCES auth.app_user(id),
  decision varchar(200) NOT NULL CHECK (decision IN ('APPROVE','REJECT')),
  reason varchar(1000) NOT NULL CHECK (char_length(reason)>=10),
  decided_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(approval_id,reviewer_user_id)
);
CREATE TRIGGER tr_action_approval_decision_append_only
  BEFORE UPDATE OR DELETE ON audit.action_approval_decision
  FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
REVOKE UPDATE,DELETE,TRUNCATE ON audit.action_approval_decision FROM PUBLIC;
COMMENT ON TABLE audit.action_approval_decision IS 'PD-2026-10-18.v1独立复核事实，仅追加；服务端排除申请人/执行人自批并重校权限，currentPassword不落库';

ALTER TABLE operation.decryption_request ADD COLUMN approval_id uuid
  UNIQUE REFERENCES operation.action_approval(id);
ALTER TABLE operation.decryption_result_grant ALTER COLUMN result_file_id DROP NOT NULL;
ALTER TABLE operation.decryption_result_grant ADD COLUMN approval_id uuid REFERENCES operation.action_approval(id);
ALTER TABLE operation.decryption_result_grant ADD COLUMN consumed_at timestamptz;
ALTER TABLE operation.decryption_result_grant ADD CONSTRAINT ck_decrypt_controlled_no_file
  CHECK (approval_id IS NULL OR result_file_id IS NULL);
CREATE UNIQUE INDEX uq_decrypt_request_approval ON operation.decryption_result_grant(request_id,authorized_user_id,approval_id);
COMMENT ON COLUMN operation.decryption_request.approval_id IS '同事务创建readDecryptionResult审批，执行人为recipient';
COMMENT ON COLUMN operation.decryption_result_grant.approval_id IS '新受控模式必须非空且绑定request/recipient，result_file_id必须为空；服务事务校验，不保存持久明文文件';
COMMENT ON COLUMN operation.decryption_result_grant.consumed_at IS '一次查看先同事务消费，HTTP断开不重放明文';

COMMIT;
