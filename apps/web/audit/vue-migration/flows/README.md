# 扩展Mock流程结果

这里是vue-flows.e2e.cjs输出，覆盖检索、导入、布控、模型、配置、报表、权限和日志等流程。

## 文件与目录

| 文件或目录 | 功能 |
| --- | --- |
| [results.json](<results.json>) | 最后实际执行的扩展流程结果、过滤范围、通过/失败及控制台错误，检查是否22组完整运行 |
| [failure-1.png](<failure-1.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-10.png](<failure-10.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-11.png](<failure-11.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-13.png](<failure-13.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-15.png](<failure-15.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-17.png](<failure-17.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-18.png](<failure-18.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-19.png](<failure-19.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-2.png](<failure-2.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-20.png](<failure-20.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-21.png](<failure-21.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-3.png](<failure-3.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-4.png](<failure-4.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-5.png](<failure-5.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-7.png](<failure-7.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-8.png](<failure-8.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |
| [failure-9.png](<failure-9.png>) | 历史失败现场截图，序号对应当时运行，不代表当前results仍失败 |

## 怎么使用

从前端根目录先启动Mock服务，再执行node tests/vue-flows.e2e.cjs；默认5180，可设PROTOTYPE_URL。FLOW_FILTER用|分隔片段，定向执行会覆盖results.json，不能当完整22组证明。

## 维护与检查

查看results的实际time、checks数量、passed/failed/errors/consoleErrors。旧failure图片不会因后来成功自动删除，应按运行时间解释。 返回[上级入口](<../README.md>)。
