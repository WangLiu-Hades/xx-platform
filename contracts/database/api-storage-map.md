# API、需求与存储映射（DB-DESIGN-2）

依据：26页原始PDF、完整M01–M15工程实施文档、当前 api/openapi.yaml。沿用API逐项需求编号，工程实施要求优先；未定规则保持OPENAPI_PENDING中的P-001～P-021（P-012已收敛）。

MAPPED表示存储结构已有明确映射；PENDING_RULE表示结构已保留而业务规则仍待签认；NO_API_REQUIRED表示纯UI/部署/CI等不应创建业务表或REST接口。此矩阵不是生产实现或算法验收声明。

## M01–M15模块覆盖

| 模块 | 存储/复用能力 | 状态 |
| --- | --- | --- |
| M01 | 会话/CSRF、权限菜单、字典配置、受控文件、导入导出、幂等和统一任务；health/ready实时检测 | MAPPED；相关规则PENDING_RULE |
| M02 | 复用菜单/权限/字典/配置/Context；路由、复制、表格、状态组件无需业务表 | MAPPED + NO_API_REQUIRED |
| M03 | 四源公共采集及专属结果、源事件去重、接入/回放/补数、逐行错误、状态与源计数独立接入 | MAPPED；相关规则PENDING_RULE |
| M04 | 12类首页指标、数据归属与电围分类、按类型场所排行、去重趋势、操作统计、应用入口，复用metric定义/维度/桶 | MAPPED；相关规则PENDING_RULE |
| M05 | 四源过滤、详情、脱敏、游标、目标精确索引、人像候选、异步Excel和Context | MAPPED；相关规则PENDING_RULE |
| M06 | Context、统计、轨迹、位置追踪、布控、数据集、导出/解密；复制已脱敏文本属于前端 | MAPPED；相关规则PENDING_RULE |
| M07 | Point/LineString/Polygon/MultiPolygon、bbox/zoom聚合、地图设备、目标轨迹、时间轴、热力和区域统计；缩放/重置为前端 | MAPPED；相关规则PENDING_RULE |
| M08 | 分析领域ID与统一执行ID、三类参数/结果、选区、证据、版本修订、取消重试/分页/导出 | MAPPED；相关规则PENDING_RULE |
| M09 | TARGET/CAPTURE元素、固定版本、有效区间、授权共享、上下文/文件导入、增删/交并差/混合/预估/异步 | MAPPED；相关规则PENDING_RULE |
| M10 | 布控版本/目标/范围、实时告警及原事件、结束后统计、ONCE/DAILY通知计划、执行、收件人、三通道/模板/回执/重试 | MAPPED；相关规则PENDING_RULE |
| M11 | 人员库完整原始字段、人员/车辆档案、关联目标/证据/置信/历史/人工修订/合并拆分/冲突、常驻模型/参数/规则/标签/执行结果 | MAPPED；相关规则PENDING_RULE |
| M12 | 场所、设备、分组、位置/状态、DESIRED与REPORTED参数、批量命令/逐台尝试、导入导出、8类报表/历史预览/SINGLE XLSX与BUNDLE ZIP | MAPPED；相关规则PENDING_RULE |
| M13 | 复用M04指标/M07地图/M12报表与设备；短信猫完整遥测、12类异常、时/日/月桶及周季年窗口 | MAPPED；相关规则PENDING_RULE |
| M14 | 用户/角色/菜单/权限、分别保存用户/角色场所与数据范围、禁用/密码/删除引用保护、审计和短信日志/搜索/导出 | MAPPED；相关规则PENDING_RULE |
| M15 | 健康/就绪/必要组件状态、任务诊断；CI/CD、Docker、备份、压测、发布本身NO_API_REQUIRED | MAPPED + NO_API_REQUIRED |

## 逐项源需求（578项）

包含214项PDF操作、230项现有原型动作、14项跨页/系统流程以及逐条工程开发任务/验收要求。工程要求按模块服务边界复用，UI/CI/部署不额外持久化。PENDING_RULE行的物理存储已映射；待确认原因以对应操作x-pending-reason为准，P-012集合ID语法已收敛不再列作阻塞。

