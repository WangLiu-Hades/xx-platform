package com.xxplatform.api.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * M02 取址单测：可信代理下才采信 {@code X-Forwarded-For}，否则一律用 {@code remoteAddr}。
 *
 * <p>为什么这层要单独测：登录来源约束（CIDR）是否成立，取决于"客户端 IP 算得对不对"。
 * 一旦取址被转发头带偏，攻击者写一个 {@code X-Forwarded-For: 10.0.0.1} 就能绕过
 * {@code xx.session.allowed-networks}，而且是静默绕过——HTTP 依旧 200，只有数据库里的
 * 来源字段和审计能看出异常。所以这里把"伪造 XFF 无效""只有可信代理才解析"钉成断言。
 */
class ClientNetworksTests {

    private static final String PROXY = "10.0.0.1";
    private static final List<String> TRUSTED = List.of("10.0.0.0/8");

    @Test
    @DisplayName("CIDR 匹配：内网通过、公网拒绝、非法配置默认拒绝")
    void allowedFollowsCidrAndDefaultsToDeny() {
        assertTrue(ClientNetworks.allowed("10.1.2.3", List.of("10.0.0.0/8")));
        assertTrue(ClientNetworks.allowed("192.168.9.9", List.of("192.168.0.0/16")));
        assertTrue(ClientNetworks.allowed("::1", List.of("::1/128")));
        assertFalse(ClientNetworks.allowed("8.8.8.8", List.of("10.0.0.0/8")));
        // 空白/非法/配置写错 → 默认拒绝（宁可登录失败，也不要"谁都能登录"）
        assertFalse(ClientNetworks.allowed(null, List.of("10.0.0.0/8")));
        assertFalse(ClientNetworks.allowed("", List.of("10.0.0.0/8")));
        assertFalse(ClientNetworks.allowed("10.0.0.1", List.of()));
        assertFalse(ClientNetworks.allowed("10.0.0.999", List.of("10.0.0.0/8")));
        assertFalse(ClientNetworks.allowed("example.com", List.of("10.0.0.0/8")));
        assertFalse(ClientNetworks.allowed("10.0.0.1", List.of("10.0.0.0/abc")));
    }

    @Test
    @DisplayName("非可信对端伪造 X-Forwarded-For 无效，取址仍是 remoteAddr")
    void forwardedHeaderFromUntrustedPeerIsIgnored() {
        // 直连攻击者带 XFF：完全忽略
        assertEquals("203.0.113.9",
                ClientNetworks.resolveClientIp("203.0.113.9", List.of("10.0.0.1"), TRUSTED));
        // 默认配置（可信代理为空）：即使对端是回环也不采信
        assertEquals("127.0.0.1",
                ClientNetworks.resolveClientIp("127.0.0.1", List.of("10.0.0.1"), List.of()));
        assertEquals("127.0.0.1",
                ClientNetworks.resolveClientIp("127.0.0.1", List.of("10.0.0.1"), null));
    }

    @Test
    @DisplayName("可信代理：从右往左跳过可信跳，取第一个不可信地址（左侧伪造无效）")
    void trustedProxyResolvesRightmostUntrustedHop() {
        assertEquals("203.0.113.9",
                ClientNetworks.resolveClientIp(PROXY, List.of("203.0.113.9"), TRUSTED));
        // 攻击者在左侧塞 1.1.1.1，真实客户端 203.0.113.9 由最近一跳写入
        assertEquals("203.0.113.9",
                ClientNetworks.resolveClientIp(PROXY,
                        List.of("1.1.1.1", "203.0.113.9", "10.0.0.7"), TRUSTED));
        // 只有可信跳：返回最左一个（链上最上游）
        assertEquals("10.0.0.9",
                ClientNetworks.resolveClientIp(PROXY, List.of("10.0.0.9", "10.0.0.7"), TRUSTED));
        // 没有转发头：就是对端地址
        assertEquals(PROXY, ClientNetworks.resolveClientIp(PROXY, List.of(), TRUSTED));
        assertEquals(PROXY, ClientNetworks.resolveClientIp(PROXY, null, TRUSTED));
    }

    @Test
    @DisplayName("XFF 畸形条目停止解析，不越过它继续向左找")
    void malformedHopStopsResolution() {
        assertEquals("10.0.0.7",
                ClientNetworks.resolveClientIp(PROXY,
                        List.of("203.0.113.9", "unknown", "10.0.0.7"), TRUSTED));
        // 带端口的 IPv4 条目被归一
        assertEquals("203.0.113.9",
                ClientNetworks.resolveClientIp(PROXY, List.of("203.0.113.9:51234"), TRUSTED));
        // [v6]:port 形式
        assertEquals("2001:db8::1",
                ClientNetworks.resolveClientIp(PROXY, List.of("[2001:db8::1]:443"), TRUSTED));
    }

    @Test
    @DisplayName("同一地址的多种 IPv6 写法按字节相等（避免误踢会话）")
    void sameAddressComparesBytes() {
        assertTrue(ClientNetworks.sameAddress("::1", "0:0:0:0:0:0:0:1"));
        assertTrue(ClientNetworks.sameAddress("10.0.0.1", "10.0.0.1"));
        assertFalse(ClientNetworks.sameAddress("10.0.0.1", "10.0.0.2"));
        assertFalse(ClientNetworks.sameAddress("::1", "10.0.0.1"));
        assertFalse(ClientNetworks.sameAddress(null, "10.0.0.1"));
        assertFalse(ClientNetworks.sameAddress("unknown", "10.0.0.1"));
    }

    @Test
    @DisplayName("伪造 XFF 不能绕过登录 CIDR 判定（组合断言）")
    void forgedForwardedHeaderCannotBypassLoginCidr() {
        List<String> allowed = List.of("10.0.0.0/8");
        // 攻击者从公网直连，自称来自 10.0.0.1
        String resolved = ClientNetworks.resolveClientIp(
                "203.0.113.9", List.of("10.0.0.1"), List.of());
        assertFalse(ClientNetworks.allowed(resolved, allowed),
                "伪造 XFF 不得让公网来源通过 CIDR 检查");
        // 同一请求若来自配置好的可信代理，则采信 XFF 里的真实客户端
        String viaProxy = ClientNetworks.resolveClientIp(
                PROXY, List.of("203.0.113.9"), TRUSTED);
        assertFalse(ClientNetworks.allowed(viaProxy, allowed));
    }
}
