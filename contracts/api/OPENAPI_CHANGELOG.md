# OpenAPI 变更记录

## 1.0.0-dev.2（2026-10-07，真实开发交付复审）

基线dev.1：134 paths/173 operations/482 schemas；当前152/193/581。初审28+二审3问题，修复30，H12外部待签认保留。最新PDF优先；修改前审计与完整验证见openapi-audit-report。

### 新增20个操作

| operationId | Method/API | 来源 |
| --- | --- | --- |
| changeMyPassword | POST /me/password | SD04/SD06/SD08；原型应用壳/公共任务 |
| createLibraryPerson | POST /person-libraries/{libraryId}/persons | PDF p9/16-17；libraryPage/profilePage/modelPage；工程M11 |
| deleteDeviceCommand | DELETE /device-commands/{taskId} | PDF p18-23；adminPage/configPage/reportPage；工程M12 |
| deleteResourcesBatch | POST /batch-deletions | SD04/SD06/SD08；原型应用壳/公共任务 |
| exportAnalysisTasks | POST /analysis-tasks/exports | PDF p10-14；analysisForm/taskPage |
| exportMetrics | POST /metrics/exports | PDF p1-2/17-18；dashboard/opsPage |
| exportPersonLibraries | POST /person-libraries/exports | PDF p9/16-17；libraryPage/profilePage/modelPage；工程M11 |
| getImportTaskResult | GET /tasks/{taskId}/import-result | SD04 task result；PDF p16/19 导入后资源衔接 |
| getImportTemplate | GET /import-templates/{templateCode} | SD04/SD06/SD08；原型应用壳/公共任务 |
| importPersonLibraries | POST /person-libraries/imports | PDF p9/16-17；libraryPage/profilePage/modelPage；工程M11 |
| listMenuCatalog | GET /menus | SD04/SD06/SD08；原型应用壳/公共任务 |
| listNotificationTemplates | GET /notification-templates | PDF p15/23-24/26；watchPage/notificationPage |
| listPermissionCatalog | GET /permissions | SD04/SD06/SD08；原型应用壳/公共任务 |
| listPlatformMessages | GET /messages | PDF p15/23-24/26；watchPage/notificationPage |
| listProfileConflicts | GET /profiles/{profileId}/conflicts | PDF p9/16-17；libraryPage/profilePage/modelPage；工程M11 |
| listRegions | GET /regions | SD04/SD06/SD08；原型应用壳/公共任务 |
| resolveTargets | POST /targets/resolve | PDF p2-7；通用动作/dataAction |
| reviseDeviceCommand | POST /device-commands/{taskId}/revisions | PDF p18-23；adminPage/configPage/reportPage；工程M12 |
| revokeResourcesBatch | POST /batch-revocations | PDF p15/p24；原型revoke-watch/revoke-notification |
| updateNotificationTask | PUT /notification-tasks/{notificationTaskId} | PDF p15/23-24/26；watchPage/notificationPage |

### 迁移/合并/行为变化

- removeDatasetItem路径targetId→itemId；operationId保留，生成SDK需要更新。
- 通知计划创建201/取消200；schedule改ONCE/DAILY，计划和Task状态分开；recipientRef与notificationTemplateCode改明确输入/快照/RETAINED。
- 集合改operands+version+显式expression，数据集新增elementType；不跨CAPTURE/TARGET运算。
- 重复统计不创建新口径；M13复用M04/M07/M12，页面批量动作统一接口。
- 常驻模型移除无原文依据TRAJECTORY/FREQUENCY；首页两旧指标保留deprecated兼容字段；ClientConfig旧featureAvailability投影features，不能独立计算。
- 更新响应强类型/判别/错误/状态/分页/ETag/幂等/导入模板和全部Mock例；维持API /api/v1，开发阶段字段变化需双方同步生成。
- Java名称映射setTaskId=setOperationId只改Java方法；pattern/discriminator修复真实编译问题。
- 唯一正式输入openapi.yaml；历史副本保留，README明确排除。

