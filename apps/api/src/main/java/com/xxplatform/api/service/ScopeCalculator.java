package com.xxplatform.api.service;

import com.xxplatform.api.infra.RbacRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 把"角色范围"与"用户显式范围"合成为 {@code /me} 返回的有效范围。
 *
 * <p><b>为什么组合规则要先声明再实现</b>：契约里 {@code DataScope} 与 {@code SiteScope}
 * 都带 {@code x-design-status: pending}，reason 指向 **P-002**（"跨模块场所范围组合、
 * 角色默认授权与权限收缩时在途任务处理待签认"）。{@code ADR-007} 与 SD06 的要求是：
 * <b>P-002 签认前不合并、不扩大权限</b>。所以这里实现的是"只能收窄"的保守规则，
 * 并在 README 与开工清单里登记为 P-002 的临时口径，而不是当成已签认的最终策略。
 *
 * <p>规则（逐条都有依据）：
 * <ol>
 *   <li><b>角色之间取并集</b>。每个 {@code role_permission}/{@code role_site}/
 *       {@code role_data_type} 行都是管理员显式授予的，不被并集覆盖才是"意外缩权"。
 *       多角色的最终策略属 P-002，若将来改为交集，只需改这一个方法。</li>
 *   <li><b>{@code allSites} 取"角色允许 且 用户未收窄"</b>（逻辑与）。用户行写下
 *       {@code data_all_sites=false} 就是明确表示"不要全部"，此时角色给的全量必须被收窄掉。</li>
 *   <li><b>场所列表取交集</b>（角色并集 ∩ 用户列表）。角色是上限，用户行只能在其内选。</li>
 *   <li><b>{@code ownOnly} 取逻辑或</b>。它是"只能看自己的数据"这类限制，
 *       任一来源要求限制就必须限制——取与会把限制取消掉，属扩权。</li>
 *   <li><b>{@code allSites=true} 时 {@code siteIds} 必须为空数组</b>。这是契约
 *       {@code DataScope}/{@code SiteScope} 的描述明文（"allSites=true时siteIds必须[]"）。</li>
 *   <li><b>用户行存在但没有任何类型/场所条目 = 没有该方面权限</b>，不是"不受限"。
 *       依据是同一段描述里"false 且 siteIds 为空表示无场所权限"，按最小权限取同一口径。</li>
 *   <li><b>没有用户行 = 管理员没做收窄</b>，直接取角色并集。这是唯一一处"null 比空集合宽松"
 *       的地方，出现在 {@code /me} 与用户详情，语义必须一致。</li>
 * </ol>
 */
public final class ScopeCalculator {

    /** {@code DataType} 枚举的稳定顺序，避免前端拿到的类型顺序随查询计划变化。 */
    public static final List<String> DATA_TYPE_ORDER = List.of("FENCE", "MAC", "PLATE", "FACE");

    private ScopeCalculator() {
    }

    /**
     * 合成有效范围。
     *
     * @param roleFlags      角色范围开关的并集
     * @param roleSites      角色授予的场所（含 MANAGEMENT/DATA 两类）
     * @param roleDataTypes  角色授予的数据类型
     * @param userScope      用户显式范围行；{@code null} 表示未做收窄
     * @param userSites      用户显式场所（仅在 {@code userScope != null} 时有意义）
     * @param userDataTypes  用户显式数据类型（同上）
     */
    public static ScopeResult combine(RbacRepository.RoleScopeFlags roleFlags,
                                      Collection<RbacRepository.SiteGrant> roleSites,
                                      Collection<String> roleDataTypes,
                                      RbacRepository.UserScopeRow userScope,
                                      Collection<RbacRepository.SiteGrant> userSites,
                                      Collection<String> userDataTypes) {
        boolean narrowed = userScope != null;

        boolean dataAllSites = roleFlags.dataAllSites() && (!narrowed || userScope.dataAllSites());
        boolean managementAllSites = roleFlags.managementAllSites()
                && (!narrowed || userScope.managementAllSites());
        // 限制取或：任一来源说"只看自己的"就必须只看自己的，取与等于取消限制（扩权）
        boolean ownOnly = roleFlags.ownOnly() || (narrowed && userScope.ownOnly());
        long scopeVersion = narrowed ? userScope.scopeVersion() : 0L;

        Set<UUID> roleData = sitesOfKind(roleSites, RbacRepository.SiteGrant.DATA);
        Set<UUID> roleManagement = sitesOfKind(roleSites, RbacRepository.SiteGrant.MANAGEMENT);
        Set<UUID> userData = narrowed ? sitesOfKind(userSites, RbacRepository.SiteGrant.DATA) : null;
        Set<UUID> userManagement = narrowed
                ? sitesOfKind(userSites, RbacRepository.SiteGrant.MANAGEMENT) : null;

        List<UUID> dataSiteIds = dataAllSites ? List.of() : ordered(intersect(roleData, userData));
        List<UUID> managementSiteIds = managementAllSites
                ? List.of() : ordered(intersect(roleManagement, userManagement));

        List<String> dataTypes = sortedDataTypes(intersect(roleDataTypes, narrowed ? userDataTypes : null));

        return new ScopeResult(dataAllSites, dataSiteIds, dataTypes, ownOnly, scopeVersion,
                managementAllSites, managementSiteIds);
    }

