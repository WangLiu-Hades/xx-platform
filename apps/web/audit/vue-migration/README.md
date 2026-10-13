# Vue迁移与回归证据

本目录保存迁移追踪、契约生成和测试输出。当前不同报告的执行日期不同，不能把目录名final或汇总时间当成全量重新验收。

## 文件与目录

| 文件或目录 | 功能 |
| --- | --- |
| [requirements-source.json](<requirements-source.json>) | 原PDF文字需求的来源/页码/内容，供Vue逐项追踪 |
| [pdf-text.txt](<pdf-text.txt>) | 原PDF提取文字，不包含可用于像素比对的有效原截图 |
| [source-manifest.json](<source-manifest.json>) | 原PDF、旧原型和源资料的来源哈希与边界 |
| [openapi-operations.json](<openapi-operations.json>) | 生成时的193操作完整目录，来自指定YAML，不是真实服务 |
| [openapi-generation.json](<openapi-generation.json>) | 契约来源、哈希、193操作/581Schema及生成时间 |
| [unit-initial.json](<unit-initial.json>) | 迁移初期单元测试记录，历史证据 |
| [unit-final.json](<unit-final.json>) | 最后实际保存的单测结果，当前283项；看报告执行时间 |
| [smoke-initial.json](<smoke-initial.json>) | 初期28页冒烟记录 |
| [smoke-final.json](<smoke-final.json>) | 最后实际保存的28页冒烟及页面/控制台错误记录 |
| [vue-e2e-results.json](<vue-e2e-results.json>) | 13组Mock浏览器回归记录，保留历史实际时间 |
| [http-e2e-results.json](<http-e2e-results.json>) | 5组HTTP契约样例回归，不是真后端 |
| [http-analysis-results.json](<http-analysis-results.json>) | 3组HTTP研判表单样例回归 |
| [http-resources-results.json](<http-resources-results.json>) | 8组受控上传/资源/画像/地图等样例回归 |
| [map-selection-results.json](<map-selection-results.json>) | 11项真实鼠标操作的合成点位框选回归结果 |
| [dependency-audit.json](<dependency-audit.json>) | 迁移批次历史npm漏洞响应；当前补检在治理audit/dependency-current.json |
| [final-audit.md](<final-audit.md>) | 聚合已有证据的历史迁移审计阅读版，生成时间不是所有输入的执行时间 |
| [final-audit.json](<final-audit.json>) | 迁移审计矩阵/检查的机器可读版，范围同上 |
| [FRONTEND_REMAINING.md](<FRONTEND_REMAINING.md>) | 尚依赖真实后端、规则或外部条件的前端交付边界 |
| [flows](<flows>) | 22组扩展Mock业务流程结果与历史失败截图 |
| [dashboard-final.png](<dashboard-final.png>) | 对应首页/地图/搜索等场景的历史浏览器截图，不是新的独立验收 |
| [dashboard-initial.png](<dashboard-initial.png>) | 对应首页/地图/搜索等场景的历史浏览器截图，不是新的独立验收 |
| [dashboard-smoke.png](<dashboard-smoke.png>) | 对应首页/地图/搜索等场景的历史浏览器截图，不是新的独立验收 |
| [http-analysis-failure.png](<http-analysis-failure.png>) | 保留的失败现场截图，可能早于后续通过结果，须结合JSON时间判断 |
| [http-resources-failure.png](<http-resources-failure.png>) | 保留的失败现场截图，可能早于后续通过结果，须结合JSON时间判断 |
| [map-final.png](<map-final.png>) | 对应首页/地图/搜索等场景的历史浏览器截图，不是新的独立验收 |
| [map-selection-failure.png](<map-selection-failure.png>) | 保留的失败现场截图，可能早于后续通过结果，须结合JSON时间判断 |
| [map-selection.png](<map-selection.png>) | 对应首页/地图/搜索等场景的历史浏览器截图，不是新的独立验收 |
| [search-context-final.png](<search-context-final.png>) | 对应首页/地图/搜索等场景的历史浏览器截图，不是新的独立验收 |

## 怎么使用

先读final-audit及FRONTEND_REMAINING，再查对应JSON的time/timestamp、checks、errors和URL。重跑命令在../../tests/README.md；各脚本覆盖指定文件，聚合命令npm run audit不主动跑测试。

## 维护与检查

当前工程交接和授权后依赖补检以docs/project-governance/PROJECT_AUDIT.md及DEPENDENCY_AUDIT.md为准。Mock/HTTP样例均不能代替真实服务、设备或算法效果验收。 返回[上级入口](<../README.md>)。
