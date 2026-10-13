# 稳定错误码

版本1.0.0-dev.2。本表与ErrorCode逐项一致；HTTP是标准映射，操作仅使用它声明的状态。失败固定code/message/data:null/traceId/details，details可[]。429/503带Retry-After秒数。

| code | HTTP | 客户端行为 |
| --- | --- | --- |
| VALIDATION_ERROR | 400 | 字段/格式/范围或跨字段非法；details.field给camelCase路径 |
| AUTH_REQUIRED | 401 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| AUTH_INVALID_CREDENTIALS | 401 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| AUTH_CSRF_INVALID | 403 | 同步器token或可信Origin不匹配 |
| AUTH_SESSION_EXPIRED | 401 | 清登录态，不自动重放写入 |
| PERMISSION_DENIED | 403 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| PERMISSION_DATA_SCOPE | 403 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| DATA_INVALID_FILTER | 422 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| DATA_QUERY_TOO_BROAD | 422 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| DATA_NOT_FOUND | 404 | 不存在/已删/不可见，隐藏对象存在性 |
| RESOURCE_CONFLICT | 409 | 状态/引用/唯一性冲突，禁止盲重试 |
| RESOURCE_VERSION_CONFLICT | 409 | 刷新详情确认新版本后重试 |
| TASK_CONFLICT | 409 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| TASK_NOT_CANCELLABLE | 409 | 终态或副作用不能撤回 |
| TASK_FAILED | 422 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| ANALYSIS_INVALID_RULE | 422 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| DEVICE_OFFLINE | 422 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| ALERT_RULE_INACTIVE | 422 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| FILE_INVALID | 422 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| FILE_EXPIRED | 410 | 已清理资产/结果，重新受权导出 |
| RATE_LIMITED | 429 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| SYSTEM_INTERNAL | 500 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| SYSTEM_UNAVAILABLE | 503 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |
| CONTEXT_EXPIRED | 410 | 重新建立Context |
| TASK_RESULT_NOT_READY | 409 | 轮询到SUCCESS再读结果 |
| REQUIREMENT_PENDING | 422 | 未签认业务/算法/安全/厂商规则；页面显示不可用原因 |
| NOTIFICATION_INVALID_RECIPIENT | 422 | 地址或所属计划recipientId非法 |
| MODEL_PARAMETER_INVALID | 422 | 参数编码/类型/签认范围非法 |
| DATASET_ELEMENT_TYPE_MISMATCH | 422 | CAPTURE/TARGET不兼容，不隐式转换 |
| FILE_TOO_LARGE | 413 | 超uploadPolicy.maxBytes |
| FILE_MEDIA_UNSUPPORTED | 415 | 实际文件类型不允许 |
| DATA_CURSOR_INVALID | 400 | 清旧游标重新查询 |
| IDEMPOTENCY_CONFLICT | 409 | 同键不同payload，检查原提交 |
| MAP_GEOMETRY_INVALID | 422 | 领域规则失败，按code处理并展示message/traceId，不用Mock掩盖未签认规则。 |

批量200不代表逐项成功；REJECTED项复用ErrorCode。文件流失败改为application/json。消息可本地化而code不更名。

```json
{"code":"RESOURCE_VERSION_CONFLICT","message":"资源已更新，请刷新后重试","data":null,"traceId":"example-trace","details":[{"field":"version","reason":"提交与当前版本不同"}]}
```
