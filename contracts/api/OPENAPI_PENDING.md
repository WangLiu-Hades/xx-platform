# API暂定设计主账

PD-2026-10-19.v1：全部未决设计已有暂定开发规则，**甲方仍待确认**。不再仅因未获甲方签字将全部相关能力统一挡为422。实现/实际资源未就绪仍按能力目录禁用并给出真实原因，不得假成功。

处理摘要与官方来源见[暂定方案与修改记录](../../docs/project-governance/暂定方案与修改记录.md)；细则见[policy](../../docs/project-governance/provisional-policy.json)。

| 编号 | 领域 | 暂定处理 |
| --- | --- | --- |
| P-001 | 登录 | 服务端SID；空闲15分钟/绝对8小时；主动操作续空闲，轮询不续期；IP/UA变化重新认证。单会话、新登录撤旧沿用。 |
| P-002 | 权限 | 保留58码/28菜单；操作码暂取角色并集；账号额外范围为附加授权；siteId最小边界；缩权立即生效。三类初始角色可扩展。 |
| P-003 | 容量/保存 | 沿用已给验收指标；查询31天；图片10MiB、导入100MiB/10万行；普通/复杂排队5/15分钟。按2026-10-19用户决定统一保存一年（12个自然月），保护活跃引用及年报冻结输入。 |
| P-004 | 采集/去重 | 版本化SourceProfile；未知映射/时区/坐标系隔离；源事件键优先。无源键仅保证批内幂等，跨批去重标未验证。 |
| P-005 | 回放/补数 | 留存期内、冻结输入、独立审批；新批次追加、源事件不重复计数，失败仅重试未成功行。 |
| P-006 | 指标 | METRIC_V1；采集counter与入库count分开；在线心跳90秒；自然周期；首页水位至可见≤5秒；未知为null。 |
| P-007 | 人像 | 授权内网模型adapter；默认20候选、最多100、相似度下限0仅排序；人工复核。评测目标须甲方确认，不是实测。 |
| P-008 | 解密 | 普通账号可申请转交；指定涉密接收人；两名独立审批人；最多200条；5分钟内一次性受控查看，不生成持久明文文件。 |
| P-009 | 查询/导出 | Context固定30分钟、游标15分钟；SQL5秒硬超时；异步导出100万行/1GiB、5万行分片；下载许可5分钟。 |
| P-010 | 地图 | 高德离线PC Web适配；全部资源内网化；WGS84；单次2000点/2000顶点/1万km²。具体产品与许可待落实。 |
| P-011 | 伴随/碰撞 | 默认120秒/100米；最大3600秒/5000米、7天；伴随≥3共现桶、Jaccard分；碰撞按主目标桶覆盖率；保留证据。 |
| P-013 | 数据集 | 共享仍与源权限求交；固定版本；ARCHIVED只读、不可新引用；活跃引用删除409；管理导入全有或全无。 |
| P-014 | 布控/告警 | 同型标识精确匹配；已确认关联才跨源；5分钟外发去重；默认LOW；运行中流水与终态统计分开。 |
| P-015 | 通知/短信验收 | 短信仅送达成功回执算成功；UNKNOWN先对账。重试3次30/120/600秒+抖动；回执24小时；暂按连续7天/≥1000逻辑投递统计≥99%，重试合并。 |
| P-016 | 档案 | 跨源关联先候选后复核，不自动合档；合并遵守retainedProfileId；保留旧ID/证据/修订；不改写原始事实。 |
| P-017 | 常驻模型 | 仅RESIDENCE：自然月、22:00—06:00、≥10条/≥5夜、夜晚占比≥0.6；标签是待复核场所候选；参数不可变，无自由DSL。 |
| P-018 | 设备 | 真实DeviceProfile必需；desired/reported分开；RESET暂指安全重启；TTL120秒；UNKNOWN先对账；重启不自动重试。 |
| P-019 | 敏感审批 | 冻结操作/资源/版本/完整payload摘要；普通1人、解密等关键操作2人，禁止自批；申请24小时、执行许可10分钟、一次消费。 |
| P-020 | 报表 | 保留全部8类；同源公式与冻结预览；SINGLE XLSX/BUNDLE ZIP；年度注明实际覆盖，缺历史不补零；暂时失败重试1次。 |
| P-021 | 账号/审计 | 密码15—128字符/NFC/弱密码表；PBKDF2-SHA256 60万次；首次强改；不强制组合或90天轮换。先禁用后软删，审计仅追加，UI遥测不冒充服务端审计。 |


