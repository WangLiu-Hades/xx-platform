# API 开发约定

版本1.0.0-dev.2，2026-10-07。唯一机器契约`openapi.yaml`；原始PDF文字优先分析业务，原型定位页面，system-design确定工程边界。Mock值不是签认规则。

## 1. HTTP、字段与响应

- 同源Base URL `/api/v1`，生产HTTPS。管理资源复数，敏感复杂检索/统计/长任务POST JSON。厂商、Worker、短信和算法内部协议不是Web接口。
- 字段camelCase，实体ID是UUID字符串。targetId不是IMSI/IMEI/MAC/车牌，fileAssetId不是targetId；analysisTaskId与执行taskId分开。
- 成功JSON：`{code:"0",message:"success",data:<具体Schema>,traceId:"..."}`。
- 失败JSON：`{code:<ErrorCode>,message:<可展示文本>,data:null,traceId:"...",details:[{field,reason}]}`。按code判断，禁止按中文message判断。
- 全部响应X-Trace-Id与JSON traceId相同；文件流不包装JSON，只用该Header；文件错误改回JSON。
- optional省略表示未提供，只有nullable允许null；PATCH省略保留，PUT必填字段完整提供。敏感收件人需RETAINED保持，不能回填脱敏值。
- OpenAPI3.0固定错误data=null采用nullable string+not string约束，生成器可能显示string|null，但非null线上非法。这不是任意object。
- 所有业务DTO明确properties/additionalProperties:false；单值判别字段以pattern+x-const-value约束，discriminator.mapping保留联合分派；状态/排序/权限/错误仍enum。

## 2. 分页、查询与排序

| 数据 | 请求 | 响应 |
| --- | --- | --- |
| 管理资源/任务/配置 | page≥1、pageSize默认20最大100 | items/page/pageSize/total |
| 采集/轨迹/告警/日志/大结果/错误行 | cursor可省略、pageSize默认20最大100 | items/nextCursor/pageSize |
| 平台消息 | 同cursor | 同cursor，另totalCount/asOf |

- 两种策略沿用SD04；不引入第三套分页。事件原型页码是Mock，实际用cursor历史支持上一页，nextCursor=null终结，不伪造total。
- 空结果items=[]；管理total=0，超末页仍返回空页及真实total。
- cursor签名且绑定账号、范围版本、查询快照、sortBy/sortOrder/pageSize；不得解析。改条件清空cursor；篡改/失效/不匹配400 DATA_CURSOR_INVALID，范围变化重新查询。
- 同组多值OR，不同条件AND；数组query重复键siteIds=x&siteIds=y。精确敏感标识仅body，普通keyword大小写归一后包含匹配。
- 每操作限制sortBy，默认值见Schema；同值追加唯一资源ID作次键，同排序方向。轨迹固定captureTime ASC/captureId ASC，不接受DESC。
- 可选管理时间省略表示不按时间筛选；高容量查询须在保留和query预算内，超预算422 DATA_QUERY_TOO_BROAD。client-config给maxOnlineWindowDays/maxOnlineQuerySeconds，禁止以当前页代替全量统计/导出/集合。

## 3. 时间、GeoJSON与上下文

