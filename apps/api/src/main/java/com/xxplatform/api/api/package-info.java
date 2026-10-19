/**
 * 对外 HTTP 接口层（Controller 实现）。
 *
 * <p>职责边界：
 * <ul>
 *   <li>本层只做三件事：参数绑定与校验、调用 service、返回 {@code ApiResponses} 统一壳。</li>
 *   <li>不写业务规则、不直接访问数据库、不自己拼错误壳（异常交给 GlobalExceptionHandler）。</li>
 *   <li>Controller 实现契约生成的 {@code XxxApi} 接口（见 {@code generated} 包），
 *       方法签名以生成接口为准，不要手写一套平行签名。</li>
 * </ul>
 *
 * <p>命名建议：{@code 领域 + Controller}，例如 {@code SiteController implements SitesApi}。
 * 每个生成的 Api 接口只允许存在一个实现类，多实现会导致 Spring 启动报
 * {@code NoUniqueBeanDefinitionException}；建议补一个上下文测试断言"一接口一实现"。
 *
 * <p>归属：模块级分工见 {@code docs/project-governance/BACKEND_TWO_DEV_SPLIT.md}。
 */
package com.xxplatform.api.api;
