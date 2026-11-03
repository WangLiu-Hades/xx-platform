package com.xxplatform.api.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.RequestIdFilter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

/**
 * 把"跨源请求被拒"的响应改成契约里规定的统一响应壳。
 *
 * <h2>为什么需要它</h2>
 * Spring 默认的 {@link DefaultCorsProcessor} 在 Origin 不在白名单时，直接
 * {@code 403} 并写一行纯文本 {@code Invalid CORS request}。这有两个问题：
 * <ol>
 *   <li><b>违反契约</b>：{@code contracts/api/api-conventions.md} 要求所有 >400 的响应都是
 *       {@code {code,message,data:null,traceId,details}} 结构，前端统一按 {@code code}
 *       分支处理；纯文本会让前端的错误处理直接抛 JSON 解析异常，用户看到的是
 *       "网络错误"而不是"请求来源不可信"。</li>
 *   <li><b>语义丢失</b>：契约把"来源不可信"固定为 {@code 403 AUTH_CSRF_INVALID}
 *       （{@code /auth/csrf} 的描述明确写了"同源/Origin/Referer 校验"），
 *       纯文本无法表达这个码。</li>
 * </ol>
 *
 * <p>实测确认这不是理论问题：{@code verify-csrf.ps1} 里"外部 Origin 应返回
 * AUTH_CSRF_INVALID"一条最初失败，响应体就是 {@code Invalid CORS request}——
 * 因为 <b>CORS 过滤器跑在控制器之前</b>，控制器里的 Origin 校验根本没有机会执行。
 *
 * <h2>为什么码选 AUTH_CSRF_INVALID 而不是 PERMISSION_DENIED</h2>
 * 这两者语义不同：{@code PERMISSION_DENIED} 是"这个已认证用户没有该操作的权限"；
 * 而来源校验失败时我们**连调用者是谁都不知道**，拒绝的理由是"无法确认这条请求
 * 由本站页面发起"，属于 CSRF 防线。混用会让安全审计无法区分"越权"和"跨站伪造"。
 */
public class ContractCorsProcessor extends DefaultCorsProcessor {

    private static final Logger log = LoggerFactory.getLogger(ContractCorsProcessor.class);

    /** 拒绝时给用户看的话术；不暴露白名单内容，避免帮攻击者试探。 */
    private static final String REJECT_MESSAGE = "请求来源不可信";

    private final ObjectMapper objectMapper;

    public ContractCorsProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Spring 6.2 的签名是单参数（旧版本是 {@code rejectRequest(response, message)}）。
     * 覆写错签名不会编译失败，而是**静默不生效**——仍然返回纯文本，所以这里
     * 必须与 {@code DefaultCorsProcessor} 当前版本的签名完全一致。
     */
    @Override
    protected void rejectRequest(ServerHttpResponse response) throws IOException {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String traceId = RequestIdFilter.currentTraceIdOrFallback();
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(
                    ApiResponses.error(ErrorCode.AUTH_CSRF_INVALID, REJECT_MESSAGE, traceId));
        } catch (JsonProcessingException e) {
            // 序列化失败也不能退回纯文本：那正是本类要消除的行为。
            // 手写一个最小合法壳，字段少但结构正确，前端仍能按 code 分支。
            log.error("CORS 拒绝响应序列化失败，使用最小响应壳", e);
            body = ("{\"code\":\"AUTH_CSRF_INVALID\",\"message\":\"" + REJECT_MESSAGE
                    + "\",\"data\":null,\"traceId\":\"" + traceId + "\",\"details\":[]}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        response.getBody().write(body);
    }
}
