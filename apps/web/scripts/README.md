# 生成与审计汇总脚本

从前端工程根目录调用，先npm ci；脚本覆盖指定生成/报告文件，不生成真实后端。

## 文件与目录

| 文件 | 输入、输出与调用 |
| --- | --- |
| [generate-api.mjs](<generate-api.mjs>) | 读工程根相对../../contracts/api/openapi.yaml，生成src/api四文件和操作/生成记录；npm run generate:api |
| [contract-stub.mjs](<contract-stub.mjs>) | 本地契约桩（127.0.0.1:8080，/api/v1），登录链、分页、导出、任务与场景开关；npm run stub |
| [llm-stub.mjs](<llm-stub.mjs>) | 本地大模型桩，OpenAI兼容/v1/models与/v1/chat/completions流式SSE，供未安装Ollama时演示智能体；npm run llm，自检npm run llm:selfcheck |
| [first-chain-evidence.mjs](<first-chain-evidence.mjs>) | 驱动HTTP首链场景截图与请求序列；npm run evidence |
| [baseline.mjs](<baseline.mjs>) | 汇总类型检查、单测、双构建与全部菜单页冒烟；npm run baseline |
| [audit.mjs](<audit.mjs>) | 读需求、契约、已有测试/依赖证据，写final-audit.md/json；npm run audit |

## 怎么使用

改契约后生成，再typecheck、相关测试和build。audit不会重跑测试/构建或联网查漏洞；读取的dependency-audit.json和浏览器报告可能为历史，generatedAt只是汇总时间。

`llm-stub.mjs` 默认监听11434（Ollama默认端口），被占用时自动尝试11435、11436，并在控制台打印实际地址；前端"模型设置"填入该地址即可。它只做演示，不包含真实模型权重，不能替代本地模型验收。

## 维护与检查

不能把新汇总时间当输入执行时间。当前交接及授权依赖结果看[项目审查](<../../../docs/project-governance/PROJECT_AUDIT.md>)和[依赖补检](<../../../docs/project-governance/DEPENDENCY_AUDIT.md>)。复跑前逐项核输入日期/范围；生成文件只来自当前YAML，手工适配保留在client/gateway等。返回[工程入口](<../README.md>)。
