# 数据库设计变更（DB-DESIGN-2）

日期：2026-10-07。先对比旧43表方案，再采用新163表方案。源业务文件和OpenAPI YAML保持原样。

## 新增/完善

- 权限菜单、用户与人员库输入范围、会话空闲/绝对过期、CSRF、文件策略/导入模板、幂等/outbox/inbox。
- 源事件与批次行结果、字段映射/质量/回放、状态/真实采集计数、带类型目标/HMAC版本、历史归属。
- typed分析参数/逐目标结果/证据/选区/碰撞事件、任务尝试/租约代次/发布指针、输入引用/manifest。
- 数据集元素UUID、CAPTURE/TARGET、版本摘要/有效区间、集合固定操作数/共享保护。
- 人员/车辆完整字段、库成员UUID、证据/目标有效关联、人工修订/merge/split谱系/冲突、RESIDENCE参数/规则/标签/执行。
- 布控版本与最终统计、ONCE/DAILY通知计划/执行、加密收件人/模板版本、投递/尝试/平台消息/短信猫遥测。
- 设备分组同场所约束、DESIRED/REPORTED参数、逐设备命令/回执/尝试、8类报表历史预览与单XLSX/多ZIP文件。
- 统一指标定义/维度/桶、12类异常、不可变审计和受控解密申请/授权。

## 更名/替代

- capture_event.target_id改为primary_target_id；其他目标通过capture_target。经纬度不重复存，使用geometry；region_code_at_capture保存历史归属。
- session token_hash/expires_at替为sid_hash/idle_expires_at/absolute_expires_at；不新增JWT方案。
- 原notification_task/notification_target拆为notification_plan/notification_execution/notification_recipient与固定版本关联。
- model_version改为明确model_parameter_version与execution/result结构；现行模型仍RESIDENCE，未授权算法不启用。
- analysis_result从每任务JSON摘要改为逐目标/类型/尝试结果；profile_link/revision与库成员获得API可用UUID。

## 文件同步和迁移

03、SQL、全字段字典、ER、索引、API存储映射、查询示例、迁移/验证/待确认清单及有关00/01/02/04/06～10、ADR、入口和时序图均同步。空库DDL不能直接用于旧库；真实数据转换按migration-plan另行生成增量迁移。新增结构有约束/维护成本，负载收益通过共享汇总、游标、版本区间和文件manifest实现，生产需按实际硬件/规模验证。
