# 共用与HTTP展示组件

组件由页面/App复用，通过props/emits、store或HTTP获取数据，不单独启动。

## 文件与目录

| 文件 | 功能 |
| --- | --- |
| [DataTable.vue](<DataTable.vue>) | 选择、分页、详情、右键和可见行事件，区分本地/远端分页 |
| [QueryForm.vue](<QueryForm.vue>) | 按Field生成筛选控件并发search/reset |
| [FormDialog.vue](<FormDialog.vue>) | 通用新增/编辑弹窗、字段、错误、busy和区域选择 |
| [ChartPanel.vue](<ChartPanel.vue>) | ECharts折线/柱图、图例、尺寸响应及适用导出 |
| [DeviceMap.vue](<DeviceMap.vue>) | OpenLayers点/聚合、单选/框选、轨迹、热力、详情与重置；展开框内聚合点 |
| [RegionPicker.vue](<RegionPicker.vue>) | 表单中框选区域和区域几何双向绑定 |
| [RemoteAssetImage.vue](<RemoteAssetImage.vue>) | 受控图片元数据/下载、对象URL释放和旧请求防护 |
| [RemoteDashboard.vue](<RemoteDashboard.vue>) | 服务端系统首页指标/图表 |
| [RemoteOperations.vue](<RemoteOperations.vue>) | 服务端运维汇总、在线率/采集指标 |
| [RemoteMap.vue](<RemoteMap.vue>) | HTTP地图、区域、轨迹/热力及取消请求 |
| [RemoteProfiles.vue](<RemoteProfiles.vue>) | HTTP档案、关联记录、人像与轨迹 |
| [RemoteReportPreview.vue](<RemoteReportPreview.vue>) | 固定历史报表资源的详情、预览及图表 |
| [RemoteTaskDetails.vue](<RemoteTaskDetails.vue>) | 任务状态、行错误、导入/集合结果、取消/重试 |
| [agent/](<agent/README.md>) | 智能体对话组件：欢迎页、会话列表、消息气泡、操作卡片、输入区和本地模型设置 |

## 智能体组件

| 文件 | 功能 |
| --- | --- |
| [AgentWelcome.vue](<agent/AgentWelcome.vue>) | 欢迎页：开场白、四类快捷入口、能力示例、当前应答通道 |
| [AgentConversationList.vue](<agent/AgentConversationList.vue>) | 本机会话列表：新建、搜索、按日期分组、删除 |
| [AgentMessageItem.vue](<agent/AgentMessageItem.vue>) | 用户/智能体气泡、思考过程、Markdown正文、来源标签、复制/重生成 |
| [AgentActionCard.vue](<agent/AgentActionCard.vue>) | 把动作目录渲染为可点击卡片，主操作（新建）高亮 |
| [AgentComposer.vue](<agent/AgentComposer.vue>) | 多行输入、Enter发送/Shift+Enter换行、停止生成、模型状态入口 |
| [AgentModelDialog.vue](<agent/AgentModelDialog.vue>) | 本地模型地址/模型/温度/超时、连接测试与部署提示 |

## 怎么使用

先检查调用页面的props/events再改组件。Remote组件读真实接口状态，不生成Mock替代结果。DeviceMap同时用于地图与设备场景，RegionPicker用于表单选区，需要按调用者核对影响。

## 维护与检查

保持卸载清理监听/图表/地图/对象URL及过期响应防护。地图改动跑11项框选回归；共用表单/表格跑相关单测和页面冒烟；HTTP展示核Schema及样例。返回[源码入口](<../README.md>)。