    private static Set<UUID> sitesOfKind(Collection<RbacRepository.SiteGrant> grants, String kind) {
        Set<UUID> result = new LinkedHashSet<>();
        if (grants == null) {
            return result;
        }
        for (RbacRepository.SiteGrant grant : grants) {
            if (grant != null && kind.equals(grant.scopeKind()) && grant.siteId() != null) {
                result.add(grant.siteId());
            }
        }
        return result;
    }

    /**
     * 交集；{@code narrowing == null} 表示不收窄，原样返回 {@code base}。
     *
     * <p>注意"收窄集合为空"与"不收窄"是两种不同结果：前者得到空集（无权限），
     * 后者得到 {@code base}。这正是第 6/7 条规则要区分的东西。
     */
    private static Set<UUID> intersect(Set<UUID> base, Set<UUID> narrowing) {
        if (narrowing == null) {
            return base;
        }
        Set<UUID> result = new LinkedHashSet<>(base);
        result.retainAll(narrowing);
        return result;
    }

    private static Set<String> intersect(Collection<String> base, Collection<String> narrowing) {
        Set<String> baseSet = base == null ? Set.of() : new LinkedHashSet<>(base);
        if (narrowing == null) {
            return baseSet;
        }
        baseSet.retainAll(new LinkedHashSet<>(narrowing));
        return baseSet;
    }

    /** UUID 列表按字符串序排序，保证同一份授权每次返回顺序一致。 */
    private static List<UUID> ordered(Collection<UUID> values) {
        Set<UUID> sorted = new TreeSet<>((a, b) -> a.toString().compareTo(b.toString()));
        sorted.addAll(values);
        return List.copyOf(sorted);
    }

    /** 按 {@link #DATA_TYPE_ORDER} 排序；不在枚举内的码排在末尾并按字母序。 */
    private static List<String> sortedDataTypes(Collection<String> values) {
        List<String> result = new ArrayList<>(values == null ? Set.of() : values);
        result.sort((a, b) -> {
            int ia = DATA_TYPE_ORDER.indexOf(a);
            int ib = DATA_TYPE_ORDER.indexOf(b);
            if (ia < 0 && ib < 0) {
                return a.compareTo(b);
            }
            if (ia < 0) {
                return 1;
            }
            if (ib < 0) {
                return -1;
            }
            return Integer.compare(ia, ib);
        });
        return List.copyOf(result);
    }

    /**
     * 合成结果。
     *
     * <p>{@code data*} 面向 {@code DataScope}（采集数据可跨哪些场所、哪些数据类型），
     * {@code management*} 面向 {@code SiteScope}（管理操作可见哪些场所）。
     */
    public record ScopeResult(
            boolean dataAllSites,
            List<UUID> dataSiteIds,
            List<String> dataTypes,
            boolean ownOnly,
            long scopeVersion,
            boolean managementAllSites,
            List<UUID> managementSiteIds) {
    }
}