| 编号/来源 | 页面/要求 | API operationId | 存储表 | 状态/待确认 |
| --- | --- | --- | --- | --- |
| PDF-001 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 登录后自动打开个人首页 | login, getMe | auth.app_user, auth.csrf_session, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session | PENDING_RULE / P-001,P-002 |
| PDF-002 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 左上不同功率设备总量及在线率 | getDashboardSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-002,P-006 |
| PDF-003 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 左中各设备近7日采集 | getDashboardSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-002,P-006 |
| PDF-004 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 左下按设备类型切换场所前5排行 | getDashboardSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-002,P-006 |
| PDF-005 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 中上当日与累计采集定时更新且与入库区分 | getDashboardSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-002,P-006 |
| PDF-006 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 中左各类型累计入库 | getDashboardSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-002,P-006 |
| PDF-007 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 累计入库下方设备告警次数 | getDashboardSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-002,P-006 |
| PDF-008 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 中右地图区域悬停1秒显示分类数量 | listRegions, getMapAreaSummary | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| PDF-009 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 地图区域左击转数据应用并传区域 | createContext, getDeviceMapFeatures | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009,P-010 |
| PDF-010 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 中下三设备类型及三归属分类去重周数据切换 | getDashboardSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-002,P-006 |
| PDF-011 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 中下归属地Top10 | getDashboardSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-002,P-006 |
| PDF-012 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 右上四个应用入口跳转 | getDashboardSummary, getMe | auth.app_user, auth.csrf_session, auth.menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-001,P-002,P-006 |
| PDF-013 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 右中2G4G5G月折线与右上下载报表 | getDashboardSummary, exportMetrics, getTask, downloadFile | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, auth.menu, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-002,P-003,P-006 |
| PDF-014 / PDF p1-2；app.js::dashboard:72 | 系统首页 / 右下周操作折线三个图例独立隐藏 | getDashboardSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-002,P-006 |
| PDF-015 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 场所时间IMSI与IMEI组合搜索 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| PDF-016 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 同区域重置恢复条件 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| PDF-017 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 涉密角色右上密文解密且非涉密隐藏 | getMe, requestDecryption, getDecryptionRequest | analysis.async_task, audit.sensitive_action_review, auth.app_user, auth.csrf_session, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, operation.decryption_capture, operation.decryption_request, operation.decryption_result_grant | PENDING_RULE / P-001,P-002,P-008 |
| PDF-018 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 右侧数据集添加隐藏栏 | listDatasets, createDataset, importDatasetItems | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | PENDING_RULE / P-013 |
| PDF-019 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 列表每项真实右键菜单九项及顺序 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| PDF-020 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 勾选后复制数据 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| PDF-021 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 勾选后统计在右侧显示五项指标 | createContext, getCaptureStatistics | analysis.analysis_context, analysis.context_target, analysis.export_job, analysis.input_snapshot, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-009 |
| PDF-022 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 统计右栏检索伴随布控且多选禁用伴随 | createContext, searchCaptures, createAnalysisTask, createWatchRule | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-003,P-007,P-009,P-011,P-014,P-015,P-016 |
| PDF-023 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 轨迹跳地图研判并回填搜索栏 | createContext, queryTrajectory | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009 |
| PDF-024 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 伴随打开新建任务并带入所有时空信息 | createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-009,P-011 |
| PDF-025 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 检索回填搜索栏 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| PDF-026 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 布控新增临时任务 | createContext, createWatchRule, activateWatchRule | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-009,P-014,P-015,P-016 |
| PDF-027 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 位置追踪地图显示途径点 | createContext, queryTrajectory | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009 |
| PDF-028 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 无勾选右键区域统计打开新增弹窗 | createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-009,P-011 |
| PDF-029 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 无勾选右键添加当前条件全部数据集 | createContext, createDataset, importDatasetItems, getTask | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.set_job, analysis.set_operand, analysis.task_attempt, analysis.task_event, analysis.task_row_error | PENDING_RULE / P-003,P-009,P-013 |
| PDF-030 / PDF p2-4；app.js::searchPage:57 | 围栏检索 / 数据详情右上导出查询结果Excel | createContext, createExport, getTask, downloadFile | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003,P-009 |
| PDF-031 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 场所时间车牌组合搜索与重置 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| PDF-032 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 右侧数据集添加隐藏栏 | listDatasets, createDataset, importDatasetItems | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | PENDING_RULE / P-013 |
| PDF-033 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 九项真实右键菜单与选择约束 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| PDF-034 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 复制勾选数据 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| PDF-035 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 右侧五项统计 | createContext, getCaptureStatistics | analysis.analysis_context, analysis.context_target, analysis.export_job, analysis.input_snapshot, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-009 |
| PDF-036 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 轨迹跳转传目标 | createContext, queryTrajectory | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009 |
| PDF-037 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 伴随传入时空 | createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-009,P-011 |
| PDF-038 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 检索回填车牌条件 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| PDF-039 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 临时布控 | createContext, createWatchRule, activateWatchRule | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-009,P-014,P-015,P-016 |
| PDF-040 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 位置追踪 | createContext, queryTrajectory | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009 |
| PDF-041 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 无勾选区域统计弹窗 | createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-009,P-011 |
| PDF-042 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 无勾选当前结果添加数据集 | createContext, createDataset, importDatasetItems, getTask | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.set_job, analysis.set_operand, analysis.task_attempt, analysis.task_event, analysis.task_row_error | PENDING_RULE / P-003,P-009,P-013 |
| PDF-043 / PDF p4-5；app.js::searchPage:57 | 车牌检索 / 详情右上Excel导出 | createContext, createExport, getTask, downloadFile | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003,P-009 |
| PDF-044 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 场所时间MAC组合搜索与重置 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| PDF-045 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 右侧数据集添加隐藏栏 | listDatasets, createDataset, importDatasetItems | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | PENDING_RULE / P-013 |
| PDF-046 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 九项真实右键菜单与选择约束 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| PDF-047 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 复制勾选数据 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| PDF-048 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 统计右栏输出 | createContext, getCaptureStatistics | analysis.analysis_context, analysis.context_target, analysis.export_job, analysis.input_snapshot, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-009 |
| PDF-049 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 轨迹跳转传目标 | createContext, queryTrajectory | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009 |
| PDF-050 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 伴随传入时空 | createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-009,P-011 |
| PDF-051 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 检索回填MAC条件 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| PDF-052 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 临时布控 | createContext, createWatchRule, activateWatchRule | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-009,P-014,P-015,P-016 |
| PDF-053 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 位置追踪 | createContext, queryTrajectory | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009 |
| PDF-054 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 无勾选区域统计弹窗 | createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-009,P-011 |
| PDF-055 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 无勾选当前结果添加数据集 | createContext, createDataset, importDatasetItems, getTask | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.set_job, analysis.set_operand, analysis.task_attempt, analysis.task_event, analysis.task_row_error | PENDING_RULE / P-003,P-009,P-013 |
| PDF-056 / PDF p5-7；app.js::searchPage:57 | MAC检索 / 详情右上Excel导出 | createContext, createExport, getTask, downloadFile | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003,P-009 |
| PDF-057 / PDF p7-8；app.js::facePage:58 | 人像检索 / 场所时间搜索与重置 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| PDF-058 / PDF p7-8；app.js::facePage:58 | 人像检索 / 每项图片真实右键轨迹伴随布控位置追踪 | createContext, queryTrajectory, createAnalysisTask, createWatchRule | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-009,P-011,P-014,P-015,P-016 |
| PDF-059 / PDF p7-8；app.js::facePage:58 | 人像检索 / 悬停图片搜索同人默认相似度大于0 | createFaceSearchTask, getTask, listFaceSearchResults | analysis.async_task, analysis.face_search_job, analysis.face_search_result, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003,P-007 |
| PDF-060 / PDF p7-8；app.js::facePage:58 | 人像检索 / 搜图左上阈值筛选右侧结果 | createFaceSearchTask, getTask, listFaceSearchResults | analysis.async_task, analysis.face_search_job, analysis.face_search_result, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003,P-007 |
| PDF-061 / PDF p7-8；app.js::facePage:58 | 人像检索 / 右上上传照片自动搜图 | uploadFile, getFileAsset, createFaceSearchTask, getTask, listFaceSearchResults | analysis.async_task, analysis.face_search_job, analysis.face_search_result, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003,P-007 |
| PDF-062 / PDF p7-8；app.js::facePage:58 | 人像检索 / 悬停图片保存下载 | downloadFile | analysis.async_task, analysis.import_job, core.file_asset | MAPPED |
| PDF-063 / PDF p9；app.js::libraryPage:85 | 人员库 / 库名分类组合搜索与同位置重置 | listPersonLibraries | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_link | MAPPED |
| PDF-064 / PDF p9；app.js::libraryPage:85 | 人员库 / 列表上方新增库窗口 | createPersonLibrary | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_link | PENDING_RULE / P-002 |
| PDF-065 / PDF p9；app.js::libraryPage:85 | 人员库 / 库名进入库内详情与新增编辑人员 | getPersonLibrary, listPersonLibraryMembers, createLibraryPerson, createProfileCorrection, removePersonLibraryMember | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-002,P-016 |
| PDF-066 / PDF p9；app.js::libraryPage:85 | 人员库 / 右上导出选中库Excel | exportPersonLibraries, getTask, downloadFile | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_link, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003 |
| PDF-067 / PDF p9；app.js::libraryPage:85 | 人员库 / 右上导入文件确认更新列表 | getImportTemplate, uploadFile, getFileAsset, importPersonLibraries, getTask, getImportTaskResult, listTaskRowErrors | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_link, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset, core.import_column, core.import_template | PENDING_RULE / P-003,P-016 |
| PDF-068 / PDF p9；app.js::libraryPage:85 | 人员库 / 勾选删除库 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| PDF-069 / PDF p9；app.js::libraryPage:85 | 人员库 / 人员详情与布控人员选择联动 | listPersonLibraryMembers, getProfile, createContext, createWatchRule | analysis.analysis_context, analysis.context_target, analysis.export_job, analysis.import_job, analysis.input_snapshot, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-009,P-014,P-015,P-016 |
| PDF-070 / PDF p9-10；app.js::mapPage:75 | 地图研判 / 地图轨迹支持电围人脸车牌MAC | createContext, queryTrajectory | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009 |
| PDF-071 / PDF p9-10；app.js::mapPage:75 | 地图研判 / 场所时间IMSIIMEI搜索与列表 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| PDF-072 / PDF p9-10；app.js::mapPage:75 | 地图研判 / 蓝色设备点与时空轨迹 | getDeviceMapFeatures, queryTrajectory | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-010 |
| PDF-073 / PDF p9-10；app.js::mapPage:75 | 地图研判 / 右侧人员库十一项基本资料 | searchProfiles, getProfile | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| PDF-074 / PDF p9-10；app.js::mapPage:75 | 地图研判 / 搜索栏右侧框选拖拽设备右侧列表 | getDeviceMapFeatures, listDevices | analysis.export_job, analysis.import_job, capture.capture_event, capture.capture_target, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-010 |
| PDF-075 / PDF p9-10；app.js::mapPage:75 | 地图研判 / 搜索栏右侧单选点右侧列表 | getDeviceMapFeatures, listDevices | analysis.export_job, analysis.import_job, capture.capture_event, capture.capture_target, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-010 |
| PDF-076 / PDF p9-10；app.js::mapPage:75 | 地图研判 / 对选择点位和区域进行研判 | getMapAreaSummary, createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009,P-011 |
| PDF-077 / PDF p9-10；app.js::mapPage:75 | 地图研判 / 重置地图初始状态 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| PDF-078 / PDF p10-11；app.js::taskPage:83 | 伴随任务 / 任务名称分析日期搜索与重置 | listAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| PDF-079 / PDF p10-11；app.js::taskPage:83 | 伴随任务 / 列表上方新增伴随分析 | createContext, createAnalysisTask, getTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, analysis.task_attempt, analysis.task_event, analysis.task_row_error | PENDING_RULE / P-003,P-009,P-011 |
| PDF-080 / PDF p10-11；app.js::taskPage:83 | 伴随任务 / 任务名进入详情四类结果 | getAnalysisTask, listAnalysisResults, getAnalysisResult | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| PDF-081 / PDF p10-11；app.js::taskPage:83 | 伴随任务 / 关联度百分比输出 | listAnalysisResults | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-011 |
| PDF-082 / PDF p10-11；app.js::taskPage:83 | 伴随任务 / 详情任务编辑同新增配置 | getAnalysisTask, reviseAnalysisTask, getTask | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, analysis.task_attempt, analysis.task_event, analysis.task_row_error | PENDING_RULE / P-003,P-011 |
| PDF-083 / PDF p10-11；app.js::taskPage:83 | 伴随任务 / 结果二次检索轨迹伴随布控 | createContext, searchCaptures, queryTrajectory, createAnalysisTask, createWatchRule | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.device, core.metric_snapshot, core.region, core.site, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-003,P-007,P-009,P-011,P-014,P-015,P-016 |
| PDF-084 / PDF p10-11；app.js::taskPage:83 | 伴随任务 / 布控产生临时任务 | createContext, createWatchRule, activateWatchRule | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-009,P-014,P-015,P-016 |
| PDF-085 / PDF p10-11；app.js::taskPage:83 | 伴随任务 / 右上导出选中结果Excel | exportAnalysisResults, exportAnalysisTasks, getTask, downloadFile | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003 |
| PDF-086 / PDF p10-11；app.js::taskPage:83 | 伴随任务 / 勾选删除任务 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| PDF-087 / PDF p12-13；app.js::taskPage:83 | 碰撞任务 / 任务名称分析日期搜索与同区域重置 | listAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| PDF-088 / PDF p12-13；app.js::taskPage:83 | 碰撞任务 / 列表上方新增窗口 | createContext, createAnalysisTask, getTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, analysis.task_attempt, analysis.task_event, analysis.task_row_error | PENDING_RULE / P-003,P-009,P-011 |
| PDF-089 / PDF p12-13；app.js::taskPage:83 | 碰撞任务 / 任务名进入详情四类结果 | getAnalysisTask, listAnalysisResults, getAnalysisResult | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| PDF-090 / PDF p12-13；app.js::taskPage:83 | 碰撞任务 / 碰撞关联度百分比 | listAnalysisResults | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-011 |
| PDF-091 / PDF p12-13；app.js::taskPage:83 | 碰撞任务 / 详情任务编辑同新增配置 | getAnalysisTask, reviseAnalysisTask, getTask | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, analysis.task_attempt, analysis.task_event, analysis.task_row_error | PENDING_RULE / P-003,P-011 |
| PDF-092 / PDF p12-13；app.js::taskPage:83 | 碰撞任务 / 结果二次检索轨迹伴随布控 | createContext, searchCaptures, queryTrajectory, createAnalysisTask, createWatchRule | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.device, core.metric_snapshot, core.region, core.site, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-003,P-007,P-009,P-011,P-014,P-015,P-016 |
| PDF-093 / PDF p12-13；app.js::taskPage:83 | 碰撞任务 / 右上导出选中结果Excel | exportAnalysisResults, exportAnalysisTasks, getTask, downloadFile | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003 |
| PDF-094 / PDF p12-13；app.js::taskPage:83 | 碰撞任务 / 勾选删除任务 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| PDF-095 / PDF p13-14；app.js::taskPage:83 | 区域统计 / 任务名称分析日期搜索与同区域重置 | listAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| PDF-096 / PDF p13-14；app.js::taskPage:83 | 区域统计 / 列表上方新增窗口 | createContext, createAnalysisTask, getTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, analysis.task_attempt, analysis.task_event, analysis.task_row_error | PENDING_RULE / P-003,P-009,P-011 |
| PDF-097 / PDF p13-14；app.js::taskPage:83 | 区域统计 / 任务名详情电围车牌MAC结果 | getAnalysisTask, listAnalysisResults, getAnalysisResult | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| PDF-098 / PDF p13-14；app.js::taskPage:83 | 区域统计 / IMSI采集总次数站点选区指标 | listAnalysisResults | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-011 |
| PDF-099 / PDF p13-14；app.js::taskPage:83 | 区域统计 / 匹配度经过选区数除以总选区数 | listAnalysisResults | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-011 |
| PDF-100 / PDF p13-14；app.js::taskPage:83 | 区域统计 / 详情任务编辑同新增配置 | getAnalysisTask, reviseAnalysisTask, getTask | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, analysis.task_attempt, analysis.task_event, analysis.task_row_error | PENDING_RULE / P-003,P-011 |
| PDF-101 / PDF p13-14；app.js::taskPage:83 | 区域统计 / 结果二次检索轨迹伴随布控 | createContext, searchCaptures, queryTrajectory, createAnalysisTask, createWatchRule | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.device, core.metric_snapshot, core.region, core.site, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-003,P-007,P-009,P-011,P-014,P-015,P-016 |
| PDF-102 / PDF p13-14；app.js::taskPage:83 | 区域统计 / 右上导出选中结果Excel | exportAnalysisResults, exportAnalysisTasks, getTask, downloadFile | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003 |
| PDF-103 / PDF p13-14；app.js::taskPage:83 | 区域统计 / 勾选删除任务 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| PDF-104 / PDF p14-15；app.js::datasetPage:90 | 数据运算 / 平台添加与直接导入数据集 | createDataset, getImportTemplate, uploadFile, getFileAsset, importDatasetItems, getTask, getImportTaskResult | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.set_job, analysis.set_operand, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset, core.import_column, core.import_template | PENDING_RULE / P-003,P-013 |
| PDF-105 / PDF p14-15；app.js::datasetPage:90 | 数据运算 / 数据集拖动到右侧运算框 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| PDF-106 / PDF p14-15；app.js::datasetPage:90 | 数据运算 / 运算框下方交并差及混合运算 | previewSetTask, createSetTask, getTask, getSetTask, listSetTaskResults | analysis.async_task, analysis.dataset_item, analysis.dataset_version, analysis.import_job, analysis.import_row_result, analysis.set_job, analysis.set_operand, analysis.task_attempt, analysis.task_event, analysis.task_row_error | PENDING_RULE / P-003,P-007 |
| PDF-107 / PDF p14-15；app.js::datasetPage:90 | 数据运算 / 操作说明按钮 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| PDF-108 / PDF p14-15；app.js::datasetPage:90 | 数据运算 / 点击数据集右侧数据详情 | getDataset, listDatasetItems | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | PENDING_RULE / P-007,P-013 |
| PDF-109 / PDF p14-15；app.js::datasetPage:90 | 数据运算 / 数据集右上垃圾桶删除 | getDataset, deleteDataset | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | PENDING_RULE / P-013 |
| PDF-110 / PDF p14-15；app.js::datasetPage:90 | 数据运算 / 详情右上Excel导出 | exportDataset, getTask, downloadFile | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.set_job, analysis.set_operand, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003 |
| PDF-111 / PDF p14-15；app.js::datasetPage:90 | 数据运算 / 详情复制检索轨迹伴随布控位置追踪 | createContext, searchCaptures, queryTrajectory, createAnalysisTask, createWatchRule | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.device, core.metric_snapshot, core.region, core.site, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-003,P-007,P-009,P-011,P-014,P-015,P-016 |
| PDF-112 / PDF p14-15；app.js::datasetPage:90 | 数据运算 / 检索及研判右侧隐藏数据集栏与一键进入 | listDatasets, createDataset, importDatasetItems | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | PENDING_RULE / P-013 |
| PDF-113 / PDF p15；app.js::watchPage:93 | 综合布控 / 布控名称账号开始结束组合查询与重置 | listWatchRules | operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-014,P-016 |
| PDF-114 / PDF p15；app.js::watchPage:93 | 综合布控 / 列表上方新增四种数据布控 | resolveTargets, createContext, createWatchRule, activateWatchRule | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, analysis.profile_link, capture.capture_event, capture.capture_target, capture.target, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-004,P-009,P-014,P-015,P-016 |
| PDF-115 / PDF p15；app.js::watchPage:93 | 综合布控 / 人员库重点人员添加 | listPersonLibraries, listPersonLibraryMembers, createContext, createWatchRule | analysis.analysis_context, analysis.context_target, analysis.export_job, analysis.import_job, analysis.input_snapshot, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_link, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-009,P-014,P-015,P-016 |
| PDF-116 / PDF p15；app.js::watchPage:93 | 综合布控 / 选中后列表下方撤销布控 | revokeResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, operation.notification_plan, operation.watch_rule | MAPPED |
| PDF-117 / PDF p15；app.js::watchPage:93 | 综合布控 / 任务名进入详情结果检索伴随 | getWatchRule, listAlerts, getAlert, getWatchStatistics | capture.capture_event, operation.alert_event, operation.delivery, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-006,P-007,P-014,P-015,P-016,P-017 |
| PDF-118 / PDF p15；app.js::watchPage:93 | 综合布控 / 详情编辑信息弹窗 | getWatchRule, updateWatchRule | operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-014,P-016 |
| PDF-119 / PDF p15；app.js::watchPage:93 | 综合布控 / 结束后生成五项集群统计及二次操作 | getWatchStatistics, createContext, getCaptureStatistics, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-006,P-009,P-011,P-017 |
| PDF-120 / PDF p15；app.js::watchPage:93 | 综合布控 / 勾选删除任务 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| PDF-121 / PDF p15；app.js::watchPage:93 | 综合布控 / 命中状态与平台通知 | listAlerts, getAlert, listDeliveries, listPlatformMessages | capture.capture_event, operation.alert_event, operation.delivery, operation.delivery_attempt, operation.notification_recipient, operation.platform_message, operation.watch_rule_revision | PENDING_RULE / P-007,P-014,P-015,P-016 |
| PDF-122 / PDF p16；app.js::profilePage:97 | 图码档案 / 一人一档图像归档与基本资料 | searchProfiles, getProfile | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| PDF-123 / PDF p16；app.js::profilePage:97 | 图码档案 / 外貌或特征码搜索 | searchProfiles, resolveTargets | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile, capture.capture_event, capture.capture_target, capture.target | PENDING_RULE / P-004,P-016 |
| PDF-124 / PDF p16；app.js::profilePage:97 | 图码档案 / 综合轨迹查看 | listProfileTrajectories | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| PDF-125 / PDF p16；app.js::profilePage:97 | 图码档案 / 关联围栏MAC车牌人像数据拟合 | listProfileTargets, listProfileEvidence, listProfileHistory, listProfileConflicts, createProfileCorrection, mergeProfiles, splitProfile, resolveProfileConflict | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| PDF-126 / PDF p16；app.js::profilePage:97 | 车码档案 / 一车一档车牌归档与基本资料 | searchProfiles, getProfile | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| PDF-127 / PDF p16；app.js::profilePage:97 | 车码档案 / 车牌或特征码搜索 | searchProfiles, resolveTargets | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile, capture.capture_event, capture.capture_target, capture.target | PENDING_RULE / P-004,P-016 |
| PDF-128 / PDF p16；app.js::profilePage:97 | 车码档案 / 综合轨迹查看 | listProfileTrajectories | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| PDF-129 / PDF p16；app.js::profilePage:97 | 车码档案 / 关联围栏MAC车牌人像数据拟合 | listProfileTargets, listProfileEvidence, listProfileHistory, listProfileConflicts, createProfileCorrection, mergeProfiles, splitProfile, resolveProfileConflict | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| PDF-130 / PDF p16-17；app.js::modelPage:99 | 数据态势 / 常驻模型参数配置创建 | listTrendModels, createTrendModel, createModelParameterVersion, getModelParameterVersion | analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.trend_model | PENDING_RULE / P-017 |
| PDF-131 / PDF p16-17；app.js::modelPage:99 | 数据态势 / 符合参数自动标记标签 | runTrendModel, getTask, listTrendModelResults | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.task_attempt, analysis.task_event, analysis.task_row_error, analysis.trend_model | PENDING_RULE / P-003,P-017 |
| PDF-132 / PDF p16-17；app.js::modelPage:99 | 数据态势 / 按标签筛选有效数据 | listModelTags, listTrendModelResults | analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.trend_model | PENDING_RULE / P-017 |
| PDF-133 / PDF p17-18；app.js::opsPage:101 | 运维首页 / 左上设备总量在线数总及分类在线率 | getOperationSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-006,P-015 |
| PDF-134 / PDF p17-18；app.js::opsPage:101 | 运维首页 / 中部分类今日及累计采集 | getOperationSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-006,P-015 |
| PDF-135 / PDF p17-18；app.js::opsPage:101 | 运维首页 / 右上短信猫九项状态 | getOperationSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-006,P-015 |
| PDF-136 / PDF p17-18；app.js::opsPage:101 | 运维首页 / 下部三数据类型各四折线图 | getOperationSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-006,P-015 |
| PDF-137 / PDF p17-18；app.js::opsPage:101 | 运维首页 / 右上周月季度年范围 | getOperationSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-006,P-015 |
| PDF-138 / PDF p17-18；app.js::opsPage:101 | 运维首页 / 采集量四指标切换悬停日期值 | getOperationSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-006,P-015 |
| PDF-139 / PDF p17-18；app.js::opsPage:101 | 运维首页 / 异常设备四指标及Excel导出 | getOperationSummary, exportMetrics, getTask, downloadFile | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, auth.menu, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-003,P-006,P-015 |
| PDF-140 / PDF p17-18；app.js::opsPage:101 | 运维首页 / 异常数据五指标及悬停详情 | getOperationSummary | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-006,P-015 |
| PDF-141 / PDF p17-18；app.js::opsPage:101 | 运维首页 / 数据量异常设备三指标及Excel导出 | getOperationSummary, exportMetrics, getTask, downloadFile | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, auth.menu, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-003,P-006,P-015 |
| PDF-142 / PDF p18-19；app.js::adminPage:102 | 场所管理 / 名称区域查询与重置 | listSites | core.device, core.site | MAPPED |
| PDF-143 / PDF p18-19；app.js::adminPage:102 | 场所管理 / 点击结果看设备信息 | getSite, listDevices | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot, core.site | MAPPED |
| PDF-144 / PDF p18-19；app.js::adminPage:102 | 场所管理 / 新增未登记场所 | createSite | core.device, core.site | MAPPED |
| PDF-145 / PDF p18-19；app.js::adminPage:102 | 场所管理 / 编辑场所信息 | getSite, updateSite | core.device, core.site | MAPPED |
| PDF-146 / PDF p18-19；app.js::adminPage:102 | 场所管理 / 勾选删除场所 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| PDF-147 / PDF p19-20；app.js::adminPage:102 | 设备管理 / 场所名称序列号状态分类查询 | listDevices | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | MAPPED |
| PDF-148 / PDF p19-20；app.js::adminPage:102 | 设备管理 / 点击结果设备信息详情 | getDevice, getDeviceStatus | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-018,P-019 |
| PDF-149 / PDF p19-20；app.js::adminPage:102 | 设备管理 / 设备参数窗口查询修改远控 | getDeviceParameters, updateDeviceParameters, getTask, listDeviceCommandResults | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.sensitive_action_review, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot, operation.device_command, operation.device_command_attempt, operation.device_command_job | PENDING_RULE / P-003,P-018,P-019 |
| PDF-150 / PDF p19-20；app.js::adminPage:102 | 设备管理 / 设备离线记录 | listDeviceStatusHistory | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | MAPPED |
| PDF-151 / PDF p19-20；app.js::adminPage:102 | 设备管理 / 设备采集历史记录 | searchCaptures, getDeviceCaptureStatistics | analysis.analysis_context, analysis.export_job, analysis.import_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-003,P-006,P-007,P-009 |
| PDF-152 / PDF p19-20；app.js::adminPage:102 | 设备管理 / 重置条件 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| PDF-153 / PDF p19-20；app.js::adminPage:102 | 设备管理 / 新增设备窗口 | createDevice | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-018,P-019 |
| PDF-154 / PDF p19-20；app.js::adminPage:102 | 设备管理 / 选中设备复位数据变化 | createDeviceCommand, getTask, listDeviceCommandResults | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.sensitive_action_review, operation.device_command, operation.device_command_attempt, operation.device_command_job | PENDING_RULE / P-003,P-018,P-019 |
| PDF-155 / PDF p19-20；app.js::adminPage:102 | 设备管理 / 选中设备删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| PDF-156 / PDF p19-20；app.js::adminPage:102 | 设备管理 / 操作列导入设备信息 | getImportTemplate, uploadFile, getFileAsset, importDevices, getTask, getImportTaskResult | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.file_asset, core.import_column, core.import_template, core.metric_snapshot | PENDING_RULE / P-003,P-018 |
| PDF-157 / PDF p19-20；app.js::adminPage:102 | 设备管理 / 操作列Excel导出设备信息 | exportDevices, getTask, downloadFile | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.file_asset, core.metric_snapshot | PENDING_RULE / P-002,P-003 |
| PDF-158 / PDF p20,23；app.js::mapPage:75 | 设备分布 / 场所设备勾选在线状态关键字组合过滤 | getDeviceMapFeatures | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-010 |
| PDF-159 / PDF p20,23；app.js::mapPage:75 | 设备分布 / 点击聚合数字放大区域 | getDeviceMapFeatures | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-010 |
| PDF-160 / PDF p20,23；app.js::mapPage:75 | 设备分布 / 框选设备加入右侧列表 | getDeviceMapFeatures, listDevices | analysis.export_job, analysis.import_job, capture.capture_event, capture.capture_target, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-010 |
| PDF-161 / PDF p20,23；app.js::mapPage:75 | 设备分布 / 单选模式设备加入右侧列表 | getDeviceMapFeatures, listDevices | analysis.export_job, analysis.import_job, capture.capture_event, capture.capture_target, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-010 |
| PDF-162 / PDF p20,23；app.js::mapPage:75 | 设备分布 / 右栏下方场所详情跳管理带选区 | listSites, getSite | core.device, core.site | MAPPED |
| PDF-163 / PDF p20,23；app.js::mapPage:75 | 设备分布 / 右栏下方设备详情跳管理带选区 | listDevices, getDevice | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-018,P-019 |
| PDF-164 / PDF p20,23；app.js::mapPage:75 | 设备分布 / 区域在线率统计图 | getMapAreaSummary | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| PDF-165 / PDF p20,23；app.js::mapPage:75 | 设备分布 / 采集量颜色深浅热力图 | getMapHeatmap | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| PDF-166 / PDF p20,23；app.js::mapPage:75 | 设备分布 / 重置地图 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| PDF-167 / PDF p20-21；app.js::configPage:121 | 参数配置 / 分组名称设备名称搜索与重置 | listDeviceCommands | analysis.async_task, audit.sensitive_action_review, operation.device_command, operation.device_command_attempt, operation.device_command_job | PENDING_RULE / P-018,P-019 |
| PDF-168 / PDF p20-21；app.js::configPage:121 | 参数配置 / 勾选删除任务 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| PDF-169 / PDF p20-21；app.js::configPage:121 | 参数配置 / 新增窗口步骤1任务名称区域关联分组 | listRegions, listDeviceGroups, createDeviceCommand | analysis.async_task, audit.sensitive_action_review, core.device, core.device_group, core.device_group_member, core.region, operation.device_command, operation.device_command_attempt, operation.device_command_job | PENDING_RULE / P-018,P-019 |
| PDF-170 / PDF p20-21；app.js::configPage:121 | 参数配置 / 步骤2选择设备 | listDeviceGroupMembers, listDevices | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_group, core.device_group_member, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | MAPPED |
| PDF-171 / PDF p20-21；app.js::configPage:121 | 参数配置 / 步骤3参数批量设置反馈 | getDeviceParameters, createDeviceCommand, getTask, listDeviceCommandResults | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.sensitive_action_review, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot, operation.device_command, operation.device_command_attempt, operation.device_command_job | PENDING_RULE / P-003,P-018,P-019 |
| PDF-172 / PDF p20-21；app.js::configPage:121 | 参数配置 / 任务名详情 | getDeviceCommand, listDeviceCommandResults | analysis.async_task, audit.sensitive_action_review, operation.device_command, operation.device_command_attempt, operation.device_command_job | PENDING_RULE / P-018,P-019 |
| PDF-173 / PDF p20-21；app.js::configPage:121 | 参数配置 / 详情编辑任务 | getDeviceCommand, reviseDeviceCommand, getTask | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.sensitive_action_review, operation.device_command, operation.device_command_attempt, operation.device_command_job | PENDING_RULE / P-003,P-018,P-019 |
| PDF-174 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 任务名称创建时间搜索与重置 | listReports | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | MAPPED |
| PDF-175 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 左上新增单报表多报表不同条件 | createReport, getTask, getReport | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-003,P-006,P-020 |
| PDF-176 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 单报表设备数据系统三大类型 | createReport, getTask, getReport | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-003,P-006,P-020 |
| PDF-177 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 多报表默认全选报表类型 | createReport, getTask, getReport | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-003,P-006,P-020 |
| PDF-178 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 年月日报表不同内容 | createReport, getTask, getReport | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-003,P-006,P-020 |
| PDF-179 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 单报表任务名详情 | getReport, getReportPreview | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-006,P-020 |
| PDF-180 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 电围运营商区域设备数总览日期分时曲线 | getReport, getReportPreview | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-006,P-020 |
| PDF-181 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 电围区域设备数总量日期运营商采集图 | getReport, getReportPreview | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-006,P-020 |
| PDF-182 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 电围设备各设备资料及运营商采集图 | getReport, getReportPreview | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-006,P-020 |
| PDF-183 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 设备实时状态报表 | getReport, getReportPreview | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-006,P-020 |
| PDF-184 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 区域设备实时在线率报表 | getReport, getReportPreview | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-006,P-020 |
| PDF-185 / PDF p21-23；app.js::reportPage:125 | 运维报表 / MAC车牌数据报表 | getReport, getReportPreview | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-006,P-020 |
| PDF-186 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 系统组件状态报表 | getReport, getReportPreview | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-006,P-020 |
| PDF-187 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 单详情右上下载Excel | downloadReport | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | MAPPED |
| PDF-188 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 多报表无详情下载ZIP集合 | downloadReport | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | MAPPED |
| PDF-189 / PDF p21-23；app.js::reportPage:125 | 运维报表 / 勾选删除报表任务 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| PDF-190 / PDF p23-24；app.js::notificationPage:134 | 运维通知 / 通知任务名查询与重置 | listNotificationTasks | analysis.async_task, operation.delivery, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient | MAPPED |
| PDF-191 / PDF p23-24；app.js::notificationPage:134 | 运维通知 / 列表上方新增窗口 | listNotificationTemplates, createNotificationTask | analysis.async_task, operation.delivery, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient, operation.notification_template, operation.notification_template_variable | PENDING_RULE / P-014,P-015 |
| PDF-192 / PDF p23-24；app.js::notificationPage:134 | 运维通知 / 类型时间对象定时配置短信邮件平台 | listNotificationTemplates, createNotificationTask | analysis.async_task, operation.delivery, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient, operation.notification_template, operation.notification_template_variable | PENDING_RULE / P-014,P-015 |
| PDF-193 / PDF p23-24；app.js::notificationPage:134 | 运维通知 / 任务名详情 | getNotificationTask, listDeliveries | analysis.async_task, operation.delivery, operation.delivery_attempt, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient | PENDING_RULE / P-015 |
| PDF-194 / PDF p23-24；app.js::notificationPage:134 | 运维通知 / 上方选中撤销通知 | revokeResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, operation.notification_plan, operation.watch_rule | MAPPED |
| PDF-195 / PDF p23-24；app.js::notificationPage:134 | 运维通知 / 上方选中删除任务 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| PDF-196 / PDF p24-25；app.js::adminPage:102 | 角色管理 / 角色编号名称搜索 | listRoles | auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role | MAPPED |
| PDF-197 / PDF p24-25；app.js::adminPage:102 | 角色管理 / 新增角色窗口 | listPermissionCatalog, listMenuCatalog, listSites, createRole | auth.menu, auth.permission, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, core.device, core.site | PENDING_RULE / P-002 |
| PDF-198 / PDF p24-25；app.js::adminPage:102 | 角色管理 / 单击角色编号编辑基本信息 | getRole, updateRole | auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role | PENDING_RULE / P-002 |
| PDF-199 / PDF p24-25；app.js::adminPage:102 | 角色管理 / 菜单权限与场所权限保存生效 | listPermissionCatalog, listMenuCatalog, listSites, setRolePermissions, setRoleScopes, getMe | auth.app_user, auth.csrf_session, auth.menu, auth.permission, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.device, core.site | PENDING_RULE / P-001,P-002 |
| PDF-200 / PDF p24-25；app.js::adminPage:102 | 角色管理 / 测试角色无法修改系统设置 | getMe, getClientConfig | auth.app_user, auth.csrf_session, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary_binding, core.feature_config, core.upload_policy | PENDING_RULE / P-001,P-002 |
| PDF-201 / PDF p24-25；app.js::adminPage:102 | 角色管理 / 被用户占用角色禁止删除 | deleteRole | auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role | MAPPED |
| PDF-202 / PDF p24-25；app.js::adminPage:102 | 角色管理 / 勾选删除空闲角色 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| PDF-203 / PDF p25；app.js::adminPage:102 | 用户管理 / 编号名称账号查询 | listUsers | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site | MAPPED |
| PDF-204 / PDF p25；app.js::adminPage:102 | 用户管理 / 新增用户窗口 | listRoles, createUser | auth.app_user, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site | PENDING_RULE / P-002,P-021 |
| PDF-205 / PDF p25；app.js::adminPage:102 | 用户管理 / 单击用户编号编辑基本信息密码 | getUser, updateUser, changeUserPassword | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site | PENDING_RULE / P-002,P-021 |
| PDF-206 / PDF p25；app.js::adminPage:102 | 用户管理 / 关联角色权限与管理员最高权限 | listRoles, getMe, updateUser | auth.app_user, auth.csrf_session, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site, auth.user_session | PENDING_RULE / P-001,P-002,P-021 |
| PDF-207 / PDF p25；app.js::adminPage:102 | 用户管理 / 用户先禁用再上方删除 | disableUser, deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site, core.idempotency_request | PENDING_RULE / P-001,P-002,P-021 |
| PDF-208 / PDF p25；app.js::logPage:137 | 操作日志 / 每次操作形成日志 | searchAuditEvents, getAuditEvent | analysis.export_job, audit.audit_event | PENDING_RULE / P-021 |
| PDF-209 / PDF p25；app.js::logPage:137 | 操作日志 / 用户名操作时间查询与重置 | searchAuditEvents | analysis.export_job, audit.audit_event | PENDING_RULE / P-021 |
| PDF-210 / PDF p25；app.js::logPage:137 | 操作日志 / 超级管理员所有用户普通用户本人 | searchAuditEvents | analysis.export_job, audit.audit_event | PENDING_RULE / P-021 |
| PDF-211 / PDF p25；app.js::logPage:137 | 操作日志 / 右上导出当前账户所有操作Excel | exportAuditEvents, getTask, downloadFile | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.audit_event, core.file_asset | PENDING_RULE / P-003,P-021 |
| PDF-212 / PDF p26；app.js::logPage:137 | 短信日志 / 记录每次布控短信 | searchSmsLogs, getDelivery | operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE / P-015,P-021 |
| PDF-213 / PDF p26；app.js::logPage:137 | 短信日志 / 任务名接收号发送时间查询重置 | searchSmsLogs, getDelivery | operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE / P-015,P-021 |
| PDF-214 / PDF p26；app.js::logPage:137 | 短信日志 / 右上导出当前页面短信Excel | exportSmsLogs, getTask, downloadFile | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset, operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE / P-003,P-015,P-021 |
| DOM-001 / 当前浏览器DOM； | 系统首页 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-002 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 系统首页 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-003 / 当前浏览器DOM；home-seven／电围,home-seven／车牌,home-seven／MAC,home-dedup／日采集卡码量,home-fence／2G,home-fence／4G,home-fence／5G,home-actions／数据查询,home-actions／数据分析,home-actions／运维操作 | 系统首页 / chart-toggle；电围 / 车牌 / MAC / 日采集卡码量 / 2G / 4G / 5G / 数据查询 / 数据分析 / 运维操作 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-004 / 当前浏览器DOM；中心区,东区,西区,北区,南区 | 系统首页 / home-region；中心区 / 东区 / 西区 / 北区 / 南区 | createContext, getDeviceMapFeatures | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009,P-010 |
| DOM-005 / 当前浏览器DOM；围栏检索,伴随任务,地图研判,综合布控 | 系统首页 / nav；数据检索 / 数据研判 / 综合分析 / 布控预警 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-006 / 当前浏览器DOM；电围分类统计 | 系统首页 / chart-export:下载报表；下载报表 | exportMetrics, getTask, downloadFile | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, auth.menu, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-003,P-006 |
| DOM-007 / 当前浏览器DOM； | 围栏检索 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-008 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 围栏检索 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-009 / 当前浏览器DOM； | 围栏检索 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-010 / 当前浏览器DOM； | 围栏检索 / decrypt；密文解密 | requestDecryption, getDecryptionRequest | analysis.async_task, audit.sensitive_action_review, operation.decryption_capture, operation.decryption_request, operation.decryption_result_grant | PENDING_RULE / P-008 |
| DOM-011 / 当前浏览器DOM； | 围栏检索 / export-captures；导出 | createContext, createExport, getTask, downloadFile | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003,P-009 |
| DOM-012 / 当前浏览器DOM；captures／time,captures／target | 围栏检索 / sort；采集时间 ↕ / 目标标识 ↕ | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| DOM-013 / 当前浏览器DOM；D0,D1,D2,D3,D4 | 围栏检索 / device-info；设备 A-01 / 设备 A-02 / 设备 A-03 / 设备 A-04 / 设备 A-05 | getDevice | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-018,P-019 |
| DOM-014 / 当前浏览器DOM；captures／-1,captures／1 | 围栏检索 / page；上一页 / 下一页 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| DOM-015 / 当前浏览器DOM； | 围栏检索 / drawer；数据集 ▸ | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-016 / 当前浏览器DOM； | 车牌检索 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-017 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 车牌检索 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-018 / 当前浏览器DOM； | 车牌检索 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-019 / 当前浏览器DOM； | 车牌检索 / export-captures；导出 | createContext, createExport, getTask, downloadFile | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003,P-009 |
| DOM-020 / 当前浏览器DOM；captures／time,captures／target | 车牌检索 / sort；采集时间 ↕ / 目标标识 ↕ | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| DOM-021 / 当前浏览器DOM；D0,D1,D2,D3,D4 | 车牌检索 / device-info；设备 A-01 / 设备 A-02 / 设备 A-03 / 设备 A-04 / 设备 A-05 | getDevice | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-018,P-019 |
| DOM-022 / 当前浏览器DOM；captures／-1,captures／1 | 车牌检索 / page；上一页 / 下一页 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| DOM-023 / 当前浏览器DOM； | 车牌检索 / drawer；数据集 ▸ | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-024 / 当前浏览器DOM； | MAC检索 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-025 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | MAC检索 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-026 / 当前浏览器DOM； | MAC检索 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-027 / 当前浏览器DOM； | MAC检索 / export-captures；导出 | createContext, createExport, getTask, downloadFile | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.input_snapshot, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset | PENDING_RULE / P-003,P-009 |
| DOM-028 / 当前浏览器DOM；captures／time,captures／target | MAC检索 / sort；采集时间 ↕ / 目标标识 ↕ | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| DOM-029 / 当前浏览器DOM；D0,D1,D2,D3,D4 | MAC检索 / device-info；设备 A-01 / 设备 A-02 / 设备 A-03 / 设备 A-04 / 设备 A-05 | getDevice | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-018,P-019 |
| DOM-030 / 当前浏览器DOM；captures／-1,captures／1 | MAC检索 / page；上一页 / 下一页 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| DOM-031 / 当前浏览器DOM； | MAC检索 / drawer；数据集 ▸ | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-032 / 当前浏览器DOM； | 人像检索 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-033 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 人像检索 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-034 / 当前浏览器DOM； | 人像检索 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-035 / 当前浏览器DOM； | 人像检索 / upload-face；上传照片 | uploadFile, getFileAsset, createFaceSearchTask | analysis.async_task, analysis.face_search_job, analysis.face_search_result, analysis.import_job, core.file_asset | PENDING_RULE / P-007 |
| DOM-036 / 当前浏览器DOM；C3-0,C3-1,C3-2,C3-3,C3-4,C3-5,C3-6,C3-7,C3-8,C3-9,C3-10,C3-11,C3-12,C3-13,C3-14,C3-15,C3-16,C3-17 | 人像检索 / same-face；搜索 | createFaceSearchTask, listFaceSearchResults | analysis.async_task, analysis.face_search_job, analysis.face_search_result, core.file_asset | PENDING_RULE / P-007 |
| DOM-037 / 当前浏览器DOM；C3-0,C3-1,C3-2,C3-3,C3-4,C3-5,C3-6,C3-7,C3-8,C3-9,C3-10,C3-11,C3-12,C3-13,C3-14,C3-15,C3-16,C3-17 | 人像检索 / save-face；保存 | downloadFile | analysis.async_task, analysis.import_job, core.file_asset | MAPPED |
| DOM-038 / 当前浏览器DOM； | 人像检索 / drawer；数据集 ▸ | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-039 / 当前浏览器DOM； | 人员库 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-040 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 人员库 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-041 / 当前浏览器DOM； | 人员库 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-042 / 当前浏览器DOM； | 人员库 / add-library；新增 | createPersonLibrary | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_link | PENDING_RULE / P-002 |
| DOM-043 / 当前浏览器DOM；libraries | 人员库 / delete-scope；删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| DOM-044 / 当前浏览器DOM； | 人员库 / import-libraries；导入 | getImportTemplate, uploadFile, importPersonLibraries, getImportTaskResult | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_link, analysis.task_attempt, analysis.task_event, analysis.task_row_error, core.file_asset, core.import_column, core.import_template | PENDING_RULE / P-016 |
| DOM-045 / 当前浏览器DOM； | 人员库 / export-libraries；导出 | exportPersonLibraries | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_link | MAPPED |
| DOM-046 / 当前浏览器DOM；LIB0 | 人员库 / library-detail；重点人员示例库 | getPersonLibrary, listPersonLibraryMembers, createLibraryPerson | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_link | PENDING_RULE / P-002,P-016 |
| DOM-047 / 当前浏览器DOM；libraries／-1,libraries／1 | 人员库 / page；上一页 / 下一页 | listPersonLibraries | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_link | MAPPED |
| DOM-048 / 当前浏览器DOM； | 地图研判 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-049 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 地图研判 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-050 / 当前浏览器DOM； | 地图研判 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-051 / 当前浏览器DOM；box,single | 地图研判 / map-tool；框选 / 单选 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-052 / 当前浏览器DOM； | 地图研判 / map-reset；重置地图 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-053 / 当前浏览器DOM；D0,D1,D2,D3,D4,D5,D6,D7,D8,D9 | 地图研判 / map-point；1 / 2 / 3 / 4 / 5 / 6 / 7 / 8 / 9 / 10 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-054 / 当前浏览器DOM；0.25,-0.25 | 地图研判 / map-zoom；+ / − | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-055 / 当前浏览器DOM； | 地图研判 / map-layer；图层 | getMapFeatures, getDeviceMapFeatures | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-010 |
| DOM-056 / 当前浏览器DOM； | 地图研判 / map-heat；热力图 | getMapHeatmap | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| DOM-057 / 当前浏览器DOM； | 地图研判 / map-trajectory；展示轨迹 | createContext, queryTrajectory | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009 |
| DOM-058 / 当前浏览器DOM；map-results／time,map-results／target | 地图研判 / sort；采集时间 ↕ / 目标标识 ↕ | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| DOM-059 / 当前浏览器DOM；D0,D1,D2,D3,D4 | 地图研判 / device-info；设备 A-01 / 设备 A-02 / 设备 A-03 / 设备 A-04 / 设备 A-05 | getDevice | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-018,P-019 |
| DOM-060 / 当前浏览器DOM；map-results／-1,map-results／1 | 地图研判 / page；上一页 / 下一页 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| DOM-061 / 当前浏览器DOM；区域统计 | 地图研判 / map-analysis:区域统计；区域统计 | createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-009,P-011 |
| DOM-062 / 当前浏览器DOM；伴随任务 | 地图研判 / map-analysis:伴随分析；伴随分析 | createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-009,P-011 |
| DOM-063 / 当前浏览器DOM； | 地图研判 / drawer；数据集 ▸ | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-064 / 当前浏览器DOM； | 伴随任务 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-065 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 伴随任务 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-066 / 当前浏览器DOM； | 伴随任务 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-067 / 当前浏览器DOM； | 伴随任务 / add-analysis；新增伴随分析 | createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-009,P-011 |
| DOM-068 / 当前浏览器DOM；analysis | 伴随任务 / delete-scope；删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| DOM-069 / 当前浏览器DOM； | 伴随任务 / export-analysis；导出 | exportAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | MAPPED |
| DOM-070 / 当前浏览器DOM；analysis／name | 伴随任务 / sort；任务名称 ↕ | listAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| DOM-071 / 当前浏览器DOM；T00,T01 | 伴随任务 / analysis-detail；伴随分析示例 1 / 伴随分析示例 2 | getAnalysisTask, listAnalysisResults | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| DOM-072 / 当前浏览器DOM；analysis／-1,analysis／1 | 伴随任务 / page；上一页 / 下一页 | listAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| DOM-073 / 当前浏览器DOM； | 伴随任务 / drawer；数据集 ▸ | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-074 / 当前浏览器DOM； | 碰撞任务 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-075 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 碰撞任务 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-076 / 当前浏览器DOM； | 碰撞任务 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-077 / 当前浏览器DOM； | 碰撞任务 / add-analysis；新增 | createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-009,P-011 |
| DOM-078 / 当前浏览器DOM；analysis | 碰撞任务 / delete-scope；删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| DOM-079 / 当前浏览器DOM； | 碰撞任务 / export-analysis；导出 | exportAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | MAPPED |
| DOM-080 / 当前浏览器DOM；analysis／name | 碰撞任务 / sort；任务名称 ↕ | listAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| DOM-081 / 当前浏览器DOM；T10,T11 | 碰撞任务 / analysis-detail；碰撞分析示例 1 / 碰撞分析示例 2 | getAnalysisTask, listAnalysisResults | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| DOM-082 / 当前浏览器DOM；analysis／-1,analysis／1 | 碰撞任务 / page；上一页 / 下一页 | listAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| DOM-083 / 当前浏览器DOM； | 碰撞任务 / drawer；数据集 ▸ | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-084 / 当前浏览器DOM； | 区域统计 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-085 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 区域统计 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-086 / 当前浏览器DOM； | 区域统计 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-087 / 当前浏览器DOM； | 区域统计 / add-analysis；新增 | createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-009,P-011 |
| DOM-088 / 当前浏览器DOM；analysis | 区域统计 / delete-scope；删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| DOM-089 / 当前浏览器DOM； | 区域统计 / export-analysis；导出 | exportAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | MAPPED |
| DOM-090 / 当前浏览器DOM；analysis／name | 区域统计 / sort；任务名称 ↕ | listAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| DOM-091 / 当前浏览器DOM；T20,T21 | 区域统计 / analysis-detail；区域统计示例 1 / 区域统计示例 2 | getAnalysisTask, listAnalysisResults | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| DOM-092 / 当前浏览器DOM；analysis／-1,analysis／1 | 区域统计 / page；上一页 / 下一页 | listAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-003,P-011 |
| DOM-093 / 当前浏览器DOM； | 区域统计 / drawer；数据集 ▸ | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-094 / 当前浏览器DOM； | 数据运算 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-095 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 数据运算 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-096 / 当前浏览器DOM； | 数据运算 / import-dataset；导入 | uploadFile, importDatasetItems | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand, core.file_asset | MAPPED |
| DOM-097 / 当前浏览器DOM； | 数据运算 / add-dataset；添加数据集 | createDataset | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | PENDING_RULE / P-013 |
| DOM-098 / 当前浏览器DOM；SET0,SET1 | 数据运算 / select-dataset；检索集合 A / 检索集合 B | getDataset, listDatasetItems | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | PENDING_RULE / P-007,P-013 |
| DOM-099 / 当前浏览器DOM；SET0,SET1 | 数据运算 / delete-dataset；🗑 | deleteDataset | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | MAPPED |
| DOM-100 / 当前浏览器DOM； | 数据运算 / dataset-help；操作方法 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-101 / 当前浏览器DOM； | 数据运算 / compute-set；计算并保存 | previewSetTask, createSetTask | analysis.async_task, analysis.dataset_item, analysis.dataset_version, analysis.set_job, analysis.set_operand | MAPPED |
| DOM-102 / 当前浏览器DOM；dataset-results | 数据运算 / export-scope；导出 | exportDataset | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | MAPPED |
| DOM-103 / 当前浏览器DOM；dataset-results／time,dataset-results／target | 数据运算 / sort；采集时间 ↕ / 目标标识 ↕ | listDatasetItems | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | PENDING_RULE / P-007 |
| DOM-104 / 当前浏览器DOM；D0,D6,D2 | 数据运算 / device-info；设备 A-01 / 设备 A-07 / 设备 A-03 | getDevice | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-018,P-019 |
| DOM-105 / 当前浏览器DOM；dataset-results／-1,dataset-results／1 | 数据运算 / page；上一页 / 下一页 | listDatasetItems | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | PENDING_RULE / P-007 |
| DOM-106 / 当前浏览器DOM；复制 | 数据运算 / dataset-action:复制；复制 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-107 / 当前浏览器DOM；检索 | 数据运算 / dataset-action:检索；检索 | searchCaptures | analysis.analysis_context, analysis.export_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup | PENDING_RULE / P-003,P-007,P-009 |
| DOM-108 / 当前浏览器DOM；轨迹 | 数据运算 / dataset-action:轨迹；轨迹 | createContext, queryTrajectory | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009 |
| DOM-109 / 当前浏览器DOM；伴随 | 数据运算 / dataset-action:伴随；伴随 | createContext, createAnalysisTask | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | PENDING_RULE / P-009,P-011 |
| DOM-110 / 当前浏览器DOM；布控 | 数据运算 / dataset-action:布控；布控 | createContext, createWatchRule | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-009,P-014,P-015,P-016 |
| DOM-111 / 当前浏览器DOM；位置追踪 | 数据运算 / dataset-action:位置追踪；位置追踪 | createContext, queryTrajectory | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-009 |
| DOM-112 / 当前浏览器DOM； | 综合布控 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-113 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 综合布控 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-114 / 当前浏览器DOM； | 综合布控 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-115 / 当前浏览器DOM； | 综合布控 / add-watch；新增 | resolveTargets, createContext, createWatchRule | analysis.analysis_context, analysis.context_target, analysis.input_snapshot, analysis.profile_link, capture.capture_event, capture.capture_target, capture.target, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-004,P-009,P-014,P-015,P-016 |
| DOM-116 / 当前浏览器DOM；watches | 综合布控 / delete-scope；删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| DOM-117 / 当前浏览器DOM；W0,W1 | 综合布控 / watch-detail；示例布控运行任务 / 示例已结束布控 | getWatchRule, listAlerts, getWatchStatistics | capture.capture_event, operation.alert_event, operation.delivery, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-006,P-014,P-016,P-017 |
| DOM-118 / 当前浏览器DOM；watches／-1,watches／1 | 综合布控 / page；上一页 / 下一页 | listWatchRules | operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-014,P-016 |
| DOM-119 / 当前浏览器DOM； | 综合布控 / revoke-watch；撤销布控 | revokeResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, operation.notification_plan, operation.watch_rule | MAPPED |
| DOM-120 / 当前浏览器DOM； | 告警结果 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-121 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 告警结果 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-122 / 当前浏览器DOM； | 告警结果 / reset-query；重置条件 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-123 / 当前浏览器DOM；alerts／-1,alerts／1 | 告警结果 / page；上一页 / 下一页 | listAlerts | capture.capture_event, operation.alert_event, operation.delivery, operation.watch_rule_revision | PENDING_RULE / P-014 |
| DOM-124 / 当前浏览器DOM； | 图码档案 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-125 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 图码档案 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-126 / 当前浏览器DOM； | 图码档案 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-127 / 当前浏览器DOM；P0,P1,P2,P3,P4 | 图码档案 / profile-detail；示例人员1 / 示例人员2 / 示例人员3 / 示例人员4 / 示例人员5 | getProfile, listProfileTargets, listProfileEvidence | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| DOM-128 / 当前浏览器DOM；P0,P1,P2,P3,P4 | 图码档案 / profile-track；综合轨迹 | listProfileTrajectories | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| DOM-129 / 当前浏览器DOM；profiles／-1,profiles／1 | 图码档案 / page；上一页 / 下一页 | searchProfiles | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| DOM-130 / 当前浏览器DOM； | 车码档案 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-131 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 车码档案 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-132 / 当前浏览器DOM； | 车码档案 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-133 / 当前浏览器DOM；示A10000,示A10001,示A10002,示A10003,示A10004 | 车码档案 / profile-detail；示A10000 / 示A10001 / 示A10002 / 示A10003 / 示A10004 | getProfile, listProfileTargets, listProfileEvidence | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| DOM-134 / 当前浏览器DOM；示A10000,示A10001,示A10002,示A10003,示A10004 | 车码档案 / profile-track；综合轨迹 | listProfileTrajectories | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| DOM-135 / 当前浏览器DOM；profiles／-1,profiles／1 | 车码档案 / page；上一页 / 下一页 | searchProfiles | analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile | PENDING_RULE / P-016 |
| DOM-136 / 当前浏览器DOM； | 数据态势 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-137 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 数据态势 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-138 / 当前浏览器DOM； | 数据态势 / add-model；新增常驻模型 | createTrendModel | analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.trend_model | PENDING_RULE / P-017 |
| DOM-139 / 当前浏览器DOM；M0 | 数据态势 / edit-model；配置参数 | getTrendModel, createModelParameterVersion | analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.trend_model | PENDING_RULE / P-016,P-017 |
| DOM-140 / 当前浏览器DOM；models／-1,models／1,labeled／-1,labeled／1 | 数据态势 / page；上一页 / 下一页 | listTrendModels | analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.trend_model | PENDING_RULE / P-017 |
| DOM-141 / 当前浏览器DOM； | 数据态势 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-142 / 当前浏览器DOM； | 运维首页 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-143 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 运维首页 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-144 / 当前浏览器DOM；ops-0／当日采集,ops-1／未登记设备,ops-2／未登记数据数量,ops-3／在线无数据的设备量 | 运维首页 / chart-toggle；当日采集 / 未登记设备 / 未登记数据数量 / 在线无数据的设备量 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-145 / 当前浏览器DOM；异常设备,数据量异常设备 | 运维首页 / chart-export:↗ 导出；↗ 导出 | exportMetrics, getTask, downloadFile | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, auth.menu, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE / P-003,P-006 |
| DOM-146 / 当前浏览器DOM； | 场所管理 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-147 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 场所管理 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-148 / 当前浏览器DOM； | 场所管理 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-149 / 当前浏览器DOM；sites | 场所管理 / add-admin；新增 | createSite | core.device, core.site | MAPPED |
| DOM-150 / 当前浏览器DOM；sites | 场所管理 / delete-scope；删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| DOM-151 / 当前浏览器DOM；S0,S1,S2,S3,S4 | 场所管理 / site-info；中心广场 / 东区车站 / 西区公园 / 北区路口 / 南区商场 | getSite, listDevices | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot, core.site | MAPPED |
| DOM-152 / 当前浏览器DOM；sites／S0,sites／S1,sites／S2,sites／S3,sites／S4 | 场所管理 / edit-admin；编辑 | updateSite | core.device, core.site | MAPPED |
| DOM-153 / 当前浏览器DOM；sites／-1,sites／1 | 场所管理 / page；上一页 / 下一页 | listSites | core.device, core.site | MAPPED |
| DOM-154 / 当前浏览器DOM； | 设备管理 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-155 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 设备管理 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-156 / 当前浏览器DOM； | 设备管理 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-157 / 当前浏览器DOM；devices | 设备管理 / add-admin；新增 | createDevice | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-018,P-019 |
| DOM-158 / 当前浏览器DOM；devices | 设备管理 / delete-scope；删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| DOM-159 / 当前浏览器DOM； | 设备管理 / reset-devices；复位 | createDeviceCommand, listDeviceCommandResults | analysis.async_task, audit.sensitive_action_review, operation.device_command, operation.device_command_attempt, operation.device_command_job | PENDING_RULE / P-018,P-019 |
| DOM-160 / 当前浏览器DOM；D0,D1,D2,D3,D4 | 设备管理 / device-info；设备 A-01 / 设备 A-02 / 设备 A-03 / 设备 A-04 / 设备 A-05 | getDevice | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-018,P-019 |
| DOM-161 / 当前浏览器DOM；D0／参数,D1／参数,D2／参数,D3／参数,D4／参数 | 设备管理 / device-operation:参数；参数 | getDeviceParameters, updateDeviceParameters | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-018,P-019 |
| DOM-162 / 当前浏览器DOM；D0／离线记录,D1／离线记录,D2／离线记录,D3／离线记录,D4／离线记录 | 设备管理 / device-operation:离线记录；离线记录 | listDeviceStatusHistory | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | MAPPED |
| DOM-163 / 当前浏览器DOM；D0／历史记录,D1／历史记录,D2／历史记录,D3／历史记录,D4／历史记录 | 设备管理 / device-operation:历史记录；历史记录 | searchCaptures, getDeviceCaptureStatistics | analysis.analysis_context, analysis.export_job, analysis.import_job, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-003,P-006,P-007,P-009 |
| DOM-164 / 当前浏览器DOM；D0／导入,D1／导入,D2／导入,D3／导入,D4／导入 | 设备管理 / device-operation:导入；导入 | uploadFile, importDevices | analysis.async_task, analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.file_asset, core.metric_snapshot | PENDING_RULE / P-018 |
| DOM-165 / 当前浏览器DOM；D0／导出,D1／导出,D2／导出,D3／导出,D4／导出 | 设备管理 / device-operation:导出；导出 | exportDevices | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-002 |
| DOM-166 / 当前浏览器DOM；devices／-1,devices／1 | 设备管理 / page；上一页 / 下一页 | listDevices | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | MAPPED |
| DOM-167 / 当前浏览器DOM； | 设备分布 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-168 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 设备分布 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-169 / 当前浏览器DOM； | 设备分布 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-170 / 当前浏览器DOM；box,single | 设备分布 / map-tool；框选 / 手动选择 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-171 / 当前浏览器DOM； | 设备分布 / map-reset；重置地图 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-172 / 当前浏览器DOM；中心区,东区,西区,北区,南区 | 设备分布 / map-cluster；2 | getDeviceMapFeatures | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-010 |
| DOM-173 / 当前浏览器DOM；D0,D1,D2,D3,D4,D5,D6,D7,D8,D9 | 设备分布 / map-point；1 / 2 / 3 / 4 / 5 / 6 / 7 / 8 / 9 / 10 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-174 / 当前浏览器DOM；0.25,-0.25 | 设备分布 / map-zoom；+ / − | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-175 / 当前浏览器DOM； | 设备分布 / map-layer；图层 | getMapFeatures, getDeviceMapFeatures | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | PENDING_RULE / P-010 |
| DOM-176 / 当前浏览器DOM； | 设备分布 / map-heat；热力图 | getMapHeatmap | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| DOM-177 / 当前浏览器DOM； | 设备分布 / map-sites；场所详情 | listSites, getSite | core.device, core.site | MAPPED |
| DOM-178 / 当前浏览器DOM； | 设备分布 / map-devices；设备详情 | listDevices, getDevice | analysis.export_job, analysis.import_job, capture.capture_event, core.device, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot | PENDING_RULE / P-018,P-019 |
| DOM-179 / 当前浏览器DOM； | 参数配置 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-180 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 参数配置 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-181 / 当前浏览器DOM； | 参数配置 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-182 / 当前浏览器DOM； | 参数配置 / add-config；新增 | listDeviceGroups, listDeviceGroupMembers, createDeviceCommand | analysis.async_task, audit.sensitive_action_review, core.device, core.device_group, core.device_group_member, operation.device_command, operation.device_command_attempt, operation.device_command_job | PENDING_RULE / P-018,P-019 |
| DOM-183 / 当前浏览器DOM；configs | 参数配置 / delete-scope；删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| DOM-184 / 当前浏览器DOM；CFG0 | 参数配置 / config-detail；核心组参数任务 | getDeviceCommand, listDeviceCommandResults | analysis.async_task, audit.sensitive_action_review, operation.device_command, operation.device_command_attempt, operation.device_command_job | PENDING_RULE / P-018,P-019 |
| DOM-185 / 当前浏览器DOM；configs／-1,configs／1 | 参数配置 / page；上一页 / 下一页 | listDeviceCommands | analysis.async_task, audit.sensitive_action_review, operation.device_command, operation.device_command_attempt, operation.device_command_job | PENDING_RULE / P-018,P-019 |
| DOM-186 / 当前浏览器DOM； | 运维报表 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-187 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 运维报表 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-188 / 当前浏览器DOM； | 运维报表 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-189 / 当前浏览器DOM； | 运维报表 / add-report；新增 | createReport | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-006,P-020 |
| DOM-190 / 当前浏览器DOM；reports | 运维报表 / delete-scope；删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| DOM-191 / 当前浏览器DOM；RP0 | 运维报表 / report-detail；电围区域日统计 | getReport, getReportPreview | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE / P-006,P-020 |
| DOM-192 / 当前浏览器DOM；RP0,RP1 | 运维报表 / download-report；下载 | downloadReport | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | MAPPED |
| DOM-193 / 当前浏览器DOM；reports／-1,reports／1 | 运维报表 / page；上一页 / 下一页 | listReports | analysis.async_task, core.file_asset, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | MAPPED |
| DOM-194 / 当前浏览器DOM； | 运维通知 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-195 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 运维通知 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-196 / 当前浏览器DOM； | 运维通知 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-197 / 当前浏览器DOM； | 运维通知 / add-notification；新增 | listNotificationTemplates, createNotificationTask | analysis.async_task, operation.delivery, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient, operation.notification_template, operation.notification_template_variable | PENDING_RULE / P-014,P-015 |
| DOM-198 / 当前浏览器DOM； | 运维通知 / revoke-notification；撤销通知 | revokeResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, operation.notification_plan, operation.watch_rule | MAPPED |
| DOM-199 / 当前浏览器DOM；notifications | 运维通知 / delete-scope；删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| DOM-200 / 当前浏览器DOM；N0 | 运维通知 / notification-detail；设备状态日通知 | getNotificationTask, listDeliveries | analysis.async_task, operation.delivery, operation.delivery_attempt, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient | PENDING_RULE / P-015 |
| DOM-201 / 当前浏览器DOM；N0 | 运维通知 / simulate-notification；模拟到达通知时间 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-202 / 当前浏览器DOM；notifications／-1,notifications／1 | 运维通知 / page；上一页 / 下一页 | listNotificationTasks | analysis.async_task, operation.delivery, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient | MAPPED |
| DOM-203 / 当前浏览器DOM； | 用户管理 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-204 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 用户管理 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-205 / 当前浏览器DOM； | 用户管理 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-206 / 当前浏览器DOM；users | 用户管理 / add-admin；新增 | createUser | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site | PENDING_RULE / P-002,P-021 |
| DOM-207 / 当前浏览器DOM；users | 用户管理 / delete-scope；删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| DOM-208 / 当前浏览器DOM；users／U0,users／U1,users／U2,users／U3,users／U4 | 用户管理 / edit-admin；U0 / U1 / U2 / U3 / U4 | updateUser | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site | PENDING_RULE / P-002,P-021 |
| DOM-209 / 当前浏览器DOM；users／-1,users／1 | 用户管理 / page；上一页 / 下一页 | listUsers | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site | MAPPED |
| DOM-210 / 当前浏览器DOM； | 角色管理 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-211 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 角色管理 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-212 / 当前浏览器DOM； | 角色管理 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-213 / 当前浏览器DOM；roles | 角色管理 / add-admin；新增 | createRole | auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role | PENDING_RULE / P-002 |
| DOM-214 / 当前浏览器DOM；roles | 角色管理 / delete-scope；删除 | deleteResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, core.idempotency_request | MAPPED |
| DOM-215 / 当前浏览器DOM；roles／R0,roles／R1,roles／R2,roles／R3,roles／R4 | 角色管理 / edit-admin；R0 / R1 / R2 / R3 / R4 | updateRole | auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role | PENDING_RULE / P-002 |
| DOM-216 / 当前浏览器DOM；roles／-1,roles／1 | 角色管理 / page；上一页 / 下一页 | listRoles | auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role | MAPPED |
| DOM-217 / 当前浏览器DOM； | 操作日志 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-218 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 操作日志 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-219 / 当前浏览器DOM； | 操作日志 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-220 / 当前浏览器DOM； | 操作日志 / export-logs；导出日志 | exportAuditEvents | analysis.export_job, audit.audit_event | PENDING_RULE / P-021 |
| DOM-221 / 当前浏览器DOM；logs／-1,logs／1 | 操作日志 / page；上一页 / 下一页 | searchAuditEvents | analysis.export_job, audit.audit_event | PENDING_RULE / P-021 |
| DOM-222 / 当前浏览器DOM； | 短信日志 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-223 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 短信日志 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-224 / 当前浏览器DOM； | 短信日志 / reset-query；重置 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-225 / 当前浏览器DOM； | 短信日志 / export-logs；导出日志 | exportSmsLogs | operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE / P-015,P-021 |
| DOM-226 / 当前浏览器DOM；sms／-1,sms／1 | 短信日志 / page；上一页 / 下一页 | searchSmsLogs | operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE / P-015,P-021 |
| DOM-227 / 当前浏览器DOM； | 任务中心 / back；返回 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-228 / 当前浏览器DOM；data,loading,empty,error,permission,partial,offline | 任务中心 / state；正常 / 加载 / 空结果 / 失败/超时 / 无权限 / 部分失败 / 离线 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-229 / 当前浏览器DOM；伴随任务,碰撞任务,区域统计 | 任务中心 / nav；前往 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| DOM-230 / 当前浏览器DOM；all-tasks／-1,all-tasks／1 | 任务中心 / page；上一页 / 下一页 | listTasks | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error | PENDING_RULE / P-003 |
| SYS-001 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 应用壳 / 登录/登出/续期/本人改密 | getCsrfToken, login, getMe, renewSession, logout, changeMyPassword | auth.app_user, auth.csrf_session, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session | PENDING_RULE / P-001,P-002,P-021 |
| SYS-002 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 应用壳 / 平台消息列表/徽标 | listPlatformMessages | operation.delivery, operation.platform_message | MAPPED |
| SYS-003 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 所有列表 / 读取页面字典/配置/权限 | getDictionary, getClientConfig, getMe | auth.app_user, auth.csrf_session, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary, core.dictionary_binding, core.dictionary_item, core.feature_config, core.upload_policy | PENDING_RULE / P-001,P-002 |
| SYS-004 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 任务中心 / 取消/失败重试/尝试记录/错误行 | getTask, cancelTask, retryTask, listTaskAttempts, listTaskRowErrors, getImportTaskResult | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error | PENDING_RULE / P-003 |
| SYS-005 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 检索右键/详情 / 选中对象全部目标动作 | getCapture, createContext, getCaptureStatistics, queryTrajectory, createAnalysisTask, createWatchRule | analysis.analysis_context, analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.device, core.metric_snapshot, core.region, core.site, operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-002,P-007,P-009,P-011,P-014,P-015,P-016 |
| SYS-006 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 人员库弹窗 / 编辑人员/删除成员/照片上传 | createProfileCorrection, removePersonLibraryMember, uploadFile, getFileAsset | analysis.async_task, analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.vehicle_profile, core.file_asset | PENDING_RULE / P-016 |
| SYS-007 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 通知弹窗 / 编辑现有计划且保留敏感收件人 | getNotificationTask, updateNotificationTask, listNotificationTemplates | analysis.async_task, operation.delivery, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient, operation.notification_template, operation.notification_template_variable | PENDING_RULE / P-014,P-015 |
| SYS-008 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 布控弹窗 / 编辑/激活/撤销/最终统计 | getWatchRule, updateWatchRule, activateWatchRule, revokeWatchRule, getWatchStatistics | operation.alert_event, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE / P-006,P-014,P-015,P-016,P-017 |
| SYS-009 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 模型弹窗 / 执行及参数版本/结果详情 | listModelParameterVersions, createModelParameterVersion, runTrendModel, getTask, listTrendModelResults, getTrendModelResult | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.task_attempt, analysis.task_event, analysis.task_row_error, analysis.trend_model | PENDING_RULE / P-003,P-016,P-017 |
| SYS-010 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 角色弹窗 / 权限目录/菜单/场所范围 | listPermissionCatalog, listMenuCatalog, listSites, setRolePermissions, setRoleScopes | auth.menu, auth.permission, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, core.device, core.site | PENDING_RULE / P-002 |
| SYS-011 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 设备弹窗 / 编辑元数据/组成员/配置任务修订删除重试 | updateDevice, listDeviceGroups, getDeviceGroup, createDeviceGroup, updateDeviceGroup, deleteDeviceGroup, listDeviceGroupMembers, addDeviceGroupMembers, removeDeviceGroupMember, reviseDeviceCommand, deleteDeviceCommand, retryDeviceCommand | analysis.async_task, analysis.export_job, analysis.import_job, audit.sensitive_action_review, capture.capture_event, core.device, core.device_group, core.device_group_member, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.metric_snapshot, operation.device_command, operation.device_command_attempt, operation.device_command_job | PENDING_RULE / P-018,P-019 |
| SYS-012 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 集合抽屉 / 重命名/追加/移除/版本 | updateDataset, addDatasetItems, removeDatasetItem, listDatasetVersions | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | PENDING_RULE / P-013 |
| SYS-013 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 后台M03 / 接入批次/质量/回放/补数 | listIngestionBatches, createIngestionBatch, getIngestionBatch, listIngestionErrors, getIngestionQualitySummary, replayIngestionBatch, createIngestionBackfill | analysis.async_task, capture.ingestion_batch, capture.ingestion_item, capture.ingestion_source, capture.quality_issue, capture.source_counter_sample, capture.source_event, capture.source_mapping_version, core.device_status_event | PENDING_RULE / P-004,P-005 |
| SYS-014 / SD03/SD04/SD06/SD08；app.js弹窗与表单提交逻辑 | 探针M15 / 存活/就绪 | getHealth, getReadiness | 无需业务持久化 | MAPPED |
| ENG-001 / S2 M01 开发任务 | M01 / 设计 API Gateway、服务边界、版本策略、统一响应体、错误码、异常处理和 TraceId 传递。 | getCsrfToken, login, logout, renewSession, getMe, getDictionary, getClientConfig, uploadFile, getFileAsset, downloadFile, createExport, listTasks, getTask, cancelTask, retryTask, listTaskAttempts, listTaskRowErrors, listPermissionCatalog, listMenuCatalog, listRegions, getImportTemplate, changeMyPassword, deleteResourcesBatch, getImportTaskResult | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.audit_event, auth.app_user, auth.csrf_session, auth.menu, auth.permission, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary, core.dictionary_binding, core.dictionary_item, core.feature_config, core.file_asset, core.idempotency_request, core.import_column, core.import_template, core.region, core.upload_policy | PENDING_RULE |
| ENG-002 / S2 M01 开发任务 | M01 / 建立用户认证、Token 生命周期、RBAC、菜单权限、数据权限、登录失效和无权访问处理。 | getCsrfToken, login, logout, renewSession, getMe, getDictionary, getClientConfig, uploadFile, getFileAsset, downloadFile, createExport, listTasks, getTask, cancelTask, retryTask, listTaskAttempts, listTaskRowErrors, listPermissionCatalog, listMenuCatalog, listRegions, getImportTemplate, changeMyPassword, deleteResourcesBatch, getImportTaskResult | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.audit_event, auth.app_user, auth.csrf_session, auth.menu, auth.permission, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary, core.dictionary_binding, core.dictionary_item, core.feature_config, core.file_asset, core.idempotency_request, core.import_column, core.import_template, core.region, core.upload_policy | PENDING_RULE |
| ENG-003 / S2 M01 开发任务 | M01 / 提供统一分页、排序、筛选、文件导入导出、字典、ID、时间和地理坐标处理规范。 | getCsrfToken, login, logout, renewSession, getMe, getDictionary, getClientConfig, uploadFile, getFileAsset, downloadFile, createExport, listTasks, getTask, cancelTask, retryTask, listTaskAttempts, listTaskRowErrors, listPermissionCatalog, listMenuCatalog, listRegions, getImportTemplate, changeMyPassword, deleteResourcesBatch, getImportTaskResult | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.audit_event, auth.app_user, auth.csrf_session, auth.menu, auth.permission, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary, core.dictionary_binding, core.dictionary_item, core.feature_config, core.file_asset, core.idempotency_request, core.import_column, core.import_template, core.region, core.upload_policy | PENDING_RULE |
| ENG-004 / S2 M01 开发任务 | M01 / 建立统一日志、审计事件、配置中心、定时任务、消息/事件发布和幂等处理机制。 | getCsrfToken, login, logout, renewSession, getMe, getDictionary, getClientConfig, uploadFile, getFileAsset, downloadFile, createExport, listTasks, getTask, cancelTask, retryTask, listTaskAttempts, listTaskRowErrors, listPermissionCatalog, listMenuCatalog, listRegions, getImportTemplate, changeMyPassword, deleteResourcesBatch, getImportTaskResult | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.audit_event, auth.app_user, auth.csrf_session, auth.menu, auth.permission, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary, core.dictionary_binding, core.dictionary_item, core.feature_config, core.file_asset, core.idempotency_request, core.import_column, core.import_template, core.region, core.upload_policy | PENDING_RULE |
| ENG-005 / S2 M01 开发任务 | M01 / 输出服务模板、数据库迁移规范、API 文档、数据字典和 ADR；约束其他模块按同一规范接入。 | getCsrfToken, login, logout, renewSession, getMe, getDictionary, getClientConfig, uploadFile, getFileAsset, downloadFile, createExport, listTasks, getTask, cancelTask, retryTask, listTaskAttempts, listTaskRowErrors, listPermissionCatalog, listMenuCatalog, listRegions, getImportTemplate, changeMyPassword, deleteResourcesBatch, getImportTaskResult | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.audit_event, auth.app_user, auth.csrf_session, auth.menu, auth.permission, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary, core.dictionary_binding, core.dictionary_item, core.feature_config, core.file_asset, core.idempotency_request, core.import_column, core.import_template, core.region, core.upload_policy | PENDING_RULE |
| ENG-006 / S2 M01 交付物与验收重点 | M01 / 新模块可通过标准模板注册服务、鉴权、日志、异常、分页和 OpenAPI 文档。 | getCsrfToken, login, logout, renewSession, getMe, getDictionary, getClientConfig, uploadFile, getFileAsset, downloadFile, createExport, listTasks, getTask, cancelTask, retryTask, listTaskAttempts, listTaskRowErrors, listPermissionCatalog, listMenuCatalog, listRegions, getImportTemplate, changeMyPassword, deleteResourcesBatch, getImportTaskResult | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.audit_event, auth.app_user, auth.csrf_session, auth.menu, auth.permission, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary, core.dictionary_binding, core.dictionary_item, core.feature_config, core.file_asset, core.idempotency_request, core.import_column, core.import_template, core.region, core.upload_policy | PENDING_RULE |
| ENG-007 / S2 M01 交付物与验收重点 | M01 / 任一接口异常均能返回统一错误码并带 TraceId；关键操作能在日志中追溯到用户和请求。 | getCsrfToken, login, logout, renewSession, getMe, getDictionary, getClientConfig, uploadFile, getFileAsset, downloadFile, createExport, listTasks, getTask, cancelTask, retryTask, listTaskAttempts, listTaskRowErrors, listPermissionCatalog, listMenuCatalog, listRegions, getImportTemplate, changeMyPassword, deleteResourcesBatch, getImportTaskResult | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.audit_event, auth.app_user, auth.csrf_session, auth.menu, auth.permission, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary, core.dictionary_binding, core.dictionary_item, core.feature_config, core.file_asset, core.idempotency_request, core.import_column, core.import_template, core.region, core.upload_policy | PENDING_RULE |
| ENG-008 / S2 M01 交付物与验收重点 | M01 / 权限校验既覆盖路由/接口，也覆盖数据范围；不存在只在前端隐藏按钮而后端放行的情况。 | getCsrfToken, login, logout, renewSession, getMe, getDictionary, getClientConfig, uploadFile, getFileAsset, downloadFile, createExport, listTasks, getTask, cancelTask, retryTask, listTaskAttempts, listTaskRowErrors, listPermissionCatalog, listMenuCatalog, listRegions, getImportTemplate, changeMyPassword, deleteResourcesBatch, getImportTaskResult | analysis.async_task, analysis.export_job, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, audit.audit_event, auth.app_user, auth.csrf_session, auth.menu, auth.permission, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary, core.dictionary_binding, core.dictionary_item, core.feature_config, core.file_asset, core.idempotency_request, core.import_column, core.import_template, core.region, core.upload_policy | PENDING_RULE |
| ENG-009 / S2 M02 开发任务 | M02 / 实现登录页、Token 注入/续期、退出、超时跳转、菜单树、路由守卫和按钮/数据权限指令。 | getCsrfToken, login, logout, renewSession, getMe, getClientConfig, uploadFile, getTask, listMenuCatalog | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, auth.app_user, auth.csrf_session, auth.menu, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary_binding, core.feature_config, core.file_asset, core.upload_policy | PENDING_RULE |
| ENG-010 / S2 M02 开发任务 | M02 / 实现统一页面布局、查询条件区、重置/查询按钮、表格、分页、详情抽屉、右键菜单、弹窗、空状态和错误状态。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| ENG-011 / S2 M02 开发任务 | M02 / 封装 API 请求、上传/下载、Excel 导出、WebSocket/轮询刷新、文件进度和失败重试。 | getCsrfToken, login, logout, renewSession, getMe, getClientConfig, uploadFile, getTask, listMenuCatalog | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, auth.app_user, auth.csrf_session, auth.menu, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary_binding, core.feature_config, core.file_asset, core.upload_policy | PENDING_RULE |
| ENG-012 / S2 M02 开发任务 | M02 / 提供 ECharts 图表、地图容器、时间范围、设备/场所选择器、任务表单等公共组件。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| ENG-013 / S2 M02 开发任务 | M02 / 按 DEV-01 的 OpenAPI 和 DEV-03 的数据契约实现前端类型，禁止页面自行拼装不受控字段。 | getCsrfToken, login, logout, renewSession, getMe, getClientConfig, uploadFile, getTask, listMenuCatalog | analysis.async_task, analysis.import_job, analysis.import_row_result, analysis.task_attempt, analysis.task_event, analysis.task_row_error, auth.app_user, auth.csrf_session, auth.menu, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_session, core.config_item, core.dictionary_binding, core.feature_config, core.file_asset, core.upload_policy | PENDING_RULE |
| ENG-014 / S2 M02 交付物与验收重点 | M02 / 同类页面的查询、重置、分页、详情、导出、删除和无权提示交互一致。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| ENG-015 / S2 M02 交付物与验收重点 | M02 / 页面刷新、网络抖动、空数据、超时和接口错误均有可理解的反馈，不出现白屏或死循环请求。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| ENG-016 / S2 M02 交付物与验收重点 | M02 / 登录、路由、按钮和数据权限在前端可用但不替代后端校验，页面可被自动化测试稳定定位。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| ENG-017 / S2 M03 开发任务 | M03 / 定义并实现 CommonCaptureRecord、FenceRecord、MACRecord、PlateRecord、FaceRecord 等统一模型。 | listIngestionBatches, createIngestionBatch, getIngestionBatch, listIngestionErrors, getIngestionQualitySummary, replayIngestionBatch, createIngestionBackfill | analysis.async_task, capture.ingestion_batch, capture.ingestion_item, capture.ingestion_source, capture.quality_issue, capture.source_counter_sample, capture.source_event, capture.source_mapping_version, core.device_status_event | PENDING_RULE |
| ENG-018 / S2 M03 开发任务 | M03 / 统一 targetId、deviceId、siteId、captureTime、经纬度、数据来源、运营商和状态字段，建立字段映射和版本。 | listIngestionBatches, createIngestionBatch, getIngestionBatch, listIngestionErrors, getIngestionQualitySummary, replayIngestionBatch, createIngestionBackfill | analysis.async_task, capture.ingestion_batch, capture.ingestion_item, capture.ingestion_source, capture.quality_issue, capture.source_counter_sample, capture.source_event, capture.source_mapping_version, core.device_status_event | PENDING_RULE |
| ENG-019 / S2 M03 开发任务 | M03 / 实现批量接入、格式校验、必填校验、时间/坐标规范化、非法数据隔离、重复数据识别和去重。 | listIngestionBatches, createIngestionBatch, getIngestionBatch, listIngestionErrors, getIngestionQualitySummary, replayIngestionBatch, createIngestionBackfill | analysis.async_task, capture.ingestion_batch, capture.ingestion_item, capture.ingestion_source, capture.quality_issue, capture.source_counter_sample, capture.source_event, capture.source_mapping_version, core.device_status_event | PENDING_RULE |
| ENG-020 / S2 M03 开发任务 | M03 / 为围栏、MAC、车牌、人像检索与运营统计提供可复用的查询索引、分区、聚合和数据质量指标。 | listIngestionBatches, createIngestionBatch, getIngestionBatch, listIngestionErrors, getIngestionQualitySummary, replayIngestionBatch, createIngestionBackfill | analysis.async_task, capture.ingestion_batch, capture.ingestion_item, capture.ingestion_source, capture.quality_issue, capture.source_counter_sample, capture.source_event, capture.source_mapping_version, core.device_status_event | PENDING_RULE |
| ENG-021 / S2 M03 开发任务 | M03 / 建立数据回放/补数能力，使测试和生产问题可以按来源、时间范围、批次重放并保留审计记录。 | listIngestionBatches, createIngestionBatch, getIngestionBatch, listIngestionErrors, getIngestionQualitySummary, replayIngestionBatch, createIngestionBackfill | analysis.async_task, capture.ingestion_batch, capture.ingestion_item, capture.ingestion_source, capture.quality_issue, capture.source_counter_sample, capture.source_event, capture.source_mapping_version, core.device_status_event | PENDING_RULE |
| ENG-022 / S2 M03 交付物与验收重点 | M03 / 四类采集数据可用同一查询语义检索，跨模块字段名称、时间和位置口径一致。 | listIngestionBatches, createIngestionBatch, getIngestionBatch, listIngestionErrors, getIngestionQualitySummary, replayIngestionBatch, createIngestionBackfill | analysis.async_task, capture.ingestion_batch, capture.ingestion_item, capture.ingestion_source, capture.quality_issue, capture.source_counter_sample, capture.source_event, capture.source_mapping_version, core.device_status_event | PENDING_RULE |
| ENG-023 / S2 M03 交付物与验收重点 | M03 / 脏数据、重复数据和无法解析数据不会阻断主流程，并能通过质量日志定位来源和原因。 | listIngestionBatches, createIngestionBatch, getIngestionBatch, listIngestionErrors, getIngestionQualitySummary, replayIngestionBatch, createIngestionBackfill | analysis.async_task, capture.ingestion_batch, capture.ingestion_item, capture.ingestion_source, capture.quality_issue, capture.source_counter_sample, capture.source_event, capture.source_mapping_version, core.device_status_event | PENDING_RULE |
| ENG-024 / S2 M03 交付物与验收重点 | M03 / 检索与统计在目标数据量下有可验证的响应时间和索引方案，数据回放不会产生重复计数。 | listIngestionBatches, createIngestionBatch, getIngestionBatch, listIngestionErrors, getIngestionQualitySummary, replayIngestionBatch, createIngestionBackfill | analysis.async_task, capture.ingestion_batch, capture.ingestion_item, capture.ingestion_source, capture.quality_issue, capture.source_counter_sample, capture.source_event, capture.source_mapping_version, core.device_status_event | PENDING_RULE |
| ENG-025 / S2 M04 开发任务 | M04 / 实现系统首页 12 类区域：设备在线数量、近 7 日采集量、采集场所排行、当日/累计采集、设备累计量、告警、设备地图、去重数据、数据归属 Top10、应用入口、电围分类、操作统计。 | getDashboardSummary, getOperationSummary, exportMetrics | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE |
| ENG-026 / S2 M04 开发任务 | M04 / 实现运营首页的设备在线、采集情况、短信猫状态和采集/异常统计四类区域，支持周/月/季度/年度切换。 | getDashboardSummary, getOperationSummary, exportMetrics | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE |
| ENG-027 / S2 M04 开发任务 | M04 / 实现地图悬浮显示设备类别数量、点击/跳转数据应用、图表详情钻取和 Excel 导出。 | getDashboardSummary, getOperationSummary, exportMetrics | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE |
| ENG-028 / S2 M04 开发任务 | M04 / 实现 WebSocket 或轮询刷新策略，区分实时指标、日统计和历史趋势，避免整页高频刷新。 | getDashboardSummary, getOperationSummary, exportMetrics | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE |
| ENG-029 / S2 M04 开发任务 | M04 / 由 DEV-03 提供统计口径与聚合接口，由 DEV-01 负责接口权限、缓存和异常降级。 | getDashboardSummary, getOperationSummary, exportMetrics | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE |
| ENG-030 / S2 M04 交付物与验收重点 | M04 / 12 类系统首页指标均有数据来源、刷新周期、空值/异常态和单位说明。 | getDashboardSummary, getOperationSummary, exportMetrics | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE |
| ENG-031 / S2 M04 交付物与验收重点 | M04 / 图表交互不会改变统计口径；点击查询、数据分析和运维操作后能跳转到正确模块并保留上下文。 | getDashboardSummary, getOperationSummary, exportMetrics | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE |
| ENG-032 / S2 M04 交付物与验收重点 | M04 / 实时设备数与后台口径一致，首页在接口部分失败时仍能展示可用区域并提示失败项。 | getDashboardSummary, getOperationSummary, exportMetrics | auth.menu, core.anomaly_event, core.device, core.metric_definition, core.metric_scope, core.metric_snapshot, operation.sms_modem_status | PENDING_RULE |
| ENG-033 / S2 M05 开发任务 | M05 / 围栏数据支持场所、采集时间、IMSI、IMEI 组合查询；车牌支持场所、时间段、车牌号；MAC 支持场所、时间段、MAC；人像支持场所、时间段。 | searchCaptures, getCapture, createFaceSearchTask, listFaceSearchResults | analysis.analysis_context, analysis.async_task, analysis.export_job, analysis.face_search_job, analysis.face_search_result, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.file_asset | PENDING_RULE |
| ENG-034 / S2 M05 开发任务 | M05 / 实现查询条件重置、分页、排序、结果详情、Excel 导出、结果复制及涉密场景的密文解密。 | searchCaptures, getCapture, createFaceSearchTask, listFaceSearchResults | analysis.analysis_context, analysis.async_task, analysis.export_job, analysis.face_search_job, analysis.face_search_result, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.file_asset | PENDING_RULE |
| ENG-035 / S2 M05 开发任务 | M05 / 统一右键菜单和多选策略：统计、轨迹、伴随、检索、布控、位置追踪、区域统计、加入数据集。 | searchCaptures, getCapture, createFaceSearchTask, listFaceSearchResults | analysis.analysis_context, analysis.async_task, analysis.export_job, analysis.face_search_job, analysis.face_search_result, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.file_asset | PENDING_RULE |
| ENG-036 / S2 M05 开发任务 | M05 / 为查询结果返回可供地图、分析任务和档案关联使用的完整上下文，不把页面展示字段当作唯一数据契约。 | searchCaptures, getCapture, createFaceSearchTask, listFaceSearchResults | analysis.analysis_context, analysis.async_task, analysis.export_job, analysis.face_search_job, analysis.face_search_result, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.file_asset | PENDING_RULE |
| ENG-037 / S2 M05 开发任务 | M05 / 设计大结果集的分页/异步导出/取消查询机制，并明确敏感字段脱敏与访问审计。 | searchCaptures, getCapture, createFaceSearchTask, listFaceSearchResults | analysis.analysis_context, analysis.async_task, analysis.export_job, analysis.face_search_job, analysis.face_search_result, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.file_asset | PENDING_RULE |
| ENG-038 / S2 M05 交付物与验收重点 | M05 / 四类检索的查询、重置、分页和导出行为一致；查询条件可从 URL 或任务上下文恢复。 | searchCaptures, getCapture, createFaceSearchTask, listFaceSearchResults | analysis.analysis_context, analysis.async_task, analysis.export_job, analysis.face_search_job, analysis.face_search_result, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.file_asset | PENDING_RULE |
| ENG-039 / S2 M05 交付物与验收重点 | M05 / 查询结果能被 M06、M07、M08、M09、M10 复用，避免每个分析模块重复查询和重复去重。 | searchCaptures, getCapture, createFaceSearchTask, listFaceSearchResults | analysis.analysis_context, analysis.async_task, analysis.export_job, analysis.face_search_job, analysis.face_search_result, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.file_asset | PENDING_RULE |
| ENG-040 / S2 M05 交付物与验收重点 | M05 / 大数据量查询有超时、取消、限流和导出任务状态，不因单次查询拖垮在线服务。 | searchCaptures, getCapture, createFaceSearchTask, listFaceSearchResults | analysis.analysis_context, analysis.async_task, analysis.export_job, analysis.face_search_job, analysis.face_search_result, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, core.file_asset | PENDING_RULE |
| ENG-041 / S2 M06 开发任务 | M06 / 实现复制、统计、轨迹、伴随、检索、布控、位置追踪、区域统计、加入数据集和数据导出动作的统一命令模型。 | getCaptureStatistics, requestDecryption, getDecryptionRequest, createContext, getContext, resolveTargets | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.profile_link, audit.sensitive_action_review, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, operation.decryption_capture, operation.decryption_request, operation.decryption_result_grant | PENDING_RULE |
| ENG-042 / S2 M06 开发任务 | M06 / 为每个动作定义输入数据、权限、异步/同步方式、任务状态、失败重试、结果引用和操作审计。 | getCaptureStatistics, requestDecryption, getDecryptionRequest, createContext, getContext, resolveTargets | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.profile_link, audit.sensitive_action_review, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, operation.decryption_capture, operation.decryption_request, operation.decryption_result_grant | PENDING_RULE |
| ENG-043 / S2 M06 开发任务 | M06 / 处理涉密用户的数据解密、脱敏、结果回传和下载权限；普通用户不得通过接口绕过解密规则。 | getCaptureStatistics, requestDecryption, getDecryptionRequest, createContext, getContext, resolveTargets | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.profile_link, audit.sensitive_action_review, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, operation.decryption_capture, operation.decryption_request, operation.decryption_result_grant | PENDING_RULE |
| ENG-044 / S2 M06 开发任务 | M06 / 支持从数据详情直接跳转地图研判、伴随/碰撞任务、布控任务和数据集，保留选中目标、时间范围和来源。 | getCaptureStatistics, requestDecryption, getDecryptionRequest, createContext, getContext, resolveTargets | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.profile_link, audit.sensitive_action_review, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, operation.decryption_capture, operation.decryption_request, operation.decryption_result_grant | PENDING_RULE |
| ENG-045 / S2 M06 开发任务 | M06 / 建立动作幂等键和任务关联 ID，避免重复点击产生重复任务或重复告警。 | getCaptureStatistics, requestDecryption, getDecryptionRequest, createContext, getContext, resolveTargets | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.profile_link, audit.sensitive_action_review, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, operation.decryption_capture, operation.decryption_request, operation.decryption_result_grant | PENDING_RULE |
| ENG-046 / S2 M06 交付物与验收重点 | M06 / 同一条数据在四类检索页面上的动作名称、参数、权限和结果入口一致。 | getCaptureStatistics, requestDecryption, getDecryptionRequest, createContext, getContext, resolveTargets | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.profile_link, audit.sensitive_action_review, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, operation.decryption_capture, operation.decryption_request, operation.decryption_result_grant | PENDING_RULE |
| ENG-047 / S2 M06 交付物与验收重点 | M06 / 异步动作可查询进度、查看结果、导出或取消；失败时保留可定位的错误原因。 | getCaptureStatistics, requestDecryption, getDecryptionRequest, createContext, getContext, resolveTargets | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.profile_link, audit.sensitive_action_review, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, operation.decryption_capture, operation.decryption_request, operation.decryption_result_grant | PENDING_RULE |
| ENG-048 / S2 M06 交付物与验收重点 | M06 / 从检索到地图、分析、布控、数据集的上下文传递可被 E2E 测试验证。 | getCaptureStatistics, requestDecryption, getDecryptionRequest, createContext, getContext, resolveTargets | analysis.analysis_context, analysis.async_task, analysis.context_target, analysis.export_job, analysis.input_snapshot, analysis.profile_link, audit.sensitive_action_review, capture.capture_event, capture.capture_target, capture.face_record, capture.fence_record, capture.mac_record, capture.plate_record, capture.target, capture.target_attribution, capture.target_lookup, operation.decryption_capture, operation.decryption_request, operation.decryption_result_grant | PENDING_RULE |
| ENG-049 / S2 M07 开发任务 | M07 / 实现地图搜索、目标结果列表、框选、多选、单选、地图重置、结果回传和目标上下文保持。 | getMapFeatures, getMapHeatmap, getMapAreaSummary, queryTrajectory | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| ENG-050 / S2 M07 开发任务 | M07 / 实现地图图层、点聚合、轨迹线、时间轴、位置追踪、热力图、设备分布、围栏/场所和设备详情。 | getMapFeatures, getMapHeatmap, getMapAreaSummary, queryTrajectory | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| ENG-051 / S2 M07 开发任务 | M07 / 实现按区域/设备/数据类型查看采集量，支持地图与右侧列表联动、缩放、定位和详情钻取。 | getMapFeatures, getMapHeatmap, getMapAreaSummary, queryTrajectory | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| ENG-052 / S2 M07 开发任务 | M07 / 统一 GeoJSON、坐标系、时间格式、轨迹点排序、点位样式和大数据量渲染策略。 | getMapFeatures, getMapHeatmap, getMapAreaSummary, queryTrajectory | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| ENG-053 / S2 M07 开发任务 | M07 / 提供可复用的地图状态模型，供设备分布、运维首页、区域统计和报告详情使用。 | getMapFeatures, getMapHeatmap, getMapAreaSummary, queryTrajectory | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| ENG-054 / S2 M07 交付物与验收重点 | M07 / 框选和单选的语义清楚，选择结果与列表、任务输入和统计范围一致。 | getMapFeatures, getMapHeatmap, getMapAreaSummary, queryTrajectory | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| ENG-055 / S2 M07 交付物与验收重点 | M07 / 同一目标在地图上的轨迹、详情、时间范围和检索结果一致；地图无数据或数据过多时有明确提示。 | getMapFeatures, getMapHeatmap, getMapAreaSummary, queryTrajectory | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| ENG-056 / S2 M07 交付物与验收重点 | M07 / 轨迹、热力图和设备分布在目标点数下保持可用帧率，地图重置可恢复初始状态。 | getMapFeatures, getMapHeatmap, getMapAreaSummary, queryTrajectory | capture.capture_event, capture.capture_target, core.device, core.metric_snapshot, core.region, core.site | MAPPED |
| ENG-057 / S2 M08 开发任务 | M08 / 伴随分析：按时间窗口、空间窗口和目标/设备集合计算共同出现、经过站点、轨迹重合和关联度。 | listAnalysisTasks, createAnalysisTask, getAnalysisTask, deleteAnalysisTask, reviseAnalysisTask, listAnalysisResults, getAnalysisResult, exportAnalysisResults, exportAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | MAPPED |
| ENG-058 / S2 M08 开发任务 | M08 / 碰撞分析：建立多源目标之间的碰撞/关联规则，输出关联目标、碰撞次数、时间地点和匹配率。 | listAnalysisTasks, createAnalysisTask, getAnalysisTask, deleteAnalysisTask, reviseAnalysisTask, listAnalysisResults, getAnalysisResult, exportAnalysisResults, exportAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | MAPPED |
| ENG-059 / S2 M08 开发任务 | M08 / 区域统计：按所选区域统计 IMSI、经过站点数、采集总数、最早出现时间和区域匹配度。 | listAnalysisTasks, createAnalysisTask, getAnalysisTask, deleteAnalysisTask, reviseAnalysisTask, listAnalysisResults, getAnalysisResult, exportAnalysisResults, exportAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | MAPPED |
| ENG-060 / S2 M08 开发任务 | M08 / 实现任务新建、查询、重置、详情、编辑、结果导出、删除及结果上的检索/轨迹/伴随/布控动作。 | listAnalysisTasks, createAnalysisTask, getAnalysisTask, deleteAnalysisTask, reviseAnalysisTask, listAnalysisResults, getAnalysisResult, exportAnalysisResults, exportAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | MAPPED |
| ENG-061 / S2 M08 开发任务 | M08 / 为长耗时分析提供异步任务、进度、失败重试、结果版本和参数快照，保证历史结果可复现。 | listAnalysisTasks, createAnalysisTask, getAnalysisTask, deleteAnalysisTask, reviseAnalysisTask, listAnalysisResults, getAnalysisResult, exportAnalysisResults, exportAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | MAPPED |
| ENG-062 / S2 M08 交付物与验收重点 | M08 / 任务参数、数据范围、算法版本和生成时间可追溯；相同输入可得到可解释的结果。 | listAnalysisTasks, createAnalysisTask, getAnalysisTask, deleteAnalysisTask, reviseAnalysisTask, listAnalysisResults, getAnalysisResult, exportAnalysisResults, exportAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | MAPPED |
| ENG-063 / S2 M08 交付物与验收重点 | M08 / 分析结果支持分页/导出，指标定义与首页、检索详情和报告口径一致。 | listAnalysisTasks, createAnalysisTask, getAnalysisTask, deleteAnalysisTask, reviseAnalysisTask, listAnalysisResults, getAnalysisResult, exportAnalysisResults, exportAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | MAPPED |
| ENG-064 / S2 M08 交付物与验收重点 | M08 / 任务被取消、失败或重复提交时不会产生脏结果或无法清理的后台任务。 | listAnalysisTasks, createAnalysisTask, getAnalysisTask, deleteAnalysisTask, reviseAnalysisTask, listAnalysisResults, getAnalysisResult, exportAnalysisResults, exportAnalysisTasks | analysis.analysis_job, analysis.analysis_result, analysis.async_task, analysis.collision_event, analysis.collision_parameter, analysis.collision_result, analysis.companion_parameter, analysis.companion_result, analysis.input_snapshot, analysis.region_parameter, analysis.region_result, analysis.result_evidence, analysis.result_selected_region | MAPPED |
| ENG-065 / S2 M09 开发任务 | M09 / 支持从检索条件导入、从文件导入和在平台内添加数据集；记录创建人、来源、字段版本和时间范围。 | listDatasets, createDataset, getDataset, updateDataset, deleteDataset, listDatasetItems, addDatasetItems, importDatasetItems, listDatasetVersions, exportDataset, previewSetTask, listSetTasks, createSetTask, getSetTask, listSetTaskResults, removeDatasetItem | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | MAPPED |
| ENG-066 / S2 M09 开发任务 | M09 / 实现交集、并集、差集、混合运算和数据集详情查看，明确同一目标、同一时间和同一来源的去重规则。 | listDatasets, createDataset, getDataset, updateDataset, deleteDataset, listDatasetItems, addDatasetItems, importDatasetItems, listDatasetVersions, exportDataset, previewSetTask, listSetTasks, createSetTask, getSetTask, listSetTaskResults, removeDatasetItem | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | MAPPED |
| ENG-067 / S2 M09 开发任务 | M09 / 数据集详情支持复制、检索、轨迹、伴随、布控、位置追踪及再次加入其他数据集。 | listDatasets, createDataset, getDataset, updateDataset, deleteDataset, listDatasetItems, addDatasetItems, importDatasetItems, listDatasetVersions, exportDataset, previewSetTask, listSetTasks, createSetTask, getSetTask, listSetTaskResults, removeDatasetItem | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | MAPPED |
| ENG-068 / S2 M09 开发任务 | M09 / 提供数据集命名、权限、生命周期、版本、删除保护和异步运算状态，避免删除仍被任务引用的数据。 | listDatasets, createDataset, getDataset, updateDataset, deleteDataset, listDatasetItems, addDatasetItems, importDatasetItems, listDatasetVersions, exportDataset, previewSetTask, listSetTasks, createSetTask, getSetTask, listSetTaskResults, removeDatasetItem | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | MAPPED |
| ENG-069 / S2 M09 开发任务 | M09 / 为前端提供集合运算预览、结果数量预估、失败原因和导出能力。 | listDatasets, createDataset, getDataset, updateDataset, deleteDataset, listDatasetItems, addDatasetItems, importDatasetItems, listDatasetVersions, exportDataset, previewSetTask, listSetTasks, createSetTask, getSetTask, listSetTaskResults, removeDatasetItem | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | MAPPED |
| ENG-070 / S2 M09 交付物与验收重点 | M09 / 集合运算结果有可解释的输入集合、运算符、去重规则和结果数量。 | listDatasets, createDataset, getDataset, updateDataset, deleteDataset, listDatasetItems, addDatasetItems, importDatasetItems, listDatasetVersions, exportDataset, previewSetTask, listSetTasks, createSetTask, getSetTask, listSetTaskResults, removeDatasetItem | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | MAPPED |
| ENG-071 / S2 M09 交付物与验收重点 | M09 / 大集合运算在后台执行且可恢复；重复提交不会重复创建相同结果。 | listDatasets, createDataset, getDataset, updateDataset, deleteDataset, listDatasetItems, addDatasetItems, importDatasetItems, listDatasetVersions, exportDataset, previewSetTask, listSetTasks, createSetTask, getSetTask, listSetTaskResults, removeDatasetItem | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | MAPPED |
| ENG-072 / S2 M09 交付物与验收重点 | M09 / 数据集权限与原始数据权限一致或更严格，删除、导出和共享均有审计记录。 | listDatasets, createDataset, getDataset, updateDataset, deleteDataset, listDatasetItems, addDatasetItems, importDatasetItems, listDatasetVersions, exportDataset, previewSetTask, listSetTasks, createSetTask, getSetTask, listSetTaskResults, removeDatasetItem | analysis.async_task, analysis.dataset, analysis.dataset_grant, analysis.dataset_item, analysis.dataset_version, analysis.export_job, analysis.import_job, analysis.set_job, analysis.set_operand | MAPPED |
| ENG-073 / S2 M10 开发任务 | M10 / 综合布控支持围栏、人像、车牌、MAC 等多维目标，配置布控范围、开始/结束时间、重点人员/设备和匹配条件。 | listWatchRules, createWatchRule, getWatchRule, updateWatchRule, deleteWatchRule, activateWatchRule, revokeWatchRule, getWatchStatistics, listAlerts, getAlert, listNotificationTasks, createNotificationTask, getNotificationTask, deleteNotificationTask, updateNotificationTask, cancelNotificationTask, retryNotificationTask, listDeliveries, getDelivery, listNotificationTemplates, listPlatformMessages, revokeResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, capture.capture_event, operation.alert_event, operation.delivery, operation.delivery_attempt, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient, operation.notification_template, operation.notification_template_variable, operation.platform_message, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE |
| ENG-074 / S2 M10 开发任务 | M10 / 实现布控任务查询、重置、新增、撤销、详情、编辑、统计和删除；布控结果支持检索与伴随。 | listWatchRules, createWatchRule, getWatchRule, updateWatchRule, deleteWatchRule, activateWatchRule, revokeWatchRule, getWatchStatistics, listAlerts, getAlert, listNotificationTasks, createNotificationTask, getNotificationTask, deleteNotificationTask, updateNotificationTask, cancelNotificationTask, retryNotificationTask, listDeliveries, getDelivery, listNotificationTemplates, listPlatformMessages, revokeResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, capture.capture_event, operation.alert_event, operation.delivery, operation.delivery_attempt, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient, operation.notification_template, operation.notification_template_variable, operation.platform_message, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE |
| ENG-075 / S2 M10 开发任务 | M10 / 实现通知任务查询、重置、新增、详情、撤销和删除，支持短信、邮件和平台消息三类通道。 | listWatchRules, createWatchRule, getWatchRule, updateWatchRule, deleteWatchRule, activateWatchRule, revokeWatchRule, getWatchStatistics, listAlerts, getAlert, listNotificationTasks, createNotificationTask, getNotificationTask, deleteNotificationTask, updateNotificationTask, cancelNotificationTask, retryNotificationTask, listDeliveries, getDelivery, listNotificationTemplates, listPlatformMessages, revokeResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, capture.capture_event, operation.alert_event, operation.delivery, operation.delivery_attempt, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient, operation.notification_template, operation.notification_template_variable, operation.platform_message, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE |
| ENG-076 / S2 M10 开发任务 | M10 / 建立规则命中、去重、告警分级、通知模板、重试、频控、失败转人工和短信日志关联。 | listWatchRules, createWatchRule, getWatchRule, updateWatchRule, deleteWatchRule, activateWatchRule, revokeWatchRule, getWatchStatistics, listAlerts, getAlert, listNotificationTasks, createNotificationTask, getNotificationTask, deleteNotificationTask, updateNotificationTask, cancelNotificationTask, retryNotificationTask, listDeliveries, getDelivery, listNotificationTemplates, listPlatformMessages, revokeResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, capture.capture_event, operation.alert_event, operation.delivery, operation.delivery_attempt, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient, operation.notification_template, operation.notification_template_variable, operation.platform_message, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE |
| ENG-077 / S2 M10 开发任务 | M10 / 由 DEV-03 提供规则计算/匹配服务，DEV-01 负责任务编排、可靠送达、权限和审计。 | listWatchRules, createWatchRule, getWatchRule, updateWatchRule, deleteWatchRule, activateWatchRule, revokeWatchRule, getWatchStatistics, listAlerts, getAlert, listNotificationTasks, createNotificationTask, getNotificationTask, deleteNotificationTask, updateNotificationTask, cancelNotificationTask, retryNotificationTask, listDeliveries, getDelivery, listNotificationTemplates, listPlatformMessages, revokeResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, capture.capture_event, operation.alert_event, operation.delivery, operation.delivery_attempt, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient, operation.notification_template, operation.notification_template_variable, operation.platform_message, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE |
| ENG-078 / S2 M10 交付物与验收重点 | M10 / 命中结果可追溯到具体规则、目标、时间、设备、原始数据和通知状态。 | listWatchRules, createWatchRule, getWatchRule, updateWatchRule, deleteWatchRule, activateWatchRule, revokeWatchRule, getWatchStatistics, listAlerts, getAlert, listNotificationTasks, createNotificationTask, getNotificationTask, deleteNotificationTask, updateNotificationTask, cancelNotificationTask, retryNotificationTask, listDeliveries, getDelivery, listNotificationTemplates, listPlatformMessages, revokeResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, capture.capture_event, operation.alert_event, operation.delivery, operation.delivery_attempt, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient, operation.notification_template, operation.notification_template_variable, operation.platform_message, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE |
| ENG-079 / S2 M10 交付物与验收重点 | M10 / 同一事件在频控窗口内不会重复发送；通道失败会重试且不会阻塞其他告警。 | listWatchRules, createWatchRule, getWatchRule, updateWatchRule, deleteWatchRule, activateWatchRule, revokeWatchRule, getWatchStatistics, listAlerts, getAlert, listNotificationTasks, createNotificationTask, getNotificationTask, deleteNotificationTask, updateNotificationTask, cancelNotificationTask, retryNotificationTask, listDeliveries, getDelivery, listNotificationTemplates, listPlatformMessages, revokeResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, capture.capture_event, operation.alert_event, operation.delivery, operation.delivery_attempt, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient, operation.notification_template, operation.notification_template_variable, operation.platform_message, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE |
| ENG-080 / S2 M10 交付物与验收重点 | M10 / 撤销、删除和权限变更对正在运行的任务有明确状态转移和生效时间。 | listWatchRules, createWatchRule, getWatchRule, updateWatchRule, deleteWatchRule, activateWatchRule, revokeWatchRule, getWatchStatistics, listAlerts, getAlert, listNotificationTasks, createNotificationTask, getNotificationTask, deleteNotificationTask, updateNotificationTask, cancelNotificationTask, retryNotificationTask, listDeliveries, getDelivery, listNotificationTemplates, listPlatformMessages, revokeResourcesBatch | analysis.async_task, analysis.task_row_error, audit.audit_event, capture.capture_event, operation.alert_event, operation.delivery, operation.delivery_attempt, operation.notification_execution, operation.notification_plan, operation.notification_plan_recipient, operation.notification_plan_revision, operation.notification_recipient, operation.notification_template, operation.notification_template_variable, operation.platform_message, operation.watch_notification_recipient, operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_site, operation.watch_rule_target, operation.watch_statistics, operation.watch_statistics_run | PENDING_RULE |
| ENG-081 / S2 M11 开发任务 | M11 / 人员库支持按库名/分类查询、新增、编辑、详情、导入、导出和删除，并支持与布控任务关联。 | listPersonLibraries, createPersonLibrary, getPersonLibrary, updatePersonLibrary, deletePersonLibrary, listPersonLibraryMembers, addPersonLibraryMembers, removePersonLibraryMember, importPersonLibraryMembers, exportPersonLibrary, searchProfiles, getProfile, listProfileTargets, listProfileEvidence, listProfileHistory, listProfileTrajectories, createProfileCorrection, mergeProfiles, splitProfile, resolveProfileConflict, exportProfile, listTrendModels, createTrendModel, getTrendModel, updateTrendModel, listModelParameterVersions, createModelParameterVersion, getModelParameterVersion, listModelTags, runTrendModel, listTrendModelResults, getTrendModelResult, createLibraryPerson, listProfileConflicts, importPersonLibraries, exportPersonLibraries | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.trend_model, analysis.vehicle_profile | PENDING_RULE |
| ENG-082 / S2 M11 开发任务 | M11 / 图码档案、车码档案按一人一档/一车一档聚合多源数据，展示基础信息、关联轨迹、关联度和历史记录。 | listPersonLibraries, createPersonLibrary, getPersonLibrary, updatePersonLibrary, deletePersonLibrary, listPersonLibraryMembers, addPersonLibraryMembers, removePersonLibraryMember, importPersonLibraryMembers, exportPersonLibrary, searchProfiles, getProfile, listProfileTargets, listProfileEvidence, listProfileHistory, listProfileTrajectories, createProfileCorrection, mergeProfiles, splitProfile, resolveProfileConflict, exportProfile, listTrendModels, createTrendModel, getTrendModel, updateTrendModel, listModelParameterVersions, createModelParameterVersion, getModelParameterVersion, listModelTags, runTrendModel, listTrendModelResults, getTrendModelResult, createLibraryPerson, listProfileConflicts, importPersonLibraries, exportPersonLibraries | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.trend_model, analysis.vehicle_profile | PENDING_RULE |
| ENG-083 / S2 M11 开发任务 | M11 / 数据趋势提供模型库、模型参数、标签、匹配规则和有效数据筛选；支持驻留、轨迹、频次等趋势模型扩展。 | listPersonLibraries, createPersonLibrary, getPersonLibrary, updatePersonLibrary, deletePersonLibrary, listPersonLibraryMembers, addPersonLibraryMembers, removePersonLibraryMember, importPersonLibraryMembers, exportPersonLibrary, searchProfiles, getProfile, listProfileTargets, listProfileEvidence, listProfileHistory, listProfileTrajectories, createProfileCorrection, mergeProfiles, splitProfile, resolveProfileConflict, exportProfile, listTrendModels, createTrendModel, getTrendModel, updateTrendModel, listModelParameterVersions, createModelParameterVersion, getModelParameterVersion, listModelTags, runTrendModel, listTrendModelResults, getTrendModelResult, createLibraryPerson, listProfileConflicts, importPersonLibraries, exportPersonLibraries | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.trend_model, analysis.vehicle_profile | PENDING_RULE |
| ENG-084 / S2 M11 开发任务 | M11 / 建立档案合并、拆分、冲突处理、来源证据和人工修订记录，避免模型结果覆盖原始数据。 | listPersonLibraries, createPersonLibrary, getPersonLibrary, updatePersonLibrary, deletePersonLibrary, listPersonLibraryMembers, addPersonLibraryMembers, removePersonLibraryMember, importPersonLibraryMembers, exportPersonLibrary, searchProfiles, getProfile, listProfileTargets, listProfileEvidence, listProfileHistory, listProfileTrajectories, createProfileCorrection, mergeProfiles, splitProfile, resolveProfileConflict, exportProfile, listTrendModels, createTrendModel, getTrendModel, updateTrendModel, listModelParameterVersions, createModelParameterVersion, getModelParameterVersion, listModelTags, runTrendModel, listTrendModelResults, getTrendModelResult, createLibraryPerson, listProfileConflicts, importPersonLibraries, exportPersonLibraries | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.trend_model, analysis.vehicle_profile | PENDING_RULE |
| ENG-085 / S2 M11 开发任务 | M11 / 提供档案检索、关联目标、证据详情、导出和权限审计接口。 | listPersonLibraries, createPersonLibrary, getPersonLibrary, updatePersonLibrary, deletePersonLibrary, listPersonLibraryMembers, addPersonLibraryMembers, removePersonLibraryMember, importPersonLibraryMembers, exportPersonLibrary, searchProfiles, getProfile, listProfileTargets, listProfileEvidence, listProfileHistory, listProfileTrajectories, createProfileCorrection, mergeProfiles, splitProfile, resolveProfileConflict, exportProfile, listTrendModels, createTrendModel, getTrendModel, updateTrendModel, listModelParameterVersions, createModelParameterVersion, getModelParameterVersion, listModelTags, runTrendModel, listTrendModelResults, getTrendModelResult, createLibraryPerson, listProfileConflicts, importPersonLibraries, exportPersonLibraries | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.trend_model, analysis.vehicle_profile | PENDING_RULE |
| ENG-086 / S2 M11 交付物与验收重点 | M11 / 档案结论与原始采集记录可双向追溯；同一目标的跨源字段有来源和置信度。 | listPersonLibraries, createPersonLibrary, getPersonLibrary, updatePersonLibrary, deletePersonLibrary, listPersonLibraryMembers, addPersonLibraryMembers, removePersonLibraryMember, importPersonLibraryMembers, exportPersonLibrary, searchProfiles, getProfile, listProfileTargets, listProfileEvidence, listProfileHistory, listProfileTrajectories, createProfileCorrection, mergeProfiles, splitProfile, resolveProfileConflict, exportProfile, listTrendModels, createTrendModel, getTrendModel, updateTrendModel, listModelParameterVersions, createModelParameterVersion, getModelParameterVersion, listModelTags, runTrendModel, listTrendModelResults, getTrendModelResult, createLibraryPerson, listProfileConflicts, importPersonLibraries, exportPersonLibraries | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.trend_model, analysis.vehicle_profile | PENDING_RULE |
| ENG-087 / S2 M11 交付物与验收重点 | M11 / 模型参数可版本化，模型重新运行不会无痕覆盖历史结果。 | listPersonLibraries, createPersonLibrary, getPersonLibrary, updatePersonLibrary, deletePersonLibrary, listPersonLibraryMembers, addPersonLibraryMembers, removePersonLibraryMember, importPersonLibraryMembers, exportPersonLibrary, searchProfiles, getProfile, listProfileTargets, listProfileEvidence, listProfileHistory, listProfileTrajectories, createProfileCorrection, mergeProfiles, splitProfile, resolveProfileConflict, exportProfile, listTrendModels, createTrendModel, getTrendModel, updateTrendModel, listModelParameterVersions, createModelParameterVersion, getModelParameterVersion, listModelTags, runTrendModel, listTrendModelResults, getTrendModelResult, createLibraryPerson, listProfileConflicts, importPersonLibraries, exportPersonLibraries | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.trend_model, analysis.vehicle_profile | PENDING_RULE |
| ENG-088 / S2 M11 交付物与验收重点 | M11 / 人员库导入失败可逐条定位，删除/编辑受到任务引用和权限约束。 | listPersonLibraries, createPersonLibrary, getPersonLibrary, updatePersonLibrary, deletePersonLibrary, listPersonLibraryMembers, addPersonLibraryMembers, removePersonLibraryMember, importPersonLibraryMembers, exportPersonLibrary, searchProfiles, getProfile, listProfileTargets, listProfileEvidence, listProfileHistory, listProfileTrajectories, createProfileCorrection, mergeProfiles, splitProfile, resolveProfileConflict, exportProfile, listTrendModels, createTrendModel, getTrendModel, updateTrendModel, listModelParameterVersions, createModelParameterVersion, getModelParameterVersion, listModelTags, runTrendModel, listTrendModelResults, getTrendModelResult, createLibraryPerson, listProfileConflicts, importPersonLibraries, exportPersonLibraries | analysis.export_job, analysis.import_job, analysis.library_scope_data_type, analysis.library_scope_site, analysis.model_execution, analysis.model_matching_rule, analysis.model_parameter_definition, analysis.model_parameter_value, analysis.model_parameter_version, analysis.model_result, analysis.model_result_evidence, analysis.model_tag, analysis.person_library, analysis.person_library_member, analysis.person_profile, analysis.profile, analysis.profile_conflict, analysis.profile_evidence, analysis.profile_link, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.trend_model, analysis.vehicle_profile | PENDING_RULE |
| ENG-089 / S2 M12 开发任务 | M12 / 场所管理：按名称/区域查询，新增、编辑、删除；设备管理：按场所、设备名/序列号、状态、分类查询。 | listSites, createSite, getSite, updateSite, deleteSite, listDevices, createDevice, getDevice, updateDevice, deleteDevice, getDeviceStatus, getDeviceParameters, updateDeviceParameters, listDeviceStatusHistory, getDeviceCaptureStatistics, importDevices, exportDevices, listDeviceGroups, createDeviceGroup, getDeviceGroup, updateDeviceGroup, deleteDeviceGroup, listDeviceGroupMembers, addDeviceGroupMembers, removeDeviceGroupMember, listDeviceCommands, createDeviceCommand, getDeviceCommand, deleteDeviceCommand, listDeviceCommandResults, retryDeviceCommand, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport, reviseDeviceCommand | analysis.async_task, analysis.export_job, analysis.import_job, audit.sensitive_action_review, capture.capture_event, core.device, core.device_group, core.device_group_member, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.file_asset, core.metric_snapshot, core.site, operation.device_command, operation.device_command_attempt, operation.device_command_job, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE |
| ENG-090 / S2 M12 开发任务 | M12 / 设备详情支持参数查询/修改、在线/离线记录、采集量历史、复位、导入、导出、定位和状态变更。 | listSites, createSite, getSite, updateSite, deleteSite, listDevices, createDevice, getDevice, updateDevice, deleteDevice, getDeviceStatus, getDeviceParameters, updateDeviceParameters, listDeviceStatusHistory, getDeviceCaptureStatistics, importDevices, exportDevices, listDeviceGroups, createDeviceGroup, getDeviceGroup, updateDeviceGroup, deleteDeviceGroup, listDeviceGroupMembers, addDeviceGroupMembers, removeDeviceGroupMember, listDeviceCommands, createDeviceCommand, getDeviceCommand, deleteDeviceCommand, listDeviceCommandResults, retryDeviceCommand, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport, reviseDeviceCommand | analysis.async_task, analysis.export_job, analysis.import_job, audit.sensitive_action_review, capture.capture_event, core.device, core.device_group, core.device_group_member, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.file_asset, core.metric_snapshot, core.site, operation.device_command, operation.device_command_attempt, operation.device_command_job, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE |
| ENG-091 / S2 M12 开发任务 | M12 / 设备分组与参数配置支持按场所、分组、设备选择和批量下发；记录任务状态、执行结果和失败原因。 | listSites, createSite, getSite, updateSite, deleteSite, listDevices, createDevice, getDevice, updateDevice, deleteDevice, getDeviceStatus, getDeviceParameters, updateDeviceParameters, listDeviceStatusHistory, getDeviceCaptureStatistics, importDevices, exportDevices, listDeviceGroups, createDeviceGroup, getDeviceGroup, updateDeviceGroup, deleteDeviceGroup, listDeviceGroupMembers, addDeviceGroupMembers, removeDeviceGroupMember, listDeviceCommands, createDeviceCommand, getDeviceCommand, deleteDeviceCommand, listDeviceCommandResults, retryDeviceCommand, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport, reviseDeviceCommand | analysis.async_task, analysis.export_job, analysis.import_job, audit.sensitive_action_review, capture.capture_event, core.device, core.device_group, core.device_group_member, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.file_asset, core.metric_snapshot, core.site, operation.device_command, operation.device_command_attempt, operation.device_command_job, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE |
| ENG-092 / S2 M12 开发任务 | M12 / 运营报表支持设备报表、数据报表、系统报表的单报表/多报表任务，按年/月/日生成并下载 Excel 或 ZIP。 | listSites, createSite, getSite, updateSite, deleteSite, listDevices, createDevice, getDevice, updateDevice, deleteDevice, getDeviceStatus, getDeviceParameters, updateDeviceParameters, listDeviceStatusHistory, getDeviceCaptureStatistics, importDevices, exportDevices, listDeviceGroups, createDeviceGroup, getDeviceGroup, updateDeviceGroup, deleteDeviceGroup, listDeviceGroupMembers, addDeviceGroupMembers, removeDeviceGroupMember, listDeviceCommands, createDeviceCommand, getDeviceCommand, deleteDeviceCommand, listDeviceCommandResults, retryDeviceCommand, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport, reviseDeviceCommand | analysis.async_task, analysis.export_job, analysis.import_job, audit.sensitive_action_review, capture.capture_event, core.device, core.device_group, core.device_group_member, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.file_asset, core.metric_snapshot, core.site, operation.device_command, operation.device_command_attempt, operation.device_command_job, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE |
| ENG-093 / S2 M12 开发任务 | M12 / 统一设备远程命令、超时、幂等、权限、审批/确认和审计，避免重复下发高风险操作。 | listSites, createSite, getSite, updateSite, deleteSite, listDevices, createDevice, getDevice, updateDevice, deleteDevice, getDeviceStatus, getDeviceParameters, updateDeviceParameters, listDeviceStatusHistory, getDeviceCaptureStatistics, importDevices, exportDevices, listDeviceGroups, createDeviceGroup, getDeviceGroup, updateDeviceGroup, deleteDeviceGroup, listDeviceGroupMembers, addDeviceGroupMembers, removeDeviceGroupMember, listDeviceCommands, createDeviceCommand, getDeviceCommand, deleteDeviceCommand, listDeviceCommandResults, retryDeviceCommand, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport, reviseDeviceCommand | analysis.async_task, analysis.export_job, analysis.import_job, audit.sensitive_action_review, capture.capture_event, core.device, core.device_group, core.device_group_member, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.file_asset, core.metric_snapshot, core.site, operation.device_command, operation.device_command_attempt, operation.device_command_job, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE |
| ENG-094 / S2 M12 交付物与验收重点 | M12 / 设备、场所、参数、状态和历史数据的主键/关联关系明确，删除有引用保护。 | listSites, createSite, getSite, updateSite, deleteSite, listDevices, createDevice, getDevice, updateDevice, deleteDevice, getDeviceStatus, getDeviceParameters, updateDeviceParameters, listDeviceStatusHistory, getDeviceCaptureStatistics, importDevices, exportDevices, listDeviceGroups, createDeviceGroup, getDeviceGroup, updateDeviceGroup, deleteDeviceGroup, listDeviceGroupMembers, addDeviceGroupMembers, removeDeviceGroupMember, listDeviceCommands, createDeviceCommand, getDeviceCommand, deleteDeviceCommand, listDeviceCommandResults, retryDeviceCommand, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport, reviseDeviceCommand | analysis.async_task, analysis.export_job, analysis.import_job, audit.sensitive_action_review, capture.capture_event, core.device, core.device_group, core.device_group_member, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.file_asset, core.metric_snapshot, core.site, operation.device_command, operation.device_command_attempt, operation.device_command_job, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE |
| ENG-095 / S2 M12 交付物与验收重点 | M12 / 批量参数任务可查看每台设备的成功/失败状态，失败可重试且不会重复执行成功项。 | listSites, createSite, getSite, updateSite, deleteSite, listDevices, createDevice, getDevice, updateDevice, deleteDevice, getDeviceStatus, getDeviceParameters, updateDeviceParameters, listDeviceStatusHistory, getDeviceCaptureStatistics, importDevices, exportDevices, listDeviceGroups, createDeviceGroup, getDeviceGroup, updateDeviceGroup, deleteDeviceGroup, listDeviceGroupMembers, addDeviceGroupMembers, removeDeviceGroupMember, listDeviceCommands, createDeviceCommand, getDeviceCommand, deleteDeviceCommand, listDeviceCommandResults, retryDeviceCommand, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport, reviseDeviceCommand | analysis.async_task, analysis.export_job, analysis.import_job, audit.sensitive_action_review, capture.capture_event, core.device, core.device_group, core.device_group_member, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.file_asset, core.metric_snapshot, core.site, operation.device_command, operation.device_command_attempt, operation.device_command_job, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE |
| ENG-096 / S2 M12 交付物与验收重点 | M12 / 单报表和多报表生成结果可下载、可重试、可清理；报表数据与首页/统计口径一致。 | listSites, createSite, getSite, updateSite, deleteSite, listDevices, createDevice, getDevice, updateDevice, deleteDevice, getDeviceStatus, getDeviceParameters, updateDeviceParameters, listDeviceStatusHistory, getDeviceCaptureStatistics, importDevices, exportDevices, listDeviceGroups, createDeviceGroup, getDeviceGroup, updateDeviceGroup, deleteDeviceGroup, listDeviceGroupMembers, addDeviceGroupMembers, removeDeviceGroupMember, listDeviceCommands, createDeviceCommand, getDeviceCommand, deleteDeviceCommand, listDeviceCommandResults, retryDeviceCommand, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport, reviseDeviceCommand | analysis.async_task, analysis.export_job, analysis.import_job, audit.sensitive_action_review, capture.capture_event, core.device, core.device_group, core.device_group_member, core.device_parameter_definition, core.device_parameter_value, core.device_revision, core.device_status_event, core.file_asset, core.metric_snapshot, core.site, operation.device_command, operation.device_command_attempt, operation.device_command_job, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point | PENDING_RULE |
| ENG-097 / S2 M13 开发任务 | M13 / 实现按场所、设备在线状态和关键词筛选；支持地图框选/手动选择，右侧列表查看设备和场所详情。 | getDashboardSummary, getOperationSummary, getMapFeatures, getDeviceMapFeatures, getMapHeatmap, getMapAreaSummary, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport | analysis.async_task, auth.menu, capture.capture_event, capture.capture_target, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, core.region, core.site, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point, operation.sms_modem_status | PENDING_RULE |
| ENG-098 / S2 M13 开发任务 | M13 / 实现设备在线率统计、设备采集量热力图、区域放大、地图重置和从地图跳转场所/设备管理。 | getDashboardSummary, getOperationSummary, getMapFeatures, getDeviceMapFeatures, getMapHeatmap, getMapAreaSummary, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport | analysis.async_task, auth.menu, capture.capture_event, capture.capture_target, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, core.region, core.site, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point, operation.sms_modem_status | PENDING_RULE |
| ENG-099 / S2 M13 开发任务 | M13 / 实现运营首页设备在线、采集、短信猫、异常统计图表，以及按时间粒度查看明细和导出入口。 | getDashboardSummary, getOperationSummary, getMapFeatures, getDeviceMapFeatures, getMapHeatmap, getMapAreaSummary, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport | analysis.async_task, auth.menu, capture.capture_event, capture.capture_target, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, core.region, core.site, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point, operation.sms_modem_status | PENDING_RULE |
| ENG-100 / S2 M13 开发任务 | M13 / 实现运维报表任务列表、报表详情、单报表在线查看、多报表 ZIP 下载和删除确认。 | getDashboardSummary, getOperationSummary, getMapFeatures, getDeviceMapFeatures, getMapHeatmap, getMapAreaSummary, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport | analysis.async_task, auth.menu, capture.capture_event, capture.capture_target, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, core.region, core.site, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point, operation.sms_modem_status | PENDING_RULE |
| ENG-101 / S2 M13 开发任务 | M13 / 统一设备点位样式、聚合、选中态、状态颜色和地图/列表滚动同步，支持大量设备渲染。 | getDashboardSummary, getOperationSummary, getMapFeatures, getDeviceMapFeatures, getMapHeatmap, getMapAreaSummary, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport | analysis.async_task, auth.menu, capture.capture_event, capture.capture_target, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, core.region, core.site, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point, operation.sms_modem_status | PENDING_RULE |
| ENG-102 / S2 M13 交付物与验收重点 | M13 / 地图筛选条件与列表查询条件保持同步，框选/手动选中能驱动详情和任务创建。 | getDashboardSummary, getOperationSummary, getMapFeatures, getDeviceMapFeatures, getMapHeatmap, getMapAreaSummary, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport | analysis.async_task, auth.menu, capture.capture_event, capture.capture_target, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, core.region, core.site, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point, operation.sms_modem_status | PENDING_RULE |
| ENG-103 / S2 M13 交付物与验收重点 | M13 / 地图与图表在无数据、离线、接口超时和部分失败时有清晰状态，不出现错误颜色误导。 | getDashboardSummary, getOperationSummary, getMapFeatures, getDeviceMapFeatures, getMapHeatmap, getMapAreaSummary, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport | analysis.async_task, auth.menu, capture.capture_event, capture.capture_target, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, core.region, core.site, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point, operation.sms_modem_status | PENDING_RULE |
| ENG-104 / S2 M13 交付物与验收重点 | M13 / 用户从运营首页进入设备、场所、报表后可返回并恢复筛选条件。 | getDashboardSummary, getOperationSummary, getMapFeatures, getDeviceMapFeatures, getMapHeatmap, getMapAreaSummary, listReports, createReport, getReport, deleteReport, getReportPreview, retryReport, downloadReport | analysis.async_task, auth.menu, capture.capture_event, capture.capture_target, core.anomaly_event, core.device, core.file_asset, core.metric_definition, core.metric_scope, core.metric_snapshot, core.region, core.site, operation.report_component_row, operation.report_definition, operation.report_device_row, operation.report_job, operation.report_metric_row, operation.report_output_file, operation.report_region_row, operation.report_series, operation.report_series_point, operation.sms_modem_status | PENDING_RULE |
| ENG-105 / S2 M14 开发任务 | M14 / 角色管理：按角色编号/名称查询，新增、编辑、删除；配置菜单权限、场所权限和数据范围。 | listUsers, createUser, getUser, updateUser, deleteUser, disableUser, enableUser, changeUserPassword, listRoles, createRole, getRole, updateRole, deleteRole, setRolePermissions, setRoleScopes, searchAuditEvents, getAuditEvent, exportAuditEvents, searchSmsLogs, exportSmsLogs | analysis.export_job, audit.audit_event, auth.app_user, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site, operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE |
| ENG-106 / S2 M14 开发任务 | M14 / 用户管理：按用户编号/名称/登录账号查询，新增、编辑、禁用、删除和密码修改；删除前必须禁用。 | listUsers, createUser, getUser, updateUser, deleteUser, disableUser, enableUser, changeUserPassword, listRoles, createRole, getRole, updateRole, deleteRole, setRolePermissions, setRoleScopes, searchAuditEvents, getAuditEvent, exportAuditEvents, searchSmsLogs, exportSmsLogs | analysis.export_job, audit.audit_event, auth.app_user, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site, operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE |
| ENG-107 / S2 M14 开发任务 | M14 / 操作日志：按用户名/操作时间查询、重置、导出；超级管理员可查看所有用户，普通用户只看自身。 | listUsers, createUser, getUser, updateUser, deleteUser, disableUser, enableUser, changeUserPassword, listRoles, createRole, getRole, updateRole, deleteRole, setRolePermissions, setRoleScopes, searchAuditEvents, getAuditEvent, exportAuditEvents, searchSmsLogs, exportSmsLogs | analysis.export_job, audit.audit_event, auth.app_user, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site, operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE |
| ENG-108 / S2 M14 开发任务 | M14 / 短信日志：按任务名称/接收人员/发送时间查询、重置、导出，关联布控/通知任务和发送结果。 | listUsers, createUser, getUser, updateUser, deleteUser, disableUser, enableUser, changeUserPassword, listRoles, createRole, getRole, updateRole, deleteRole, setRolePermissions, setRoleScopes, searchAuditEvents, getAuditEvent, exportAuditEvents, searchSmsLogs, exportSmsLogs | analysis.export_job, audit.audit_event, auth.app_user, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site, operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE |
| ENG-109 / S2 M14 开发任务 | M14 / 所有权限变更、用户状态变更、日志导出和高风险操作写入不可篡改审计事件。 | listUsers, createUser, getUser, updateUser, deleteUser, disableUser, enableUser, changeUserPassword, listRoles, createRole, getRole, updateRole, deleteRole, setRolePermissions, setRoleScopes, searchAuditEvents, getAuditEvent, exportAuditEvents, searchSmsLogs, exportSmsLogs | analysis.export_job, audit.audit_event, auth.app_user, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site, operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE |
| ENG-110 / S2 M14 交付物与验收重点 | M14 / 角色删除受用户引用保护；用户禁用后 Token、任务创建和系统设置权限立即失效或按策略生效。 | listUsers, createUser, getUser, updateUser, deleteUser, disableUser, enableUser, changeUserPassword, listRoles, createRole, getRole, updateRole, deleteRole, setRolePermissions, setRoleScopes, searchAuditEvents, getAuditEvent, exportAuditEvents, searchSmsLogs, exportSmsLogs | analysis.export_job, audit.audit_event, auth.app_user, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site, operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE |
| ENG-111 / S2 M14 交付物与验收重点 | M14 / 操作日志能够回答谁在什么时间对哪个对象执行了什么动作，且敏感字段按权限脱敏。 | listUsers, createUser, getUser, updateUser, deleteUser, disableUser, enableUser, changeUserPassword, listRoles, createRole, getRole, updateRole, deleteRole, setRolePermissions, setRoleScopes, searchAuditEvents, getAuditEvent, exportAuditEvents, searchSmsLogs, exportSmsLogs | analysis.export_job, audit.audit_event, auth.app_user, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site, operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE |
| ENG-112 / S2 M14 交付物与验收重点 | M14 / 审计日志与业务日志可通过 TraceId/业务单号关联，导出结果不泄露无权数据。 | listUsers, createUser, getUser, updateUser, deleteUser, disableUser, enableUser, changeUserPassword, listRoles, createRole, getRole, updateRole, deleteRole, setRolePermissions, setRoleScopes, searchAuditEvents, getAuditEvent, exportAuditEvents, searchSmsLogs, exportSmsLogs | analysis.export_job, audit.audit_event, auth.app_user, auth.role, auth.role_data_type, auth.role_menu, auth.role_permission, auth.role_site, auth.user_role, auth.user_scope, auth.user_scope_data_type, auth.user_scope_site, operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE |
| ENG-113 / S2 M15 开发任务 | M15 / 建立单元、API、集成、回归、E2E、性能、压测、安全和数据质量测试；覆盖登录→检索→地图→分析→布控→通知→运维。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| ENG-114 / S2 M15 开发任务 | M15 / 建设 DEV/TEST/UAT/PROD 环境、配置隔离、数据库迁移、种子数据、测试数据脱敏和发布审批。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| ENG-115 / S2 M15 开发任务 | M15 / 建设 Git 分支规范、CI/CD、自动构建、自动化测试、Docker 镜像、灰度/回滚和版本追踪。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| ENG-116 / S2 M15 开发任务 | M15 / 接入 Metrics、Logs、Trace、Alert 和 TraceId 链路；监控 API QPS/延迟/错误率、Kafka、Redis、数据库和业务指标。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| ENG-117 / S2 M15 开发任务 | M15 / 验证备份恢复、服务降级、高可用、通知失败补偿、数据回放、设备离线和采集异常处置。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| ENG-118 / S2 M15 交付物与验收重点 | M15 / 主流程具备可重复执行的自动化测试，发布前质量门禁能阻止高风险版本进入生产。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| ENG-119 / S2 M15 交付物与验收重点 | M15 / 核心接口、异步任务和消息链路可从监控定位到具体服务、请求和业务对象。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |
| ENG-120 / S2 M15 交付物与验收重点 | M15 / 系统在数据库/缓存/消息/设备异常场景下有演练记录，能够按文档完成恢复或回滚。 | 前端/交付能力 | 无需业务持久化 | NO_API_REQUIRED |

