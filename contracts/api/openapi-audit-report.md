# API真实开发交付审计报告

日期：2026-10-07；契约1.0.0-dev.1 → 1.0.0-dev.2。完整平台独立开发结论：**前端NO；后端NO；完整无歧义集成NO**。已确定字段/接口可用于生成、Mock、开发和测试；外部决策未签认，不能以结构通过掩盖。

## 1. 审计范围与顺序

1. 修改前读取完整原始PDF 26页（p9–15截断部分单独补读），system-design需求/架构/模块/安全/性能/测试/风险文档、ADR、Mermaid、SQL/字典/索引和整个API目录；工程实施版沿用前轮已读模块边界。
2. 完整检查原型index/app.js/app.css、UI说明与214条PDF需求映射；逐页浏览当前28页并收集DOM动作/筛选/列；弹窗/右键/提交分支同时查源码。没有把原型Mock当真实后端，也没有将历史69个原型用例冒认为本轮重跑。
3. 修改前建立28项分级问题清单并输出关键缺口，再修改隔离草稿。
4. 生成与真实编译、示例/负例/链路验证后，重新核对PDF→原型→OpenAPI；第二轮另发现R01–R03并修复。
5. 只写API目录。历史副本保留，UI/数据库/其他系统设计保持不变，差异在本报告明确说明。

最新用户要求以原始PDF文字优先分析业务；工程实施保留模块归属，系统设计保留工程边界。原型模拟阈值/状态不得覆盖原文或自动成为业务规则。

## 2. 数量

| 项目 | 修改前 | 修改后 |
| --- | --- | --- |
| paths | 134 | 152 |
| operations | 173 | 193 |
| schemas | 482 | 581 |

初审28项，第二轮新增3项，合计31项：修复30，剩余1组EXTERNAL（H12）。严重级别为CRITICAL 7、HIGH 13、MEDIUM 8、LOW 3；剩余HIGH 1（汇总20组未签认决定，不能理解为仅一个业务小问题）。

覆盖矩阵458条：COVERED 73、PENDING_REQUIREMENT 270、NO_API_REQUIRED 115；PARTIAL/MISSING结构映射为0。214个PDF编号和28页DOM均有对应行，未匹配DOM动作0。PENDING_REQUIREMENT不能计为完整开发交付。

## 3. 主要修订

- 消除手填目标、库内人员、列表分析/集合ID、导入成功结果、脱敏收件人保留等断链。
- 首页12区域和运营指标补类型/时间/设备维度；一个口径、多页面复用，图表导出异步。
- 明确CAPTURE/TARGET集合、版本快照、混合表达式；不把跨类型身份关联偷偷当集合相等。
- 通知持久计划与单次执行状态分开，ONCE/DAILY、模板/收件人/平台消息、编辑/撤销、送达记录明确。
- 分析/命令终态修订、新执行ID、版本保护；批量删除/撤销/导出避免页面循环大量单条请求。
- 报告8类型、SINGLE XLSX/BUNDLE ZIP、previewKind强分型、分页明细；人员/车辆档案、冲突/证据/模型版本强类型。
- ErrorCode 34项、所有错误data=null、分页/时区/UUID/GeoJSON/CSRF/ETag/幂等统一；上传413/415与运行策略可发现。
- 请求/响应示例可验证，单值判别pattern+mapping兼容TS/Spring，Java名称映射避方法冲突，没有修改生成源代码凑通过。

## 4. 新增API及来源

