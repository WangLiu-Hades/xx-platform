package com.xxplatform.api.service.approval;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.OpenApiRequests;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.CurrentDataScope;
import com.xxplatform.api.service.ScopeCalculator;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthorizationBoundsTests {
    @Test
    void authoritativeTypedGrantsAreNotExpandedIntoAProjectionCrossProduct() {
        UUID actor = UUID.randomUUID(), siteA = UUID.randomUUID(), siteB = UUID.randomUUID();
        RbacRepository rbac = mock(RbacRepository.class);
        when(rbac.permissionCodes(actor)).thenReturn(List.of("role:grant"));
        when(rbac.menus(actor)).thenReturn(List.of());
        CurrentDataScope live = mock(CurrentDataScope.class);
        when(live.scopeOf(actor)).thenReturn(new ScopeCalculator.ScopeResult(List.of(
                new ScopeCalculator.ScopeGrant("FENCE", ScopeCalculator.SiteScope.of(List.of(siteA)), false),
                new ScopeCalculator.ScopeGrant("FACE", ScopeCalculator.SiteScope.of(List.of(siteB)), false)),
                false, List.of(siteA, siteB), List.of("FENCE", "FACE"), false, 1, false, List.of()));
        Map<String, Object> data = new LinkedHashMap<>(Map.of("allSites", false, "siteIds", List.of(siteA.toString(), siteB.toString()),
                "dataTypes", List.of("FENCE", "FACE"), "ownOnly", false, "scopeVersion", 0,
                "scopeGrants", List.of(
                        Map.of("dataType", "FENCE", "siteScope", Map.of("allSites", false, "siteIds", List.of(siteA.toString())), "ownOnly", false),
                        Map.of("dataType", "FACE", "siteScope", Map.of("allSites", false, "siteIds", List.of(siteB.toString())), "ownOnly", false))));
        Map<String, Object> body = Map.of("roleCode", "SYNTHETIC", "name", "Synthetic typed role", "permissionCodes", List.of(),
                "menuIds", List.of(), "siteScope", Map.of("allSites", false, "siteIds", List.of()), "dataScope", data);
        OpenApiRequests.validate("RoleInput", body, "body");
        AuthorizationBounds bounds = new AuthorizationBounds(new JdbcTemplate(), rbac, live);
        assertDoesNotThrow(() -> bounds.check(actor, ApprovalOperation.CREATE_ROLE, Map.of(), body));
        data.remove("scopeGrants");
        assertThrows(ApiException.class, () -> bounds.check(actor, ApprovalOperation.CREATE_ROLE, Map.of(), body));
        when(live.scopeOf(actor)).thenReturn(new ScopeCalculator.ScopeResult(List.of(
                new ScopeCalculator.ScopeGrant("FENCE", ScopeCalculator.SiteScope.of(List.of(siteA)), true)),
                false, List.of(siteA), List.of("FENCE"), true, 1, false, List.of()));
        data.put("siteIds", List.of(siteA.toString()));
        data.put("dataTypes", List.of("FENCE"));
        data.put("ownOnly", true);
        assertThrows(ApiException.class, () -> bounds.check(actor, ApprovalOperation.CREATE_ROLE, Map.of(), body));
    }
}