### 验证与未决

YAML语义、官方验证、1,837示例、182静态链路/负例、TS strict编译、Spring Maven编译、Swagger193操作展示均通过。CommonCaptureRecord统一基础模型有一个未引用建议；保留业务定义。全部生产独立开发NO，不能将未签认规则模拟为已实现。

## 历史记录（原文保留）

# OpenAPI 契约变更记录

本记录比较原始设计骨架与 `1.0.0-dev.1` 开发级契约。覆盖矩阵和待确认事项分别见 `OPENAPI_COVERAGE.md`、`OPENAPI_PENDING.md`。本次是契约升级，尚未宣称后端已实现这些接口。

## 规模与原则

- 路径：38 → 134；HTTP 操作：45 → 173；Schema：22 → 482。
- 新增 132 个方法+路径组合，保留并修订 41 个，替换 4 个旧组合。
- 所有成功 JSON 响应采用 `code/message/data/traceId`，其中 `data` 指向具体 Schema；错误响应采用稳定的 `code/message/traceId/details`。此处有意把失败体与成功体分开，替代系统设计早期 `data: null` 的统一占位写法，避免错误码和错误细节在代码生成中退化为任意对象。
- 统一 `/api/v1`、Cookie Session/CSRF、PermissionCode、ID UUID、RFC 3339 带时区、WGS84 GeoJSON、管理型 page 与海量 cursor 分页、受限 sortBy/sortOrder、异步 TaskStatus 和幂等键。认证方案、权限目录及算法/设备/通知细节仍标为 pending。
- M02 前端交互和 M15 CI/CD、容器、备份、压测等工程能力按覆盖矩阵标为 `NO_API_REQUIRED`，未虚构业务 REST。

## 替换与迁移的路径

| 原操作 | 新操作 | 原因 |
| --- | --- | --- |
| `GET /profiles` | `POST /profiles/search` | 身份/目标搜索条件可能敏感，放 JSON 请求体并明确游标分页；详情和关联资源独立提供。 |
| `GET /models` | `GET /trend-models` | 将趋势模型与参数版本、标签、执行及结果资源归为一组，避免与算法内部模型混淆。 |
| `POST /report-tasks` | `POST /reports` | 报表成为有列表、详情、预览、重试、删除及下载的异步资源；返回 taskId。 |
| `GET /audit-events` | `POST /audit-events/search` | 用户名、目标和时间等日志检索条件按受控请求体传输，导出单独授权。 |

上述旧操作已从新契约移除；前端 Client 与 Spring Controller 应按新 operationId 更新。其它保留路径虽未改名，请求、响应和错误结构均已强类型化，不能视为线兼容。

## 新增操作清单

### M01（9）

- `GET /auth/csrf` — `getCsrfToken`：获取 CSRF 令牌。
- `POST /auth/session/renew` — `renewSession`：续期当前 Cookie 会话。
- `GET /client-config` — `getClientConfig`：读取非机密前端配置。
- `GET /dictionaries/{dictionaryCode}` — `getDictionary`：读取前端字典。
- `GET /files/{fileAssetId}` — `getFileAsset`：查询文件处理状态。
- `GET /tasks` — `listTasks`：查询当前可见任务。
- `GET /tasks/{taskId}/attempts` — `listTaskAttempts`：查看任务尝试历史。
- `GET /tasks/{taskId}/errors` — `listTaskRowErrors`：分页查看文件导入的逐行错误。
- `POST /tasks/{taskId}/retry` — `retryTask`：重试失败任务并保留 taskId。

### M03（6）

