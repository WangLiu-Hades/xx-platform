# 敏感动作审批与存储协议

PD-2026-10-19.v1，开发暂定，待甲方确认实际审批人/密钥/用途。API见Approvals标签，数据库见schema.sql与增量脚本。未实现事务、没有资源时保持能力不可用，不以样例冒充批准。

## 判定及闭环

- 全部RESET、网络/密钥等敏感参数、批量≥10台、角色/范围授权与撤回、禁用/启用他人、管理员重置、回放/补数、档案合并拆分、涉密结果、敏感批量导出需审批。敏感批量导出暂定义为任何个人标识/档案/审计/SMS投递导出；纯聚合指标导出可无审批，但仍鉴权。用户/角色仅名称修改且不影响授权可不审批；所有授权赋值入口（含create/update user）均不得绕过。
- 一般高风险1名独立审批人；解密/密钥/批量≥10台2名。审批人需在部署审批人配置中、具备目标操作权限及当前范围；申请人、执行人均不能担任该审批的审批人。服务端先排除自身提权和授予上界冲突，再受理审批；缺足够独立审批人则禁用该动作。
- 提案仅接收强类型operationId联合及对应路径、完整请求体、If-Match；服务端补执行人/解析后的设备集合/资源version/授权版本/policyVersion并冻结。对规范化UTF-8快照计算SHA-256；客户端不能自报摘要或缩减目标。新建无既有resourceId时以操作、完整body及生成前目标绑定。
- 申请24小时过期；最终APPROVED后10分钟执行许可，解密5分钟；REJECT任一票终结，所有批准凑齐才APPROVED。每名审批人一票，重复用相同Idempotency-Key返回原决定。内容、目标集合、版本、授权或策略版本改变即拒绝执行并要求新申请。
- 执行原API时携带X-Approval-Id；同数据库事务锁审批、复核当前双方权限/版本/digest、消费许可并写领域动作/outbox/审计。原请求幂等回放先鉴权并返回原结果，不能因许可已消费再次执行；不同幂等键复用已消费许可409。审批许可过期410，内容/版本冲突409，无权限403，对象不可见404。

## 解密

`requestDecryption`冻结context/采集集合和recipient，同事务自动创建readDecryptionResult审批，响应approvalId。普通capture:read申请人可转交；执行人固定为当前具有capture:decrypt及范围的recipient；普通申请人不得自己读结果。getDecryptionRequest/审批详情仅申请人、recipient或被配置且满足范围的审批人可见，不能枚举全平台申请。

createActionApproval对已有解密request仅返回/复用当前有效审批，不制造多份同时有效许可；原申请过期/审批拒绝需重新申请。批准后同步原request为APPROVED并建grant。GET仅元数据；POST result在事务消费grant后即时解密，返回no-store，不落明文文件/缓存/日志。HTTP断开仍视为已消费，需新申请，不通过幂等缓存重放明文。审批过期/拒绝同步request状态；已查看后request可保持APPROVED但resultAvailable=false。

## 存储

- operation.action_approval：申请/执行人、operationId、policyVersion、SHA-256摘要、加密冻结快照/密钥版本、脱敏summary、状态、所需票数、时限、consumedAt/version。快照用部署密钥认证加密；随机nonce随密文封装，禁止密码/标识明文落JSONB。
- audit.action_approval_decision：追加式批准/拒绝事实，唯一(approvalId,reviewerId)；currentPassword仅现场校验，不存储。旧audit.sensitive_action_review继续保存已有设备/导出/解密关联审计，不强行塞入其三选一约束的角色授权动作。
- decryption_request新增approval_id；decryption_result_grant新增approval_id/consumed_at，result_file_id改可空并对新受控许可要求为空。旧明文文件必须先撤销并受控清理，不自动移入新模式；迁移发现旧记录则中止，防止悄然破坏历史访问规则。
- 新decision表同样使用audit.reject_mutation触发器并撤销PUBLIC写删截断；部署必须为实际应用/Worker/维护账号分别授权，owner可绕过触发器，因此应用不得持owner。过期清理只由独立维护身份执行并留审计/引用保护。

审批表是受控待办资源，决策事实在审计域；业务应用不得直接构造APPROVED或修改历史票据。只有受控服务事务能做状态转换，DDL约束不能替代该服务逻辑。

## 批量删除与截止边界

USER/ROLE批量删除不能绕过单条审批。每个BatchResourceRef.approvalId引用deleteUser/deleteRole的对应资源/If-Match提案；逐项独立事务核验并消费，失败单项REJECTED。禁止一个外层X-Approval-Id授权整个不同对象批次。

执行许可截止=min(批准时刻+10分钟,申请expiresAt)，解密改为5分钟。设备groupId在申请时解析并冻结设备集合，执行前重解析；集合变化即409重提，不在旧许可下执行新增设备。申请人和执行人均不能自批。若执行人权限缩小、被禁用或配置审批人失效，重校后拒绝，不沿用旧许可绕过。

审批详情的reviewItems由服务端从冻结快照生成，必须完整显示目标集合/版本/授权范围/用途等重要字段；密码、密钥、敏感标识掩码。summary只是摘要，不足以替代完整reviewItems；超过显示/接口预算要求拆分申请，不允许截断后批准。
