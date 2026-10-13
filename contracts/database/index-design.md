# 索引、查询和分区（DB-DESIGN-2）

## 索引边界

DDL定义75个显式查询索引，另有主键/唯一约束自动索引；不能将75误认为全部物理索引数。部分索引谓词必须被查询显式包含，例如status=VALID、deleted=false。小表可能选择Seq Scan是优化器合理选择，不强制全表索引。

| 索引 | 表 | 方法/列或表达式 | 部分条件 | 唯一 |
| --- | --- | --- | --- | --- |
| ix_session_user_active | auth.user_session | btree / user_id,idle_expires_at | revoked_at IS NULL | 否 |
| ix_csrf_expiry | auth.csrf_session | btree / expires_at | 无 | 否 |
| ix_region_parent | core.region | btree / parent_region_code | 无 | 否 |
| ix_region_geometry | core.region | gist / boundary | boundary IS NOT NULL | 否 |
| ix_site_region_name | core.site | btree / region_code,name,id | deleted=false | 否 |
| ix_site_geo | core.site | gist / location | location IS NOT NULL AND deleted=false | 否 |
| ix_device_site_status | core.device | btree / site_id,status,id | deleted=false | 否 |
| ix_device_category | core.device | btree / category_code,id | deleted=false | 否 |
| ix_device_geo | core.device | gist / location | location IS NOT NULL AND deleted=false | 否 |
| ix_group_member_device | core.device_group_member | btree / device_id,group_id | 无 | 否 |
| ix_device_status_history | core.device_status_event | btree / device_id,occurred_at DESC,id DESC | 无 | 否 |
| ix_device_parameters | core.device_parameter_value | btree / device_id,value_kind,parameter_version DESC | 无 | 否 |
| ix_file_expiry | core.file_asset | btree / expires_at | expires_at IS NOT NULL | 否 |
| ix_idempotency_expiry | core.idempotency_request | btree / expires_at | 无 | 否 |
| ix_outbox_pending | core.outbox_event | btree / next_attempt_at,id | status IN ('PENDING','FAILED') | 否 |
| ix_context_owner_expiry | analysis.analysis_context | btree / owner_user_id,expires_at | 无 | 否 |
| ix_task_claim | analysis.async_task | btree / priority,next_run_at,created_at,id | status IN ('PENDING','QUEUED') AND deleted=false | 否 |
| ix_task_expired_lease | analysis.async_task | btree / lease_until,id | status='RUNNING' | 否 |
| ix_task_owner_list | analysis.async_task | btree / owner_user_id,created_at DESC,id DESC | deleted=false | 否 |
| ix_task_event_history | analysis.task_event | btree / task_id,occurred_at,id | 无 | 否 |
| ix_task_row_error | analysis.task_row_error | btree / task_id,attempt_no,row_number,id | 无 | 否 |
| ix_ingestion_source_list | capture.ingestion_batch | btree / source_code,created_at DESC,id DESC | 无 | 否 |
| ix_quality_time | capture.quality_issue | btree / occurred_at DESC,id DESC | 无 | 否 |
| ix_counter_device_time | capture.source_counter_sample | btree / device_id,period_from DESC,id | 无 | 否 |
| ix_target_attribution | capture.target_attribution | btree / target_id,valid_from DESC,id | 无 | 否 |
| ix_capture_type_time | capture.capture_event | btree / data_type,capture_time DESC,id DESC | status='VALID' | 否 |
| ix_capture_target_time | capture.capture_event | btree / primary_target_id,capture_time DESC,id DESC | status='VALID' AND primary_target_id IS NOT NULL | 否 |
| ix_capture_site_time | capture.capture_event | btree / site_id,capture_time DESC,id DESC | status='VALID' AND site_id IS NOT NULL | 否 |
| ix_capture_device_time | capture.capture_event | btree / device_id,capture_time DESC,id DESC | status='VALID' AND device_id IS NOT NULL | 否 |
| ix_capture_geo | capture.capture_event | gist / location | location IS NOT NULL AND status='VALID' | 否 |
| ix_capture_batch | capture.capture_event | btree / batch_id,id | 无 | 否 |
| ix_capture_other_target | capture.capture_target | btree / target_id,capture_id | 无 | 否 |
| ix_analysis_kind_created | analysis.analysis_job | btree / kind,created_at DESC,id DESC | deleted=false | 否 |
| ix_analysis_result_page | analysis.analysis_result | btree / analysis_job_id,attempt_no,data_type,id | 无 | 否 |
| ix_collision_events | analysis.collision_event | btree / result_id,occurred_at,id | 无 | 否 |
| ux_dataset_active_capture | analysis.dataset_item | btree / dataset_id,capture_id | element_type='CAPTURE' AND valid_to_version IS NULL | 是 |
| ux_dataset_active_target | analysis.dataset_item | btree / dataset_id,target_id | element_type='TARGET' AND valid_to_version IS NULL | 是 |
| ix_dataset_history | analysis.dataset_item | btree / dataset_id,valid_from_version,id | 无 | 否 |
| ux_dataset_user_grant | analysis.dataset_grant | btree / dataset_id,user_id | user_id IS NOT NULL | 是 |
| ux_dataset_role_grant | analysis.dataset_grant | btree / dataset_id,role_id | role_id IS NOT NULL | 是 |
| ix_profile_search | analysis.profile | btree / profile_type,updated_at DESC,id DESC | deleted=false | 否 |
| ix_profile_identifier | analysis.profile_identifier_index | btree / field_code,hmac_key_version,normalization_version,lookup_hash | 无 | 否 |
| ux_profile_active_link | analysis.profile_link | btree / profile_id,target_id | valid_until IS NULL | 是 |
| ix_profile_target | analysis.profile_link | btree / target_id,profile_id | valid_until IS NULL | 否 |
| ix_evidence_capture | analysis.profile_evidence | btree / capture_id,profile_id | 无 | 否 |
| ix_library_category_name | analysis.person_library | btree / category_code,name,id | deleted=false | 否 |
| ux_library_active_member | analysis.person_library_member | btree / library_id,profile_id | deleted=false | 是 |
| ix_library_member_profile | analysis.person_library_member | btree / profile_id,library_id | deleted=false | 否 |
| ix_face_results | analysis.face_search_result | btree / task_id,attempt_no,similarity DESC,capture_id | 无 | 否 |
| ix_model_result_page | analysis.model_result | btree / execution_task_id,attempt_no,id | 无 | 否 |
| ix_model_evidence | analysis.model_result_evidence | btree / result_id,id | 无 | 否 |
| ix_watch_owner_list | operation.watch_rule | btree / owner_user_id,created_at DESC,id | deleted=false | 否 |
| ix_watch_active_window | operation.watch_rule | btree / effective_from,effective_until,id | status='ACTIVE' AND deleted=false | 否 |
| ix_watch_target_candidates | operation.watch_rule_target | btree / target_id,rule_id,rule_version | 无 | 否 |
| ix_watch_site | operation.watch_rule_site | btree / site_id,rule_id,rule_version | 无 | 否 |
| ix_alert_rule_time | operation.alert_event | btree / rule_id,occurred_at DESC,id DESC | 无 | 否 |
| ix_alert_time | operation.alert_event | btree / occurred_at DESC,id DESC | 无 | 否 |
| ix_notification_due | operation.notification_plan | btree / next_run_at,id | status='ACTIVE' AND deleted=false | 否 |
| ix_notification_owner_list | operation.notification_plan | btree / owner_user_id,created_at DESC,id | deleted=false | 否 |
| ux_recipient_address | operation.notification_recipient | btree / owner_user_id,channel_type,hmac_key_version,normalization_version,recipient_hash | channel_type IN ('SMS','EMAIL') | 是 |
| ux_recipient_platform | operation.notification_recipient | btree / owner_user_id,recipient_user_id | channel_type='PLATFORM' | 是 |
| ix_delivery_sms_time | operation.delivery | btree / sent_at DESC,id DESC | channel_type='SMS' | 否 |
| ix_delivery_recipient_time | operation.delivery | btree / recipient_id,sent_at DESC,id DESC | 无 | 否 |
| ix_delivery_alert | operation.delivery | btree / alert_id,id | alert_id IS NOT NULL | 否 |
| ix_delivery_plan | operation.delivery | btree / plan_id,id | plan_id IS NOT NULL | 否 |
| ix_platform_message_user | operation.platform_message | btree / recipient_user_id,created_at DESC,id DESC | 无 | 否 |
| ix_anomaly_series | core.anomaly_event | btree / category_code,detected_at DESC,id DESC | 无 | 否 |
| ix_device_command_history | operation.device_command | btree / device_id,created_at DESC,id DESC | 无 | 否 |
| ix_audit_actor_time | audit.audit_event | btree / actor_user_id,occurred_at DESC,id DESC | 无 | 否 |
| ix_audit_time | audit.audit_event | btree / occurred_at DESC,id DESC | 无 | 否 |
| ix_audit_resource | audit.audit_event | btree / resource_type,resource_id,occurred_at DESC,id DESC | resource_id IS NOT NULL | 否 |
| ix_audit_trace | audit.audit_event | btree / trace_id | 无 | 否 |
| ix_analysis_result_capture_sort | analysis.analysis_result | btree / analysis_job_id,attempt_no,capture_count DESC,id | 无 | 否 |
| ix_profile_link_history | analysis.profile_link | btree / profile_id,valid_from DESC,id | 无 | 否 |
| ix_profile_conflicts | analysis.profile_conflict | btree / profile_id,status,created_at,id | 无 | 否 |

