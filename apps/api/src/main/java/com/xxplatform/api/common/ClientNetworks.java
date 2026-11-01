package com.xxplatform.api.common;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 登录来源网段判定（{@code xx.session.allowed-networks}）。
 *
 * <p><b>为什么需要它</b>：本系统是内网运维/研判平台，登录只应来自管理网段。仅靠防火墙不够——
 * 容器化后端口可能被误发布到办公网，应用层再判一次是"纵深防御"里成本最低的一层。
 *
 * <p><b>为什么自己解析 IP 文本而不是交给 {@link InetAddress#getByName}</b>：{@code getByName}
 * 对非字面量会走 DNS 解析。调用方传进来的是 {@code request.getRemoteAddr()}（通常是字面量），
 * 但一旦某天有人把用户输入接到这里，就可能变成一次外部 DNS 查询（SSRF/信息泄露）。
 * 所以先做字符集白名单（只允许十六进制数字、点、冒号），非字面量直接判不匹配。
 *
 * <p><b>不支持 IPv4-mapped IPv6</b>（{@code ::ffff:10.0.0.1}）：字节长度不同，不会命中
 * IPv4 网段。双栈监听时 Tomcat 通常已把它规范化成 IPv4 字面量；若容器配置成保留映射形式，
 * 需要把允许网段同时写成映射形式。这是有意的显式配置，不做隐式等价转换——隐式转换会让
 * "看似只允许 IPv4 私网"的配置意外放行 IPv6 流量。
 */
public final class ClientNetworks {

    private ClientNetworks() {
    }

    /**
     * 判断来源 IP 是否落在任一允许网段内。
     *
     * <p>空白 IP、空白网段列表、非法字面量一律判 false（默认拒绝）。默认拒绝是刻意的：
     * 配置解析出错时应该"登录不了"而不是"谁都能登录"。
     *
     * @param clientIp 客户端 IP 字面量（应来自 {@code request.getRemoteAddr()}）
     * @param networks CIDR 列表，元素形如 {@code 10.0.0.0/8}
     */
    public static boolean allowed(String clientIp, List<String> networks) {
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        if (networks == null || networks.isEmpty()) {
            return false;
        }
        byte[] address = literal(clientIp);
        if (address == null) {
            return false;
        }
        for (String network : networks) {
            if (matches(address, network)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析客户端 IP：**只有** {@code remoteAddr} 属于可信代理时才采信 {@code X-Forwarded-For}。
     *
     * <p>为什么不能"只要带了 XFF 就用它"：那个头是客户端可以随便写的。直连的攻击者带上
     * {@code X-Forwarded-For: 10.0.0.1} 就能伪装成内网来源，绕过 {@code allowed-networks}
     * 与登录频控的分桶。所以采信的前提是"这一跳确实来自部署方配置的代理"
     * （{@link #isTrustedProxy}），否则整个头被忽略，返回 {@code remoteAddr} 本身。
     *
     * <p>解析顺序：从右往左遍历（最右一跳由离我们最近的代理写入，最不可伪造），
     * 跳过可信代理，第一个不可信地址即客户端。这是 RFC 7239/XFF 的通行做法；
     * 从左往右取会取到攻击者自己填的最左值。
     *
     * <p>遇到无法解析的条目（{@code unknown}、带端口的畸形值）就停下并返回已确认的候选，
     * 不"跳过继续找"——继续往前找等于让攻击者在左侧塞任意值。
     *
     * @param remoteAddr     容器给出的对端地址（{@code request.getRemoteAddr()}）
     * @param forwardedFor   {@code X-Forwarded-For} 按逗号拆开后的条目，可为 null
     * @param trustedProxies 部署配置中的可信代理 CIDR/单地址列表，空 = 永不采信转发头
     * @return 解析出的客户端地址字面量；无输入时返回 {@code remoteAddr}（可能为 null）
     */
    public static String resolveClientIp(String remoteAddr, List<String> forwardedFor,
                                         List<String> trustedProxies) {
        String peer = remoteAddr == null ? null : remoteAddr.trim();
        if (peer == null || peer.isEmpty() || forwardedFor == null || forwardedFor.isEmpty()) {
            return peer;
        }
        if (!isTrustedProxy(peer, trustedProxies)) {
            // 直连客户端（或未声明的代理）：它写的转发头一律不可信。
            return peer;
        }
        String candidate = peer;
        for (int i = forwardedFor.size() - 1; i >= 0; i--) {
            String hop = hopAddress(forwardedFor.get(i));
            if (hop == null) {
                break;
            }
            candidate = hop;
            if (!isTrustedProxy(hop, trustedProxies)) {
                return hop;
            }
        }
        return candidate;
    }

    /** 地址是否属于可信代理列表；空列表恒为 false（默认不信任任何代理）。 */
    public static boolean isTrustedProxy(String address, List<String> trustedProxies) {
        return allowed(address, trustedProxies);
    }

    /**
     * 两个地址字面量是否表示同一个地址（按字节比较）。
     *
     * <p>为什么不直接比字符串：IPv6 允许多种写法（{@code ::1} 与
     * {@code 0:0:0:0:0:0:0:1}），字符串比较会把同一台机器判成"换了客户端"，
     * 进而误踢会话。字节比较对两者等价，且天然覆盖 IPv4。
     */
    public static boolean sameAddress(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] leftBytes = literal(left);
        byte[] rightBytes = literal(right);
        return leftBytes != null && rightBytes != null
                && java.util.Arrays.equals(leftBytes, rightBytes);
    }

    /**
     * 单个 XFF 条目的地址归一：去空白/引号、去掉 {@code host:port} 与 {@code [v6]:port} 的端口。
     *
     * @return 合法的 IP 字面量；不是合法字面量时返回 null（调用方据此停止解析）
     */
    private static String hopAddress(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1).trim();
        }
        if (text.startsWith("[")) {
            int close = text.indexOf(']');
            if (close <= 1) {
                return null;
            }
            text = text.substring(1, close);
        } else if (text.indexOf(':') >= 0 && text.indexOf(':') == text.lastIndexOf(':') && text.indexOf('.') >= 0) {
            // 单个冒号且含点：IPv4:port
            text = text.substring(0, text.indexOf(':'));
        }
        return literal(text) == null ? null : text;
    }

    /** 单条网段匹配；{@code network} 不含 {@code /} 时按单地址（全 1 前缀）处理。 */
    static boolean matches(byte[] address, String network) {
        if (network == null || network.isBlank()) {
            return false;
        }
        String raw = network.trim();
        int slash = raw.indexOf('/');
        String base = slash < 0 ? raw : raw.substring(0, slash);
        int prefix = address.length * 8;
        if (slash >= 0) {
            try {
                prefix = Integer.parseInt(raw.substring(slash + 1).trim());
            } catch (NumberFormatException e) {
                // 配置写错（例如 10.0.0.0/abc）时按"不匹配"处理并让登录 403，
                // 而不是抛异常变成 500：宁可登录失败，也不要把配置错误暴露成服务故障。
                return false;
            }
        }
        byte[] baseAddress = literal(base);
        if (baseAddress == null || baseAddress.length != address.length) {
            return false;
        }
        if (prefix < 0 || prefix > address.length * 8) {
            return false;
        }
        int fullBytes = prefix / 8;
        int remainderBits = prefix % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (address[i] != baseAddress[i]) {
                return false;
            }
        }
        if (remainderBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainderBits);
        return (address[fullBytes] & mask) == (baseAddress[fullBytes] & mask);
    }

    /** IP 字面量 → 字节；非字面量返回 null（绝不触发 DNS）。 */
    static byte[] literal(String value) {
        String text = value.trim();
        if (text.isEmpty() || text.length() > 64) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean allowed = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F')
                    || c == '.'
                    || c == ':';
            if (!allowed) {
                return null;
            }
        }
        try {
            return InetAddress.getByName(text).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
