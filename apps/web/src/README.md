# 前端应用源码

本目录由Vite编译，不独立运行。先从[工程README](<../README.md>)启动，再按以下分层查代码。

## 文件与目录

| 文件或目录 | 功能 |
| --- | --- |
| [main.ts](<main.ts>) | 创建Vue应用，注册Pinia、Router、Element Plus中文及全局样式 |
| [App.vue](<App.vue>) | 登录/导航/页面壳，选择业务页面，承载通用表单、导入、消息、任务入口及悬浮智能助手入口 |
| [router.ts](<router.ts>) | Hash路由、/page/:id及默认/无效地址跳转（默认页为智能对话） |
| [domain.ts](<domain.ts>) | 页面分组、登录默认页、领域类型、字段/筛选、数据/报表类型和日期/ID工具 |
| [style.css](<style.css>) | 应用壳、卡片、表格、弹窗、地图、智能助手悬浮入口等全局样式 |
| [pages/](<pages/README.md>) | 12类页面组件承载29菜单页（含智能体对话主界面） |
| [components/](<components/README.md>) | 表格、表单、图表、地图、HTTP专用组件及agent/对话组件 |
| [stores/](<stores/README.md>) | Pinia状态、用户动作、会话及Mock/HTTP分支，含智能体会话状态 |
| [api/](<api/README.md>) | 生成类型、Axios、校验、查询/写操作/文件/任务适配及本地大模型客户端 |
| [mock/](<mock/README.md>) | 合成数据、浏览器持久化、角色菜单迁移和模拟导入/计算 |
| [utils/](<utils/README.md>) | 浏览器文件、集合表达式、智能体意图识别与气泡Markdown工具 |

## 怎么使用

按钮通常由页面/组件调用usePlatform动作；HTTP动作经api适配调用request(operationId,args)，Axios按生成目录发请求并校验响应，数据回写状态再渲染。部分HTTP专用组件直接调用request。Mock使用本地repository，不能当后端实现。

智能体链路：`pages/AgentPage.vue` 调用 `stores/agent.ts`；`utils/agentNlu.ts` 先把输入映射为平台能力与动作，`api/llm.ts` 负责本地模型探活和流式对话，`utils/agentMarkdown.ts` 负责转义后的受限渲染。模型只生成文字，页面跳转和表单打开仍由动作目录与用户点击执行。

## 维护与检查

页面名称/菜单与domain、App和路由保持一致，请求使用当前operationId和DTO。修改后从工程根目录检查类型、相关单测；交互或接口变更再做对应浏览器回归。新增README不参与运行逻辑。返回[工程入口](<../README.md>)。
