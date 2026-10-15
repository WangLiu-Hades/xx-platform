/**
 * 基础设施层（持久化、幂等、游标、文件存储、审计埋点、迁移）。
 *
 * <p>职责边界：
 * <ul>
 *   <li>这里放"技术实现"，不放业务规则。业务判断属于 {@code service}。</li>
 *   <li>数据库迁移（DDL 版本化）由平台侧统一合并发布，
 *       另一侧的迁移片段走 PR 提交，不各自改库（见 BACKEND_TWO_DEV_SPLIT.md §3）。</li>
 * </ul>
 *
 * <p>按阶段落地的基础设施（当前均未实现）：
 * <ul>
 *   <li>幂等：{@code Idempotency-Key}（16–160 字符）作用域 = 账号 + operationId + 键，
 *       相同输入返回原响应，不同输入 {@code 409 IDEMPOTENCY_CONFLICT}。</li>
 *   <li>乐观锁：{@code If-Match} ↔ 资源 version，冲突 {@code 409 RESOURCE_VERSION_CONFLICT}。</li>
 *   <li>游标：服务端签名（绑定账号/范围版本/查询快照/排序/页大小），只校验不解析，
 *       失败 {@code 400 DATA_CURSOR_INVALID}。</li>
 *   <li>文件：multipart 上传 + 分类策略、状态机 {@code PROCESSING→ACTIVE|QUARANTINED}、
 *       受权下载（不返回永久裸 URL）。</li>
 *   <li>任务：{@code async_task} 表与领取/续租/取消/重试/发布协议（H04 交接物）。</li>
 * </ul>
 */
package com.xxplatform.api.infra;
