package com.xxplatform.api.api;

import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.RequestIdFilter;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 骨架自检端点（仅 {@code dev} profile 生效，生产不注册）。
 *
 * <p>用途：验证"统一成功壳 + traceId + UTF-8 + 时间格式"这条链路确实通了，
 * 也是前端在真实接口就绪前确认代理是否打通的探针。
 *
 * <p>启动：{@code mvn spring-boot:run -Dspring-boot.run.profiles=dev}
 * 访问：{@code GET /api/v1/smoke}（注意 application.yml 已配 context-path=/api/v1）
 *
 * <p>契约自带 {@code GET /health} 与 {@code GET /ready}（M15），
 * 实现完成后本类应删除，不要长期保留两套探针。
 */
@RestController
@Profile("dev")
public class SmokeController {

    @GetMapping("/smoke")
    public ResponseEntity<ApiResponses> smoke() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("profile", "dev");
        // 验证时间序列化：必须是带偏移的 ISO-8601，不是毫秒数字（契约 §3）
        data.put("checkedAt", OffsetDateTime.now());
        data.put("note", "骨架自检：统一成功壳 / traceId / UTF-8");

        return ResponseEntity.ok(ApiResponses.success(data, RequestIdFilter.currentTraceIdOrFallback()));
    }
}
