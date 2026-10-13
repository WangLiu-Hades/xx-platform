# 项目审查与工程任务交接

本目录是2026-10-10针对现有原型、未决事项、工程师边界和后续开发计划的核对结果。业务要求以用户指定的《平台功能设计（原始）.pdf》为准；本轮不替业务/安全/算法负责人签认未知规则。

## 文件与目录

| 交付物 | 用途 |
| --- | --- |
| [待确认事项定位与责任](<PENDING_REGISTER.md>) | P-016在哪、21项主清单和关闭证据 |
| [前端接口交接](<FRONTEND_API_HANDOFF.md>) | 193操作/127运行时引用/66仅契约，调用链和后端/数据交接 |
| [工程师任务修订版](<ENGINEERING_ASSIGNMENTS.md>) | 四角色、15模块、10个交接及首批任务 |
| [多维数据分析平台开发模块与人员分工（修订版）.docx](<多维数据分析平台开发模块与人员分工（修订版）.docx>) | 可编辑Word；在原分工副本中修正文案并补充执行边界 |
| [进度与开发计划](<DEVELOPMENT_PLAN.md>) | 真实完成度、12工作包、人日、阶段和首轮两周任务 |
| [全目录审查与结果](<PROJECT_AUDIT.md>) | 审查覆盖、修复、验证和仍缺的输入 |
| [依赖漏洞补检](<DEPENDENCY_AUDIT.md>) | 用户授权后的公共npm registry检查、当前结果和可追溯证据 |
| [本轮修改清单](<MODIFICATIONS.md>) | 新增交付、已修改源码/设计文件及保留原件 |
| [API逐项CSV](<audit/api-inventory.csv>) / [JSON](<audit/api-inventory.json>) | 每个接口的方法/路径、参数/Schema、代码引用、主责和pending |
| [全文件清单](<audit/workspace-files.jsonl>) / [汇总](<audit/workspace-inventory-summary.json>) | 初始快照22,313文件的分类、大小、时间和SHA256；本轮交付另做最终验证 |
| [README_REVIEW.md](<README_REVIEW.md>) | 本次关键目录README覆盖、修正和检查结果 |
| [scripts/](<scripts/README.md>) | 清点、接口盘点、交付生成、Word渲染、依赖及README检查脚本 |
| [audit/](<audit/README.md>) | 原始证据、初始快照、报告、Word逐页图及交付哈希；每类文件说明见子目录 |

现行工程实现边界以本目录修订版为入口；历史审计、旧原型、OpenAPI副本和合同修订资料保留为证据，不用于推断当前生产完成度。原始PDF和原分工DOCX未覆盖修改。

## 怎么使用

先读ENGINEERING_ASSIGNMENTS领任务，再读DEVELOPMENT_PLAN的依赖/阶段门禁；前后端对接看FRONTEND_API_HANDOFF及CSV/JSON。遇P-016等问题先查PENDING_REGISTER的主账本和关闭责任，不能由开发人员自行编造阈值。PROJECT_AUDIT记录当前完成范围，MODIFICATIONS记录上一轮工程审查修改；本次目录文档的修改另记README_REVIEW。

## 维护与检查

通常只阅读本目录即可。需要复跑时先读scripts/README的输入、覆盖行为和本机路径限制；build_governance会覆盖部分交接文档，不能当只读校验。audit/checks.json的通过范围限于工程交接及已有前端证据，README检查单独记录，不等于生产验收。返回[文档入口](<../../archive/design-project-root/docs/README.md>)。
