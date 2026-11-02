package com.xxplatform.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.OpenApiRequests;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.infra.AnalysisContextRepository.ContextRow;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.task.TaskParameters;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Live authorization facts shared by approvals, task submission/publication and downloads. */
@Component
public class CurrentDataScope {
    private final JdbcTemplate jdbc;
    private final RbacRepository rbac;
    private final ObjectMapper mapper = new ObjectMapper();

    public CurrentDataScope(JdbcTemplate jdbc, RbacRepository rbac) { this.jdbc = jdbc; this.rbac = rbac; }

    public ScopeCalculator.ScopeResult scopeOf(UUID userId) {
        return ScopeCalculator.resolve(rbac.roleScopes(userId), rbac.roleSiteGrants(userId),
                rbac.roleDataTypes(userId), rbac.userScope(userId).orElse(null),
                rbac.userScopeSites(userId), rbac.userScopeDataTypes(userId));
    }

    public boolean active(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM auth.app_user WHERE id=? AND status='ACTIVE' AND deleted=false)",
                Boolean.class, userId));
    }

    public long authzVersion(UUID userId) {
        List<Long> versions = jdbc.queryForList(
                "SELECT authz_version FROM auth.app_user WHERE id=? AND deleted=false", Long.class, userId);
        return versions.isEmpty() ? -1 : versions.get(0);
    }

    /** Includes the actual tuple grants, so a missed version increment cannot silently widen access. */
    public String stamp(UUID userId) {
        ScopeCalculator.ScopeResult scope = scopeOf(userId);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("active", active(userId));
        facts.put("authzVersion", authzVersion(userId));
        facts.put("scopeVersion", scope.scopeVersion());
        facts.put("permissions", rbac.permissionCodes(userId));
        facts.put("managementAllSites", scope.managementAllSites());
        facts.put("managementSiteIds", scope.managementSiteIds().stream().map(UUID::toString).sorted().toList());
        facts.put("scopeGrants", scope.grants().stream().map(g -> Map.of("dataType", g.dataType(),
                "allSites", g.siteScope().allSites(), "siteIds", g.siteScope().siteIds().stream()
                        .map(UUID::toString).sorted().toList(), "ownOnly", g.ownOnly())).toList());
        return Sha256.hex(Sha256.ofBytes(TaskParameters.canonicalJson(facts).getBytes(StandardCharsets.UTF_8)));
    }

    public Map<String, Object> filter(ContextRow context) {
        try {
            Map<String, Object> result = mapper.readValue(context.filterSnapshot(), new TypeReference<>() { });
            OpenApiRequests.validate("ContextFilter", result, "context.filter");
            if (!context.filterType().equals(result.get("filterType"))
                    || !(result.get("filterVersion") instanceof Number v)
                    || v.longValue() != context.filterSchemaVersion()) return Map.of();
            return result;
        } catch (java.io.IOException | ApiException | IllegalArgumentException e) { return Map.of(); }
    }

    public boolean contextAllowed(UUID userId, ContextRow context) {
        return contextAllowed(userId, context, true);
    }

    public boolean contextAllowed(UUID userId, ContextRow context, boolean checkVersion) {
        if (!active(userId) || !userId.equals(context.ownerUserId())
                || checkVersion && context.permissionVersion() != authzVersion(userId)) return false;
        Map<String, Object> filter = filter(context);
        if (filter.isEmpty()) return false;
        // Other domain Contexts need their own resource resolver; absence is never global access.
        if (!List.of("GENERAL", "CAPTURE_SEARCH").contains(context.filterType())) return false;
        Map<String, Object> query = filter;
        List<String> types;
        if ("CAPTURE_SEARCH".equals(context.filterType())) {
            query = object(filter.get("captureFilter"));
            types = List.of(String.valueOf(query.get("dataType")));
        } else {
            types = strings(filter.get("dataTypes"));
            if (types.isEmpty()) types = ScopeCalculator.DATA_TYPE_ORDER;
        }
        ScopeCalculator.ScopeResult scope = scopeOf(userId);
        List<UUID> sites = uuids(query.get("siteIds"));
        for (String type : types) {
            List<ScopeCalculator.ScopeGrant> grants = scope.grants().stream()
                    // A Context owner is not proof of ownership of its underlying capture records.
                    // No capture ownership resolver is bound in this skeleton; do not turn ownOnly
                    // into unrestricted capture access, including through another type's grant.
                    .filter(g -> type.equals(g.dataType()) && !g.ownOnly() && !g.siteScope().deniesAll()).toList();
            if (grants.isEmpty()) return false;
            for (UUID site : sites)
                if (grants.stream().noneMatch(g -> g.siteScope().allSites() || g.siteScope().siteIds().contains(site)))
                    return false;
        }
        for (UUID deviceId : uuids(query.get("deviceIds"))) {
            List<UUID> deviceSites = jdbc.queryForList(
                    "SELECT site_id FROM core.device WHERE id=? AND deleted=false", UUID.class, deviceId);
            if (deviceSites.size() != 1) return false;
            for (String type : types) if (!allows(scope, type, deviceSites.get(0), false)) return false;
        }
        // Explicit record IDs are checked against real full records, never Context.targets previews.
        for (UUID captureId : uuids(query.get("sourceCaptureIds"))) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT data_type,site_id FROM capture.capture_event WHERE id=? AND status='VALID'", captureId);
            if (rows.size() != 1 || !allows(scope, String.valueOf(rows.get(0).get("data_type")),
                    (UUID) rows.get(0).get("site_id"), false)) return false;
        }
        return true;
    }

    /** ownOnly must remain attached to each tuple; it is never ORed into another tuple. */
    public static boolean allows(ScopeCalculator.ScopeResult scope, String type, UUID siteId, boolean owned) {
        if (siteId == null) return false;
        return scope.grants().stream().anyMatch(g -> type.equals(g.dataType()) && (!g.ownOnly() || owned)
                && (g.siteScope().allSites() || g.siteScope().siteIds().contains(siteId)));
    }

    public static boolean managementCovers(ScopeCalculator.ScopeResult scope, Collection<UUID> sites) {
        return scope.managementAllSites() || !sites.isEmpty() && scope.managementSiteIds().containsAll(sites);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object raw) {
        return raw instanceof Map<?, ?> ? (Map<String, Object>) raw : Map.of();
    }
    public static List<String> strings(Object raw) {
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }
    public static List<UUID> uuids(Object raw) {
        List<UUID> ids = new ArrayList<>();
        for (String value : strings(raw)) ids.add(UUID.fromString(value));
        return List.copyOf(ids);
    }
}
