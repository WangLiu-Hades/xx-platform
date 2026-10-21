# 合成 PLATE 标识边界修复

> 2026-10-21主线整合：当前按PD-2026-10-19.v1执行，DEV-04数据、DEV-01任务/授权/迁移底座、DEV-02 M03 API、DEV-05独立复验。下述2026-10-17成功记录使用旧163表；本次保留受测Python源码及JSON报告原字节，日志按仓库属性转换为LF，合并主线文档，165表兼容仍待D3-12R。

2026-10-17：根据同事综合测试报告，优先修复 a74f38d 的确定性 ID 碰撞。原来的 source=A、recordId=B:C 与 source=A:B、recordId=C 都拼成 A:B:C，captureId 和 sourceEventId 因此相同。本机已在修改前复现。此修复只涉及合成适配器，不签认生产身份规则、H04 或 P-004/005。

当前契约复验入口已扩展为[run_d3_12r.py](../run_d3_12r.py)，使用独立基线和d3-12r证据族；下述103/30记录保留为当时163表验收。

## 编码与版本

仍使用 reference-data 中的 UUID namespace 和 seed。UUID5 的 name 为 str(seed) + ":" + label，具体 label：

| 输入 | label |
| --- | --- |
| source、recordId 均不含冒号 | kind + ":" + source + ":" + recordId，沿用已发布算法 |
| 任一字段含冒号 | "plate-id-v2:" + canonical([kind, source, recordId]) |

kind 仅由内部调用指定为 capture 或 source-event。canonical 是 UTF-8 JSON 数组，紧凑分隔、不转义非 ASCII 字符，JSON 自身处理引号、反斜杠和控制字符。不对键做大小写、Unicode 或空白归一化。

旧 label 以 capture: 或 source-event: 开头，新 label 以 plate-id-v2: 开头，两个域不重叠；数组保存完整字段边界。该修复消除拼接歧义，不声称 UUID 哈希绝无理论碰撞。普通 identity(label) 仍只接收调用者构造的内部标签，不用于拼接不定长 source/recordId。

两个字段任一含冒号都进入新域：只处理 source 中的冒号仍可能与旧库中已存的歧义 ID 冲突。冻结样本不含这类键，所有既有样本 ID 与预期字节保持不变。mappingVersion、dedupVersion 不变，因为自然键与同键冲突规则未改变；新算法通过 plate-id-v2 域及源码版本追踪，尚无新增公开版本字段。

## 已有数据与回放

| 已有状态 | 本次行为 |
| --- | --- |
| 无冒号的既有事实 | 生成 ID 不变 |
| 含冒号、按旧算法入库的事实 | 按 (source, recordId) 和 payloadDigest 查重；返回持久化的 captureId/sourceEventId，保留 firstBatchId/ingestedAt |
| 旧事实的另一组歧义输入尚未入库 | 使用 v2 独立 ID，可新增为另一事实 |
| 同一自然键、内容已变 | 继续拒绝 FIXTURE_KEY_CONFLICT，不覆盖旧事实 |
| 成功批次重试 | 返回已存结果；同版本新批次 REPLAY 不多写事实 |

SQLite pipeline 与 PG 写入均已有自然键查重逻辑，不需要重写现有主键或外键。调用方必须使用持久化结果中的 ID，不能用新版算法重算旧事实 ID。不要在新旧 Worker 间混跑或降级处理含冒号的输入；旧程序仍有碰撞缺陷。

本次未连接或修改任何已有生产数据库。无法从这份报告确认真实数据是否受损；唯一约束下的冲突可能导致批次回滚，不能据此宣称发生覆盖或丢失。上线前由数据负责人和 DEV-01 在受控环境盘点 source_code/source_record_key 含冒号的记录、关联事实及历史失败批次，核对当时 namespace/seed/版本和原文件。需要补处理的批次按批准流程重试；若发现孤立记录或外部缓存 ID，单独制定修复与迁移方案，不自动全库重编号。

可先执行只读候选数量检查，结果只说明需要核对的范围：

~~~sql
SELECT count(*) AS delimiter_candidates
FROM capture.source_event
WHERE position(':' in source_code) > 0
   OR position(':' in source_record_key) > 0;
~~~

## 回归与验收

- [10 项本地回归](../tests/test_identity.py)：报告正例、多处分隔、Unicode/转义、域隔离、冻结 ID、离线/PG 适配器一致、持久化重启回放、两种旧数据顺序、内容冲突。
- [3 项真实 PG 用例](../lab/identity_integration.py)：两条新事实分别入库并重连回放；旧 recordId 含冒号；旧 source 含冒号。后两项同时验证新对端入库和内容冲突隔离。
- PG runner 已注册 identity 组；完整套件应发现 103 项本地及 30 项 PG，实际通过情况见[验证记录](../validation/README.md)。新增 PG 探针最多增加 6 条事实，16 条冻结基线不变；库存以实际报告为准。

完成D3-12R契约兼容后，从仓库根复验；新证据使用独立前缀，避免覆盖历史identity-v2报告：

~~~powershell
python workers/data/run_stage1_local.py
python workers/data/run_pg_lab.py
python workers/data/run_stage1_local.py
python workers/data/verify_package.py --validation-prefix identity-v2-main
~~~

最后一条仅做包、语法、链接和冻结摘要检查，不代表 PG 验收。历史identity-v2-*、m03-*保持原样，新主线使用identity-v2-main-*；--require-validation 仅在当前源码对应的本地/PG共享报告均已收齐后使用。

## 对阶段计划的调整

D3-14 已收到针对 a74f38d 的报告级独立反馈：93 本地、27 PG 通过，另有本缺陷；报告原始日志位于同事环境，本轮未取得。先修复并复验 ID，再由同事对新源码独立复测。下一步先完成D3-12R新契约/165表复验。D3-13依赖DEV-01正式TaskStore/H04、文件/引用、授权与同事务publisher，以及DEV-02的M03领域API。当前由DEV-04负责数据、DEV-05负责独立复验，历史D3编号仅作任务追踪。S1/M03 继续 INCOMPLETE。