| operationId | Method | API | 来源 |
| --- | --- | --- | --- |
| changeMyPassword | POST | /me/password | SD04/SD06/SD08；原型应用壳/公共任务 |
| createLibraryPerson | POST | /person-libraries/{libraryId}/persons | PDF p9/16-17；libraryPage/profilePage/modelPage；工程M11 |
| deleteDeviceCommand | DELETE | /device-commands/{taskId} | PDF p18-23；adminPage/configPage/reportPage；工程M12 |
| deleteResourcesBatch | POST | /batch-deletions | SD04/SD06/SD08；原型应用壳/公共任务 |
| exportAnalysisTasks | POST | /analysis-tasks/exports | PDF p10-14；analysisForm/taskPage |
| exportMetrics | POST | /metrics/exports | PDF p1-2/17-18；dashboard/opsPage |
| exportPersonLibraries | POST | /person-libraries/exports | PDF p9/16-17；libraryPage/profilePage/modelPage；工程M11 |
| getImportTaskResult | GET | /tasks/{taskId}/import-result | SD04 task result；PDF p16/19 导入后资源衔接 |
| getImportTemplate | GET | /import-templates/{templateCode} | SD04/SD06/SD08；原型应用壳/公共任务 |
| importPersonLibraries | POST | /person-libraries/imports | PDF p9/16-17；libraryPage/profilePage/modelPage；工程M11 |
| listMenuCatalog | GET | /menus | SD04/SD06/SD08；原型应用壳/公共任务 |
| listNotificationTemplates | GET | /notification-templates | PDF p15/23-24/26；watchPage/notificationPage |
| listPermissionCatalog | GET | /permissions | SD04/SD06/SD08；原型应用壳/公共任务 |
| listPlatformMessages | GET | /messages | PDF p15/23-24/26；watchPage/notificationPage |
| listProfileConflicts | GET | /profiles/{profileId}/conflicts | PDF p9/16-17；libraryPage/profilePage/modelPage；工程M11 |
| listRegions | GET | /regions | SD04/SD06/SD08；原型应用壳/公共任务 |
| resolveTargets | POST | /targets/resolve | PDF p2-7；通用动作/dataAction |
| reviseDeviceCommand | POST | /device-commands/{taskId}/revisions | PDF p18-23；adminPage/configPage/reportPage；工程M12 |
| revokeResourcesBatch | POST | /batch-revocations | PDF p15/p24；原型revoke-watch/revoke-notification |
| updateNotificationTask | PUT | /notification-tasks/{notificationTaskId} | PDF p15/23-24/26；watchPage/notificationPage |

## 5. 删除、迁移、合并与兼容影响

- 无业务operationId删除；新增20个operationId。
- 迁移removeDatasetItem：DELETE /datasets/{datasetId}/items/{targetId} → /datasets/{datasetId}/items/{itemId}，使用成员真正itemId，支持CAPTURE/TARGET；旧路由不在新版契约，SDK需重新生成。
- 不新增重复统计计算API；M13复用首页/地图/报告，公共批量操作合并页内多条调用。
- 通知创建202改201（同步创建计划），取消202改200（同步计划状态）；执行仍TaskStatus/202，plan单独ACTIVE/CANCELLED/COMPLETED；scheduleAt改ONCE/DAILY schedule。
- Watch notificationTemplateCode合并入notification.templateCode，recipientRef改请求地址/已存recipientId保留；响应脱敏。
- NotificationTaskDetail旧taskId去除歧义，以lastExecutionTaskId取得最近一次执行。
- SetTaskInput旧左右二元字段改operands+version+expression；DatasetInput新增必填elementType；不再把CAPTURE视为TARGET。
- 常驻模型仅RESIDENCE，移除无原文来源TRAJECTORY/FREQUENCY可选值；算法仍pending。
- 标准模板mappingVersion兼容别名仅1且可省略，不能要求前端猜厂商映射；原始接入mappingVersion仍依已批准协议P-004。
- 单值判别改pattern约束+mapping，无改变JSON取值；Spring生成须记录的name-mappings。
- Dashboard旧topCaptureSites/deduplicatedCapture保留deprecated兼容字段，新页用分类分组模型。
- ClientConfig以features为唯一能力目录，featureAvailability是deprecated精确投影；maxUploadBytes只作全局上限，分类uploadPolicies给实际限制。
- 这些是开发阶段契约变化，不保证旧SDK二进制/调用兼容；前后端一起更新生成输入，不能混用历史副本。

## 6. 主要Schema变化

| 实体 | 修订 |
| --- | --- |
| Context/目标 | 精确标识输入、代理解析、来源分支/版本、全量快照/预览截断/失效 |
| Dashboard/Operations | 功率在线率、类型Top5/去重序列、逐设备告警、四指标时序/范围/粒度 |
| Analysis | 三类参数/结果/日期/阈值/公共统计、领域ID及修订ID |
| Dataset/Set | CAPTURE/TARGET oneOf、itemId、A–Z操作数/版本/表达式 |
| Watch/Notification/Delivery | 最终逐目标/落脚引用、模板/脱敏收件人、RETAINED、计划/执行/消息 |
| Profile/Library/Model | 人工新增资料与成员/档案ID、外貌查询、冲突列表、参数目录/常驻版本/标签 |
| Device/Report | 参数类型、配置任务名/区域/组、逐设备回执、8报告类型和previewKind |
| Common/File | 错误null固定约束、34码、上传策略/导入模板/导入成功行/字典绑定/能力状态 |

