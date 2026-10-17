package com.xxplatform.api.auth;

import com.xxplatform.api.common.Instants;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组装 {@code GET /me} 的响应数据（任务 6）。
 *
 * <p>只做"取数 + 组形状"，不做授权判定：会话合法性已经在
 * {@link SessionAuthenticator} 判完，范围合成在 {@link ScopeCalculator}。
 * 这里如果再加分支判断，就会出现两套口径。
 *
 * <p><b>为什么整段在一个只读事务里</b>：这里要读 8 张表（角色、权限、菜单、
 * 角色范围开关、角色场所、角色类型、用户范围行、用户场所/类型）。不放进同一事务时，
 * 管理员恰好在读的过程中改了角色，会得到"roleIds 是新的、permissionCodes 是旧的"
 * 这种自相矛盾的组合。{@code readOnly=true} 让 PostgreSQL 走只读事务，
 * 也避免误写。
 */
@Service
public class MeService {

    private static final Logger log = LoggerFactory.getLogger(MeService.class);

    private final RbacRepository rbac;

    public MeService(RbacRepository rbac) {
        this.rbac = rbac;
    }

    /** 读取并组装当前用户的全量视图。调用方必须已通过认证。 */
    @Transactional(readOnly = true)
    public MeView load(AuthenticatedSession session) {
        UUID userId = session.userId();

        List<UUID> roleIds = rbac.roleIds(userId);
        List<String> permissionCodes = rbac.permissionCodes(userId);
        List<MeView.MenuView> menus = menus(userId, permissionCodes);

        RbacRepository.RoleScopeFlags roleFlags = rbac.roleScopeFlags(userId);
        List<RbacRepository.SiteGrant> roleSites = rbac.roleSites(userId);
        List<String> roleDataTypes = rbac.roleDataTypes(userId);

        RbacRepository.UserScopeRow userScope = rbac.userScope(userId).orElse(null);
        // 没有用户范围行时不查它的子表：user_scope_site/user_scope_data_type 的 FK 指向
        // user_scope(user_id)，没有主行就必然没有子行，多查一次纯属浪费一次往返。
        List<RbacRepository.SiteGrant> userSites = userScope == null
                ? List.of() : rbac.userScopeSites(userId);
        List<String> userDataTypes = userScope == null
                ? List.of() : rbac.userScopeDataTypes(userId);

        ScopeCalculator.ScopeResult scope = ScopeCalculator.combine(
                roleFlags, roleSites, roleDataTypes, userScope, userSites, userDataTypes);

        UserAccountRepository.UserAccountRow user = session.user();
        return new MeView(
                new MeView.UserView(user.id(), user.loginName(), user.displayName(), user.status()),
                List.copyOf(roleIds),
                List.copyOf(permissionCodes),
                menus,
                new MeView.DataScopeView(scope.dataSiteIds(), scope.dataAllSites(),
                        scope.dataTypes(), scope.ownOnly(), scope.scopeVersion()),
                new MeView.SiteScopeView(scope.managementSiteIds(), scope.managementAllSites()),
                new MeView.SessionView(session.userId(),
                        Instants.iso(session.idleExpiresAt()),
                        Instants.iso(session.absoluteExpiresAt())));
    }

    /**
     * 菜单映射，并剔除"用户没有所需权限"的菜单。
     *
     * <p>为什么要过滤：{@code auth.menu.required_permission_code} 就是菜单自己声明的准入码。
     * 角色被收回权限后，{@code role_menu} 里的关联往往还在（收权限不等于解绑菜单）。
     * 把这类菜单照常返回，用户会在导航里看到一个点进去必然 403 的入口；
     * 过滤掉只会**减少**可见项，不会扩大任何权限，符合 P-002 未签认前的保守口径。
     *
     * <p>{@code required_permission_code} 为空的菜单一律保留：那是"无需额外权限"的菜单。
     */
    private List<MeView.MenuView> menus(UUID userId, List<String> permissionCodes) {
        Set<String> granted = new LinkedHashSet<>(permissionCodes);
        List<MeView.MenuView> result = new ArrayList<>();
        for (RbacRepository.MenuRow row : rbac.menus(userId)) {
            String required = row.requiredPermissionCode();
            if (required != null && !granted.contains(required)) {
                log.debug("菜单 {} 需要权限 {}，当前用户不具备，已从 /me 结果中剔除", row.code(), required);
                continue;
            }
            result.add(new MeView.MenuView(
                    row.menuId(),
                    row.parentMenuId(),
                    row.code(),
                    row.label(),
                    row.route(),
                    row.sortOrder(),
                    required));
        }
        return List.copyOf(result);
    }
}
