# 后端双人分工方案（DEV-01 内部拆分）

面向：`apps/api` 的两位 Spring Boot 后端开发。
输入：[openapi.yaml](<../../contracts/api/openapi.yaml>)（193 操作 / 581 Schema）、[工程师任务与交接边界](<ENGINEERING_ASSIGNMENTS.md>)、[工作包人日估算](<audit/work-packages.json>)、[前端接口交接](<FRONTEND_API_HANDOFF.md>)、[API 速读指南](<../API速读指南.md>)。
本方案只拆分 **DEV-01 内部的两个人**，不改变 DEV-01 对外的责任归属：对 DEV-02/03/04 仍然只有一个交付面。

## 设计判据（按优先级）

1. **并行性**：两人起步时互不阻塞；谁手上的活不依赖对方也能往前推。
2. **边界清晰**：尽量做到"一块只有一个人动"，不出现两人改同一个文件、同一张表。
3. **落地顺手**：同一个人手上的东西结构相似（要么都是编排与横切，要么都是资源与查询），减少来回切换。
4. **不追求绝对平均**：允许 60/40，只要整体不被单点卡住。

---

## 1. 结论

| | **后端甲：平台与运维** | **后端乙：数据与研判** |
| --- | --- | --- |
| 一句话 | 所有请求都要经过的地基 + 场所设备 + 布控通知 + 用户权限 | 接入、检索、地图、分析、数据集、档案模型、看板 |
| 模块（**模块级不重叠**） | M01、M06、M10、M12、M14、M15 | M03、M04、M05、M07、M08、M09、M11、M13 |
| 操作数 | 113 | 80 |
| 可建工作量（剔除 pending） | 108.3（60%） | 72.7（40%） |
| 前端正在真实调用（其中已签认） | 79（53） | 48（41） |
| **依赖 DEV-03 的操作** | **0** | **72** |
| 外部副作用 | 设备命令下发、通知投递与回执、短信/邮件通道 | 无（只有计算与查询） |
| 上游卡点 | 厂商协议 P-018/P-019、认证 P-001 | **DEV-03 的查询/计算模块交付** |

**为什么允许 60/40**：乙的 72 个操作全部要等 DEV-03 交出参数化查询、计算函数与结果投影；DEV-03 没交东西之前，乙只能做"契约里有、前端还没用"的那部分自包含接口。甲手上 0 个 DEV-03 依赖，唯一等待项是厂商协议，所以让甲多背一部分业务操作，整体吞吐更高。这不是"少给乙活"，而是**按上游供给能力配产能**。

**为什么按模块整块切，不按能力拆模块**：模块级不重叠带来三件事——① 迁移脚本、实体、枚举、DTO 各改各的，不冲突；② 代码评审与责任归属清楚；③ 排期时不用反复协商"这个接口算谁的"。代价是边界处（设备↔报表、通知↔送达记录、任务底座↔领域任务）要遵守 §3 的规则。

---

## 2. 逐模块归属

