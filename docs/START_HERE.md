# 从这里开始开发

当前开发基线为[PD-2026-10-18.v1](project-governance/暂定方案与修改记录.md)。后端个人分工按2026-10-19的[双人分工](project-governance/BACKEND_TWO_DEV_SPLIT.md)，其余规则延续主分支。

1. 读[原始功能PDF](requirements/source/平台功能设计（原始）.pdf)及[需求追踪](system-design/00_需求追踪与设计说明.md)。原始业务要求优先，Mock不作为业务依据。
2. 从[角色入口](team/README.md)领取：DEV-01平台后端、DEV-02研判后端、DEV-03前端、DEV-04数据、DEV-05测试。
3. 查[工程分工](project-governance/ENGINEERING_ASSIGNMENTS.md)、[开发计划](project-governance/DEVELOPMENT_PLAN.md)及[待确认事项](project-governance/PENDING_REGISTER.md)。历史四人编号及旧停做门禁已被当前方案覆盖；历史人日估算不等于当前60工作日承诺的可行性证明。
4. API唯一机器入口是[openapi.yaml](../contracts/api/openapi.yaml)，数据库入口是[schema.sql](../contracts/database/schema.sql)。契约变更同步生成类型、校验、测试与说明；本轮合并保持主分支契约字节不变。
5. 按[内部任务交接](project-governance/INTERNAL_TASK_PROTOCOL.md)与[敏感动作协议](project-governance/SENSITIVE_ACTION_PROTOCOL.md)做真实链路。审批人配置、真实厂商资源及生产启用分别验收。

## 当前可使用范围

Vue展示原型、Spring Boot骨架、合成PLATE数据处理/查询模块及198操作/623 Schema契约可使用或查阅。后端业务接口、正式H04集成、真实四源、外部投递、容量和生产部署仍未完成。目录或设计存在不代表真实服务就绪。

启动见[前端](../apps/web/README.md)、[后端](../apps/api/README.md)、[数据Worker](../workers/data/README.md)。旧原型在archive，只供历史对照。
