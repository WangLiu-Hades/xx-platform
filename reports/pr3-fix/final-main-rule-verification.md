# PR #3 主分支规则修复与复核

日期：2026-11-02。仓库：`WangLiu-Hades/xx-platform`。分支：`codex/dev01-heyifan-api-skeleton`。

本轮已修复复核中的 R01—R14，并完成下述验证。合并规则基线固定为远程主分支 `aca438ebfb218d40350b60ff933d2251bed50500`，修改前 PR 头为 `c1feeeae79ab7258295385aa72cacf3d4676f592`。本记录不替代核心规则，也不把分支旧报告作为开发依据。

本地修复可以进入合并步骤；远程写入尚受 GitHub 认证阻塞。本页随修复提交保存，最终提交号、实际合并和推送结果记录在交付文件 `PR3_修复与合并结果_20261102.md`，不能仅凭本页认定远程已经合并。

## 规则来源及保护

- [主分支规则入口][rules]、[原始平台功能设计][pdf]、[系统设计][design]、[OpenAPI][oas]、[API约定][api]、[数据库定义及数据字典][db]均使用上述主分支提交的原件。
- 本轮逐一比对155份规则及入口文件，Git blob 与主分支全部一致，详见 [main-rule-baseline.json](main-rule-baseline.json)。清单保留主分支历史资料以便追溯；条款优先级和当前生效规则仍按主分支入口及 PD-2026-10-19.v1 确定。
- 撤销 PR 对 OpenAPI、迁移说明和核心治理记录的改写；没有通过改规则迁就实现。`contracts/`、`docs/project-governance/`、`docs/system-design/` 相对该主分支零差异，没有新增或修改数据库迁移。
- 原始 OpenAPI 被打包为后端资源，审批请求直接按其中的 Schema 校验；前端生成文件也由该原件重新生成。原件中的历史文字差异保持原状，幂等业务错误码遵循 API约定及错误码定义的 `IDEMPOTENCY_CONFLICT`。

## 问题修复、依据和回归证据

下表均为已完成的代码修复。测试类的全部用例名称与实际结果见 [test-cases-final.json](test-cases-final.json)，不是只凭代码注释判定通过。

