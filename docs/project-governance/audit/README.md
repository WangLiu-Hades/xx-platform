# 工程审查证据与文件清单

本目录是项目审查的证据存储区，不是前端部署目录。先读上一级PROJECT_AUDIT与README_REVIEW，再按文件查看来源、日期和范围。

## 文件与目录

| 文件或目录 | 功能 |
| --- | --- |
| [api-inventory.csv](<api-inventory.csv>) | 193项API的方法/路径/责任/引用状态简表，UTF-8 BOM，供筛选分发 |
| [api-inventory.json](<api-inventory.json>) | 同一盘点的完整操作、参数/Schema、pending、源码引用行与统计 |
| [contract-only-operations.md](<contract-only-operations.md>) | 66项仅契约操作列表，供确定调用方与排期，不能等同缺66个按钮 |
| [work-packages.json](<work-packages.json>) | 12工作包、四角色人日范围与合计，供排期文档核算 |
| [workspace-files.jsonl](<workspace-files.jsonl>) | 初始22,313文件快照，每行路径、分类、大小、时间及SHA256；不是当前总数 |
| [workspace-inventory-summary.json](<workspace-inventory-summary.json>) | 初始快照分类、读取/JSON错误、外部来源和检查方法汇总 |
| `workspace-docx-text.json`（本机保留，Git排除） | 自有Word提取文本，可能含合同等原始信息；仅供受控本地核对 |
| `external-docx-0.txt`（本机保留，Git排除） | 外部原人员分工DOCX文字提取，供修订对照，不是现行执行分工 |
| `first-party-text.txt`（本机保留，Git排除） | 自有文本汇编，含设计/源码/参考文本，供搜索而非运行，分发前核内容范围 |
| [unit-current.json](<unit-current.json>) | 2026-10-10实际单测结果283通过；README编写不会重跑它 |
| [dependency-current.json](<dependency-current.json>) | 用户授权后的npm原始漏洞响应，2026-10-10检查0项已知漏洞 |
| [dependency-current-run.json](<dependency-current-run.json>) | 联网审计的授权来源、UTC时间、命令、版本、退出码、输入与结果哈希 |
| [dependency-current.stderr.txt](<dependency-current.stderr.txt>) | 本次npm标准错误输出，当前为空 |
| [dependency-pre-authorization.json](<dependency-pre-authorization.json>) | 授权前DNS失败记录，不能解读为漏洞为0 |
| [checks.json](<checks.json>) | 工程交接/既有前端证据的可重复门禁核对结果，不是生产验收 |
| [output-manifest.json](<output-manifest.json>) | 治理交付物路径、大小和SHA256；不包含自身及部分大体积/原文提取物 |
| [word-visual-review.json](<word-visual-review.json>) | 修订Word及最终PDF哈希、19页逐页视觉核查和范围 |
| [assignment-reviewed.pdf](<assignment-reviewed.pdf>) | 当前通过逐页核查的Word渲染PDF，与word-visual-review哈希对应 |
| [word-render](<word-render>) | 上述当前PDF的逐页PNG与阅读说明，供视觉QA |
| <code>render-temp/</code>（本机空临时目录，Git不保存空目录） | 渲染中间/临时目录，不是正式Word或最终PDF入口 |
| [readme-source-baseline.json](<readme-source-baseline.json>) | 本轮README修改前已有非README文件的哈希，用于核对业务/契约未改 |
| [readme-catalog.json](<readme-catalog.json>) | 本轮关键目录与逐文件用途登记、生成类目录范围，供README覆盖检查 |
| [readme-checks.json](<readme-checks.json>) | 本轮README覆盖、链接、命令引用、源码保护及说明哈希的检查结果 |
| [assignment-review.pdf](<assignment-review.pdf>) | Word排版迭代的历史PDF；即使名字含final也不是当前assignment-reviewed.pdf |
| [assignment-review-v2.pdf](<assignment-review-v2.pdf>) | Word排版迭代的历史PDF；即使名字含final也不是当前assignment-reviewed.pdf |
| [assignment-review-v3.pdf](<assignment-review-v3.pdf>) | Word排版迭代的历史PDF；即使名字含final也不是当前assignment-reviewed.pdf |
| [assignment-review-v4.pdf](<assignment-review-v4.pdf>) | Word排版迭代的历史PDF；即使名字含final也不是当前assignment-reviewed.pdf |
| [assignment-review-final.pdf](<assignment-review-final.pdf>) | Word排版迭代的历史PDF；即使名字含final也不是当前assignment-reviewed.pdf |

## 怎么使用

用编辑器打开JSON/CSV/Markdown，PDF及word-render用于版面核对。复跑脚本入口在../scripts/README.md；脚本可能覆盖同名快照/报告，先确认输入和历史保留要求。文档检查不会重新执行283项业务测试。

## 维护与检查

依赖审计需同时核run记录和当前锁文件哈希；测试看真实执行时间。旧PDF、旧failure截图与初始快照不能假冒当前结果。原文提取资料不应放入公开静态站点包。 返回[上级入口](<../README.md>)。
