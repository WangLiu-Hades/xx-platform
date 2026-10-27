/* Generated from the specified OpenAPI. Do not edit. */
export const operationCatalog = {
  "getHealth": {
    "method": "GET",
    "path": "/health",
    "summary": "存活检查",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getReadiness": {
    "method": "GET",
    "path": "/ready",
    "summary": "依赖就绪检查",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getCsrfToken": {
    "method": "GET",
    "path": "/auth/csrf",
    "summary": "获取 CSRF 令牌",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "login": {
    "method": "POST",
    "path": "/auth/login",
    "summary": "登录并创建服务端会话",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "logout": {
    "method": "POST",
    "path": "/auth/logout",
    "summary": "退出并撤销会话",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "renewSession": {
    "method": "POST",
    "path": "/auth/session/renew",
    "summary": "续期当前 Cookie 会话",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getMe": {
    "method": "GET",
    "path": "/me",
    "summary": "读取当前用户、菜单与权限范围",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getDictionary": {
    "method": "GET",
    "path": "/dictionaries/{dictionaryCode}",
    "summary": "读取前端字典",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getClientConfig": {
    "method": "GET",
    "path": "/client-config",
    "summary": "读取非机密前端配置",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "uploadFile": {
    "method": "POST",
    "path": "/files/uploads",
    "summary": "上传受控文件",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getFileAsset": {
    "method": "GET",
    "path": "/files/{fileAssetId}",
    "summary": "查询文件处理状态",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "downloadFile": {
    "method": "GET",
    "path": "/files/{fileAssetId}/download",
    "summary": "下载受控文件",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createExport": {
    "method": "POST",
    "path": "/exports",
    "summary": "创建异步导出",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listTasks": {
    "method": "GET",
    "path": "/tasks",
    "summary": "查询当前可见任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getTask": {
    "method": "GET",
    "path": "/tasks/{taskId}",
    "summary": "读取任务状态与结果引用",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "cancelTask": {
    "method": "POST",
    "path": "/tasks/{taskId}/cancel",
    "summary": "请求取消任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "retryTask": {
    "method": "POST",
    "path": "/tasks/{taskId}/retry",
    "summary": "重试失败任务并保留 taskId",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listTaskAttempts": {
    "method": "GET",
    "path": "/tasks/{taskId}/attempts",
    "summary": "查看任务尝试历史",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listTaskRowErrors": {
    "method": "GET",
    "path": "/tasks/{taskId}/errors",
    "summary": "分页查看文件导入的逐行错误",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listIngestionBatches": {
    "method": "GET",
    "path": "/ingestion-batches",
    "summary": "查询接入批次",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createIngestionBatch": {
    "method": "POST",
    "path": "/ingestion-batches",
    "summary": "提交文件接入批次",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getIngestionBatch": {
    "method": "GET",
    "path": "/ingestion-batches/{batchId}",
    "summary": "查看接入批次详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listIngestionErrors": {
    "method": "GET",
    "path": "/ingestion-batches/{batchId}/errors",
    "summary": "查看逐条接入错误",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getIngestionQualitySummary": {
    "method": "GET",
    "path": "/ingestion/quality-summary",
    "summary": "查看接入质量汇总",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "replayIngestionBatch": {
    "method": "POST",
    "path": "/ingestion-batches/{batchId}/replay",
    "summary": "按版本回放批次",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createIngestionBackfill": {
    "method": "POST",
    "path": "/ingestion/backfills",
    "summary": "创建补数任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getDashboardSummary": {
    "method": "GET",
    "path": "/dashboard/summary",
    "summary": "读取系统首页 12 类区域",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getOperationSummary": {
    "method": "GET",
    "path": "/operations/summary",
    "summary": "读取运维首页四类区域",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "searchCaptures": {
    "method": "POST",
    "path": "/captures/search",
    "summary": "按类型检索采集记录",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getCapture": {
    "method": "GET",
    "path": "/captures/{captureId}",
    "summary": "查看采集记录详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getCaptureStatistics": {
    "method": "POST",
    "path": "/captures/statistics",
    "summary": "统计上下文内目标",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createFaceSearchTask": {
    "method": "POST",
    "path": "/faces/search-tasks",
    "summary": "创建人像相似检索任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listFaceSearchResults": {
    "method": "GET",
    "path": "/faces/search-tasks/{taskId}/results",
    "summary": "分页读取人像候选",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "requestDecryption": {
    "method": "POST",
    "path": "/decrypt-requests",
    "summary": "提交解密申请",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getDecryptionRequest": {
    "method": "GET",
    "path": "/decrypt-requests/{requestId}",
    "summary": "查询解密申请状态",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createContext": {
    "method": "POST",
    "path": "/contexts",
    "summary": "建立跨页研判上下文",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getContext": {
    "method": "GET",
    "path": "/contexts/{contextId}",
    "summary": "重校权限后读取上下文",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getMapFeatures": {
    "method": "GET",
    "path": "/map/features",
    "summary": "查询采集点/聚合地图要素",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getDeviceMapFeatures": {
    "method": "GET",
    "path": "/map/devices",
    "summary": "查询设备点与聚合",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getMapHeatmap": {
    "method": "GET",
    "path": "/map/heatmap",
    "summary": "按时间窗读取设备采集热力",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getMapAreaSummary": {
    "method": "POST",
    "path": "/map/area-summary",
    "summary": "统计选区内采集与设备",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "queryTrajectory": {
    "method": "POST",
    "path": "/trajectories/query",
    "summary": "分页查询目标轨迹",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listAnalysisTasks": {
    "method": "GET",
    "path": "/analysis-tasks",
    "summary": "按名称、类型和日期查询分析任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createAnalysisTask": {
    "method": "POST",
    "path": "/analysis-tasks",
    "summary": "创建伴随/碰撞/区域统计任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getAnalysisTask": {
    "method": "GET",
    "path": "/analysis-tasks/{analysisTaskId}",
    "summary": "查看分析任务与参数快照",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deleteAnalysisTask": {
    "method": "DELETE",
    "path": "/analysis-tasks/{analysisTaskId}",
    "summary": "软删终态分析任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "reviseAnalysisTask": {
    "method": "POST",
    "path": "/analysis-tasks/{analysisTaskId}/revisions",
    "summary": "以新版本重建分析任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listAnalysisResults": {
    "method": "GET",
    "path": "/analysis-tasks/{analysisTaskId}/results",
    "summary": "分页查看分析结果",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getAnalysisResult": {
    "method": "GET",
    "path": "/analysis-tasks/{analysisTaskId}/results/{resultId}",
    "summary": "查看分析结果及证据引用",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "exportAnalysisResults": {
    "method": "POST",
    "path": "/analysis-tasks/{analysisTaskId}/results/export",
    "summary": "异步导出分析结果",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listDatasets": {
    "method": "GET",
    "path": "/datasets",
    "summary": "查询数据集",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createDataset": {
    "method": "POST",
    "path": "/datasets",
    "summary": "创建空或上下文数据集",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getDataset": {
    "method": "GET",
    "path": "/datasets/{datasetId}",
    "summary": "查看数据集详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "updateDataset": {
    "method": "PATCH",
    "path": "/datasets/{datasetId}",
    "summary": "重命名数据集",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deleteDataset": {
    "method": "DELETE",
    "path": "/datasets/{datasetId}",
    "summary": "受引用保护地软删数据集",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listDatasetItems": {
    "method": "GET",
    "path": "/datasets/{datasetId}/items",
    "summary": "游标读取数据集成员",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "addDatasetItems": {
    "method": "POST",
    "path": "/datasets/{datasetId}/items",
    "summary": "批量添加数据集成员",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "importDatasetItems": {
    "method": "POST",
    "path": "/datasets/{datasetId}/imports",
    "summary": "从查询上下文或文件导入成员",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listDatasetVersions": {
    "method": "GET",
    "path": "/datasets/{datasetId}/versions",
    "summary": "查看数据集版本",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "exportDataset": {
    "method": "POST",
    "path": "/datasets/{datasetId}/export",
    "summary": "异步导出数据集",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "previewSetTask": {
    "method": "POST",
    "path": "/set-tasks/preview",
    "summary": "预览集合运算兼容性与估量",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listSetTasks": {
    "method": "GET",
    "path": "/set-tasks",
    "summary": "查询集合运算任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createSetTask": {
    "method": "POST",
    "path": "/set-tasks",
    "summary": "创建异步集合运算",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getSetTask": {
    "method": "GET",
    "path": "/set-tasks/{setTaskId}",
    "summary": "查看集合任务详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listSetTaskResults": {
    "method": "GET",
    "path": "/set-tasks/{setTaskId}/results",
    "summary": "游标查看集合结果",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listWatchRules": {
    "method": "GET",
    "path": "/watch-rules",
    "summary": "查询布控规则",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createWatchRule": {
    "method": "POST",
    "path": "/watch-rules",
    "summary": "创建布控草稿",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getWatchRule": {
    "method": "GET",
    "path": "/watch-rules/{ruleId}",
    "summary": "查看规则详情和当前版本",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "updateWatchRule": {
    "method": "PATCH",
    "path": "/watch-rules/{ruleId}",
    "summary": "修改规则并生成新版本",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deleteWatchRule": {
    "method": "DELETE",
    "path": "/watch-rules/{ruleId}",
    "summary": "软删终态布控规则",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "activateWatchRule": {
    "method": "POST",
    "path": "/watch-rules/{ruleId}/activate",
    "summary": "激活布控草稿",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "revokeWatchRule": {
    "method": "POST",
    "path": "/watch-rules/{ruleId}/revoke",
    "summary": "撤销运行中的布控",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getWatchStatistics": {
    "method": "GET",
    "path": "/watch-rules/{ruleId}/statistics",
    "summary": "查看实时命中数或最终统计",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listAlerts": {
    "method": "GET",
    "path": "/alerts",
    "summary": "游标检索实时告警",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getAlert": {
    "method": "GET",
    "path": "/alerts/{alertId}",
    "summary": "查看告警、源采集、规则快照与送达",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listNotificationTasks": {
    "method": "GET",
    "path": "/notification-tasks",
    "summary": "查询通知任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createNotificationTask": {
    "method": "POST",
    "path": "/notification-tasks",
    "summary": "创建定时通知任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getNotificationTask": {
    "method": "GET",
    "path": "/notification-tasks/{notificationTaskId}",
    "summary": "查看通知任务详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deleteNotificationTask": {
    "method": "DELETE",
    "path": "/notification-tasks/{notificationTaskId}",
    "summary": "软删已终结通知任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "updateNotificationTask": {
    "method": "PUT",
    "path": "/notification-tasks/{notificationTaskId}",
    "summary": "更新未终结通知计划",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "cancelNotificationTask": {
    "method": "POST",
    "path": "/notification-tasks/{notificationTaskId}/cancel",
    "summary": "请求取消未完成通知",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "retryNotificationTask": {
    "method": "POST",
    "path": "/notification-tasks/{notificationTaskId}/retry",
    "summary": "重试符合条件的失败通知",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listDeliveries": {
    "method": "GET",
    "path": "/deliveries",
    "summary": "游标查看送达记录",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getDelivery": {
    "method": "GET",
    "path": "/deliveries/{deliveryId}",
    "summary": "查看送达状态与失败原因",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listPersonLibraries": {
    "method": "GET",
    "path": "/person-libraries",
    "summary": "查询人员库",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createPersonLibrary": {
    "method": "POST",
    "path": "/person-libraries",
    "summary": "新建人员库",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getPersonLibrary": {
    "method": "GET",
    "path": "/person-libraries/{libraryId}",
    "summary": "查看人员库详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "updatePersonLibrary": {
    "method": "PUT",
    "path": "/person-libraries/{libraryId}",
    "summary": "编辑人员库",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deletePersonLibrary": {
    "method": "DELETE",
    "path": "/person-libraries/{libraryId}",
    "summary": "删除人员库",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listPersonLibraryMembers": {
    "method": "GET",
    "path": "/person-libraries/{libraryId}/members",
    "summary": "查询人员库成员",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "addPersonLibraryMembers": {
    "method": "POST",
    "path": "/person-libraries/{libraryId}/members",
    "summary": "添加人员库成员",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "removePersonLibraryMember": {
    "method": "DELETE",
    "path": "/person-libraries/{libraryId}/members/{memberId}",
    "summary": "移除人员库成员",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "importPersonLibraryMembers": {
    "method": "POST",
    "path": "/person-libraries/{libraryId}/imports",
    "summary": "从文件导入人员库成员",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "exportPersonLibrary": {
    "method": "POST",
    "path": "/person-libraries/{libraryId}/exports",
    "summary": "导出人员库",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "searchProfiles": {
    "method": "POST",
    "path": "/profiles/search",
    "summary": "检索人车档案",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getProfile": {
    "method": "GET",
    "path": "/profiles/{profileId}",
    "summary": "查看档案详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listProfileTargets": {
    "method": "GET",
    "path": "/profiles/{profileId}/targets",
    "summary": "查看档案关联目标",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listProfileEvidence": {
    "method": "GET",
    "path": "/profiles/{profileId}/evidence",
    "summary": "查看档案证据",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listProfileHistory": {
    "method": "GET",
    "path": "/profiles/{profileId}/history",
    "summary": "查看档案修订历史",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listProfileTrajectories": {
    "method": "GET",
    "path": "/profiles/{profileId}/trajectories",
    "summary": "查看档案轨迹",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createProfileCorrection": {
    "method": "POST",
    "path": "/profiles/{profileId}/corrections",
    "summary": "人工修订档案",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "mergeProfiles": {
    "method": "POST",
    "path": "/profiles/merge",
    "summary": "合并档案",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "splitProfile": {
    "method": "POST",
    "path": "/profiles/{profileId}/split",
    "summary": "拆分档案",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "resolveProfileConflict": {
    "method": "POST",
    "path": "/profiles/{profileId}/conflicts/{conflictId}/resolve",
    "summary": "处理档案证据冲突",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "exportProfile": {
    "method": "POST",
    "path": "/profiles/{profileId}/exports",
    "summary": "导出档案",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listTrendModels": {
    "method": "GET",
    "path": "/trend-models",
    "summary": "查询趋势模型",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createTrendModel": {
    "method": "POST",
    "path": "/trend-models",
    "summary": "新建趋势模型",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getTrendModel": {
    "method": "GET",
    "path": "/trend-models/{modelId}",
    "summary": "查看趋势模型详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "updateTrendModel": {
    "method": "PUT",
    "path": "/trend-models/{modelId}",
    "summary": "编辑趋势模型",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listModelParameterVersions": {
    "method": "GET",
    "path": "/trend-models/{modelId}/parameter-versions",
    "summary": "查询模型参数版本",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createModelParameterVersion": {
    "method": "POST",
    "path": "/trend-models/{modelId}/parameter-versions",
    "summary": "新增模型参数版本",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getModelParameterVersion": {
    "method": "GET",
    "path": "/trend-models/{modelId}/parameter-versions/{parameterVersionId}",
    "summary": "查看模型参数版本",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listModelTags": {
    "method": "GET",
    "path": "/model-tags",
    "summary": "查询模型标签目录",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "runTrendModel": {
    "method": "POST",
    "path": "/trend-models/{modelId}/runs",
    "summary": "执行趋势模型",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listTrendModelResults": {
    "method": "GET",
    "path": "/trend-models/{modelId}/results",
    "summary": "查看模型执行结果",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getTrendModelResult": {
    "method": "GET",
    "path": "/trend-models/{modelId}/results/{resultId}",
    "summary": "查看模型结果详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listSites": {
    "method": "GET",
    "path": "/sites",
    "summary": "查询场所",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createSite": {
    "method": "POST",
    "path": "/sites",
    "summary": "新增场所",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getSite": {
    "method": "GET",
    "path": "/sites/{siteId}",
    "summary": "查看场所详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "updateSite": {
    "method": "PUT",
    "path": "/sites/{siteId}",
    "summary": "编辑场所",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deleteSite": {
    "method": "DELETE",
    "path": "/sites/{siteId}",
    "summary": "删除场所",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listDevices": {
    "method": "GET",
    "path": "/devices",
    "summary": "查询设备",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createDevice": {
    "method": "POST",
    "path": "/devices",
    "summary": "新增设备",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getDevice": {
    "method": "GET",
    "path": "/devices/{deviceId}",
    "summary": "查看设备详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "updateDevice": {
    "method": "PUT",
    "path": "/devices/{deviceId}",
    "summary": "编辑设备信息",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deleteDevice": {
    "method": "DELETE",
    "path": "/devices/{deviceId}",
    "summary": "删除设备",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getDeviceStatus": {
    "method": "GET",
    "path": "/devices/{deviceId}/status",
    "summary": "查看设备实时状态",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getDeviceParameters": {
    "method": "GET",
    "path": "/devices/{deviceId}/parameters",
    "summary": "读取设备参数",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "updateDeviceParameters": {
    "method": "POST",
    "path": "/devices/{deviceId}/parameter-updates",
    "summary": "提交设备参数更新",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listDeviceStatusHistory": {
    "method": "GET",
    "path": "/devices/{deviceId}/status-history",
    "summary": "查看设备在线离线历史",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getDeviceCaptureStatistics": {
    "method": "GET",
    "path": "/devices/{deviceId}/capture-statistics",
    "summary": "查看设备采集历史",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "importDevices": {
    "method": "POST",
    "path": "/devices/imports",
    "summary": "批量导入设备",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "exportDevices": {
    "method": "POST",
    "path": "/devices/exports",
    "summary": "导出设备清单",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listDeviceGroups": {
    "method": "GET",
    "path": "/device-groups",
    "summary": "查询设备分组",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createDeviceGroup": {
    "method": "POST",
    "path": "/device-groups",
    "summary": "新建设备分组",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getDeviceGroup": {
    "method": "GET",
    "path": "/device-groups/{groupId}",
    "summary": "查看设备分组详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "updateDeviceGroup": {
    "method": "PUT",
    "path": "/device-groups/{groupId}",
    "summary": "编辑设备分组",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deleteDeviceGroup": {
    "method": "DELETE",
    "path": "/device-groups/{groupId}",
    "summary": "删除设备分组",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listDeviceGroupMembers": {
    "method": "GET",
    "path": "/device-groups/{groupId}/members",
    "summary": "查询分组设备",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "addDeviceGroupMembers": {
    "method": "POST",
    "path": "/device-groups/{groupId}/members",
    "summary": "添加分组设备",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "removeDeviceGroupMember": {
    "method": "DELETE",
    "path": "/device-groups/{groupId}/members/{deviceId}",
    "summary": "移除分组设备",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listDeviceCommands": {
    "method": "GET",
    "path": "/device-commands",
    "summary": "查询设备命令任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createDeviceCommand": {
    "method": "POST",
    "path": "/device-commands",
    "summary": "下发远程设备命令",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getDeviceCommand": {
    "method": "GET",
    "path": "/device-commands/{taskId}",
    "summary": "查看设备命令任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deleteDeviceCommand": {
    "method": "DELETE",
    "path": "/device-commands/{taskId}",
    "summary": "软删终态配置任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listDeviceCommandResults": {
    "method": "GET",
    "path": "/device-commands/{taskId}/results",
    "summary": "查看逐设备执行结果",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "retryDeviceCommand": {
    "method": "POST",
    "path": "/device-commands/{taskId}/retry",
    "summary": "重试失败的设备命令",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listReports": {
    "method": "GET",
    "path": "/reports",
    "summary": "查询报表任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createReport": {
    "method": "POST",
    "path": "/reports",
    "summary": "创建报表任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getReport": {
    "method": "GET",
    "path": "/reports/{reportId}",
    "summary": "查看报表详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deleteReport": {
    "method": "DELETE",
    "path": "/reports/{reportId}",
    "summary": "删除报表任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getReportPreview": {
    "method": "GET",
    "path": "/reports/{reportId}/preview",
    "summary": "预览单报表",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "retryReport": {
    "method": "POST",
    "path": "/reports/{reportId}/retry",
    "summary": "重试失败报表",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "downloadReport": {
    "method": "GET",
    "path": "/reports/{reportId}/download",
    "summary": "下载报表文件",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listUsers": {
    "method": "GET",
    "path": "/users",
    "summary": "查询用户",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createUser": {
    "method": "POST",
    "path": "/users",
    "summary": "新增用户",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getUser": {
    "method": "GET",
    "path": "/users/{userId}",
    "summary": "查看用户详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "updateUser": {
    "method": "PUT",
    "path": "/users/{userId}",
    "summary": "编辑用户",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deleteUser": {
    "method": "DELETE",
    "path": "/users/{userId}",
    "summary": "删除已禁用用户",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "disableUser": {
    "method": "POST",
    "path": "/users/{userId}/disable",
    "summary": "禁用用户",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "enableUser": {
    "method": "POST",
    "path": "/users/{userId}/enable",
    "summary": "启用用户",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "changeUserPassword": {
    "method": "POST",
    "path": "/users/{userId}/password",
    "summary": "修改用户密码",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listRoles": {
    "method": "GET",
    "path": "/roles",
    "summary": "查询角色",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createRole": {
    "method": "POST",
    "path": "/roles",
    "summary": "新增角色",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getRole": {
    "method": "GET",
    "path": "/roles/{roleId}",
    "summary": "查看角色详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "updateRole": {
    "method": "PUT",
    "path": "/roles/{roleId}",
    "summary": "编辑角色基本信息",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deleteRole": {
    "method": "DELETE",
    "path": "/roles/{roleId}",
    "summary": "删除未被引用的角色",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "setRolePermissions": {
    "method": "PUT",
    "path": "/roles/{roleId}/permissions",
    "summary": "设置角色菜单和按钮权限",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "setRoleScopes": {
    "method": "PUT",
    "path": "/roles/{roleId}/scopes",
    "summary": "设置角色场所和数据范围",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "searchAuditEvents": {
    "method": "POST",
    "path": "/audit-events/search",
    "summary": "查询操作日志",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getAuditEvent": {
    "method": "GET",
    "path": "/audit-events/{eventId}",
    "summary": "查看操作日志详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "exportAuditEvents": {
    "method": "POST",
    "path": "/audit-events/export",
    "summary": "导出操作日志",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "searchSmsLogs": {
    "method": "POST",
    "path": "/deliveries/search",
    "summary": "查询短信送达日志",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "exportSmsLogs": {
    "method": "POST",
    "path": "/deliveries/export",
    "summary": "导出短信送达日志",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listPermissionCatalog": {
    "method": "GET",
    "path": "/permissions",
    "summary": "读取可分配权限目录",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listMenuCatalog": {
    "method": "GET",
    "path": "/menus",
    "summary": "读取可分配菜单目录",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listRegions": {
    "method": "GET",
    "path": "/regions",
    "summary": "读取授权区域目录",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "resolveTargets": {
    "method": "POST",
    "path": "/targets/resolve",
    "summary": "批量解析或登记目标代理ID",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "exportMetrics": {
    "method": "POST",
    "path": "/metrics/exports",
    "summary": "导出首页或运维指标图表",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "removeDatasetItem": {
    "method": "DELETE",
    "path": "/datasets/{datasetId}/items/{itemId}",
    "summary": "移除数据集成员",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listNotificationTemplates": {
    "method": "GET",
    "path": "/notification-templates",
    "summary": "查询已批准通知模板",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listPlatformMessages": {
    "method": "GET",
    "path": "/messages",
    "summary": "查询本人平台消息",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createLibraryPerson": {
    "method": "POST",
    "path": "/person-libraries/{libraryId}/persons",
    "summary": "新增库内人员及手工档案",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listProfileConflicts": {
    "method": "GET",
    "path": "/profiles/{profileId}/conflicts",
    "summary": "读取可复核档案冲突",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getImportTemplate": {
    "method": "GET",
    "path": "/import-templates/{templateCode}",
    "summary": "读取导入模板列与文件约定",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "importPersonLibraries": {
    "method": "POST",
    "path": "/person-libraries/imports",
    "summary": "批量导入库列表和库内人员",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "exportPersonLibraries": {
    "method": "POST",
    "path": "/person-libraries/exports",
    "summary": "导出选定多个人员库",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "reviseDeviceCommand": {
    "method": "POST",
    "path": "/device-commands/{taskId}/revisions",
    "summary": "重建参数配置任务",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "changeMyPassword": {
    "method": "POST",
    "path": "/me/password",
    "summary": "修改本人密码",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "deleteResourcesBatch": {
    "method": "POST",
    "path": "/batch-deletions",
    "summary": "批量软删选定记录",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "exportAnalysisTasks": {
    "method": "POST",
    "path": "/analysis-tasks/exports",
    "summary": "合并导出选定分析任务结果",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getImportTaskResult": {
    "method": "GET",
    "path": "/tasks/{taskId}/import-result",
    "summary": "读取成功导入结果和领域ID",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "revokeResourcesBatch": {
    "method": "POST",
    "path": "/batch-revocations",
    "summary": "批量撤销布控或通知计划",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "listActionApprovals": {
    "method": "GET",
    "path": "/action-approvals",
    "summary": "查询本人申请或待本人复核的审批",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "createActionApproval": {
    "method": "POST",
    "path": "/action-approvals",
    "summary": "创建冻结的敏感动作审批申请",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "getActionApproval": {
    "method": "GET",
    "path": "/action-approvals/{approvalId}",
    "summary": "查询受控审批详情",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "decideActionApproval": {
    "method": "POST",
    "path": "/action-approvals/{approvalId}/decisions",
    "summary": "提交独立复核决定",
    "permission": null,
    "pending": false,
    "pendingReason": null
  },
  "readDecryptionResult": {
    "method": "POST",
    "path": "/decrypt-requests/{requestId}/result",
    "summary": "指定接收人一次性查看受控解密结果",
    "permission": null,
    "pending": false,
    "pendingReason": null
  }
} as const;
