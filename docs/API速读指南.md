# API速读指南

契约：contracts/api/openapi.yaml，1.0.0-dev.4-provisional，PD-2026-10-19.v1。开发按暂定方案，甲方确认状态仍pending。此目录未验证阿里云服务器实际实现。

1. `/api/v1`同源HTTPS，SID Cookie；写操作CSRF/Origin；不使用浏览器localStorage Token。
2. 先读[约定](../contracts/api/api-conventions.md)，按operationId调用；UUID代理标识、敏感条件POST JSON。
3. `/me`给权限/菜单/范围，列表、详情、导出、结果、文件每次由服务端重校。菜单共28页，见policy.menuCatalog。
4. 普通查询31天/分页20默认100最大；Context30分钟；SQL5秒硬超时。导出一律异步。
5. Task的SUCCESS是工作流状态，Delivery的ACKNOWLEDGED才是通道确认；UNKNOWN先对账。任务执行30分钟/2小时从RUNNING开始，排队另计。
6. 敏感动作先申请`createActionApproval`、独立复核，再带`X-Approval-Id`执行原操作。提案/版本改变需重新申请。解密申请自动建立审批，仅指定recipient一次性POST结果。
7. 生成：`cd apps/web`后`npm run generate:api`；随后`npm run typecheck`、`npm test`、`npm run build`。生成文件不手改。

## 新增接口

| operationId | 方法与路径 |
| --- | --- |
| listActionApprovals | GET /action-approvals |
| createActionApproval | POST /action-approvals |
| getActionApproval | GET /action-approvals/{approvalId} |
| decideActionApproval | POST /action-approvals/{approvalId}/decisions |
| readDecryptionResult | POST /decrypt-requests/{requestId}/result |


新审批资源前端页面/服务端事务仍由开发实现，本轮提供契约、存储和流程设计，不声称已实装。`AUTHENTICATED`不是业务权限码：审批还须被配置为该操作审批人、有动作权限及范围、不能自批。

## 暂定与真实就绪

原65操作/37处字段已采用暂定规则，不再以设计未确认统一422；外部资源缺失503或features.disabled说明原因。历史REQUIREMENT_PENDING错误码保留兼容，不代表本轮仍有未设计的条目。

决策摘要、未取得资源和网上依据见[主文档](project-governance/暂定方案与修改记录.md)，全部操作追踪见[主账](../contracts/api/OPENAPI_PENDING.md)。

## 新人阅读与后端模块分工（2026-10-19）

保留PR中的接口导航思路，所有数字和行为按主分支当前198操作、623 Schema重核。DEV-01平台与运维，DEV-02数据与研判；DEV-03前端、DEV-04数据、DEV-05测试。模块与逐操作责任见[后端双人分工](project-governance/BACKEND_TWO_DEV_SPLIT.md)及[JSON清单](project-governance/backend-operation-owners.json)。

| 顺序 | 阅读入口 | 解决的问题 |
| --- | --- | --- |
| 1 | [API启动说明](../apps/api/README.md) | Java 17 / Spring Boot骨架、探针、环境及未实现项 |
| 2 | [API约定](../contracts/api/api-conventions.md) | 认证、分页、错误、权限、幂等及状态机 |
| 3 | [错误码](../contracts/api/error-codes.md) | 按code处理，不依赖中文message |
| 4 | [前端联调](../contracts/api/FRONTEND_INTEGRATION.md) | 同源HTTPS、Vite代理及真实失败 |
| 5 | [内部任务协议](project-governance/INTERNAL_TASK_PROTOCOL.md) | TaskStore、租约、尝试隔离及同事务发布 |
| 6 | [敏感动作协议](project-governance/SENSITIVE_ACTION_PROTOCOL.md) | 审批、版本冻结、独立复核及一次性消费 |
| 7 | [数据库设计](../contracts/database/README.md) | schema、投影和迁移；代码存在不等于迁移完成 |

### 接口实现要点

- 统一成功壳`{code:"0",message,data,traceId}`；失败显式`data:null`与`details`数组，响应头X-Trace-Id与体内一致。下载二进制不套成功JSON壳。
- 管理资源用page/pageSize/total；大结果用cursor/nextCursor，绑定账号、授权版本与查询快照，不伪造total。
- UUID代理ID不等于敏感明文。targetId不等于fileAssetId；analysisTaskId等领域ID不等于执行taskId。
- 时间UTC/RFC3339、区间[from,to)；GeoJSON为WGS84、经度在前；缺坐标不画(0,0)。精确参数以当前OpenAPI为准。
- 202只表示接受，不能当执行成功；RUNNING取消先提出请求，第三方UNKNOWN先对账；失败、失租或取消不公开半成品。
- required/nullable/additionalProperties及跨字段约束必须由服务端校验；前端隐藏按钮不代替授权，列表/详情/导出/文件每次重校范围。
- 原65操作的停做门禁已被PD暂定方案覆盖；甲方待确认、实际资源未取得、尚未实现应分别标明，不以Mock伪造正式结果。

### 本轮明确暂缓的文案

用户要求下一任务再处理以下PR原句，本轮保留原文；该段不得覆盖当前OpenAPI、ADR和后端模块责任：

技术栈见 [ADR-002 后端选型](<system-design/adr/ADR-002-backend.md>)：**Spring Boot 承担唯一业务 API，Python Worker 只做接入/分析/导出/报表**，不要在 Java 里重写用户/权限/系统 CRUD，也不要在 Python 里复制一份 RBAC。

本轮只整合分工与契约基线；未宣称198业务操作已实现或生产验收通过。
