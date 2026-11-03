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
 * {@link ScopeCalculator} 的规则单测（任务 6，2026-10-26 按评审 A01 改为元组口径）。
 *
 * <p>为什么这些必须用纯单测覆盖，而不是只靠 {@code verify-me.ps1} 的端到端断言：
 * 范围计算的每一个分支都要**特定的数据组合**（角色几条、用户行有/无、子表有/无、开关真/假），
 * 端到端要造这些数据得反复改库，代价高且互相干扰。纯函数单测能一次把所有组合钉住。
 *
 * <p>不用 Mockito：本机沙箱禁止 JVM 自附加（{@code Mockito is unable to load MockMaker}），
 * 而 {@link ScopeCalculator} 本来就是纯静态函数，没有任何依赖需要 mock。
 *
 * <p><b>这些断言钉的是 A01 的反例</b>：授权单位是 {@code (dataType, siteScope, ownOnly)} 元组，
 * 角色之间、账号附加授权都对元组求并集；<strong>绝不能</strong>先合并 {@code dataTypes} 与
 * {@code siteIds} 再取交集——那会把 {@code PLATE@A} + {@code FACE@B} 变成
 * {@code {PLATE,FACE}×{A,B}} 的笛卡尔扩权。若将来 P-002 签认成别的语义，改规则的同时改断言，
 * 不要删断言。
 */
class ScopeCalculatorTests {

    /** 让 UUID 的字符串序可预测：A < B < C。 */
    private static final UUID SITE_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SITE_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SITE_C = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ROLE_1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ROLE_2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

    private static RbacRepository.RoleScope role(UUID roleId, boolean dataAll, boolean mgmtAll,
                                                 boolean ownOnly) {
        return new RbacRepository.RoleScope(roleId, dataAll, mgmtAll, ownOnly);
    }

    private static RbacRepository.RoleSite roleData(UUID roleId, UUID siteId) {
        return new RbacRepository.RoleSite(roleId, siteId, RbacRepository.SiteGrant.DATA);
    }

    private static RbacRepository.RoleSite roleManagement(UUID roleId, UUID siteId) {
        return new RbacRepository.RoleSite(roleId, siteId, RbacRepository.SiteGrant.MANAGEMENT);
    }

    private static RbacRepository.RoleDataType type(UUID roleId, String dataType) {
        return new RbacRepository.RoleDataType(roleId, dataType);
    }

    private static RbacRepository.UserScopeRow userScope(boolean dataAll, boolean mgmtAll,
                                                         boolean ownOnly, long version) {
        return new RbacRepository.UserScopeRow(mgmtAll, dataAll, ownOnly, version);
    }

    private static RbacRepository.SiteGrant data(UUID siteId) {
        return new RbacRepository.SiteGrant(siteId, RbacRepository.SiteGrant.DATA);
    }

    private static RbacRepository.SiteGrant management(UUID siteId) {
        return new RbacRepository.SiteGrant(siteId, RbacRepository.SiteGrant.MANAGEMENT);
    }

    private static ScopeCalculator.ScopeGrant grant(String dataType, boolean allSites,
                                                    List<UUID> siteIds, boolean ownOnly) {
        return new ScopeCalculator.ScopeGrant(dataType,
                new ScopeCalculator.SiteScope(allSites, siteIds), ownOnly);
    }