新增命名Schema 99 个，已有Schema 482 个更新（含说明/示例）；总数581。核心DTO没有开放object/any，GeoJSON/业务联合有明确判别。最外层统一但data均具体Schema。

## 7. 分级问题清单

| ID | 级别 | 修改前/第二轮问题 | 处理 | 状态 |
| --- | --- | --- | --- | --- |
| C01 | CRITICAL | 手工目标无 targetId 解析/登记入口，未被采集目标无法创建布控/人员<br>PDF p9/15；watchForm/personForm | POST /targets/resolve明确查找/登记/逐项拒绝；标识规范化P-004/P-016未伪造。 | FIXED |
| C02 | CRITICAL | 人员库只能挂已有 profileId，无新增人员，资料编辑缺外貌和落脚点<br>PDF p9/10；personForm | createLibraryPerson原子返回member/profile；外貌/落脚资料、照片category、corrections/members拆分。 | FIXED |
| C03 | CRITICAL | 通知 recipientRef/templateCode 无来源，布控未定义通知对象和方式<br>PDF p15/24；watchForm/notificationForm | SMS/EMAIL/PLATFORM强类型请求，template目录、布控notification、recipientId/RETAINED，响应脱敏。 | FIXED |
| C04 | CRITICAL | CAPTURE 记录集合与 TARGET 元素不符；MIXED 无表达式；研判全量结果无法导入 Context<br>PDF p14/15；datasetPage/setEvaluate | CAPTURE/TARGET强类型元素，Context来源分支，A–Z版本操作数和显式混合表达式。 | FIXED |
| C05 | CRITICAL | 角色只能读当前用户菜单，无法取得可分配完整菜单/权限目录<br>PDF p24；adminForm | GET /permissions、/menus、client-config.catalogBindings；按钮/API共享PermissionCode。 | FIXED |
| H01 | HIGH | 首页缺功率在线率、分类排行、分类周去重时序、逐设备告警；图表导出用采集导出不适配<br>PDF p1/2；dashboard | DashboardSummary功率分类/类型Top5/去重周时序/逐设备告警；metrics/exports。 | FIXED |
| H02 | HIGH | 运维缺分类采集时序和四指标；范围枚举误称粒度；异常无法按类型切换<br>PDF p17/18；opsPage | OperationsSummary.byDataType+4指标时序+实际timeRange/bucketGranularity，复用统计口径。 | FIXED |
| H03 | HIGH | 布控列表缺账号/开始结束查询；最终统计非逐目标<br>PDF p15；watchPage | ownerLoginName/effectiveFrom/effectiveUntil；终态逐目标统计含疑似落脚引用（公式pending）。 | FIXED |
| H04 | HIGH | 通知计划混用 TaskStatus，无每日计划、编辑/上次发送；平台消息无读取 API<br>PDF p24；notificationPage/index.html | NotificationSchedule ONCE/DAILY、计划状态、nextRun/lastExecution/version、PUT及本人messages/totalCount。 | FIXED |
| H05 | HIGH | 参数命令缺任务名、区域/分组/设备名查询、编辑/删除<br>PDF p21；configPage/configForm | 命令name/region/groupName/deviceName、终态修订/删除、逐设备结果。 | FIXED |
| H06 | HIGH | 分析缺分析日期筛选、结果类型/阈值过滤、显示目标和公共统计；修订不返 analysisTaskId<br>PDF p11-14；taskPage | analysisFrom/To、dataType/minScore、显示标识/统计、修订返回AnalysisTaskCreated。 | FIXED |
| H07 | HIGH | 检索无法用已脱敏目标回填，多目标/跨源轨迹无法表达；地图多设备/多场所/区域筛选不足<br>PDF p2-10/23；dataAction/mapPage | targetIds回填，Context保留全量/来源版本，轨迹点target分组，地图多device/site筛选。 | FIXED |
| H08 | HIGH | 人员库列表批量导入/导出、选择设备导出、批量删除没有契约<br>PDF p9/11-15/19-25；exportLibraries/deleteScope | 库批量import/export、设备选中export、统一batch-deletions、分析多任务合并导出。 | FIXED |
| H09 | HIGH | 文件类别缺人员库/档案头像；导入模板、限制、行错误/事务语义不明确<br>PDF p9/19；importDialog | 上传新增类别/策略、标准v1模板、兼容mappingVersion=1、错误行与导入成功ID/事务边界。 | FIXED |
| H10 | HIGH | 报表预览缺运营商/设备/区域时序和统计日期；大预览无分页<br>PDF p22；reportDetail | previewKind多态、8报告类型、运营商/设备/区域时序、设备/区域cursor明细。 | FIXED |
| H11 | HIGH | ErrorResponse 与系统统一失败 data=null 冲突；Context 过期误 FILE_EXPIRED；结果未完成码不稳定<br>SD04；contexts/tasks | ErrorResponse固定data=null，CONTEXT_EXPIRED/TASK_RESULT_NOT_READY及统一错误/headers。 | FIXED |
| H12 | HIGH | 源文未提供算法/解密/厂商协议等21组决策，无法实现完整平台<br>PDF/SD11；OPENAPI_PENDING | 仍EXTERNAL：20组来源不足的业务/安全/算法/厂商决定，保留pending/原因/feature disabled。P-012集合语法已收敛。 | EXTERNAL |
| M01 | MEDIUM | 仅3操作成功例，缺主要请求/响应 Mock 数据<br>全文件 | 所有JSON请求/成功/共享错误有例，1,837项校验；几何非退化，车牌核心例可衔接。 | FIXED |
| M02 | MEDIUM | If-Match 格式未定、ETag 未返；写入版本和幂等保持期不明确<br>SD03/04 | ETag/If-Match强引号版本、幂等作用域/冲突/保留、批量逐项版本。 | FIXED |
| M03 | MEDIUM | query/schema enum/字段解释不足；默认排序与全局空值语义未明确<br>全文件 | 字段/nullable/timeRange、允许排序、默认值/同值次键和查询AND/OR规范。 | FIXED |
| M04 | MEDIUM | 常驻模型外自行扩展类型；模型标签与标记采集无连接<br>PDF p16/17；modelPage | 仅RESIDENCE；参数定义/version/tag、结果captureIds/dataType和execution taskId过滤。 | FIXED |
| M05 | MEDIUM | 日志用户名展示字段不足；短信发送时间与创建时间不一致；账户全日志导出无法表达<br>PDF p25/26；logPage | actorDisplayName、sentAt排序、Audit OWN_ALL、SMS SELECTED_PAGE。 | FIXED |
| M06 | MEDIUM | 档案外貌查询、区域列表/冲突ID来源缺失<br>PDF p10/16；profilePage | appearanceQuery/区域目录/冲突列表给conflictId，并保留自动身份规则pending。 | FIXED |
| M07 | MEDIUM | 用户有历史日志即禁止删除与软删生命周期冲突；本人改密需管理员权限<br>PDF p25；SD03/06 | 软删用户只阻活跃引用、历史日志保留；本人改密与管理员重置分开。 | FIXED |
| M08 | MEDIUM | 上传/下载缺具体失败码、Retry-After、媒体/最大字节与可用能力发现<br>SD04；file endpoints | 413/415、Retry-After、uploadPolicies/features、download mime/filename/授权。 | FIXED |
| L01 | LOW | api-conventions/error-codes 仍是旧版；api无 README，副本易误用<br>API目录 | README排除历史副本；约定/错误同步，不修改用户备份。 | FIXED |
| L02 | LOW | 旧模块矩阵过度宣称 COVERED；待确认优先级与本次PDF优先要求不符<br>OPENAPI_COVERAGE/PENDING | 本轮PDF优先，M01–M15矩阵不再将pending计为完整交付。 | FIXED |
| L03 | LOW | 缺逐页 UI→请求→响应→后续映射和操作需求来源<br>API目录 | 458条页面/需求/DOM/工程映射与193操作来源索引。 | FIXED |
| R01 | CRITICAL | 第二轮发现分析/集合列表详情缺领域ID，无法可靠打开选中资源<br>listAnalysisTasks/listSetTasks→getAnalysisTask/getSetTask | 三种AnalysisTaskDetail必返analysisTaskId；SetTaskDetail必返setTaskId；Model结果引用补modelId；CaptureRecord必返targetType，列表可直接构成ContextTarget。 | FIXED |
| R02 | CRITICAL | 第二轮真实编译暴露固定null/oneOf判别类型/Java方法重名/轨迹默认值及多态分派问题<br>TypeScript tsc；Spring Maven编译；GeoJSON/ReportPreview模型 | 固定null用nullable string+not string；固定判别pattern+mapping；Geo/Report discriminator；Java setTaskId名称映射；轨迹ASC默认一致，真实编译通过。 | FIXED |
| R03 | HIGH | 第二轮发现勾选撤销仍需循环单条HTTP，批量行为未覆盖<br>PDF p15/p24；revoke-watch/revoke-notification | POST /batch-revocations，WATCH_RULE/NOTIFICATION_TASK，逐项版本/权限/状态/部分失败。 | FIXED |

