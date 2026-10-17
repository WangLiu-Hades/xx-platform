package com.xxplatform.api.service;

import com.xxplatform.api.infra.RbacRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScopeCalculator} 的规则单测（任务 6）。
 *
 * <p>为什么这些必须用纯单测覆盖，而不是只靠 {@code verify-me.ps1} 的端到端断言：
 * 范围计算的每一个分支都要**特定的数据组合**（用户行有/无、子表有/无、角色开关真/假），
 * 端到端要造这些数据得反复改库，代价高且互相干扰。纯函数单测能一次把所有组合钉住。
 *
 * <p>不用 Mockito：本机沙箱禁止 JVM 自附加（{@code Mockito is unable to load MockMaker}），
 * 而 {@link ScopeCalculator} 本来就是纯静态函数，没有任何依赖需要 mock。
 *
 * <p>这些断言是 P-002 未签认前的**临时口径**（"只能收窄、不能扩权"）。
 * P-002 一旦签认成别的语义（例如多角色取交集），这里会失败——那时应当改规则并同步改断言，
 * 而不是删断言。
 */
class ScopeCalculatorTests {

    /** 让两个 UUID 的字符串序可预测：siteA 排序在前。 */
    private static final UUID SITE_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SITE_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SITE_C = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static RbacRepository.RoleScopeFlags flags(boolean dataAll, boolean mgmtAll, boolean ownOnly) {
        return new RbacRepository.RoleScopeFlags(dataAll, mgmtAll, ownOnly);
    }

    private static RbacRepository.UserScopeRow userScope(boolean dataAll, boolean mgmtAll, boolean ownOnly,
                                                         long version) {
        return new RbacRepository.UserScopeRow(mgmtAll, dataAll, ownOnly, version);
    }

    private static RbacRepository.SiteGrant data(UUID siteId) {
        return new RbacRepository.SiteGrant(siteId, RbacRepository.SiteGrant.DATA);
    }

    private static RbacRepository.SiteGrant management(UUID siteId) {
        return new RbacRepository.SiteGrant(siteId, RbacRepository.SiteGrant.MANAGEMENT);
    }

    @Test
    @DisplayName("没有用户范围行时直接取角色并集，scopeVersion 为 0")
    void withoutUserScopeTakesRoleUnion() {
        var result = ScopeCalculator.combine(
                flags(false, false, false),
                List.of(data(SITE_A), data(SITE_B)),
                List.of("PLATE", "FENCE"),
                null, List.of(), List.of());

        assertFalse(result.dataAllSites());
        assertEquals(List.of(SITE_A, SITE_B), result.dataSiteIds());
        // 类型按 DATA_TYPE_ORDER 排序，不随入参顺序变化
        assertEquals(List.of("FENCE", "PLATE"), result.dataTypes());
        assertFalse(result.ownOnly());
        assertEquals(0L, result.scopeVersion(), "没有用户行时 scopeVersion 归零");
    }

    @Test
    @DisplayName("用户行 data_all_sites=false 会把角色的全量收窄成列表")
    void userScopeNarrowsAllSites() {
        var result = ScopeCalculator.combine(
                flags(true, true, false),
                List.of(data(SITE_A), management(SITE_A), management(SITE_B)),
                List.of("FENCE", "MAC", "PLATE", "FACE"),
                userScope(false, false, false, 7L),
                List.of(data(SITE_A), management(SITE_B)),
                List.of("FENCE", "FACE"));

        assertFalse(result.dataAllSites(), "角色给了全量，但用户明确收窄过，必须为 false");
        assertEquals(List.of(SITE_A), result.dataSiteIds());
        // 管理场所取交集：角色 {A,B} ∩ 用户 {B} = {B}
        assertEquals(List.of(SITE_B), result.managementSiteIds());
        assertEquals(List.of("FENCE", "FACE"), result.dataTypes());
        assertEquals(7L, result.scopeVersion(), "签认后前端按 scopeVersion 判断缓存是否失效");
    }