- date-time是带偏移ISO8601/RFC3339，UTC存储/返回Z，前端Asia/Shanghai显示；无时区或秒/毫秒时间戳非法。
- TimeRange为[from,to)，from必须早于to。报告date按Asia/Shanghai自然日/月/年，periodEnd含所指自然周期，省略只取起始周期。
- WGS84/EPSG:4326；coordinates=[longitude,latitude]；经度[-180,180]、纬度[-90,90]。Point二维、LineString≥2点、Polygon环≥4点并闭合；非法拓扑/超限422 MAP_GEOMETRY_INVALID。OAS3.0无法分别限制数组第1/2项，服务端须校验纬度、闭环和拓扑。
- 无可信定位经纬同时null，不能画(0,0)。地图bbox=minLon,minLat,maxLon,maxLat；zoom/limit/aggregation限量，clusterId/count/expansionZoom用于下一bbox，设备与采集点selectionType分开。
- 手填标识通过resolveTargets得到ContextTarget；只LOOKUP需capture:read，登记需watch:write或person-library:write。逐项FOUND/REGISTERED/NOT_FOUND/REJECTED；规范化/身份规则未定422 REQUIREMENT_PENDING，不自行推断同人。
- CaptureRecord各分支必返targetType：FENCE为IMSI或IMEI，MAC/PLATE/FACE对应同名目标类型。targetType与targetId形成ContextTarget，不能从脱敏displayTarget推导。
- displayTarget仅展示/复制；二次检索用targetIds。CAPTURE可保存未解析身份的记录，targetId=null时禁用目标级伴随/布控。
- POST /contexts冻结查询/来源，contextId/expiresAt用于复用。targets最多100个预览，targetsTruncated与targetCount表示完整范围，不能拿预览当全量。
- CAPTURE_SEARCH保存类型条件；targets=[]表示完整授权查询。DATASET绑定datasetVersion，ANALYSIS_RESULTS省略resultIds表示全部结果，PROFILE绑定profileId。每次消费重校权限；过期410 CONTEXT_EXPIRED。

## 4. SID、CSRF、权限

- 当前唯一身份方案SID Cookie Session，HttpOnly/Secure/SameSite=Lax/Path=/api/v1。不并列Bearer或localStorage Token。认证ADR最终签认P-001。
- GET /auth/csrf匿名/登录同源可用，设置随机CSRFID HttpOnly Cookie并返回绑定同步器token/headerName；CSRFID不是身份。写请求Cookie+X-CSRF-Token+可信Origin；失败403 AUTH_CSRF_INVALID；不能用通配凭据CORS。
- login轮换SID，返回userId/idleExpiresAt/absoluteExpiresAt；重新获取csrf并/me。renew延长空闲不延长绝对过期；退出清Cookie。401清身份回登录，不自动重放写请求。
- /me返回menus/permissionCodes/SiteScope/DataScope；前端按钮与API共享PermissionCode。/permissions和/menus给角色授权目录；不能授超授予者范围。
- 采集DataScope与必要SiteScope交集，管理SiteScope。allSites=false且siteIds=[]无范围；true仍受类型/ownOnly限制。完整多角色策略P-002。
- 列表/详情/Context/任务/导出/文件每次重校范围；操作无权403，单对象不可见404隐藏存在性；Context收缩403 PERMISSION_DATA_SCOPE。
- 本人改密/me/password须currentPassword；管理员重置不收currentPassword，reason必填并撤销该用户会话。
- client-config.features是唯一能力目录，featureCode使用契约规定的枚举；旧featureAvailability已deprecated，只能逐项投影features的code/enabled，禁止独立计算。未签认能力disabled并返回reason，不能用Mock阈值上线。
- client-config.catalogBindings提供字段对应的dictionaryCode；设备/人员库category、gender、ethnicity使用该绑定读取字典，不猜目录名。uploadPolicies按category返回限制；旧maxUploadBytes仅全局上限，实际限制取全局与分类maxBytes的较小值。

## 5. 幂等、版本与批量

- 创建/导入/导出/任务/命令等重复风险用Idempotency-Key（16–160字符），作用域账号+operationId+键，比较规范化body与领域ID。相同输入返回原响应，不同输入409 IDEMPOTENCY_CONFLICT。
- 资源/任务存活期至少保留键，清理后旧键409，不创建新副作用；失败执行走retry。所有幂等回放仍重校权限。
- ETag为双引号资源version，例如`"3"`，详情header原样回传If-Match；缺失/非法400，版本冲突409 RESOURCE_VERSION_CONFLICT。scopeVersion/parameterVersionId/algorithmVersion不是ETag。
- batch-deletions/batch-revocations最多100个不同UUID+version，逐项独立事务、按输入顺序；200只说明整批完成，逐项DELETED/REVOKED/REJECTED，失败有errorCode。整类无操作权限403不执行。
- 单条与批量同状态/引用保护。用户先DISABLED，角色仍绑定未删除用户包括禁用用户409；分析/报告/命令终态，通知CANCELLED/COMPLETED，布控FINISHED/REVOKED；软删保留审计/回执，历史日志不阻止用户删除。

