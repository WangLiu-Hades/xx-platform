# 智能体对话组件

与 `pages/AgentPage.vue`、`stores/agent.ts` 配合，只负责展示与交互，不直接调用模型或业务接口。

## 文件与目录

| 文件 | 功能 |
| --- | --- |
| [AgentWelcome.vue](<AgentWelcome.vue>) | 欢迎页、快捷入口卡片、能力示例和应答通道提示 |
| [AgentConversationList.vue](<AgentConversationList.vue>) | 会话列表、搜索、日期分组、删除确认 |
| [AgentMessageItem.vue](<AgentMessageItem.vue>) | 消息气泡、思考步骤、Markdown、来源标签、操作卡片与建议 |
| [AgentActionCard.vue](<AgentActionCard.vue>) | 动作卡片网格；`data-action-id` 供浏览器回归定位 |
| [AgentComposer.vue](<AgentComposer.vue>) | 输入框、快捷追问、停止生成按钮与模型状态胶囊 |
| [AgentModelDialog.vue](<AgentModelDialog.vue>) | 本地模型配置、连接测试、部署与隐私提示 |

## 怎么使用

页面把 `agent.messages` 传给 `AgentMessageItem`，动作通过 `run` 事件回到页面，再交给 `stores/agent.ts` 的 `runAction` 执行。输入区用 `v-model` 绑定 `agent.draft`，发送后由 store 负责流式更新。

## 维护与检查

气泡内容必须经 `utils/agentMarkdown.ts` 转义渲染，不能直接把模型输出交给 `v-html`；新增动作只改 `utils/agentNlu.ts`，不要在组件里写死页面跳转。改动后运行 `tests/agent.test.ts` 和 `tests/vue-agent.e2e.cjs`。返回[组件说明](<../README.md>)。