## 全部HTTP操作（193项）

表是该资源族的服务边界；只读API不意味着必须写全部这些表。幂等写入另外使用idempotency_request，业务写入另记audit/outbox；不在每行重复列出。批量删除/撤销按有限resourceType路由到既有实体，批次失败项用task_row_error，不另建并行实体系统。

| 模块 | 操作 | 名称 | 表 | 状态 |
| --- | --- | --- | --- | --- |
| M15 | `GET /health` [getHealth] | 存活检查 | 实时进程/依赖探针 | NO_DB_REQUIRED |
| M15 | `GET /ready` [getReadiness] | 依赖就绪检查 | 实时进程/依赖探针 | NO_DB_REQUIRED |
| M01 | `GET /auth/csrf` [getCsrfToken] | 获取 CSRF 令牌 | auth.app_user, auth.user_session, auth.csrf_session, auth.user_role, auth.role_permission, auth.user_scope, auth.role_site | MAPPED |
| M01 | `POST /auth/login` [login] | 登录并创建服务端会话 | auth.app_user, auth.user_session, auth.csrf_session, auth.user_role, auth.role_permission, auth.user_scope, auth.role_site | MAPPED |
| M01 | `POST /auth/logout` [logout] | 退出并撤销会话 | auth.app_user, auth.user_session, auth.csrf_session, auth.user_role, auth.role_permission, auth.user_scope, auth.role_site | MAPPED |
| M01 | `POST /auth/session/renew` [renewSession] | 续期当前 Cookie 会话 | auth.app_user, auth.user_session, auth.csrf_session, auth.user_role, auth.role_permission, auth.user_scope, auth.role_site | PENDING_RULE |
| M01 | `GET /me` [getMe] | 读取当前用户、菜单与权限范围 | auth.app_user, auth.user_session, auth.csrf_session, auth.user_role, auth.role_permission, auth.user_scope, auth.role_site | MAPPED |
| M01 | `GET /dictionaries/{dictionaryCode}` [getDictionary] | 读取前端字典 | core.dictionary, core.dictionary_item | MAPPED |
| M01 | `GET /client-config` [getClientConfig] | 读取非机密前端配置 | core.config_item, core.feature_config, core.upload_policy, core.dictionary_binding | MAPPED |
| M01 | `POST /files/uploads` [uploadFile] | 上传受控文件 | core.file_asset, analysis.import_job, analysis.async_task | MAPPED |
| M01 | `GET /files/{fileAssetId}` [getFileAsset] | 查询文件处理状态 | core.file_asset, analysis.import_job, analysis.async_task | MAPPED |
| M01 | `GET /files/{fileAssetId}/download` [downloadFile] | 下载受控文件 | core.file_asset, analysis.import_job, analysis.async_task | MAPPED |
| M01 | `POST /exports` [createExport] | 创建异步导出 | analysis.export_job, analysis.async_task, core.file_asset | MAPPED |
| M01 | `GET /tasks` [listTasks] | 查询当前可见任务 | analysis.async_task, analysis.task_attempt, analysis.task_row_error, analysis.task_event, analysis.import_job, analysis.import_row_result | MAPPED |
| M01 | `GET /tasks/{taskId}` [getTask] | 读取任务状态与结果引用 | analysis.async_task, analysis.task_attempt, analysis.task_row_error, analysis.task_event, analysis.import_job, analysis.import_row_result | MAPPED |
| M01 | `POST /tasks/{taskId}/cancel` [cancelTask] | 请求取消任务 | analysis.async_task, analysis.task_attempt, analysis.task_row_error, analysis.task_event, analysis.import_job, analysis.import_row_result | MAPPED |
| M01 | `POST /tasks/{taskId}/retry` [retryTask] | 重试失败任务并保留 taskId | analysis.async_task, analysis.task_attempt, analysis.task_row_error, analysis.task_event, analysis.import_job, analysis.import_row_result | MAPPED |
| M01 | `GET /tasks/{taskId}/attempts` [listTaskAttempts] | 查看任务尝试历史 | analysis.async_task, analysis.task_attempt, analysis.task_row_error, analysis.task_event, analysis.import_job, analysis.import_row_result | MAPPED |
| M01 | `GET /tasks/{taskId}/errors` [listTaskRowErrors] | 分页查看文件导入的逐行错误 | analysis.async_task, analysis.task_attempt, analysis.task_row_error, analysis.task_event, analysis.import_job, analysis.import_row_result | MAPPED |
| M03 | `GET /ingestion-batches` [listIngestionBatches] | 查询接入批次 | capture.ingestion_source, capture.source_mapping_version, capture.ingestion_batch, capture.source_event, capture.ingestion_item, capture.quality_issue, capture.source_counter_sample, core.device_status_event, analysis.async_task | MAPPED |
| M03 | `POST /ingestion-batches` [createIngestionBatch] | 提交文件接入批次 | capture.ingestion_source, capture.source_mapping_version, capture.ingestion_batch, capture.source_event, capture.ingestion_item, capture.quality_issue, capture.source_counter_sample, core.device_status_event, analysis.async_task | PENDING_RULE |
| M03 | `GET /ingestion-batches/{batchId}` [getIngestionBatch] | 查看接入批次详情 | capture.ingestion_source, capture.source_mapping_version, capture.ingestion_batch, capture.source_event, capture.ingestion_item, capture.quality_issue, capture.source_counter_sample, core.device_status_event, analysis.async_task | MAPPED |
| M03 | `GET /ingestion-batches/{batchId}/errors` [listIngestionErrors] | 查看逐条接入错误 | capture.ingestion_source, capture.source_mapping_version, capture.ingestion_batch, capture.source_event, capture.ingestion_item, capture.quality_issue, capture.source_counter_sample, core.device_status_event, analysis.async_task | MAPPED |
| M03 | `GET /ingestion/quality-summary` [getIngestionQualitySummary] | 查看接入质量汇总 | capture.ingestion_source, capture.source_mapping_version, capture.ingestion_batch, capture.source_event, capture.ingestion_item, capture.quality_issue, capture.source_counter_sample, core.device_status_event, analysis.async_task | MAPPED |
| M03 | `POST /ingestion-batches/{batchId}/replay` [replayIngestionBatch] | 按版本回放批次 | capture.ingestion_source, capture.source_mapping_version, capture.ingestion_batch, capture.source_event, capture.ingestion_item, capture.quality_issue, capture.source_counter_sample, core.device_status_event, analysis.async_task | PENDING_RULE |
| M03 | `POST /ingestion/backfills` [createIngestionBackfill] | 创建补数任务 | capture.ingestion_source, capture.source_mapping_version, capture.ingestion_batch, capture.source_event, capture.ingestion_item, capture.quality_issue, capture.source_counter_sample, core.device_status_event, analysis.async_task | PENDING_RULE |
| M04 | `GET /dashboard/summary` [getDashboardSummary] | 读取系统首页 12 类区域 | core.metric_definition, core.metric_scope, core.metric_snapshot, core.device, operation.sms_modem_status, core.anomaly_event, auth.menu | MAPPED |
| M04 | `GET /operations/summary` [getOperationSummary] | 读取运维首页四类区域 | core.metric_definition, core.metric_scope, core.metric_snapshot, core.device, operation.sms_modem_status, core.anomaly_event, auth.menu | MAPPED |
| M05 | `POST /captures/search` [searchCaptures] | 按类型检索采集记录 | capture.capture_event, capture.target, capture.target_lookup, capture.capture_target, capture.fence_record, capture.mac_record, capture.plate_record, capture.face_record, capture.target_attribution, analysis.analysis_context, analysis.export_job | MAPPED |
| M05 | `GET /captures/{captureId}` [getCapture] | 查看采集记录详情 | capture.capture_event, capture.target, capture.target_lookup, capture.capture_target, capture.fence_record, capture.mac_record, capture.plate_record, capture.face_record, capture.target_attribution, analysis.analysis_context, analysis.export_job | MAPPED |
| M06 | `POST /captures/statistics` [getCaptureStatistics] | 统计上下文内目标 | capture.capture_event, capture.target, capture.target_lookup, capture.capture_target, capture.fence_record, capture.mac_record, capture.plate_record, capture.face_record, capture.target_attribution, analysis.analysis_context, analysis.export_job | MAPPED |
| M05 | `POST /faces/search-tasks` [createFaceSearchTask] | 创建人像相似检索任务 | analysis.face_search_job, analysis.face_search_result, core.file_asset, analysis.async_task | PENDING_RULE |
| M05 | `GET /faces/search-tasks/{taskId}/results` [listFaceSearchResults] | 分页读取人像候选 | analysis.face_search_job, analysis.face_search_result, core.file_asset, analysis.async_task | PENDING_RULE |
| M06 | `POST /decrypt-requests` [requestDecryption] | 提交解密申请 | operation.decryption_request, operation.decryption_capture, audit.sensitive_action_review, operation.decryption_result_grant, analysis.async_task | PENDING_RULE |
| M06 | `GET /decrypt-requests/{requestId}` [getDecryptionRequest] | 查询解密申请状态 | operation.decryption_request, operation.decryption_capture, audit.sensitive_action_review, operation.decryption_result_grant, analysis.async_task | PENDING_RULE |
| M06 | `POST /contexts` [createContext] | 建立跨页研判上下文 | analysis.analysis_context, analysis.context_target, analysis.input_snapshot | MAPPED |
| M06 | `GET /contexts/{contextId}` [getContext] | 重校权限后读取上下文 | analysis.analysis_context, analysis.context_target, analysis.input_snapshot | MAPPED |
| M07 | `GET /map/features` [getMapFeatures] | 查询采集点/聚合地图要素 | core.region, core.site, core.device, capture.capture_event, capture.capture_target, core.metric_snapshot | MAPPED |
| M13 | `GET /map/devices` [getDeviceMapFeatures] | 查询设备点与聚合 | core.region, core.site, core.device, capture.capture_event, capture.capture_target, core.metric_snapshot | MAPPED |
| M07 | `GET /map/heatmap` [getMapHeatmap] | 按时间窗读取设备采集热力 | core.region, core.site, core.device, capture.capture_event, capture.capture_target, core.metric_snapshot | MAPPED |
| M07 | `POST /map/area-summary` [getMapAreaSummary] | 统计选区内采集与设备 | core.region, core.site, core.device, capture.capture_event, capture.capture_target, core.metric_snapshot | MAPPED |
| M07 | `POST /trajectories/query` [queryTrajectory] | 分页查询目标轨迹 | core.region, core.site, core.device, capture.capture_event, capture.capture_target, core.metric_snapshot | MAPPED |
| M08 | `GET /analysis-tasks` [listAnalysisTasks] | 按名称、类型和日期查询分析任务 | analysis.analysis_job, analysis.async_task, analysis.input_snapshot, analysis.companion_parameter, analysis.collision_parameter, analysis.region_parameter, analysis.analysis_result, analysis.companion_result, analysis.collision_result, analysis.region_result, analysis.result_evidence, analysis.collision_event, analysis.result_selected_region | MAPPED |
| M08 | `POST /analysis-tasks` [createAnalysisTask] | 创建伴随/碰撞/区域统计任务 | analysis.analysis_job, analysis.async_task, analysis.input_snapshot, analysis.companion_parameter, analysis.collision_parameter, analysis.region_parameter, analysis.analysis_result, analysis.companion_result, analysis.collision_result, analysis.region_result, analysis.result_evidence, analysis.collision_event, analysis.result_selected_region | MAPPED |
| M08 | `GET /analysis-tasks/{analysisTaskId}` [getAnalysisTask] | 查看分析任务与参数快照 | analysis.analysis_job, analysis.async_task, analysis.input_snapshot, analysis.companion_parameter, analysis.collision_parameter, analysis.region_parameter, analysis.analysis_result, analysis.companion_result, analysis.collision_result, analysis.region_result, analysis.result_evidence, analysis.collision_event, analysis.result_selected_region | MAPPED |
| M08 | `DELETE /analysis-tasks/{analysisTaskId}` [deleteAnalysisTask] | 软删终态分析任务 | analysis.analysis_job, analysis.async_task, analysis.input_snapshot, analysis.companion_parameter, analysis.collision_parameter, analysis.region_parameter, analysis.analysis_result, analysis.companion_result, analysis.collision_result, analysis.region_result, analysis.result_evidence, analysis.collision_event, analysis.result_selected_region | MAPPED |
| M08 | `POST /analysis-tasks/{analysisTaskId}/revisions` [reviseAnalysisTask] | 以新版本重建分析任务 | analysis.analysis_job, analysis.async_task, analysis.input_snapshot, analysis.companion_parameter, analysis.collision_parameter, analysis.region_parameter, analysis.analysis_result, analysis.companion_result, analysis.collision_result, analysis.region_result, analysis.result_evidence, analysis.collision_event, analysis.result_selected_region | MAPPED |
| M08 | `GET /analysis-tasks/{analysisTaskId}/results` [listAnalysisResults] | 分页查看分析结果 | analysis.analysis_job, analysis.async_task, analysis.input_snapshot, analysis.companion_parameter, analysis.collision_parameter, analysis.region_parameter, analysis.analysis_result, analysis.companion_result, analysis.collision_result, analysis.region_result, analysis.result_evidence, analysis.collision_event, analysis.result_selected_region | MAPPED |
| M08 | `GET /analysis-tasks/{analysisTaskId}/results/{resultId}` [getAnalysisResult] | 查看分析结果及证据引用 | analysis.analysis_job, analysis.async_task, analysis.input_snapshot, analysis.companion_parameter, analysis.collision_parameter, analysis.region_parameter, analysis.analysis_result, analysis.companion_result, analysis.collision_result, analysis.region_result, analysis.result_evidence, analysis.collision_event, analysis.result_selected_region | MAPPED |
| M08 | `POST /analysis-tasks/{analysisTaskId}/results/export` [exportAnalysisResults] | 异步导出分析结果 | analysis.analysis_job, analysis.async_task, analysis.input_snapshot, analysis.companion_parameter, analysis.collision_parameter, analysis.region_parameter, analysis.analysis_result, analysis.companion_result, analysis.collision_result, analysis.region_result, analysis.result_evidence, analysis.collision_event, analysis.result_selected_region | MAPPED |
| M09 | `GET /datasets` [listDatasets] | 查询数据集 | analysis.dataset, analysis.dataset_version, analysis.dataset_item, analysis.dataset_grant, analysis.set_job, analysis.set_operand, analysis.import_job, analysis.export_job, analysis.async_task | MAPPED |
| M09 | `POST /datasets` [createDataset] | 创建空或上下文数据集 | analysis.dataset, analysis.dataset_version, analysis.dataset_item, analysis.dataset_grant, analysis.set_job, analysis.set_operand, analysis.import_job, analysis.export_job, analysis.async_task | MAPPED |
| M09 | `GET /datasets/{datasetId}` [getDataset] | 查看数据集详情 | analysis.dataset, analysis.dataset_version, analysis.dataset_item, analysis.dataset_grant, analysis.set_job, analysis.set_operand, analysis.import_job, analysis.export_job, analysis.async_task | MAPPED |
| M09 | `PATCH /datasets/{datasetId}` [updateDataset] | 重命名数据集 | analysis.dataset, analysis.dataset_version, analysis.dataset_item, analysis.dataset_grant, analysis.set_job, analysis.set_operand, analysis.import_job, analysis.export_job, analysis.async_task | MAPPED |
| M09 | `DELETE /datasets/{datasetId}` [deleteDataset] | 受引用保护地软删数据集 | analysis.dataset, analysis.dataset_version, analysis.dataset_item, analysis.dataset_grant, analysis.set_job, analysis.set_operand, analysis.import_job, analysis.export_job, analysis.async_task | MAPPED |
| M09 | `GET /datasets/{datasetId}/items` [listDatasetItems] | 游标读取数据集成员 | analysis.dataset, analysis.dataset_version, analysis.dataset_item, analysis.dataset_grant, analysis.set_job, analysis.set_operand, analysis.import_job, analysis.export_job, analysis.async_task | MAPPED |
| M09 | `POST /datasets/{datasetId}/items` [addDatasetItems] | 批量添加数据集成员 | analysis.dataset, analysis.dataset_version, analysis.dataset_item, analysis.dataset_grant, analysis.set_job, analysis.set_operand, analysis.import_job, analysis.export_job, analysis.async_task | MAPPED |
| M09 | `POST /datasets/{datasetId}/imports` [importDatasetItems] | 从查询上下文或文件导入成员 | analysis.dataset, analysis.dataset_version, analysis.dataset_item, analysis.dataset_grant, analysis.set_job, analysis.set_operand, analysis.import_job, analysis.export_job, analysis.async_task | MAPPED |
| M09 | `GET /datasets/{datasetId}/versions` [listDatasetVersions] | 查看数据集版本 | analysis.dataset, analysis.dataset_version, analysis.dataset_item, analysis.dataset_grant, analysis.set_job, analysis.set_operand, analysis.import_job, analysis.export_job, analysis.async_task | MAPPED |
| M09 | `POST /datasets/{datasetId}/export` [exportDataset] | 异步导出数据集 | analysis.dataset, analysis.dataset_version, analysis.dataset_item, analysis.dataset_grant, analysis.set_job, analysis.set_operand, analysis.import_job, analysis.export_job, analysis.async_task | MAPPED |
| M09 | `POST /set-tasks/preview` [previewSetTask] | 预览集合运算兼容性与估量 | analysis.set_job, analysis.set_operand, analysis.dataset_version, analysis.dataset_item, analysis.async_task | MAPPED |
| M09 | `GET /set-tasks` [listSetTasks] | 查询集合运算任务 | analysis.set_job, analysis.set_operand, analysis.dataset_version, analysis.dataset_item, analysis.async_task | MAPPED |
| M09 | `POST /set-tasks` [createSetTask] | 创建异步集合运算 | analysis.set_job, analysis.set_operand, analysis.dataset_version, analysis.dataset_item, analysis.async_task | MAPPED |
| M09 | `GET /set-tasks/{setTaskId}` [getSetTask] | 查看集合任务详情 | analysis.set_job, analysis.set_operand, analysis.dataset_version, analysis.dataset_item, analysis.async_task | MAPPED |
| M09 | `GET /set-tasks/{setTaskId}/results` [listSetTaskResults] | 游标查看集合结果 | analysis.set_job, analysis.set_operand, analysis.dataset_version, analysis.dataset_item, analysis.async_task | MAPPED |
| M10 | `GET /watch-rules` [listWatchRules] | 查询布控规则 | operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_target, operation.watch_rule_site, operation.watch_notification_recipient, operation.watch_statistics_run, operation.watch_statistics, operation.alert_event | MAPPED |
| M10 | `POST /watch-rules` [createWatchRule] | 创建布控草稿 | operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_target, operation.watch_rule_site, operation.watch_notification_recipient, operation.watch_statistics_run, operation.watch_statistics, operation.alert_event | PENDING_RULE |
| M10 | `GET /watch-rules/{ruleId}` [getWatchRule] | 查看规则详情和当前版本 | operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_target, operation.watch_rule_site, operation.watch_notification_recipient, operation.watch_statistics_run, operation.watch_statistics, operation.alert_event | MAPPED |
| M10 | `PATCH /watch-rules/{ruleId}` [updateWatchRule] | 修改规则并生成新版本 | operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_target, operation.watch_rule_site, operation.watch_notification_recipient, operation.watch_statistics_run, operation.watch_statistics, operation.alert_event | PENDING_RULE |
| M10 | `DELETE /watch-rules/{ruleId}` [deleteWatchRule] | 软删终态布控规则 | operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_target, operation.watch_rule_site, operation.watch_notification_recipient, operation.watch_statistics_run, operation.watch_statistics, operation.alert_event | MAPPED |
| M10 | `POST /watch-rules/{ruleId}/activate` [activateWatchRule] | 激活布控草稿 | operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_target, operation.watch_rule_site, operation.watch_notification_recipient, operation.watch_statistics_run, operation.watch_statistics, operation.alert_event | PENDING_RULE |
| M10 | `POST /watch-rules/{ruleId}/revoke` [revokeWatchRule] | 撤销运行中的布控 | operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_target, operation.watch_rule_site, operation.watch_notification_recipient, operation.watch_statistics_run, operation.watch_statistics, operation.alert_event | MAPPED |
| M10 | `GET /watch-rules/{ruleId}/statistics` [getWatchStatistics] | 查看实时命中数或最终统计 | operation.watch_rule, operation.watch_rule_revision, operation.watch_rule_target, operation.watch_rule_site, operation.watch_notification_recipient, operation.watch_statistics_run, operation.watch_statistics, operation.alert_event | MAPPED |
| M10 | `GET /alerts` [listAlerts] | 游标检索实时告警 | operation.alert_event, operation.watch_rule_revision, capture.capture_event, operation.delivery | MAPPED |
| M10 | `GET /alerts/{alertId}` [getAlert] | 查看告警、源采集、规则快照与送达 | operation.alert_event, operation.watch_rule_revision, capture.capture_event, operation.delivery | MAPPED |
| M10 | `GET /notification-tasks` [listNotificationTasks] | 查询通知任务 | operation.notification_plan, operation.notification_plan_revision, operation.notification_plan_recipient, operation.notification_recipient, operation.notification_execution, operation.delivery, analysis.async_task | MAPPED |
| M10 | `POST /notification-tasks` [createNotificationTask] | 创建定时通知任务 | operation.notification_plan, operation.notification_plan_revision, operation.notification_plan_recipient, operation.notification_recipient, operation.notification_execution, operation.delivery, analysis.async_task | PENDING_RULE |
| M10 | `GET /notification-tasks/{notificationTaskId}` [getNotificationTask] | 查看通知任务详情 | operation.notification_plan, operation.notification_plan_revision, operation.notification_plan_recipient, operation.notification_recipient, operation.notification_execution, operation.delivery, analysis.async_task | MAPPED |
| M10 | `DELETE /notification-tasks/{notificationTaskId}` [deleteNotificationTask] | 软删已终结通知任务 | operation.notification_plan, operation.notification_plan_revision, operation.notification_plan_recipient, operation.notification_recipient, operation.notification_execution, operation.delivery, analysis.async_task | MAPPED |
| M10 | `PUT /notification-tasks/{notificationTaskId}` [updateNotificationTask] | 更新未终结通知计划 | operation.notification_plan, operation.notification_plan_revision, operation.notification_plan_recipient, operation.notification_recipient, operation.notification_execution, operation.delivery, analysis.async_task | PENDING_RULE |
| M10 | `POST /notification-tasks/{notificationTaskId}/cancel` [cancelNotificationTask] | 请求取消未完成通知 | operation.notification_plan, operation.notification_plan_revision, operation.notification_plan_recipient, operation.notification_recipient, operation.notification_execution, operation.delivery, analysis.async_task | MAPPED |
| M10 | `POST /notification-tasks/{notificationTaskId}/retry` [retryNotificationTask] | 重试符合条件的失败通知 | operation.notification_plan, operation.notification_plan_revision, operation.notification_plan_recipient, operation.notification_recipient, operation.notification_execution, operation.delivery, analysis.async_task | PENDING_RULE |
| M10 | `GET /deliveries` [listDeliveries] | 游标查看送达记录 | operation.delivery, operation.delivery_attempt, operation.notification_recipient | MAPPED |
| M10 | `GET /deliveries/{deliveryId}` [getDelivery] | 查看送达状态与失败原因 | operation.delivery, operation.delivery_attempt, operation.notification_recipient | MAPPED |
| M11 | `GET /person-libraries` [listPersonLibraries] | 查询人员库 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | MAPPED |
| M11 | `POST /person-libraries` [createPersonLibrary] | 新建人员库 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | MAPPED |
| M11 | `GET /person-libraries/{libraryId}` [getPersonLibrary] | 查看人员库详情 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | MAPPED |
| M11 | `PUT /person-libraries/{libraryId}` [updatePersonLibrary] | 编辑人员库 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | MAPPED |
| M11 | `DELETE /person-libraries/{libraryId}` [deletePersonLibrary] | 删除人员库 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | MAPPED |
| M11 | `GET /person-libraries/{libraryId}/members` [listPersonLibraryMembers] | 查询人员库成员 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | MAPPED |
| M11 | `POST /person-libraries/{libraryId}/members` [addPersonLibraryMembers] | 添加人员库成员 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | PENDING_RULE |
| M11 | `DELETE /person-libraries/{libraryId}/members/{memberId}` [removePersonLibraryMember] | 移除人员库成员 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | MAPPED |
| M11 | `POST /person-libraries/{libraryId}/imports` [importPersonLibraryMembers] | 从文件导入人员库成员 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | PENDING_RULE |
| M11 | `POST /person-libraries/{libraryId}/exports` [exportPersonLibrary] | 导出人员库 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | PENDING_RULE |
| M11 | `POST /profiles/search` [searchProfiles] | 检索人车档案 | analysis.profile, analysis.person_profile, analysis.vehicle_profile, analysis.profile_link, analysis.profile_evidence, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.profile_conflict | PENDING_RULE |
| M11 | `GET /profiles/{profileId}` [getProfile] | 查看档案详情 | analysis.profile, analysis.person_profile, analysis.vehicle_profile, analysis.profile_link, analysis.profile_evidence, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.profile_conflict | PENDING_RULE |
| M11 | `GET /profiles/{profileId}/targets` [listProfileTargets] | 查看档案关联目标 | analysis.profile, analysis.person_profile, analysis.vehicle_profile, analysis.profile_link, analysis.profile_evidence, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.profile_conflict | PENDING_RULE |
| M11 | `GET /profiles/{profileId}/evidence` [listProfileEvidence] | 查看档案证据 | analysis.profile, analysis.person_profile, analysis.vehicle_profile, analysis.profile_link, analysis.profile_evidence, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.profile_conflict | MAPPED |
| M11 | `GET /profiles/{profileId}/history` [listProfileHistory] | 查看档案修订历史 | analysis.profile, analysis.person_profile, analysis.vehicle_profile, analysis.profile_link, analysis.profile_evidence, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.profile_conflict | MAPPED |
| M11 | `GET /profiles/{profileId}/trajectories` [listProfileTrajectories] | 查看档案轨迹 | analysis.profile, analysis.person_profile, analysis.vehicle_profile, analysis.profile_link, analysis.profile_evidence, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.profile_conflict | PENDING_RULE |
| M11 | `POST /profiles/{profileId}/corrections` [createProfileCorrection] | 人工修订档案 | analysis.profile, analysis.person_profile, analysis.vehicle_profile, analysis.profile_link, analysis.profile_evidence, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.profile_conflict | PENDING_RULE |
| M11 | `POST /profiles/merge` [mergeProfiles] | 合并档案 | analysis.profile, analysis.person_profile, analysis.vehicle_profile, analysis.profile_link, analysis.profile_evidence, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.profile_conflict | PENDING_RULE |
| M11 | `POST /profiles/{profileId}/split` [splitProfile] | 拆分档案 | analysis.profile, analysis.person_profile, analysis.vehicle_profile, analysis.profile_link, analysis.profile_evidence, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.profile_conflict | PENDING_RULE |
| M11 | `POST /profiles/{profileId}/conflicts/{conflictId}/resolve` [resolveProfileConflict] | 处理档案证据冲突 | analysis.profile, analysis.person_profile, analysis.vehicle_profile, analysis.profile_link, analysis.profile_evidence, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.profile_conflict | PENDING_RULE |
| M11 | `POST /profiles/{profileId}/exports` [exportProfile] | 导出档案 | analysis.profile, analysis.person_profile, analysis.vehicle_profile, analysis.profile_link, analysis.profile_evidence, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.profile_conflict | PENDING_RULE |
| M11 | `GET /trend-models` [listTrendModels] | 查询趋势模型 | analysis.trend_model, analysis.model_parameter_definition, analysis.model_parameter_version, analysis.model_parameter_value, analysis.model_matching_rule, analysis.model_tag, analysis.model_execution, analysis.model_result, analysis.model_result_evidence | PENDING_RULE |
| M11 | `POST /trend-models` [createTrendModel] | 新建趋势模型 | analysis.trend_model, analysis.model_parameter_definition, analysis.model_parameter_version, analysis.model_parameter_value, analysis.model_matching_rule, analysis.model_tag, analysis.model_execution, analysis.model_result, analysis.model_result_evidence | PENDING_RULE |
| M11 | `GET /trend-models/{modelId}` [getTrendModel] | 查看趋势模型详情 | analysis.trend_model, analysis.model_parameter_definition, analysis.model_parameter_version, analysis.model_parameter_value, analysis.model_matching_rule, analysis.model_tag, analysis.model_execution, analysis.model_result, analysis.model_result_evidence | PENDING_RULE |
| M11 | `PUT /trend-models/{modelId}` [updateTrendModel] | 编辑趋势模型 | analysis.trend_model, analysis.model_parameter_definition, analysis.model_parameter_version, analysis.model_parameter_value, analysis.model_matching_rule, analysis.model_tag, analysis.model_execution, analysis.model_result, analysis.model_result_evidence | PENDING_RULE |
| M11 | `GET /trend-models/{modelId}/parameter-versions` [listModelParameterVersions] | 查询模型参数版本 | analysis.trend_model, analysis.model_parameter_definition, analysis.model_parameter_version, analysis.model_parameter_value, analysis.model_matching_rule, analysis.model_tag, analysis.model_execution, analysis.model_result, analysis.model_result_evidence | MAPPED |
| M11 | `POST /trend-models/{modelId}/parameter-versions` [createModelParameterVersion] | 新增模型参数版本 | analysis.trend_model, analysis.model_parameter_definition, analysis.model_parameter_version, analysis.model_parameter_value, analysis.model_matching_rule, analysis.model_tag, analysis.model_execution, analysis.model_result, analysis.model_result_evidence | PENDING_RULE |
| M11 | `GET /trend-models/{modelId}/parameter-versions/{parameterVersionId}` [getModelParameterVersion] | 查看模型参数版本 | analysis.trend_model, analysis.model_parameter_definition, analysis.model_parameter_version, analysis.model_parameter_value, analysis.model_matching_rule, analysis.model_tag, analysis.model_execution, analysis.model_result, analysis.model_result_evidence | MAPPED |
| M11 | `GET /model-tags` [listModelTags] | 查询模型标签目录 | analysis.trend_model, analysis.model_parameter_definition, analysis.model_parameter_version, analysis.model_parameter_value, analysis.model_matching_rule, analysis.model_tag, analysis.model_execution, analysis.model_result, analysis.model_result_evidence | PENDING_RULE |
| M11 | `POST /trend-models/{modelId}/runs` [runTrendModel] | 执行趋势模型 | analysis.trend_model, analysis.model_parameter_definition, analysis.model_parameter_version, analysis.model_parameter_value, analysis.model_matching_rule, analysis.model_tag, analysis.model_execution, analysis.model_result, analysis.model_result_evidence | PENDING_RULE |
| M11 | `GET /trend-models/{modelId}/results` [listTrendModelResults] | 查看模型执行结果 | analysis.trend_model, analysis.model_parameter_definition, analysis.model_parameter_version, analysis.model_parameter_value, analysis.model_matching_rule, analysis.model_tag, analysis.model_execution, analysis.model_result, analysis.model_result_evidence | PENDING_RULE |
| M11 | `GET /trend-models/{modelId}/results/{resultId}` [getTrendModelResult] | 查看模型结果详情 | analysis.trend_model, analysis.model_parameter_definition, analysis.model_parameter_version, analysis.model_parameter_value, analysis.model_matching_rule, analysis.model_tag, analysis.model_execution, analysis.model_result, analysis.model_result_evidence | PENDING_RULE |
| M12 | `GET /sites` [listSites] | 查询场所 | core.site, core.device | MAPPED |
| M12 | `POST /sites` [createSite] | 新增场所 | core.site, core.device | MAPPED |
| M12 | `GET /sites/{siteId}` [getSite] | 查看场所详情 | core.site, core.device | MAPPED |
| M12 | `PUT /sites/{siteId}` [updateSite] | 编辑场所 | core.site, core.device | MAPPED |
| M12 | `DELETE /sites/{siteId}` [deleteSite] | 删除场所 | core.site, core.device | MAPPED |
| M12 | `GET /devices` [listDevices] | 查询设备 | core.device, core.device_revision, core.device_parameter_definition, core.device_parameter_value, core.device_status_event, core.metric_snapshot, capture.capture_event, analysis.import_job, analysis.export_job | MAPPED |
| M12 | `POST /devices` [createDevice] | 新增设备 | core.device, core.device_revision, core.device_parameter_definition, core.device_parameter_value, core.device_status_event, core.metric_snapshot, capture.capture_event, analysis.import_job, analysis.export_job | PENDING_RULE |
| M12 | `GET /devices/{deviceId}` [getDevice] | 查看设备详情 | core.device, core.device_revision, core.device_parameter_definition, core.device_parameter_value, core.device_status_event, core.metric_snapshot, capture.capture_event, analysis.import_job, analysis.export_job | MAPPED |
| M12 | `PUT /devices/{deviceId}` [updateDevice] | 编辑设备信息 | core.device, core.device_revision, core.device_parameter_definition, core.device_parameter_value, core.device_status_event, core.metric_snapshot, capture.capture_event, analysis.import_job, analysis.export_job | PENDING_RULE |
| M12 | `DELETE /devices/{deviceId}` [deleteDevice] | 删除设备 | core.device, core.device_revision, core.device_parameter_definition, core.device_parameter_value, core.device_status_event, core.metric_snapshot, capture.capture_event, analysis.import_job, analysis.export_job | MAPPED |
| M12 | `GET /devices/{deviceId}/status` [getDeviceStatus] | 查看设备实时状态 | core.device, core.device_revision, core.device_parameter_definition, core.device_parameter_value, core.device_status_event, core.metric_snapshot, capture.capture_event, analysis.import_job, analysis.export_job | MAPPED |
| M12 | `GET /devices/{deviceId}/parameters` [getDeviceParameters] | 读取设备参数 | core.device, core.device_revision, core.device_parameter_definition, core.device_parameter_value, core.device_status_event, core.metric_snapshot, capture.capture_event, analysis.import_job, analysis.export_job | PENDING_RULE |
| M12 | `POST /devices/{deviceId}/parameter-updates` [updateDeviceParameters] | 提交设备参数更新 | core.device, core.device_revision, core.device_parameter_definition, core.device_parameter_value, core.device_status_event, core.metric_snapshot, capture.capture_event, analysis.import_job, analysis.export_job | PENDING_RULE |
| M12 | `GET /devices/{deviceId}/status-history` [listDeviceStatusHistory] | 查看设备在线离线历史 | core.device, core.device_revision, core.device_parameter_definition, core.device_parameter_value, core.device_status_event, core.metric_snapshot, capture.capture_event, analysis.import_job, analysis.export_job | MAPPED |
| M12 | `GET /devices/{deviceId}/capture-statistics` [getDeviceCaptureStatistics] | 查看设备采集历史 | core.device, core.device_revision, core.device_parameter_definition, core.device_parameter_value, core.device_status_event, core.metric_snapshot, capture.capture_event, analysis.import_job, analysis.export_job | PENDING_RULE |
| M12 | `POST /devices/imports` [importDevices] | 批量导入设备 | core.device, core.device_revision, core.device_parameter_definition, core.device_parameter_value, core.device_status_event, core.metric_snapshot, capture.capture_event, analysis.import_job, analysis.export_job | PENDING_RULE |
| M12 | `POST /devices/exports` [exportDevices] | 导出设备清单 | core.device, core.device_revision, core.device_parameter_definition, core.device_parameter_value, core.device_status_event, core.metric_snapshot, capture.capture_event, analysis.import_job, analysis.export_job | PENDING_RULE |
| M12 | `GET /device-groups` [listDeviceGroups] | 查询设备分组 | core.device_group, core.device_group_member, core.device | MAPPED |
| M12 | `POST /device-groups` [createDeviceGroup] | 新建设备分组 | core.device_group, core.device_group_member, core.device | MAPPED |
| M12 | `GET /device-groups/{groupId}` [getDeviceGroup] | 查看设备分组详情 | core.device_group, core.device_group_member, core.device | MAPPED |
| M12 | `PUT /device-groups/{groupId}` [updateDeviceGroup] | 编辑设备分组 | core.device_group, core.device_group_member, core.device | MAPPED |
| M12 | `DELETE /device-groups/{groupId}` [deleteDeviceGroup] | 删除设备分组 | core.device_group, core.device_group_member, core.device | MAPPED |
| M12 | `GET /device-groups/{groupId}/members` [listDeviceGroupMembers] | 查询分组设备 | core.device_group, core.device_group_member, core.device | MAPPED |
| M12 | `POST /device-groups/{groupId}/members` [addDeviceGroupMembers] | 添加分组设备 | core.device_group, core.device_group_member, core.device | MAPPED |
| M12 | `DELETE /device-groups/{groupId}/members/{deviceId}` [removeDeviceGroupMember] | 移除分组设备 | core.device_group, core.device_group_member, core.device | MAPPED |
| M12 | `GET /device-commands` [listDeviceCommands] | 查询设备命令任务 | operation.device_command_job, operation.device_command, operation.device_command_attempt, analysis.async_task, audit.sensitive_action_review | MAPPED |
| M12 | `POST /device-commands` [createDeviceCommand] | 下发远程设备命令 | operation.device_command_job, operation.device_command, operation.device_command_attempt, analysis.async_task, audit.sensitive_action_review | PENDING_RULE |
| M12 | `GET /device-commands/{taskId}` [getDeviceCommand] | 查看设备命令任务 | operation.device_command_job, operation.device_command, operation.device_command_attempt, analysis.async_task, audit.sensitive_action_review | MAPPED |
| M12 | `DELETE /device-commands/{taskId}` [deleteDeviceCommand] | 软删终态配置任务 | operation.device_command_job, operation.device_command, operation.device_command_attempt, analysis.async_task, audit.sensitive_action_review | MAPPED |
| M12 | `GET /device-commands/{taskId}/results` [listDeviceCommandResults] | 查看逐设备执行结果 | operation.device_command_job, operation.device_command, operation.device_command_attempt, analysis.async_task, audit.sensitive_action_review | MAPPED |
| M12 | `POST /device-commands/{taskId}/retry` [retryDeviceCommand] | 重试失败的设备命令 | operation.device_command_job, operation.device_command, operation.device_command_attempt, analysis.async_task, audit.sensitive_action_review | PENDING_RULE |
| M12 | `GET /reports` [listReports] | 查询报表任务 | operation.report_job, operation.report_definition, operation.report_device_row, operation.report_region_row, operation.report_metric_row, operation.report_series, operation.report_series_point, operation.report_component_row, operation.report_output_file, core.file_asset, analysis.async_task | MAPPED |
| M12 | `POST /reports` [createReport] | 创建报表任务 | operation.report_job, operation.report_definition, operation.report_device_row, operation.report_region_row, operation.report_metric_row, operation.report_series, operation.report_series_point, operation.report_component_row, operation.report_output_file, core.file_asset, analysis.async_task | PENDING_RULE |
| M12 | `GET /reports/{reportId}` [getReport] | 查看报表详情 | operation.report_job, operation.report_definition, operation.report_device_row, operation.report_region_row, operation.report_metric_row, operation.report_series, operation.report_series_point, operation.report_component_row, operation.report_output_file, core.file_asset, analysis.async_task | MAPPED |
| M12 | `DELETE /reports/{reportId}` [deleteReport] | 删除报表任务 | operation.report_job, operation.report_definition, operation.report_device_row, operation.report_region_row, operation.report_metric_row, operation.report_series, operation.report_series_point, operation.report_component_row, operation.report_output_file, core.file_asset, analysis.async_task | MAPPED |
| M12 | `GET /reports/{reportId}/preview` [getReportPreview] | 预览单报表 | operation.report_job, operation.report_definition, operation.report_device_row, operation.report_region_row, operation.report_metric_row, operation.report_series, operation.report_series_point, operation.report_component_row, operation.report_output_file, core.file_asset, analysis.async_task | PENDING_RULE |
| M12 | `POST /reports/{reportId}/retry` [retryReport] | 重试失败报表 | operation.report_job, operation.report_definition, operation.report_device_row, operation.report_region_row, operation.report_metric_row, operation.report_series, operation.report_series_point, operation.report_component_row, operation.report_output_file, core.file_asset, analysis.async_task | MAPPED |
| M12 | `GET /reports/{reportId}/download` [downloadReport] | 下载报表文件 | operation.report_job, operation.report_definition, operation.report_device_row, operation.report_region_row, operation.report_metric_row, operation.report_series, operation.report_series_point, operation.report_component_row, operation.report_output_file, core.file_asset, analysis.async_task | MAPPED |
| M14 | `GET /users` [listUsers] | 查询用户 | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_site, auth.user_scope_data_type | MAPPED |
| M14 | `POST /users` [createUser] | 新增用户 | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_site, auth.user_scope_data_type | PENDING_RULE |
| M14 | `GET /users/{userId}` [getUser] | 查看用户详情 | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_site, auth.user_scope_data_type | MAPPED |
| M14 | `PUT /users/{userId}` [updateUser] | 编辑用户 | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_site, auth.user_scope_data_type | PENDING_RULE |
| M14 | `DELETE /users/{userId}` [deleteUser] | 删除已禁用用户 | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_site, auth.user_scope_data_type | MAPPED |
| M14 | `POST /users/{userId}/disable` [disableUser] | 禁用用户 | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_site, auth.user_scope_data_type | PENDING_RULE |
| M14 | `POST /users/{userId}/enable` [enableUser] | 启用用户 | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_site, auth.user_scope_data_type | MAPPED |
| M14 | `POST /users/{userId}/password` [changeUserPassword] | 修改用户密码 | auth.app_user, auth.user_role, auth.user_scope, auth.user_scope_site, auth.user_scope_data_type | PENDING_RULE |
| M14 | `GET /roles` [listRoles] | 查询角色 | auth.role, auth.role_permission, auth.role_menu, auth.role_site, auth.role_data_type, auth.user_role | MAPPED |
| M14 | `POST /roles` [createRole] | 新增角色 | auth.role, auth.role_permission, auth.role_menu, auth.role_site, auth.role_data_type, auth.user_role | PENDING_RULE |
| M14 | `GET /roles/{roleId}` [getRole] | 查看角色详情 | auth.role, auth.role_permission, auth.role_menu, auth.role_site, auth.role_data_type, auth.user_role | MAPPED |
| M14 | `PUT /roles/{roleId}` [updateRole] | 编辑角色基本信息 | auth.role, auth.role_permission, auth.role_menu, auth.role_site, auth.role_data_type, auth.user_role | MAPPED |
| M14 | `DELETE /roles/{roleId}` [deleteRole] | 删除未被引用的角色 | auth.role, auth.role_permission, auth.role_menu, auth.role_site, auth.role_data_type, auth.user_role | MAPPED |
| M14 | `PUT /roles/{roleId}/permissions` [setRolePermissions] | 设置角色菜单和按钮权限 | auth.role, auth.role_permission, auth.role_menu, auth.role_site, auth.role_data_type, auth.user_role | PENDING_RULE |
| M14 | `PUT /roles/{roleId}/scopes` [setRoleScopes] | 设置角色场所和数据范围 | auth.role, auth.role_permission, auth.role_menu, auth.role_site, auth.role_data_type, auth.user_role | PENDING_RULE |
| M14 | `POST /audit-events/search` [searchAuditEvents] | 查询操作日志 | audit.audit_event, analysis.export_job | PENDING_RULE |
| M14 | `GET /audit-events/{eventId}` [getAuditEvent] | 查看操作日志详情 | audit.audit_event, analysis.export_job | MAPPED |
| M14 | `POST /audit-events/export` [exportAuditEvents] | 导出操作日志 | audit.audit_event, analysis.export_job | PENDING_RULE |
| M14 | `POST /deliveries/search` [searchSmsLogs] | 查询短信送达日志 | operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE |
| M14 | `POST /deliveries/export` [exportSmsLogs] | 导出短信送达日志 | operation.delivery, operation.delivery_attempt, operation.notification_recipient | PENDING_RULE |
| M01 | `GET /permissions` [listPermissionCatalog] | 读取可分配权限目录 | auth.permission, auth.role_permission | MAPPED |
| M01 | `GET /menus` [listMenuCatalog] | 读取可分配菜单目录 | auth.menu, auth.role_menu | MAPPED |
| M01 | `GET /regions` [listRegions] | 读取授权区域目录 | core.region | MAPPED |
| M06 | `POST /targets/resolve` [resolveTargets] | 批量解析或登记目标代理ID | capture.target, capture.capture_event, capture.capture_target, analysis.profile_link | PENDING_RULE |
| M04 | `POST /metrics/exports` [exportMetrics] | 导出首页或运维指标图表 | core.metric_definition, core.metric_scope, core.metric_snapshot, core.device, operation.sms_modem_status, core.anomaly_event, auth.menu | PENDING_RULE |
| M09 | `DELETE /datasets/{datasetId}/items/{itemId}` [removeDatasetItem] | 移除数据集成员 | analysis.dataset, analysis.dataset_version, analysis.dataset_item, analysis.dataset_grant, analysis.set_job, analysis.set_operand, analysis.import_job, analysis.export_job, analysis.async_task | MAPPED |
| M10 | `GET /notification-templates` [listNotificationTemplates] | 查询已批准通知模板 | operation.notification_template, operation.notification_template_variable | PENDING_RULE |
| M10 | `GET /messages` [listPlatformMessages] | 查询本人平台消息 | operation.platform_message, operation.delivery | MAPPED |
| M11 | `POST /person-libraries/{libraryId}/persons` [createLibraryPerson] | 新增库内人员及手工档案 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | PENDING_RULE |
| M11 | `GET /profiles/{profileId}/conflicts` [listProfileConflicts] | 读取可复核档案冲突 | analysis.profile, analysis.person_profile, analysis.vehicle_profile, analysis.profile_link, analysis.profile_evidence, analysis.profile_link_evidence, analysis.profile_revision, analysis.profile_revision_member, analysis.profile_conflict | PENDING_RULE |
| M01 | `GET /import-templates/{templateCode}` [getImportTemplate] | 读取导入模板列与文件约定 | core.import_template, core.import_column | MAPPED |
| M11 | `POST /person-libraries/imports` [importPersonLibraries] | 批量导入库列表和库内人员 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | PENDING_RULE |
| M11 | `POST /person-libraries/exports` [exportPersonLibraries] | 导出选定多个人员库 | analysis.person_library, analysis.person_library_member, analysis.library_scope_site, analysis.library_scope_data_type, analysis.profile, analysis.person_profile, analysis.profile_link, analysis.import_job, analysis.export_job | MAPPED |
| M12 | `POST /device-commands/{taskId}/revisions` [reviseDeviceCommand] | 重建参数配置任务 | operation.device_command_job, operation.device_command, operation.device_command_attempt, analysis.async_task, audit.sensitive_action_review | PENDING_RULE |
| M01 | `POST /me/password` [changeMyPassword] | 修改本人密码 | auth.app_user, auth.user_session, auth.csrf_session, auth.user_role, auth.role_permission, auth.user_scope, auth.role_site | PENDING_RULE |
| M01 | `POST /batch-deletions` [deleteResourcesBatch] | 批量软删选定记录 | analysis.async_task, analysis.task_row_error, core.idempotency_request, audit.audit_event | MAPPED |
| M08 | `POST /analysis-tasks/exports` [exportAnalysisTasks] | 合并导出选定分析任务结果 | analysis.analysis_job, analysis.async_task, analysis.input_snapshot, analysis.companion_parameter, analysis.collision_parameter, analysis.region_parameter, analysis.analysis_result, analysis.companion_result, analysis.collision_result, analysis.region_result, analysis.result_evidence, analysis.collision_event, analysis.result_selected_region | MAPPED |
| M01 | `GET /tasks/{taskId}/import-result` [getImportTaskResult] | 读取成功导入结果和领域ID | analysis.async_task, analysis.task_attempt, analysis.task_row_error, analysis.task_event, analysis.import_job, analysis.import_row_result | MAPPED |
| M10 | `POST /batch-revocations` [revokeResourcesBatch] | 批量撤销布控或通知计划 | analysis.async_task, operation.watch_rule, operation.notification_plan, analysis.task_row_error, audit.audit_event | MAPPED |

