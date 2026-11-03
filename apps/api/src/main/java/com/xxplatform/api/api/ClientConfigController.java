package com.xxplatform.api.api;

import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.service.ClientConfigService;
import com.xxplatform.api.service.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /client-config}（任务 7，M01）。
 *
 * <p>契约要点：{@code x-permission: AUTHENTICATED}、{@code security: [{sessionCookie: []}]}、
 * <b>没有声明任何 parameters</b>（所以不需要 {@code X-CSRF-Token}：读操作不改状态）、
 * 200 只带 {@code X-Trace-Id}、无 ETag、无 404/422。
 *
 * <p><b>为什么校验会话</b>：响应里有部署上限与能力开关，属"登录后才告诉你的运行参数"。
 * 匿名可读会让攻击者据此判断某个能力是否启用（例如探测解密能力开没开），
 * 也违背契约的 {@code sessionCookie} 安全方案。
 *
 * <p><b>为什么不做角色过滤</b>：契约把它定义为"仅前端需要读取的非机密配置"，
 * 同一部署里所有登录用户拿到的必须是同一份——否则前端按角色分支读不同的值，
 * 行为差异会变得不可复现。能力是否可用的业务判断在各自接口的权限码里做。
 */
@RestController
public class ClientConfigController {

    private final SessionAuthenticator sessionAuthenticator;
    private final ClientConfigService clientConfigService;

    public ClientConfigController(SessionAuthenticator sessionAuthenticator,
                                  ClientConfigService clientConfigService) {
        this.sessionAuthenticator = sessionAuthenticator;
        this.clientConfigService = clientConfigService;
    }

    /**
     * 返回前端引导所需的非机密配置。
     *
     * <p>先认证再读库：未登录时直接 401，不白跑四张表的查询。
     */
    @GetMapping("/client-config")
    public ResponseEntity<ApiResponses> getClientConfig(HttpServletRequest request) {
        sessionAuthenticator.require(request);
        return ResponseEntity.ok(ApiResponses.success(
                clientConfigService.load(),
                RequestIdFilter.currentTraceIdOrFallback()));
    }
}