- `POST /ingestion-batches` — `createIngestionBatch`：提交文件接入批次。
- `GET /ingestion-batches/{batchId}` — `getIngestionBatch`：查看接入批次详情。
- `GET /ingestion-batches/{batchId}/errors` — `listIngestionErrors`：查看逐条接入错误。
- `POST /ingestion-batches/{batchId}/replay` — `replayIngestionBatch`：按版本回放批次。
- `POST /ingestion/backfills` — `createIngestionBackfill`：创建补数任务。
- `GET /ingestion/quality-summary` — `getIngestionQualitySummary`：查看接入质量汇总。

### M05（2）

- `GET /captures/{captureId}` — `getCapture`：查看采集记录详情。
- `GET /faces/search-tasks/{taskId}/results` — `listFaceSearchResults`：分页读取人像候选。

### M06（2）

- `POST /captures/statistics` — `getCaptureStatistics`：统计上下文内目标。
- `GET /decrypt-requests/{requestId}` — `getDecryptionRequest`：查询解密申请状态。

### M07（2）

- `POST /map/area-summary` — `getMapAreaSummary`：统计选区内采集与设备。
- `GET /map/heatmap` — `getMapHeatmap`：按时间窗读取设备采集热力。

### M08（7）

- `GET /analysis-tasks` — `listAnalysisTasks`：按名称、类型和日期查询分析任务。
- `DELETE /analysis-tasks/{analysisTaskId}` — `deleteAnalysisTask`：软删终态分析任务。
- `GET /analysis-tasks/{analysisTaskId}` — `getAnalysisTask`：查看分析任务与参数快照。
- `GET /analysis-tasks/{analysisTaskId}/results` — `listAnalysisResults`：分页查看分析结果。
- `POST /analysis-tasks/{analysisTaskId}/results/export` — `exportAnalysisResults`：异步导出分析结果。
- `GET /analysis-tasks/{analysisTaskId}/results/{resultId}` — `getAnalysisResult`：查看分析结果及证据引用。
- `POST /analysis-tasks/{analysisTaskId}/revisions` — `reviseAnalysisTask`：以新版本重建分析任务。

### M09（13）

- `DELETE /datasets/{datasetId}` — `deleteDataset`：受引用保护地软删数据集。
- `GET /datasets/{datasetId}` — `getDataset`：查看数据集详情。
- `PATCH /datasets/{datasetId}` — `updateDataset`：重命名数据集。
- `POST /datasets/{datasetId}/export` — `exportDataset`：异步导出数据集。
- `POST /datasets/{datasetId}/imports` — `importDatasetItems`：从查询上下文或文件导入成员。
- `GET /datasets/{datasetId}/items` — `listDatasetItems`：游标读取数据集成员。
- `POST /datasets/{datasetId}/items` — `addDatasetItems`：批量添加数据集成员。
- `DELETE /datasets/{datasetId}/items/{targetId}` — `removeDatasetItem`：移除数据集成员。
- `GET /datasets/{datasetId}/versions` — `listDatasetVersions`：查看数据集版本。
- `GET /set-tasks` — `listSetTasks`：查询集合运算任务。
- `POST /set-tasks/preview` — `previewSetTask`：预览集合运算兼容性与估量。
- `GET /set-tasks/{setTaskId}` — `getSetTask`：查看集合任务详情。
- `GET /set-tasks/{setTaskId}/results` — `listSetTaskResults`：游标查看集合结果。

### M10（11）

- `GET /alerts/{alertId}` — `getAlert`：查看告警、源采集、规则快照与送达。
- `GET /deliveries/{deliveryId}` — `getDelivery`：查看送达状态与失败原因。
- `DELETE /notification-tasks/{notificationTaskId}` — `deleteNotificationTask`：软删已终结通知任务。
- `GET /notification-tasks/{notificationTaskId}` — `getNotificationTask`：查看通知任务详情。
- `POST /notification-tasks/{notificationTaskId}/cancel` — `cancelNotificationTask`：请求取消未完成通知。
- `POST /notification-tasks/{notificationTaskId}/retry` — `retryNotificationTask`：重试符合条件的失败通知。
- `DELETE /watch-rules/{ruleId}` — `deleteWatchRule`：软删终态布控规则。
- `GET /watch-rules/{ruleId}` — `getWatchRule`：查看规则详情和当前版本。
- `PATCH /watch-rules/{ruleId}` — `updateWatchRule`：修改规则并生成新版本。
- `POST /watch-rules/{ruleId}/activate` — `activateWatchRule`：激活布控草稿。
- `GET /watch-rules/{ruleId}/statistics` — `getWatchStatistics`：查看实时命中数或最终统计。