    @Test
    @DisplayName("用户行存在但没有任何数据场所 = 无场所权限，不是不受限")
    void emptyUserScopeMeansNoAccess() {
        var result = ScopeCalculator.combine(
                flags(true, true, false),
                List.of(data(SITE_A), management(SITE_A)),
                List.of("FENCE"),
                userScope(false, false, false, 3L),
                List.of(),
                List.of());

        assertFalse(result.dataAllSites());
        assertTrue(result.dataSiteIds().isEmpty(), "空集合是'没有任何场所'，不是'全部场所'");
        assertTrue(result.managementSiteIds().isEmpty());
        assertTrue(result.dataTypes().isEmpty(), "没有类型条目 = 无数据类型权限");
    }

    @Test
    @DisplayName("ownOnly 取逻辑或：任一方要求限制就限制")
    void ownOnlyIsOr() {
        // 角色要求 ownOnly，用户放开了 -> 仍然受限（取或）
        assertTrue(ScopeCalculator.combine(flags(false, false, true), List.of(), List.of(),
                userScope(false, false, false, 1L), List.of(), List.of()).ownOnly());

        // 角色放开，用户要求 ownOnly -> 受限
        assertTrue(ScopeCalculator.combine(flags(false, false, false), List.of(), List.of(),
                userScope(false, false, true, 1L), List.of(), List.of()).ownOnly());

        // 两边都放开 -> 不受限
        assertFalse(ScopeCalculator.combine(flags(false, false, false), List.of(), List.of(),
                userScope(false, false, false, 1L), List.of(), List.of()).ownOnly());
    }

    @Test
    @DisplayName("allSites=true 时 siteIds 必须是空数组（契约明文）")
    void allSitesForcesEmptySiteList() {
        var result = ScopeCalculator.combine(
                flags(true, true, false),
                List.of(data(SITE_A), management(SITE_A)),
                List.of("FENCE"),
                null, List.of(), List.of());

        assertTrue(result.dataAllSites());
        assertTrue(result.dataSiteIds().isEmpty(), "allSites=true 时返回列表会违反契约描述");
        assertTrue(result.managementAllSites());
        assertTrue(result.managementSiteIds().isEmpty());
    }

    @Test
    @DisplayName("场所取交集：角色是上限，用户只能在其内选")
    void siteListsIntersect() {
        var result = ScopeCalculator.combine(
                flags(false, false, false),
                List.of(data(SITE_A), data(SITE_B)),
                List.of("FENCE"),
                userScope(false, false, false, 2L),
                // 用户选了 B 和 C：C 不在角色范围内，必须被丢弃
                List.of(data(SITE_B), data(SITE_C)),
                List.of("FENCE"));

        assertEquals(List.of(SITE_B), result.dataSiteIds(), "用户选了角色没有的场所不能生效");
    }

    @Test
    @DisplayName("数据类型恒按 FENCE/MAC/PLATE/FACE 排序，未知码排末尾")
    void dataTypesAreOrderedStably() {
        var result = ScopeCalculator.combine(
                flags(false, false, false),
                List.of(),
                List.of("FACE", "PLATE", "MAC", "FENCE"),
                null, List.of(), List.of());

        assertEquals(List.of("FENCE", "MAC", "PLATE", "FACE"), result.dataTypes());
        assertEquals(ScopeCalculator.DATA_TYPE_ORDER, result.dataTypes());
    }

    @Test
    @DisplayName("MANAGEMENT 与 DATA 两类场所互不串味")
    void siteKindsDoNotLeak() {
        var result = ScopeCalculator.combine(
                flags(false, false, false),
                List.of(data(SITE_A), management(SITE_B)),
                List.of("FENCE"),
                userScope(false, false, false, 1L),
                List.of(data(SITE_B), management(SITE_A)),
                List.of("FENCE"));

        // DATA：角色 {A} ∩ 用户 {B} = 空；MANAGEMENT：角色 {B} ∩ 用户 {A} = 空
        assertTrue(result.dataSiteIds().isEmpty());
        assertTrue(result.managementSiteIds().isEmpty());
    }
}
