/**
 * 业务服务层（领域规则、状态机、事务边界）。
 *
 * <p>职责边界：
 * <ul>
 *   <li>领域状态机在这里：任务 {@code PENDING→QUEUED→RUNNING→SUCCESS|FAILED|CANCELLED}、
 *       布控 {@code DRAFT→ACTIVE→FINISHED|REVOKED}、通知计划 {@code ACTIVE|CANCELLED|COMPLETED}。</li>
 *   <li>事务边界在这里（{@code @Transactional}），Controller 不开事务、infra 不做业务判断。</li>
 *   <li>需要数据计算/查询时调用 {@code client} 层的 DEV-03 模块，不在这里重写算法。</li>
 * </ul>
 *
 * <p>禁止事项（契约与治理要求）：
 * <ul>
 *   <li>未签认能力不得用 Mock 阈值实现，应经能力开关返回 {@code REQUIREMENT_PENDING}。</li>
 *   <li>不得把脱敏展示值当内部 ID 使用；{@code targetId} 与 {@code fileAssetId} 不是同一概念。</li>
 * </ul>
 */
package com.xxplatform.api.service;