### M11（30）

- `GET /model-tags` — `listModelTags`：查询模型标签目录。
- `DELETE /person-libraries/{libraryId}` — `deletePersonLibrary`：删除人员库。
- `GET /person-libraries/{libraryId}` — `getPersonLibrary`：查看人员库详情。
- `PUT /person-libraries/{libraryId}` — `updatePersonLibrary`：编辑人员库。
- `POST /person-libraries/{libraryId}/exports` — `exportPersonLibrary`：导出人员库。
- `POST /person-libraries/{libraryId}/imports` — `importPersonLibraryMembers`：从文件导入人员库成员。
- `GET /person-libraries/{libraryId}/members` — `listPersonLibraryMembers`：查询人员库成员。
- `POST /person-libraries/{libraryId}/members` — `addPersonLibraryMembers`：添加人员库成员。
- `DELETE /person-libraries/{libraryId}/members/{memberId}` — `removePersonLibraryMember`：移除人员库成员。
- `POST /profiles/merge` — `mergeProfiles`：合并档案。
- `POST /profiles/search` — `searchProfiles`：检索人车档案。
- `GET /profiles/{profileId}` — `getProfile`：查看档案详情。
- `POST /profiles/{profileId}/conflicts/{conflictId}/resolve` — `resolveProfileConflict`：处理档案证据冲突。
- `POST /profiles/{profileId}/corrections` — `createProfileCorrection`：人工修订档案。
- `GET /profiles/{profileId}/evidence` — `listProfileEvidence`：查看档案证据。
- `POST /profiles/{profileId}/exports` — `exportProfile`：导出档案。
- `GET /profiles/{profileId}/history` — `listProfileHistory`：查看档案修订历史。
- `POST /profiles/{profileId}/split` — `splitProfile`：拆分档案。
- `GET /profiles/{profileId}/targets` — `listProfileTargets`：查看档案关联目标。
- `GET /profiles/{profileId}/trajectories` — `listProfileTrajectories`：查看档案轨迹。
- `GET /trend-models` — `listTrendModels`：查询趋势模型。
- `POST /trend-models` — `createTrendModel`：新建趋势模型。
- `GET /trend-models/{modelId}` — `getTrendModel`：查看趋势模型详情。
- `PUT /trend-models/{modelId}` — `updateTrendModel`：编辑趋势模型。
- `GET /trend-models/{modelId}/parameter-versions` — `listModelParameterVersions`：查询模型参数版本。
- `POST /trend-models/{modelId}/parameter-versions` — `createModelParameterVersion`：新增模型参数版本。
- `GET /trend-models/{modelId}/parameter-versions/{parameterVersionId}` — `getModelParameterVersion`：查看模型参数版本。
- `GET /trend-models/{modelId}/results` — `listTrendModelResults`：查看模型执行结果。
- `GET /trend-models/{modelId}/results/{resultId}` — `getTrendModelResult`：查看模型结果详情。
- `POST /trend-models/{modelId}/runs` — `runTrendModel`：执行趋势模型。

### M12（33）

