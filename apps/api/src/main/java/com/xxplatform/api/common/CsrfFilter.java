package com.xxplatform.api.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.service.CsrfTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 写请求的统一 CSRF + Origin 闸门（W2 任务 3）。
 *
 * <h2>为什么必须在过滤器里做</h2>
 * 逐个端点写 CSRF 校验，正确性依赖"每个新端点作者都记得加"——W2 之后还有 ~110 个操作要落地，
 * 漏一个就是一个可被跨站伪造的写口。放到过滤器里，规则只有一处：<b>所有</b>
 * {@code POST/PUT/PATCH/DELETE} 默认都要过闸门，新增端点无需任何额外代码。
 *
 * <h2>顺序</h2>
 * {@code HIGHEST_PRECEDENCE + 20}，排在 {@link RequestIdFilter}（{@code +0}，保证拒绝体里有
 * traceId）与 {@code WebConfiguration#developmentCorsFilter}（{@code +10}，先按 Origin 白名单
 * 拒绝跨源请求）之后。CORS 拒绝与这里拒绝用的是同一个 {@code AUTH_CSRF_INVALID}，
 * 调用方看到的语义一致。
 *
 * <h2>两道检查</h2>
 * <ol>
 *   <li><b>Origin/Referer</b>：所有写请求都查，包括白名单端点（登录）——登录同样可能被
 *       跨站伪造，只是它的 token 校验要拿到 {@link CsrfTokenService.CsrfBinding} 才能绑定新会话，
 *       所以 token 留在控制器里做。</li>
 *   <li><b>同步器 token</b>：除 {@code /auth/login} 外都要查。白名单只此一项：
 *       {@code GET /auth/csrf} 是读请求，本来就不进闸门。</li>
 * </ol>
 *
 * <p><b>为什么登录不清 token 检查而是"移到控制器"</b>：{@code POST /auth/login} 通过后要把
 * {@code auth.csrf_session.session_id} 绑到新会话上（契约"登录后轮换"的前提），这需要
 * {@code resolve(...)} 返回的行 id，而不是一个 boolean。控制器做这件事比过滤器多查一遍更省。
 *
 * <h2>失败形状</h2>
 * 统一 {@code 403 AUTH_CSRF_INVALID} 契约错误壳，<b>不区分</b>"没带 Cookie"与"token 不匹配"——
 * 区分会告诉攻击者哪一半猜对了。序列化失败时退回最小合法壳，绝不返回纯文本
 * （纯文本会让前端错误处理抛 JSON 解析异常，把"来源不可信"显示成"网络错误"）。
 *
 * <p><b>与认证的顺序</b>：闸门在认证之前，所以未登录 + 无 token 的写请求现在得到 403 而不是 401。
 * 这是有意的：CSRF 防线不应依赖"是否已登录"，否则攻击者可以先用一个无会话请求探出端点行为。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CsrfFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CsrfFilter.class);

    /** 会改变服务端状态的方法；其余（GET/HEAD/OPTIONS/TRACE）不进闸门。 */
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * 跳过同步器 token 校验的路径（servlet 内路径，不含 {@code /api/v1} 前缀）。
     *
     * <p>只允许存在登录这一个例外：它需要在控制器里拿到 {@code CsrfBinding}。
     * 不要为了方便把新端点加进来——写口一律过闸门才是这个类的意义。
     */
    private static final Set<String> TOKEN_EXEMPT_PATHS = Set.of("/auth/login");

    /** 拒绝话术；与 CORS 拒绝保持一致，且不暴露白名单内容。 */
    private static final String REJECT_MESSAGE = "请求来源不可信";

    private final CsrfTokenService csrfTokenService;
    private final ObjectMapper objectMapper;

    public CsrfFilter(CsrfTokenService csrfTokenService, ObjectMapper objectMapper) {
        this.csrfTokenService = csrfTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = pathWithinApplication(request);
        if (!SameOriginVerifier.isSameOrigin(request)) {
            log.debug("CSRF 闸门拒绝：非可信来源 path={} origin={}",
                    path, request.getHeader("Origin"));
            reject(response, HttpStatus.FORBIDDEN, ErrorCode.AUTH_CSRF_INVALID, REJECT_MESSAGE);
            return;
        }

        if (!TOKEN_EXEMPT_PATHS.contains(path)) {
            String cookieValue = RequestCookies.value(
                    request, csrfTokenService.properties().getCookieName());
            String token = request.getHeader(CsrfTokenService.HEADER_NAME);
            boolean valid;
            try {
                valid = csrfTokenService.validate(cookieValue, token);
            } catch (RuntimeException e) {
                // 查询失败时不能放行（否则数据库抖动 = CSRF 防线整体失效），也不能
                // 让容器错误页替我们返回非契约体。
                log.error("CSRF token 校验失败，按 500 拒绝 path={}", path, e);
                reject(response, HttpStatus.INTERNAL_SERVER_ERROR,
                        ErrorCode.SYSTEM_INTERNAL, "服务内部错误");
                return;
            }
            if (!valid) {
                log.debug("CSRF 闸门拒绝：缺少或无效的 token path={}", path);
                reject(response, HttpStatus.FORBIDDEN, ErrorCode.AUTH_CSRF_INVALID, REJECT_MESSAGE);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 写出契约错误壳。
     *
     * <p>用 {@code OutputStream} 而不是 {@code Writer}：壳里只有 ASCII 码与中文话术，
     * 但长度必须按字节给，交给容器算容易在编码切换时错位。
     */
    private void reject(HttpServletResponse response, HttpStatus status, ErrorCode code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String traceId = RequestIdFilter.currentTraceIdOrFallback();
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ApiResponses.error(code, message, traceId));
        } catch (JsonProcessingException e) {
            // 序列化失败也不能退回纯文本：那正是本类要消除的"前端解析异常 → 网络错误"。
            log.error("CSRF 拒绝响应序列化失败，使用最小响应壳", e);
            body = ("{\"code\":\"" + code.name() + "\",\"message\":\"" + message
                    + "\",\"data\":null,\"traceId\":\"" + traceId + "\",\"details\":[]}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        response.getOutputStream().write(body);
    }

    /**
     * servlet 内路径（去掉 context-path）。
     *
     * <p>本服务 {@code context-path=/api/v1}，{@code getRequestURI()} 是 {@code /api/v1/auth/login}，
     * 而白名单按 servlet 路径匹配（{@code /auth/login}）。CORS 配置踩过同一个坑
     * （写成 {@code /api/v1/**} 一条都匹配不上），这里用 {@code getServletPath()} 最稳，
     * 只有它在非默认映射下有空值风险，才回退到手工裁剪。
     */
    private static String pathWithinApplication(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath;
        }
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri != null && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri == null ? "" : uri;
    }
}
