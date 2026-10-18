# API速读指南

契约：contracts/api/openapi.yaml，1.0.0-dev.3-provisional，PD-2026-10-18.v1。开发按暂定方案，甲方确认状态仍pending。此目录未验证阿里云服务器实际实现。

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
