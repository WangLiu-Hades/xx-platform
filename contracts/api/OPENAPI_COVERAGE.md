> 2026-10-18当前版本：[PD-2026-10-18.v1](../../docs/project-governance/暂定方案与修改记录.md)。当前分工01/02后端、03前端、04数据、05测试；下文旧数量/编号/NO及签认门禁保留作历史追踪，不覆盖本轮暂定设计，也不证明当前实现。旧治理生成脚本不得覆盖本轮policy/暂定文档。

# M01–M15覆盖索引

逐页面/动作使用[api-coverage-matrix.md](<api-coverage-matrix.md>)。最新要求优先分析原始PDF，工程版保留模块边界，不沿用旧优先级和“接口存在即全部覆盖”结论。PENDING_REQUIREMENT表示边界有但规则未签认，整体独立开发NO。

| 模块 | 功能 | 对应API/operationId | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| M01 | 基础/认证/文件/公共任务 | getCsrfToken<br>login<br>logout<br>renewSession<br>getMe<br>getDictionary<br>getClientConfig<br>uploadFile<br>getFileAsset<br>downloadFile<br>createExport<br>listTasks<br>getTask<br>cancelTask<br>retryTask<br>listTaskAttempts<br>listTaskRowErrors<br>listPermissionCatalog<br>listMenuCatalog<br>listRegions<br>getImportTemplate<br>changeMyPassword<br>deleteResourcesBatch<br>getImportTaskResult | PENDING_REQUIREMENT | 权限/CSRF/错误/分页/上传/导出；内部Spring配置NO_API_REQUIRED |
| M02 | Vue应用壳 | getMe<br>getClientConfig<br>getDictionary | COVERED + NO_API_REQUIRED | 路由/组件/弹窗/状态/主题NO_API_REQUIRED，菜单身份复用M01 |
| M03 | 接入批次/质量/回放/补数 | listIngestionBatches<br>createIngestionBatch<br>getIngestionBatch<br>listIngestionErrors<br>getIngestionQualitySummary<br>replayIngestionBatch<br>createIngestionBackfill | PENDING_REQUIREMENT | 原始协议/坐标/映射/去重P-004/P-005 |
| M04 | 首页12类指标/图表导出 | getDashboardSummary<br>getOperationSummary<br>exportMetrics | PENDING_REQUIREMENT | 一次汇总+一次导出；统计公式P-006 |
| M05 | 四类检索/详情/搜图/解密 | searchCaptures<br>getCapture<br>createFaceSearchTask<br>listFaceSearchResults | PENDING_REQUIREMENT | 分页/脱敏强类型；人像与解密P-007/P-008 |
| M06 | 通用研判动作/Context | getCaptureStatistics<br>requestDecryption<br>getDecryptionRequest<br>createContext<br>getContext<br>resolveTargets | PENDING_REQUIREMENT | 复制/导航本地；统计/轨迹/分析/布控/集合复用领域API |
| M07 | GIS/轨迹/设备/选区 | getMapFeatures<br>getMapHeatmap<br>getMapAreaSummary<br>queryTrajectory | PENDING_REQUIREMENT | bbox/zoom/limit/aggregation；手势NO_API_REQUIRED，坐标/许可P-010 |
| M08 | 分析任务/修订/结果/导出 | listAnalysisTasks<br>createAnalysisTask<br>getAnalysisTask<br>deleteAnalysisTask<br>reviseAnalysisTask<br>listAnalysisResults<br>getAnalysisResult<br>exportAnalysisResults<br>exportAnalysisTasks | PENDING_REQUIREMENT | 3类参数/结果分型，analysisTaskId/taskId分开；算法P-011 |
| M09 | 数据集/版本/集合运算 | listDatasets<br>createDataset<br>getDataset<br>updateDataset<br>deleteDataset<br>listDatasetItems<br>addDatasetItems<br>importDatasetItems<br>listDatasetVersions<br>exportDataset<br>previewSetTask<br>listSetTasks<br>createSetTask<br>getSetTask<br>listSetTaskResults<br>removeDatasetItem | PENDING_REQUIREMENT | CAPTURE/TARGET、混合表达式明确；共享/归档P-013 |
| M10 | 布控/告警/通知/消息 | listWatchRules<br>createWatchRule<br>getWatchRule<br>updateWatchRule<br>deleteWatchRule<br>activateWatchRule<br>revokeWatchRule<br>getWatchStatistics<br>listAlerts<br>getAlert<br>listNotificationTasks<br>createNotificationTask<br>getNotificationTask<br>deleteNotificationTask<br>updateNotificationTask<br>cancelNotificationTask<br>retryNotificationTask<br>listDeliveries<br>getDelivery<br>listNotificationTemplates<br>listPlatformMessages<br>revokeResourcesBatch | PENDING_REQUIREMENT | 实时/最终、计划/执行分开；匹配/投递P-014/P-015 |
| M11 | 人员库/档案/常驻模型 | listPersonLibraries<br>createPersonLibrary<br>getPersonLibrary<br>updatePersonLibrary<br>deletePersonLibrary<br>listPersonLibraryMembers<br>addPersonLibraryMembers<br>removePersonLibraryMember<br>importPersonLibraryMembers<br>exportPersonLibrary<br>searchProfiles<br>getProfile<br>listProfileTargets<br>listProfileEvidence<br>listProfileHistory<br>listProfileTrajectories<br>createProfileCorrection<br>mergeProfiles<br>splitProfile<br>resolveProfileConflict<br>exportProfile<br>listTrendModels<br>createTrendModel<br>getTrendModel<br>updateTrendModel<br>listModelParameterVersions<br>createModelParameterVersion<br>getModelParameterVersion<br>listModelTags<br>runTrendModel<br>listTrendModelResults<br>getTrendModelResult<br>createLibraryPerson<br>listProfileConflicts<br>importPersonLibraries<br>exportPersonLibraries | PENDING_REQUIREMENT | 人工新增/导入/证据ID；自动身份与模型P-016/P-017 |
| M12 | 场所/设备/配置/报表 | listSites<br>createSite<br>getSite<br>updateSite<br>deleteSite<br>listDevices<br>createDevice<br>getDevice<br>updateDevice<br>deleteDevice<br>getDeviceStatus<br>getDeviceParameters<br>updateDeviceParameters<br>listDeviceStatusHistory<br>getDeviceCaptureStatistics<br>importDevices<br>exportDevices<br>listDeviceGroups<br>createDeviceGroup<br>getDeviceGroup<br>updateDeviceGroup<br>deleteDeviceGroup<br>listDeviceGroupMembers<br>addDeviceGroupMembers<br>removeDeviceGroupMember<br>listDeviceCommands<br>createDeviceCommand<br>getDeviceCommand<br>deleteDeviceCommand<br>listDeviceCommandResults<br>retryDeviceCommand<br>listReports<br>createReport<br>getReport<br>deleteReport<br>getReportPreview<br>retryReport<br>downloadReport<br>reviseDeviceCommand | PENDING_REQUIREMENT | 参数/逐设备回执/8种报表强类型；P-018/P-019/P-020 |
| M13 | 运维可视化 | getOperationSummary<br>getDeviceMapFeatures<br>getMapHeatmap<br>getMapAreaSummary<br>getDeviceCaptureStatistics<br>getReportPreview<br>exportMetrics | PENDING_REQUIREMENT | 复用M04/M07/M12，不产生重复口径API |
| M14 | 用户/角色/日志 | listUsers<br>createUser<br>getUser<br>updateUser<br>deleteUser<br>disableUser<br>enableUser<br>changeUserPassword<br>listRoles<br>createRole<br>getRole<br>updateRole<br>deleteRole<br>setRolePermissions<br>setRoleScopes<br>searchAuditEvents<br>getAuditEvent<br>exportAuditEvents<br>searchSmsLogs<br>exportSmsLogs | PENDING_REQUIREMENT | 引用409、own/all权限、日志导出范围；P-021 |
| M15 | 交付/部署/探针 | getHealth<br>getReadiness<br>getTask | COVERED + NO_API_REQUIRED | health/ready/task HTTP映射；Docker/CI/CD/备份/压测/发布NO_API_REQUIRED |

## 差异处理

- 车牌回填IMSI、车档案复制人员文案等串文按功能对象分析，保留P-016，不创建错实体接口。
- 原型每日通知用ONCE/DAILY明确表达；补发/供应商规则P-015。
- 模拟状态、命中、结束、发送不暴露生产API。
- 数据库仅TARGET与原型采集集合差异：API明确CAPTURE/TARGET，不跨型运算。数据库迁移属于实现，不在本次修改范围。

## 2026-10-18新增契约

198操作/623 Schema；原65操作+37处字段已有暂定规则，甲方待确认。新增listActionApprovals/createActionApproval/getActionApproval/decideActionApproval/readDecryptionResult。可达性/后端实现不由本数量证明，完整检查见docs/project-governance/checks。
