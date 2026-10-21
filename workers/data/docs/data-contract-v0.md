> 2026-10-19当前分工：DEV-04数据、DEV-03前端、DEV-05独立测试；DEV-01维护共享TaskStore/授权/迁移，DEV-02负责M03等领域公开API与查询编排。下文D3编号和v0协议为交付历史；业务与任务参数以[主分支PD方案](../../../docs/project-governance/暂定方案与修改记录.md)及[内部协议](../../../docs/project-governance/INTERNAL_TASK_PROTOCOL.md)为准，旧pending停做/实验租约数值不覆盖现行规则。

# D3-01 数据字段与接入契约草案

日期：2026-10-16；草案版本：`d3-data-contract-v0`。D3-01 已交付可评审的字段映射，真实厂商映射与业务规则仍待签认。

这份材料把“输入数据叫什么、平台怎样表达、存到哪张表、接口输出哪个字段”连起来。完整清单有 **65 条映射**，覆盖四类采集、设备状态、设备计数，以及接入与质量记录。

- [Excel 可打开的字段映射 CSV](data-field-mapping-v0.csv)：建议先看这份。
- [同内容的 JSON](data-field-mapping-v0.json)：后续工具和代码可读。
- [冻结的历史来源版本和 SHA-256](../fixtures/contract-baseline.json)：记录原版本依赖的8个文件；当前165表与OpenAPI使用[独立D3-12R基线](../fixtures/contract-pd-2026-10-19-v1.json)，详见[复验说明](d3-12r-current-contract.md)。
- [首批车牌合成样本](../fixtures/plate-v0/README.md)：用于检验接入程序。

## 当前依据与适用范围

基线提交为 `486a5b40f557ac81e6efa6a986cd1b629b3f7c8d`；OpenAPI `info.version = 1.0.0-dev.2`。权威来源仍是项目 [OpenAPI](../../../contracts/api/openapi.yaml)、[API 约定](../../../contracts/api/api-conventions.md) 和 [DDL](../../../contracts/database/schema.sql)。本草案没有修改它们。

仓库和本次对话尚未提供可确认的厂商协议。映射表的“源字段”使用逻辑候选名：

| source_status | 含义 |
| --- | --- |
| UPSTREAM_PENDING | 为讨论提出的源字段名，需厂商确认；不是已落地的适配器字段 |
| PLATE_FIXTURE | 本次虚构车牌协议中已明确的字段，限合成样本使用 |
| SYSTEM | 平台生成、目录映射或聚合得到，不应由原始数据直接决定 |

表里的 DDL/DTO 目标已逐项检查存在；规则中标记的 P 项仍保持未决。字段存在不代表对应算法、转换或授权已实现。

## 统一的输入与输出边界

建议内部处理对象分为来源信息、采集事实、类型载荷和处理结果。本文的 standard_field 是工作模型名称，不增加公开 HTTP DTO，也不增加新数据库字段。

| 部分 | 标准内容 | 落地与对外表现 |
| --- | --- | --- |
| 来源/批次 | sourceCode、mappingVersion、schemaVersion、dedupVersion、batchId、taskId、rowNumber、sourceRecordKey | 接入源、映射版本、批次、source_event、ingestion_item |
| 采集事实 | captureId、dataType、targetType/targetId、deviceId/siteId、captureTime、ingestedAt、location、status | capture.capture_event → CommonCaptureRecord 的字段 |
| 类型载荷 | fence / mac / plate / face | 四类独立 payload 表 → 对应 CaptureRecord 分支 |
| 遥测 | 状态事件或源计数样本 | core.device_status_event / capture.source_counter_sample，分别投影或聚合 |
| 处理结果 | ACCEPTED / REJECTED / DUPLICATE、质量原因、原始行引用 | ingestion_item / quality_issue；批次计数与业务事实计数分开 |

接入配置由服务端给定。原始行不得直接指定授权范围、事实状态、taskId 或数据库写入权限。

## 公共采集字段

下表为现有 DTO 的要求。**字段必返且可为 null** 与 **字段可省略** 是两种不同约束。