| 编号 | 原问题与本次处理（简单说明） | 主分支原始依据 | 实际回归证据 |
| --- | --- | --- | --- |
| R01 | 以前长浏览器标识会被截断。现在完整 UA 与 IP 共同计算摘要并校验；缺少完整绑定的旧会话需重新登录，改变末尾字符也会被拒绝。 | [暂定规则 P-001-03][rules]，会话绑定与失效要求 | `AuthSessionIntegrationTests.longUserAgentRemainsBoundAfterLoginAndSuffixChangesAreRejected`；`legacySessionWithoutCompleteBindingRequiresNewLogin` |
| R02 | 以前审批人配置为空仍可能继续审批，单台密钥修改也可能只需一票。现在必须配置实际审批人，且具备当前权限、范围并独立于申请人和执行人；密钥类参数需要两票。设备参数类别来自明确设备配置及启用、可写的参数定义，密钥值在复核清单中掩码；输入不齐时不可用。 | [审批协议“判定及闭环”][sensitive]；[P-019-01、P-019-02][rules] | `ApprovalExportIntegrationTests.missingConfigurationAndOutOfScopeReviewersDisableCreation`；`oneDeviceKeyChangeNeedsTwoIndependentVotesAndNeverDisplaysTheKey` |
| R03 | 以前审批权限可能被当成查看所有审批的通行证。现在列表和详情都检查目标的当前范围；无权看到的单个审批返回404，分页只计算可见对象。 | [OpenAPI Approvals][oas]；[API约定第4节][api] | `ApprovalExportIntegrationTests.outOfScopeReviewerCannotReadDetailsOrListTheApproval` |
| R04 | 以前部分审批请求的字段、数组和版本格式与契约不一致。现在30种审批提案直接使用主分支 OpenAPI 的完整嵌套规则；角色必填字段不能漏，参数修改是规定的数组，If-Match 必须带双引号。管理员重置使用 newPassword 和 reason，不收 currentPassword。 | [OpenAPI ActionApprovalProposal、RoleInput、PasswordChangeInput][oas]；[API约定第4、5节][api] | `ApprovalProposalsTests` 中 `everyApprovalOperationHasAProposalSpec`、`roleProposalCannotOmitAnyRequiredInputField`、`adminPasswordResetUsesReasonAndNewPasswordWithoutCurrentPassword`、`parameterChangesAreTypedArraysAndIfMatchIsAQuotedVersion` |
| R05 | 以前审批可能批准超出授权人的角色、菜单或数据范围。现在逐项检查授予上限，保留类型与场所的授权元组，不能组合成更大范围；本人不能给自己加权、自禁用或自删，最后有效管理员受保护。仅本人数据的权限不能直接当成向另一用户授权的上限。 | [P-002-02、P-002-04、P-021-02][rules]；[审批协议][sensitive] | `AuthorizationBoundsTests.authoritativeTypedGrantsAreNotExpandedIntoAProjectionCrossProduct`；`ApprovalExportIntegrationTests.grantApplicationCannotExceedTheGrantorsCeiling` |
| R06 | 以前只填合法格式的 ID 就可能生成“已核实”审批。现在读取真实 Context、用户、角色、设备和分组，并核实存在性、版本、归属、成员及参数定义。没有接入真实资源或完整事务的操作返回不可用，不生成虚假批准。 | [审批协议资源冻结与事务要求][sensitive]；[API约定第4节能力目录][api] | `ApprovalExportIntegrationTests.nonexistentAndUnconnectedTargetsNeverProduceVerifiedApplications` |
| R07 | 以前批准后资源、授权或分组变了仍可能执行。现在消费时重新解析完整目标，复核申请人、执行人和原投票人的当前资格、策略及范围；版本或目标变化即拒绝。审批消费、资源及授权锁在事务内处理，失败不留下已消费状态。 | [审批协议冻结、重校、事务消费条款][sensitive]；[P-019-02、P-019-03][rules] | `ApprovalExportIntegrationTests.changedGroupMembershipInvalidatesTheFrozenApproval`；`changedAuthorizationAndRemovedReviewerCannotConsumeApproval` |
| R08 | 以前同一个幂等键并发请求可能先产生多份申请。现在先对“账号+操作+键”取得事务锁，再检查记录和创建对象；相同输入只产生一份，不同输入返回409。 | [API约定第5节][api]；[审批协议][sensitive] | `ApprovalExportIntegrationTests.concurrentIdenticalApprovalKeysCreateExactlyOneApplication`（8个并发请求）；原有异内容键冲突用例 |
| R09 | 以前旧幂等响应可能绕过缩权或当前密码检查。现在回放前也重新鉴权，审批决定回放必须验证本次提交的当前密码；密码及其摘要不进入业务幂等指纹，失败仍审计。 | [API约定第5节“所有幂等回放仍重校权限”][api]；[审批协议][sensitive] | `ApprovalExportIntegrationTests.approvalReplayRechecksCurrentPermission`；审批决定幂等用例增加错误密码拒绝及单票断言 |
| R10 | 以前使用契约不存在的 task:run 权限，或把 task:manage 当成所有业务许可。现在按任务的真实业务和冻结 operationId 校验既有权限；采集导出和审计导出分别使用自己的业务权限。 | [P-002-01固定权限目录][rules]；[OpenAPI各业务 x-permission][oas] | `ApprovalExportIntegrationTests.exportWorksWithBusinessPermissionWithoutTaskManagement`；`TaskPublishGuardIntegrationTests.exportsUseTheirOwnDomainPermissionRatherThanCapturePermissionForEveryExport` |
| R11 | 以前只比较版本号，不能证明当前仍有数据范围。现在提交、发布、审批、文件都重新计算当前授权元组并检查完整 ContextFilter、真实设备和显式采集记录；无效快照不能跳过。不能用 Context 创建者身份证明底层采集记录都属于本人，缺少来源归属解析时拒绝。 | [P-002-02、P-002-04、P-009-01][rules]；[API约定第3、4节][api] | `TaskPublishGuardIntegrationTests.matchingVersionsDoNotOverrideTheCurrentTypedScope`；`contextScopeNeverMultipliesTypesAndSitesAcrossRoles` |
| R12 | 以前排队额度漏算 PENDING，领取时还可能错误阻塞有空位的任务类别。现在 PENDING 与 QUEUED 一并计入每人5个等待任务；全局锁内逐个领取并重新计算本人、重型、计算、I/O及保留额度。 | [P-003-02][rules]；[内部任务协议][task] | `TaskQuotaIntegrationTests.pendingTasksCountTowardTheFiveWaitingTaskLimit`、`threeQueuedHeavyTasksStillAllowTheFirstTwoToBeClaimed`、`fullComputeBudgetDoesNotBlockAnAvailableNotificationSlot`，以及原有8线程并发额度用例 |
| R13 | 以前拥有文件权限就可能长期访问已经失去来源权限的文件。现在校验真实发布任务和导出来源、当前业务权限及范围，并要求最多5分钟、绑定用户/会话/文件/授权状态的签名许可。现有文件详情接口发放 HttpOnly Cookie，前端先取详情再下载；来源无法核实时不可用。上传状态查询按原契约保持 AUTHENTICATED 和对象可见性，不额外要求下载权限；缺少下载密钥也不阻断状态查询，实际下载不可用。 | [OpenAPI getFileAsset、downloadFile][oas]；[P-009-03][rules]；[API约定第4节][api] | `DownloadPermitsTests` 的6项许可测试；`FileHttpIntegrationTests.downloadRequiresItsOwnShortLivedPermit`、`authenticatedOwnerCanPollAnUploadWithoutDownloadPermissions`、`missingDownloadKeyKeepsMetadataAvailableAndDownloadUnavailable`；`ApprovalExportIntegrationTests.publishedExportFileRequiresPermitAndCurrentSourcePermission`（实际发布、字节下载、缩权及撤权拒绝） |
| R14 | 以前 ACTIVE 文件到期时间为空可能形成永久文件。保留原主分支 V5 回填与新文件到期逻辑，并补上元数据、下载和发布时的拒绝检查；未完成处理的临时文件仍按原状态规则处理。 | [保存策略][retention]；[P-003-03][rules] | `FileHttpIntegrationTests.activeFileWithoutExpiryIsNeverPermanentlyDownloadable`；`TaskPublishGuardIntegrationTests.activeFileWithoutAnExpiryCannotBePublished`；既有自然月保存测试 |

