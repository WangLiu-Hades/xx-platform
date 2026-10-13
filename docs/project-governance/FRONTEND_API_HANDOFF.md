# 当前前端接口对接清单和后端交接

核对日期：2026-10-10；唯一机器契约[openapi.yaml](<../../contracts/api/openapi.yaml>)，版本1.0.0-dev.2，SHA256 `f10fc158252bad97cb48d9d223e7878618dda403dca5fa863a54cdf6b8aa54cd`。

当前契约193个操作、581个Schema；65个操作自身标pending。对当前src的TypeScript AST、条件变量和Vue模板检查发现127个操作有运行时静态引用，66个未发现。**静态引用只证明代码出现，不证明按钮可达、业务全部实现或真实后端联调通过。** 逐操作的参数、请求/成功响应Schema、权限、pending、代码文件/行号和交接责任见[JSON](<audit/api-inventory.json>)；可分发筛选的[CSV](<audit/api-inventory.csv>)。[66项明细](<audit/contract-only-operations.md>)保留健康/接入/高级维护等契约，不直接删接口。

| 分配领域 | 全部操作 | 前端静态引用 | 仅契约 | 操作级pending |
| --- | --- | --- | --- | --- |
| M01/M15 | 2 | 0 | 2 | 0 |
| M01/M06 | 32 | 26 | 6 | 6 |
| M03 | 7 | 0 | 7 | 3 |
| M04 | 1 | 1 | 0 | 0 |
| M13 | 1 | 1 | 0 | 0 |
| M05 | 5 | 5 | 0 | 2 |
| M07/M13 | 5 | 4 | 1 | 0 |
| M08 | 9 | 7 | 2 | 0 |
| M09 | 16 | 8 | 8 | 0 |
| M10/M14 | 22 | 14 | 8 | 9 |
| M11 | 36 | 23 | 13 | 24 |
| M12 | 39 | 25 | 14 | 12 |
| M14 | 18 | 13 | 5 | 9 |

所有公开API由DEV-01负责实现和统一鉴权，数据查询/计算部分按ENGINEERING_ASSIGNMENTS的H02–H05交接给DEV-03；代码可以同仓协作，不要求跨语言多建公开服务。路径族仅作排期分组；例如批量删除会分派到多个领域，exportMetrics由DEV-03计算指标，认证及通用任务不应被误认为同一业务模块。

## 页面动作怎样调用服务

| 用户动作或页面 | 当前前端主要operationId | 后端必须完成 | 数据工程师必须完成 |
| --- | --- | --- | --- |
| 登录、初始化、退出 | getCsrfToken/login/getMe/getClientConfig/listMenuCatalog/listPermissionCatalog/logout | 会话、CSRF/Origin、用户/范围、配置开关 | 提供合成或批准脱敏的测试数据 |
| 系统首页/运维首页 | getDashboardSummary/getOperationSummary | 授权后的聚合响应和局部失败 | 统一指标定义、水位快照、采集量≠入库量 |
| 四类采集检索 | searchCaptures/getCapture/resolveTargets/createContext | 请求验证、范围/脱敏、游标、Context | 参数化检索、类型payload、索引/投影 |
| 人像上传搜图 | uploadFile/getFileAsset/createFaceSearchTask/getTask/listFaceSearchResults | 文件校验/受权、任务/结果API | 合法模型适配、阈值/上限、候选与评测 |
| 地图/轨迹/热力 | getDeviceMapFeatures/queryTrajectory/getMapHeatmap/getMapAreaSummary | bbox/点数/时间窗和访问范围 | 空间聚合、WGS84转换、按目标排序轨迹 |
| 伴随/碰撞/区域统计 | createAnalysisTask/reviseAnalysisTask/listAnalysisTasks/listAnalysisResults | 任务资源、幂等、范围及发布 | 三类计算和解释证据；保留输入/版本 |
| 数据集/集合计算 | createDataset/importDatasetItems/listDatasetItems/previewSetTask/createSetTask/getSetTask | 数据集版本、引用保护、导入任务 | ID集合运算、导入解析、固定版本 |
| 人员库/图码车码/模型 | listPersonLibraries/createLibraryPerson/createProfileCorrection/searchProfiles/listProfileTargets/listProfileEvidence/listTrendModels/createModelParameterVersion/listTrendModelResults | 库/档案资源、事务/权限/谱系、版本 | 去重/关联候选/证据、RESIDENCE执行 |
| 布控/告警/运维通知 | createWatchRule/activateWatchRule/listAlerts/getWatchStatistics/createNotificationTask/listPlatformMessages | 规则与计划状态、告警去重、可靠投递/回执 | 匹配候选/证据及结束后统计 |
| 场所/设备/参数 | listSites/listDevices/getDeviceParameters/updateDeviceParameters/createDeviceCommand | CRUD/分组、命令下发、逐台回执、审批 | 状态标准化、历史采集/质量数据 |
| 报表/受控导出 | createReport/getReportPreview/downloadReport/createExport/getTask/downloadFile | 任务、文件权限、过期和下载 | 八类公式/预览冻结、Excel/ZIP生成 |
| 用户/角色/日志 | listUsers/listRoles/searchAuditEvents/searchSmsLogs/deleteResourcesBatch/revokeResourcesBatch | 授权、禁用/引用保护、脱敏审计和批量逐项结果 | 查询投影协作，不另起RBAC |