P-012的ID集合语法沿用已收敛基线，不重开；跨源身份问题归P-004/P-016。项目G01/G03/H04详见主文档。

## 状态标记

`x-design-status: provisional`表示可依本轮暂定规则开发；`x-client-confirmation: pending`表示尚未得到甲方最终确认；`x-decision-ids`追踪P编号。原102处pending均逐一转换，未删除其确认责任。

## 原65个操作对应

| operationId | 方法与路径 | 决策 |
| --- | --- | --- |
| renewSession | POST /auth/session/renew | P-001 |
| createIngestionBatch | POST /ingestion-batches | P-004 |
| replayIngestionBatch | POST /ingestion-batches/{batchId}/replay | P-004,P-005 |
| createIngestionBackfill | POST /ingestion/backfills | P-004,P-005 |
| createFaceSearchTask | POST /faces/search-tasks | P-007 |
| listFaceSearchResults | GET /faces/search-tasks/{taskId}/results | P-007 |
| requestDecryption | POST /decrypt-requests | P-008 |
| getDecryptionRequest | GET /decrypt-requests/{requestId} | P-008 |
| createWatchRule | POST /watch-rules | P-014,P-015 |
| updateWatchRule | PATCH /watch-rules/{ruleId} | P-014 |
| activateWatchRule | POST /watch-rules/{ruleId}/activate | P-014,P-015 |
| createNotificationTask | POST /notification-tasks | P-014,P-015 |
| updateNotificationTask | PUT /notification-tasks/{notificationTaskId} | P-015 |
| retryNotificationTask | POST /notification-tasks/{notificationTaskId}/retry | P-015 |
| addPersonLibraryMembers | POST /person-libraries/{libraryId}/members | P-002,P-016 |
| importPersonLibraryMembers | POST /person-libraries/{libraryId}/imports | P-016 |
| exportPersonLibrary | POST /person-libraries/{libraryId}/exports | P-002,P-016 |
| searchProfiles | POST /profiles/search | P-016 |
| getProfile | GET /profiles/{profileId} | P-016 |
| listProfileTargets | GET /profiles/{profileId}/targets | P-016 |
| listProfileTrajectories | GET /profiles/{profileId}/trajectories | P-016 |
| createProfileCorrection | POST /profiles/{profileId}/corrections | P-016 |
| mergeProfiles | POST /profiles/merge | P-016 |
| splitProfile | POST /profiles/{profileId}/split | P-016 |
| resolveProfileConflict | POST /profiles/{profileId}/conflicts/{conflictId}/resolve | P-016 |
| exportProfile | POST /profiles/{profileId}/exports | P-016,P-021 |
| listTrendModels | GET /trend-models | P-017 |
| createTrendModel | POST /trend-models | P-017 |
| getTrendModel | GET /trend-models/{modelId} | P-016 |
| updateTrendModel | PUT /trend-models/{modelId} | P-016 |
| createModelParameterVersion | POST /trend-models/{modelId}/parameter-versions | P-017 |
| listModelTags | GET /model-tags | P-017 |
| runTrendModel | POST /trend-models/{modelId}/runs | P-017 |
| listTrendModelResults | GET /trend-models/{modelId}/results | P-017 |
| getTrendModelResult | GET /trend-models/{modelId}/results/{resultId} | P-016 |
| createDevice | POST /devices | P-018 |
| updateDevice | PUT /devices/{deviceId} | P-018 |
| getDeviceParameters | GET /devices/{deviceId}/parameters | P-018,P-019 |
| updateDeviceParameters | POST /devices/{deviceId}/parameter-updates | P-018,P-019 |
| getDeviceCaptureStatistics | GET /devices/{deviceId}/capture-statistics | P-006 |
| importDevices | POST /devices/imports | P-018 |
| exportDevices | POST /devices/exports | P-002 |
| createDeviceCommand | POST /device-commands | P-018,P-019 |
| retryDeviceCommand | POST /device-commands/{taskId}/retry | P-018,P-019 |
| createReport | POST /reports | P-006,P-020 |
| getReportPreview | GET /reports/{reportId}/preview | P-006,P-020 |
| createUser | POST /users | P-021 |
| updateUser | PUT /users/{userId} | P-002,P-021 |
| disableUser | POST /users/{userId}/disable | P-001,P-021 |
| changeUserPassword | POST /users/{userId}/password | P-021 |
| createRole | POST /roles | P-002 |
| setRolePermissions | PUT /roles/{roleId}/permissions | P-002 |
| setRoleScopes | PUT /roles/{roleId}/scopes | P-002 |
| searchAuditEvents | POST /audit-events/search | P-021 |
| exportAuditEvents | POST /audit-events/export | P-021 |
| searchSmsLogs | POST /deliveries/search | P-015,P-021 |
| exportSmsLogs | POST /deliveries/export | P-015,P-021 |
| resolveTargets | POST /targets/resolve | P-004,P-016 |
| exportMetrics | POST /metrics/exports | P-006 |
| listNotificationTemplates | GET /notification-templates | P-015 |
| createLibraryPerson | POST /person-libraries/{libraryId}/persons | P-016 |
| listProfileConflicts | GET /profiles/{profileId}/conflicts | P-016 |
| importPersonLibraries | POST /person-libraries/imports | P-016 |
| reviseDeviceCommand | POST /device-commands/{taskId}/revisions | P-018,P-019 |
| changeMyPassword | POST /me/password | P-021 |

