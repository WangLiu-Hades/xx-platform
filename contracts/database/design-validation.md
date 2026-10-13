# 数据库设计验证报告（2026-10-07）

## 结果

采用DB-DESIGN-2。以下验证均使用本任务目录的独立临时数据库和合成数据；没有连接或修改生产平台数据库。

| 验证 | 实际结果 |
| --- | --- |
| PostgreSQL / PostGIS | 14.24 / 3.6.1（PG14配套构建）；3.1.4最低配套版本未另行运行 |
| SQL语法及空库执行 | 2335个语句解析通过；DDL实际执行成功 |
| 字段/类型/宽度/可空性 | 163表、1460列逐项与实际PG目录和字典、ER一致 |
| 外键 | 363条，父键唯一/类型一致；循环当前版本引用延迟至事务提交校验 |
| 索引 | 75个显式查询索引；含PK/UNIQUE实际共295个 |
| 源要求 | 578行：PDF214、原型230、系统流程14、工程任务/验收120；M01～M15均有映射 |
| 当前API | 全部193 operationId有存储/实时探针映射；OpenAPI YAML hash未变 |
| 核心响应字段 | 164个Schema / 1148字段，未解析来源0；明确字段引用存在 |
| 查询SQL | 11条PREPARE真实PG检查通过，未运行示例副作用 |
| 链路/约束反例 | 63项通过，0失败；见下面用例 |
| 关系图 | Mermaid10.9.3解析4份同步图，完整ER实际渲染成功，无页面错误 |
| 合成计划检查 | 20000事实 +源身份/typed payload/行结果；目标、类型游标、bbox使用预期索引 |

### 查询计划

| 查询 | 实际索引 | 执行时间ms（本次本机样本） |
| --- | --- | --- |
| target_cursor | ix_capture_target_time | 0.413 |
| type_cursor | ix_capture_type_time | 0.046 |
| bbox | ix_capture_geo | 0.579 |
| task_claim | Seq Scan（队列小样本） | 0.074 |

2万条完整采集处理写入阶段耗时5.632秒；此数字未包含生产并发、远端网络、全流程提交/文件/算法耗时，不作为吞吐承诺。没有强制优化器走索引，任务小样本Seq Scan是正常选择。

## 正向与反例

| 用例 | 结果 |
| --- | --- |
| all catalogue fields and nullability match executed PostgreSQL | PASS |
| all foreign keys exist | PASS |
| seed permission enum equals OpenAPI | PASS |
| pending capabilities closed by default | PASS |
| login session only stores hashes | PASS |
| session idle expiry cannot exceed absolute expiry | PASS；SQLSTATE 23514 |
| assigned role deletion protected | PASS；SQLSTATE 23503 |
| logout revocation stored | PASS |
| group membership cannot cross site | PASS；SQLSTATE 23514 |
| device site move cannot invalidate group | PASS；SQLSTATE 23514 |
| latitude outside WGS84 rejected | PASS；SQLSTATE 23514 |
| four source types have concrete payloads | PASS |
| immutable capture ownership | PASS；SQLSTATE 23514 |
| source replay unique identity | PASS；SQLSTATE 23505 |
| typed payload cannot point to another kind | PASS；SQLSTATE 23514 |
| fence IMSI must be IMSI target | PASS；SQLSTATE 23514 |
| reported capture count separate from 4 ingested rows | PASS |
| analysis result typed and evidence traceable | PASS |
| terminal task state cannot be rewritten | PASS；SQLSTATE 23514 |
| stale worker cannot update via fencing predicate | PASS |
| dataset old version survives item removal | PASS |
| dataset new version reflects removal | PASS |
| dataset versions cannot drift | PASS；SQLSTATE 23514 |
| library -> person -> target -> original evidence | PASS |
| vehicle profile cannot use MAC plate | PASS；SQLSTATE 23503 |
| library cannot bind another profile link | PASS；SQLSTATE 23514 |
| delivery recipient and channel must agree | PASS；SQLSTATE 23514 |
| one scheduled occurrence creates only one execution | PASS；SQLSTATE 23505 |
| audit append only update | PASS；SQLSTATE 23514 |
| audit append only delete | PASS；SQLSTATE 23514 |
| full search-analysis-dataset-watch-alert-notification-audit storage flow | PASS |
| typed model parameter rejects two values | PASS；SQLSTATE 23514 |
| approved parameter bounds enforced | PASS；SQLSTATE 23514 |
| unacknowledged desired parameter not reported | PASS |
| device command result traceable per device | PASS |
| report preview frozen despite later device rename | PASS |
| SINGLE report cannot use ZIP | PASS；SQLSTATE 23514 |
| multi-report ZIP format accepted | PASS |
| referenced source capture cannot be hard deleted | PASS；SQLSTATE 23503 |
| UUID IDs do not accept numeric strings | PASS；SQLSTATE 22P02 |
| stable cursor for tied timestamps | PASS |
| maximum API delivery summary length accepted | PASS |
| maximum API dataset name length accepted | PASS |
| context spatial range must be polygon | PASS；SQLSTATE 23514 |
| telemetry uptime cannot be negative | PASS；SQLSTATE 23514 |
| readonly device parameter cannot be sent | PASS；SQLSTATE 23514 |
| all workflow fixtures commit with deferred references | PASS |
| collision event keeps own evidence | PASS |
| region coverage derived from selected areas | PASS；SQLSTATE 23514 |
| selected regions cannot be from another job | PASS；SQLSTATE 23503 |
| intersection | PASS |
| union | PASS |
| difference | PASS |
| mixed operation | PASS |
| GeoJSON coordinate order | PASS |
| distance in meters uses geography | PASS |
| self intersecting polygon rejected | PASS；SQLSTATE 23514 |
| parameter cannot cross model | PASS；SQLSTATE 23514 |
| model current version belongs to itself | PASS |
| target cursor uses declared index | PASS |
| type cursor uses declared index | PASS |
| bbox uses GiST | PASS |
| scope SQL filters non-PLATE data | PASS |

