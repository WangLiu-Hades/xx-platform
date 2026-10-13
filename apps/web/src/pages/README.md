# 业务页面组件

11个Vue组件由App按当前菜单和状态选择，承载28页，共用Pinia、表单、表格与接口层，不是11个独立站点。

## 文件与目录

| 文件 | 页面职责 |
| --- | --- |
| [DashboardPage.vue](<DashboardPage.vue>) | 系统首页指标、排行、地图、应用入口；HTTP委托RemoteDashboard |
| [SearchPage.vue](<SearchPage.vue>) | 四源检索、人像搜图、受控图片和右键动作 |
| [LibraryPage.vue](<LibraryPage.vue>) | 人员库/人员、导入导出、删除及选人布控 |
| [MapPage.vue](<MapPage.vue>) | 地图研判、设备分布，Mock视图与RemoteMap入口、选择/轨迹/重置 |
| [AnalysisPage.vue](<AnalysisPage.vue>) | 伴随/碰撞/区域统计任务、分类结果和二次动作 |
| [DatasetsPage.vue](<DatasetsPage.vue>) | 数据集、拖拽操作数、表达式、运算任务和结果 |
| [WatchPage.vue](<WatchPage.vue>) | 综合布控、状态、详情/结束后统计和告警结果 |
| [ProfilesPage.vue](<ProfilesPage.vue>) | 图码/车码档案、数据态势/模型，HTTP委托RemoteProfiles |
| [OperationsPage.vue](<OperationsPage.vue>) | 运维首页指标、趋势和设备概览，HTTP委托RemoteOperations |
| [AdminPage.vue](<AdminPage.vue>) | 场所、设备、角色、用户管理列表及操作 |
| [ServicePage.vue](<ServicePage.vue>) | 参数配置、运维报表/通知、操作/短信日志及共享任务中心等服务页 |

## 怎么使用

从[工程入口](<../../README.md>)运行，按domain.ts的groups和App.vue组件选择关系定位菜单。改筛选/按钮同步检查store动作、HTTP字段和权限；HTTP分页/游标使用远端状态，不对合成数组计算正式结果。

## 维护与检查

核对PDF要求、空/加载/失败/无权状态和跨页Context，共用组件变更检查其他页面。按页面执行相关单测、Mock流程或HTTP样例回归；真实算法和设备效果另做服务联调验收。返回[源码入口](<../README.md>)。
