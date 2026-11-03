package com.xxplatform.api.api;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 探针端点：{@code GET /health}（存活）与 {@code GET /ready}（就绪），模块 M15。
 *
 * <p>两者契约都是 {@code x-permission: PUBLIC} + {@code security: []}：**不做会话校验**。
 * 这不是疏忽——探针跑在编排层，没有 SID Cookie，也不该为了探活去登录。它们也不查权限码，
 * 返回内容与调用者身份无关。
 *
 * <p>PUT/POST 到这两个路径会被框架拒绝成 405：契约对每个操作只声明
 * 400/401/403/500/503，405 是"实现比契约多一个状态码"的既有例外（见 README §7）。
 *
 * <p>注意契约里这两个操作的 {@code x-owner} 写的是 {@code DEV-04}，与
 * {@code docs/project-governance/BACKEND_TWO_DEV_SPLIT.md} 的"M15 归后端甲"不一致。
 * 归属以分工方案为准（契约元数据的口径问题已记入 README，待契约方统一）。
 */
@RestController
public class SystemController {

    /** 未就绪时给调用方的可展示文案（不含任何内部细节）。 */
    static final String NOT_READY_MESSAGE = "服务尚未就绪";

    private final HealthService healthService;

    public SystemController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * 存活检查。
     *
     * <p>永远 200：进程能走到这里就说明它没死。失败语义留给编排层（进程退出、端口不通），
     * 不在这里用 {@code DOWN} 制造一个"仍然 200 的不健康响应"。
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponses> getHealth() {
        return ResponseEntity.ok(ApiResponses.success(
                healthService.health(), RequestIdFilter.currentTraceIdOrFallback()));
    }

    /**
     * 依赖就绪检查。
     *
     * <p>全部就绪 → 200 + {@code {status:"READY", checkedAt, dependencies}}。
     *
     * <p>任一依赖未就绪 → **503 {@code SYSTEM_UNAVAILABLE}**，而不是 200 + {@code NOT_READY}。
     * 理由：探针（k8s、负载均衡、部署脚本）只看 HTTP 状态码，一个 200 的"未就绪"会被当成
     * "可以放流量"，等于把请求打给一个连不上数据库的实例。契约同时声明了 200 与 503，
     * 就是把"状态码"和"数据形状"分开用。
     *
     * <p>代价是 503 的错误壳 {@code data:null} 丢掉了 {@code dependencies} 明细，所以未就绪原因
     * 通过 {@code details}（{@code [{field:<依赖名>, reason:<原因>}]}）带回，既保住"哪个依赖坏了"
     * 的可诊断性，又不破坏错误壳的形状。{@code Retry-After} 由 {@code ApiExceptionHandler} 落头。
     */
    @GetMapping("/ready")
    public ResponseEntity<ApiResponses> getReadiness() {
        HealthService.ReadinessResult result = healthService.readiness();
        if (!result.ready()) {
            throw new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, NOT_READY_MESSAGE,
                    result.failures(), HealthService.RETRY_AFTER);
        }
        return ResponseEntity.ok(ApiResponses.success(
                result.view(), RequestIdFilter.currentTraceIdOrFallback()));
    }
}
