# 指标与算法暂定口径

PD-2026-10-19.v1；待甲方确认。以下公式及数值均为本轮工程选择，网上来源见主文档；不是实测效果。单位：条、个、台、比例0—1、秒、米；不在API把0—1比例乘100，界面才显示百分号。

## METRIC_V1

所有公式先套当前授权元组和site谓词，再按固定水位计算。Asia/Shanghai自然周期，WEEK周一00:00起；内部[start,end)。today/最近7天止于实际asOf。all/total表示**当前实际保留覆盖**，不是已清理历史的终身累计；明确显示coverage，不因名字total补造历史。periodEnd含指定报告周期。

| API区域/字段 | 定义 |
| --- | --- |
| onlineDevices/deviceOnline | 当前有效ACTIVE设备；最近90秒真实心跳为在线。onlineRate=在线/有效设备总数，0分母为null。分设备类型/功率分类同式；未知功率UNKNOWN，不猜测。 |
| lastSevenDaysCapture | 今天及之前6个自然日的设备权威counter增量；若只有入库事实而无counter，则采集量UNAVAILABLE，不以入库量替换。 |
| todayAndTotalCapture/captureOverview | captured取reportedAt的counter增量；ingested取ingestedAt成功事实count。counter按(source,device,epoch)相邻采样差，重启新epoch，未知epoch/不明下降隔离；首样本仅基线，不计全量历史。 |
| deviceCaptureTotals | counter按设备类型汇总；按类型不可得则UNKNOWN或不可用，不能将同一设备总量重复分配给多种类型。 |
| alertCount/deviceAlertCounts | count distinct alertId；按告警occurredAt和关联deviceId归属。多个命中记录不等于多条告警。 |
| deviceMap | 当前授权有效设备/场所数；图层复用/map/devices，未知坐标不画点。 |
| topSitesByDataType/topCaptureSites | 按capture事实的历史siteId、dataType计条数，降序取10，同值siteId升序；旧字段为不分类型的同源投影。 |
| deduplicatedSeries/deduplicatedCapture | 每桶count distinct(targetType,targetId)，null目标不入身份去重；总量重新去重不累加日去重。originClass使用有效归属字典，本地边界未绑定则UNKNOWN。旧字段是同源当期分类投影。 |
| originTopTen | target_attribution来源/版本在采集时刻有效；按独立typed target数降序、regionCode升序，未知单列UNKNOWN。不把采集场所当号码归属地。 |
| fenceRadioStatistics | FENCE事实按已登记radio制式字典及桶count；无字段UNKNOWN，不能猜制式。 |
| operationStatistics | FENCE事实按已登记operator归属及桶count，保留字典版本；未能确定时UNKNOWN。 |
| applicationEntries | 由当前用户可见28菜单目录投影，不能静态放出无权页面。 |
| 运维byDataType/captureSeries | FENCE/PLATE/MAC/FACE分组，分别返回CAPTURED、INGESTED、DEDUPLICATED；去重按同样typed identity。counter无法按类型区分的CAPTURED项不可用。 |
| 运维smsModem | online/uptime/networkRegistration等来自真实adapter；sentTotal=实际提交尝试数，sentSuccess按设备实际受理确认数；不是短信验收送达成功率。 |

同一指标以相同授权/水位/公式版本取得相同结果。首页提交水位至发布≤3秒、UI轮询≤2秒；运维同源。MetricStatus仅沿用READY/STALE/UNAVAILABLE：已完成当前覆盖为READY，超过刷新预算但有上次真实快照为STALE，无可靠值UNAVAILABLE/null；不新增PARTIAL枚举。asOf是实际水位，无数据空集可0，但缺数据/缺来源不可0。

暂定异常采用每5分钟桶，计实际异常事件数而非总源行数：

| 既有码 | 判定 |
| --- | --- |
| UNREGISTERED_DEVICE | 未登记设备的遥测/心跳事件；设备标识仅内部脱敏引用。 |
| DUPLICATE_DEVICE_DATA | 同设备遥测sourceRecordKey重复提交的拒绝次数。 |
| DEVICE_TIME_ANOMALY | 遥测无合法时区、未来超过5分钟、倒序超过5分钟；合法迟到单列质量事件，不伪改设备时钟。 |
| DEVICE_MODE_ANOMALY | reported与已确认desired在120秒传播期后仍不符，且真实回读证明；无回读为未知，不报不符。 |
| UNREGISTERED_DATA | 未登记source/profile的采集行数，隔离不入事实。 |
| DUPLICATE_DATA | 已存在同一源事件key再次提交的行数。 |
| DATA_TIME_ANOMALY | 采集时区/格式非法、未来超过5分钟或超留存期；隔离原因保留。 |
| DATA_MODE_ANOMALY | 按已绑定profile声明的类型/制式不匹配行数；未交字段映射不能假判。 |
| DATA_FORMAT_ANOMALY | 必填、类型、标识、坐标/几何非法行数。 |
| ONLINE_NO_DATA | 在线且profile声明应持续采集的设备，连续10分钟无采集；非连续模式不触发。 |
| DATA_SPIKE/DATA_DROP | 与前7个完整自然日的相同日内5分钟桶的入库条数中位数比较，变化≥50%且绝对差≥100条。不足7个完整历史桶时暂不判断，预热期明确标不可用；不假造历史。 |

