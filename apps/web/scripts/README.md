# 生成与审计汇总脚本

从前端工程根目录调用，先npm ci；脚本覆盖指定生成/报告文件，不生成真实后端。

## 文件与目录

| 文件 | 输入、输出与调用 |
| --- | --- |
| [generate-api.mjs](<generate-api.mjs>) | 读工程根相对../../contracts/api/openapi.yaml，生成src/api四文件和操作/生成记录；npm run generate:api |
| [audit.mjs](<audit.mjs>) | 读需求、契约、已有测试/依赖证据，写final-audit.md/json；npm run audit |

## 怎么使用

改契约后生成，再typecheck、相关测试和build。audit不会重跑测试/构建或联网查漏洞；读取的dependency-audit.json和浏览器报告可能为历史，generatedAt只是汇总时间。

## 维护与检查

不能把新汇总时间当输入执行时间。当前交接及授权依赖结果看[项目审查](<../../../docs/project-governance/PROJECT_AUDIT.md>)和[依赖补检](<../../../docs/project-governance/DEPENDENCY_AUDIT.md>)。复跑前逐项核输入日期/范围；生成文件只来自当前YAML，手工适配保留在client/gateway等。返回[工程入口](<../README.md>)。