- `GET /device-commands` — `listDeviceCommands`：查询设备命令任务。
- `GET /device-commands/{taskId}` — `getDeviceCommand`：查看设备命令任务。
- `GET /device-commands/{taskId}/results` — `listDeviceCommandResults`：查看逐设备执行结果。
- `POST /device-commands/{taskId}/retry` — `retryDeviceCommand`：重试失败的设备命令。
- `GET /device-groups` — `listDeviceGroups`：查询设备分组。
- `POST /device-groups` — `createDeviceGroup`：新建设备分组。
- `DELETE /device-groups/{groupId}` — `deleteDeviceGroup`：删除设备分组。
- `GET /device-groups/{groupId}` — `getDeviceGroup`：查看设备分组详情。
- `PUT /device-groups/{groupId}` — `updateDeviceGroup`：编辑设备分组。
- `GET /device-groups/{groupId}/members` — `listDeviceGroupMembers`：查询分组设备。
- `POST /device-groups/{groupId}/members` — `addDeviceGroupMembers`：添加分组设备。
- `DELETE /device-groups/{groupId}/members/{deviceId}` — `removeDeviceGroupMember`：移除分组设备。
- `POST /devices` — `createDevice`：新增设备。
- `POST /devices/exports` — `exportDevices`：导出设备清单。
- `POST /devices/imports` — `importDevices`：批量导入设备。
- `DELETE /devices/{deviceId}` — `deleteDevice`：删除设备。
- `GET /devices/{deviceId}` — `getDevice`：查看设备详情。
- `PUT /devices/{deviceId}` — `updateDevice`：编辑设备信息。
- `GET /devices/{deviceId}/capture-statistics` — `getDeviceCaptureStatistics`：查看设备采集历史。
- `POST /devices/{deviceId}/parameter-updates` — `updateDeviceParameters`：提交设备参数更新。
- `GET /devices/{deviceId}/parameters` — `getDeviceParameters`：读取设备参数。
- `GET /devices/{deviceId}/status` — `getDeviceStatus`：查看设备实时状态。
- `GET /devices/{deviceId}/status-history` — `listDeviceStatusHistory`：查看设备在线离线历史。
- `GET /reports` — `listReports`：查询报表任务。
- `POST /reports` — `createReport`：创建报表任务。
- `DELETE /reports/{reportId}` — `deleteReport`：删除报表任务。
- `GET /reports/{reportId}` — `getReport`：查看报表详情。
- `GET /reports/{reportId}/download` — `downloadReport`：下载报表文件。
- `GET /reports/{reportId}/preview` — `getReportPreview`：预览单报表。
- `POST /reports/{reportId}/retry` — `retryReport`：重试失败报表。
- `DELETE /sites/{siteId}` — `deleteSite`：删除场所。
- `GET /sites/{siteId}` — `getSite`：查看场所详情。
- `PUT /sites/{siteId}` — `updateSite`：编辑场所。

### M14（16）

- `POST /audit-events/export` — `exportAuditEvents`：导出操作日志。
- `POST /audit-events/search` — `searchAuditEvents`：查询操作日志。
- `GET /audit-events/{eventId}` — `getAuditEvent`：查看操作日志详情。
- `POST /deliveries/export` — `exportSmsLogs`：导出短信送达日志。
- `POST /deliveries/search` — `searchSmsLogs`：查询短信送达日志。
- `DELETE /roles/{roleId}` — `deleteRole`：删除未被引用的角色。
- `GET /roles/{roleId}` — `getRole`：查看角色详情。
- `PUT /roles/{roleId}` — `updateRole`：编辑角色基本信息。
- `PUT /roles/{roleId}/permissions` — `setRolePermissions`：设置角色菜单和按钮权限。
- `PUT /roles/{roleId}/scopes` — `setRoleScopes`：设置角色场所和数据范围。
- `DELETE /users/{userId}` — `deleteUser`：删除已禁用用户。
- `GET /users/{userId}` — `getUser`：查看用户详情。
- `PUT /users/{userId}` — `updateUser`：编辑用户。
- `POST /users/{userId}/disable` — `disableUser`：禁用用户。
- `POST /users/{userId}/enable` — `enableUser`：启用用户。
- `POST /users/{userId}/password` — `changeUserPassword`：修改用户密码。

### M15（1）