## 8. 验证结果

| 验证 | 结果/范围 |
| --- | --- |
| YAML | YamlDotNet完整解析，与修改JSON语义round-trip一致 |
| OpenAPI3.0.3 | 官方Generator7.25.0 validate通过；仅CommonCaptureRecord未被引用建议，保留统一基础模型，不删除业务字段 |
| refs/operationId/path参数 | 独立checker 0错误0警告；所有引用解析/ID唯一/路径参数required，JSON模型图无循环 |
| Schema/示例 | 1,837个Schema/请求/响应/共享错误样本，通过JSON Schema适配nullable及严格RFC3339检查 |
| 负例/链路 | 9流程182检查全通过：必填、UUID、未知字段、固定判别、分页上限、状态、data只能null等 |
| TypeScript | typescript-axios成功；所有DTO无any/object/unknown/NULL_SCHEMA_ERR；Vue ESNext/Bundler strict tsc通过，链路fixture同时编译 |
| Spring | Java17/Spring Boot3 interfaceOnly/useOneOfInterfaces；name-mappings后Maven package成功，未修改生成代码，DTO/Controller接口/Enum可编译 |
| Swagger UI | 无头Edge离线加载193操作，error为空，pageErrors=[] |
| 原型覆盖 | 当前28页DOM+214需求+源码弹窗/提交映射；458行，未匹配动作0；pending如实标识 |

