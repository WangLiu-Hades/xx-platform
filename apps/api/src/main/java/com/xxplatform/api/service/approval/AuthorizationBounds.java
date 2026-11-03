package com.xxplatform.api.service.approval;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.CurrentDataScope;
import com.xxplatform.api.service.ScopeCalculator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** P-002/P-021: an approval never grants an exception to the grantor's authorization ceiling. */
final class AuthorizationBounds {
    private final JdbcTemplate jdbc;
    private final RbacRepository rbac;
    private final CurrentDataScope live;

    AuthorizationBounds(JdbcTemplate jdbc, RbacRepository rbac, CurrentDataScope live) {
        this.jdbc = jdbc; this.rbac = rbac; this.live = live;
    }

    void check(UUID actor, ApprovalOperation operation, Map<String, Object> path, Map<String, Object> body) {
        ScopeCalculator.ScopeResult upper = live.scopeOf(actor);
        if (path.get("userId") instanceof String text) {
            UUID target = UUID.fromString(text);
            if (actor.equals(target) && (operation == ApprovalOperation.DELETE_USER
                    || operation == ApprovalOperation.DISABLE_USER)) deny();
            if (actor.equals(target) && operation == ApprovalOperation.UPDATE_USER
                    && !unchangedOwnGrants(actor, body)) deny();
            if ((operation == ApprovalOperation.DELETE_USER || operation == ApprovalOperation.DISABLE_USER)
                    && lastAdministrator(target)) deny();
            checkExistingUser(actor, target);
        }
        for (UUID role : CurrentDataScope.uuids(body.get("roleIds"))) checkRole(actor, role);
        if (path.get("roleId") instanceof String role) checkRole(actor, UUID.fromString(role));
        checkPermissions(actor, CurrentDataScope.strings(body.get("permissionCodes")));
        checkMenus(actor, CurrentDataScope.uuids(body.get("menuIds")));
        if (body.get("siteScope") instanceof Map<?, ?>) siteScope(upper, CurrentDataScope.object(body.get("siteScope")));
        if (body.get("dataScope") instanceof Map<?, ?>) dataScope(upper, CurrentDataScope.object(body.get("dataScope")));
    }