    @Test
    @DisplayName("单角色：类型 × 场所 生成元组；没有用户行时 scopeVersion 为 0")
    void singleRoleProducesOneGrantPerType() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, false, false, false)),
                List.of(roleData(ROLE_1, SITE_A), roleData(ROLE_1, SITE_B)),
                List.of(type(ROLE_1, "PLATE"), type(ROLE_1, "FENCE")),
                null, List.of(), List.of());

        assertEquals(List.of(
                grant("FENCE", false, List.of(SITE_A, SITE_B), false),
                grant("PLATE", false, List.of(SITE_A, SITE_B), false)), result.grants());
        assertEquals(List.of("FENCE", "PLATE"), result.dataTypes());
        assertEquals(List.of(SITE_A, SITE_B), result.dataSiteIds());
        assertFalse(result.dataAllSites());
        assertEquals(0L, result.scopeVersion(), "没有用户行时 scopeVersion 归零");
    }

    @Test
    @DisplayName("A01 反例：角色甲 PLATE@A + 角色乙 FACE@B 不得合成 {PLATE,FACE}×{A,B}")
    void rolesDoNotCrossMultiply() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, false, false, false), role(ROLE_2, false, false, false)),
                List.of(roleData(ROLE_1, SITE_A), roleData(ROLE_2, SITE_B)),
                List.of(type(ROLE_1, "PLATE"), type(ROLE_2, "FACE")),
                null, List.of(), List.of());

        assertEquals(List.of(
                grant("PLATE", false, List.of(SITE_A), false),
                grant("FACE", false, List.of(SITE_B), false)), result.grants());
        // 关键断言：没有任何一条元组同时包含 PLATE 与场所 B（或 FACE 与场所 A）
        assertFalse(result.grants().stream().anyMatch(
                g -> g.dataType().equals("PLATE") && g.siteScope().siteIds().contains(SITE_B)),
                "PLATE@B 是谁都没授予的权限，出现即扩权");
        assertFalse(result.grants().stream().anyMatch(
                g -> g.dataType().equals("FACE") && g.siteScope().siteIds().contains(SITE_A)),
                "FACE@A 是谁都没授予的权限，出现即扩权");
        // 旧五字段只是投影：并集可以显示，但服务端鉴权必须用 scopeGrants
        assertEquals(List.of(SITE_A, SITE_B), result.dataSiteIds());
        assertEquals(List.of("PLATE", "FACE"), result.dataTypes());
    }

    @Test
    @DisplayName("ownOnly 逐元组保留，不在角色之间抹平")
    void ownOnlyStaysPerTuple() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, false, false, false), role(ROLE_2, false, false, true)),
                List.of(roleData(ROLE_1, SITE_A), roleData(ROLE_2, SITE_B)),
                List.of(type(ROLE_1, "PLATE"), type(ROLE_2, "FACE")),
                null, List.of(), List.of());

        assertEquals(List.of(
                grant("PLATE", false, List.of(SITE_A), false),
                grant("FACE", false, List.of(SITE_B), true)), result.grants());
        assertTrue(result.ownOnly(), "旧字段取保守投影：任一元组受限就报受限");
    }

    @Test
    @DisplayName("账号附加授权是并集（旧实现当收窄，会让 PLATE@A + FACE@B 变成空集）")
    void accountScopeIsAdditive() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, false, false, false)),
                List.of(roleData(ROLE_1, SITE_A)),
                List.of(type(ROLE_1, "PLATE")),
                userScope(false, false, false, 4L),
                List.of(data(SITE_B)),
                List.of("FACE"));

        assertEquals(List.of(
                grant("PLATE", false, List.of(SITE_A), false),
                grant("FACE", false, List.of(SITE_B), false)), result.grants());
        assertEquals(4L, result.scopeVersion(), "scopeVersion 取用户行，前端据此判断缓存失效");
    }

    @Test
    @DisplayName("账号附加授权可以给全量场所，角色场所不被并集吞掉")
    void accountScopeAllSites() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, false, false, false)),
                List.of(roleData(ROLE_1, SITE_A)),
                List.of(type(ROLE_1, "PLATE")),
                userScope(true, false, false, 2L),
                List.of(),
                List.of("PLATE"));

        // 同一类型在不同场所范围下是两条元组；allSites 的元组排在前面
        assertEquals(List.of(
                grant("PLATE", true, List.of(), false),
                grant("PLATE", false, List.of(SITE_A), false)), result.grants());
        assertFalse(result.dataAllSites(), "并非所有元组都是全量，投影不能声称 allSites");
    }

    @Test
    @DisplayName("用户行存在但没有任何子表条目 = 不追加任何授权（不是全量）")
    void emptyUserScopeAddsNothing() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, false, false, false)),
                List.of(roleData(ROLE_1, SITE_A)),
                List.of(type(ROLE_1, "FENCE")),
                userScope(false, false, false, 3L),
                List.of(),
                List.of());

        assertEquals(List.of(grant("FENCE", false, List.of(SITE_A), false)), result.grants());
        assertEquals(List.of(SITE_A), result.dataSiteIds());
        assertFalse(result.dataAllSites());
        assertEquals(3L, result.scopeVersion());
    }

    @Test
    @DisplayName("角色 allSites=true 时元组为全量，siteIds 投影必须是空数组")
    void roleAllSitesForcesEmptySiteList() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, true, true, false)),
                List.of(roleData(ROLE_1, SITE_A), roleManagement(ROLE_1, SITE_A)),
                List.of(type(ROLE_1, "FENCE"), type(ROLE_1, "MAC"),
                        type(ROLE_1, "PLATE"), type(ROLE_1, "FACE")),
                null, List.of(), List.of());

        assertEquals(4, result.grants().size());
        assertTrue(result.grants().stream().allMatch(g -> g.siteScope().allSites()));
        assertTrue(result.grants().stream().allMatch(g -> g.siteScope().siteIds().isEmpty()));
        assertTrue(result.dataAllSites());
        assertTrue(result.dataSiteIds().isEmpty(), "allSites=true 时返回列表会违反契约描述");
        assertTrue(result.managementAllSites());
        assertTrue(result.managementSiteIds().isEmpty());
        assertEquals(List.of("FENCE", "MAC", "PLATE", "FACE"), result.dataTypes());
    }

    @Test
    @DisplayName("角色既没有全量场所也没有场所条目 = 不产生采集授权")
    void roleWithoutSitesGrantsNothing() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, false, false, false)),
                List.of(),
                List.of(type(ROLE_1, "FENCE")),
                null, List.of(), List.of());

        assertTrue(result.grants().isEmpty(), "空场所不是'不受限'");
        assertFalse(result.dataAllSites());
        assertTrue(result.dataSiteIds().isEmpty());
        assertTrue(result.dataTypes().isEmpty());
    }

    @Test
    @DisplayName("角色没有数据类型 = 不产生采集授权（管理侧仍可生效）")
    void roleWithoutDataTypesGrantsNothing() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, true, false, false)),
                List.of(roleManagement(ROLE_1, SITE_B)),
                List.of(),
                null, List.of(), List.of());

        assertTrue(result.grants().isEmpty());
        assertTrue(result.dataSiteIds().isEmpty());
        assertEquals(List.of(SITE_B), result.managementSiteIds());
        assertFalse(result.managementAllSites());
    }

    @Test
    @DisplayName("账号附加场所与角色场所并集，不做交集裁剪")
    void accountSitesAreUnioned() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, false, false, false)),
                List.of(roleData(ROLE_1, SITE_A)),
                List.of(type(ROLE_1, "FENCE")),
                userScope(false, false, false, 1L),
                List.of(data(SITE_B), data(SITE_C)),
                List.of("FENCE"));

        assertEquals(List.of(
                grant("FENCE", false, List.of(SITE_A), false),
                grant("FENCE", false, List.of(SITE_B, SITE_C), false)), result.grants());
        assertEquals(List.of(SITE_A, SITE_B, SITE_C), result.dataSiteIds());
    }

    @Test
    @DisplayName("重复元组去重：两个角色都给 PLATE@A 只返回一条")
    void duplicateGrantsAreCollapsed() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, false, false, false), role(ROLE_2, false, false, false)),
                List.of(roleData(ROLE_1, SITE_A), roleData(ROLE_2, SITE_A)),
                List.of(type(ROLE_1, "PLATE"), type(ROLE_2, "PLATE")),
                null, List.of(), List.of());

        assertEquals(List.of(grant("PLATE", false, List.of(SITE_A), false)), result.grants());
    }

    @Test
    @DisplayName("数据类型恒按 FENCE/MAC/PLATE/FACE 排序，未知码排末尾")
    void dataTypesAreOrderedStably() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, true, false, false)),
                List.of(),
                List.of(type(ROLE_1, "FACE"), type(ROLE_1, "PLATE"),
                        type(ROLE_1, "MAC"), type(ROLE_1, "FENCE")),
                null, List.of(), List.of());

        assertEquals(ScopeCalculator.DATA_TYPE_ORDER, result.dataTypes());
        assertEquals(ScopeCalculator.DATA_TYPE_ORDER,
                result.grants().stream().map(ScopeCalculator.ScopeGrant::dataType).toList());
    }

    @Test
    @DisplayName("MANAGEMENT 与 DATA 两类场所互不串味，管理侧取并集")
    void siteKindsDoNotLeak() {
        var result = ScopeCalculator.resolve(
                List.of(role(ROLE_1, false, false, false)),
                List.of(roleData(ROLE_1, SITE_A), roleManagement(ROLE_1, SITE_B)),
                List.of(type(ROLE_1, "FENCE")),
                userScope(false, false, false, 1L),
                List.of(data(SITE_C), management(SITE_A)),
                List.of("FENCE"));

        assertEquals(List.of(SITE_A), result.managementSiteIds().stream().limit(1).toList(),
                "管理侧不得混入 DATA 场所");
        assertEquals(List.of(SITE_A, SITE_B), result.managementSiteIds(),
                "管理侧取角色 {B} ∪ 附加 {A}");
        assertEquals(List.of(
                grant("FENCE", false, List.of(SITE_A), false),
                grant("FENCE", false, List.of(SITE_C), false)), result.grants());
    }
}