验证是契约静态、合成Mock、代码生成和编译验证；未声称真实算法、厂商、短信供应商、生产数据公式或服务器端到端已运行。生成框架transport模板中的局部any不属于业务DTO；固定null和GeoJSON位置/拓扑等不能仅靠生成DTO表达，已在约定中明确服务端校验。

### 链路走查

1. CSRF/login/me → PLATE search → capture详情 → Context → 轨迹 → COMPANION → 执行状态 → 结果 → 数据集 → 布控 → 后端告警 → 送达/消息 → 审计。
2. 设备列表 → 详情/ETag → typed参数 → 更新/命令 → task → 逐设备结果 → 终态修订/删除。
3. 库 → 新增手工人员 → member/profile → 目标/证据/历史 → Context → 布控。
4. DAILY计划 → 执行引用/脱敏收件人 → RETAINED编辑 → 撤销 → Delivery/平台消息。
5. 混合集合 → 版本冻结 → task/setTaskId → 结果datasetId；CAPTURE保留原记录。
6. 模板 → 上传 → ACTIVE → import → 错误行/成功资源ID。
7. SINGLE/BUNDLE报告 → 执行 → typed分页预览/XLSX或ZIP下载。
8. 批量删除/撤销与引用保护；本人全审计日志/当前页SMS导出。
9. 模型参数版本 → 执行taskId → modelId+taskId过滤结果；不制造runId。

## 9. 第二轮全面审计

重新对214条PDF逐项映射、28页动作/表单/右键与193操作来源索引核对，纠正R01–R03并重新跑以上验证。当前没有未定位的后端HTTP功能边界；真正未明确的是下列业务决策，不是用any隐藏字段。无需API的UI/部署操作明确分离。

以下25项检查已逐项覆盖：功能映射/操作来源/重复资源/命名；ID/时间/GeoJSON/状态；分页/排序/错误/认证/权限；上传下载/长任务/任务状态/版本幂等；强DTO/refs/operationId/路径参数；JSON示例/SDK/业务链路；独立开发/集成阻塞。第二轮独立回归脚本second_audit.py执行4,392项结构/字段/权限/示例/来源/兼容别名/覆盖检查，全部通过；证据见second-audit-validation.json。结构与生成项PASS；完整生产行为项因pending为NO。