## 查询规则

1. 采集按类型/主目标/场所/设备+capture_time,id稳定游标。其他目标走capture_target(target_id,capture_id)，与主目标合并按事件ID去重。跨时间页游标需绑定筛选和授权，排序白名单按OpenAPI。

2. bitmap组合只用于计划证实有收益的条件；不用OFFSET扫描海量深页。管理列表page分页需稳定排序和受限count。审计普通用户actor_user_id过滤，短信日志channel_type=SMS，并按task_name_snapshot、recipient_id/time筛选。号码先授权HMAC解析代理，不明文模糊扫描。

3. 目标精确检索用target_lookup的版本化唯一键。HMAC密钥/规范化版本变化时查当前有效版本并归一到同UUID；明文检索值不入SQL日志。

4. geometry4326 GiST用于bbox；zoom与limit由服务执行。米制距离使用geography，先有界bbox减少候选。几何索引不能被不必要ST_Transform包裹；不在未测量前同时添加geometry与geography冗余大索引。

5. 首页只读统一metric_snapshot。源采集计数≠capture_event入库数；日去重不能直接加总为月去重，在线率需一致分母/窗口。大统计移Worker，流式汇总/批量upsert，失败块不把NULL改0。

6. task_claim的短事务排序priority,next_run_at,created_at,id；claim时lease_generation加1。长任务不持有行锁；心跳与发布按租约版本防止旧Worker写回。过期扫描有专用索引。

