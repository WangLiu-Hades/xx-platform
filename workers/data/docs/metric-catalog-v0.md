> 2026-10-19当前分工：DEV-04数据、DEV-03前端、DEV-05独立测试；DEV-01维护共享TaskStore/授权/迁移，DEV-02负责M03等领域公开API与查询编排。下文D3编号和v0协议为交付历史；业务与任务参数以[主分支PD方案](../../../docs/project-governance/暂定方案与修改记录.md)及[内部协议](../../../docs/project-governance/INTERNAL_TASK_PROTOCOL.md)为准，旧pending停做/实验租约数值不覆盖现行规则。

# D3-08：指标口径与验收目录草案

更新：2026-10-16。状态：工程计数已实现离线计算与对账；业务指标公式和八类报表仍待 P-006/P-020 签认。

机器清单：[metric-catalog-v0.json](metric-catalog-v0.json)。实现：[metrics.py](../data_worker/metrics.py)。复验入口：[run_stage1_local.py](../run_stage1_local.py)。正式来源：[API 约定](../../../contracts/api/api-conventions.md)、[DDL](../../../contracts/database/schema.sql)、[待确认清单](../../../docs/project-governance/PENDING_REGISTER.md)。

## 已实现的工程计数

这些指标只接收完整的合成 SQLite 账本导出，不接收查询当前页，也不直接提供公开 API。版本 d3-engineering-metrics-v0；不会写入平台 core.metric_definition 或将业务能力置为启用。

| 指标 | 本样本值 | 计算与边界 |
| --- | --- | --- |
| 处理行数 D3_PROCESSING_ROWS | 72 | 已提交批次逐行结果；batchId+物理行号唯一；跳过的空白行不计 |
| 接纳行数 D3_ACCEPTED_ROWS | 16 | 首次成功产生事实的 ACCEPTED 行 |
| 重复行数 D3_DUPLICATE_ROWS | 17 | 同源同稳定键且摘要相同；回放作为新批次记重复 |
| 拒绝行数 D3_REJECTED_ROWS | 39 | 输入校验失败或同键内容冲突；不占事实 |
| 事实库存 D3_FACT_INVENTORY | 16 | 当前完整快照中唯一 captureId；不是目标人数或车牌数 |
| 错误问题 D3_QUALITY_ERRORS | 39 | 当前合成协议每条拒绝行一个 ERROR；不是生产通用一对一规则 |
| 告警问题 D3_QUALITY_WARNINGS | 4 | 按行、按问题计数；未知设备/目标在回放结果中可再次告警 |
| 拒绝比例 D3_REJECTION_RATIO | 39/72≈54.17% | 输出0～1比值和分子/分母；空分母返回UNAVAILABLE/null |

本次完整账本满足：处理行数=接纳+重复+拒绝；事实数=接纳行数；质量问题=错误+告警=43。第二个等式仅因输入是“从空状态开始的完整合成 CAPTURE 账本”，不能对任意分页、存量库、DEVICE_STATUS/DEVICE_COUNTER 事件照搬。

首次批次34行，接纳14/重复1/拒绝19；回放34行，接纳0/重复15/拒绝19；增量3行，接纳1/重复1/拒绝1；另一来源1行，接纳1。相同 batchId 重试读取原结果，所有计数不再增加。按来源分组不会跨来源合并稳定键。

加法只对互不重叠的处理行/批次集合有效。事实库存不能跨快照求和；比例需汇总分子分母后再计算，不能把每天百分比相加或简单平均。

## 时间、范围、空值与水位

- 事件时间取 captureTime；入库时间拟取 ingestedAt；两者不能互换。当前合成 ingestedAt，以及 PG 测试写入的 processed_at，来源于批次 receivedAt，不能声称是真实提交时间或据此算时延。
- 工程报告 asOf 是一致导出的时间；maxObservedCaptureTime/maxObservedIngestedAt 仅为已见最大时间。sourceCompletenessWatermark=null，不能由最大时间推断迟到数据已齐。
- 未来时间分桶统一 [from,to)，UTC存储；报表自然日/月/年按 Asia/Shanghai 转换为排他上界，periodEnd 含所指完整自然周期。分桶边界、迟到修订/回补与版本必须冻结；本轮未实现生产时间序列计算。
- 未来指标必须在完整授权场所/类型交集上计算；scopeVersion、规则版本、来源和 asOf 可追溯。不得用当前分页、目标预览或脱敏显示号计算全量指标。当前本地审计只适用于合成全账本，不实现平台用户鉴权。
- 有完整证据且确实为空时计数为0；缺数据、未签认规则、未知完整性不能补0。比例分母为0为不可用；接口是否支持 null 需要逐项按 DTO 处理。

