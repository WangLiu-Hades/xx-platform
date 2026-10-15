# OpenAPI 契约速读指南（后端新人版）

面向：从零加入、用 Spring Boot 实现 `apps/api` 的后端开发（对应仓库角色 DEV-01）。
唯一机器契约：[openapi.yaml](<../contracts/api/openapi.yaml>)（OpenAPI 3.0.3，版本 `1.0.0-dev.2`，193 个操作、581 个 Schema）。
本指南是「人话翻译 + 导航 + 施工顺序」，不是契约本身。**有冲突时以 openapi.yaml 和原始 PDF 需求为准。**

本指南的所有数字、路径、operationId、权限都是从 openapi.yaml 用脚本解析出来的（解析脚本 `local-only/agent/extract-openapi.cjs`），不是手抄，但仍请以契约原文为准。

---

## 1. 五分钟看懂这个项目

### 1.1 业务是什么

多维数据分析平台：把**场所 / 设备**采集到的多类目标数据（围栏 IMSI/IMEI、MAC、车牌、人脸）统一接入，提供**检索、地图与轨迹、分析计算、数据集与集合运算、人员/车辆档案、布控告警、通知与报表、用户角色与审计**。前端是已经做好的 Vue 原型，后端要按契约把接口真正实现出来。

### 1.2 技术分工（决定你写什么、不写什么）

| 角色 | 负责 |
| --- | --- |
| **DEV-01（你）** | `apps/api`：全部公开 HTTP API、鉴权/事务、任务底座、Context、文件、导出、通知与设备命令编排 |
| DEV-03 | `workers/data`：数据接入 ETL、检索、分析计算、模型执行；你调用它，不重复实现 |
| DEV-02 | 前端 Vue；后端契约不变时前端不跟着改 |
| DEV-04 | 测试/部署 |

技术栈见 [ADR-002 后端选型](<system-design/adr/ADR-002-backend.md>)：**Spring Boot 承担唯一业务 API，Python Worker 只做接入/分析/导出/报表**，不要在 Java 里重写用户/权限/系统 CRUD，也不要在 Python 里复制一份 RBAC。

### 1.3 最重要的一句话

> 契约里 **193 个操作中有 65 个自身标记为 `pending`（设计未签认）**，还有一批项目级未决项 P-001～P-021。
> **这些接口不要自己猜业务规则，也不要用 Mock 阈值上线**：未签认能力必须在 `client-config` 中不可用并返回 `422 REQUIREMENT_PENDING`。
> 先做已签认的骨架（会话、任务、文件、CRUD），把 pending 项留到规则确认后再实现。

### 1.4 交付标准（不是"接口能返回 JSON"）

联调通过标准（来自 [前端接口交接](<project-governance/FRONTEND_API_HANDOFF.md>)）：
浏览器真实 HTTP 请求 → 服务端日志 traceId → **真实数据库写入/查询** → Worker 结果 → 刷新页面读回；
同时必须验证：无权、冲突、失败、取消、超时、文件过期、范围收缩、服务停止。只把 OpenAPI 示例拦下来不算完成。

---

## 2. 我该读哪些文件（按顺序）

| 顺序 | 文件 | 读它解决什么 | 建议 |
| --- | --- | --- | --- |
| 1 | 本指南 | 建立全局印象 | 通读 |
| 2 | [api-conventions.md](<../contracts/api/api-conventions.md>) | 响应封装、分页、时间、会话、幂等、状态机 | **精读，9 节全读** |
| 3 | [error-codes.md](<../contracts/api/error-codes.md>) | 34 个稳定错误码 → HTTP 状态 → 客户端处理 | 精读，做成 Java enum |
| 4 | [FRONTEND_INTEGRATION.md](<../contracts/api/FRONTEND_INTEGRATION.md>) | 前端怎么发请求、代理、请求头、联调配置 | 联调前必读 |
| 5 | [FRONTEND_API_HANDOFF.md](<project-governance/FRONTEND_API_HANDOFF.md>) | 页面动作 ↔ operationId ↔ 谁负责 | 排期用 |
| 6 | [OPENAPI_PENDING.md](<../contracts/api/OPENAPI_PENDING.md>) | P-001～P-021 未决清单 | 动手前先看哪些不能做 |
| 7 | [schema.sql](<../contracts/database/schema.sql>) / [data-dictionary.md](<../contracts/database/data-dictionary.md>) | 表结构、字段、枚举 | 建库时精读 |
| 8 | [api-storage-map.md](<../contracts/database/api-storage-map.md>) | 接口 ↔ 存储字段投影 | 写 DAO 时查 |
| 9 | [04_API与接口契约设计.md](<system-design/04_API与接口契约设计.md>)、[ADR-005 异步任务](<system-design/adr/ADR-005-async-task.md>)、[ADR-007 认证](<system-design/adr/ADR-007-authentication.md>) | 设计理由 | 遇到"为什么要这样"时查 |

**不要去读的东西**：`openapi.yaml` 有 37853 行、1.36 MB，逐行读没有意义。它其实是"每个接口都自带成功/失败示例"的机器文件，请**按 operationId 用编辑器搜索定位**（见 §10.1 的检索技巧）。

---

## 3. 数据层高频约定（先记住这 8 条）

1. **字段名 `camelCase`**，所有实体 ID 都是 **UUID 字符串**。
2. **Base URL 是 `/api/v1`**（契约里 `servers.url: /api/v1`），写接口时路径不要重复写前缀。
3. **时间**：RFC 3339 带偏移；**UTC 存储/返回 Z**；前端按 `Asia/Shanghai` 展示。不接受无时区时间、不接受秒/毫秒时间戳。`TimeRange` 是 **`[from,to)` 左闭右开**，`from` 必须早于 `to`。
4. **GeoJSON**：WGS84 / EPSG:4326，`coordinates=[经度,纬度]`；经纬度同时缺失时两者都为 `null`，**绝对不能画 (0,0)**。
5. **可选字段省略表示未提供，只有 `nullable: true` 才允许显式 `null`**。这是全契约的统一语义，别用 null 表示"没传"。
6. **不要混淆三组 ID**：`targetId`（目标代理 ID，不是 IMSI/MAC 明文）、`fileAssetId`（文件资产，不是目标）、以及 `analysisTaskId`（业务分析任务）与执行用 `taskId`（异步任务底座）——它们是不同的东西。
7. **权限在后端**："前端按钮隐藏不是后端授权"。每个列表/详情/导出/文件访问都要重新校验数据范围。
8. **越权按 404 隐藏存在性**：单对象不可见返回 `404 DATA_NOT_FOUND`，而不是 403。403 只用于"操作整体无权"或范围被收缩。

---

## 4. 统一响应壳（所有 JSON 接口都长这样）

**成功**（`code` 固定字符串 `"0"`）：

```json
{ "code": "0", "message": "success", "data": { /* 具体业务 Schema */ }, "traceId": "..." }
```

**失败**（`data` 固定为 `null`，`details` 强类型数组）：

```json
{ "code": "VALIDATION_ERROR", "message": "字段非法", "data": null, "traceId": "...",
  "details": [ { "field": "captureTime", "reason": "格式非法" } ] }
```

要点：

- 每个 operation 的 `200` 直接引用一个 `XxxResponse`（形如 `GetHealthResponse`、`ListTasksResponse`），**没有额外嵌套**，`data` 里才是业务对象。
- **按 `code` 判断，禁止按中文 `message` 判断**（message 可本地化，code 稳定不变）。
- 所有响应带 `X-Trace-Id` 响应头，值与 JSON 里 `traceId` 相同。
- **文件流是例外**：下载不套 JSON，只用 `X-Trace-Id` 头；文件出错时改回 JSON 错误体。
- `ErrorResponse` 的 required 是 `code/message/traceId/details/data` 五个，一个都不能省。
- 契约里 400/401/403/500 出现在**全部 193 个操作**上（组件化复用），404 出现 153 次、409 出现 102 次、422 出现 117 次、202 出现 36 次。**别以为 400 只是某些接口才有——每个接口都要做统一异常兜底。**

### 4.1 错误码 → HTTP（后端按此实现，前端按 code 处理）

