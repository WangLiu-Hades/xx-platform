# #5 与主分支、修复后 #3 的修复及验证

日期：2026-11-03。原 #5：`ca18806f7c21031c460dfe3eb684488d7aa2ad30`；修复后 #3：`7e5f131704c784fc61657d8c2f5999bc83e44d44`；唯一核心规则来源：远程main `aca438ebfb218d40350b60ff933d2251bed50500` 的Git原件。最终修复提交号、合并关系及交付校验值在提交后生成的外置交付版本文件中记录。

## 结论与交付范围

本轮完成本地组合适配修复。7项已确认代码/测试/文档问题已修复，组合后后端785项、前端296项测试通过，均无失败、错误或跳过；原始数据库结构一致，实际JAR启动及HTTP检查通过。没有改写主分支OpenAPI、数据库字典/DDL、原始需求、核心治理或系统设计原件。发布仍应按最终远程头和仓库保护规则验收。

P01—P05为尚待真实接入的业务端口，本轮保留规则规定的不可用状态，不能记成已经实现。此交付证明阶段性代码兼容，不声称198个接口、实际厂商数据、完整领域事务或生产系统已全部交付。实际未就绪能力继续在client-config.features中保持禁用。

## 规则来源和保护

[主分支入口](https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/docs/START_HERE.md)、[原始功能设计](https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/docs/requirements/source/平台功能设计（原始）.pdf)、[机器接口](https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/contracts/api/openapi.yaml)、[API约定](https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/contracts/api/api-conventions.md)、[数据库入口](https://github.com/WangLiu-Hades/xx-platform/tree/aca438ebfb218d40350b60ff933d2251bed50500/contracts/database)、[分工与协议](https://github.com/WangLiu-Hades/xx-platform/tree/aca438ebfb218d40350b60ff933d2251bed50500/docs/project-governance)。

逐一核对155份规则和入口，Git blob均与main一致，见[清单](main-rule-baseline.json)。`contracts/`、`docs/project-governance/`、`docs/system-design/`完整目录零差异，无新增DDL或迁移。API README为当前使用说明，旧阶段README仅作为[历史记录](history/api-readme-before-compatibility-fix.md)，不充当核心规则。

## 问题处理

| 编号 | 问题 | 状态 | 修改及边界 |
| --- | --- | --- | --- |
| M01 | 数据库启动及测试配置 | 已修复 | 合入修复后 #3，六类HTTP套件共享隔离真库配置，实际登录并校验/me；完整测试通过。 |
| M02 | dev-check合并冲突 | 已修复 | 保留dev,db及health/ready，统一生成/完整验收入口，缺少测试库输入时先报错。 |
| M03 | 8处旧构造方法 | 已修复 | 全部改为RequestIdFilter(false)，没有改变公共实现的安全默认值。 |
| M04 | 18项假CSRF HTTP测试 | 已修复 | 改为真实数据库账号、真实HTTP登录、SID和登录轮换后绑定的CSRF，保留Filter并新增错误token拒绝用例。 |
| M05 | ACTIVE文件无到期时间仍可引用 | 已修复 | 接入及回放均拒绝空到期时间；新增两个回归，既有到期边界和有效文件用例保留。 |
| M06 | 详情及错误行缩写UUID | 已修复 | 两个入口复用严格UUID检查；新增两个路径负例，合法UUID及隐藏对象404用例继续通过。 |
| M07 | 构建与启动说明失配 | 已修复 | 生成profile默认激活并实跑无显式profile的生成；当前README/环境示例统一，旧说明保留为历史。 |
| G01 | 组合CI缺少真实数据库 | 本地等效检查通过，远程CI待上传后运行 | 工作流提供PostgreSQL/PostGIS及XX_TASK_IT_DB_*并保留Surefire；本地实际完整785项通过，未声称远程CI已变绿。 |
| P01 | SharedAccess真实身份/范围适配 | 保持未绑定、按规则不可用 | 本轮不制造生产适配Bean；真实SID/CSRF已进入测试装配，测试替身仍只证明编排边界。 |
| P02 | AtomicSubmission真实领域事务 | 保持未绑定、按规则不可用 | 需要真实批次、公共任务、幂等回执、审批、审计的同事务实现后方可启用。 |
| P03 | 回放/补数真实资源与审批消费 | 保持未绑定、按规则不可用 | 保留 #3 缺少完整领域解析时503的保护，不以UUID格式代替批准。 |
| P04 | 真实数据、查询窗口及目标LOOKUP | 保持未绑定、按规则不可用 | 没有真实服务/资源绑定时不编造传输地址、身份或查询结果。 |
| P05 | 完整Context和真实导出执行 | 保持未绑定、按规则不可用 | 不把已注册的来源Bean宣称成真实导出闭环，沿用 #3 文件和下载许可边界。 |

对应依据：M01/M03/M07/G01遵循API约定第9节可重复生成与真实验收；构造、构建、合并错误直接来自实际编译/合并日志，不冒充PDF指定了Java写法。M02遵循既有运行底座及接口约定。M04遵循API约定第4节SID/CSRF/可信Origin。M05遵循保存策略、P-003-03/P-005-01及ACTIVE文件引用约束。M06对应OpenAPI两条路径的UUID格式。P01—P05按双人分工、内部任务协议及敏感动作协议接入，资源或事务未就绪时不制造成功。

## 实际重新执行

| 检查 | 结果 |
| --- | --- |
| 主分支规则 | 155项原件一致，保护目录零差异 |
| 契约生成 | 按原始OpenAPI生成655个Java文件；显式-Pgenerate-api及默认激活入口均通过 |
| 全部生产/测试编译 | 655个生成源文件、140个生产源文件、65个测试源文件实际经Java17编译，生产类独立编译 |
| Maven Surefire完整组合测试 | 63套件、785项通过，0失败/0错误/0跳过，包含原 #3 平台回归及 #5 领域回归 |
| 前端 | 原契约重新生成结果与修复后 #3 的src/api无差异；8文件296项通过，类型检查及生产构建通过 |
| 真实数据库迁移一致性 | PostgreSQL16/PostGIS上执行V1—V6及schema.sql，165表/3033项结构指纹一致，比对创建的两个库已清理 |
| 可执行JAR | 打包成功，包含原始OpenAPI、不含测试类；实际随机端口启动 |
| 实际HTTP | health/csrf 200且trace一致，未知路径404；缩写UUID两条路径400；非法CSRF 403；合法CSRF但未绑定能力503，无批次或任务新增 |

实际完整用例索引见[test-cases.json](test-cases.json)，不是用注释或旧报告替代验证。新增4个到期/UUID回归场景及1个无效CSRF阻断场景；未删除或跳过原测试来取得通过。HTTP领域编排用例仍有明确的上游测试替身；真实登录、SID、CSRF和数据库不使用替身，真实领域数据事务仍按P项说明。

本机Windows沙箱限制标准javac关闭依赖JAR的ZipFS句柄。验证使用真实Java17 Compiler API编译全部源文件，由进程退出释放句柄；临时POM只增加build.directory。先实际运行契约生成，全部编译后由Maven verify跳过重复生成（codegen.skip），继续执行资源、真实Surefire及可执行打包，退出码0，未跳过测试。没有宣称标准 `mvn clean verify` 在本机直接一次完成。CI保留标准 `mvn -B -Pgenerate-api clean verify`，上传后需实际运行。

受测工作副本原始SHA256及原始日志、Surefire XML、JAR摘要随外部交付包提供；Git导出的交付源码采用原始对象字节，文本换行由.gitattributes定义，另有逐文件SHA256/Git blob清单，不能把工作副本CRLF差异当作内容变更。

## 后续启用与上传

1. 推荐先上传修复后 #3，再上传本分支；本分支的合并历史会保留两个父分支，不强推。最终提交同时包含main原件、修复后 #3 和原 #5。
2. 审批人、快照密钥、设备Profile及下载许可密钥仍由部署方提供，不设置默认生产密钥。旧会话按 #3 规则重新登录。
3. 未绑定的生产端口保持不可用；后续真实接入不得重建TaskStore、绕过当前授权、审批或下载许可，也不得以Mock证明业务闭环。
4. 远程规则或分支头变化时重新核对；本地检查、提交和交付不代表已经远程推送、CI成功或合入main。