    private void checkExistingUser(UUID actor, UUID target) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM auth.app_user WHERE id=? AND deleted=false)", Boolean.class, target)))
            throw new ApiException(ErrorCode.DATA_NOT_FOUND, "目标用户不可见");
        for (UUID role : rbac.roleIds(target)) checkRole(actor, role);
        ScopeCalculator.ScopeResult targetScope = live.scopeOf(target);
        requireSubset(live.scopeOf(actor), targetScope);
    }

    private boolean unchangedOwnGrants(UUID actor, Map<String, Object> body) {
        if (!new java.util.HashSet<>(CurrentDataScope.uuids(body.get("roleIds")))
                .equals(new java.util.HashSet<>(rbac.roleIds(actor)))) return false;
        RbacRepository.UserScopeRow input = rbac.userScope(actor).orElse(null);
        Map<String, Object> site = CurrentDataScope.object(body.get("siteScope"));
        Map<String, Object> data = CurrentDataScope.object(body.get("dataScope"));
        boolean managementAll = input != null && input.managementAllSites();
        boolean dataAll = input != null && input.dataAllSites();
        boolean ownOnly = input != null && input.ownOnly();
        if (!java.util.Objects.equals(site.get("allSites"), managementAll)
                || !java.util.Objects.equals(data.get("allSites"), dataAll)
                || !java.util.Objects.equals(data.get("ownOnly"), ownOnly)) return false;
        List<RbacRepository.SiteGrant> sites = rbac.userScopeSites(actor);
        return same(CurrentDataScope.uuids(site.get("siteIds")), sites.stream()
                .filter(s -> "MANAGEMENT".equals(s.scopeKind())).map(RbacRepository.SiteGrant::siteId).toList())
                && same(CurrentDataScope.uuids(data.get("siteIds")), sites.stream()
                .filter(s -> "DATA".equals(s.scopeKind())).map(RbacRepository.SiteGrant::siteId).toList())
                && same(CurrentDataScope.strings(data.get("dataTypes")), rbac.userScopeDataTypes(actor))
                && !data.containsKey("scopeGrants");
    }

    private static boolean same(java.util.Collection<?> a, java.util.Collection<?> b) {
        return new java.util.HashSet<>(a).equals(new java.util.HashSet<>(b));
    }

    private void checkRole(UUID actor, UUID role) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT data_all_sites,management_all_sites,own_only FROM auth.role WHERE id=? AND deleted=false", role);
        if (rows.size() != 1) throw new ApiException(ErrorCode.DATA_NOT_FOUND, "角色不存在或不可授予");
        checkPermissions(actor, jdbc.queryForList("SELECT permission_code FROM auth.role_permission WHERE role_id=?", String.class, role));
        checkMenus(actor, jdbc.queryForList("SELECT menu_id FROM auth.role_menu WHERE role_id=?", UUID.class, role));
        Map<String, Object> row = rows.get(0);
        siteScope(live.scopeOf(actor), Map.of("allSites", row.get("management_all_sites"), "siteIds",
                jdbc.queryForList("SELECT site_id FROM auth.role_site WHERE role_id=? AND scope_kind='MANAGEMENT'", UUID.class, role)
                        .stream().map(UUID::toString).toList()));
        dataScope(live.scopeOf(actor), Map.of("allSites", row.get("data_all_sites"), "ownOnly", row.get("own_only"),
                "siteIds", jdbc.queryForList("SELECT site_id FROM auth.role_site WHERE role_id=? AND scope_kind='DATA'", UUID.class, role)
                        .stream().map(UUID::toString).toList(),
                "dataTypes", jdbc.queryForList("SELECT data_type FROM auth.role_data_type WHERE role_id=?", String.class, role)));
    }

    private void checkPermissions(UUID actor, List<String> permissions) {
        if (!rbac.permissionCodes(actor).containsAll(permissions)) deny();
    }
    private void checkMenus(UUID actor, List<UUID> menus) {
        if (!rbac.menus(actor).stream().map(RbacRepository.MenuRow::menuId).toList().containsAll(menus)) deny();
    }
    private static void siteScope(ScopeCalculator.ScopeResult upper, Map<String, Object> scope) {
        boolean all = Boolean.TRUE.equals(scope.get("allSites"));
        List<UUID> sites = CurrentDataScope.uuids(scope.get("siteIds"));
        if (all && !sites.isEmpty() || all && !upper.managementAllSites()
                || !all && !sites.isEmpty() && !CurrentDataScope.managementCovers(upper, sites)) deny();
    }
    private static void dataScope(ScopeCalculator.ScopeResult upper, Map<String, Object> scope) {
        boolean all = Boolean.TRUE.equals(scope.get("allSites"));
        boolean own = Boolean.TRUE.equals(scope.get("ownOnly"));
        List<UUID> sites = CurrentDataScope.uuids(scope.get("siteIds"));
        if (all && !sites.isEmpty()) deny();
        if (scope.get("scopeGrants") instanceof List<?> grants) {
            for (Object raw : grants) {
                Map<String, Object> grant = CurrentDataScope.object(raw);
                Map<String, Object> site = CurrentDataScope.object(grant.get("siteScope"));
                checkTuple(upper, String.valueOf(grant.get("dataType")), Boolean.TRUE.equals(site.get("allSites")),
                        CurrentDataScope.uuids(site.get("siteIds")), Boolean.TRUE.equals(grant.get("ownOnly")));
            }
        } else {
            for (String type : CurrentDataScope.strings(scope.get("dataTypes"))) checkTuple(upper, type, all, sites, own);
        }
    }
    private static void checkTuple(ScopeCalculator.ScopeResult upper, String type, boolean all, List<UUID> sites, boolean own) {
        if (all && !sites.isEmpty()) deny();
        List<ScopeCalculator.ScopeGrant> eligible = upper.grants().stream()
                // Granting to another principal changes the meaning of "own". An actor-relative
                // ownership predicate cannot establish a role/user grant's upper bound.
                .filter(g -> type.equals(g.dataType()) && !g.ownOnly()).toList();
        if (all && eligible.stream().noneMatch(g -> g.siteScope().allSites())) deny();
        for (UUID site : sites)
            if (eligible.stream().noneMatch(g -> g.siteScope().allSites() || g.siteScope().siteIds().contains(site))) deny();
    }
    private static void requireSubset(ScopeCalculator.ScopeResult upper, ScopeCalculator.ScopeResult target) {
        siteScope(upper, Map.of("allSites", target.managementAllSites(), "siteIds",
                target.managementSiteIds().stream().map(UUID::toString).toList()));
        for (ScopeCalculator.ScopeGrant grant : target.grants())
            checkTuple(upper, grant.dataType(), grant.siteScope().allSites(), grant.siteScope().siteIds(), grant.ownOnly());
    }
    private boolean lastAdministrator(UUID target) {
        Boolean administrator = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM auth.user_role ur JOIN auth.role r ON r.id=ur.role_id
                 JOIN auth.app_user u ON u.id=ur.user_id
                 WHERE u.id=? AND u.status='ACTIVE' AND u.deleted=false AND r.role_code='SUPER_ADMIN' AND r.deleted=false)
                """, Boolean.class, target);
        Long count = jdbc.queryForObject("""
                SELECT count(DISTINCT u.id) FROM auth.app_user u JOIN auth.user_role ur ON ur.user_id=u.id
                 JOIN auth.role r ON r.id=ur.role_id
                 WHERE u.status='ACTIVE' AND u.deleted=false AND r.role_code='SUPER_ADMIN' AND r.deleted=false
                """, Long.class);
        return Boolean.TRUE.equals(administrator) && count != null && count <= 1;
    }
    private static void deny() { throw new ApiException(ErrorCode.PERMISSION_DENIED, "授权越过可授予上界或违反自身/最后管理员保护规则"); }
}
