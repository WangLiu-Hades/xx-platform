package com.xxplatform.api.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /me} 的 {@code data} 形状，逐字段对齐契约 {@code CurrentUserData}。
 *
 * <p>七个字段在契约里全部 {@code required}，且四处都是 {@code additionalProperties: false}，
 * 所以这里用 record 精确表达、不做通用 Map——少一个字段就是契约违约，
 * 多一个字段也会被契约校验判为非法。
 *
 * <p>{@link UserView#status()} 用 {@code String} 而不是枚举：取值来自
 * {@code auth.app_user.status}（表上 {@code CHECK IN ('ACTIVE','DISABLED')}），
 * 而契约 {@code UserStatus} 只有这两个值。用枚举需要额外的反序列化/校验分支，
 * 收益只是编译期类型，而数据库约束已经是更强的保证。
 */
public record MeView(
        UserView user,
        List<UUID> roleIds,
        List<String> permissionCodes,
        List<MenuView> menus,
        DataScopeView dataScope,
        SiteScopeView siteScope,
        SessionView session) {

    /** 契约 {@code UserSummary}：四个字段全 required。 */
    public record UserView(UUID userId, String loginName, String displayName, String status) {
    }

    /**
     * 契约 {@code Menu}：{@code menuId/code/label/route/order} required，
     * {@code parentMenuId}/{@code requiredPermission} 可选。
     *
     * <p>{@code NON_NULL} 是必须的：{@code requiredPermission} 在契约里是
     * {@code $ref: PermissionCode}（字符串枚举，**不可为 null**），顶层没有菜单时它是 null；
     * 若照常序列化会输出 {@code "requiredPermission": null}，违反"不可空"的约束。
     * 契约对可选字段的约定是"省略表示未提供"，所以省略才是正确表达。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MenuView(
            UUID menuId,
            UUID parentMenuId,
            String code,
            String label,
            String route,
            long order,
            String requiredPermission) {
    }

    /**
     * 契约 {@code DataScope}：旧五字段全 required，{@code scopeGrants} 为可选但本轮**始终返回**。
     *
     * <p>为什么始终返回：契约明文"多元组无法由旧字段精确表达时必返"，而只要有两条以上
     * 不同类型/场所的授权，旧五字段的投影就不精确（{@code dataTypes × siteIds} 会被读成笛卡尔积）。
     * 旧五字段保留是为了不破坏既有前端，仅供 UI 提示，服务端鉴权以 {@code scopeGrants} 为准。
     */
    public record DataScopeView(
            List<UUID> siteIds,
            boolean allSites,
            List<String> dataTypes,
            boolean ownOnly,
            long scopeVersion,
            List<ScopeGrantView> scopeGrants) {
    }

    /**
     * 契约 {@code DataScope.scopeGrants[]}：一条 {@code (dataType, siteScope, ownOnly)} 授权元组。
     *
     * <p>三个字段在契约里都是 required，所以不能为 null；{@code siteScope.allSites=true} 时
     * {@code siteIds} 必为 {@code []}。
     */
    public record ScopeGrantView(String dataType, SiteScopeView siteScope, boolean ownOnly) {
    }

    /** 契约 {@code SiteScope}：两个字段全 required。 */
    public record SiteScopeView(List<UUID> siteIds, boolean allSites) {
    }

    /**
     * 契约 {@code SessionData}：三个字段全 required。
     *
     * <p>时间是格式化好的字符串而不是 {@code OffsetDateTime}，理由见
     * {@link com.xxplatform.api.common.Instants}：对外格式只允许有一处定义。
     */
    public record SessionView(UUID userId, String idleExpiresAt, String absoluteExpiresAt) {
    }
}
