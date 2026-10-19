# 当前开发分工

2026-10-19：后端分工采用PR #1模块方案，其余规则遵循主分支PD-2026-10-18.v1；共5人，各一人全职。

| 人员 | 当前职责 |
| --- | --- |
| DEV-01 | 平台与运维后端：M01/M06/M10/M12/M14/M15；共享权限/任务/审批/文件底座、通知/设备动作及数据库迁移集成 |
| DEV-02 | 数据与研判后端：M03/M04/M05/M07/M08/M09/M11/M13；领域API、事务及数据Worker编排 |
| DEV-03 | apps/web；Web/GIS前端、客户端调用和页面交互 |
| DEV-04 | workers/data；采集、查询、指标及算法，自测、映射与索引提案 |
| DEV-05 | tests；独立质量验证、跨模块回归及发布验收 |

模块与逐操作归属见[后端双人分工](../project-governance/BACKEND_TWO_DEV_SPLIT.md)，机器映射见[ownership.json](ownership.json)。公开REST只在apps/api；数据Worker不复制公共REST、RBAC或任务状态机。DEV-01主写common/infra/config及TaskStore，DEV-02调用共享接口并提交领域迁移片段，由DEV-01统一合并。

部署、监控、备份和恢复仍由对应开发者登记实际执行人。开发编号不是生产账号或甲方审批资格，不自动把运维分给DEV-05测试。

工期：2026-10-13为第1个工作日，共60个中国大陆法定工作日；需补齐2027官历后再算截止日。原PDF全部功能本期，未做功能延期。其他约束见[暂定方案](../project-governance/暂定方案与修改记录.md)、[内部任务交接](../project-governance/INTERNAL_TASK_PROTOCOL.md)。

历史四人编号仅用于追踪，不能按旧DEV-02前端/DEV-03数据/DEV-04测试领取任务。