| code | HTTP | 含义 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 字段/格式/范围/跨字段非法，`details.field` 给 camelCase 路径 |
| `AUTH_REQUIRED` / `AUTH_INVALID_CREDENTIALS` / `AUTH_SESSION_EXPIRED` | 401 | 未登录 / 凭据错 / 会话过期 |
| `AUTH_CSRF_INVALID` | 403 | 同步器 token 或可信 Origin 不匹配 |
| `PERMISSION_DENIED` | 403 | 操作无权 |
| `PERMISSION_DATA_SCOPE` | 403 | 范围被收缩 |
| `DATA_INVALID_FILTER` | 422 | 筛选条件非法 |
| `DATA_QUERY_TOO_BROAD` | 422 | 超出查询预算（超出 `maxOnlineQuerySeconds`/`maxOnlineWindowDays`） |
| `DATA_NOT_FOUND` | 404 | 不存在/已删/**不可见**（隐藏存在性） |
| `RESOURCE_CONFLICT` | 409 | 状态/引用/唯一性冲突，禁止盲重试 |
| `RESOURCE_VERSION_CONFLICT` | 409 | ETag/version 不匹配，刷新后重试 |
| `TASK_CONFLICT` / `TASK_NOT_CANCELLABLE` | 409 | 任务状态冲突 / 终态不可撤销 |
| `TASK_FAILED` | 422 | 任务执行失败 |
| `ANALYSIS_INVALID_RULE` | 422 | 分析规则非法 |
| `DEVICE_OFFLINE` | 422 | 设备离线 |
| `ALERT_RULE_INACTIVE` | 422 | 告警规则未生效 |
| `FILE_INVALID` | 422 | 文件非法 |
| `FILE_EXPIRED` | 410 | 资产/结果已清理 |
| `RATE_LIMITED` | 429 | 限流（带 `Retry-After` 秒数） |
| `SYSTEM_INTERNAL` | 500 | 内部错误 |
| `SYSTEM_UNAVAILABLE` | 503 | 依赖不可用（带 `Retry-After`） |
| `CONTEXT_EXPIRED` | 410 | Context 过期，重建 |
| `TASK_RESULT_NOT_READY` | 409 | 结果未就绪，轮询到 SUCCESS |
| `REQUIREMENT_PENDING` | 422 | **未签认规则，页面显示不可用原因** |
| `NOTIFICATION_INVALID_RECIPIENT` | 422 | 收件人或计划 recipientId 非法 |
| `MODEL_PARAMETER_INVALID` | 422 | 参数编码/类型/范围非法 |
| `DATASET_ELEMENT_TYPE_MISMATCH` | 422 | CAPTURE/TARGET 不兼容，**不隐式转换** |
| `FILE_TOO_LARGE` | 413 | 超 `uploadPolicy.maxBytes` |
| `FILE_MEDIA_UNSUPPORTED` | 415 | 实际文件类型不允许 |
| `DATA_CURSOR_INVALID` | 400 | 游标失效/被篡改/条件不匹配 |
| `IDEMPOTENCY_CONFLICT` | 409 | 同幂等键不同 payload |
| `MAP_GEOMETRY_INVALID` | 422 | 几何拓扑/范围非法 |

---

## 5. 分页只有两套（不要发明第三套）

| 数据 | 请求 | 响应字段 |
| --- | --- | --- |
| 管理资源 / 任务 / 配置 | `page≥1`，`pageSize` 默认 20 最大 100 | `items` / `page` / `pageSize` / `total` |
| 采集 / 轨迹 / 告警 / 日志 / 大结果 / 错误行 | `cursor`（可省略），`pageSize` 默认 20 最大 100 | `items` / `nextCursor` / `pageSize` |
| 平台消息 | 同 cursor | 再加 `totalCount` / `asOf` |

- **页码是 Mock**：原型上的页码不是真的，历史翻页靠 cursor；`nextCursor=null` 表示结束，**不要伪造 total**。
- **cursor 是签名串**，绑定账号 + 范围版本 + 查询快照 + `sortBy/sortOrder/pageSize`；服务端**只签名和校验，不解析**。改条件必须清空 cursor；篡改/失效/范围变化 → `400 DATA_CURSOR_INVALID`。
- 空结果是 `items: []`（管理分页 `total: 0`），超末页返回空页 + 真实 total。
- 查询语法：**同组多值 OR，不同条件 AND**；数组 query 用重复键 `siteIds=x&siteIds=y`；**精确敏感标识只放 body**，普通 keyword 大小写归一后包含匹配。
- 每个 operation 的可用 `sortBy` 在 Schema 里有枚举，别自创字段；同值要追加"唯一资源 ID"作次键。轨迹固定 `captureTime ASC / captureId ASC`，不接受 DESC。

---

## 6. 同步 201 与异步 202（最容易做错的一处）

- **同步创建资源** → `201`，返回该领域资源本身（如 `CreateSiteResponse` → `SiteData`）。
- **异步执行** → `202` + `taskId`，**领域资源 ID 与执行 taskId 是两个不同的 ID**。
- 只返回三个必填字段：`taskId` / `status` / `createdAt`（`resourceId`、`resourceType` 可选）。

任务状态机（`TaskStatus`）：

```
PENDING → QUEUED → RUNNING → SUCCESS | FAILED | CANCELLED
```

- `202` 收到**不等于**副作用已撤回：取消要先写 `cancelRequestedAt`，等 Worker 确认后才 `CANCELLED`；`SUCCESS/FAILED` 不可撤回 → `409 TASK_NOT_CANCELLABLE`。
- 轮询 `GET /tasks/{taskId}`：未执行 `progress=0`，`SUCCESS=100`；失败带 `errorCode`/`errorMessage`，**参数快照和算法版本必须保留**。
- 重试 `POST /tasks/{taskId}/retry`：**复用同一个 taskId**，`attemptNo+1`；第三方状态 UNKNOWN 时先对账，不要盲重发。
- 结果未就绪 → `409 TASK_RESULT_NOT_READY`；已清理 → `410 FILE_EXPIRED`；成功但空结果 → `items: []`。

`TaskResultReference` 各类型取结果的位置不同（**照抄，不要统一**）：

| 任务类型 | 取结果方式 |
| --- | --- |
| `ANALYSIS` | `resultId = analysisTaskId` |
| `SET_OPERATION` | `setTaskId`，再由领域详情取 `resultDatasetId` |
| `FACE_SEARCH` / `DEVICE_COMMAND` / `MODEL_RUN` | `taskId`（`MODEL_RUN` 必须返回 `modelId`） |
| `REPORT` | `reportId` |
| `INGESTION` | `batchId` |
| `NOTIFICATION` | `notificationTaskId` |
| `EXPORT` | **必须有 `fileAssetId`** |

---

## 7. 枚举字典（写 Java enum 直接抄）

| 枚举 | 值 |
| --- | --- |
| `TaskStatus` | PENDING, QUEUED, RUNNING, SUCCESS, FAILED, CANCELLED（只用于**一次执行**，不用于周期计划） |
| `DataType` | FENCE, MAC, PLATE, FACE |
| `TargetType` | IMSI, IMEI, MAC, PLATE, FACE |
| `SortOrder` | ASC, DESC |
| `DeviceStatus` | ONLINE, OFFLINE, UNKNOWN, FAULT |
| `FileStatus` | PROCESSING, ACTIVE, QUARANTINED, DELETED |
| `CaptureStatus` | VALID, QUARANTINED, RETRACTED |
| `IngestionStatus` | PENDING, RUNNING, SUCCESS, FAILED, CANCELLED |
| `UserStatus` | ACTIVE, DISABLED |
| `ProfileType` | PERSON, VEHICLE |
| `WatchStatus` | DRAFT, ACTIVE, FINISHED, REVOKED |
| `NotificationPlanStatus` | ACTIVE, CANCELLED, COMPLETED |
| `DeliveryStatus` | PENDING, SENT, ACKNOWLEDGED, FAILED, UNKNOWN |
| `NotificationChannel` | SMS, EMAIL, PLATFORM |
| `DatasetKind` | SINGLE, MIXED |
| `DatasetElementType` | CAPTURE, TARGET |
| `SetOperator` | INTERSECTION, UNION, DIFFERENCE, MIXED |
| `MetricStatus` | READY, STALE, UNAVAILABLE |
| `RefreshPolicy` | POLL, ON_DEMAND, SNAPSHOT |
| `RegionAnalysisDataType` | FENCE, PLATE, MAC（**注意：区域统计只有这三类，没有 MAC 之外的第四类，也没有 FACE**） |
| `PermissionCode`（58 个） | `dashboard:read`, `capture:read/export/decrypt`, `face:search/download`, `context:use`, `map:read`, `analysis:read/create/update/delete`, `dataset:read/write/export`, `watch:read/write/revoke/delete`, `notification:read/send/cancel`, `device:read/write/command/reset`, `report:read/create/delete`, `user:read/write/disable`, `role:read/grant`, `audit:read/export`, `delivery:read/export`, `ingestion:read/write/replay`, `file:download`, `task:read/manage`, `person-library:read/write/export`, `profile:read/correct/merge/export`, `model:read/write/execute`, `site:read/write`, `device-group:read/write` |
| `CaptureExportColumn`（16 列白名单） | captureId, dataType, captureTime, ingestedAt, deviceId, siteId, displayTarget, source, status, longitude, latitude, imsiMasked, imeiMasked, macMasked, plateMasked, faceImageAssetId |

其他高频字段级枚举：

- `FileAsset.category` / `UploadPolicy.category`：FACE_SEARCH, DATASET_IMPORT, DEVICE_IMPORT, PERSON_LIBRARY_IMPORT, PROFILE_PORTRAIT, REPORT, EXPORT, CAPTURE_IMAGE, RAW_INGESTION
- `FeatureAvailability.featureCode`（能力开关 12 项）：FACE_SEARCH, DECRYPTION, COMPANION_ANALYSIS, COLLISION_ANALYSIS, REGION_STATISTICS, PROFILE_ASSOCIATION, RESIDENCE_MODEL, WATCH_ACTIVATION, NOTIFICATION_SEND, DEVICE_COMMAND, REPORT_GENERATION, RAW_INGESTION
- `TaskCreated.resourceType`：EXPORT, FACE_SEARCH, DATASET_IMPORT, PERSON_LIBRARY_IMPORT, DEVICE_IMPORT, PROFILE_MERGE, PROFILE_SPLIT, MODEL_RUN, DEVICE_COMMAND, REPORT, NOTIFICATION, INGESTION, BACKFILL
- `BatchDeletionInput.resourceType`：ANALYSIS_TASK, WATCH_RULE, PERSON_LIBRARY, SITE, DEVICE, USER, ROLE, REPORT, NOTIFICATION_TASK, DEVICE_COMMAND, DATASET
- `ReportDefinition.reportType`（八类报表）：DEVICE_REALTIME_ONLINE_RATE, DEVICE_REALTIME_STATUS, FENCE_OPERATOR_STATISTICS, FENCE_AREA_CAPTURE, FENCE_DEVICE_CAPTURE, MAC_CAPTURE, PLATE_CAPTURE, SYSTEM_COMPONENT_STATUS
- `DeviceCommandInput.commandType`：RESET, APPLY_PARAMETERS
- `TrendModel.modelType`：只有 **RESIDENCE** 一个值
- `ModelParameterDescriptor.valueType`：TEXT, INTEGER, NUMBER, BOOLEAN, DURATION_SECONDS, DISTANCE_METERS
- `BatchDeletionItem.status`：DELETED, REJECTED；`BatchRevocationItem.status`：REVOKED, REJECTED
- `ImportResultItem.status`：CREATED, UPDATED, SKIPPED
- `ProfileRevision.action`：CORRECT, MERGE, SPLIT, RESOLVE_CONFLICT
- `Alert.severity`：LOW, MEDIUM, HIGH；`Alert.status`：OPEN, ACKNOWLEDGED, CLOSED
- `TargetResolution.status`：FOUND, REGISTERED, NOT_FOUND, REJECTED
- `DecryptionRequestDetail.status`：PENDING, APPROVED, REJECTED, EXPIRED

> 内部 ID 用 UUID，**但对外展示的枚举值一律用上面这些英文常量**；展示文本来自字典接口 `GET /dictionaries/{dictionaryCode}`，不要在后端硬编码中文。

---

## 8. 身份、权限、横切能力（Spring 里要写的基础设施）

### 8.1 会话与 CSRF

- 认证方案：**SID Cookie Session**（`apiKey` in cookie，名为 `SID`），`HttpOnly / Secure / SameSite=Lax / Path=/api/v1`。
- **不并列 Bearer Token，也不用 localStorage token**。ADR-007 仍是"拟议"，`POST /auth/session/renew` 本身是 pending（P-001），续期策略待安全负责人签认。
- 流程：`GET /auth/csrf`（匿名也可用，设置随机 CSRFID Cookie + 返回同步器 token 与 headerName）→ `POST /auth/login`（**轮换 SID**，返回 userId/idleExpiresAt/absoluteExpiresAt）→ 重新获取 CSRF 并 `GET /me` → `POST /auth/logout`。
- **写请求**必须同时满足：Cookie + `X-CSRF-Token`（header 名以 csrf 接口返回为准，当前是 `X-CSRF-Token`）+ 可信 Origin，否则 `403 AUTH_CSRF_INVALID`。不允许通配凭据 CORS。
- `renew` 只延长**空闲**过期，不延长绝对过期；401 时前端清身份回登录，**不自动重放写请求**。

### 8.2 /me 与权限模型

- `GET /me` 返回 `menus` / `permissionCodes` / `SiteScope` / `DataScope`；**后端按钮权限与 API 权限共用同一套 PermissionCode**。
- `GET /permissions`、`GET /menus` 是"可分配目录"，给角色授权页用；**不能授予超出授予者自身范围的东西**。
- 范围模型：采集数据取 **DataScope ∩ SiteScope**；管理资源用 SiteScope。`allSites=false` 且 `siteIds=[]` 表示**无范围**（不是全部）；`allSites=true` 仍受类型限制和 `ownOnly` 限制。完整多角色策略见 P-002。
- 每次列表/详情/Context/任务/导出/文件访问都要**重新校验**范围；无权 403，单对象不可见 404，Context 收缩 403 `PERMISSION_DATA_SCOPE`。
- 改本人密码（`POST /me/password`）要 `currentPassword`；管理员重置他人密码**不收** `currentPassword`，但 `reason` 必填，且要**撤销该用户会话**。

### 8.3 client-config 是唯一能力目录

- `features` 是唯一能力目录，`featureCode` 用 §7 的 12 项枚举；旧的 `featureAvailability` 已 deprecated，只能**逐项投影** features 的 `code/enabled`，禁止独立计算。
- 未签认能力 `enabled=false` 并返回原因；**不能用 Mock 阈值上线**。
- `catalogBindings` 给出字段 → `dictionaryCode` 的绑定（设备/人员库 category、gender、ethnicity 都要按它读字典，**不要猜目录名**）。
- `uploadPolicies` 按 category 返回限制；旧 `maxUploadBytes` 只是全局上限，**实际限制取"全局"与"分类 maxBytes"的较小值**。
- 配置里还有 `taskPollIntervalSeconds`、`contextTtlSeconds`、`maxOnlineQuerySeconds`、`maxOnlineWindowDays`、`institutionTimeZone=Asia/Shanghai`、`mapCoordinateSystem=EPSG:4326`。

### 8.4 幂等 / 版本 / 批量

- **幂等**：创建、导入、导出、任务、命令等有重复风险的操作用 `Idempotency-Key`（16–160 字符），作用域 = 账号 + operationId + 键，并比较**规范化 body** 与领域 ID。相同输入返回**原响应**，不同输入 `409 IDEMPOTENCY_CONFLICT`。资源存活期内至少保留键；清理后旧键也返回 409，**不产生新副作用**。幂等回放仍要重新校验权限。
- **乐观锁**：ETag = 带双引号的资源 version（如 `"3"`），详情响应 header 原样返回，更新时前端回传 `If-Match`。缺失/非法 400，版本冲突 `409 RESOURCE_VERSION_CONFLICT`。注意 `scopeVersion`、`parameterVersionId`、`algorithmVersion` **不是** ETag，别混用。
- **批量**：`/batch-deletions`、`/batch-revocations` 最多 100 个**不同 UUID + version**；逐项独立事务、按输入顺序返回。**200 只表示整批处理完了**，逐项才是 `DELETED/REVOKED/REJECTED` + `errorCode`。整类无操作权限时 403 且不执行任何一项。
- 删除/撤销权限对照表在 api-conventions §5，例如 `ANALYSIS_TASK`→`analysis:delete`（不支持撤销）、`WATCH_RULE`→`watch:delete`/`watch:revoke`、`NOTIFICATION_TASK`→`notification:cancel`、`DEVICE_COMMAND`→`device:command`。

### 8.5 Context（跨页研判上下文）

- `POST /contexts` **冻结查询/来源**，返回 `contextId` / `expiresAt` 供复用；过期 → `410 CONTEXT_EXPIRED`。
- 来源类型：`CAPTURE_SEARCH` 保存类型条件；`targets=[]` 表示完整授权查询；`DATASET` 绑定 `datasetVersion`；`ANALYSIS_RESULTS` 省略 `resultIds` 表示全部结果；`PROFILE` 绑定 `profileId`。**每次消费都重校权限**。
- `targets` 最多 **100** 个预览，必须同时返回 `targetsTruncated` 与 `targetCount` 表示完整范围；**不能拿预览当全量**。

### 8.6 文件与导入导出

- 上传是 `multipart`（`file` + `category` 必填），**校验真实内容而不是扩展名**；先让前端读 `uploadPolicies`。`FACE_SEARCH`/`PROFILE_PORTRAIT` 只允许 JPEG/PNG；导入只允许 CSV/XLSX/JSON；`RAW_INGESTION` 按已批准 mapping。
- 超限 `413 FILE_TOO_LARGE`；类型不支持 `415 FILE_MEDIA_UNSUPPORTED`；上传后是 `PROCESSING`，要轮询 `GET /files/{fileAssetId}` 到 `ACTIVE` 才可引用（`QUARANTINED` 不可用）。**上传成功 ≠ 导入成功。**
- 导入模板：`GET /import-templates/{templateCode}`，version=1（`DATASET_ITEMS_V1` / `DEVICES_V1` / `PERSON_LIBRARIES_V1`）。XLSX 第一表首行英文列，CSV 是 RFC4180/UTF8（可带 BOM），JSON 是对象数组。未知列 422；`ALL_OR_NOTHING` 全量预检、有非法行整体回滚；`SKIP` 只跳过确定重复。逐行结果用 `GET /tasks/{taskId}/import-result`（CREATED/UPDATED/SKIPPED）。
- 导出是**异步**的：`TaskReference.fileAssetId` → `GET /files/{fileAssetId}/download`。成功响应 `200` 带真实 `mimeType`、`Content-Disposition: attachment`、RFC5987 的 UTF-8 `filename*`；过期 410。**不能给永久裸 URL。**
- 上传/下载/导入模块的字段级细节看 [api-conventions.md](<../contracts/api/api-conventions.md>) §8。

---

## 9. 领域地图：193 个操作长什么样

17 个 Tag（= 契约里的业务分组），数量分布如下。**注意 Tag 是"资源族"，不等于模块**：例如 `Auth` Tag 里既有登录，也有用户/角色/批量删除（它们属于 M14）。

| Tag | 操作数 | 主要模块 | 一句话 |
| --- | --- | --- | --- |
| System | 4 | M01/M15 | 健康检查、就绪、字典、client-config |
| Auth | 27 | M01/M14 | 会话 CSRF、用户、角色、权限/菜单目录、批量删除/撤销、本人改密 |
| File | 4 | M01 | 上传、状态、下载、创建异步导出 |
| Task | 7 | M01 | 任务查询/取消/重试/尝试历史/逐行错误/导入结果 |
| Ingestion | 7 | M03 | 接入批次、质量汇总、错误、回放、补数 |
| Dashboard | 3 | M04 | 系统首页 12 区、运维首页 4 区、指标导出 |
| Capture | 7 | M05/M06 | 四类采集检索、详情、统计、人像检索、解密申请 |
| Context | 3 | M06 | 建/查 Context、目标解析 |
| Map | 5 | M07/M13 | 地图要素、设备点、热力、区域统计、轨迹 |
| Analysis | 9 | M08 | 伴随/碰撞/区域统计任务、结果、导出 |
| Dataset | 16 | M09 | 数据集 CRUD/成员/版本/导入导出 + 集合运算 |
| Watch | 10 | M10 | 布控规则、激活/撤销、告警、统计 |
| Notification | 11 | M10 | 通知计划、送达记录、模板、平台消息 |
| Profile | 36 | M11 | 人员库、档案（人/车）、证据、修订、合并拆分、趋势模型 |
| Operation | 32 | M12 | 场所、设备、参数、状态历史、分组、命令、报表 |
| Report | 7 | M12 | 报表任务、预览、下载（八类报表） |
| Audit | 5 | M14 | 操作日志查询/详情/导出、短信日志查询/导出 |

按模块看（契约里的 `x-module`）：

| 模块 | 操作数 | 说明 |
| --- | --- | --- |
| M01 | 24 | 认证、会话、文件、任务底座、字典/配置、模板、目录 |
| M03 | 7 | 数据接入 |
| M04 | 3 | 首页与运维看板 |
| M05 | 4 | 采集检索 |
| M06 | 6 | 统计、解密、Context |
| M07 | 4 | 地图、轨迹、区域统计 |
| M08 | 9 | 分析任务 |
| M09 | 16 | 数据集与集合运算 |
| M10 | 22 | 布控、告警、通知 |
| M11 | 36 | 人员库、档案、模型 |
| M12 | 39 | 场所、设备、分组、命令、报表 |
| M13 | 1 | 设备地图 |
| M14 | 20 | 用户、角色、审计、短信日志 |
| M15 | 2 | 健康/就绪 |

归属（`x-owner`）：DEV-01 = 112 个（你），DEV-03 = 72 个（数据侧），DEV-02 = 7 个，DEV-04 = 2 个。

### 9.1 接口的"骨架模式"（看懂这个就不怕 193 个）

大多数资源族都是同一套模板，学会一个就会一片：

```
GET    /资源            列表（分页 + 过滤 + 排序）
POST   /资源            创建（201 或 202）
GET    /资源/{id}       详情（带 ETag/version）
PUT    /资源/{id}       全量更新（必填字段完整提供）
PATCH  /资源/{id}       局部更新（省略 = 保留）
DELETE /资源/{id}       软删（有引用保护）
POST   /资源/{id}/activate | revoke | cancel | retry | revisions   状态动作
```

状态机（`api-conventions` §6）：

- `WatchStatus`：`DRAFT → ACTIVE → FINISHED/REVOKED`。201 建草稿，`activate` 才生效；`ACTIVE` 看告警，终结后看**最终**统计，**实时口径和最终口径不能合并**。
- `NotificationPlanStatus`：`ACTIVE / CANCELLED / COMPLETED`。同步 201 返回 `notificationTaskId`/`ACTIVE`/`version`；`ONCE` 完成即 `COMPLETED`；`DAILY` 按 `Asia/Shanghai` 当地日期唯一执行，保持 `ACTIVE` 直到结束/取消，**每次执行有独立 taskId**（取 `lastExecutionTaskId`）。编辑仅 `ACTIVE`，已有 Delivery 不变。
- 通知的收件人：响应只给 `recipientMasked` / `recipientId`；编辑用 `RETAINED` + 同计划 `recipientId` 保留，**创建时禁止 `RETAINED`**；SMS/EMAIL 地址只出现在请求 body；PLATFORM 用 `userId`。
- 数据集与集合运算：先建**空 dataset** 再异步导入；`CAPTURE` 按 `captureId` 等价，`TARGET` 按 `targetType+targetId` 等价，混用 `422 DATASET_ELEMENT_TYPE_MISMATCH`。集合运算 `operands` 是唯一 A–Z 符号，`MIXED` 必须给 `expression`；只允许字母、括号和 `∩∪−/-`，同优先级左结合，**禁止 eval、禁止自动跨源身份合并**（要跟前端 `evaluate` 行为一致）。`preview` 只是估量，不是承诺，执行时才冻结版本。
- 人员/档案：新增人员**同事务**返回 `memberId` / `profileId` / `linkedTargets`；移除成员**不删除全局档案**；重复标识**不能静默合并**（同人判定未定 → `422 REQUIREMENT_PENDING`）。
- 设备与命令：命令结果是**逐设备**的，外层成功不代表每台成功；`RESET` / `APPLY_PARAMETERS` 两类。
- 报表：`SINGLE` 是一 definition → XLSX（有在线预览，按 `previewKind` 分型）；`BUNDLE` 是多 definitions → ZIP（无在线预览）。大预览用 cursor 取明细，`operatorSeries` 对应当页设备，摘要是**全授权范围**。

---

## 10. 施工顺序建议（不要按 Tag 顺序做）

### 第 0 步：最小闭环（先证明链路通）

`getCsrfToken` → `login` → `getMe` → `getClientConfig` → `logout` + 统一响应/错误/traceId/异常兜底。
这一步把"会话 + 统一壳 + 错误码 + traceId 日志"钉死，后面所有接口都复用，**最省时间的一步**。

### 第 1 步：底座

`Task`（7 个：查询/取消/重试/尝试/逐行错误/导入结果）+ `File`（4 个：上传/状态/下载/导出）+ `Health/Ready` + 批量删除/撤销。
任务底座是后面几十个 202 接口的地基，必须先做。

### 第 2 步：主数据 CRUD（已签认、无 pending、纯业务）

`Site`（5 个全部已签认）、`Device`（部分 pending）、`DeviceGroup`（7 个全部已签认）、`User` / `Role`。
这一批能快速产出"真实数据库写入 → 前端读回"的完整闭环。

### 第 3 步：查询类

`Capture` 四类检索 + `Context` + `Map` / 轨迹（依赖 DEV-03 的检索与空间聚合）。

### 第 4 步：任务型

`Ingestion`、`Analysis`、`Dataset` + 集合运算、`Report`。

### 第 5 步：编排型

`Watch` / 告警 / `Notification` / 送达 / 设备命令（外部副作用最多，最需要幂等与回执设计）。

### 第 6 步：Profile 与模型（pending 最多，36 个里 24 个 pending）

先把**已签认**的做掉：`listPersonLibraries`、`createPersonLibrary`、`getPersonLibrary`、`updatePersonLibrary`、`deletePersonLibrary`、`listPersonLibraryMembers`、`removePersonLibraryMember`、`listProfileEvidence`、`listProfileHistory`、参数版本查询、多库导出。
档案检索/详情/轨迹/合并/拆分/修订/导出/趋势模型执行**等规则签认**。

### 第 7 步：看板与审计

`Dashboard` 指标（依赖指标定义）、`Audit` 查询导出、`Delivery` 日志。

### 每个领域的固定动作

1. 用 OpenAPI Generator 生成接口/模型骨架（见 §11）。
2. 接上统一响应壳、异常映射、权限注解、数据范围切面。
3. 接数据库（`contracts/database/schema.sql`），DAO 投影按 `api-storage-map.md`。
4. 需要计算的调 DEV-03。
5. 写测试：正例 + 负例（无权 403 / 不可见 404 / 版本冲突 409 / 游标非法 400 / 结果未就绪 409）。

---

## 11. Spring Boot 落地要点

### 11.1 代码生成（契约已给出建议配置）

```
generatorName: spring
interfaceOnly=true, useSpringBoot3=true, useOneOfInterfaces=true
CLI: --name-mappings setTaskId=setOperationId
```

- 生成器版本参考 **7.25.0**（审计报告里用过），Java **17**。
- **`setTaskId=setOperationId` 是关键**：JSON 字段仍叫 `taskId`，但 Java setter 改名，避免和"执行 taskId"的 setter 重名。**不要为了编译通过去改契约字段名。**
- `interfaceOnly=true` 只生成接口，你可以自己控 Controller 实现，适合先接基础设施。
- 判别字段用 `pattern` + `discriminator.mapping` 固定值：Spring 侧分支 getter 会一致，但仍必须**校验常量**，不能任意赋值。
- 生成成功后**不要手改生成代码**，把它当契约产物；业务写在 `service` / `mapper` 层。

### 11.2 需要自己写的基础设施（生成器不会给）

| 能力 | 落地方式 |
| --- | --- |
| 统一响应壳 | `ResponseBodyAdvice` 或显式包装，保证 `code/message/data/traceId`；文件流走 `ResponseEntity<Resource>` 且不套壳 |
| 统一错误 | `@RestControllerAdvice` → `ErrorCode` enum → HTTP 映射表（§4.1）；`details` 给字段路径；HTTP 与 code 必须一致 |
| traceId | 过滤器生成/透传，写入 MDC 与 `X-Trace-Id` 响应头，与 JSON 里一致 |
| 会话 | Spring Session + `SID` Cookie（HttpOnly/Secure/SameSite=Lax/Path=/api/v1）；登录轮换 SID |
| CSRF | Cookie 同步器模式：`GET /auth/csrf` 下发 token，写请求校验 `X-CSRF-Token` + Origin，失败 403 `AUTH_CSRF_INVALID` |
| 幂等 | `Idempotency-Key` 表（账号 + operationId + 键 + 规范化 body 摘要 + 原响应），相同返回原响应，不同 409 |
| 乐观锁 | `If-Match` ↔ `version`，冲突 409 `RESOURCE_VERSION_CONFLICT` |
| 游标 | 签名（HMAC）cursor，绑定账号/范围版本/查询快照/排序/页大小；**不解析**，校验失败 400 `DATA_CURSOR_INVALID` |
| 权限 | `@PreAuthorize` + `PermissionCode` 枚举（58 项）；**数据范围单独一层切面/过滤器**，列表/详情/导出/文件都要过 |
| 审计 | 写操作落审计日志（M14），字段脱敏，`eventId` 可查 |
| 能力开关 | 读 `client-config.features`，未签认能力直接 `422 REQUIREMENT_PENDING` |

### 11.3 包结构建议

```
apps/api/src/main/java/<base>/
  api/        生成或手写的 Controller（薄，只做参数校验与编排）
  domain/     业务服务、状态机（任务、布控、通知计划、数据集版本）
  infra/      持久化、幂等表、游标签名、文件存储、CSRF/会话、审计
  client/     调用 DEV-03 数据/计算 Worker 的内部客户端
  common/     响应壳、错误码、traceId、分页、权限注解
```

---

## 12. ⚠ 65 个 pending 操作（别先做，做了要返工）

契约里这 65 个操作自己带 `x-design-status: pending` 和 `x-pending-reason`。**先读原因再动手**，多数是"规则未签认"，不是"接口没定义"。

按领域分布：

| 领域 | pending 数 | 典型原因 |
| --- | --- | --- |
| Auth（含用户/角色/批量） | 8 | P-001 会话续期、密码/审批策略 |
| Ingestion | 3 | P-004/P-005 设备协议、映射版本、回放去重键 |
| Capture（人像/解密） | 4 | P-007 模型阈值与候选上限、P-008 解密审批与密钥 |
| Context | 1 | 标识规范化/同人判定（P-016） |
| Watch | 3 | 布控规则与激活策略 |
| Notification | 4 | P-015 补发/频控、模板 |
| Profile（人员库/档案/模型） | 24 | P-016 身份归一/自动关联/合并拆分、P-017 算法阈值 |
| Operation（设备/命令/报表） | 12 | P-004 设备协议、命令审批、报表公式 |
| Dashboard/Audit | 6 | 指标定义、导出范围 |

完整 65 项清单（含路径与 operationId）见本指南附录 C。

**开工前的项目级检查**：读 [OPENAPI_PENDING.md](<../contracts/api/OPENAPI_PENDING.md>) 与 [PENDING_REGISTER.md](<project-governance/PENDING_REGISTER.md>)。其中 **P-001（认证方案）直接影响你的会话设计**，如果 P-001 结论变了，认证层要改，所以第一版请把认证做成可替换的一层，不要散落各处。

---

## 13. 验收与常见坑

### 13.1 联调验收清单

- [ ] 浏览器真实请求 → 服务端日志有同一个 traceId
- [ ] 真实数据库写入，刷新页面能读回
- [ ] 202 任务：创建 → 轮询 → SUCCESS → 取领域结果 / 受权下载
- [ ] 无权 403 / 单对象不可见 404（不是 403）/ 范围收缩 403 `PERMISSION_DATA_SCOPE`
- [ ] 版本冲突 409 `RESOURCE_VERSION_CONFLICT`、幂等冲突 409 `IDEMPOTENCY_CONFLICT`
- [ ] 游标篡改 400 `DATA_CURSOR_INVALID`；条件变更后旧游标失效
- [ ] 结果未就绪 409 `TASK_RESULT_NOT_READY`；文件过期 410 `FILE_EXPIRED`
- [ ] 取消失败路径：RUNNING 取消 → `cancelRequestedAt` → 最终 CANCELLED；终态取消 409 `TASK_NOT_CANCELLABLE`
- [ ] 批量部分失败：200 但逐项 REJECTED + errorCode
- [ ] 文件超限 413 / 类型不符 415 / 未 ACTIVE 不可引用
- [ ] 服务停止 503，且限流/503 带 `Retry-After`
- [ ] 未签认能力在 client-config 中不可用并返回 `422 REQUIREMENT_PENDING`

### 13.2 最容易踩的坑（新人自查）

1. 用 `message` 判断错误 → 必须用 `code`。
2. 用 null 表示"未提供" → 省略才是，null 只给 nullable 字段。
3. 越权返回 403 → 单对象应 404 隐藏存在性。
4. 异步任务把业务 ID 当 `taskId`，或 `analysisTaskId` 与执行 `taskId` 混用。
5. 取消 202 就当已取消 → 要等 Worker 确认。
6. 给下载接口"永久 URL"，或让下载返回 JSON 壳。
7. 忘了 `X-Trace-Id` 响应头，或头与 body 的 traceId 不一致。
8. 上传只校验扩展名 → 必须校验真实内容与 category 策略。
9. 自己发明第三种分页，或给 cursor 分页伪造 `total`。
10. 把 pending 接口用 Mock 阈值先实现 → 违反项目规则，必须返回 `REQUIREMENT_PENDING`。
11. 权限只做"操作级"`@PreAuthorize`，漏掉**数据范围**校验（列表/详情/导出/文件都要）。
12. 时间不带时区、或 `[from,to)` 写成了闭区间。
13. 经纬度顺序写成 `[lat,lon]`，或把缺失经纬画成 (0,0)。
14. 修改生成代码让编译通过。

---

## 14. 检索技巧：怎么在 37853 行里快速找到目标接口

### 14.1 用编辑器按 operationId 搜索

所有接口在 YAML 里都是这个形状，搜 `operationId: "getHealth"` 直接跳过去：

```yaml
  "/health":
    get:
      operationId: "getHealth"
      x-module: "M15"        # 属于哪个模块
      x-owner: "DEV-04"      # 谁负责（你是 DEV-01）
      x-permission: "PUBLIC" # 需要的权限
      responses:
        "200": { ... }       # data 的 Schema + 成功示例
        "400": { "$ref": "#/components/responses/Http400" }
      x-requirement-source: "SD08/SD09/SD10 T20"   # 需求出处
```

每个操作还带 `x-design-status`（pending 时才有）和 `x-pending-reason`（为什么不能做）。

### 14.2 看 Schema

`components.schemas` 从第 16627 行开始，一共 581 个。命名有规律，按前缀就能猜：

| 前缀 | 用途 | 例子 |
| --- | --- | --- |
| `XxxResponse` | 统一壳（code/message/data/traceId） | `GetHealthResponse` |
| `XxxData` / 领域名 | `data` 里的真实业务对象 | `Task`, `HealthData`, `Site` |
| `XxxPage` | 分页容器（38 个） | `TaskPage`（page 型）、`AlertPage`（cursor 型） |
| `XxxInput` / `XxxRequest` | 请求 body | `AnalysisTaskInput`, `CaptureSearchRequest` |
| `XxxUpdate` / `XxxExportInput` | 局部更新 / 导出参数 | `DatasetUpdate`, `ResultExportInput` |
| `ListXxx` / `GetXxx` / `CreateXxx` … | 按动作前缀的操作级 Schema | `ListTasksResponse` |

`Page` 类型只有两种形状，一眼就能分辨：有 `page/total` 的是页码型，有 `nextCursor` 的是游标型（只有 `PlatformMessagePage` 两者都有，另带 `totalCount/asOf`）。

### 14.3 常用命令（仓库就绪）

```powershell
# 看某个接口定义（含示例）
Select-String -Path contracts\api\openapi.yaml -Pattern 'operationId: "createAnalysisTask"' -Context 0,60

# 生成前端类型（改了契约要同步，别手改生成物）
npm --prefix apps/web run generate:api
npm --prefix apps/web run typecheck
npm --prefix apps/web test
```

### 14.4 明确不做的事（契约边界）

厂商协议、Worker 内部协议、短信/算法内部协议**都不是 Web 接口**；CI/CD、备份、发布也不是业务 REST。平台前端 HTTP 边界之外的东西不要塞进 `/api/v1`。

---

## 附录 A：全部 193 个操作（按 Tag 分组）

> 完整路径 = `/api/v1` + 下表中的路径。`pending` 列有 ⚠ 的表示契约未签认（见 §12）。请求/响应 Schema 名可用 §14.1 的方法定位。

### System（4 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/health` | `getHealth` | PUBLIC |  |
| GET | `/ready` | `getReadiness` | PUBLIC |  |
| GET | `/dictionaries/{dictionaryCode}` | `getDictionary` | AUTHENTICATED |  |
| GET | `/client-config` | `getClientConfig` | AUTHENTICATED |  |

### Auth（27 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/auth/csrf` | `getCsrfToken` | PUBLIC |  |
| POST | `/auth/login` | `login` | PUBLIC |  |
| POST | `/auth/logout` | `logout` | AUTHENTICATED |  |
| POST | `/auth/session/renew` | `renewSession` | AUTHENTICATED | ⚠ |
| GET | `/me` | `getMe` | AUTHENTICATED |  |
| GET | `/users` | `listUsers` | user:read |  |
| POST | `/users` | `createUser` | user:write | ⚠ |
| GET | `/users/{userId}` | `getUser` | user:read |  |
| PUT | `/users/{userId}` | `updateUser` | user:write | ⚠ |
| DELETE | `/users/{userId}` | `deleteUser` | user:write |  |
| POST | `/users/{userId}/disable` | `disableUser` | user:disable | ⚠ |
| POST | `/users/{userId}/enable` | `enableUser` | user:disable |  |
| POST | `/users/{userId}/password` | `changeUserPassword` | user:write | ⚠ |
| GET | `/roles` | `listRoles` | role:read |  |
| POST | `/roles` | `createRole` | role:grant | ⚠ |
| GET | `/roles/{roleId}` | `getRole` | role:read |  |
| PUT | `/roles/{roleId}` | `updateRole` | role:grant |  |
| DELETE | `/roles/{roleId}` | `deleteRole` | role:grant |  |
| PUT | `/roles/{roleId}/permissions` | `setRolePermissions` | role:grant | ⚠ |
| PUT | `/roles/{roleId}/scopes` | `setRoleScopes` | role:grant | ⚠ |
| GET | `/permissions` | `listPermissionCatalog` | role:grant |  |
| GET | `/menus` | `listMenuCatalog` | role:grant |  |
| GET | `/regions` | `listRegions` | AUTHENTICATED |  |
| GET | `/import-templates/{templateCode}` | `getImportTemplate` | AUTHENTICATED |  |
| POST | `/me/password` | `changeMyPassword` | AUTHENTICATED | ⚠ |
| POST | `/batch-deletions` | `deleteResourcesBatch` | AUTHENTICATED |  |
| POST | `/batch-revocations` | `revokeResourcesBatch` | AUTHENTICATED |  |

### File（4 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| POST | `/files/uploads` | `uploadFile` | AUTHENTICATED |  |
| GET | `/files/{fileAssetId}` | `getFileAsset` | AUTHENTICATED |  |
| GET | `/files/{fileAssetId}/download` | `downloadFile` | file:download |  |
| POST | `/exports` | `createExport` | capture:export |  |

### Task（7 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/tasks` | `listTasks` | task:read |  |
| GET | `/tasks/{taskId}` | `getTask` | task:read |  |
| POST | `/tasks/{taskId}/cancel` | `cancelTask` | task:manage |  |
| POST | `/tasks/{taskId}/retry` | `retryTask` | task:manage |  |
| GET | `/tasks/{taskId}/attempts` | `listTaskAttempts` | task:read |  |
| GET | `/tasks/{taskId}/errors` | `listTaskRowErrors` | task:read |  |
| GET | `/tasks/{taskId}/import-result` | `getImportTaskResult` | task:read |  |

### Ingestion（7 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/ingestion-batches` | `listIngestionBatches` | ingestion:read |  |
| POST | `/ingestion-batches` | `createIngestionBatch` | ingestion:write | ⚠ |
| GET | `/ingestion-batches/{batchId}` | `getIngestionBatch` | ingestion:read |  |
| GET | `/ingestion-batches/{batchId}/errors` | `listIngestionErrors` | ingestion:read |  |
| GET | `/ingestion/quality-summary` | `getIngestionQualitySummary` | ingestion:read |  |
| POST | `/ingestion-batches/{batchId}/replay` | `replayIngestionBatch` | ingestion:replay | ⚠ |
| POST | `/ingestion/backfills` | `createIngestionBackfill` | ingestion:replay | ⚠ |

### Dashboard（3 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/dashboard/summary` | `getDashboardSummary` | dashboard:read |  |
| GET | `/operations/summary` | `getOperationSummary` | dashboard:read |  |
| POST | `/metrics/exports` | `exportMetrics` | dashboard:read | ⚠ |

### Capture（7 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| POST | `/captures/search` | `searchCaptures` | capture:read |  |
| GET | `/captures/{captureId}` | `getCapture` | capture:read |  |
| POST | `/captures/statistics` | `getCaptureStatistics` | capture:read |  |
| POST | `/faces/search-tasks` | `createFaceSearchTask` | face:search | ⚠ |
| GET | `/faces/search-tasks/{taskId}/results` | `listFaceSearchResults` | face:search | ⚠ |
| POST | `/decrypt-requests` | `requestDecryption` | capture:decrypt | ⚠ |
| GET | `/decrypt-requests/{requestId}` | `getDecryptionRequest` | capture:decrypt | ⚠ |

### Context（3 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| POST | `/contexts` | `createContext` | context:use |  |
| GET | `/contexts/{contextId}` | `getContext` | context:use |  |
| POST | `/targets/resolve` | `resolveTargets` | AUTHENTICATED | ⚠ |

### Map（5 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/map/features` | `getMapFeatures` | map:read |  |
| GET | `/map/devices` | `getDeviceMapFeatures` | map:read |  |
| GET | `/map/heatmap` | `getMapHeatmap` | map:read |  |
| POST | `/map/area-summary` | `getMapAreaSummary` | map:read |  |
| POST | `/trajectories/query` | `queryTrajectory` | map:read |  |

### Analysis（9 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/analysis-tasks` | `listAnalysisTasks` | analysis:read |  |
| POST | `/analysis-tasks` | `createAnalysisTask` | analysis:create |  |
| GET | `/analysis-tasks/{analysisTaskId}` | `getAnalysisTask` | analysis:read |  |
| DELETE | `/analysis-tasks/{analysisTaskId}` | `deleteAnalysisTask` | analysis:delete |  |
| POST | `/analysis-tasks/{analysisTaskId}/revisions` | `reviseAnalysisTask` | analysis:update |  |
| GET | `/analysis-tasks/{analysisTaskId}/results` | `listAnalysisResults` | analysis:read |  |
| GET | `/analysis-tasks/{analysisTaskId}/results/{resultId}` | `getAnalysisResult` | analysis:read |  |
| POST | `/analysis-tasks/{analysisTaskId}/results/export` | `exportAnalysisResults` | capture:export |  |
| POST | `/analysis-tasks/exports` | `exportAnalysisTasks` | capture:export |  |

### Dataset（16 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/datasets` | `listDatasets` | dataset:read |  |
| POST | `/datasets` | `createDataset` | dataset:write |  |
| GET | `/datasets/{datasetId}` | `getDataset` | dataset:read |  |
| PATCH | `/datasets/{datasetId}` | `updateDataset` | dataset:write |  |
| DELETE | `/datasets/{datasetId}` | `deleteDataset` | dataset:write |  |
| GET | `/datasets/{datasetId}/items` | `listDatasetItems` | dataset:read |  |
| POST | `/datasets/{datasetId}/items` | `addDatasetItems` | dataset:write |  |
| POST | `/datasets/{datasetId}/imports` | `importDatasetItems` | dataset:write |  |
| GET | `/datasets/{datasetId}/versions` | `listDatasetVersions` | dataset:read |  |
| POST | `/datasets/{datasetId}/export` | `exportDataset` | dataset:export |  |
| POST | `/set-tasks/preview` | `previewSetTask` | dataset:read |  |
| GET | `/set-tasks` | `listSetTasks` | dataset:read |  |
| POST | `/set-tasks` | `createSetTask` | dataset:write |  |
| GET | `/set-tasks/{setTaskId}` | `getSetTask` | dataset:read |  |
| GET | `/set-tasks/{setTaskId}/results` | `listSetTaskResults` | dataset:read |  |
| DELETE | `/datasets/{datasetId}/items/{itemId}` | `removeDatasetItem` | dataset:write |  |

### Watch（10 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/watch-rules` | `listWatchRules` | watch:read |  |
| POST | `/watch-rules` | `createWatchRule` | watch:write | ⚠ |
| GET | `/watch-rules/{ruleId}` | `getWatchRule` | watch:read |  |
| PATCH | `/watch-rules/{ruleId}` | `updateWatchRule` | watch:write | ⚠ |
| DELETE | `/watch-rules/{ruleId}` | `deleteWatchRule` | watch:delete |  |
| POST | `/watch-rules/{ruleId}/activate` | `activateWatchRule` | watch:write | ⚠ |
| POST | `/watch-rules/{ruleId}/revoke` | `revokeWatchRule` | watch:revoke |  |
| GET | `/watch-rules/{ruleId}/statistics` | `getWatchStatistics` | watch:read |  |
| GET | `/alerts` | `listAlerts` | watch:read |  |
| GET | `/alerts/{alertId}` | `getAlert` | watch:read |  |

### Notification（11 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/notification-tasks` | `listNotificationTasks` | notification:read |  |
| POST | `/notification-tasks` | `createNotificationTask` | notification:send | ⚠ |
| GET | `/notification-tasks/{notificationTaskId}` | `getNotificationTask` | notification:read |  |
| PUT | `/notification-tasks/{notificationTaskId}` | `updateNotificationTask` | notification:send | ⚠ |
| DELETE | `/notification-tasks/{notificationTaskId}` | `deleteNotificationTask` | notification:cancel |  |
| POST | `/notification-tasks/{notificationTaskId}/cancel` | `cancelNotificationTask` | notification:cancel |  |
| POST | `/notification-tasks/{notificationTaskId}/retry` | `retryNotificationTask` | notification:send | ⚠ |
| GET | `/deliveries` | `listDeliveries` | delivery:read |  |
| GET | `/deliveries/{deliveryId}` | `getDelivery` | delivery:read |  |
| GET | `/notification-templates` | `listNotificationTemplates` | notification:read | ⚠ |
| GET | `/messages` | `listPlatformMessages` | AUTHENTICATED |  |

### Profile（36 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/person-libraries` | `listPersonLibraries` | person-library:read |  |
| POST | `/person-libraries` | `createPersonLibrary` | person-library:write |  |
| GET | `/person-libraries/{libraryId}` | `getPersonLibrary` | person-library:read |  |
| PUT | `/person-libraries/{libraryId}` | `updatePersonLibrary` | person-library:write |  |
| DELETE | `/person-libraries/{libraryId}` | `deletePersonLibrary` | person-library:write |  |
| GET | `/person-libraries/{libraryId}/members` | `listPersonLibraryMembers` | person-library:read |  |
| POST | `/person-libraries/{libraryId}/members` | `addPersonLibraryMembers` | person-library:write | ⚠ |
| DELETE | `/person-libraries/{libraryId}/members/{memberId}` | `removePersonLibraryMember` | person-library:write |  |
| POST | `/person-libraries/{libraryId}/imports` | `importPersonLibraryMembers` | person-library:write | ⚠ |
| POST | `/person-libraries/{libraryId}/exports` | `exportPersonLibrary` | person-library:export | ⚠ |
| POST | `/profiles/search` | `searchProfiles` | profile:read | ⚠ |
| GET | `/profiles/{profileId}` | `getProfile` | profile:read | ⚠ |
| GET | `/profiles/{profileId}/targets` | `listProfileTargets` | profile:read | ⚠ |
| GET | `/profiles/{profileId}/evidence` | `listProfileEvidence` | profile:read |  |
| GET | `/profiles/{profileId}/history` | `listProfileHistory` | profile:read |  |
| GET | `/profiles/{profileId}/trajectories` | `listProfileTrajectories` | profile:read | ⚠ |
| POST | `/profiles/{profileId}/corrections` | `createProfileCorrection` | profile:correct | ⚠ |
| POST | `/profiles/merge` | `mergeProfiles` | profile:merge | ⚠ |
| POST | `/profiles/{profileId}/split` | `splitProfile` | profile:merge | ⚠ |
| POST | `/profiles/{profileId}/conflicts/{conflictId}/resolve` | `resolveProfileConflict` | profile:correct | ⚠ |
| POST | `/profiles/{profileId}/exports` | `exportProfile` | profile:export | ⚠ |
| GET | `/trend-models` | `listTrendModels` | model:read | ⚠ |
| POST | `/trend-models` | `createTrendModel` | model:write | ⚠ |
| GET | `/trend-models/{modelId}` | `getTrendModel` | model:read | ⚠ |
| PUT | `/trend-models/{modelId}` | `updateTrendModel` | model:write | ⚠ |
| GET | `/trend-models/{modelId}/parameter-versions` | `listModelParameterVersions` | model:read |  |
| POST | `/trend-models/{modelId}/parameter-versions` | `createModelParameterVersion` | model:write | ⚠ |
| GET | `/trend-models/{modelId}/parameter-versions/{parameterVersionId}` | `getModelParameterVersion` | model:read |  |
| GET | `/model-tags` | `listModelTags` | model:read | ⚠ |
| POST | `/trend-models/{modelId}/runs` | `runTrendModel` | model:execute | ⚠ |
| GET | `/trend-models/{modelId}/results` | `listTrendModelResults` | model:read | ⚠ |
| GET | `/trend-models/{modelId}/results/{resultId}` | `getTrendModelResult` | model:read | ⚠ |
| POST | `/person-libraries/{libraryId}/persons` | `createLibraryPerson` | person-library:write | ⚠ |
| GET | `/profiles/{profileId}/conflicts` | `listProfileConflicts` | profile:read | ⚠ |
| POST | `/person-libraries/imports` | `importPersonLibraries` | person-library:write | ⚠ |
| POST | `/person-libraries/exports` | `exportPersonLibraries` | person-library:export |  |

### Operation（32 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/sites` | `listSites` | site:read |  |
| POST | `/sites` | `createSite` | site:write |  |
| GET | `/sites/{siteId}` | `getSite` | site:read |  |
| PUT | `/sites/{siteId}` | `updateSite` | site:write |  |
| DELETE | `/sites/{siteId}` | `deleteSite` | site:write |  |
| GET | `/devices` | `listDevices` | device:read |  |
| POST | `/devices` | `createDevice` | device:write | ⚠ |
| GET | `/devices/{deviceId}` | `getDevice` | device:read |  |
| PUT | `/devices/{deviceId}` | `updateDevice` | device:write | ⚠ |
| DELETE | `/devices/{deviceId}` | `deleteDevice` | device:write |  |
| GET | `/devices/{deviceId}/status` | `getDeviceStatus` | device:read |  |
| GET | `/devices/{deviceId}/parameters` | `getDeviceParameters` | device:read | ⚠ |
| POST | `/devices/{deviceId}/parameter-updates` | `updateDeviceParameters` | device:command | ⚠ |
| GET | `/devices/{deviceId}/status-history` | `listDeviceStatusHistory` | device:read |  |
| GET | `/devices/{deviceId}/capture-statistics` | `getDeviceCaptureStatistics` | device:read | ⚠ |
| POST | `/devices/imports` | `importDevices` | device:write | ⚠ |
| POST | `/devices/exports` | `exportDevices` | device:read | ⚠ |
| GET | `/device-groups` | `listDeviceGroups` | device-group:read |  |
| POST | `/device-groups` | `createDeviceGroup` | device-group:write |  |
| GET | `/device-groups/{groupId}` | `getDeviceGroup` | device-group:read |  |
| PUT | `/device-groups/{groupId}` | `updateDeviceGroup` | device-group:write |  |
| DELETE | `/device-groups/{groupId}` | `deleteDeviceGroup` | device-group:write |  |
| GET | `/device-groups/{groupId}/members` | `listDeviceGroupMembers` | device-group:read |  |
| POST | `/device-groups/{groupId}/members` | `addDeviceGroupMembers` | device-group:write |  |
| DELETE | `/device-groups/{groupId}/members/{deviceId}` | `removeDeviceGroupMember` | device-group:write |  |
| GET | `/device-commands` | `listDeviceCommands` | device:read |  |
| POST | `/device-commands` | `createDeviceCommand` | device:command | ⚠ |
| GET | `/device-commands/{taskId}` | `getDeviceCommand` | device:read |  |
| DELETE | `/device-commands/{taskId}` | `deleteDeviceCommand` | device:command |  |
| GET | `/device-commands/{taskId}/results` | `listDeviceCommandResults` | device:read |  |
| POST | `/device-commands/{taskId}/retry` | `retryDeviceCommand` | device:command | ⚠ |
| POST | `/device-commands/{taskId}/revisions` | `reviseDeviceCommand` | device:command | ⚠ |

### Report（7 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| GET | `/reports` | `listReports` | report:read |  |
| POST | `/reports` | `createReport` | report:create | ⚠ |
| GET | `/reports/{reportId}` | `getReport` | report:read |  |
| DELETE | `/reports/{reportId}` | `deleteReport` | report:delete |  |
| GET | `/reports/{reportId}/preview` | `getReportPreview` | report:read | ⚠ |
| POST | `/reports/{reportId}/retry` | `retryReport` | report:create |  |
| GET | `/reports/{reportId}/download` | `downloadReport` | report:read |  |

### Audit（5 个）

| 方法 | 路径 | operationId | 权限 | pending |
| --- | --- | --- | --- | --- |
| POST | `/audit-events/search` | `searchAuditEvents` | audit:read | ⚠ |
| GET | `/audit-events/{eventId}` | `getAuditEvent` | audit:read |  |
| POST | `/audit-events/export` | `exportAuditEvents` | audit:export | ⚠ |
| POST | `/deliveries/search` | `searchSmsLogs` | delivery:read | ⚠ |
| POST | `/deliveries/export` | `exportSmsLogs` | delivery:export | ⚠ |

## 附录 B：按第一方文件

- 契约：[openapi.yaml](<../contracts/api/openapi.yaml>)、[约定](<../contracts/api/api-conventions.md>)、[错误码](<../contracts/api/error-codes.md>)、[前端集成](<../contracts/api/FRONTEND_INTEGRATION.md>)、[未决项](<../contracts/api/OPENAPI_PENDING.md>)、[覆盖矩阵](<../contracts/api/api-coverage-matrix.md>)、[审计报告](<../contracts/api/openapi-audit-report.md>)
- 交接：[前端接口交接](<project-governance/FRONTEND_API_HANDOFF.md>)、[DEV-01 后端入口](<team/DEV-01-backend.md>)、[开发计划](<project-governance/DEVELOPMENT_PLAN.md>)
- 数据库：[schema.sql](<../contracts/database/schema.sql>)、[数据字典](<../contracts/database/data-dictionary.md>)、[接口↔存储映射](<../contracts/database/api-storage-map.md>)、[索引设计](<../contracts/database/index-design.md>)
- 设计：[API 与契约设计](<system-design/04_API与接口契约设计.md>)、[异步任务 ADR](<system-design/adr/ADR-005-async-task.md>)、[认证 ADR](<system-design/adr/ADR-007-authentication.md>)、[后端选型 ADR](<system-design/adr/ADR-002-backend.md>)、[集成方案](<system-design/10_开发联调与集成方案.md>)

## 附录 C：pending 操作清单（65 个）

| # | 方法 | 路径 | operationId | 未签认原因（x-pending-reason） |
| --- | --- | --- | --- | --- |
| 1 | POST | `/auth/session/renew` | `renewSession` | P-001：工程实施版写 Token 续期，ADR-007 拟议 Cookie Session；续期是否显式、空闲与绝对期限尚需安全负责人签认。 |
| 2 | POST | `/ingestion-batches` | `createIngestionBatch` | P-004：真实设备协议、字段/坐标映射和稳定去重键待设备方确认；接口仅接收已上传文件与版本引用。 |
| 3 | POST | `/ingestion-batches/{batchId}/replay` | `replayIngestionBatch` | P-004/P-005：回放去重键、映射版本及源协议待确认；不能覆盖原始记录。 |
| 4 | POST | `/ingestion/backfills` | `createIngestionBackfill` | P-004/P-005：补数来源协议与许可时间窗待设备/数据负责人确认。 |
| 5 | POST | `/faces/search-tasks` | `createFaceSearchTask` | P-007：模型、阈值、候选上限与人工复核规则未签认。 |
| 6 | GET | `/faces/search-tasks/{taskId}/results` | `listFaceSearchResults` | P-007：候选展示和模型版本保留，可信阈值仍待签认。 |
| 7 | POST | `/decrypt-requests` | `requestDecryption` | P-008：真实解密、审批、密钥和明文暴露规则未签认；此接口边界仅允许申请和状态。 |
| 8 | GET | `/decrypt-requests/{requestId}` | `getDecryptionRequest` | P-008：明文结果访问方式待安全负责人确认。 |
| 9 | POST | `/watch-rules` | `createWatchRule` | P-014/P-015：匹配算法、频控及通知规则未签认；创建 DRAFT，激活前重新校验。 |
| 10 | PATCH | `/watch-rules/{ruleId}` | `updateWatchRule` | P-014：运行中规则变更生效边界待签认；历史命中仍引用旧版本。 |
| 11 | POST | `/watch-rules/{ruleId}/activate` | `activateWatchRule` | P-014/P-015：激活前规则匹配和通知频控必须由业务确认；接口只定义状态边界。 |
| 12 | POST | `/notification-tasks` | `createNotificationTask` | P-014/P-015：模板、频控、通道及回执规则待确认；创建请求保留受控引用。 |
| 13 | PUT | `/notification-tasks/{notificationTaskId}` | `updateNotificationTask` | P-015：错过执行窗口的补发/频控规则待确认。 |
| 14 | POST | `/notification-tasks/{notificationTaskId}/retry` | `retryNotificationTask` | P-015：回执不明时的对账、重试次数和失败转人工策略未定，不能盲重发。 |
| 15 | POST | `/person-libraries/{libraryId}/members` | `addPersonLibraryMembers` | P-002/P-016：成员身份与源数据权限继承规则未签认。 |
| 16 | POST | `/person-libraries/{libraryId}/imports` | `importPersonLibraryMembers` | P-016：人员库导入模板、逐行校验和去重规则未签认。 |
| 17 | POST | `/person-libraries/{libraryId}/exports` | `exportPersonLibrary` | P-002/P-016：人员库导出字段、数据范围和审批规则未签认。 |
| 18 | POST | `/profiles/search` | `searchProfiles` | P-016：跨源档案生成与检索字段需产品/算法签认。 |
| 19 | GET | `/profiles/{profileId}` | `getProfile` | P-016：跨源关联、人工修订、合并拆分和档案冲突解决规则待产品与算法负责人签认。 |
| 20 | GET | `/profiles/{profileId}/targets` | `listProfileTargets` | P-016：跨源关联、人工修订、合并拆分和档案冲突解决规则待产品与算法负责人签认。 |
| 21 | GET | `/profiles/{profileId}/trajectories` | `listProfileTrajectories` | P-016：跨源关联、人工修订、合并拆分和档案冲突解决规则待产品与算法负责人签认。 |
| 22 | POST | `/profiles/{profileId}/corrections` | `createProfileCorrection` | P-016：跨源关联、人工修订、合并拆分和档案冲突解决规则待产品与算法负责人签认。 |
| 23 | POST | `/profiles/merge` | `mergeProfiles` | P-016：跨源关联、人工修订、合并拆分和档案冲突解决规则待产品与算法负责人签认。 |
| 24 | POST | `/profiles/{profileId}/split` | `splitProfile` | P-016：跨源关联、人工修订、合并拆分和档案冲突解决规则待产品与算法负责人签认。 |
| 25 | POST | `/profiles/{profileId}/conflicts/{conflictId}/resolve` | `resolveProfileConflict` | P-016：跨源关联、人工修订、合并拆分和档案冲突解决规则待产品与算法负责人签认。 |
| 26 | POST | `/profiles/{profileId}/exports` | `exportProfile` | P-016/P-021：档案敏感字段和导出审批策略未签认。 |
| 27 | GET | `/trend-models` | `listTrendModels` | P-017：常驻以外的模型类型与可用参数待算法负责人确认。 |
| 28 | POST | `/trend-models` | `createTrendModel` | P-017：模型类型、输入与有效数据筛选规则待确认。 |
| 29 | GET | `/trend-models/{modelId}` | `getTrendModel` | P-016：跨源关联、人工修订、合并拆分和档案冲突解决规则待产品与算法负责人签认。 |
| 30 | PUT | `/trend-models/{modelId}` | `updateTrendModel` | P-016：跨源关联、人工修订、合并拆分和档案冲突解决规则待产品与算法负责人签认。 |
| 31 | POST | `/trend-models/{modelId}/parameter-versions` | `createModelParameterVersion` | P-017：参数目录、匹配规则与阈值尚未签认。 |
| 32 | GET | `/model-tags` | `listModelTags` | P-017：标签代码和含义待模型负责人确认。 |
| 33 | POST | `/trend-models/{modelId}/runs` | `runTrendModel` | P-017：算法条件、阈值与复核流程尚未签认。 |
| 34 | GET | `/trend-models/{modelId}/results` | `listTrendModelResults` | P-017：结果置信度解释与标签规则尚未签认。 |
| 35 | GET | `/trend-models/{modelId}/results/{resultId}` | `getTrendModelResult` | P-016：跨源关联、人工修订、合并拆分和档案冲突解决规则待产品与算法负责人签认。 |
| 36 | POST | `/devices` | `createDevice` | P-018：设备类型、厂商型号和序列号唯一性规则待设备方确认。 |
| 37 | PUT | `/devices/{deviceId}` | `updateDevice` | P-018：可编辑设备字段和厂商同步语义待确认。 |
| 38 | GET | `/devices/{deviceId}/parameters` | `getDeviceParameters` | P-018/P-019：厂商设备参数、命令协议、审批与确认策略未签认。 |
| 39 | POST | `/devices/{deviceId}/parameter-updates` | `updateDeviceParameters` | P-018/P-019：厂商设备参数、命令协议、审批与确认策略未签认。 |
| 40 | GET | `/devices/{deviceId}/capture-statistics` | `getDeviceCaptureStatistics` | P-006：采集与入库指标的窗口和去重口径待确认。 |
| 41 | POST | `/devices/imports` | `importDevices` | P-018：导入模板和设备型号字段映射待厂商确认。 |
| 42 | POST | `/devices/exports` | `exportDevices` | P-002：设备导出范围与权限策略待安全负责人确认。 |
| 43 | POST | `/device-commands` | `createDeviceCommand` | P-018/P-019：厂商设备参数、命令协议、审批与确认策略未签认。 |
| 44 | POST | `/device-commands/{taskId}/retry` | `retryDeviceCommand` | P-018/P-019：厂商设备参数、命令协议、审批与确认策略未签认。 |
| 45 | POST | `/reports` | `createReport` | P-006/P-020：细分报表字段、指标口径、时间粒度和保留期未逐项签认。 |
| 46 | GET | `/reports/{reportId}/preview` | `getReportPreview` | P-006/P-020：细分报表字段、指标口径、时间粒度和保留期未逐项签认。 |
| 47 | POST | `/users` | `createUser` | P-021：初始密码策略和首次登录要求待安全确认。 |
| 48 | PUT | `/users/{userId}` | `updateUser` | P-002/P-021：范围缩小对会话与在途任务的生效规则待确认。 |
| 49 | POST | `/users/{userId}/disable` | `disableUser` | P-001/P-021：禁用后现有会话和在途任务的处理策略待签认。 |
| 50 | POST | `/users/{userId}/password` | `changeUserPassword` | P-021：用户生命周期、日志保留、脱敏及导出范围待安全负责人签认。 |
| 51 | POST | `/roles` | `createRole` | P-002：完整权限目录和角色默认数据范围待确认。 |
| 52 | PUT | `/roles/{roleId}/permissions` | `setRolePermissions` | P-002：菜单、按钮和接口权限码目录待产品与安全确认。 |
| 53 | PUT | `/roles/{roleId}/scopes` | `setRoleScopes` | P-002：场所与数据范围组合以及权限收缩生效规则待确认。 |
| 54 | POST | `/audit-events/search` | `searchAuditEvents` | P-021：管理员可见范围、日志保留与字段脱敏待确认。 |
| 55 | POST | `/audit-events/export` | `exportAuditEvents` | P-021：用户生命周期、日志保留、脱敏及导出范围待安全负责人签认。 |
| 56 | POST | `/deliveries/search` | `searchSmsLogs` | P-015/P-021：短信通道回执和收件人脱敏规则待确认。 |
| 57 | POST | `/deliveries/export` | `exportSmsLogs` | P-015/P-021：短信日志导出字段、范围与审批规则待确认。 |
| 58 | POST | `/targets/resolve` | `resolveTargets` | P-004/P-016：标识规范化与FACE身份代理规则待确认；接口不跨类型推断同一人。 |
| 59 | POST | `/metrics/exports` | `exportMetrics` | P-006：精确指标公式与数据源待业务签认。 |
| 60 | GET | `/notification-templates` | `listNotificationTemplates` | P-015：模板正文/变量目录须产品确认。 |
| 61 | POST | `/person-libraries/{libraryId}/persons` | `createLibraryPerson` | P-016：登记标识重复/身份冲突的人工处理策略待确认。 |
| 62 | GET | `/profiles/{profileId}/conflicts` | `listProfileConflicts` | P-016：冲突识别与裁决依据尚未签认。 |
| 63 | POST | `/person-libraries/imports` | `importPersonLibraries` | P-016：人员重复判定/身份冲突策略需确认，不自动合并档案。 |
| 64 | POST | `/device-commands/{taskId}/revisions` | `reviseDeviceCommand` | P-018/P-019：参数目录/厂商协议/高风险确认规则需签认。 |
| 65 | POST | `/me/password` | `changeMyPassword` | P-021：密码策略未签认。 |