| 字段 | 标准类型 / 是否可空 | 映射与处理 |
| --- | --- | --- |
| captureId | UUID，必返不可空 | capture_event.id；生产 ID 策略与 dev1 对齐 |
| dataType | 四种枚举，必返 | FENCE、MAC、PLATE、FACE |
| targetType | 五种枚举，必返不可空 | FENCE 选 IMSI 或 IMEI；其余 MAC、PLATE、FACE |
| targetId | UUID，必返可空 | primary_target_id；未解析时空值，不能拿 displayTarget 代替 |
| deviceId / siteId | UUID，必返可空 | 受控源设备/场所映射；客户端给的场所不是权限 |
| captureTime | RFC3339，必返不可空 | 采集时刻；标准层 UTC，查询区间 [from,to) |
| ingestedAt | RFC3339，必返不可空 | 首次入库时刻；回放不修改原事实的这一值 |
| longitude / latitude | 数字，必返可空 | 同时有值或同时 null；经度在前，从 geometry(Point,4326) 投影 |
| source | 字符串，必返不可空 | source_code，不是自由填写的供应商展示名 |
| status | VALID / QUARANTINED / RETRACTED | 事实状态，不能填 ACCEPTED 等行处理状态 |
| displayTarget / isMasked | 字符串 / 布尔，必返不可空 | 由受控脱敏投影生成，DDL 无同名明文展示列 |

source_event 的稳定身份是 `(source_code, source_record_key)`；capture_event 对 source_event_id 唯一。batchId、接收时间不能参与源事件身份，否则每次回放都会变成新事实。同目标、同一时刻但源事件不同，也不能自动删除其中一条。

unknown device 的采集可在结构上保留空 deviceId/siteId；是否入库或隔离属于 P-004。此次合成样本选择“保留并给质量提示”，只用于测试这个分支。场所限制查询必须排除 null 场所，不得把它当全场所可见。

## 四类载荷

| 类型 | 源字段候选 → 标准内容 | 表与 DTO | 约束/未决点 |
| --- | --- | --- | --- |
| FENCE | imsi / imei → 两个带类型的目标代理 | capture.fence_record.imsi_target_id / imei_target_id → FenceRecord.imsiMasked / imeiMasked | 输出字段均必返可空；主目标选择及规范化待 P-004，不能自动认同人 |
| FENCE | carrier / radioType → 运营商代码/无线制式 | carrier_code / radio_type → carrier / radioType | 可选可空，目录和来源转换待确认 |
| MAC | mac → MAC 目标代理；wirelessType → 无线类型 | capture.mac_record → MacRecord | macMasked 必返不可空；wirelessType 可选可空；格式归一待确认 |
| PLATE | plateText → 车牌目标代理 | capture.plate_record.plate_target_id → PlateRecord.plateMasked | plateMasked 必返不可空；真实车牌格式与去重规则待确认 |
| PLATE | plateColor / vehicleImageRef → 颜色/受控图片 ID | plate_color / vehicle_image_asset_id → plateColor / vehicleImageAssetId | 颜色可选可空；图片 ID 可省略但不能显式 null |
| FACE | imageRef → 受控图像 ID | capture.face_record.image_asset_id → FaceRecord.imageAssetId | 必返不可空；原始 URL 先接文件服务，不能直接当 UUID |
| FACE | modelVersion / qualityScore → 模型版本/质量分 | model_version / quality_score → 同名 DTO 字段 | 均必返可空；DDL 分值为 [0,1]、最多5位小数，供应商量纲待确认 |
| FACE | featureRef / featureModelVersion → 特征工件引用 | capture.face_feature | 内部数据，不是公开 DTO 字段；模型授权与评测待 P-007 |

每条采集事实只关联匹配其 dataType 的 payload。载荷表里的 *_target_id、设备和文件引用必须指向实际存在且符合类型的资源；D3-02 的 UUID 只是未来测试种子参照，不是已入库记录。

## 两类遥测