| 模块 | 内容 | 归属 | 操作数 | 可建工作量 | pending | 仅契约 | 依赖 DEV-03 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| M01 | 会话/CSRF、用户角色底座、文件、任务、字典与配置、批量删除撤销 | 甲 | 24 | 27.0 | 2 | 3 | 0 |
| M06 | Context、采集统计、解密申请 | 甲 | 6 | 3.7 | 3 | 2 | 0 |
| M10 | 布控规则、告警、通知计划、送达与消息、模板、批量撤销 | 甲 | 22 | 22.1 | 7 | 8 | 0 |
| M12 | 场所、设备、参数、状态历史、设备分组、设备命令、八类报表 | 甲 | 39 | 40.1 | 12 | 14 | 0 |
| M14 | 用户、角色、权限/菜单授予、操作日志、短信日志、导出 | 甲 | 20 | 13.2 | 11 | 5 | 0 |
| M15 | 健康/就绪探针 | 甲 | 2 | 2.2 | 0 | 2 | 0 |
| M03 | 接入批次、质量汇总、回放、补数 | 乙 | 7 | 5.6 | 3 | 7 | 7 |
| M04 | 系统首页/运维首页聚合、指标导出 | 乙 | 3 | 2.8 | 1 | 1 | 0 |
| M05 | 四类采集检索、人像检索 | 乙 | 4 | 2.4 | 2 | 0 | 4 |
| M07 | 地图要素、热力、区域统计、轨迹 | 乙 | 4 | 7.0 | 0 | 1 | 0 |
| M08 | 伴随/碰撞/区域统计分析任务、结果、导出 | 乙 | 9 | 13.0 | 0 | 2 | 9 |
| M09 | 数据集、成员、版本、导入导出、集合运算 | 乙 | 16 | 22.2 | 0 | 8 | 16 |
| M11 | 人员库、人车档案、证据、修订、合并拆分、趋势模型 | 乙 | 36 | 17.7 | 24 | 13 | 36 |
| M13 | 设备点与聚合（运维可视化） | 乙 | 1 | 2.1 | 0 | 0 | 0 |

> 甲的 113 个与乙的 80 个操作逐条清单见附录 A / 附录 B（含 pending、仅契约、DEV-03 依赖标记），可直接当排期表和交接单用。

**甲的 DEV-03 依赖为 0**：甲的任何接口都不需要等数据侧。**乙的 72 个依赖项里有 41 个前端正在用**——这是全项目最可能卡住联调的地方，所以乙必须主动跟 DEV-03 对齐交付顺序（见 §4、§5）。

---

## 3. 边界处的规则（唯一需要协商的三处）

模块整块切之后，只有三处会跨边界；一次说清，以后不用再谈：

1. **设备 ↔ 报表**：设备实体、`deviceId` 语义、设备参数枚举只有甲能改；乙的报表定义引用设备/场所时只读消费，不改甲的表结构。
2. **通知 ↔ 送达记录**：投递、回执、重试、频控的写路径只有甲能动；乙只查 `/deliveries`、`/messages`（只读）。
3. **任务底座 ↔ 领域任务**：`async_task` 表、状态机、租约协议只有甲能动（这是 H04 交接）；乙创建任务只调甲发布的接口，**不自己写状态推进**。这是最容易两人同时改坏的地方，务必守住。

数据库迁移：**DDL 版本化由甲统一合并发布**，乙把自己的迁移片段交甲合，两人不各自改库（对应 [统一分工规则](<ENGINEERING_ASSIGNMENTS.md>) 第 13 条）。

---

## 4. 时序：让两个人从第 1 周就有活干

```
第 1 周（一起干，只干一次）
  合定并冻结：包结构、统一响应壳、错误码 enum、traceId、分页工具、权限注解、迁移框架
  甲主写 common/ 与 infra/；乙同步写一个自包含接口，验证脚手架可用
  产出：apps/api/README.md 的"共享层契约"一节（接口冻结，之后变更每周批量一次）

第 2～4 周
  甲：会话/CSRF/登录/me/menus/permissions → RBAC 与数据范围（第 3 周前冻结接口）
      接着：任务底座（7 个）+ 文件底座（4 个）+ 幂等 + ETag + 签名游标
  乙（无 DEV-03 依赖，立即可做）：
      M04 看板骨架、M11 人员库 CRUD 与成员增删、M11 档案只读部分、M03 接入批次查询

第 5～8 周
  甲：M12 场所/设备/分组/参数/状态历史（纯 CRUD 起量）+ M14 用户角色审计
  乙：M05 检索、M07 地图与轨迹（接 DEV-03 的 H03 查询模块交付物）

第 9～12 周
  甲：设备命令（厂商协议 + 逐台回执）、通知投递与频控对账、M10 布控与告警
  乙：M08 分析任务、M09 数据集与集合运算、M11 档案（先做已签认的 12 个）

第 13 周起
  P-001/P-016/P-017 签认后各自收口 pending；互相做代码评审与交叉联调
```