## 10. 开发独立性与剩余阻塞

**前端仅Prototype+OpenAPI独立完成完整平台：NO。后端仅OpenAPI+system-design独立完成完整平台：NO。完整无歧义集成：NO。**

已确定接口字段和链路可独立开发/Mock/生成，不需要再口头确认字段名；但以下业务值/规则在源资料中不存在或尚未签认：

| 组 | 仍缺什么 | 影响 |
| --- | --- | --- |
| P-001/P-002/P-021 | 认证生命周期签认、默认角色/多角色范围组合、密码/留痕/保留/脱敏策略 | 全局认证授权、安全验收、纯UI操作是否留痕 |
| P-003/P-005/P-009/P-013 | 容量/预算/保留、回放补数、Context/数据集共享归档保护策略 | 大查询、任务、重试、集合/导入/导出 |
| P-004/P-010 | 五类原始接入映射/去重/原始坐标转换、地图许可/边界 | 可信数据与地图 |
| P-006/P-020 | 精确指标/异常/在线率分母/去重归属公式、报告口径 | 首页、运维、统计与报告 |
| P-007/P-008 | 人像模型/阈值/复核、解密审批/密钥/明文生命周期 | 搜图、同人、涉密动作 |
| P-011/P-014/P-016/P-017 | 伴随/碰撞、布控匹配、身份归档/置信度/去重合并拆分、常驻模型算法 | 分析、人员/车辆档案、布控、态势 |
| P-015/P-018/P-019 | 短信猫/邮件模板/回执/频控补发、设备参数/复位协议、远控确认 | 实际投递、设备远控 |

以上按OPENAPI_PENDING的20组保留x-design-status:pending/x-pending-reason，客户端能力可disabled，执行依赖未确认规则时422 REQUIREMENT_PENDING。不得由API作者擅自确定阈值、厂商私有载荷或安全审批，也不能删除原始功能来回答YES。

### 与未修改系统设计的差异

- 数据库SQL仍是设计草稿，新增CAPTURE集合元素、收件人计划/ID、手工档案/字段、报告预览和版本等需要实现阶段迁移；不是把DTO直接当数据库表，不修改本次范围以外文件。
- 旧SD04概述接口骨架、ADR-007拟议认证与当前合同差异在api-conventions/README标明；认证最终签认仍外部决定。
- PDF串文（车牌回填IMSI、车档案复制人员等）已分析正确对象，但产品仍需签认；不为错文新增错误实体API。
- 原型任意设备状态编辑、模拟命中/完成/发送是Mock；真实状态由心跳，生产事件由后台，不暴露演示接口。
- “尚未接真实服务器”本身不是设计缺陷；缺的是业务/外部规则。

## 11. 产物及复现证据

正式API目录9个交付文件：openapi.yaml、README.md、api-conventions.md、error-codes.md、api-coverage-matrix.md、OPENAPI_COVERAGE.md、OPENAPI_PENDING.md、OPENAPI_CHANGELOG.md、openapi-audit-report.md。

隔离验证目录：`C:\Users\C-Linglong\.codex\visualizations\2026\10\03\01a0ffe5-a575-71a0-be2e-fa932afac185\audit-2026-10-07`。

| 证据 | 文件/目录 |
| --- | --- |
| 修改前审计/完整规范 | before.json、pre-audit-issues.json、source-manifest.json |
| 页面遍历 | ui-inventory.json、ui-last-page.png |
| 示例/流程/类型样本 | examples-validation.json、flow-validation.json、flow-fixtures.json、flow-types.ts |
| TS/Spring生成 | delivery-check/typescript-axios、delivery-check/spring-server |
| 生成/编译 | delivery-check/generator-validate.log、typescript-verified.log、spring-verified.log |
| Swagger | swagger-final.json |
| 语义解析 | yaml-roundtrip.json |
| 第二轮问题/覆盖 | final-issues.json、coverage-rows.json、final-audit-data.json、second-audit-validation.json |

交付YAML SHA256：`f10fc158252bad97cb48d9d223e7878618dda403dca5fa863a54cdf6b8aa54cd`。写入后按同一哈希核对，并复查API目录之外源文件未改动。验证产物未覆盖正式前后端项目。
