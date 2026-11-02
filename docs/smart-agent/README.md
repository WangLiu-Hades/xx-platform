# 智能体主界面与本地大模型接入

2026-11-02 需求：以智能体为载体，把豆包式"用户输入 + 智能体应答"作为主界面，其他功能不变；输入"布控"对话返回"查看布控任务 / 新建布控 / 查看布控预警结果"，点击可跳转到布控操作界面；使用的大模型部署在本地。

本目录记录实现方式、运行方法、部署边界和修改日志：

| 文档 | 内容 |
| --- | --- |
| [本地大模型部署与接入](<本地大模型部署与接入.md>) | Ollama/兼容服务部署、模型选型、显存、CORS、前端配置、生产代理、排障 |
| [修改日志](<修改日志.md>) | 需求对照、逐文件改动、关键决策、验证证据、已知限制、回滚方式 |

## 一句话工作方式

用户输入 → 本地规则引擎识别平台能力与动作（离线可用）→ 本地大模型只负责组织自然语言并流式输出 → 界面渲染可点击动作卡片 → 用户点击后进入原业务页面或打开原表单。模型挂了不影响功能跳转。

## 代码位置

| 位置 | 职责 |
| --- | --- |
| [src/pages/AgentPage.vue](../../apps/web/src/pages/AgentPage.vue) | 对话主界面布局、滚动、动作分发、模型设置入口 |
| [src/stores/agent.ts](../../apps/web/src/stores/agent.ts) | 会话、流式回答、动作执行、模型探活、本机持久化、导出对话 |
| [src/utils/agentNlu.ts](../../apps/web/src/utils/agentNlu.ts) | 15 类平台能力目录、关键词打分、目标/时间/场所识别、规则应答模板 |
| [src/api/llm.ts](../../apps/web/src/api/llm.ts) | OpenAI 兼容客户端：地址归一化、探活、SSE 流式、超时/取消、内网校验 |
| [src/utils/agentMarkdown.ts](../../apps/web/src/utils/agentMarkdown.ts) | 气泡 Markdown 受限渲染（先转义后应用标记） |
| [src/components/agent/](../../apps/web/src/components/agent/README.md) | 欢迎页、会话列表、消息气泡、动作卡片、输入区、模型设置 |
| [scripts/llm-stub.mjs](../../apps/web/scripts/llm-stub.mjs) | 未安装 Ollama 时的本地模型桩（流式 SSE，可自检） |
| [tests/agent.test.ts](../../apps/web/tests/agent.test.ts) | 意图、动作、会话、权限、Markdown、SSE 解析单元测试 |
| [tests/vue-agent.e2e.cjs](../../apps/web/tests/vue-agent.e2e.cjs) | 主界面浏览器回归与截图证据 |

## 运行与演示

```powershell
cd apps/web
npm ci

# 方式 A：真实本地模型（推荐验收）
ollama pull qwen2.5:7b-instruct
ollama serve
# 另开终端
$env:OLLAMA_ORIGINS='http://127.0.0.1:5180'
npm run dev -- --port 5180 --strictPort

# 方式 B：没有 Ollama 时用模型桩演示流式
npm run llm
npm run dev -- --port 5180 --strictPort
```

登录后默认进入"智能对话"。输入"布控"可看到三张操作卡片；也可以在输入框点"布控/新建布控/查看告警结果"快捷词。右上"模型设置"可改地址、模型、温度、超时并测试连接。

`vue-agent.e2e.cjs` 需要 5180 前端服务，模型在线或离线都能跑；在线时验证流式回复，离线时验证规则降级。截图与结果写入 `audit/smart-agent-<日期>/`。

## 能力范围（当前 15 类）

布控预警、数据检索（电围/车牌/MAC/人像）、数据研判（伴随/碰撞/区域统计）、综合分析（地图/轨迹/设备分布）、数据运算、人员库、数据模型（图码/车码/数据态势）、常驻模型、运维服务、运维报表、运维通知、系统设置、任务中心、系统首页、平台帮助。

新增能力只改 `agentNlu.ts` 的能力目录和动作；如果是"新建"类动作，还要在 `stores/agent.ts` 的 `runAction` 中登记复用的表单动作。动作的 `page` 必须是 `domain.ts` 中真实存在的菜单，执行前会用 `platform.allowed` 校验权限。

## 边界与待确认

- 对话编排目前在前端完成，会话只存在本机浏览器；若后端要统一编排、审计对话或下发提示词模板，需要新增契约与接口，不能把本机存储当服务端记录。
- 本地模型只生成文字，不直接执行业务动作；设备命令、删除、上报等仍需在业务页面二次确认。
- 提示词只发送能力目录与聚合数量，不发送采集明细、人员信息、身份证/手机号；该约定需在部署评审中确认。
- 真实模型效果（首 token 延迟、准确率、并发、显存）需在目标机器上验收，模型桩不能替代。
- 模型问答可能出错，界面固定提示"结合页面数据核对"，不把模型输出当权威结论。
