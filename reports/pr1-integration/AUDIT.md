> 2026-10-19后续状态：F08已由用户决定统一保存一年，并同步[当前保存策略](../../docs/project-governance/RETENTION_POLICY.md)。下文为原合并检查快照，F08原文保留追踪，其余问题未在本次修复。

# PR #1 合并后主分支全面检查

检查日期：2026-10-19。检查对象：本地主分支合并提交 `2e72d846d8b9448674c97f71f74ed29611e30f36`，父提交为原main `8e06945` 与PR分支 `d42d6f1`。远程推送状态单独记录，不把本地合并当远程已更新。

## 已完成的整合

- 保留主分支OpenAPI `1.0.0-dev.3-provisional`，198操作/623 Schema；OpenAPI、数据库schema、审批迁移和原始PDF与原main提交字节一致。
- 主分支暂定规则、甲方待确认及真实资源启用条件不变；policy仅更新后端分工字段。
- PR后端甲映射DEV-01（118操作），乙映射DEV-02（80操作）；原193操作逐条模块/方法/路径不变，新增5个审批/受控解密操作归DEV-01。
- 同步入口、角色、policy、交接、Worker说明和机器清单，公共TaskStore和迁移只由DEV-01集成，DEV-02经接口协作。
- 用户指定的API指南原句逐字保留，单独标记下次处理。

## 检查范围与结果

逐文件清点666个已提交文件（551个文本、115个二进制），解析JSON/YAML/XML及Python语法，提取PDF/DOCX文字，核对架构、契约、角色、任务与结果发布、报表/指标及运行入口。图片、表格和历史审计作为证据清点，不把哈希盘点声称为逐像素审核或生产验收。

- 全仓库结构/语法问题：0项，详见[全仓库检查JSON](full-audit-checks.json)及[文件清单](file-inventory.json)。
- 暂定契约检查通过：198操作、623 Schema、1894示例、2626引用，设计pending为0，甲方确认仍pending。
- 前端生成/类型检查/294项测试/构建通过；生成API源码与原main一致。
- 数据模块93项本地测试通过，3次CLI过程及冻结样本对账通过。
- 目录检查14项通过，检查当时129篇活动Markdown、843个相对链接均有效；最终新增报告另做链接核对。
- Java Maven验证未通过，诊断为当前沙箱对依赖jar的`java.nio.file.AccessDeniedException`，不能据此判定源码编译失败，也不能声称后端已验证。
- 独立PG复验未执行成功，`pg_ctl: could not create restricted token: error code 87`；未改动已有数据库。历史27项PG报告不替代本次验证。

## 仍存在的问题

### F01：后端未知字段处理与契约不一致（高）