## 尚不能计算的业务指标

| 指标 | 所需来源 | 未决内容与当前结果 |
| --- | --- | --- |
| 源端采集量 SOURCE_CAPTURE_COUNT | capture.source_counter_sample | reported_count 与累计计数差值、epoch重置、窗口重叠、缺口/迟到规则；本样本无计数源，UNAVAILABLE/value=null/asOf=null |
| 设备在线率 DEVICE_ONLINE_RATIO | core.device_status_event、设备目录及策略版本 | 瞬时台数比例/区间时长比例、UNKNOWN/FAULT、心跳阈值、停用设备分母；当前UNAVAILABLE/null |
| 首页累计/当日/近7日、排行、去重周趋势 | 上述来源、事实、指标定义及完整授权范围 | P-006的时间口径、去重维度、归属、刷新与水位须逐项确认；不能直接将本地工程计数填入页面 |
| 八类报表 | 下表来源、冻结范围与 metric_definition_versions | P-006/P-020；尚未生成生产 XLSX/ZIP 或预览 |

源端累计计数不直接相加；是否差分、如何处理计数器归零/换代，须先核对 counter_epoch 和协议。线上“采集量”与“成功入库量”分别展示，72行处理量和16条事实均不能替代设备采集量。

## 八类报表逐项范围

| reportType | 需要确认的输入与规则 |
| --- | --- |
| DEVICE_REALTIME_ONLINE_RATE | 统计时刻、设备分母、在线策略及缺测 |
| DEVICE_REALTIME_STATUS | 设备状态快照、UNKNOWN/FAULT与异常定义 |
| FENCE_OPERATOR_STATISTICS | 围栏来源、运营商目录、归属与去重 |
| FENCE_AREA_CAPTURE | 授权区域/场所、历史归属和时间口径 |
| FENCE_DEVICE_CAPTURE | 设备范围，采集计数与入库事实分开 |
| MAC_CAPTURE | MAC来源、稳定事件键、采集/入库与时间粒度 |
| PLATE_CAPTURE | 车牌事实与源计数；现有合成样本只验证工程计数 |
| SYSTEM_COMPONENT_STATUS | 组件遥测、观测时刻、故障/缺测判定 |

SINGLE一份定义生成XLSX并有分型预览；BUNDLE多定义生成ZIP，无在线预览，按现有API约定。此处列齐范围用于协调，不代表报表计算和文件服务已实现。

## 需要 DEV-01 对齐的契约差异

1. D3-C01：API的periodEnd包含自然周期，DDL operation.report_definition要求period_start<period_end；显式同日起止无法直接落表。建议由DEV-01决定存储排他上界的映射或提交迁移提案，当前保留原契约不擅改。
2. D3-C02：DeviceCapturePoint/DeviceCaptureStatistics 的采集计数是必填非空整数，不支持未知时随意填null；因此本地UNAVAILABLE对象不是该公开DTO。缺源或未签认规则时，由DEV-01按能力关闭/REQUIREMENT_PENDING处理，不能补0假装有效。
3. D3-C03：合成接收时间不等于生产提交时间；需要先确定真实入库时钟再实现时延、日入库趋势等指标。

## 验收与复现

运行以下命令会执行本地回归、用新SQLite状态运行首次接入/重启回放/全部重试，并生成独立的指标与验收记录：

```powershell
python workers/data/run_stage1_local.py
```

每次在输出根中新建s1/runs目录，s1/latest.json指向最新结果；默认输出根为workers/data/.artifacts。校验四个导出文件的摘要、批次与行唯一性、事实/源事件对应、计数守恒、质量问题一一对应，以及冻结样本预期。生成 metrics.json、三次进程日志、测试日志和 acceptance.json。

该命令不会启动 PostgreSQL。localChecks=PASS 表示本地范围通过；真实PG已通过12项集成测试，s1Status仍为INCOMPLETE，继续等待正式H04及必要签认/独立复验。详见 [验收说明](../validation/README.md)。
