> 2026-10-18当前开发基线：[PD-2026-10-19.v1](../project-governance/暂定方案与修改记录.md)。本文件原有未决值、四人编号及“未签认统一422/禁用”说明由本版暂定规则覆盖；甲方仍待确认，真实资源/实现就绪仍独立校验。原文保留作追踪。

# API 与接口契约设计

## 主风格与边界

主 API 为 REST `/api/v1`，以 OpenAPI 3.0.3 为机器可读契约（`../../contracts/api/openapi.yaml`）。固定资源、强权限、长任务和审计比前端任意取字段更重要，因此不采用 GraphQL。接口实现前先经 DEV-01/02/03/04 检查请求、返回、错误、权限、数据范围和 Mock。

统一成功体为 `{ "code":"0", "message":"success", "data":..., "traceId":"..." }`；业务失败使用适当 HTTP 4xx/5xx，失败体使用ErrorResponse：code、message、traceId、details和固定null的data。异步创建返回 HTTP 202 与 `taskId`。列表 `data={items,page?,pageSize?,total?,nextCursor?}`；管理小表允许 page/pageSize；采集/日志等大表使用 cursor，`total` 默认不返回。时间 RFC 3339 带偏移并转换 UTC，枚举大写稳定值，ID UUID 字符串。GeoJSON 按 WGS84 `[lon,lat]`，精度与边界见 GIS 契约。

文件先上传受控 `file_asset`，病毒/类型/大小校验通过后供搜图或导入引用；下载经当前权限二次校验、短时令牌或同源流式接口，导出不可返回永久裸路径。敏感目标/号码不放 URL、浏览器历史或访问日志；检索条件使用 POST JSON。幂等写入携 `Idempotency-Key`；TraceId 由服务端生成并在响应头/体透传。详细约定和错误域见 `../../contracts/api/api-conventions.md`、`../../contracts/api/error-codes.md`。

## 当前资源族与权限

| 资源族 | 核心操作 | 权限/范围 | 服务端行为 |
| --- | --- | --- | --- |
| `/auth`, `/me` | 登录/退出/当前用户 | 会话 | 禁用立即拒绝 |
| `/captures/search` | 四类条件检索 | `capture:read` + 场所/类型范围 | 游标、时间窗、脱敏 |
| `/contexts` | 创建/读取跨页上下文 | `context:use` + 目标范围 | 服务端保存、读取重校 |
| `/map/features`, `/trajectories/query` | bbox、轨迹 | `map:read` + 数据范围 | 聚合/限量 GeoJSON |
| `/analysis-tasks`, `/tasks` | 创建分析/轮询/取消/结果 | `analysis:*` + 数据范围 | 参数版本化、异步 |
| `/datasets`, `/set-tasks` | 数据集和运算 | `dataset:*` | 数据范围不放宽 |
| `/watch-rules`, `/alerts` | 布控与命中 | `watch:*` | 生效区间、审计 |
| `/notification-tasks`, `/deliveries` | 通知任务与送达 | `notification:*` | 幂等、回执 |
| `/sites`, `/devices`, `/device-commands` | 运维与远控 | `device:*` + 场所范围 | 高风险确认/审计 |
| `/dashboard/summary`, `/operations/summary` | 指标 | `dashboard:read` | 快照时间、部分失败 |
| `/users`, `/roles`, `/audit-events` | 设置与审计 | 细分权限 | 后端校验、不可变审计 |

当前OpenAPI已定义193操作和强类型请求/响应；同人搜图、档案模型、人员库、批次回放与报表均有明确接口。业务算法、权限、厂商和统计口径仍见OPENAPI_PENDING。SQL表结构和响应字段逐项映射见database/api-storage-map.md；不再把当前契约描述为只有P0骨架。

## 跨模块 Context 与 GIS Contract

`AnalysisContext` 含 contextId、targets（每项 targetId 与类型）、dataTypes、timeRange、spatialRange、sourceModule、filters、createdAt、expiresAt。浏览器只保存 contextId；目标列表、敏感筛选和权限快照由服务端保管。创建时和每次复用时校验数据范围，角色变化立即影响旧上下文。选区 GeoJSON 必须是有效闭合多边形，服务端限定顶点数、范围面积和时间窗；框选设备和框选采集点是不同操作，明确 `selectionType`。轨迹点按 captureTime、id 稳定排序，时区统一。

## 版本与兼容

添加可选字段可兼容；修改字段含义、枚举、统计口径、坐标系或必填性必须新版本/ADR。错误码稳定，前端按 code 处理，不解析 message。OpenAPI 的 Mock 响应与契约测试从同一 schema 生成；DEV-04 维护负向权限、超时、空结果、部分失败场景。消息推送若后续加入，只提示 taskId/statusChanged，权威状态仍由任务 GET 接口读取。

## SQL与DTO投影

数据库snake_case与API camelCase由明确DTO映射；targetId不是自然人ID。分析analysisTaskId与执行taskId、reportId与taskId、通知notificationTaskId计划ID与executionTaskId分开。通知计划POST返回201，执行才使用统一异步Task。dataset/version及规则/计划版本与对应数据库当前版本相等；修改使用If-Match/版本按当前OpenAPI要求，冲突返回409。所有列表/详情的响应字段来源见数据库映射，不以SELECT *或自由JSON替代。