7. 外键默认保护删除；维表低频硬删除/历史清理须评估引用扫描再补反向索引，避免全部FK无条件建索引。采集批次/任务/人像/命令已有查询索引，软删除服务保留引用。

## 分区升级条件

本版不分区。PG14分区表PRIMARY KEY/UNIQUE必须包含分区键，不能在capture_time分区表上直接保留id或source_event_id单列全局唯一。若日增、清理、索引维护或备份预算触发分区评审，先完成：

- 独立全局源身份/业务UUID登记，主事实与四payload/引用设计统一(id,capture_time)键。
- 所有FK、API代理ID解析、数据集/证据/告警引用同步迁移；不同层不能出现两套ID。
- 各月份索引裁剪、迟到数据路由、保留期、影子读/备份恢复验证。
- 采用影子表和明确维护窗口；不能只ALTER父表而忽略363条跨表关系。

保留期和容量未知，不机械建几百个月分区或引入专库。触发后按目标版本重新审查。

## 本次实测

PostgreSQL14.24/PostGIS3.6.1，2万条合成事实、50目标、完整payload/行结果；EXPLAIN ANALYZE,BUFFERS中目标游标使用ix_capture_target_time、类型游标使用ix_capture_type_time、bbox使用ix_capture_geo。任务样本很小，领取选择Seq Scan，不能据此推断生产队列性能。样本不是并发压测、磁盘容量或最低硬件证明。示例SQL见query-examples.sql，原始计划摘要见design-validation.md。