## 核心DTO字段来源（164个Schema，1148字段）

列表/详情共享同一投影；数据字段与几何不任意Any化。授权、分页游标、GeoJSON包装和统计值是有定义的服务投影，不要求机械创建数据库列。以下不能将PENDING描述解释为已决定规则。

| API Schema | 字段 | 物理字段或计算来源 |
| --- | --- | --- | --- |
| UserSummary | `userId` | auth.user_role.user_id |
| UserSummary | `loginName` | auth.app_user.login_name |
| UserSummary | `displayName` | auth.app_user.display_name |
| UserSummary | `status` | auth.app_user.status |
| UserDetail | `userId` | auth.user_role.user_id |
| UserDetail | `userCode` | auth.app_user.user_code |
| UserDetail | `loginName` | auth.app_user.login_name |
| UserDetail | `displayName` | auth.app_user.display_name |
| UserDetail | `status` | auth.app_user.status |
| UserDetail | `roleIds` | auth.user_role.role_id |
| UserDetail | `siteScope` | auth.user_scope + auth.user_scope_site；服务端组合规则P-002 |
| UserDetail | `dataScope` | auth.user_scope + auth.user_scope_data_type/site；P-002 |
| UserDetail | `lastLoginAt` | auth.app_user.last_login_at |
| UserDetail | `createdAt` | auth.app_user.created_at |
| UserDetail | `updatedAt` | auth.app_user.updated_at |
| UserDetail | `version` | auth.app_user.version |
| RoleSummary | `roleId` | auth.role_permission.role_id |
| RoleSummary | `roleCode` | auth.role.role_code |
| RoleSummary | `name` | auth.role.name |
| RoleSummary | `assignedUserCount` | COUNT(auth.user_role WHERE role_id=当前角色) |
| RoleSummary | `updatedAt` | auth.role.updated_at |
| RoleSummary | `version` | auth.role.version |
| RoleDetail | `roleId` | auth.role_permission.role_id |
| RoleDetail | `roleCode` | auth.role.role_code |
| RoleDetail | `name` | auth.role.name |
| RoleDetail | `description` | auth.role.description |
| RoleDetail | `permissionCodes` | auth.role_permission.permission_code |
| RoleDetail | `menuIds` | auth.role_menu.menu_id |
| RoleDetail | `siteScope` | 用户auth.user_scope_site/角色auth.role_site/人员库analysis.library_scope_site +对应all_sites标志；P-002组合规则待签认 |
| RoleDetail | `dataScope` | auth.role.data_all_sites/own_only +role_data_type/site +策略版本P-002 |
| RoleDetail | `assignedUserCount` | COUNT(auth.user_role WHERE role_id=当前角色) |
| RoleDetail | `createdAt` | auth.role.created_at |
| RoleDetail | `updatedAt` | auth.role.updated_at |
| RoleDetail | `version` | auth.role.version |
| Menu | `menuId` | auth.menu.id |
| Menu | `parentMenuId` | auth.menu.parent_id |
| Menu | `code` | auth.menu.code |
| Menu | `label` | auth.menu.label |
| Menu | `route` | auth.menu.route |
| Menu | `order` | sort_order（菜单/字典条目） |
| Menu | `requiredPermission` | auth.menu.required_permission_code |
| PermissionDescriptor | `code` | auth.menu.code |
| PermissionDescriptor | `label` | auth.menu.label |
| PermissionDescriptor | `module` | auth.permission.module_code |
| PermissionDescriptor | `description` | auth.permission.description |
| PermissionDescriptor | `assignable` | auth.permission.assignable |
| SessionData | `userId` | auth.user_session.user_id |
| SessionData | `idleExpiresAt` | auth.user_session.idle_expires_at |
| SessionData | `absoluteExpiresAt` | auth.user_session.absolute_expires_at |
| CurrentUserData | `user` | 按固定子Schema组装；源表 auth.menu,auth.permission,auth.user_session,auth.app_user,auth.user_role,auth.role_permission,auth.user_scope |
| CurrentUserData | `roleIds` | auth.user_role.role_id |
| CurrentUserData | `permissionCodes` | auth.role_permission.permission_code |
| CurrentUserData | `menus` | auth.role_menu ->auth.menu，过滤接口权限与启用状态 |
| CurrentUserData | `dataScope` | 用户/角色/库data_type条目与data_all_sites/own_only/scope_version；P-002 |
| CurrentUserData | `siteScope` | 用户auth.user_scope_site/角色auth.role_site/人员库analysis.library_scope_site +对应all_sites标志；P-002组合规则待签认 |
| CurrentUserData | `session` | 按固定子Schema组装；源表 auth.menu,auth.permission,auth.user_session,auth.app_user,auth.user_role,auth.role_permission,auth.user_scope |
| CommonCaptureRecord | `captureId` | capture.capture_target.capture_id |
| CommonCaptureRecord | `dataType` | capture.capture_event.data_type |
| CommonCaptureRecord | `targetId` | capture.target_lookup.target_id |
| CommonCaptureRecord | `targetType` | capture.capture_event.target_type |
| CommonCaptureRecord | `deviceId` | capture.capture_event.device_id |
| CommonCaptureRecord | `siteId` | capture.capture_event.site_id |
| CommonCaptureRecord | `captureTime` | capture.capture_event.capture_time |
| CommonCaptureRecord | `ingestedAt` | capture.capture_event.ingested_at |
| CommonCaptureRecord | `longitude` | ST_X(capture.capture_event.location) |
| CommonCaptureRecord | `latitude` | ST_Y(capture.capture_event.location) |
| CommonCaptureRecord | `source` | analysis.profile_link.source |
| CommonCaptureRecord | `status` | capture.capture_event.status |
| CommonCaptureRecord | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| CommonCaptureRecord | `isMasked` | 授权投影计算；常规采集接口为脱敏，解密仅受控文件 |
| FenceCaptureRecord | `captureId` | capture.capture_target.capture_id |
| FenceCaptureRecord | `dataType` | capture.capture_event.data_type |
| FenceCaptureRecord | `targetId` | capture.target_lookup.target_id |
| FenceCaptureRecord | `targetType` | capture.capture_event.target_type |
| FenceCaptureRecord | `deviceId` | capture.capture_event.device_id |
| FenceCaptureRecord | `siteId` | capture.capture_event.site_id |
| FenceCaptureRecord | `captureTime` | capture.capture_event.capture_time |
| FenceCaptureRecord | `ingestedAt` | capture.capture_event.ingested_at |
| FenceCaptureRecord | `longitude` | ST_X(capture.capture_event.location) |
| FenceCaptureRecord | `latitude` | ST_Y(capture.capture_event.location) |
| FenceCaptureRecord | `source` | analysis.profile_link.source |
| FenceCaptureRecord | `status` | capture.capture_event.status |
| FenceCaptureRecord | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| FenceCaptureRecord | `isMasked` | 授权投影计算；常规采集接口为脱敏，解密仅受控文件 |
| FenceCaptureRecord | `fence` | capture.fence_record |
| MacCaptureRecord | `captureId` | capture.capture_target.capture_id |
| MacCaptureRecord | `dataType` | capture.capture_event.data_type |
| MacCaptureRecord | `targetId` | capture.target_lookup.target_id |
| MacCaptureRecord | `targetType` | capture.capture_event.target_type |
| MacCaptureRecord | `deviceId` | capture.capture_event.device_id |
| MacCaptureRecord | `siteId` | capture.capture_event.site_id |
| MacCaptureRecord | `captureTime` | capture.capture_event.capture_time |
| MacCaptureRecord | `ingestedAt` | capture.capture_event.ingested_at |
| MacCaptureRecord | `longitude` | ST_X(capture.capture_event.location) |
| MacCaptureRecord | `latitude` | ST_Y(capture.capture_event.location) |
| MacCaptureRecord | `source` | analysis.profile_link.source |
| MacCaptureRecord | `status` | capture.capture_event.status |
| MacCaptureRecord | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| MacCaptureRecord | `isMasked` | 授权投影计算；常规采集接口为脱敏，解密仅受控文件 |
| MacCaptureRecord | `mac` | capture.mac_record |
| PlateCaptureRecord | `captureId` | capture.capture_target.capture_id |
| PlateCaptureRecord | `dataType` | capture.capture_event.data_type |
| PlateCaptureRecord | `targetId` | capture.target_lookup.target_id |
| PlateCaptureRecord | `targetType` | capture.capture_event.target_type |
| PlateCaptureRecord | `deviceId` | capture.capture_event.device_id |
| PlateCaptureRecord | `siteId` | capture.capture_event.site_id |
| PlateCaptureRecord | `captureTime` | capture.capture_event.capture_time |
| PlateCaptureRecord | `ingestedAt` | capture.capture_event.ingested_at |
| PlateCaptureRecord | `longitude` | ST_X(capture.capture_event.location) |
| PlateCaptureRecord | `latitude` | ST_Y(capture.capture_event.location) |
| PlateCaptureRecord | `source` | analysis.profile_link.source |
| PlateCaptureRecord | `status` | capture.capture_event.status |
| PlateCaptureRecord | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| PlateCaptureRecord | `isMasked` | 授权投影计算；常规采集接口为脱敏，解密仅受控文件 |
| PlateCaptureRecord | `plate` | capture.plate_record |
| FaceCaptureRecord | `captureId` | capture.capture_target.capture_id |
| FaceCaptureRecord | `dataType` | capture.capture_event.data_type |
| FaceCaptureRecord | `targetId` | capture.target_lookup.target_id |
| FaceCaptureRecord | `targetType` | capture.capture_event.target_type |
| FaceCaptureRecord | `deviceId` | capture.capture_event.device_id |
| FaceCaptureRecord | `siteId` | capture.capture_event.site_id |
| FaceCaptureRecord | `captureTime` | capture.capture_event.capture_time |
| FaceCaptureRecord | `ingestedAt` | capture.capture_event.ingested_at |
| FaceCaptureRecord | `longitude` | ST_X(capture.capture_event.location) |
| FaceCaptureRecord | `latitude` | ST_Y(capture.capture_event.location) |
| FaceCaptureRecord | `source` | analysis.profile_link.source |
| FaceCaptureRecord | `status` | capture.capture_event.status |
| FaceCaptureRecord | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| FaceCaptureRecord | `isMasked` | 授权投影计算；常规采集接口为脱敏，解密仅受控文件 |
| FaceCaptureRecord | `face` | capture.face_record |
| CaptureDetail | `record` | capture_event和对应唯一payload，列表字段与详情同源 |
| CaptureDetail | `relatedProfileIds` | analysis.profile_link按目标与有效期去重且过滤当前权限 |
| CaptureDetail | `canDownloadImage` | 文件ACTIVE、当前用户范围和图像权限联合判定 |
| CaptureDetail | `availableActions` | 共用PermissionCode与当前资源/状态计算 |
| CaptureStatistics | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| CaptureStatistics | `captureCount` | 按当前有权目标查询COUNT(DISTINCT capture_event.id)，含次目标capture_target，避免一事件多次计数 |
| CaptureStatistics | `siteCount` | 有权采集事实COUNT(DISTINCT site_id)或已发布结果site_count |
| CaptureStatistics | `regionCount` | 采集时归属COUNT(DISTINCT region_code)或已发布结果region_count |
| CaptureStatistics | `firstSeenAt` | analysis.profile_link.first_seen_at |
| CaptureStatistics | `mostFrequentSiteId` | 授权窗口capture_event按site分组最大COUNT；并列规则P-006，不擅定 |
| CaptureStatistics | `asOf` | metric_snapshot.as_of、结果generated_at或当前受限查询一致时刻；不是固定所有接口now() |
| ContextTarget | `targetId` | capture.target_lookup.target_id |
| ContextTarget | `targetType` | capture.capture_event.target_type |
| TrajectoryPoint | `captureId` | capture.capture_target.capture_id |
| TrajectoryPoint | `captureTime` | capture.capture_event.capture_time |
| TrajectoryPoint | `location` | capture.capture_event.location |
| TrajectoryPoint | `siteId` | capture.capture_event.site_id |
| TrajectoryPoint | `deviceId` | capture.capture_event.device_id |
| TrajectoryPoint | `dataType` | capture.capture_event.data_type |
| TrajectoryPoint | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| FenceRecord | `imsiMasked` | capture.fence_record.imsi_target_id -> capture.target.identifier_cipher 由授权脱敏服务投影 |
| FenceRecord | `imeiMasked` | capture.fence_record.imei_target_id -> capture.target.identifier_cipher 由授权脱敏服务投影 |
| FenceRecord | `carrier` | 相关carrier_code目录投影 |
| FenceRecord | `radioType` | capture.fence_record.radio_type |
| MacRecord | `macMasked` | capture.mac_record.mac_target_id -> capture.target.identifier_cipher 由授权脱敏服务投影 |
| MacRecord | `wirelessType` | capture.mac_record.wireless_type |
| PlateRecord | `plateMasked` | capture.target.identifier_cipher 由授权脱敏服务投影（车牌目标） |
| PlateRecord | `plateColor` | capture.plate_record.plate_color |
| PlateRecord | `vehicleImageAssetId` | capture.plate_record.vehicle_image_asset_id |
| FaceRecord | `imageAssetId` | capture.face_record.image_asset_id |
| FaceRecord | `modelVersion` | capture.face_record.model_version |
| FaceRecord | `qualityScore` | capture.face_record.quality_score |
| IngestionBatch | `batchId` | capture.ingestion_item.batch_id |
| IngestionBatch | `source` | 所属领域 source/source_code 字段；采集通过source_event关联ingestion_source.source_code |
| IngestionBatch | `sourceFileId` | capture.ingestion_batch.source_file_id |
| IngestionBatch | `mappingVersion` | capture.ingestion_batch.mapping_version |
| IngestionBatch | `dedupVersion` | capture.ingestion_batch.dedup_rule_version |
| IngestionBatch | `status` | capture.ingestion_batch.status |
| IngestionBatch | `acceptedCount` | capture.ingestion_batch.accepted_count |
| IngestionBatch | `rejectedCount` | capture.ingestion_batch.rejected_count |
| IngestionBatch | `duplicateCount` | capture.ingestion_batch.duplicate_count |
| IngestionBatch | `createdAt` | capture.ingestion_batch.created_at |
| IngestionBatch | `finishedAt` | capture.ingestion_batch.finished_at |
| IngestionBatch | `errorSummary` | capture.ingestion_batch.error_summary |
| IngestionBatch | `taskId` | capture.ingestion_batch.task_id |
| IngestionQualitySummary | `asOf` | metric_snapshot.as_of、结果generated_at或当前受限查询一致时刻；不是固定所有接口now() |
| IngestionQualitySummary | `source` | 所属领域 source/source_code 字段；采集通过source_event关联ingestion_source.source_code |
| IngestionQualitySummary | `acceptedCount` | capture.ingestion_batch.accepted_count |
| IngestionQualitySummary | `rejectedCount` | capture.ingestion_batch.rejected_count |
| IngestionQualitySummary | `duplicateCount` | capture.ingestion_batch.duplicate_count |
| IngestionQualitySummary | `unmappedDeviceCount` | capture.quality_issue对应设备映射错误码计数 |
| IngestionQualitySummary | `invalidCoordinateCount` | capture.quality_issue对应坐标错误码计数 |
| AnalysisContext | `contextId` | analysis.context_target.context_id |
| AnalysisContext | `ownerUserId` | analysis.analysis_context.owner_user_id |
| AnalysisContext | `sourceModule` | analysis.analysis_context.source_module |
| AnalysisContext | `targets` | 关联target或notification_plan_recipient/watch_notification_recipient，按Schema选定有限类型 |
| AnalysisContext | `filter` | 按固定子Schema组装；源表 analysis.analysis_context,analysis.context_target |
| AnalysisContext | `spatialRange` | 按固定子Schema组装；源表 analysis.analysis_context,analysis.context_target |
| AnalysisContext | `createdAt` | analysis.analysis_context.created_at |
| AnalysisContext | `expiresAt` | analysis.analysis_context.expires_at |
| AnalysisContext | `permissionVersion` | analysis.analysis_context.permission_version |
| AnalysisContext | `targetCount` | COUNT(analysis.context_target)或冻结清单数量 |
| AnalysisContext | `targetsTruncated` | 上下文实际目标数大于返回上限时true；不等同清单丢失 |
| Task | `taskId` | analysis.task_attempt.task_id |
| Task | `taskType` | analysis.async_task.task_type |
| Task | `status` | analysis.async_task.status |
| Task | `progress` | analysis.async_task.progress |
| Task | `ownerUserId` | analysis.async_task.owner_user_id |
| Task | `createdAt` | analysis.async_task.created_at |
| Task | `startedAt` | analysis.async_task.started_at |
| Task | `finishedAt` | analysis.async_task.finished_at |
| Task | `cancelRequestedAt` | analysis.async_task.cancel_requested_at |
| Task | `retryCount` | analysis.async_task.retry_count |
| Task | `algorithmVersion` | analysis.async_task.algorithm_version |
| Task | `parameterSnapshot` | analysis.async_task.parameter_snapshot |
| Task | `resultReference` | 领域表结果/受控file_asset引用，由task_type选择有限响应类型 |
| Task | `errorCode` | analysis.async_task.error_code |
| Task | `errorMessage` | analysis.async_task.error_message |
| Task | `traceId` | analysis.async_task.trace_id |
| Task | `name` | analysis.async_task.name |
| Task | `resourceType` | analysis.async_task.resource_type |
| Task | `resourceId` | analysis.async_task.resource_id |
| TaskAttempt | `attemptNo` | analysis.task_attempt.attempt_no |
| TaskAttempt | `startedAt` | analysis.async_task.started_at |
| TaskAttempt | `finishedAt` | analysis.async_task.finished_at |
| TaskAttempt | `result` | analysis.task_attempt.result |
| TaskAttempt | `errorCode` | analysis.async_task.error_code |
| TaskParameterSnapshot | `schemaVersion` | async_task.parameter_schema_version |
| TaskParameterSnapshot | `contextId` | analysis.async_task.context_id |
| TaskParameterSnapshot | `submittedAt` | analysis.async_task.submitted_at |
| TaskParameterSnapshot | `parameterHash` | analysis.async_task.parameter_hash |
| TaskRowError | `rowNumber` | analysis.task_row_error.row_number |
| TaskRowError | `sourceRecordKey` | analysis.task_row_error.source_record_key |
| TaskRowError | `field` | field_name/field_code（按错误或冲突实体） |
| TaskRowError | `code` | 目录主键code/permission.code/variable_code或error_code（按Schema） |
| TaskRowError | `reason` | analysis.task_row_error.reason |
| ImportTaskResult | `taskId` | analysis.task_attempt.task_id |
| ImportTaskResult | `taskType` | analysis.async_task.task_type |
| ImportTaskResult | `importedCount` | analysis.import_job.imported_count |
| ImportTaskResult | `skippedCount` | analysis.import_job.skipped_count |
| ImportTaskResult | `items` | 所属子表按父键、指定版本/发布尝试分页投影 |
| ImportTaskResult | `nextCursor` | 按稳定排序键签名的游标，绑定查询、授权与快照；不作业务列 |
| ImportTaskResult | `pageSize` | 请求校验后的大小；不存业务表 |
| ImportResultItem | `rowNumber` | analysis.task_row_error.row_number |
| ImportResultItem | `status` | analysis.async_task.status |
| ImportResultItem | `libraryId` | analysis.import_job.library_id |
| ImportResultItem | `memberId` | analysis.import_row_result.member_id |
| ImportResultItem | `profileId` | analysis.import_row_result.profile_id |
| ImportResultItem | `deviceId` | analysis.import_row_result.device_id |
| ImportResultItem | `datasetId` | analysis.import_job.dataset_id |
| ImportResultItem | `itemId` | analysis.import_row_result.item_id |
| CompanionAnalysisTaskDetail | `task` | analysis.async_task +参数快照/结果引用 |
| CompanionAnalysisTaskDetail | `name` | analysis.analysis_job.name |
| CompanionAnalysisTaskDetail | `contextId` | analysis.analysis_job.context_id |
| CompanionAnalysisTaskDetail | `algorithmVersion` | analysis.analysis_job.algorithm_version |
| CompanionAnalysisTaskDetail | `resultCount` | analysis.analysis_job.result_count |
| CompanionAnalysisTaskDetail | `version` | analysis.analysis_job.version |
| CompanionAnalysisTaskDetail | `kind` | analysis.analysis_job.kind |
| CompanionAnalysisTaskDetail | `parameters` | 分析对应companion/collision/region_parameter +selected_target/site/region；设备typed parameter_value；模型typed model_parameter_value |
| CompanionAnalysisTaskDetail | `analysisTaskId` | analysis.analysis_job.id |
| CollisionAnalysisTaskDetail | `task` | analysis.async_task +参数快照/结果引用 |
| CollisionAnalysisTaskDetail | `name` | analysis.analysis_job.name |
| CollisionAnalysisTaskDetail | `contextId` | analysis.analysis_job.context_id |
| CollisionAnalysisTaskDetail | `algorithmVersion` | analysis.analysis_job.algorithm_version |
| CollisionAnalysisTaskDetail | `resultCount` | analysis.analysis_job.result_count |
| CollisionAnalysisTaskDetail | `version` | analysis.analysis_job.version |
| CollisionAnalysisTaskDetail | `kind` | analysis.analysis_job.kind |
| CollisionAnalysisTaskDetail | `parameters` | 分析对应companion/collision/region_parameter +selected_target/site/region；设备typed parameter_value；模型typed model_parameter_value |
| CollisionAnalysisTaskDetail | `analysisTaskId` | analysis.analysis_job.id |
| RegionAnalysisTaskDetail | `task` | analysis.async_task +参数快照/结果引用 |
| RegionAnalysisTaskDetail | `name` | analysis.analysis_job.name |
| RegionAnalysisTaskDetail | `contextId` | analysis.analysis_job.context_id |
| RegionAnalysisTaskDetail | `algorithmVersion` | analysis.analysis_job.algorithm_version |
| RegionAnalysisTaskDetail | `resultCount` | analysis.analysis_job.result_count |
| RegionAnalysisTaskDetail | `version` | analysis.analysis_job.version |
| RegionAnalysisTaskDetail | `kind` | analysis.analysis_job.kind |
| RegionAnalysisTaskDetail | `parameters` | 分析对应companion/collision/region_parameter +selected_target/site/region；设备typed parameter_value；模型typed model_parameter_value |
| RegionAnalysisTaskDetail | `analysisTaskId` | analysis.analysis_job.id |
| CompanionResultItem | `resultId` | analysis.companion_result.result_id |
| CompanionResultItem | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| CompanionResultItem | `evidenceCaptureIds` | analysis.result_evidence.capture_id；collision事件使用collision_event_evidence.capture_id |
| CompanionResultItem | `algorithmVersion` | analysis.analysis_result.algorithm_version |
| CompanionResultItem | `kind` | analysis.analysis_result.kind |
| CompanionResultItem | `commonOccurrenceCount` | analysis.companion_result.common_occurrence_count |
| CompanionResultItem | `sharedSiteCount` | analysis.companion_result.shared_site_count |
| CompanionResultItem | `trajectoryOverlapCount` | analysis.companion_result.trajectory_overlap_count |
| CompanionResultItem | `associationScore` | analysis.companion_result.association_score |
| CompanionResultItem | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| CompanionResultItem | `dataType` | analysis.analysis_result.data_type |
| CompanionResultItem | `captureCount` | analysis.analysis_result.capture_count |
| CompanionResultItem | `siteCount` | analysis.analysis_result.site_count |
| CompanionResultItem | `regionCount` | analysis.analysis_result.region_count |
| CompanionResultItem | `visitedRegionCodes` | analysis.result_region或档案有权capture_event.region_code_at_capture去重 |
| CompanionResultItem | `firstSeenAt` | analysis.analysis_result.first_seen_at |
| CollisionResultItem | `resultId` | analysis.collision_result.result_id |
| CollisionResultItem | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| CollisionResultItem | `evidenceCaptureIds` | analysis.result_evidence.capture_id；collision事件使用collision_event_evidence.capture_id |
| CollisionResultItem | `algorithmVersion` | analysis.analysis_result.algorithm_version |
| CollisionResultItem | `kind` | analysis.analysis_result.kind |
| CollisionResultItem | `collisionCount` | analysis.collision_result.collision_count |
| CollisionResultItem | `events` | analysis.collision_event按result_id + collision_event_evidence |
| CollisionResultItem | `matchRate` | analysis.collision_result.match_rate |
| CollisionResultItem | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| CollisionResultItem | `dataType` | analysis.analysis_result.data_type |
| CollisionResultItem | `captureCount` | analysis.analysis_result.capture_count |
| CollisionResultItem | `siteCount` | analysis.analysis_result.site_count |
| CollisionResultItem | `regionCount` | analysis.analysis_result.region_count |
| CollisionResultItem | `visitedRegionCodes` | analysis.result_region或档案有权capture_event.region_code_at_capture去重 |
| CollisionResultItem | `firstSeenAt` | analysis.analysis_result.first_seen_at |
| CollisionEvent | `occurredAt` | analysis.collision_event.occurred_at |
| CollisionEvent | `location` | analysis.collision_event.location |
| CollisionEvent | `evidenceCaptureIds` | analysis.result_evidence.capture_id；collision事件使用collision_event_evidence.capture_id |
| RegionResultItem | `resultId` | analysis.region_result.result_id |
| RegionResultItem | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| RegionResultItem | `evidenceCaptureIds` | analysis.result_evidence.capture_id；collision事件使用collision_event_evidence.capture_id |
| RegionResultItem | `algorithmVersion` | analysis.analysis_result.algorithm_version |
| RegionResultItem | `kind` | analysis.analysis_result.kind |
| RegionResultItem | `captureCount` | analysis.analysis_result.capture_count |
| RegionResultItem | `siteCount` | analysis.analysis_result.site_count |
| RegionResultItem | `visitedRegionCount` | analysis.region_result.visited_selected_region_count |
| RegionResultItem | `totalRegionCount` | analysis.region_result.total_selected_region_count |
| RegionResultItem | `coverageRate` | analysis.region_result.coverage_rate |
| RegionResultItem | `firstSeenAt` | analysis.analysis_result.first_seen_at |
| RegionResultItem | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| RegionResultItem | `dataType` | analysis.analysis_result.data_type |
| RegionResultItem | `regionCount` | analysis.analysis_result.region_count |
| RegionResultItem | `visitedRegionCodes` | analysis.result_region或档案有权capture_event.region_code_at_capture去重 |
| DatasetDetail | `datasetId` | analysis.dataset_version.dataset_id |
| DatasetDetail | `name` | analysis.dataset.name |
| DatasetDetail | `datasetKind` | analysis.dataset.dataset_kind |
| DatasetDetail | `status` | analysis.dataset.status |
| DatasetDetail | `version` | analysis.dataset.current_version=version |
| DatasetDetail | `ownerUserId` | analysis.dataset.owner_user_id |
| DatasetDetail | `sourceContextId` | analysis.dataset.source_context_id |
| DatasetDetail | `itemCount` | analysis.dataset_version.item_count |
| DatasetDetail | `createdAt` | analysis.dataset.created_at |
| DatasetDetail | `updatedAt` | analysis.dataset.updated_at |
| DatasetDetail | `elementType` | analysis.dataset.element_type |
| DatasetVersion | `version` | analysis.dataset_version.version_no |
| DatasetVersion | `itemCount` | analysis.dataset_version.item_count |
| DatasetVersion | `operation` | analysis.dataset_version.operation |
| DatasetVersion | `createdAt` | analysis.dataset.created_at |
| DatasetVersion | `createdBy` | analysis.dataset.created_by |
| CaptureDatasetItem | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| CaptureDatasetItem | `sourceCaptureId` | analysis.dataset_item.source_capture_id |
| CaptureDatasetItem | `addedAt` | analysis.dataset_item.added_at |
| CaptureDatasetItem | `itemId` | analysis.dataset_item.id |
| CaptureDatasetItem | `elementType` | analysis.dataset.element_type |
| CaptureDatasetItem | `capture` | capture_event强类型详情 |
| CaptureDatasetItem | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| TargetDatasetItem | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| TargetDatasetItem | `sourceCaptureId` | analysis.dataset_item.source_capture_id |
| TargetDatasetItem | `addedAt` | analysis.dataset_item.added_at |
| TargetDatasetItem | `itemId` | analysis.dataset_item.id |
| TargetDatasetItem | `elementType` | analysis.dataset.element_type |
| TargetDatasetItem | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| SetTaskDetail | `task` | analysis.async_task +参数快照/结果引用 |
| SetTaskDetail | `input` | set_job +set_operand固定版本 + expression |
| SetTaskDetail | `resultDatasetId` | analysis.set_job.result_dataset_id |
| SetTaskDetail | `resultCount` | analysis.set_job.result_count |
| SetTaskDetail | `dedupRuleVersion` | analysis.set_job.dedup_rule_version |
| SetTaskDetail | `setTaskId` | analysis.set_job.id |
| WatchRuleDetail | `ruleId` | operation.watch_rule_revision.rule_id |
| WatchRuleDetail | `name` | operation.watch_rule.name |
| WatchRuleDetail | `ownerUserId` | operation.watch_rule.owner_user_id |
| WatchRuleDetail | `version` | operation.watch_rule.current_rule_version |
| WatchRuleDetail | `status` | operation.watch_rule.status |
| WatchRuleDetail | `effectiveFrom` | operation.watch_rule.effective_from |
| WatchRuleDetail | `effectiveUntil` | operation.watch_rule.effective_until |
| WatchRuleDetail | `contextId` | operation.watch_rule.context_id |
| WatchRuleDetail | `conditions` | watch_rule_revision.condition_snapshot +watch_rule_target/site |
| WatchRuleDetail | `createdAt` | operation.watch_rule.created_at |
| WatchRuleDetail | `updatedAt` | operation.watch_rule.updated_at |
| WatchRuleDetail | `hitCount` | operation.watch_rule.hit_count |
| WatchRuleDetail | `ownerLoginName` | auth.app_user.login_name通过owner_user_id |
| WatchRuleDetail | `notification` | watch_rule_revision.notification_snapshot +watch_notification_recipient +模板引用 |
| WatchRuleSnapshot | `ruleId` | operation.watch_rule_revision.rule_id |
| WatchRuleSnapshot | `version` | operation.watch_rule_revision.rule_version |
| WatchRuleSnapshot | `name` | operation.watch_rule_revision.name_snapshot |
| WatchRuleSnapshot | `conditions` | watch_rule_revision.condition_snapshot +watch_rule_target/site |
| WatchRuleSnapshot | `effectiveFrom` | operation.watch_rule.effective_from |
| WatchRuleSnapshot | `effectiveUntil` | operation.watch_rule.effective_until |
| WatchRuleSnapshot | `notification` | watch_rule_revision.notification_snapshot +watch_notification_recipient +模板引用 |
| WatchStatistics | `ruleId` | operation.watch_rule_revision.rule_id |
| WatchStatistics | `ruleVersion` | operation.watch_statistics_run.final_rule_version |
| WatchStatistics | `final` | operation.watch_statistics_run.final（规则结束才发布） |
| WatchStatistics | `hitCount` | operation.watch_rule.hit_count |
| WatchStatistics | `distinctTargetCount` | operation.watch_statistics_run.distinct_target_count |
| WatchStatistics | `siteCount` | operation.watch_statistics_run.site_count |
| WatchStatistics | `regionCount` | operation.watch_statistics_run.region_count |
| WatchStatistics | `firstSeenAt` | operation.watch_statistics_run.first_seen_at |
| WatchStatistics | `asOf` | operation.watch_statistics_run.as_of |
| WatchStatistics | `items` | 所属子表按父键、指定版本/发布尝试分页投影 |
| WatchStatistics | `nextCursor` | 按稳定排序键签名的游标，绑定查询、授权与快照；不作业务列 |
| WatchStatistics | `pageSize` | 请求校验后的大小；不存业务表 |
| WatchTargetStatistics | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| WatchTargetStatistics | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| WatchTargetStatistics | `captureCount` | operation.watch_statistics.capture_count |
| WatchTargetStatistics | `siteCount` | operation.watch_statistics_run.site_count |
| WatchTargetStatistics | `regionCount` | operation.watch_statistics_run.region_count |
| WatchTargetStatistics | `firstSeenAt` | operation.watch_statistics_run.first_seen_at |
| WatchTargetStatistics | `mostFrequentSiteId` | operation.watch_statistics.most_frequent_site_id |
| WatchTargetStatistics | `mostFrequentSiteName` | operation.watch_statistics.most_frequent_site_name_snapshot |
| WatchNotificationSnapshot | `targets` | 关联target或notification_plan_recipient/watch_notification_recipient，按Schema选定有限类型 |
| WatchNotificationSnapshot | `templateCode` | notification_plan_revision.template_code或通知模板template_code |
| WatchNotificationSnapshot | `platformUserIds` | notification_recipient.channel_type=PLATFORM的recipient_user_id经固定版本关联 |
| Alert | `alertId` | operation.delivery.alert_id |
| Alert | `ruleId` | operation.alert_event.rule_id |
| Alert | `ruleVersion` | operation.alert_event.rule_version |
| Alert | `captureId` | operation.alert_event.capture_id |
| Alert | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| Alert | `siteId` | operation.alert_event.site_id |
| Alert | `deviceId` | operation.alert_event.device_id |
| Alert | `occurredAt` | operation.alert_event.occurred_at |
| Alert | `severity` | operation.alert_event.severity |
| Alert | `status` | operation.alert_event.status |
| Alert | `deliveryStatus` | 按关联投递集合聚合；部分失败/UNKNOWN显示规则P-015未定 |
| Alert | `traceId` | operation.alert_event.trace_id |
| AlertDetail | `alert` | operation.alert_event |
| AlertDetail | `capture` | capture_event强类型详情 |
| AlertDetail | `watchRuleSnapshot` | alert_event.rule_id/rule_version精确关联watch_rule_revision |
| AlertDetail | `deliveries` | operation.delivery按alert_id且按授权查询 |
| NotificationTaskDetail | `notificationTaskId` | operation.notification_plan.id |
| NotificationTaskDetail | `name` | operation.notification_plan.name |
| NotificationTaskDetail | `status` | operation.notification_plan.status |
| NotificationTaskDetail | `templateCode` | notification_plan_revision当前版本template_code |
| NotificationTaskDetail | `targets` | 关联target或notification_plan_recipient/watch_notification_recipient，按Schema选定有限类型 |
| NotificationTaskDetail | `createdAt` | operation.notification_plan.created_at |
| NotificationTaskDetail | `cancelledAt` | operation.notification_plan.cancelled_at |
| NotificationTaskDetail | `deliveryCount` | COUNT(operation.delivery WHERE plan_id=通知计划) |
| NotificationTaskDetail | `schedule` | notification_plan结构化ONCE/DAILY字段 +plan_revision.schedule_snapshot |
| NotificationTaskDetail | `platformUserIds` | notification_recipient.channel_type=PLATFORM的recipient_user_id经固定版本关联 |
| NotificationTaskDetail | `version` | operation.notification_plan.current_plan_version |
| NotificationTaskDetail | `lastSentAt` | operation.notification_plan.last_sent_at |
| NotificationTaskDetail | `nextRunAt` | operation.notification_plan.next_run_at |
| NotificationTaskDetail | `lastExecutionTaskId` | operation.notification_plan.last_execution_task_id |
| NotificationRecipientSummary | `channel` | operation.delivery/notification_recipient.channel_type |
| NotificationRecipientSummary | `recipientMasked` | operation.notification_recipient.recipient_cipher 授权脱敏；PLATFORM投影用户显示名 |
| NotificationRecipientSummary | `userId` | auth.app_user.id |
| NotificationRecipientSummary | `recipientId` | operation.notification_plan_recipient.recipient_id |
| NotificationTemplate | `templateCode` | operation.notification_plan_revision.template_code |
| NotificationTemplate | `name` | operation.notification_plan.name |
| NotificationTemplate | `purpose` | operation.notification_template.purpose |
| NotificationTemplate | `channels` | operation.notification_template.channels |
| NotificationTemplate | `variables` | notification_template_variable.variable_code/description/required |
| NotificationTemplate | `version` | operation.notification_template.template_version |
| NotificationTemplate | `enabled` | operation.notification_template.enabled |
| NotificationTemplateVariablesItem | `code` | 目录主键code/permission.code/variable_code或error_code（按Schema） |
| NotificationTemplateVariablesItem | `description` | operation.notification_template_variable.description |
| NotificationTemplateVariablesItem | `required` | operation.notification_template_variable.required |
| Delivery | `deliveryId` | operation.platform_message.delivery_id |
| Delivery | `notificationTaskId` | operation.notification_plan.id |
| Delivery | `alertId` | operation.delivery.alert_id |
| Delivery | `channel` | operation.delivery/notification_recipient.channel_type |
| Delivery | `recipientMasked` | operation.notification_recipient.recipient_cipher 授权脱敏；PLATFORM投影用户显示名 |
| Delivery | `status` | operation.notification_plan.status |
| Delivery | `retryCount` | operation.delivery.retry_count |
| Delivery | `sentAt` | operation.delivery.sent_at |
| Delivery | `acknowledgedAt` | operation.delivery.acknowledged_at |
| Delivery | `errorCode` | operation.delivery.error_code |
| Delivery | `errorReason` | operation.delivery.error_reason |
| Delivery | `providerReceiptRef` | operation.delivery.provider_receipt_ref |
| Delivery | `createdAt` | operation.notification_plan.created_at |
| Delivery | `traceId` | operation.delivery.trace_id |
| Delivery | `taskName` | operation.delivery.task_name_snapshot |
| Delivery | `contentSummary` | operation.delivery.content_summary |
| Delivery | `executionTaskId` | operation.delivery.execution_task_id |
| Delivery | `ruleId` | operation.delivery.rule_id |
| PlatformMessage | `messageId` | operation.platform_message.id |
| PlatformMessage | `notificationTaskId` | operation.notification_plan.id |
| PlatformMessage | `alertId` | operation.delivery.alert_id |
| PlatformMessage | `recipientUserId` | operation.notification_recipient.recipient_user_id |
| PlatformMessage | `title` | operation.platform_message.title |
| PlatformMessage | `content` | operation.platform_message.content |
| PlatformMessage | `createdAt` | operation.notification_plan.created_at |
| PersonLibrarySummary | `libraryId` | analysis.person_library_member.library_id |
| PersonLibrarySummary | `name` | analysis.person_library.name |
| PersonLibrarySummary | `category` | core.device.category_code 或 analysis.person_library.category_code（按实体） |
| PersonLibrarySummary | `description` | analysis.person_library.description |
| PersonLibrarySummary | `memberCount` | 人员库COUNT未删除member或设备组COUNT member |
| PersonLibrarySummary | `ownerUserId` | analysis.person_library.owner_user_id |
| PersonLibrarySummary | `createdAt` | analysis.person_library.created_at |
| PersonLibrarySummary | `updatedAt` | analysis.person_library.updated_at |
| PersonLibrarySummary | `version` | analysis.person_library.version |
| PersonLibraryDetail | `libraryId` | analysis.person_library_member.library_id |
| PersonLibraryDetail | `name` | analysis.person_library.name |
| PersonLibraryDetail | `category` | core.device.category_code 或 analysis.person_library.category_code（按实体） |
| PersonLibraryDetail | `description` | analysis.person_library.description |
| PersonLibraryDetail | `memberCount` | 人员库COUNT未删除member或设备组COUNT member |
| PersonLibraryDetail | `ownerUserId` | analysis.person_library.owner_user_id |
| PersonLibraryDetail | `siteScope` | 用户auth.user_scope_site/角色auth.role_site/人员库analysis.library_scope_site +对应all_sites标志；P-002组合规则待签认 |
| PersonLibraryDetail | `dataScope` | 用户/角色/库data_type条目与data_all_sites/own_only/scope_version；P-002 |
| PersonLibraryDetail | `linkedWatchRuleIds` | operation.watch_rule_target.source_library_id关联有效规则 |
| PersonLibraryDetail | `createdAt` | analysis.person_library.created_at |
| PersonLibraryDetail | `updatedAt` | analysis.person_library.updated_at |
| PersonLibraryDetail | `version` | analysis.person_library.version |
| PersonLibraryMember | `memberId` | analysis.person_library_member.id |
| PersonLibraryMember | `libraryId` | analysis.person_library_member.library_id |
| PersonLibraryMember | `profileId` | analysis.person_library_member.profile_id |
| PersonLibraryMember | `displayName` | 库成员来自profile.display_label；用户来自app_user.display_name |
| PersonLibraryMember | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| PersonLibraryMember | `source` | analysis.person_library_member.source |
| PersonLibraryMember | `addedAt` | analysis.person_library_member.added_at |
| PersonLibraryMember | `addedByUserId` | analysis.person_library_member.added_by |
| PersonLibraryMember | `person` | analysis.person_profile强类型投影 |
| PersonLibraryMember | `version` | analysis.person_library_member.version |
| PersonProfileInfo | `nameMasked` | analysis.person_profile.name_cipher 授权脱敏 |
| PersonProfileInfo | `gender` | analysis.person_profile.gender_code |
| PersonProfileInfo | `ethnicity` | analysis.person_profile.ethnicity_code |
| PersonProfileInfo | `phoneMasked` | analysis.person_profile.phone_cipher 授权脱敏 |
| PersonProfileInfo | `identityNumberMasked` | analysis.person_profile.identity_number_cipher 授权脱敏 |
| PersonProfileInfo | `residenceRegionCode` | analysis.person_profile.residence_region_code |
| PersonProfileInfo | `suspectedSiteId` | analysis.person_profile.suspected_site_id |
| PersonProfileInfo | `portraitAssetId` | analysis.person_profile.portrait_asset_id |
| PersonProfileInfo | `appearanceDescription` | analysis.person_profile.appearance_description |
| PersonProfileInfo | `suspectedLandingText` | analysis.person_profile.suspected_landing_text |
| VehicleProfileInfo | `plateMasked` | capture.target.identifier_cipher 由授权脱敏服务投影（车牌目标） |
| VehicleProfileInfo | `plateColor` | analysis.vehicle_profile.plate_color |
| VehicleProfileInfo | `vehicleType` | analysis.vehicle_profile.vehicle_type_code |
| VehicleProfileInfo | `vehicleImageAssetId` | analysis.vehicle_profile.vehicle_image_asset_id |
| ProfileSummary | `profileId` | analysis.person_profile.profile_id |
| ProfileSummary | `profileType` | analysis.profile.profile_type |
| ProfileSummary | `displayLabel` | analysis.profile.display_label |
| ProfileSummary | `confidence` | analysis.profile.confidence |
| ProfileSummary | `evidenceCount` | COUNT(DISTINCT profile_evidence.id) |
| ProfileSummary | `lastSeenAt` | analysis.profile.last_seen_at |
| ProfileSummary | `updatedAt` | analysis.profile.updated_at |
| PersonProfileDetail | `profileId` | analysis.person_profile.profile_id |
| PersonProfileDetail | `profileType` | analysis.profile.profile_type |
| PersonProfileDetail | `displayLabel` | analysis.profile.display_label |
| PersonProfileDetail | `linkedTargets` | analysis.profile_link + profile_link_evidence（仅当前有效或指定历史） |
| PersonProfileDetail | `evidenceCount` | COUNT(DISTINCT profile_evidence.id) |
| PersonProfileDetail | `captureCount` | 单目标/档案/区域窗口COUNT(DISTINCT capture_event.id)，或已发布analysis_result.capture_count；指标源采集量用source_counter_sample，不能混用 |
| PersonProfileDetail | `siteCount` | 有权采集事实COUNT(DISTINCT site_id)或已发布结果site_count |
| PersonProfileDetail | `confidence` | analysis.profile.confidence |
| PersonProfileDetail | `firstSeenAt` | analysis.profile.first_seen_at |
| PersonProfileDetail | `lastSeenAt` | analysis.profile.last_seen_at |
| PersonProfileDetail | `createdAt` | analysis.profile.created_at |
| PersonProfileDetail | `updatedAt` | analysis.profile.updated_at |
| PersonProfileDetail | `version` | analysis.profile.version |
| PersonProfileDetail | `person` | analysis.person_profile强类型投影 |
| PersonProfileDetail | `regionCount` | 采集时归属COUNT(DISTINCT region_code)或已发布结果region_count |
| PersonProfileDetail | `visitedRegionCodes` | analysis.result_region或档案有权capture_event.region_code_at_capture去重 |
| VehicleProfileDetail | `profileId` | analysis.person_profile.profile_id |
| VehicleProfileDetail | `profileType` | analysis.profile.profile_type |
| VehicleProfileDetail | `displayLabel` | analysis.profile.display_label |
| VehicleProfileDetail | `linkedTargets` | analysis.profile_link + profile_link_evidence（仅当前有效或指定历史） |
| VehicleProfileDetail | `evidenceCount` | COUNT(DISTINCT profile_evidence.id) |
| VehicleProfileDetail | `captureCount` | 单目标/档案/区域窗口COUNT(DISTINCT capture_event.id)，或已发布analysis_result.capture_count；指标源采集量用source_counter_sample，不能混用 |
| VehicleProfileDetail | `siteCount` | 有权采集事实COUNT(DISTINCT site_id)或已发布结果site_count |
| VehicleProfileDetail | `confidence` | analysis.profile.confidence |
| VehicleProfileDetail | `firstSeenAt` | analysis.profile.first_seen_at |
| VehicleProfileDetail | `lastSeenAt` | analysis.profile.last_seen_at |
| VehicleProfileDetail | `createdAt` | analysis.profile.created_at |
| VehicleProfileDetail | `updatedAt` | analysis.profile.updated_at |
| VehicleProfileDetail | `version` | analysis.profile.version |
| VehicleProfileDetail | `vehicle` | analysis.vehicle_profile强类型投影 |
| VehicleProfileDetail | `regionCount` | 采集时归属COUNT(DISTINCT region_code)或已发布结果region_count |
| VehicleProfileDetail | `visitedRegionCodes` | analysis.result_region或档案有权capture_event.region_code_at_capture去重 |
| ProfileTargetLink | `linkId` | analysis.profile_link.id |
| ProfileTargetLink | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| ProfileTargetLink | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| ProfileTargetLink | `confidence` | analysis.profile.confidence |
| ProfileTargetLink | `evidenceIds` | profile_link_evidence/profile_revision_evidence/profile_conflict_evidence（按资源） |
| ProfileTargetLink | `source` | analysis.profile.source |
| ProfileTargetLink | `algorithmVersion` | analysis.profile_link.algorithm_version |
| ProfileTargetLink | `reviewStatus` | analysis.profile_link.review_status |
| ProfileTargetLink | `firstSeenAt` | analysis.profile.first_seen_at |
| ProfileTargetLink | `lastSeenAt` | analysis.profile.last_seen_at |
| ProfileEvidence | `evidenceId` | analysis.profile_evidence.id |
| ProfileEvidence | `profileId` | analysis.person_profile.profile_id |
| ProfileEvidence | `captureId` | analysis.profile_evidence.capture_id |
| ProfileEvidence | `source` | analysis.profile.source |
| ProfileEvidence | `sourceVersion` | analysis.profile_evidence.source_version |
| ProfileEvidence | `relation` | analysis.profile_evidence.relation |
| ProfileEvidence | `confidence` | analysis.profile.confidence |
| ProfileEvidence | `collectedAt` | analysis.profile_evidence.collected_at |
| ProfileEvidence | `reviewStatus` | analysis.profile_link.review_status |
| ProfileRevision | `revisionId` | analysis.profile_revision_member.revision_id |
| ProfileRevision | `profileId` | analysis.person_profile.profile_id |
| ProfileRevision | `action` | analysis.profile_revision.action |
| ProfileRevision | `actorUserId` | analysis.profile_revision.actor_user_id |
| ProfileRevision | `reason` | analysis.profile_revision.reason |
| ProfileRevision | `occurredAt` | analysis.profile_revision.occurred_at |
| ProfileRevision | `sourceProfileIds` | analysis.profile_revision_member.direction=SOURCE |
| ProfileRevision | `resultProfileIds` | analysis.profile_revision_member.direction=RESULT |
| ProfileRevision | `evidenceIds` | profile_link_evidence/profile_revision_evidence/profile_conflict_evidence（按资源） |
| ProfileConflict | `conflictId` | analysis.profile_conflict_evidence.conflict_id |
| ProfileConflict | `profileId` | analysis.person_profile.profile_id |
| ProfileConflict | `field` | field_name/field_code（按错误或冲突实体） |
| ProfileConflict | `evidenceIds` | profile_link_evidence/profile_revision_evidence/profile_conflict_evidence（按资源） |
| ProfileConflict | `status` | analysis.profile_conflict.status |
| ProfileConflict | `description` | analysis.profile_conflict.description |
| TrendModel | `modelId` | analysis.model_parameter_definition.model_id |
| TrendModel | `code` | analysis.trend_model.code |
| TrendModel | `name` | analysis.trend_model.name |
| TrendModel | `modelType` | analysis.trend_model.model_type |
| TrendModel | `description` | analysis.trend_model.description |
| TrendModel | `enabled` | analysis.trend_model.enabled |
| TrendModel | `tagCodes` | model_tag_binding或model_result_tag.tag_code（按资源） |
| TrendModel | `currentParameterVersionId` | analysis.trend_model.current_parameter_version_id |
| TrendModel | `effectiveDataFilter` | analysis.trend_model.effective_data_filter，按EffectiveDataFilter Schema验证 |
| TrendModel | `matchingRules` | analysis.model_matching_rule +model_rule_output_tag |
| TrendModel | `createdAt` | analysis.trend_model.created_at |
| TrendModel | `updatedAt` | analysis.trend_model.updated_at |
| TrendModel | `version` | analysis.trend_model.version |
| TrendModel | `parameterDefinitions` | analysis.model_parameter_definition |
| ModelParameterVersion | `parameterVersionId` | analysis.model_parameter_value.parameter_version_id |
| ModelParameterVersion | `modelId` | analysis.model_parameter_definition.model_id |
| ModelParameterVersion | `version` | analysis.model_parameter_version.version_no |
| ModelParameterVersion | `parameters` | model_parameter_value经definition.code/value_type投影 |
| ModelParameterVersion | `matchingRules` | analysis.model_matching_rule +model_rule_output_tag |
| ModelParameterVersion | `createdAt` | analysis.trend_model.created_at |
| ModelParameterVersion | `createdByUserId` | analysis.async_task.owner_user_id（报表创建者） |
| ModelParameterVersion | `effectiveAt` | analysis.model_parameter_version.effective_at |
| ModelTag | `tagCode` | analysis.model_tag.tag_code |
| ModelTag | `label` | analysis.model_parameter_definition.label |
| ModelTag | `description` | analysis.trend_model.description |
| ModelTag | `enabled` | analysis.trend_model.enabled |
| ModelResult | `resultId` | analysis.model_result.id |
| ModelResult | `taskId` | analysis.model_execution.task_id |
| ModelResult | `modelId` | analysis.model_parameter_definition.model_id |
| ModelResult | `parameterVersionId` | analysis.model_parameter_value.parameter_version_id |
| ModelResult | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| ModelResult | `tagCodes` | model_tag_binding或model_result_tag.tag_code（按资源） |
| ModelResult | `confidence` | analysis.model_result.confidence |
| ModelResult | `evidenceIds` | profile_link_evidence/profile_revision_evidence/profile_conflict_evidence（按资源） |
| ModelResult | `algorithmVersion` | analysis.model_parameter_version.algorithm_version |
| ModelResult | `generatedAt` | analysis.model_result.generated_at |
| ModelResult | `reviewStatus` | analysis.model_result.review_status |
| ModelResult | `displayTarget` | capture.target.identifier_cipher 由授权脱敏服务投影；授权投影不得默认解密 |
| ModelResult | `captureIds` | analysis.model_result_evidence.capture_id |
| ModelResult | `dataType` | analysis.model_result.data_type |
| SiteSummary | `siteId` | core.device.site_id |
| SiteSummary | `name` | core.site.name |
| SiteSummary | `regionCode` | core.site.region_code |
| SiteSummary | `deviceCount` | 指定范围未删除设备COUNT或冻结报表device_count |
| SiteSummary | `enabled` | core.site.enabled |
| SiteSummary | `updatedAt` | core.site.updated_at |
| SiteSummary | `version` | core.site.version |
| SiteDetail | `siteId` | core.device.site_id |
| SiteDetail | `name` | core.site.name |
| SiteDetail | `regionCode` | core.site.region_code |
| SiteDetail | `address` | core.site.address |
| SiteDetail | `location` | core.site.location |
| SiteDetail | `deviceCount` | 指定范围未删除设备COUNT或冻结报表device_count |
| SiteDetail | `enabled` | core.site.enabled |
| SiteDetail | `createdAt` | core.site.created_at |
| SiteDetail | `updatedAt` | core.site.updated_at |
| SiteDetail | `version` | core.site.version |
| DeviceSummary | `deviceId` | core.device_parameter_value.device_id |
| DeviceSummary | `serialNumber` | core.device.serial_number |
| DeviceSummary | `name` | core.device.name |
| DeviceSummary | `siteId` | core.device.site_id |
| DeviceSummary | `category` | core.device.category_code 或 analysis.person_library.category_code（按实体） |
| DeviceSummary | `status` | core.device.status |
| DeviceSummary | `lastSeenAt` | core.device.last_seen_at |
| DeviceSummary | `location` | core.device.location |
| DeviceSummary | `updatedAt` | core.device.updated_at |
| DeviceSummary | `version` | core.device.version |
| DeviceSummary | `siteName` | core.site.name（报表用report_device_row.site_name） |
| DeviceSummary | `regionCode` | core.site.region_code |
| DeviceSummary | `groupIds` | core.device_group_member.group_id聚合；报表用group_ids_snapshot |
| DeviceSummary | `resetCount` | core.device.reset_count |
| DeviceDetail | `deviceId` | core.device_parameter_value.device_id |
| DeviceDetail | `serialNumber` | core.device.serial_number |
| DeviceDetail | `name` | core.device.name |
| DeviceDetail | `siteId` | core.device.site_id |
| DeviceDetail | `category` | core.device.category_code 或 analysis.person_library.category_code（按实体） |
| DeviceDetail | `manufacturer` | core.device.manufacturer |
| DeviceDetail | `model` | core.device.model |
| DeviceDetail | `status` | core.device.status |
| DeviceDetail | `statusSince` | core.device.status_since |
| DeviceDetail | `lastSeenAt` | core.device.last_seen_at |
| DeviceDetail | `location` | core.device.location |
| DeviceDetail | `installedAt` | core.device.installed_at |
| DeviceDetail | `parameters` | 分析对应companion/collision/region_parameter +selected_target/site/region；设备typed parameter_value；模型typed model_parameter_value |
| DeviceDetail | `todayCaptureCount` | 指标snapshot指定device与今日范围；口径P-006 |
| DeviceDetail | `cumulativeCaptureCount` | 指标snapshot指定device与累计范围；口径P-006 |
| DeviceDetail | `createdAt` | core.device.created_at |
| DeviceDetail | `updatedAt` | core.device.updated_at |
| DeviceDetail | `version` | core.device.version |
| DeviceDetail | `siteName` | core.site.name（报表用report_device_row.site_name） |
| DeviceDetail | `regionCode` | core.site.region_code |
| DeviceDetail | `groupIds` | core.device_group_member.group_id聚合；报表用group_ids_snapshot |
| DeviceDetail | `resetCount` | core.device.reset_count |
| DeviceStatusData | `deviceId` | core.device_parameter_value.device_id |
| DeviceStatusData | `status` | core.device.status |
| DeviceStatusData | `since` | core.device.status_since |
| DeviceStatusData | `lastSeenAt` | core.device.last_seen_at |
| DeviceStatusData | `asOf` | core.metric_snapshot.as_of |
| DeviceStatusEvent | `eventId` | core.device_status_event.id |
| DeviceStatusEvent | `deviceId` | core.device_parameter_value.device_id |
| DeviceStatusEvent | `previousStatus` | core.device_status_event.previous_status |
| DeviceStatusEvent | `status` | core.device.status |
| DeviceStatusEvent | `occurredAt` | core.device_status_event.occurred_at |
| DeviceStatusEvent | `source` | core.device_status_event.source |
| DeviceStatusEvent | `reason` | core.device_status_event.reason |
| DeviceParameters | `deviceId` | core.device_parameter_value.device_id |
| DeviceParameters | `parameterVersion` | core.device.parameter_version |
| DeviceParameters | `items` | 所属子表按父键、指定版本/发布尝试分页投影 |
| DeviceParameters | `asOf` | 相关REPORTED参数最大observed_at；无回执不伪报生效 |
| DeviceCaptureStatistics | `deviceId` | core.device_parameter_value.device_id |
| DeviceCaptureStatistics | `timeRange` | 请求/冻结输入的time_from/time_to或bucket边界 |
| DeviceCaptureStatistics | `granularity` | core.metric_snapshot.granularity |
| DeviceCaptureStatistics | `points` | metric_snapshot按bucket_start或report_series_point的typed value |
| DeviceCaptureStatistics | `totalCaptureCount` | 源source_counter_sample按口径汇总；P-006未知不能以入库量代替 |
| DeviceCaptureStatistics | `totalIngestedCount` | capture_event已入库事实数 |
| DeviceCaptureStatistics | `asOf` | core.metric_snapshot.as_of |
| TextDeviceParameter | `parameterCode` | core.device_parameter_definition.parameter_code |
| TextDeviceParameter | `label` | core.device_parameter_definition.label |
| TextDeviceParameter | `valueType` | core.device_parameter_definition.value_type |
| TextDeviceParameter | `textValue` | core.device_parameter_value.text_value |
| TextDeviceParameter | `readOnly` | core.device_parameter_definition.read_only |
| TextDeviceParameter | `updatedAt` | core.device.updated_at |
| IntegerDeviceParameter | `parameterCode` | core.device_parameter_definition.parameter_code |
| IntegerDeviceParameter | `label` | core.device_parameter_definition.label |
| IntegerDeviceParameter | `valueType` | core.device_parameter_definition.value_type |
| IntegerDeviceParameter | `integerValue` | core.device_parameter_value.integer_value |
| IntegerDeviceParameter | `readOnly` | core.device_parameter_definition.read_only |
| IntegerDeviceParameter | `updatedAt` | core.device.updated_at |
| NumberDeviceParameter | `parameterCode` | core.device_parameter_definition.parameter_code |
| NumberDeviceParameter | `label` | core.device_parameter_definition.label |
| NumberDeviceParameter | `valueType` | core.device_parameter_definition.value_type |
| NumberDeviceParameter | `numberValue` | core.device_parameter_value.number_value |
| NumberDeviceParameter | `readOnly` | core.device_parameter_definition.read_only |
| NumberDeviceParameter | `updatedAt` | core.device.updated_at |
| BooleanDeviceParameter | `parameterCode` | core.device_parameter_definition.parameter_code |
| BooleanDeviceParameter | `label` | core.device_parameter_definition.label |
| BooleanDeviceParameter | `valueType` | core.device_parameter_definition.value_type |
| BooleanDeviceParameter | `booleanValue` | core.device_parameter_value.boolean_value |
| BooleanDeviceParameter | `readOnly` | core.device_parameter_definition.read_only |
| BooleanDeviceParameter | `updatedAt` | core.device.updated_at |
| DeviceGroupSummary | `groupId` | core.device_group_member.group_id |
| DeviceGroupSummary | `siteId` | core.device_group.site_id |
| DeviceGroupSummary | `name` | core.device_group.name |
| DeviceGroupSummary | `memberCount` | 人员库COUNT未删除member或设备组COUNT member |
| DeviceGroupSummary | `updatedAt` | core.device_group.updated_at |
| DeviceGroupSummary | `version` | core.device_group.version |
| DeviceGroupDetail | `groupId` | core.device_group_member.group_id |
| DeviceGroupDetail | `siteId` | core.device_group.site_id |
| DeviceGroupDetail | `name` | core.device_group.name |
| DeviceGroupDetail | `description` | core.device_group.description |
| DeviceGroupDetail | `memberCount` | 人员库COUNT未删除member或设备组COUNT member |
| DeviceGroupDetail | `createdAt` | core.device_group.created_at |
| DeviceGroupDetail | `updatedAt` | core.device_group.updated_at |
| DeviceGroupDetail | `version` | core.device_group.version |
| DeviceCommandTask | `taskId` | operation.device_command_job.task_id |
| DeviceCommandTask | `commandType` | operation.device_command_job.command_type |
| DeviceCommandTask | `requestSnapshot` | 按固定子Schema组装；源表 operation.device_command_job,operation.device_command,operation.device_command_attempt,analysis.async_task |
| DeviceCommandTask | `status` | operation.device_command.status |
| DeviceCommandTask | `progress` | analysis.async_task.progress |
| DeviceCommandTask | `requestedDeviceCount` | COUNT(operation.device_command WHERE job_task_id=taskId) |
| DeviceCommandTask | `successCount` | COUNT逐台device_command.status=SUCCESS |
| DeviceCommandTask | `failedCount` | COUNT逐台device_command.status=FAILED；UNKNOWN不计成功 |
| DeviceCommandTask | `createdAt` | operation.device_command_job.created_at |
| DeviceCommandTask | `finishedAt` | operation.device_command.finished_at |
| DeviceCommandTask | `requestedByUserId` | analysis.async_task.owner_user_id |
| DeviceCommandTask | `errorCode` | operation.device_command.error_code |
| DeviceCommandTask | `name` | operation.device_command_job.name |
| DeviceCommandTask | `version` | operation.device_command_job.version |
| DeviceCommandTask | `groupName` | operation.device_command_job.group_name_snapshot |
| DeviceCommandTask | `deviceNames` | operation.device_command.device_name_snapshot聚合 |
| ReportSummary | `reportId` | operation.report_definition.report_id |
| ReportSummary | `taskId` | operation.report_job.task_id |
| ReportSummary | `name` | operation.report_job.name |
| ReportSummary | `mode` | operation.report_job.mode |
| ReportSummary | `status` | operation.report_metric_row.status |
| ReportSummary | `createdAt` | operation.report_job.created_at |
| ReportSummary | `completedAt` | operation.report_job.completed_at |
| ReportSummary | `createdByUserId` | analysis.async_task.owner_user_id（报表创建者） |
| ReportDetail | `reportId` | operation.report_definition.report_id |
| ReportDetail | `taskId` | operation.report_job.task_id |
| ReportDetail | `name` | operation.report_job.name |
| ReportDetail | `mode` | operation.report_job.mode |
| ReportDetail | `definitions` | operation.report_definition +report_definition_site |
| ReportDetail | `status` | operation.report_metric_row.status |
| ReportDetail | `progress` | analysis.async_task.progress |
| ReportDetail | `outputFileAssetId` | operation.report_job.output_file_id |
| ReportDetail | `outputFormat` | operation.report_job.output_format |
| ReportDetail | `errorCode` | analysis.async_task.error_code |
| ReportDetail | `errorMessage` | analysis.async_task.error_message |
| ReportDetail | `createdAt` | operation.report_job.created_at |
| ReportDetail | `completedAt` | operation.report_job.completed_at |
| ReportDetail | `createdByUserId` | analysis.async_task.owner_user_id（报表创建者） |
| ReportDetail | `version` | operation.report_job.version |
| ReportDefinition | `reportType` | operation.report_definition.report_type |
| ReportDefinition | `granularity` | operation.report_definition.granularity |
| ReportDefinition | `periodStart` | operation.report_definition.period_start |
| ReportDefinition | `periodEnd` | operation.report_definition.period_end |
| ReportDefinition | `regionCode` | operation.report_definition.region_code |
| ReportDefinition | `siteIds` | 范围关联表site_id；禁止从全库维表自动扩大授权 |
| ReportMetricRow | `label` | operation.report_metric_row.label |
| ReportMetricRow | `value` | operation.report_metric_row.value |
| ReportMetricRow | `unit` | operation.report_metric_row.unit |
| ReportMetricRow | `asOf` | operation.report_definition.as_of |
| ReportMetricRow | `status` | operation.report_metric_row.status |
| RegionOnlineReportRow | `regionCode` | operation.report_definition.region_code |
| RegionOnlineReportRow | `regionName` | operation.report_region_row.region_name |
| RegionOnlineReportRow | `deviceCount` | operation.report_definition.device_count |
| RegionOnlineReportRow | `onlineCount` | operation.report_region_row.online_count |
| RegionOnlineReportRow | `onlineRate` | operation.report_region_row.online_rate |
| CaptureReportSeries | `operatorCode` | operation.report_series.carrier_code |
| CaptureReportSeries | `operatorLabel` | operation.report_series.carrier_label |
| CaptureReportSeries | `deviceId` | operation.report_series.device_id |
| CaptureReportSeries | `deviceName` | 设备name；历史序列用device_name_snapshot |
| CaptureReportSeries | `regionCode` | operation.report_definition.region_code |
| CaptureReportSeries | `points` | metric_snapshot按bucket_start或report_series_point的typed value |
| SystemComponentStatus | `componentCode` | operation.report_component_row.component_code |
| SystemComponentStatus | `label` | operation.report_metric_row.label |
| SystemComponentStatus | `status` | operation.report_metric_row.status |
| SystemComponentStatus | `checkedAt` | operation.report_component_row.checked_at |
| AuditEvent | `eventId` | audit.audit_event.id |
| AuditEvent | `actorUserId` | audit.audit_event.actor_user_id |
| AuditEvent | `actorLoginName` | audit.audit_event.actor_login_name_snapshot |
| AuditEvent | `action` | audit.audit_event.action |
| AuditEvent | `resourceType` | audit.audit_event.resource_type |
| AuditEvent | `resourceId` | audit.audit_event.resource_id |
| AuditEvent | `occurredAt` | audit.audit_event.occurred_at |
| AuditEvent | `result` | audit.audit_event.result |
| AuditEvent | `traceId` | audit.audit_event.trace_id |
| AuditEvent | `requestId` | audit.audit_event.request_id |
| AuditEvent | `businessTaskId` | audit.audit_event.business_task_id |
| AuditEvent | `beforeSummary` | audit.audit_event.before_summary |
| AuditEvent | `afterSummary` | audit.audit_event.after_summary |
| AuditEvent | `failureCode` | audit.audit_event.failure_code |
| AuditEvent | `actorDisplayName` | audit.audit_event.actor_display_name_snapshot |
| DecryptionRequestDetail | `requestId` | operation.decryption_result_grant.request_id |
| DecryptionRequestDetail | `contextId` | operation.decryption_request.context_id |
| DecryptionRequestDetail | `status` | operation.decryption_request.status |
| DecryptionRequestDetail | `createdAt` | operation.decryption_request.created_at |
| DecryptionRequestDetail | `expiresAt` | operation.decryption_request.expires_at |
| DecryptionRequestDetail | `resultAvailable` | decryption_result_grant未过期且当前权限允许且file_asset ACTIVE；P-008未确认时false |
| SmsModemStatus | `meta` | metric_snapshot与metric_definition的asOf/status/unit/refresh_policy/error_code |
| SmsModemStatus | `online` | operation.sms_modem_status.online |
| SmsModemStatus | `uptimeSeconds` | operation.sms_modem_status.uptime_seconds |
| SmsModemStatus | `signalQuality` | operation.sms_modem_status.signal_quality |
| SmsModemStatus | `signalStrength` | operation.sms_modem_status.signal_strength |
| SmsModemStatus | `carrier` | 相关carrier_code目录投影 |
| SmsModemStatus | `networkRegistrationCount` | operation.sms_modem_status.network_registration_count |
| SmsModemStatus | `simStatus` | operation.sms_modem_status.sim_status |
| SmsModemStatus | `sentSuccessCount` | operation.sms_modem_status.sent_success_count |
| SmsModemStatus | `sentTotalCount` | operation.sms_modem_status.sent_total_count |
| SmsModemStatus | `simBalanceDisplay` | operation.sms_modem_status.sim_balance_display |
| MetricMeta | `asOf` | core.metric_snapshot.as_of |
| MetricMeta | `status` | core.metric_snapshot.status |
| MetricMeta | `unit` | core.metric_definition.unit |
| MetricMeta | `refreshPolicy` | core.metric_definition.refresh_policy |
| MetricMeta | `errorCode` | core.metric_snapshot.error_code |
| MetricPoint | `at` | metric_snapshot.bucket_start或report_series_point.bucket_start |
| MetricPoint | `value` | core.metric_snapshot.value |
| MetricCard | `meta` | metric_snapshot与metric_definition的asOf/status/unit/refresh_policy/error_code |
| MetricCard | `value` | core.metric_snapshot.value |
| RankItem | `key` | metric_scope对应site/device/attribution/category维度的规范键 |
| RankItem | `label` | auth.menu.label |
| RankItem | `value` | core.metric_snapshot.value |
| RankItem | `siteId` | core.metric_scope.site_id |
| CategoryCount | `code` | auth.menu.code |
| CategoryCount | `label` | auth.menu.label |
| CategoryCount | `count` | 有限分组COUNT或metric_snapshot.value投影非负integer，按定义验证 |
| DeviceAlertCount | `deviceId` | core.metric_scope.device_id |
| DeviceAlertCount | `deviceName` | 设备name；历史序列用device_name_snapshot |
| DeviceAlertCount | `count` | 有限分组COUNT或metric_snapshot.value投影非负integer，按定义验证 |
| SeriesMetricSeriesItem | `code` | auth.menu.code |
| SeriesMetricSeriesItem | `label` | auth.menu.label |
| SeriesMetricSeriesItem | `points` | metric_snapshot按bucket_start或report_series_point的typed value |
| AreaSummaryRegionsItem | `regionCode` | core.metric_scope.region_code |
| AreaSummaryRegionsItem | `deviceCount` | 指定范围未删除设备COUNT或冻结报表device_count |
| AreaSummaryRegionsItem | `onlineCount` | 同一授权范围ONLINE设备COUNT或report_region_row.online_count |
| AreaSummaryRegionsItem | `onlineRate` | 同一asOf与口径的ONLINE/总设备；分母0按已确认口径返回；P-006 |
| AreaSummary | `captureCount` | 单目标/档案/区域窗口COUNT(DISTINCT capture_event.id)，或已发布analysis_result.capture_count；指标源采集量用source_counter_sample，不能混用 |
| AreaSummary | `deviceCount` | 指定范围未删除设备COUNT或冻结报表device_count |
| AreaSummary | `siteCount` | 有权采集事实COUNT(DISTINCT site_id)或已发布结果site_count |
| AreaSummary | `asOf` | core.metric_snapshot.as_of |
| AreaSummary | `unit` | core.metric_definition.unit |
| AreaSummary | `onlineDeviceCount` | core.device.status=ONLINE按授权范围COUNT |
| AreaSummary | `onlineRate` | 同一asOf与口径的ONLINE/总设备；分母0按已确认口径返回；P-006 |
| AreaSummary | `regions` | analysis.selected_region或区域统计结果，按请求契约区分 |
| DashboardSummary | `onlineDevices` | metric_scope功率/分类维度online指标快照 |
| DashboardSummary | `lastSevenDaysCapture` | 授权近7日capture指标桶 |
| DashboardSummary | `topCaptureSites` | metric_scope.site_id排行 |
| DashboardSummary | `todayAndTotalCapture` | 指标定义区分当日/累计和源采集/入库 |
| DashboardSummary | `deviceCaptureTotals` | metric_scope.device_id累计快照 |
| DashboardSummary | `alertCount` | alert_event/统一alert指标快照 |
| DashboardSummary | `deviceMap` | 授权core.device位置，bbox/聚合经地图接口；首页返回有界摘要 |
| DashboardSummary | `deduplicatedCapture` | 签认去重窗口下distinct目标指标snapshot，不能加总跨桶去重数 |
| DashboardSummary | `originTopTen` | target_attribution的目录/版本 +授权指标snapshot |
| DashboardSummary | `applicationEntries` | auth.menu.route+required_permission_code生成入口/上下文要求 |
| DashboardSummary | `fenceRadioStatistics` | metric_scope.data_type=FENCE/radio_type分类 |
| DashboardSummary | `operationStatistics` | audit_event按operation_category的授权指标snapshot |
| DashboardSummary | `topSitesByDataType` | metric_scope.data_type/site_id排行 |
| DashboardSummary | `deduplicatedSeries` | metric_scope.data_type/origin_class去重桶 |
| DashboardSummary | `deviceAlertCounts` | alert_event.device_id授权聚合 |
| OperationsSummary | `deviceOnline` | 同Dashboard在线指标定义 |
| OperationsSummary | `captureOverview` | 同Dashboard源采集/入库定义 |
| OperationsSummary | `smsModem` | sms_modem_status最新有效观测，附collection_status |
| OperationsSummary | `anomalies` | core.anomaly_event.category_code的12类系列；阈值P-006/P-020 |
| OperationsSummary | `timeGranularity` | 请求规范化后时间粒度 |
| OperationsSummary | `byDataType` | core.metric_scope.data_type分组metric_snapshot |
| OperationsSummary | `timeRange` | 请求/冻结输入的time_from/time_to或bucket边界 |
| OperationsSummary | `bucketGranularity` | core.metric_snapshot.granularity |
| AnomalySeries | `code` | auth.menu.code |
| AnomalySeries | `points` | metric_snapshot按bucket_start或report_series_point的typed value |
| TypedRankMetric | `dataType` | core.metric_scope.data_type |
| TypedRankMetric | `ranking` | 授权metric_snapshot按明确value降序+稳定scope ID |
| DeduplicatedSeries | `dataType` | core.metric_scope.data_type |
| DeduplicatedSeries | `originClass` | core.metric_scope.origin_class |
| DeduplicatedSeries | `metric` | 对应定义+scope+时间桶snapshot |
| DeviceAlertMetric | `meta` | metric_snapshot与metric_definition的asOf/status/unit/refresh_policy/error_code |
| DeviceAlertMetric | `items` | 所属子表按父键、指定版本/发布尝试分页投影 |
| CaptureMetricSeries | `metricCode` | core.metric_definition.metric_code |
| CaptureMetricSeries | `metric` | 对应定义+scope+时间桶snapshot |
| FileAsset | `fileAssetId` | core.file_asset.id |
| FileAsset | `category` | core.file_asset.category |
| FileAsset | `fileName` | core.file_asset.file_name |
| FileAsset | `mimeType` | core.file_asset.mime_type |
| FileAsset | `byteSize` | core.file_asset.byte_size |
| FileAsset | `status` | core.file_asset.status |
| FileAsset | `createdAt` | core.file_asset.created_at |
| FileAsset | `expiresAt` | core.file_asset.expires_at |
| FileAsset | `errorCode` | core.file_asset.error_code |
| FileUploadData | `fileAssetId` | core.file_asset.id |
| FileUploadData | `status` | core.file_asset.status |
| UploadPolicy | `category` | core.upload_policy.category |
| UploadPolicy | `mimeTypes` | core.upload_policy.allowed_mime_types |
| UploadPolicy | `extensions` | core.upload_policy.allowed_extensions |
| UploadPolicy | `maxBytes` | core.upload_policy.max_bytes |
| UploadPolicy | `enabled` | core.upload_policy.enabled |
| CatalogBinding | `fieldKey` | core.dictionary_binding.field_key |
| CatalogBinding | `dictionaryCode` | core.dictionary_binding.dictionary_code |
| CatalogBinding | `label` | core.dictionary_binding.label |
| FeatureAvailability | `featureCode` | core.feature_config.feature_code |
| FeatureAvailability | `enabled` | core.feature_config.enabled |
| FeatureAvailability | `pendingReason` | core.feature_config.pending_reason |
| ImportTemplate | `templateCode` | core.import_template.template_code |
| ImportTemplate | `version` | core.import_template.template_version |
| ImportTemplate | `columns` | core.import_column按template_code/template_version/sort_order |
| ImportTemplate | `formats` | core.import_template.formats |
| ImportTemplate | `transactionPolicy` | core.import_template.transaction_policy |
| ImportTemplate | `encoding` | core.import_template.encoding |
| ImportTemplate | `description` | core.import_template.description |
| DictionaryData | `dictionaryCode` | core.dictionary_item.dictionary_code |
| DictionaryData | `version` | core.dictionary.catalog_version |
| DictionaryData | `items` | 所属子表按父键、指定版本/发布尝试分页投影 |
| DictionaryItem | `code` | core.dictionary.code |
| DictionaryItem | `label` | core.dictionary_item.label |
| DictionaryItem | `order` | sort_order（菜单/字典条目） |
| DictionaryItem | `enabled` | core.dictionary.enabled |
| RegionDescriptor | `regionCode` | core.region.region_code |
| RegionDescriptor | `name` | core.region.name |
| RegionDescriptor | `parentRegionCode` | core.region.parent_region_code |
| RegionDescriptor | `geometry` | core.region.boundary；RegionDescriptor仅在单Polygon且P-010确认后投影；多Polygon仅用于支持MultiPolygon的地图契约 |
| TextModelParameterValue | `parameterCode` | analysis.model_parameter_definition.parameter_code |
| TextModelParameterValue | `valueType` | analysis.model_parameter_value.value_type |
| TextModelParameterValue | `textValue` | analysis.model_parameter_value.text_value |
| IntegerModelParameterValue | `parameterCode` | analysis.model_parameter_definition.parameter_code |
| IntegerModelParameterValue | `valueType` | analysis.model_parameter_value.value_type |
| IntegerModelParameterValue | `integerValue` | analysis.model_parameter_value.integer_value |
| NumberModelParameterValue | `parameterCode` | analysis.model_parameter_definition.parameter_code |
| NumberModelParameterValue | `valueType` | analysis.model_parameter_value.value_type |
| NumberModelParameterValue | `numberValue` | analysis.model_parameter_value.number_value |
| BooleanModelParameterValue | `parameterCode` | analysis.model_parameter_definition.parameter_code |
| BooleanModelParameterValue | `valueType` | analysis.model_parameter_value.value_type |
| BooleanModelParameterValue | `booleanValue` | analysis.model_parameter_value.boolean_value |
| DurationSecondsModelParameterValue | `parameterCode` | analysis.model_parameter_definition.parameter_code |
| DurationSecondsModelParameterValue | `valueType` | analysis.model_parameter_value.value_type |
| DurationSecondsModelParameterValue | `integerValue` | analysis.model_parameter_value.integer_value |
| DistanceMetersModelParameterValue | `parameterCode` | analysis.model_parameter_definition.parameter_code |
| DistanceMetersModelParameterValue | `valueType` | analysis.model_parameter_value.value_type |
| DistanceMetersModelParameterValue | `numberValue` | analysis.model_parameter_value.number_value |
| ModelParameterDescriptor | `parameterCode` | analysis.model_parameter_definition.parameter_code |
| ModelParameterDescriptor | `label` | analysis.model_parameter_definition.label |
| ModelParameterDescriptor | `valueType` | analysis.model_parameter_value.value_type |
| ModelParameterDescriptor | `required` | analysis.model_parameter_definition.required |
| ModelParameterDescriptor | `unit` | analysis.model_parameter_definition.unit |
| ModelParameterDescriptor | `minimum` | analysis.model_parameter_definition.minimum |
| ModelParameterDescriptor | `maximum` | analysis.model_parameter_definition.maximum |
| ModelParameterDescriptor | `description` | analysis.model_parameter_definition.description |
| ModelRuleDescriptor | `ruleCode` | analysis.model_matching_rule.rule_code |
| ModelRuleDescriptor | `label` | analysis.model_parameter_definition.label |
| ModelRuleDescriptor | `algorithmVersion` | analysis.model_matching_rule.algorithm_version |
| ModelRuleDescriptor | `outputTagCodes` | analysis.model_rule_output_tag.tag_code |
| ModelRuleDescriptor | `description` | analysis.model_parameter_definition.description |
| TextDeviceParameterChange | `parameterCode` | core.device_parameter_definition.parameter_code |
| TextDeviceParameterChange | `valueType` | core.device_parameter_value.value_type |
| TextDeviceParameterChange | `textValue` | core.device_parameter_value.text_value |
| IntegerDeviceParameterChange | `parameterCode` | core.device_parameter_definition.parameter_code |
| IntegerDeviceParameterChange | `valueType` | core.device_parameter_value.value_type |
| IntegerDeviceParameterChange | `integerValue` | core.device_parameter_value.integer_value |
| NumberDeviceParameterChange | `parameterCode` | core.device_parameter_definition.parameter_code |
| NumberDeviceParameterChange | `valueType` | core.device_parameter_value.value_type |
| NumberDeviceParameterChange | `numberValue` | core.device_parameter_value.number_value |
| BooleanDeviceParameterChange | `parameterCode` | core.device_parameter_definition.parameter_code |
| BooleanDeviceParameterChange | `valueType` | core.device_parameter_value.value_type |
| BooleanDeviceParameterChange | `booleanValue` | core.device_parameter_value.boolean_value |
| DeviceCommandDeviceResult | `deviceId` | operation.device_command.device_id |
| DeviceCommandDeviceResult | `status` | operation.device_command.status |
| DeviceCommandDeviceResult | `attemptNo` | operation.device_command_attempt.attempt_no |
| DeviceCommandDeviceResult | `startedAt` | operation.device_command.started_at |
| DeviceCommandDeviceResult | `finishedAt` | operation.device_command.finished_at |
| DeviceCommandDeviceResult | `errorCode` | operation.device_command.error_code |
| DeviceCommandDeviceResult | `errorMessage` | operation.device_command.error_message |
| DeviceCommandDeviceResult | `acknowledgementId` | operation.device_command.acknowledgement_id |
| ImportColumn | `name` | core.import_column.column_name |
| ImportColumn | `label` | core.import_column.label |
| ImportColumn | `required` | core.import_column.required |
| ImportColumn | `type` | GeoJSON固定类型或import_column.value_type（按Schema） |
| ImportColumn | `description` | core.import_column.description |
| UserListItem | `userId` | auth.user_role.user_id |
| UserListItem | `userCode` | auth.app_user.user_code |
| UserListItem | `loginName` | auth.app_user.login_name |
| UserListItem | `displayName` | auth.app_user.display_name |
| UserListItem | `status` | auth.app_user.status |
| UserListItem | `roleIds` | auth.user_role.role_id |
| UserListItem | `updatedAt` | auth.app_user.updated_at |
| UserListItem | `version` | auth.app_user.version |
| CaptureTotals | `meta` | metric_snapshot与metric_definition的asOf/status/unit/refresh_policy/error_code |
| CaptureTotals | `todayCaptured` | 今日source_counter_sample统一口径快照；P-006 |
| CaptureTotals | `allCaptured` | 累计source_counter_sample统一口径快照；P-006 |
| CaptureTotals | `todayIngested` | 今日capture_event事实入库数快照 |
| CaptureTotals | `allIngested` | 累计capture_event事实入库数快照 |
| DeviceOnlineOverview | `meta` | metric_snapshot与metric_definition的asOf/status/unit/refresh_policy/error_code |
| DeviceOnlineOverview | `total` | 受限管理查询COUNT；海量检索不强制全表COUNT |
| DeviceOnlineOverview | `online` | operation.sms_modem_status.online |
| DeviceOnlineOverview | `onlineRate` | 同一asOf与口径的ONLINE/总设备；分母0按已确认口径返回；P-006 |
| DeviceOnlineOverview | `byDeviceType` | metric_scope.device_category_code在线/总设备快照 |
| DeviceOnlineOverview | `byPowerCategory` | metric_scope.power_class_code分组，分类目录P-006 |
| PowerOnlineBucket | `powerCategoryCode` | core.metric_scope.power_class_code |
| PowerOnlineBucket | `label` | auth.menu.label |
| PowerOnlineBucket | `total` | 受限管理查询COUNT；海量检索不强制全表COUNT |
| PowerOnlineBucket | `online` | operation.sms_modem_status.online |
| PowerOnlineBucket | `onlineRate` | 同一asOf与口径的ONLINE/总设备；分母0按已确认口径返回；P-006 |
| CategoryMetric | `meta` | metric_snapshot与metric_definition的asOf/status/unit/refresh_policy/error_code |
| CategoryMetric | `items` | 所属子表按父键、指定版本/发布尝试分页投影 |
| RankMetric | `meta` | metric_snapshot与metric_definition的asOf/status/unit/refresh_policy/error_code |
| RankMetric | `items` | 所属子表按父键、指定版本/发布尝试分页投影 |
| SeriesMetric | `meta` | metric_snapshot与metric_definition的asOf/status/unit/refresh_policy/error_code |
| SeriesMetric | `series` | 固定metric_scope维度 +metric_snapshot；报表series+series_point |
| DashboardMapOverview | `meta` | metric_snapshot与metric_definition的asOf/status/unit/refresh_policy/error_code |
| DashboardMapOverview | `deviceCount` | 指定范围未删除设备COUNT或冻结报表device_count |
| DashboardMapOverview | `siteCount` | 有权采集事实COUNT(DISTINCT site_id)或已发布结果site_count |
| DashboardMapOverview | `mapEndpoint` | 契约常量 /api/v1/map/devices；不是数据库字段 |
| ApplicationEntry | `code` | auth.menu.code |
| ApplicationEntry | `label` | auth.menu.label |
| ApplicationEntry | `route` | auth.menu.route |
| ApplicationEntry | `requiredPermission` | auth.menu.required_permission_code |
| AnomalyOverview | `meta` | metric_snapshot与metric_definition的asOf/status/unit/refresh_policy/error_code |
| AnomalyOverview | `deviceAnomalies` | core.anomaly_event设备类code统计 |
| AnomalyOverview | `dataAnomalies` | core.anomaly_event字段/位置等code统计 |
| AnomalyOverview | `volumeAnomalies` | core.anomaly_event数据量类code统计 |
| DataTypeOperations | `dataType` | core.metric_scope.data_type |
| DataTypeOperations | `totals` | 统一CaptureTotals指标定义snapshot |
| DataTypeOperations | `captureSeries` | core.metric_scope.data_type时间桶snapshot |
| DataTypeOperations | `anomalies` | core.anomaly_event.category_code的12类系列；阈值P-006/P-020 |
| DeviceCapturePoint | `at` | metric_snapshot.bucket_start或report_series_point.bucket_start |
| DeviceCapturePoint | `captureCount` | 单目标/档案/区域窗口COUNT(DISTINCT capture_event.id)，或已发布analysis_result.capture_count；指标源采集量用source_counter_sample，不能混用 |
| DeviceCapturePoint | `ingestedCount` | capture_event事实计数snapshot |
| MapFeatureProperties | `featureType` | 地图投影有限类型，实体表或有界格网聚合 |
| MapFeatureProperties | `captureId` | capture.capture_target.capture_id |
| MapFeatureProperties | `deviceId` | capture.capture_event.device_id |
| MapFeatureProperties | `siteId` | capture.capture_event.site_id |
| MapFeatureProperties | `count` | 有限分组COUNT或metric_snapshot.value投影非负integer，按定义验证 |
| MapFeatureProperties | `dataType` | capture.capture_event.data_type |
| MapFeatureProperties | `status` | capture.capture_event.status |
| MapFeatureProperties | `capturedAt` | capture.capture_event.capture_time |
| MapFeatureProperties | `displayLabel` | 设备/场所name或目标授权脱敏文本；聚合显示计数 |
| MapFeatureProperties | `target` | capture.target代理ID/类型 +相关target_id；不以字符串合并身份 |
| MapFeatureProperties | `regionCode` | core.site.region_code |
| MapFeatureProperties | `siteName` | core.site.name（报表用report_device_row.site_name） |
| MapFeatureProperties | `deviceName` | 设备name；历史序列用device_name_snapshot |
| MapFeatureProperties | `clusterId` | 由查询视窗、zoom和固定聚合格网生成临时ID，不作业务实体 |
| MapFeatureProperties | `expansionZoom` | 有界聚合计算展开级别，客户端地图契约 |
| MapFeatureProperties | `byDataType` | core.metric_scope.data_type分组metric_snapshot |
| GeoFeature | `type` | GeoJSON固定类型或import_column.value_type（按Schema） |
| GeoFeature | `id` | capture.capture_event.id |
| GeoFeature | `geometry` | core.region.boundary；RegionDescriptor仅在单Polygon且P-010确认后投影；多Polygon仅用于支持MultiPolygon的地图契约 |
| GeoFeature | `properties` | MapFeatureProperties由授权实体或聚合结果有限投影 |
| GeoFeatureCollection | `type` | GeoJSON固定类型或import_column.value_type（按Schema） |
| GeoFeatureCollection | `features` | 地图GeoFeature数组，或ClientConfig有限feature_config.code/enabled（按Schema） |
| GeoFeatureCollection | `aggregated` | 本次地图按zoom/请求aggregation策略计算 |
| GeoFeatureCollection | `truncated` | 超契约上限时明确标记，不以返回列表声称全量 |
| GeoFeatureCollection | `maxFeatures` | 本次请求与配置约束的有效上限 |
| GeoFeatureCollection | `asOf` | core.metric_snapshot.as_of |
| HeatCell | `cellId` | 固定格网临时键，绑定窗口与聚合口径 |
| HeatCell | `center` | 格网中心由有权capture geometry生成GeoPoint |
| HeatCell | `value` | core.metric_snapshot.value |
| HeatCell | `unit` | core.metric_definition.unit或固定契约定义 |
| HeatCell | `window` | 本次冻结查询时间范围 |
| HeatmapData | `cells` | capture_event按bbox/时间/授权有界聚合 |
| HeatmapData | `asOf` | core.metric_snapshot.as_of |
| HeatmapData | `aggregated` | 本次地图按zoom/请求aggregation策略计算 |
| OneTimeNotificationSchedule | `scheduleType` | operation.notification_plan.schedule_type |
| OneTimeNotificationSchedule | `scheduleAt` | operation.notification_plan.scheduled_at |
| DailyNotificationSchedule | `scheduleType` | operation.notification_plan.schedule_type |
| DailyNotificationSchedule | `localTime` | operation.notification_plan.local_time |
| DailyNotificationSchedule | `timeZone` | operation.notification_plan.time_zone |
| DailyNotificationSchedule | `startDate` | operation.notification_plan.start_date |
| DailyNotificationSchedule | `endDate` | operation.notification_plan.end_date |
| RetainedNotificationTarget | `channel` | operation.delivery/notification_recipient.channel_type |
| RetainedNotificationTarget | `recipientId` | operation.notification_recipient.id |
| SetOperand | `symbol` | analysis.set_operand.symbol |
| SetOperand | `datasetId` | analysis.set_operand.dataset_id |
| SetOperand | `datasetVersion` | analysis.set_operand.dataset_version |
| DeviceOnlineReportPreview | `reportType` | operation.report_definition.report_type |
| DeviceOnlineReportPreview | `regionCode` | operation.report_definition.region_code |
| DeviceOnlineReportPreview | `deviceCount` | operation.report_definition.device_count |
| DeviceOnlineReportPreview | `onlineCount` | operation.report_region_row.online_count |
| DeviceOnlineReportPreview | `onlineRate` | operation.report_region_row.online_rate |
| DeviceOnlineReportPreview | `asOf` | operation.report_definition.as_of |
| DeviceOnlineReportPreview | `items` | 所属子表按父键、指定版本/发布尝试分页投影 |
| DeviceOnlineReportPreview | `nextCursor` | 按稳定排序键签名的游标，绑定查询、授权与快照；不作业务列 |
| DeviceOnlineReportPreview | `pageSize` | 请求校验后的大小；不存业务表 |
| DeviceOnlineReportPreview | `previewKind` | report_type -> OpenAPI有限预览类型映射 |
| DeviceStatusReportPreview | `reportType` | operation.report_definition.report_type |
| DeviceStatusReportPreview | `regionCode` | operation.report_definition.region_code |
| DeviceStatusReportPreview | `items` | 所属子表按父键、指定版本/发布尝试分页投影 |
| DeviceStatusReportPreview | `asOf` | operation.report_definition.as_of |
| DeviceStatusReportPreview | `nextCursor` | 按稳定排序键签名的游标，绑定查询、授权与快照；不作业务列 |
| DeviceStatusReportPreview | `pageSize` | 请求校验后的大小；不存业务表 |
| DeviceStatusReportPreview | `previewKind` | report_type -> OpenAPI有限预览类型映射 |
| CaptureReportPreview | `reportType` | operation.report_definition.report_type |
| CaptureReportPreview | `regionCode` | operation.report_definition.region_code |
| CaptureReportPreview | `metrics` | report_metric_row冻结的摘要指标 |
| CaptureReportPreview | `series` | 固定metric_scope维度 +metric_snapshot；报表series+series_point |
| CaptureReportPreview | `asOf` | operation.report_definition.as_of |
| CaptureReportPreview | `definition` | operation.report_definition +report_definition_site |
| CaptureReportPreview | `deviceCount` | operation.report_definition.device_count |
| CaptureReportPreview | `captureCount` | operation.report_definition.capture_count |
| CaptureReportPreview | `operatorSeries` | report_series.carrier_code维度 +series_point |
| CaptureReportPreview | `devices` | report_device_row冻结DeviceSummary字段 |
| CaptureReportPreview | `nextCursor` | 按稳定排序键签名的游标，绑定查询、授权与快照；不作业务列 |
| CaptureReportPreview | `pageSize` | 请求校验后的大小；不存业务表 |
| CaptureReportPreview | `previewKind` | report_type -> OpenAPI有限预览类型映射 |
| SystemReportPreview | `reportType` | operation.report_definition.report_type |
| SystemReportPreview | `components` | report_component_row冻结的组件状态 |
| SystemReportPreview | `asOf` | operation.report_definition.as_of |
| SystemReportPreview | `previewKind` | report_type -> OpenAPI有限预览类型映射 |
| ReportPreviewData | `reportId` | operation.report_definition.report_id |
| ReportPreviewData | `preview` | 依report_type有限oneOf组装相应冻结行 |
| ClientConfig | `version` | core.config_item.config_version的配置发布版本 |
| ClientConfig | `mapCoordinateSystem` | client-config有限Schema配置，常量WGS84 |
| ClientConfig | `taskPollIntervalSeconds` | core.config_item ClientConfig字段，仅前端读取配置 |
| ClientConfig | `maxUploadBytes` | core.upload_policy最大允许大小，ClientConfig投影 |
| ClientConfig | `featureAvailability` | core.feature_config功能关闭原因；pending不擅启用 |
| ClientConfig | `uploadPolicies` | core.upload_policy |
| ClientConfig | `institutionTimeZone` | client-config配置Asia/Shanghai，与指标/通知时区一致 |
| ClientConfig | `contextTtlSeconds` | core.config_item ClientConfig有限字段，创建context时执行 |
| ClientConfig | `maxOnlineQuerySeconds` | core.config_item ClientConfig有限字段，SQL statement_timeout执行 |
| ClientConfig | `maxOnlineWindowDays` | core.config_item ClientConfig有限字段，请求校验执行 |
| ClientConfig | `features` | 地图GeoFeature数组，或ClientConfig有限feature_config.code/enabled（按Schema） |
| ClientConfig | `catalogBindings` | core.dictionary_binding |
| DataScope | `siteIds` | 范围关联表site_id；禁止从全库维表自动扩大授权 |
| DataScope | `allSites` | 对应范围实体management_all_sites或data_all_sites（由schema确定） |
| DataScope | `dataTypes` | 范围data_type条目或保存的有限data_types数组 |
| DataScope | `ownOnly` | auth.user_scope.own_only |
| DataScope | `scopeVersion` | auth.user_scope.scope_version |
| SiteScope | `siteIds` | 范围关联表site_id；禁止从全库维表自动扩大授权 |
| SiteScope | `allSites` | 对应范围实体management_all_sites或data_all_sites（由schema确定） |
