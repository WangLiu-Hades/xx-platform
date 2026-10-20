> 历史角色文件，编号已调整。当前01/02后端、03前端、04数据、05测试，以[当前分工](../project-governance/ENGINEERING_ASSIGNMENTS.md)为准；不要按本文件旧角色领取。

# DEV-02 前端 开发入口

## 负责什么、代码放哪

当前Vue界面、Pinia状态、Axios适配、客户端校验、页面图表/地图与交互。保留Mock演示，HTTP模式必须显示真实失败，不能以Mock成功代替服务响应。

工作区：[主目录](../../apps/web/README.md)、[关联目录](../../apps/web/src/README.md)。未实现的目录只有README说明，不代表服务已具备。

## 开始工作

所有前端代码集中在 apps/web；不在旧React目录继续扩展。接口有变先改当前OpenAPI并生成类型，随后同步页面和状态。原始PDF对应28页，按需求追踪逐项核验；大结果由后端分页/游标和完整Context表达。

共同必读：[原始PDF](../requirements/source/平台功能设计（原始）.pdf)、[工程任务边界](../project-governance/ENGINEERING_ASSIGNMENTS.md)、[开发计划](../project-governance/DEVELOPMENT_PLAN.md)、[前端接口交接](../project-governance/FRONTEND_API_HANDOFF.md)、[待确认清单](../project-governance/PENDING_REGISTER.md)。

精确契约：[OpenAPI](../../contracts/api/openapi.yaml)、[API约定](../../contracts/api/api-conventions.md)、[DDL](../../contracts/database/schema.sql)、[数据库未决项](../../contracts/database/pending-design.md)。

## 交付与验收

输入：原始PDF、UI规则、OpenAPI及错误码。输出：可运行前端、生成客户端、页面状态/错误/任务结果展示及回归证据。验收：类型、单元与构建通过；受影响浏览器流程、地图选择及HTTP失败路径通过；实际服务完成后再做真实联调。

每个PR注明实际作者、DEV角色、需求/接口编号、受影响契约、验证结果和未定事项。跨角色边界以工程分工中的交接表为准。返回 [角色总表](README.md)。