- `GET /ready` — `getReadiness`：依赖就绪检查。

## 保留路径的主要调整

- M01/M02：登录、退出和当前用户改为明确会话生命周期，新增 CSRF/续期；权限码与菜单/数据范围供前后端共用。
- M03/M05：四类采集记录与检索条件拆为带判别字段的类型；列表、详情、文件状态、批次质量及人像任务均有具体结果字段。
- M04/M13：首页 12 类区域与运维四类区域的每项指标均携带 asOf/status/unit/refreshPolicy；局部失败用 null 表示未知值，地图/设备统计复用既有口径。
- M06–M09：Context 保留完整检索筛选快照与到期时间；轨迹、分析、数据集与集合运算改为类型化参数/结果、统一任务状态和受限分页。
- M10–M14：布控版本与告警原事件/投递关联、人员及车辆档案、模型参数版本、设备逐台命令结果、报表单份 XLSX/多份 ZIP、角色范围和审计日志具备明确 DTO。
- 未签认的身份关联、阈值、审批、厂商协议、通知回执与统计口径保留接口边界，使用 `x-design-status: pending` 和 `x-pending-reason`，对应 `OPENAPI_PENDING.md`。

## 保留的 HTTP 操作

以下 41 个方法+路径组合沿用原路径，契约内容已修订：

- `GET /alerts` — `listAlerts`
- `POST /analysis-tasks` — `createAnalysisTask`
- `POST /auth/login` — `login`
- `POST /auth/logout` — `logout`
- `POST /captures/search` — `searchCaptures`
- `POST /contexts` — `createContext`
- `GET /contexts/{contextId}` — `getContext`
- `GET /dashboard/summary` — `getDashboardSummary`
- `GET /datasets` — `listDatasets`
- `POST /datasets` — `createDataset`
- `POST /decrypt-requests` — `requestDecryption`
- `GET /deliveries` — `listDeliveries`
- `POST /device-commands` — `createDeviceCommand`
- `GET /devices` — `listDevices`
- `POST /exports` — `createExport`
- `POST /faces/search-tasks` — `createFaceSearchTask`
- `POST /files/uploads` — `uploadFile`
- `GET /files/{fileAssetId}/download` — `downloadFile`
- `GET /health` — `getHealth`
- `GET /ingestion-batches` — `listIngestionBatches`
- `GET /map/devices` — `getDeviceMapFeatures`
- `GET /map/features` — `getMapFeatures`
- `GET /me` — `getMe`
- `GET /notification-tasks` — `listNotificationTasks`
- `POST /notification-tasks` — `createNotificationTask`
- `GET /operations/summary` — `getOperationSummary`
- `GET /person-libraries` — `listPersonLibraries`
- `POST /person-libraries` — `createPersonLibrary`
- `GET /roles` — `listRoles`
- `POST /roles` — `createRole`
- `POST /set-tasks` — `createSetTask`
- `GET /sites` — `listSites`
- `POST /sites` — `createSite`
- `GET /tasks/{taskId}` — `getTask`
- `POST /tasks/{taskId}/cancel` — `cancelTask`
- `POST /trajectories/query` — `queryTrajectory`
- `GET /users` — `listUsers`
- `POST /users` — `createUser`
- `GET /watch-rules` — `listWatchRules`
- `POST /watch-rules` — `createWatchRule`
- `POST /watch-rules/{ruleId}/revoke` — `revokeWatchRule`

## 2026-10-18 / 1.0.0-dev.3-provisional

PD-2026-10-18.v1开发暂定，甲方待确认。65操作/37字段从pending转provisional；新增5个审批/受控解密结果接口、42 Schema。收紧候选/解密/新密码约束，补recipient及审批关联。批量删除敏感单项携带approvalId；批量报表/角色删除文案分别统一report:delete/role:grant。生成器修复权限字段并保留暂定元数据。现198操作/623 Schema。详见暂定方案与修改记录，未声称真实后端实现或验收通过。
