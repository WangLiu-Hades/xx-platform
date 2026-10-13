# 原型需求与验证证据

本目录保存旧静态原型需求追踪及后续Vue迁移证据，不是程序入口。用JSON/Markdown阅读器查看，不能把报告生成时间当真实服务验收时间。

## 文件与目录

| 文件或目录 | 功能 |
| --- | --- |
| [requirements.json](<requirements.json>) | 原PDF214项文字要求到旧页面/测试编号的机器可读映射 |
| [requirements.md](<requirements.md>) | 上述需求追踪的阅读版 |
| [manifest.json](<manifest.json>) | 原PDF哈希、页数、图像占位信息及旧验证摘要 |
| [vue-migration/](<vue-migration/README.md>) | Vue需求基线、生成记录、单测/浏览器/HTTP样例和历史最终汇总 |

## 怎么使用

从requirements查需求编号，结合当前API覆盖矩阵、Vue报告及[项目审查](<../../../docs/project-governance/PROJECT_AUDIT.md>)判断实现和待交付边界。当前测试命令在[tests入口](<../tests/README.md>)，此目录内不运行服务。

## 维护与检查

需求源和历史结果保留来源与日期，不用当前Vue通过证明历史文件也已重跑。审计汇总只读取已有结果；真实后端、模型及外部服务需单独联调验收。返回[工程入口](<../README.md>)。