表中列出典型动作，逐项完整清单以JSON/CSV为准。同步201资源创建与异步202执行任务要按每个操作契约区分；通知计划201不代表消息已送达。

## 代码与实际调用链

以前端“围栏检索→查询”为例：`src/components/QueryForm.vue`触发事件，`src/pages/SearchPage.vue`调用Pinia的`src/stores/platform.ts`；`src/api/gateway.ts::loadPage/searchInput`构造FENCE查询并调用`searchCaptures`；`src/api/client.ts::request`按`operations.ts`查到`POST /captures/search`，校验参数，Axios向本站`/api/v1/captures/search`发送JSON。开发时Vite代理转发到后端；生产由反向代理转发。DEV-01验证会话、权限/场所范围并调用DEV-03提交的检索模块，从正式数据库返回受控DTO；前端校验响应，再经`captureView`与Pinia更新表格。HTTP模式无本地Mock成功回退。

长任务示例：前端POST创建分析→API同事务保存意图和async_task→返回202及taskId→数据Worker按租约领取、计算并检查取消→校验租约后原子发布结果→前端getTask查询状态→SUCCESS后读取领域结果或受控下载文件。任务底座与外部副作用由DEV-01负责，数据计算由DEV-03负责；见H04/H05。当前并非所有页面都自动轮询，任务中心提供手工刷新；正式时效要求须按能力明确实现。

## 对接配置和共同约束

详细启动、请求头和交付步骤见[FRONTEND_INTEGRATION.md](<../../contracts/api/FRONTEND_INTEGRATION.md>)。开发启动前在PowerShell设置`$env:API_PROXY_TARGET='http://127.0.0.1:8080'`；Vite从process.env读取此代理值，不能只把它写进.env.local。`.env.local`中的`VITE_API_MODE=http`和`VITE_API_BASE_URL=/api/v1`决定前端模式/路径。后端保留`/api/v1`前缀，代理没有rewrite。生产须重新以HTTP模式构建并配置同源反向代理；当前演示dist的Mock包不是正式对接包。

- 当前拟议认证为SID Cookie，withCredentials、CSRF和Origin；P-001仍需正式确认，不能同时加入互相冲突的Bearer方案。renewSession有契约、当前前端未发现调用，续期是后续任务。
- 写请求按契约发送X-CSRF-Token、Idempotency-Key；更新资源按要求携带If-Match或批量项version。用户有意重试必须保留同一逻辑请求的键；当前客户端每次未指定键会生成新值，后端不能单凭前端随机键防止所有重复业务。
- 普通成功响应是code="0"、message、data、traceId；错误使用契约HTTP状态和ErrorCode。文件下载返回受控二进制，上传是multipart，不套用JSON文件响应。
- UUID内部ID、targetType、[from,to)时间范围、RFC3339带时区、WGS84经度在前和稳定游标必须保持一致。按钮隐藏不是后端授权。
- 无pending标记不等于可以绕过项目级权限/安全确认；相关能力开关由后端配置。先以真实数据库实现一个小闭环，再逐领域接通，禁止一次性把全部开关打开。

联调通过标准：浏览器实际HTTP请求→服务端日志traceId→真实数据库写入/查询→Worker结果→刷新后读回；同时验证无权、冲突、失败、取消、超时、文件过期、缩权及服务停止。仅OpenAPI样例拦截不满足这项标准。
