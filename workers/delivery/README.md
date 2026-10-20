# 外部动作Worker（待实现）

DEV-01负责通知、设备命令、供应商适配、重试幂等、回执及外部动作对账；DEV-04提供命中/计算候选，DEV-02消费只读服务，DEV-03是前端。当前仅预留位置，尚无正式投递实现。

业务规则依[PD暂定方案](../../docs/project-governance/暂定方案与修改记录.md)，职责依[后端分工](../../docs/project-governance/BACKEND_TWO_DEV_SPLIT.md)。UNKNOWN先对账，不伪造送达或设备回执；部署实际执行人仍按每次发布登记。