同一行可有多个质量原因，但总隔离行数按batchId+rowNumber去重。设备/数据/体量异常分别装入既有三数组，不能将重复原因直接相加宣称独立记录总数。

## 分析规则

COMPANION_V1有效记录须有合法时间/坐标/typed target。按capturedAt、captureId升序遍历主目标；在允许时间与米制距离内，从未使用的候选中按绝对时间差、距离、captureId取最近项，一对一配对。UTC epoch起按timeWindowSeconds切桶；共同桶按主记录桶去重，A/B桶在同一查询窗口与相同桶定义上计数。共同桶≥3才产生伴随候选；associationScore=共同桶/(A桶+B桶−共同桶)。commonOccurrenceCount=共同桶数，sharedSiteCount=配对双方同siteId的去重数，trajectoryOverlapCount=满足时间/距离配对的记录对数。证据列出每一对captureId及距离，不用桶数冒充记录数。

COLLISION_V1的targets[0]为主目标，其余逐个目标按120秒/100米规则独立匹配；目标顺序冻结。matchRate=主目标命中桶/主目标有效桶，collisionCount=匹配记录对数，events保存每对主记录时刻/主位置和双方captureId。一个候选使用过的采集在该次主候选匹配内不能重复配对；不同候选独立。区域coverageRate仍按经过选区/总选区，空分母null。这些分数均不表示同一人或嫌疑概率。

人像默认minSimilarity=0，按score>minSimilarity筛选（默认大于0），候选20最多100；模型必须声明映射与版本，不把不同模型原分数混用。无授权离线模型503；FMR≤0.1%/Top20召回≥95%只是待确认目标，须真实正反例评测，不声称已达成。

## RESIDENCE参数目录

规则RESIDENCE_SITE_NIGHTS_V1、输出标签SUSPECTED_RESIDENCE_SITE；只有人工复核才可作业务结论。窗口为执行水位前推一个自然月的留存有效记录；夜间22:00至次日06:00，凌晨记录归前一夜。一个场所夜数/所有场所观测夜晚并集数≥0.6，且≥10条记录、≥5夜。多候选按夜数/最近时刻/siteId稳定排序。

| parameterCode | valueType | 默认/范围/单位 |
| --- | --- | --- |
| WINDOW_CALENDAR_MONTHS | INTEGER | 1，固定1，自然月；不换算30天 |
| NIGHT_START_MINUTE | INTEGER | 1320，0—1439，上海当地日内分钟 |
| NIGHT_END_MINUTE | INTEGER | 360，0—1439；必须与开始不同，跨日规则显式处理 |
| MIN_RECORDS | INTEGER | 10，1—1000000，条 |
| MIN_DISTINCT_NIGHTS | INTEGER | 5，1—31，夜 |
| MIN_NIGHT_FRACTION | NUMBER | 0.6，0—1，比例 |
| TAG_VALIDITY_DAYS | INTEGER | 7，1—31，访问天数；物理仍一年（12个自然月） |

参数版本引用后不可变；目录通过已有强类型ModelParameterValue表达，不新增DSL。参数变更有新algorithmVersion和回归基线。

## 8类报表

设备实时在线率/实时状态取固定asOf的真实设备快照；电围运营商/区域采集/设备采集分别按以上运营商与capture时region/site/device分组；MAC/车牌各返回capture条数及typed target去重；系统组件状态取真实探针结果与检查时间。只在同一个冻结水位生成预览/XLSX/ZIP；不能预览一套、下载另一套。

普通明细单次查询31天与物理保存一年（12个自然月）分别生效。年度汇总按Asia/Shanghai自然年，在相关数据清理前冻结并保护在途报表输入；冻结报表/追溯元数据另从发布时刻保存一年。滚动一年不自动证明任意历史自然年的明细仍齐全；已有完整冻结年报可按权限读取，缺历史或覆盖不全继续披露，不补零、不自动放宽查询或导出限额。详见[保存策略](RETENTION_POLICY.md)。

人像2秒验收预算包括排队、实际检索与结果可用，不能以普通任务30分钟上限替代。FACE_SEARCH走交互优先通道，暂分配排队200ms、执行1500ms、发布/获取300ms；结果轮询100ms或等价内网推送，普通任务仍2秒轮询。过载429并计入失败率，未完成不返回假结果；授权模型、10万底库与50用户实测后确认资源预算。

FMR在另行校准并冻结的阈值上评测，记录阈值、模型与样本版本；不能以默认0的候选筛选率代替FMR。Top20召回在规定底库规模上单独评测。
