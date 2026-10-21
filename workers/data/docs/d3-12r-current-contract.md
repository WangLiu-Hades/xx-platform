# D3-12R：当前契约兼容与165表复验

固定输入：main `aca438ebfb218d40350b60ff933d2251bed50500`，PD-2026-10-19.v1，OpenAPI `1.0.0-dev.4-provisional`，198操作/623 Schema/165表。此阶段继续使用合成PLATE，正式H04、审批服务、真实SourceProfile和DEV-05独立复验分别记录。

## 基线与兼容性结论

新增[当前契约基线](../fixtures/contract-pd-2026-10-19-v1.json)，同时锁定权威OpenAPI、完整schema及生成的DTO镜像。包检查、本地套件和PG入口均先核对这三份文件，拒绝旧schema、API漂移、陈旧镜像及缺失输入。[历史基线](../fixtures/contract-baseline.json)和原样本保持冻结，不把旧验收摘要改成新值。

原163份CREATE TABLE定义未改；新增operation.action_approval及audit.action_approval_decision。另有decryption_request/decryption_result_grant的审批列、可空文件引用、约束及索引变化，不能用“旧表定义未改”推断没有ALTER。采集事实与任务存储结构仍兼容原PLATE/M03执行流程。完整schema中的feature_config仍保留PD-2026-10-18.v1种子标签，12项功能均关闭；本包不改权威DDL或擅自启用能力。

现行M03的7项操作及既有数据DTO保持结构兼容，replayIngestionBatch和createIngestionBackfill增加X-Approval-Id条件头。OpenAPI将其标为可选，并不免除风险规则命中时的审批；后端必须校验冻结输入、当前权限并在受控事务消费。Worker的合成REPLAY测试不是正式审批链路验收。

## 本次行为变化

- 真实车牌解析服务尚未接入、未知来源错误没有公开映射时，返回内部SYSTEM_UNAVAILABLE，避免把已给暂定设计的能力缺失写成REQUIREMENT_PENDING。正式HTTP错误映射由DEV-02集成。
- 服务端仍需显式传入预算，并且不得超过普通查询31天、SQL5秒、游标/会话15分钟、导入100MiB/10万行的当前上限。一年保存不扩大查询窗口，也不证明这些上限的实际容量。
- 独立DTO校验器补minLength，回放理由不足10字符被回归识别；不改权威Schema或冻结预期。
- PG先核对165张表的完整名称集合，再跑原30项和新增4项存储用例。新增用例检查审批约束/FK、唯一票据与追加触发器、解密ALTER元数据以及未实现功能保持关闭。合成密文字节不构成加密或审批授权测试，探针行仅在隔离库事务内写入并回滚。

## 执行与证据

从完整的对应仓库或备齐固定契约的集成副本运行：

~~~powershell
python workers/data/run_d3_12r.py
~~~

可传`--postgres-bin`指定已安装PG/PostGIS的bin目录；沿用D3_ARTIFACT_ROOT指定输出根。入口尝试独立PG实例，随后执行128项本地测试和3个Worker CLI，再导出`validation/d3-12r-*`并执行静态与强制验收检查。不操作已有数据库服务，不改系统配置。PG创建/停止遵守原runner的所有权标记。

退出0只表示本地、当前165表PG及对应包检查都实际通过。明确缺少运行环境或显式跳过PG、且其他检查均通过时，状态PENDING_POSTGRES、退出2；实际执行失败、证据不匹配/未生成新报告、或另有包门禁失败时为FAIL、退出2。`--local-only`明确跳过PG，同样不能取得整体验收PASS。原生工具失败保留NOT_RUN及实际错误码，不合成PG结果。

当前结果以[验证记录](../validation/README.md)和输出根`d3-12r/latest.json`为准。源文件摘要和contractBaseline必须同时对应；原103/30及163表报告不能满足当前门禁。原验收族m03/identity-v2保留，当前verify_package禁止向这两个族写报告。共享记录只含适合交付的摘要/成功日志，原始失败与环境信息保留在本地.artifacts。

## 后续边界

F04的旧基线选择与163表断言由本次实现处理；“实际新PG已通过”仍取决于运行证据。F05报表包含结束日与DB排他上界、F06未知采集量非空DTO、F07正式取消确认/reaper签名继续待服务/契约负责人收口。本次没有实现报表、设备计数、真实回放审批或新TaskStore。

D3-13仍依赖DEV-01的任务/授权/迁移与同事务publisher、DEV-02的M03公开API及查询编排；DEV-04负责数据，DEV-05负责独立验收。用户要求的子agent审计是本次代码与证据复核，不替代DEV-05测试或真实PG。