**硬门禁**：第 3 周结束前必须跑通一个真实闭环——浏览器（DEV-02）请求 → 真实数据库 → 刷新读回。这是 H06 的交接要求，也是防止两人各写一半对不上的唯一手段。

---

## 5. 并行性保障清单（每月自查）

| 事项 | 做法 |
| --- | --- |
| 共享层不打架 | 只有甲改 `common/`、`infra/`；乙需要新能力提给甲，不 fork 第二份 |
| 迁移不打架 | 甲统一合并 DDL；乙的迁移片段走 PR 给甲合 |
| 实体/枚举不重复 | 设备、任务、文件、用户四类实体只有甲定义；乙引用甲的 model，不复制 |
| 乙不被 DEV-03 卡死 | 乙手上长期保留 5～8 个"无 DEV-03 依赖"的接口（仅契约项 + 纯 CRUD），上游断供也不停工 |
| 甲不被厂商卡死 | 设备命令（P-018/P-019）排最后；协议没定之前甲先做场所/设备/分组/报表 |
| 先冻结再并行 | 共享层接口第 1 周冻结；业务接口先在 Controller 落签名，再补实现 |
| 口径统一 | 统一响应壳、错误码、traceId、分页由甲提供，乙不自造第二套（口径见 [API 速读指南](<../API速读指南.md>) §4/§5） |

---

## 6. 如果甲的进度落后（不重排结构也能调整）

允许 60/40 的前提是甲**能在一周内交出共享层**。如果第 4 周甲还在补地基，按顺序启用下面两个微调，都不破坏模块边界：

1. 把 M04 + M13（4 个操作，4.9 工作量）转给甲——纯 API 包装，甲做更顺。
2. 把 M12 里的报表 7 个（6.9 工作量）转给乙——报表与导出、日志同族，乙接得住。

两项都只改归属表一行，不需要重排代码结构。

---

## 7. 双方确认项

- [ ] 模块级不重叠（§2）是唯一归属口径，不再逐接口协商。
- [ ] 共享层（`common/`、`infra/`）主写人是甲，乙只调用。
- [ ] 任务底座与 `async_task` 表只有甲能动（§3 第 3 条）。
- [ ] 第 3 周真实闭环是硬门禁。
- [ ] 契约变更时，本方案归属表与 [API 速读指南](<../API速读指南.md>) 附录同步（生成脚本在 `local-only/agent/`，可重跑）。

---

## 附录 A：后端甲的操作清单（113 个）

### 后端甲（平台底座 / 场所设备 / 布控通知 / 用户权限）（113 个操作，可建工作量 108.3 / 181.0 = 60%）

