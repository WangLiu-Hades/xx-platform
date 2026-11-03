package com.xxplatform.api.service;

import com.xxplatform.api.infra.RbacRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 把"角色范围"与"账号附加授权"合成为 {@code /me} 返回的有效范围。
 *
 * <p><b>授权单位是元组，不是三个维度</b>：{@code (dataType, siteScope, ownOnly)} 三元组才是
 * 一条授权。评审 A01 的原始缺陷就是把三列先各自合并（{@code bool_or} / 场所并集 / 类型并集）
 * 再取交集：角色甲给 {@code PLATE@A}、角色乙给 {@code FACE@B} 时，合并后变成
 * {@code {PLATE,FACE} × {A,B}}，凭空多出 {@code PLATE@B} 与 {@code FACE@A} 两条谁都没授予的
 * 权限。所以这里<strong>逐角色</strong>还原元组，再对元组集合求并集。
 *
 * <p>规则（逐条都有依据，改之前先看依据）：
 * <ol>
 *   <li><b>角色之间取元组并集</b>。每个 {@code role_site}/{@code role_data_type} 行都是管理员
 *       显式授予的，被并集覆盖才是"意外缩权"。</li>
 *   <li><b>一个角色的元组 = 它的数据类型 × 它的场所范围</b>。数据库里 {@code role_site} 没有
 *       数据类型维度（一张表只记 role→site），所以"该角色的每个数据类型都在这些场所生效"是
 *       模型本身能表达的上限；跨角色不会相乘，这正是 A01 要求的效果。</li>
 *   <li><b>账号附加授权（{@code auth.user_scope*}）取并集，只加不减</b>。契约 {@code DataScope}
 *       的描述是"按元组取角色并集及账号附加授权，不分别合并维度后扩大授权"，暂定口径
 *       {@code userScopeMode=additive_union_of_typed_scope_tuples}。旧实现把用户行当"收窄"，
 *       与契约相反；真正的缩权走 P-002-05 的撤会话/升 {@code scope_version} 流程。</li>
 *   <li><b>{@code ownOnly} 逐元组保留</b>，不再跨角色取或去覆盖别的元组；旧五字段里的
 *       {@code ownOnly} 只作 UI 提示，取"任一元组为 true"的保守投影。</li>
 *   <li><b>{@code allSites=true} 时 {@code siteIds} 必须为空数组</b>（契约 {@code SiteScope}
 *       明文），所以 {@link SiteScope#ALL} 固定带 `[]`。</li>
 *   <li><b>{@code allSites=false} 且没有任何场所 = 该元组不给场所权限</b>（契约"false 且 [] 无场所"），
 *       这类元组直接丢弃，不生成"看起来能看全部"的空授权。</li>
 *   <li><b>角色没有任何数据类型 = 不产生采集数据授权</b>；管理侧（{@code siteScope}）没有类型维度，
 *       按 {@code management_all_sites} 与 {@code MANAGEMENT} 场所求并。</li>
 * </ol>
 *
 * <p>旧五字段（{@code siteIds}/{@code allSites}/{@code dataTypes}/{@code ownOnly}/{@code scopeVersion}）
 * 仍按契约返回，但只是元组集合的<strong>投影</strong>，仅用于 UI 提示；契约明文要求服务端鉴权
 * 以 {@code scopeGrants} 为准，禁止再拿合并后的 {@code dataTypes × siteIds} 做笛卡尔匹配。
 */
public final class ScopeCalculator {

    /** {@code DataType} 枚举的稳定顺序，避免前端拿到的类型顺序随查询计划变化。 */
    public static final List<String> DATA_TYPE_ORDER = List.of("FENCE", "MAC", "PLATE", "FACE");

    private ScopeCalculator() {
    }

    /**
     * 合成有效范围。
     *
     * @param roles          每个角色一行的范围开关（不聚合）
     * @param roleSites      角色授予的场所，带 {@code roleId}
     * @param roleDataTypes  角色授予的数据类型，带 {@code roleId}
     * @param userScope      账号附加授权行；{@code null} 表示没有附加授权
     * @param userSites      账号附加的场所（仅在 {@code userScope != null} 时有意义）
     * @param userDataTypes  账号附加的数据类型（同上）
     */
    public static ScopeResult resolve(List<RbacRepository.RoleScope> roles,
                                      Collection<RbacRepository.RoleSite> roleSites,
                                      Collection<RbacRepository.RoleDataType> roleDataTypes,
                                      RbacRepository.UserScopeRow userScope,
                                      Collection<RbacRepository.SiteGrant> userSites,
                                      Collection<String> userDataTypes) {
        Map<UUID, Set<UUID>> dataSitesByRole = new LinkedHashMap<>();
        Set<UUID> managementSites = new LinkedHashSet<>();
        for (RbacRepository.RoleSite site : nullSafe(roleSites)) {
            if (site == null || site.roleId() == null || site.siteId() == null) {
                continue;
            }
            if (RbacRepository.SiteGrant.MANAGEMENT.equals(site.scopeKind())) {
                managementSites.add(site.siteId());
            } else if (RbacRepository.SiteGrant.DATA.equals(site.scopeKind())) {
                dataSitesByRole.computeIfAbsent(site.roleId(), key -> new LinkedHashSet<>())
                        .add(site.siteId());
            }
        }
        Map<UUID, List<String>> typesByRole = new LinkedHashMap<>();
        for (RbacRepository.RoleDataType entry : nullSafe(roleDataTypes)) {
            if (entry == null || entry.roleId() == null || entry.dataType() == null) {
                continue;
            }
            typesByRole.computeIfAbsent(entry.roleId(), key -> new ArrayList<>())
                    .add(entry.dataType());
        }

        List<ScopeGrant> grants = new ArrayList<>();
        boolean managementAllSites = false;

        for (RbacRepository.RoleScope role : nullSafe(roles)) {
            if (role == null) {
                continue;
            }
            managementAllSites = managementAllSites || role.managementAllSites();
            List<String> types = sortedDataTypes(typesByRole.getOrDefault(role.roleId(), List.of()));
            if (types.isEmpty()) {
                continue;
            }
            SiteScope siteScope = role.dataAllSites()
                    ? SiteScope.ALL
                    : SiteScope.of(dataSitesByRole.getOrDefault(role.roleId(), Set.of()));
            if (siteScope.deniesAll()) {
                continue;
            }
            for (String type : types) {
                grants.add(new ScopeGrant(type, siteScope, role.ownOnly()));
            }
        }

        if (userScope != null) {
            managementAllSites = managementAllSites || userScope.managementAllSites();
            managementSites.addAll(sitesOfKind(userSites, RbacRepository.SiteGrant.MANAGEMENT));
            List<String> types = sortedDataTypes(userDataTypes);
            if (!types.isEmpty()) {
                SiteScope siteScope = userScope.dataAllSites()
                        ? SiteScope.ALL
                        : SiteScope.of(sitesOfKind(userSites, RbacRepository.SiteGrant.DATA));
                if (!siteScope.deniesAll()) {
                    for (String type : types) {
                        grants.add(new ScopeGrant(type, siteScope, userScope.ownOnly()));
                    }
                }
            }
        }

        List<ScopeGrant> merged = normalize(grants);
        boolean dataAllSites = !merged.isEmpty()
                && merged.stream().allMatch(grant -> grant.siteScope().allSites());
        List<UUID> dataSiteIds = dataAllSites ? List.of() : ordered(unionDataSites(merged));
        List<String> dataTypes = orderedTypes(merged);
        boolean ownOnly = merged.stream().anyMatch(ScopeGrant::ownOnly);
        List<UUID> managementSiteIds = managementAllSites ? List.of() : ordered(managementSites);
        long scopeVersion = userScope == null ? 0L : userScope.scopeVersion();

        return new ScopeResult(merged, dataAllSites, dataSiteIds, dataTypes, ownOnly, scopeVersion,
                managementAllSites, managementSiteIds);
    }

    /** 去重（同一元组可能被多个角色或账号附加授权重复给出）并按稳定顺序排列。 */
    private static List<ScopeGrant> normalize(List<ScopeGrant> grants) {
        Map<String, ScopeGrant> unique = new LinkedHashMap<>();
        for (ScopeGrant grant : grants) {
            unique.putIfAbsent(grant.key(), grant);
        }
        List<ScopeGrant> result = new ArrayList<>(unique.values());
        result.sort((a, b) -> {
            int byType = Integer.compare(typeOrder(a.dataType()), typeOrder(b.dataType()));
            if (byType != 0) {
                return byType;
            }
            if (a.siteScope().allSites() != b.siteScope().allSites()) {
                return a.siteScope().allSites() ? -1 : 1;
            }
            int bySites = a.siteScope().siteIds().toString()
                    .compareTo(b.siteScope().siteIds().toString());
            if (bySites != 0) {
                return bySites;
            }
            return Boolean.compare(a.ownOnly(), b.ownOnly());
        });
        return List.copyOf(result);
    }

    private static int typeOrder(String dataType) {
        int index = DATA_TYPE_ORDER.indexOf(dataType);
        return index < 0 ? DATA_TYPE_ORDER.size() : index;
    }

    private static Set<UUID> unionDataSites(Collection<ScopeGrant> grants) {
        Set<UUID> result = new LinkedHashSet<>();
        for (ScopeGrant grant : grants) {
            result.addAll(grant.siteScope().siteIds());
        }
        return result;
    }

    private static List<String> orderedTypes(Collection<ScopeGrant> grants) {
        Set<String> types = new LinkedHashSet<>();
        for (ScopeGrant grant : grants) {
            types.add(grant.dataType());
        }
        return sortedDataTypes(types);
    }

    private static Set<UUID> sitesOfKind(Collection<RbacRepository.SiteGrant> grants, String kind) {
        Set<UUID> result = new LinkedHashSet<>();
        for (RbacRepository.SiteGrant grant : nullSafe(grants)) {
            if (grant != null && kind.equals(grant.scopeKind()) && grant.siteId() != null) {
                result.add(grant.siteId());
            }
        }
        return result;
    }

    private static <T> Collection<T> nullSafe(Collection<T> values) {
        return values == null ? List.of() : values;
    }

    /** UUID 列表按字符串序排序，保证同一份授权每次返回顺序一致。 */
    private static List<UUID> ordered(Collection<UUID> values) {
        Set<UUID> sorted = new TreeSet<>((a, b) -> a.toString().compareTo(b.toString()));
        sorted.addAll(values);
        return List.copyOf(sorted);
    }

    /** 去重并按 {@link #DATA_TYPE_ORDER} 排序；不在枚举内的码排在末尾并按字母序。 */
    private static List<String> sortedDataTypes(Collection<String> values) {
        List<String> result = new ArrayList<>(values == null ? Set.<String>of() : new LinkedHashSet<>(values));
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

    /** 一条元组的场所范围：{@code allSites=true} 时 {@code siteIds} 必为空（契约明文）。 */
    public record SiteScope(boolean allSites, List<UUID> siteIds) {

        /** 全部场所。 */
        public static final SiteScope ALL = new SiteScope(true, List.of());

        public SiteScope {
            siteIds = siteIds == null ? List.of() : List.copyOf(siteIds);
        }

        /** 构造"仅这些场所"的范围（空集合 = 无场所权限）。 */
        public static SiteScope of(Collection<UUID> siteIds) {
            return new SiteScope(false, ordered(siteIds));
        }

        /** {@code allSites=false} 且没有任何场所 = 不授权任何场所。 */
        public boolean deniesAll() {
            return !allSites && siteIds.isEmpty();
        }
    }

    /** 一条授权元组：{@code dataType} 在这些场所（或全部场所）可见，可能只限本人数据。 */
    public record ScopeGrant(String dataType, SiteScope siteScope, boolean ownOnly) {

        public ScopeGrant {
            siteScope = siteScope == null ? SiteScope.ALL : siteScope;
        }

        /** 去重键：类型 + 场所范围 + 是否只看本人。 */
        String key() {
            return dataType + '|' + siteScope.allSites() + '|' + siteScope.siteIds() + '|' + ownOnly;
        }
    }

    /**
     * 合成结果。
     *
     * <p>{@code grants} 是权威口径；{@code data*} 是它的投影（契约旧五字段，供 UI 提示），
     * {@code management*} 面向 {@code SiteScope}（管理操作可见哪些场所）。
     */
    public record ScopeResult(
            List<ScopeGrant> grants,
            boolean dataAllSites,
            List<UUID> dataSiteIds,
            List<String> dataTypes,
            boolean ownOnly,
            long scopeVersion,
            boolean managementAllSites,
            List<UUID> managementSiteIds) {
    }
}
