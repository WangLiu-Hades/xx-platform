> 历史角色文件，编号已调整。当前01/02后端、03前端、04数据、05测试，以[当前分工](../project-governance/ENGINEERING_ASSIGNMENTS.md)为准；不要按本文件旧角色领取。

# DEV-03 数据与算法 开发入口

## 负责什么、代码放哪

采集适配、标准化、去重、ETL与回放；领域查询SQL、成员投影、指标、分析计算、人像候选及模型。公共API、鉴权事务和外部通知由DEV-01负责。

工作区：[主目录](../../workers/data/README.md)、[关联目录](../../contracts/database/README.md)。未实现的目录只有README说明，不代表服务已具备。

## 开始工作

先提供数据样本/标准模型/查询接口与任务输入输出，再按开发计划实现Worker。数据域字段与索引提案放 contracts/database，经DEV-01合并迁移。P-016跨源拟合、合并拆分规则待定，人工动作须保留证据和谱系，禁止自行定阈值。

共同必读：[原始PDF](../requirements/source/平台功能设计（原始）.pdf)、[工程任务边界](../project-governance/ENGINEERING_ASSIGNMENTS.md)、[开发计划](../project-governance/DEVELOPMENT_PLAN.md)、[前端接口交接](../project-governance/FRONTEND_API_HANDOFF.md)、[待确认清单](../project-governance/PENDING_REGISTER.md)。

精确契约：[OpenAPI](../../contracts/api/openapi.yaml)、[API约定](../../contracts/api/api-conventions.md)、[DDL](../../contracts/database/schema.sql)、[数据库未决项](../../contracts/database/pending-design.md)。

## 交付与验收

输入：数据来源协议、获准样本、当前API/DDL、业务签认规则。输出：可重复计算、版本化结果、质量统计、查询SQL、Worker协议和失败/重试语义。验收：数据质量、去重/边界、重放一致性、计算准确性、容量及证据谱系；DEV-01验证编排，DEV-04独立复验。

每个PR注明实际作者、DEV角色、需求/接口编号、受影响契约、验证结果和未定事项。跨角色边界以工程分工中的交接表为准。返回 [角色总表](README.md)。