| 内容 | 标准字段与存储 | 对外映射/规则 |
| --- | --- | --- |
| 设备状态 | device_id、previous_status、status、occurred_at、source、reason、policy_version、trace_id → core.device_status_event | DeviceStatusEvent；deviceId 和 previousStatus 不可空，状态枚举为 ONLINE/OFFLINE/UNKNOWN/FAULT |
| 状态到达顺序 | 原始时间与受控历史状态 | 第一次状态、迟到事件、离线判定阈值待 P-004/P-006/P-018；不自行填 previousStatus |
| 计数身份 | source_code + source_device_key + sample_key → capture.source_counter_sample | 三者唯一；防止重复采样造成重复计数 |
| 计数窗口 | period_from、period_to | 开始早于结束；以已确认的桶和窗口聚合 |
| 源计数 | reported_count / reported_cumulative_count | 非负 bigint，可空但至少一个有值；累计数需结合 counter_epoch，不能直接求和 |
| 计数水位 | counter_epoch、as_of | 重启/清零、差分、缺失与回退处理待 P-004/P-006 |
| 展示统计 | 源计数聚合与 capture_event 独立聚合 | DeviceCapturePoint.captureCount / ingestedCount；分别汇总到 DeviceCaptureStatistics |

未知设备在状态事件中不能照搬采集的 null 处理，因为状态表 device_id 是非空外键。源采集量与平台成功入库量始终分别统计。没有源计数时，不能用入库量伪装它，也不能默认报 0。

## 版本、质量与回放提案

映射、源模式、去重、质量规则分开标版本；下次变更写新版本并说明影响。映射切换是否需要重新计算历史事实，不能通过覆盖旧版本解决。

来源追溯至少保留 sourceCode、sourceRecordKey、批次、原始文件/位置、行号、摘要、规则版本与 traceId。坏行可定位且不应生成半条采集事实。批次在最终完成后可以核对“总行数 = 接受 + 拒绝 + 重复”；处理中不能把尚未处理的行算成拒绝。

本次内部样本使用 `FIXTURE_*` 原因码，便于判断哪一项预期失败；它们不是 OpenAPI 的 ErrorCode。正式实现时需与 dev1 明确怎样映射到公开错误码，不能把样本原因码直接塞入 API 枚举。

以下行为只是合成协议规则：原始 JSON 全业务对象排序后做 SHA-256；同键同摘要为重复，同键异摘要拒绝；无时区/数字时间戳拒绝；未知字段拒绝；未知设备/目标允许保留空代理。真实厂商可能需要合法的不同处理，必须按签认规则替换，不能据此关闭 P-004/P-005。

## 本次发现的接口差异与待确认项

| 问题 | 当前事实与处理 | 需协作确认 |
| --- | --- | --- |
| 质量分上下限 | FaceRecord 只有 number/nullable；DDL 要求 [0,1]、numeric(6,5) | dev1/dev3 对齐校验精度；厂商确认原始量纲，P-004/P-007 |
| 源代码长度 | IngestionBatchInput.source 最大80；数据库 source_code 为200 | 通过当前公开创建接口的源代码遵守80；不从DB长度反推API放宽 |
| 图片空值 | PlateRecord.vehicleImageAssetId 可省略、不可null；DB允许NULL | 投影无图片时省略此属性；样本包含正确省略及校验器反例 |
| 目标类型描述残留 | CommonCaptureRecord 描述含旧“可解析时出现”，但 required 和分支明确必返 | 以当前 required 和类型分支约束构造输出；建议 dev1 后续清理重复说明 |
| 首条/乱序设备状态 | previous_status 与 device_id 均非空 | 提交来源协议、历史排序、未知设备处理方案，P-004/P-006/P-018 |
| 真实源键与更正 | 已有唯一约束，不代表来源能提供稳定键 | 厂商提供正反例；dev3 提案、dev1 评审回放权限，P-004/P-005 |
| 统计计数口径 | 全局质量汇总的去重窗口/设备计数单位未冻结 | P-006/P-020；此次只交逐行结果和分批预期，不假造首页指标 |

未决项仍以 [项目清单](../../../docs/project-governance/PENDING_REGISTER.md) 为准；没有修改任何 P 项状态。

## 接收方式

dev1 可用 CSV/JSON 核对字段、DTO 和种子引用；dev4 可用样本包复算行级结果和边界；dev3 下一轮从 D3-03/04 实现解析、校验与去重，再把真实输出与样本预期比较。

本次完成的是字段草案与样本准备。运行中的 Worker、真实数据库导入、厂商接入和公开 API 联调分别属于后续工作。
