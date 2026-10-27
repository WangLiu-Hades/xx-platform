> 2026-10-18当前开发基线：[PD-2026-10-19.v1](../../project-governance/暂定方案与修改记录.md)。本文件原有未决值、四人编号及“未签认统一422/禁用”说明由本版暂定规则覆盖；甲方仍待确认，真实资源/实现就绪仍独立校验。原文保留作追踪。

# ADR 005 异步执行与发布

状态：本次数据库执行边界采用；容量、重试限额和外部协议保持pending。

Context：分析、集合、导出、报表、通知发送及远控要求进度/恢复/审计，领域ID与执行ID不能混用。

Decision：async_task +task_attempt/task_event与版本输入。PENDING准备完成后QUEUED，SKIP LOCKED领取并提高lease_generation，续租/完成依租约版本条件更新；过期Worker不能发布。结果逐attempt保存，成功事务才更新published_attempt_no。FAILED同taskId新尝试，改参数使用新领域/执行资源。通知计划同步201，调度按ONCE或本地日期键创建异步execution；不将计划ACTIVE与TaskStatus混用。

Alternatives：全部同步执行、无限重试、每任务复制全明细；Redis/MQ后续有吞吐/扇出证据再评估。

Consequences：本库意图和任务可原子提交，外部发送/设备命令依幂等及回执。UNKNOWN先对账、成功设备不重发，取消不撤回已发送事实。大输入manifest/固定集合版本，领域结果只读已发布成功尝试。详细物理字段与11条示例SQL见database目录。
