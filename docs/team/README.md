# 当前开发分工

2026-10-18，以开发负责人聊天确认为准；共5人、各一人全职。

| 人员 | 职责 |
| --- | --- |
| DEV-01/DEV-02 | apps/api/；workers/delivery/；contracts/api/；contracts/database/ (后端整体，内部自行领取) |
| DEV-03 | apps/web/ |
| DEV-04 | workers/data/；contracts/database/ (数据映射、查询、投影提案) |
| DEV-05 | tests/；质量验证与发布验收 |


DEV-01/02承担后端整体需求、公开API、事务、鉴权、任务、审批、命令/通知适配、数据库迁移集成；两人工作时自行领取，不另行虚构个人模块归属。DEV-04负责采集/数据查询/指标/算法Worker，依内部协议协作；DEV-03负责Web/GIS展示；DEV-05负责测试。部署、监控、备份和恢复由对应开发者指定实际执行人，每次发布登记。

交接见[INTERNAL_TASK_PROTOCOL](../project-governance/INTERNAL_TASK_PROTOCOL.md)；暂定规则见[主文档](../project-governance/暂定方案与修改记录.md)。旧文件中的01后端/02前端/03数据/04测试编号仅为历史版本，不再作为当前领取依据。真实审批人由甲方/部署方另行提供，开发编号不是审批资格。

工期：2026-10-13为第1个工作日，共60个中国大陆法定工作日；需2027官历补齐后再算精确截止日。原PDF全部功能本期，未做功能延期。