| 模块 | 方法 | 路径 | operationId | pending | 仅契约 | 依赖 DEV-03 |
| --- | --- | --- | --- | --- | --- | --- |
| M01 | GET | `/auth/csrf` | `getCsrfToken` |   |   |   |
| M01 | POST | `/auth/login` | `login` |   |   |   |
| M01 | POST | `/auth/logout` | `logout` |   |   |   |
| M01 | POST | `/auth/session/renew` | `renewSession` | ⚠ | 是 |   |
| M01 | POST | `/batch-deletions` | `deleteResourcesBatch` |   |   |   |
| M01 | GET | `/client-config` | `getClientConfig` |   |   |   |
| M01 | GET | `/dictionaries/{dictionaryCode}` | `getDictionary` |   |   |   |
| M01 | POST | `/exports` | `createExport` |   |   |   |
| M01 | GET | `/files/{fileAssetId}` | `getFileAsset` |   |   |   |
| M01 | GET | `/files/{fileAssetId}/download` | `downloadFile` |   |   |   |
| M01 | POST | `/files/uploads` | `uploadFile` |   |   |   |
| M01 | GET | `/import-templates/{templateCode}` | `getImportTemplate` |   |   |   |
| M01 | GET | `/me` | `getMe` |   |   |   |
| M01 | POST | `/me/password` | `changeMyPassword` | ⚠ |   |   |
| M01 | GET | `/menus` | `listMenuCatalog` |   |   |   |
| M01 | GET | `/permissions` | `listPermissionCatalog` |   |   |   |
| M01 | GET | `/regions` | `listRegions` |   | 是 |   |
| M01 | GET | `/tasks` | `listTasks` |   |   |   |
| M01 | GET | `/tasks/{taskId}` | `getTask` |   |   |   |
| M01 | GET | `/tasks/{taskId}/attempts` | `listTaskAttempts` |   | 是 |   |
| M01 | POST | `/tasks/{taskId}/cancel` | `cancelTask` |   |   |   |
| M01 | GET | `/tasks/{taskId}/errors` | `listTaskRowErrors` |   |   |   |
| M01 | GET | `/tasks/{taskId}/import-result` | `getImportTaskResult` |   |   |   |
| M01 | POST | `/tasks/{taskId}/retry` | `retryTask` |   |   |   |
| M06 | POST | `/captures/statistics` | `getCaptureStatistics` |   |   |   |
| M06 | POST | `/contexts` | `createContext` |   |   |   |
| M06 | GET | `/contexts/{contextId}` | `getContext` |   | 是 |   |
| M06 | POST | `/decrypt-requests` | `requestDecryption` | ⚠ |   |   |
| M06 | GET | `/decrypt-requests/{requestId}` | `getDecryptionRequest` | ⚠ | 是 |   |
| M06 | POST | `/targets/resolve` | `resolveTargets` | ⚠ |   |   |
| M10 | GET | `/alerts` | `listAlerts` |   |   |   |
| M10 | GET | `/alerts/{alertId}` | `getAlert` |   | 是 |   |
| M10 | POST | `/batch-revocations` | `revokeResourcesBatch` |   |   |   |
| M10 | GET | `/deliveries` | `listDeliveries` |   | 是 |   |
| M10 | GET | `/deliveries/{deliveryId}` | `getDelivery` |   | 是 |   |
| M10 | GET | `/messages` | `listPlatformMessages` |   |   |   |
| M10 | GET | `/notification-tasks` | `listNotificationTasks` |   |   |   |
| M10 | POST | `/notification-tasks` | `createNotificationTask` | ⚠ |   |   |
| M10 | GET | `/notification-tasks/{notificationTaskId}` | `getNotificationTask` |   |   |   |
| M10 | PUT | `/notification-tasks/{notificationTaskId}` | `updateNotificationTask` | ⚠ |   |   |
| M10 | DELETE | `/notification-tasks/{notificationTaskId}` | `deleteNotificationTask` |   | 是 |   |
| M10 | POST | `/notification-tasks/{notificationTaskId}/cancel` | `cancelNotificationTask` |   | 是 |   |
| M10 | POST | `/notification-tasks/{notificationTaskId}/retry` | `retryNotificationTask` | ⚠ | 是 |   |
| M10 | GET | `/notification-templates` | `listNotificationTemplates` | ⚠ |   |   |
| M10 | GET | `/watch-rules` | `listWatchRules` |   |   |   |
| M10 | POST | `/watch-rules` | `createWatchRule` | ⚠ |   |   |
| M10 | GET | `/watch-rules/{ruleId}` | `getWatchRule` |   |   |   |
| M10 | PATCH | `/watch-rules/{ruleId}` | `updateWatchRule` | ⚠ |   |   |
| M10 | DELETE | `/watch-rules/{ruleId}` | `deleteWatchRule` |   | 是 |   |
| M10 | POST | `/watch-rules/{ruleId}/activate` | `activateWatchRule` | ⚠ |   |   |
| M10 | POST | `/watch-rules/{ruleId}/revoke` | `revokeWatchRule` |   | 是 |   |
| M10 | GET | `/watch-rules/{ruleId}/statistics` | `getWatchStatistics` |   |   |   |
| M12 | GET | `/device-commands` | `listDeviceCommands` |   |   |   |
| M12 | POST | `/device-commands` | `createDeviceCommand` | ⚠ |   |   |
| M12 | GET | `/device-commands/{taskId}` | `getDeviceCommand` |   |   |   |
| M12 | DELETE | `/device-commands/{taskId}` | `deleteDeviceCommand` |   | 是 |   |
| M12 | GET | `/device-commands/{taskId}/results` | `listDeviceCommandResults` |   | 是 |   |
| M12 | POST | `/device-commands/{taskId}/retry` | `retryDeviceCommand` | ⚠ | 是 |   |
| M12 | POST | `/device-commands/{taskId}/revisions` | `reviseDeviceCommand` | ⚠ |   |   |
| M12 | GET | `/device-groups` | `listDeviceGroups` |   |   |   |
| M12 | POST | `/device-groups` | `createDeviceGroup` |   | 是 |   |
| M12 | GET | `/device-groups/{groupId}` | `getDeviceGroup` |   | 是 |   |
| M12 | PUT | `/device-groups/{groupId}` | `updateDeviceGroup` |   | 是 |   |
| M12 | DELETE | `/device-groups/{groupId}` | `deleteDeviceGroup` |   | 是 |   |
| M12 | GET | `/device-groups/{groupId}/members` | `listDeviceGroupMembers` |   |   |   |
| M12 | POST | `/device-groups/{groupId}/members` | `addDeviceGroupMembers` |   | 是 |   |
| M12 | DELETE | `/device-groups/{groupId}/members/{deviceId}` | `removeDeviceGroupMember` |   | 是 |   |
| M12 | GET | `/devices` | `listDevices` |   |   |   |
| M12 | POST | `/devices` | `createDevice` | ⚠ |   |   |
| M12 | GET | `/devices/{deviceId}` | `getDevice` |   |   |   |
| M12 | PUT | `/devices/{deviceId}` | `updateDevice` | ⚠ |   |   |
| M12 | DELETE | `/devices/{deviceId}` | `deleteDevice` |   | 是 |   |
| M12 | GET | `/devices/{deviceId}/capture-statistics` | `getDeviceCaptureStatistics` | ⚠ |   |   |
| M12 | POST | `/devices/{deviceId}/parameter-updates` | `updateDeviceParameters` | ⚠ |   |   |
| M12 | GET | `/devices/{deviceId}/parameters` | `getDeviceParameters` | ⚠ |   |   |
| M12 | GET | `/devices/{deviceId}/status` | `getDeviceStatus` |   | 是 |   |
| M12 | GET | `/devices/{deviceId}/status-history` | `listDeviceStatusHistory` |   |   |   |
| M12 | POST | `/devices/exports` | `exportDevices` | ⚠ |   |   |
| M12 | POST | `/devices/imports` | `importDevices` | ⚠ |   |   |
| M12 | GET | `/reports` | `listReports` |   |   |   |
| M12 | POST | `/reports` | `createReport` | ⚠ |   |   |
| M12 | GET | `/reports/{reportId}` | `getReport` |   |   |   |
| M12 | DELETE | `/reports/{reportId}` | `deleteReport` |   | 是 |   |
| M12 | GET | `/reports/{reportId}/download` | `downloadReport` |   |   |   |
| M12 | GET | `/reports/{reportId}/preview` | `getReportPreview` | ⚠ |   |   |
| M12 | POST | `/reports/{reportId}/retry` | `retryReport` |   | 是 |   |
| M12 | GET | `/sites` | `listSites` |   |   |   |
| M12 | POST | `/sites` | `createSite` |   |   |   |
| M12 | GET | `/sites/{siteId}` | `getSite` |   |   |   |
| M12 | PUT | `/sites/{siteId}` | `updateSite` |   |   |   |
| M12 | DELETE | `/sites/{siteId}` | `deleteSite` |   | 是 |   |
| M14 | GET | `/audit-events/{eventId}` | `getAuditEvent` |   | 是 |   |
| M14 | POST | `/audit-events/export` | `exportAuditEvents` | ⚠ |   |   |
| M14 | POST | `/audit-events/search` | `searchAuditEvents` | ⚠ |   |   |
| M14 | POST | `/deliveries/export` | `exportSmsLogs` | ⚠ |   |   |
| M14 | POST | `/deliveries/search` | `searchSmsLogs` | ⚠ |   |   |
| M14 | GET | `/roles` | `listRoles` |   |   |   |
| M14 | POST | `/roles` | `createRole` | ⚠ |   |   |
| M14 | GET | `/roles/{roleId}` | `getRole` |   |   |   |
| M14 | PUT | `/roles/{roleId}` | `updateRole` |   |   |   |
| M14 | DELETE | `/roles/{roleId}` | `deleteRole` |   | 是 |   |
| M14 | PUT | `/roles/{roleId}/permissions` | `setRolePermissions` | ⚠ | 是 |   |
| M14 | PUT | `/roles/{roleId}/scopes` | `setRoleScopes` | ⚠ | 是 |   |
| M14 | GET | `/users` | `listUsers` |   |   |   |
| M14 | POST | `/users` | `createUser` | ⚠ |   |   |
| M14 | GET | `/users/{userId}` | `getUser` |   |   |   |
| M14 | PUT | `/users/{userId}` | `updateUser` | ⚠ |   |   |
| M14 | DELETE | `/users/{userId}` | `deleteUser` |   | 是 |   |
| M14 | POST | `/users/{userId}/disable` | `disableUser` | ⚠ |   |   |
| M14 | POST | `/users/{userId}/enable` | `enableUser` |   |   |   |
| M14 | POST | `/users/{userId}/password` | `changeUserPassword` | ⚠ |   |   |
| M15 | GET | `/health` | `getHealth` |   | 是 |   |
| M15 | GET | `/ready` | `getReadiness` |   | 是 |   |