| resourceType | 删除权限 | 撤销权限 |
| --- | --- | --- |
| ANALYSIS_TASK | analysis:delete | 不支持 |
| WATCH_RULE | watch:delete | watch:revoke |
| PERSON_LIBRARY | person-library:write | 不支持 |
| SITE | site:write | 不支持 |
| DEVICE | device:write | 不支持 |
| USER | user:write | 不支持 |
| ROLE | role:write | 不支持 |
| REPORT | report:write | 不支持 |
| NOTIFICATION_TASK | notification:cancel | notification:cancel |
| DEVICE_COMMAND | device:command | 不支持 |
| DATASET | dataset:write | 不支持 |

## 6. 执行、布控、计划状态

- TaskStatus：PENDING→QUEUED→RUNNING→SUCCESS/FAILED/CANCELLED。202返回taskId/status/createdAt，领域资源额外ID不同。GET tasks/id轮询，未执行progress=0，SUCCESS=100；失败errorCode/errorMessage，参数快照/算法版本保留。
- RUNNING cancel先写cancelRequestedAt，202不是最终取消；Worker确认后CANCELLED，SUCCESS/FAILED不可撤回。FAILED retry同taskId、attemptNo+1；第三方UNKNOWN先对账，不盲重发。
- 结果未完成409 TASK_RESULT_NOT_READY，已清理410 FILE_EXPIRED；成功空结果items=[]。
 - TaskResultReference：ANALYSIS resultId=analysisTaskId；SET_OPERATION=setTaskId并由领域详情取resultDatasetId；FACE_SEARCH=taskId；REPORT=reportId；DEVICE_COMMAND=taskId；MODEL_RUN=taskId，modelId必须返回，结果按modelId和taskId筛选；INGESTION=batchId；NOTIFICATION=notificationTaskId；EXPORT必须fileAssetId。IMPORT调用tasks/id/import-result；合并拆分profileIds。
- 分析/配置修订只允许终态且If-Match，产生新资源/执行ID，历史不覆盖，RESET不可编辑。命令逐设备结果，外层成功不代表每台成功。
- WatchStatus DRAFT→ACTIVE→FINISHED/REVOKED。201草稿、activate确认生效时间；ACTIVE看alerts，终结后statistics最终逐目标，不能合并实时/最终口径。
- 通知计划同步201返回notificationTaskId/ACTIVE/version，ONCE完成COMPLETED；DAILY按Asia/Shanghai当地日期唯一执行、保持ACTIVE直到结束/取消，每次独立taskId由lastExecutionTaskId取得。补发/频控P-015。
- cancel计划200返回CANCELLED，在途执行另看task。编辑仅ACTIVE，既有Delivery不变。响应只有recipientMasked/recipientId，编辑用RETAINED+同计划recipientId保留，创建禁止RETAINED；SMS/EMAIL address只在请求body。PLATFORM userId由users/me取得。
- 模板notification-templates仅已批准目录，无启用模板不可发；模拟命中/强制结束/模拟发送不公开API。

## 7. 集合、人员与模型

- 先创建空dataset，再异步导入。CAPTURE按captureId等价，TARGET按targetType+targetId等价，异型422 DATASET_ELEMENT_TYPE_MISMATCH。datasetKind为单/多源。
- operands为唯一A–Z符号+datasetId/version。MIXED必须expression，其他操作禁止；只允许字母、括号、∩∪−/-，无括号同优先级左结合，与prototype setEvaluate一致，禁止eval和自动跨源身份合并。preview估量非承诺，执行冻结版本。
- 成员移除itemId不是targetId，共享/归档P-013；实际身份归一P-016。
- 新增人员同事务返回memberId/profileId/linkedTargets；编辑corrections，移除成员不删除全局档案，重复标识不能静默合并。
- PERSON/VEHICLE分型，资料脱敏，MANUAL不制造算法置信度；关联/证据/历史/冲突有明确IDs，自动关联/合并/拆分/复核P-016。
- 模型仅原文RESIDENCE，parameters目录与parameterVersionId由服务端返回；自动标签/算法阈值P-017，前端不硬编码Mock天数/次数。

