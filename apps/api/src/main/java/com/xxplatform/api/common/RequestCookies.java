package com.xxplatform.api.common;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 读请求 Cookie 的小工具。
 *
 * <p>Servlet 没有"按名字取 Cookie"的 API，只有 {@code getCookies()} 数组，所以每个
 * 需要读 SID / CSRFID 的地方都得自己遍历。集中到这里，避免两处实现出现
 * "一处大小写敏感、一处不敏感"这种只在某个端点复现的诡异行为。
 *
 * <p><b>Cookie 名区分大小写</b>（RFC 6265 的 name 是大小写敏感的 token），这里严格相等比较。
 */
public final class RequestCookies {

    private RequestCookies() {
    }

    /** 取指定名字的 Cookie 值；不存在（或请求无 Cookie）返回 {@code null}。 */
    public static String value(HttpServletRequest request, String name) {
        if (request == null || name == null) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