## 附录 B：后端乙的操作清单（80 个）

### 后端乙（接入检索 / 地图轨迹 / 研判分析 / 数据集 / 档案模型 / 看板）（80 个操作，可建工作量 72.7 / 181.0 = 40%）

| 模块 | 方法 | 路径 | operationId | pending | 仅契约 | 依赖 DEV-03 |
| --- | --- | --- | --- | --- | --- | --- |
| M03 | GET | `/ingestion-batches` | `listIngestionBatches` |   | 是 | 是 |
| M03 | POST | `/ingestion-batches` | `createIngestionBatch` | ⚠ | 是 | 是 |
| M03 | GET | `/ingestion-batches/{batchId}` | `getIngestionBatch` |   | 是 | 是 |
| M03 | GET | `/ingestion-batches/{batchId}/errors` | `listIngestionErrors` |   | 是 | 是 |
| M03 | POST | `/ingestion-batches/{batchId}/replay` | `replayIngestionBatch` | ⚠ | 是 | 是 |
| M03 | POST | `/ingestion/backfills` | `createIngestionBackfill` | ⚠ | 是 | 是 |
| M03 | GET | `/ingestion/quality-summary` | `getIngestionQualitySummary` |   | 是 | 是 |
| M04 | GET | `/dashboard/summary` | `getDashboardSummary` |   |   |   |
| M04 | POST | `/metrics/exports` | `exportMetrics` | ⚠ | 是 |   |
| M04 | GET | `/operations/summary` | `getOperationSummary` |   |   |   |
| M05 | GET | `/captures/{captureId}` | `getCapture` |   |   | 是 |
| M05 | POST | `/captures/search` | `searchCaptures` |   |   | 是 |
| M05 | POST | `/faces/search-tasks` | `createFaceSearchTask` | ⚠ |   | 是 |
| M05 | GET | `/faces/search-tasks/{taskId}/results` | `listFaceSearchResults` | ⚠ |   | 是 |
| M07 | POST | `/map/area-summary` | `getMapAreaSummary` |   |   |   |
| M07 | GET | `/map/features` | `getMapFeatures` |   | 是 |   |
| M07 | GET | `/map/heatmap` | `getMapHeatmap` |   |   |   |
| M07 | POST | `/trajectories/query` | `queryTrajectory` |   |   |   |
| M08 | GET | `/analysis-tasks` | `listAnalysisTasks` |   |   | 是 |
| M08 | POST | `/analysis-tasks` | `createAnalysisTask` |   |   | 是 |
| M08 | GET | `/analysis-tasks/{analysisTaskId}` | `getAnalysisTask` |   |   | 是 |
| M08 | DELETE | `/analysis-tasks/{analysisTaskId}` | `deleteAnalysisTask` |   | 是 | 是 |
| M08 | GET | `/analysis-tasks/{analysisTaskId}/results` | `listAnalysisResults` |   |   | 是 |
| M08 | GET | `/analysis-tasks/{analysisTaskId}/results/{resultId}` | `getAnalysisResult` |   | 是 | 是 |
| M08 | POST | `/analysis-tasks/{analysisTaskId}/results/export` | `exportAnalysisResults` |   |   | 是 |
| M08 | POST | `/analysis-tasks/{analysisTaskId}/revisions` | `reviseAnalysisTask` |   |   | 是 |
| M08 | POST | `/analysis-tasks/exports` | `exportAnalysisTasks` |   |   | 是 |
| M09 | GET | `/datasets` | `listDatasets` |   |   | 是 |
| M09 | POST | `/datasets` | `createDataset` |   |   | 是 |
| M09 | GET | `/datasets/{datasetId}` | `getDataset` |   | 是 | 是 |
| M09 | PATCH | `/datasets/{datasetId}` | `updateDataset` |   | 是 | 是 |
| M09 | DELETE | `/datasets/{datasetId}` | `deleteDataset` |   | 是 | 是 |
| M09 | POST | `/datasets/{datasetId}/export` | `exportDataset` |   |   | 是 |
| M09 | POST | `/datasets/{datasetId}/imports` | `importDatasetItems` |   |   | 是 |
| M09 | GET | `/datasets/{datasetId}/items` | `listDatasetItems` |   |   | 是 |
| M09 | POST | `/datasets/{datasetId}/items` | `addDatasetItems` |   | 是 | 是 |
| M09 | DELETE | `/datasets/{datasetId}/items/{itemId}` | `removeDatasetItem` |   | 是 | 是 |
| M09 | GET | `/datasets/{datasetId}/versions` | `listDatasetVersions` |   | 是 | 是 |
| M09 | GET | `/set-tasks` | `listSetTasks` |   | 是 | 是 |
| M09 | POST | `/set-tasks` | `createSetTask` |   |   | 是 |
| M09 | GET | `/set-tasks/{setTaskId}` | `getSetTask` |   |   | 是 |
| M09 | GET | `/set-tasks/{setTaskId}/results` | `listSetTaskResults` |   | 是 | 是 |
| M09 | POST | `/set-tasks/preview` | `previewSetTask` |   |   | 是 |
| M11 | GET | `/model-tags` | `listModelTags` | ⚠ | 是 | 是 |
| M11 | GET | `/person-libraries` | `listPersonLibraries` |   |   | 是 |
| M11 | POST | `/person-libraries` | `createPersonLibrary` |   |   | 是 |
| M11 | GET | `/person-libraries/{libraryId}` | `getPersonLibrary` |   |   | 是 |
| M11 | PUT | `/person-libraries/{libraryId}` | `updatePersonLibrary` |   |   | 是 |
| M11 | DELETE | `/person-libraries/{libraryId}` | `deletePersonLibrary` |   | 是 | 是 |
| M11 | POST | `/person-libraries/{libraryId}/exports` | `exportPersonLibrary` | ⚠ | 是 | 是 |
| M11 | POST | `/person-libraries/{libraryId}/imports` | `importPersonLibraryMembers` | ⚠ |   | 是 |
| M11 | GET | `/person-libraries/{libraryId}/members` | `listPersonLibraryMembers` |   |   | 是 |
| M11 | POST | `/person-libraries/{libraryId}/members` | `addPersonLibraryMembers` | ⚠ | 是 | 是 |
| M11 | DELETE | `/person-libraries/{libraryId}/members/{memberId}` | `removePersonLibraryMember` |   |   | 是 |
| M11 | POST | `/person-libraries/{libraryId}/persons` | `createLibraryPerson` | ⚠ |   | 是 |
| M11 | POST | `/person-libraries/exports` | `exportPersonLibraries` |   |   | 是 |
| M11 | POST | `/person-libraries/imports` | `importPersonLibraries` | ⚠ |   | 是 |
| M11 | GET | `/profiles/{profileId}` | `getProfile` | ⚠ |   | 是 |
| M11 | GET | `/profiles/{profileId}/conflicts` | `listProfileConflicts` | ⚠ | 是 | 是 |
| M11 | POST | `/profiles/{profileId}/conflicts/{conflictId}/resolve` | `resolveProfileConflict` | ⚠ | 是 | 是 |
| M11 | POST | `/profiles/{profileId}/corrections` | `createProfileCorrection` | ⚠ |   | 是 |
| M11 | GET | `/profiles/{profileId}/evidence` | `listProfileEvidence` |   |   | 是 |
| M11 | POST | `/profiles/{profileId}/exports` | `exportProfile` | ⚠ | 是 | 是 |
| M11 | GET | `/profiles/{profileId}/history` | `listProfileHistory` |   | 是 | 是 |
| M11 | POST | `/profiles/{profileId}/split` | `splitProfile` | ⚠ | 是 | 是 |
| M11 | GET | `/profiles/{profileId}/targets` | `listProfileTargets` | ⚠ |   | 是 |
| M11 | GET | `/profiles/{profileId}/trajectories` | `listProfileTrajectories` | ⚠ |   | 是 |
| M11 | POST | `/profiles/merge` | `mergeProfiles` | ⚠ | 是 | 是 |
| M11 | POST | `/profiles/search` | `searchProfiles` | ⚠ |   | 是 |
| M11 | GET | `/trend-models` | `listTrendModels` | ⚠ |   | 是 |
| M11 | POST | `/trend-models` | `createTrendModel` | ⚠ |   | 是 |
| M11 | GET | `/trend-models/{modelId}` | `getTrendModel` | ⚠ |   | 是 |
| M11 | PUT | `/trend-models/{modelId}` | `updateTrendModel` | ⚠ |   | 是 |
| M11 | GET | `/trend-models/{modelId}/parameter-versions` | `listModelParameterVersions` |   | 是 | 是 |
| M11 | POST | `/trend-models/{modelId}/parameter-versions` | `createModelParameterVersion` | ⚠ |   | 是 |
| M11 | GET | `/trend-models/{modelId}/parameter-versions/{parameterVersionId}` | `getModelParameterVersion` |   |   | 是 |
| M11 | GET | `/trend-models/{modelId}/results` | `listTrendModelResults` | ⚠ |   | 是 |
| M11 | GET | `/trend-models/{modelId}/results/{resultId}` | `getTrendModelResult` | ⚠ | 是 | 是 |
| M11 | POST | `/trend-models/{modelId}/runs` | `runTrendModel` | ⚠ | 是 | 是 |
| M13 | GET | `/map/devices` | `getDeviceMapFeatures` |   |   |   |

## 附录 C：参考文件

- 契约：[openapi.yaml](<../../contracts/api/openapi.yaml>)、[API 速读指南](<../API速读指南.md>)、[接口约定](<../../contracts/api/api-conventions.md>)、[错误码](<../../contracts/api/error-codes.md>)
- 治理：[工程师任务与交接边界](<ENGINEERING_ASSIGNMENTS.md>)、[前端接口交接](<FRONTEND_API_HANDOFF.md>)、[开发计划](<DEVELOPMENT_PLAN.md>)、[未决事项登记](<PENDING_REGISTER.md>)
- 证据：[工作包人日](<audit/work-packages.json>)、[逐操作盘点](<audit/api-inventory.json>)、[66 项仅契约操作](<audit/contract-only-operations.md>)