## 检查中修复的问题

- 共用payload触发器跨类型读取不存在字段：改为类型分支后再读取本类型字段，四源真实写入通过。
- 历史归属字段与触发器名称不一致：统一region_code_at_capture并保护原事实不可重写。
- API字符串上限比物理列大：扩大名称/目录/引用到足够长度，投递摘要支持4000字，并测试边界。
- 模型参数/当前版本跨模型、结果选区跨任务、计划执行/告警版本与投递不匹配：补充复合FK和具体校验。
- 库成员使用其他档案目标、分组跨场所、只读或未批准设备参数发送：明确校验/服务边界。
- 源采集与入库量混用、通知计划与执行状态混用、数据集版本复制与报告动态重查：独立存储和版本发布。
- 旧架构引用model_version、notification_task、report-tasks以及错误pending编号：同步当前物理实体/HTTP路径/签认清单。
- 完整ER超过Mermaid默认文本限制、时间类型空格导致语法错误：增加图大小配置并规范化类型标记，实际渲染通过。

## 尚未验证/仍待决定

1. 这不是真实后端API自动化测试：尚无正式服务实现；验证的是DDL、存储链路、查询与静态契约映射。算法效果、误报率、厂商通信/回执、密码/授权合并/审批规则仍按pending清单签认。
2. 未执行旧业务库数据迁移、生产权限部署、真实并发/容量压测、备份恢复；这些是migration-plan及T21～T27的后续门禁。不能承诺绝对最低服务器负担或已经满足未知SLA。
3. 测试PG14.24/PostGIS3.6.1；部署不同配套版本需重跑相同检查。PG14不能配PostGIS3.0.x。
4. P-012 ID集合等价/表达式语法已明确，不再阻塞；跨源身份拟合仍不得凭空确定。
5. 本次不改OpenAPI业务契约、原始PDF/DOCX及正式前后端工程。

## 可重复核对的hash

- openapi.yaml SHA256：`f10fc158252bad97cb48d9d223e7878618dda403dca5fa863a54cdf6b8aa54cd`
- schema.sql SHA256：`7d3534b536e46a5879d338df08cc70a02012ef86d0efe423d25747cf37a26ab1`
- 临时验证完整结果、计划与旧文件备份保存在本次任务验证目录；不写入正式工程运行环境。