[apps/api/src/main/java/com/xxplatform/api/config/WebConfiguration.java:30](../../apps/api/src/main/java/com/xxplatform/api/config/WebConfiguration.java#L30)忽略未知JSON字段；[contracts/api/api-conventions.md:14](../../contracts/api/api-conventions.md#L14)要求业务DTO关闭额外字段。生成Bean Validation注解不能自动保证未知属性拒绝，后续业务接口会继承这个差异。此次只同步文档，未改运行行为，需单独修复并验证请求边界。

### F02：CORS的生效环境、路径和审批头不明确（中）

[apps/api/src/main/java/com/xxplatform/api/config/WebConfiguration.java:46](../../apps/api/src/main/java/com/xxplatform/api/config/WebConfiguration.java#L46)注册`/api/v1/**`，而[apps/api/src/main/resources/application.yml:13](../../apps/api/src/main/resources/application.yml#L13)已经使用该上下文路径，MVC内部路径匹配需要按去掉context-path后的路径验证。名为development的CORS Bean没有dev profile约束；允许头也未包含主分支新增`X-Approval-Id`。同源代理不会暴露所有这些问题，直连跨域联调须补预检测试。

### F03：Windows Maven包装脚本可能把失败返回为成功（高，已复现）

[apps/api/mvnw.cmd:35](../../apps/api/mvnw.cmd#L35)在括号块内展开`%ERRORLEVEL%`，取得调用Maven前的值。隔离探针将Maven替换成退出37的stub，原包装脚本实际退出0，可能掩盖构建失败。证据见[退出码探针](wrapper-probe.json)。

### F04：Worker打包校验仍锁定旧schema摘要（高，已复现）

[workers/data/verify_package.py:50](../../workers/data/verify_package.py#L50)要求当前schema等于[workers/data/fixtures/contract-baseline.json:10](../../workers/data/fixtures/contract-baseline.json#L10)中的旧基线，主分支已加入审批结构。实际`verify_package.py`返回`Authority schema changed`。此次未改冻结基线、预期和历史报告来使检查变绿；应审查兼容性并建立对应现行schema的正式验证基线。

### F05：报表日期边界在API与数据库之间缺少明确映射（中）

[docs/project-governance/METRIC_ALGORITHM_RULES.md:7](../../docs/project-governance/METRIC_ALGORITHM_RULES.md#L7)使用包含结束周期的语义；[contracts/database/schema.sql:5250](../../contracts/database/schema.sql#L5250)要求`period_start < period_end`。单日报表的相同起止日期不能直接落库。需明确转为排他上界的服务层转换，或审查DDL修订；当前未见正式实现与验证。

### F06：未知采集计数的公开响应策略不完整（中）

[docs/project-governance/METRIC_ALGORITHM_RULES.md:26](../../docs/project-governance/METRIC_ALGORITHM_RULES.md#L26)要求未知指标为null/不可用；[contracts/api/openapi.yaml:30764](../../contracts/api/openapi.yaml#L30764)和[contracts/api/openapi.yaml:30789](../../contracts/api/openapi.yaml#L30789)将采集计数设为必填非空整数。不能补0，也不能把null直接塞进现有DTO；需明确整接口不可用、部分指标表示或契约演进策略。

### F07：公共任务取消确认与恢复接口还未收敛（中）

[docs/project-governance/INTERNAL_TASK_PROTOCOL.md:11](../../docs/project-governance/INTERNAL_TASK_PROTOCOL.md#L11)列出claim/heartbeat/requestCancel/finish/fail/retry，但运行中取消确认和过期租约reaper缺少同等明确的调用签名、幂等和事务结果约定；[workers/data/docs/worker-contract-v0.md:36](../../workers/data/docs/worker-contract-v0.md#L36)的v0草案列出acknowledgeCancel/recoverExpired。当前文档已注明主协议优先，但实现正式H04前仍需形成可共同验收的完整接口。

### F08：年度完整报表与一个月保存的业务要求仍冲突（业务待确认）

[docs/project-governance/暂定方案与修改记录.md:44](../../docs/project-governance/暂定方案与修改记录.md#L44)已经登记，当前方案按实际历史覆盖披露，不补零。该处理没有解决甲方是否必须提供完整年度历史的最终选择，需要确认保留策略或报表验收范围。

### F09：当前工期要求缺少匹配五人分工的资源排期（计划待复核）

[docs/project-governance/ENGINEERING_ASSIGNMENTS.md:17](../../docs/project-governance/ENGINEERING_ASSIGNMENTS.md#L17)保留60工作日及全部功能；[docs/project-governance/DEVELOPMENT_PLAN.md:5](../../docs/project-governance/DEVELOPMENT_PLAN.md#L5)仍保存旧四人情景的34–48周估算。文档已明确历史身份，不把它当现行承诺；尚缺按DEV-01/02拆分后的依赖、实际产能和发布验收窗口重估，无法从现有计划证明工期可行。

### F10：GitHub绿色检查不覆盖Java后端（验证缺口）

[.github/workflows/frontend.yml:5](../../.github/workflows/frontend.yml#L5)仅运行前端生成、类型、单测、构建和目录检查，未运行Maven或正式API/Worker联调。此次Java和PG验证又受本地环境限制，因此合并后仍需在支持的CI/共享环境补验后端构建、契约生成编译和真实数据库交接。

## 按用户要求暂缓

API速读指南中“不要在Java里重写用户/权限/系统CRUD”的原句保持不变。本轮未修正文案，已隔离标明不能覆盖现行契约和职责；不把它计入本次待修问题的处置。

## 架构判断

模块化单体API、独立Python数据Worker、共享数据库任务交接的方向一致；未发现本轮合并把系统改成多套公共API、重复权限或多个任务状态机。上述具体契约/配置/接口及验收问题仍存在，不能将目录合理或静态检查通过理解为正式平台可用。