## 实际验证结果

| 验证 | 结果 | 原始证据 |
| --- | --- | --- |
| Java17全部源文件编译 | 110个生产源文件、43个测试源文件完成编译，生产类独立编译 | [生产编译日志](logs/final-main-rule-check/maven-main-compile.log)、[测试编译日志](logs/final-main-rule-check/maven-test-compile.log) |
| Maven Surefire后端测试 | 42个测试套件，425项通过，失败0、错误0、跳过0；使用隔离的 PostgreSQL16/PostGIS 真实数据库并执行原始 Flyway 迁移 | [测试日志](logs/final-main-rule-check/maven-verify.log)、[用例清单](test-cases-final.json) |
| 前端测试 | 8个测试文件，296项通过 | [前端测试日志](logs/final-main-rule-check/frontend-tests.log) |
| 前端类型检查及生产构建 | 通过 | [构建日志](logs/final-main-rule-check/frontend-build.log) |
| 迁移与 schema.sql 一致性 | 165张表、3033项结构指纹一致；只创建并清理本次所属的隔离数据库 | [迁移比对日志](logs/final-main-rule-check/migration-parity.log) |
| 可执行后端打包 | 成功；包含原始 OpenAPI，不含测试类；SHA256 为 `51ce0ee978800b1060cbc32450a4368456dc59412596244437396b07c882871d` | [打包日志](logs/final-main-rule-check/maven-package.log)、[结果摘要](final-verification.json) |
| 打包后启动检查 | 使用另一隔离数据库启动实际 JAR；health及csrf均200，trace匹配，csrf Cookie正常；本次启动的进程已停止 | [启动检查](logs/final-main-rule-check/jar-smoke.json) |

本机 Windows 沙箱限制导致标准 javac 在关闭依赖 JAR 的 ZipFS 文件管理器时出现 AccessDenied，且旧 target 清理受限。本次使用新的私有构建目录，临时 POM 仅增加 build.directory；生产及测试全部实际经过 Java17 编译，不关闭该受限文件管理器而由进程退出释放句柄，再由 Maven 执行实际 Surefire 测试。首次 verify 的425项测试全部通过后，仅打包阶段因离线缓存缺插件失败；联网获取官方 Maven Central 插件后单独重试打包成功，该次重试不重复测试。因此本报告没有把标准 `mvn clean verify` 声称为一次完整成功。

完整修复工作副本、构建脚本、XML结果和数据库运行证据保存在交付目录；机器摘要同时记录受测源文件 SHA256，见 [final-verification.json](final-verification.json)。旧报告、旧测试统计和旧规则采集脚本均保留为历史证据，不作为本轮规则或复核结论。

## 部署输入与能力边界

- 配置独立审批人以及审批快照加密密钥 `XX_APPROVAL_PAYLOAD_KEY`；设备审批还需明确的 `xx.approval.device-profiles` 和真实参数定义。配置缺失时不会降级为自动批准。
- 下载许可需 `XX_FILE_DOWNLOAD_PERMIT_KEY`，值为至少32字节随机密钥的 Base64；多实例共享同一部署密钥，不在仓库设置默认生产密钥。浏览器已接入既有元数据及下载接口，JSON契约没有新增字段。
- 尚未接入的解密 recipient/grant 完整事务、命令重试/修订的失败目标解析、其他领域的资源解析及真实设备外部执行保持不可用。本轮测试证明已实现代码的保护边界，不代表198个接口、实际外部设备、完整数据算法或生产验收已交付。实际能力仍依主分支规则及 `client-config.features` 的真实接入状态配置。
- 来源授权版本变化后，对无法证明原结果完整范围的旧导出文件采取拒绝访问；不会把缩权后的当前查询结果冒充旧文件的内容范围。需要重新发起符合当前权限的业务流程。
- Git文本合并无冲突、核心规则无差异及测试通过，均需与最终远程头一致才可沿用。若主分支或 PR 被他人更新，先重新比对新主分支规则，再合并；不强推，不跳过受保护分支要求。

[rules]: https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/docs/project-governance/暂定规则明细.md
[pdf]: https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/docs/requirements/source/平台功能设计（原始）.pdf
[design]: https://github.com/WangLiu-Hades/xx-platform/tree/aca438ebfb218d40350b60ff933d2251bed50500/docs/system-design
[oas]: https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/contracts/api/openapi.yaml
[api]: https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/contracts/api/api-conventions.md
[db]: https://github.com/WangLiu-Hades/xx-platform/tree/aca438ebfb218d40350b60ff933d2251bed50500/contracts/database
[sensitive]: https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md
[task]: https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/docs/project-governance/INTERNAL_TASK_PROTOCOL.md
[retention]: https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/docs/project-governance/RETENTION_POLICY.md