## 8. 文件与模板

- multipart file+category必填，实际内容校验；先读uploadPolicies(mimeTypes/extensions/maxBytes/enabled)，服务端不信扩展名。FACE_SEARCH/PROFILE_PORTRAIT只JPEG/PNG，导入CSV/XLSX/JSON，RAW_INGESTION按已批准mapping。
- 413 FILE_TOO_LARGE、415 FILE_MEDIA_UNSUPPORTED；202文件PROCESSING，GET至ACTIVE/QUARANTINED，只有ACTIVE可引用。上传成功不等于导入成功。
- 标准设备/人员库导入的mappingVersion仅为deprecated兼容别名，可省略；若给出只允许字符串1，与固定模板v1一致。原始RAW_INGESTION映射仍由已批准协议给出，不能套用该别名。
- 模板GET import-templates/templateCode，version=1。XLSX第一表首行英文列；CSV RFC4180/UTF8可带BOM；JSON对象数组。未知列422、空可选格省略，不默认补造device/site/capture。
- ALL_OR_NOTHING全部行预检，有非法行整体回滚，task/errors cursor返回错误行。SKIP只跳过确定重复，其它错误仍回滚；身份去重P-016。SUCCESS task/import-result返回逐行CREATED/UPDATED/SKIPPED及资源ID。
- DATASET_ITEMS_V1列elementType,captureId,targetType,targetId,sourceCaptureId；CAPTURE必填captureId，TARGET必填targetType/targetId，可选sourceCaptureId，已有授权引用。
- DEVICES_V1必填serialNumber,name,siteId,category，可选manufacturer,model,longitude,latitude；经纬同时给出，禁止导入status；targetDeviceId行操作必须一行且编号匹配；UPDATE只改元数据。
- PERSON_LIBRARIES_V1批量必填libraryName,category,name,targetType,targetValue，可选gender,ethnicity,phone,identityNumber,residenceRegionCode,suspectedLandingText,appearanceDescription。库内URL指定库时name/category可省略，若给出必须匹配库；每行一个明确标识，身份合并不猜测。
- 导出异步，TaskReference.fileAssetId→files/id/download；200实际mimeType、Content-Disposition attachment、RFC5987 UTF8 filename*，过期410，受权下载不能永久裸URL。
- CaptureExportColumn白名单；省略columns取允许列，[]400；类型不适用格空，默认脱敏，faceImageAssetId映射face.imageAssetId。
- SINGLE报告一definition→XLSX，previewKind分型；BUNDLE多definitions→ZIP无在线preview。大预览cursor明细，operatorSeries对应当页设备，摘要是全授权范围。
- Audit OWN_ALL是本人全部保留日志，忽略页筛选；SMS SELECTED_PAGE仅deliveryIds。批量分析/库/设备导出单次创建任务。

## 9. 可复现生成与验收

- 示例合成，不能当算法/审批/厂商阈值。结构、全部JSON示例、负例、链路ID及生成代码分别验证；YAML可解析不是完成标准。
- 前端原型使用 openapi-typescript 从唯一 `openapi.yaml` 生成 DTO/操作类型，Axios 负责同源请求、withCredentials 与动态 CSRF；运行时按契约校验。后端生成器选择另行联调确认。
- Spring Generator 7.25.0 spring，interfaceOnly=true,useSpringBoot3=true,useOneOfInterfaces=true，CLI `--name-mappings setTaskId=setOperationId`。只改变Java变量/方法，JSON仍setTaskId，避开taskId setter重名。
- 单判别值pattern+mapping使Spring接口/分支getter一致，同时JSON Schema/@Pattern验证常量；不得为编译通过删除业务字段或修改生成代码。
- 完整生产独立开发结论NO，真实待确认见OPENAPI_PENDING/审计报告；已明确部分可强类型Mock、开发和测试。CI/CD/备份/发布不是业务REST。