## 原37处Schema/字段对应

| 位置 | 决策 |
| --- | --- |
| PermissionCode | P-002 |
| SessionData | P-001 |
| DataScope | P-002 |
| SiteScope | P-002 |
| ExportRequest | P-003,P-009 |
| Task | P-003 |
| AnalysisContext | P-009 |
| GeoFeatureCollection | P-010 |
| FaceRecord | P-007 |
| CaptureSearchRequest | P-003,P-009 |
| FaceSearchInput | P-007 |
| DashboardSummary | P-006 |
| SmsModemStatus | P-015 |
| OperationsSummary | P-006,P-015 |
| CompanionAnalysisParameters | P-011 |
| CollisionAnalysisParameters | P-011 |
| CompanionResultItem | P-011 |
| CollisionResultItem | P-011 |
| DatasetDetail | P-013 |
| WatchRuleConditions | P-014,P-016 |
| Alert | P-014 |
| NotificationTaskInput | P-014,P-015 |
| Delivery | P-015 |
| DecryptionInput | P-008 |
| DecryptionRequestDetail | P-008 |
| PersonLibraryImportInput | P-016 |
| ProfileDetail | P-016 |
| PersonProfileCorrectionInput | P-016 |
| VehicleProfileCorrectionInput | P-016 |
| ModelParameterValue | P-017 |
| ModelRuleDescriptor | P-017 |
| DeviceParameter | P-018,P-019 |
| DeviceParameterChange | P-018,P-019 |
| ReportDefinition | P-006,P-020 |
| ReportPreview | P-006,P-020 |
| PasswordChangeInput | P-021 |
| WatchTargetStatistics.properties.mostFrequentSiteId | P-006,P-017 |
