package com.xxplatform.api.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * RBAC 读取面（任务 6：{@code GET /me} 的角色、权限、菜单与范围来源）。
 *
 * <p>数据模型（{@code contracts/database/schema.sql}）：
 * <pre>
 * auth.app_user --&lt; auth.user_role &gt;-- auth.role --&lt; auth.role_permission &gt;-- auth.permission
 *                                    \--&lt; auth.role_menu &gt;-- auth.menu
 *                                    \--&lt; auth.role_site / auth.role_data_type
 * auth.app_user --1:1-- auth.user_scope --&lt; auth.user_scope_site / auth.user_scope_data_type
 * </pre>
 *
 * <p><b>每个查询都从 {@code user_id} 出发 join {@code auth.user_role}</b>，而不是先取
 * roleIds 再拼 {@code IN (...)}：后者要自己拼占位符（批量大小不定、易出 SQL 注入式拼接错误），
 * 且多一次往返。同一条 SQL 里完成过滤还能顺带排除已软删的角色。
 *
 * <p><b>软删过滤</b>：{@code auth.role}/{@code auth.menu}/{@code auth.app_user} 用
 * {@code deleted boolean}，所以条件写 {@code deleted = false}。关联表
 * （{@code user_role}/{@code role_permission}/{@code role_menu}/{@code role_site}/
 * {@code role_data_type}）没有软删列，物理删除即解绑。
 */
@Repository
public class RbacRepository {

    /** 用户的角色 ID（已排除软删角色），按 role_code 排序以保证输出稳定。 */
    private static final String SELECT_ROLE_IDS = """
            SELECT ur.role_id
              FROM auth.user_role ur
              JOIN auth.role r ON r.id = ur.role_id AND r.deleted = false
             WHERE ur.user_id = ?
             ORDER BY r.role_code
            """;

    /** 权限码并集。{@code DISTINCT}：多个角色重复授予同一码时只出现一次。 */
    private static final String SELECT_PERMISSION_CODES = """
            SELECT DISTINCT rp.permission_code
              FROM auth.user_role ur
              JOIN auth.role r  ON r.id = ur.role_id AND r.deleted = false
              JOIN auth.role_permission rp ON rp.role_id = ur.role_id
             WHERE ur.user_id = ?
             ORDER BY rp.permission_code
            """;

    /**
     * 菜单并集。
     *
     * <p>只取 {@code enabled = true} 且未软删的菜单：停用菜单仍留在 {@code role_menu} 里是
     * 允许的（停用不等于解绑），但不应出现在 {@code /me} 的导航数据里。
     *
     * <p>排序 {@code sort_order, code}：{@code sort_order} 可能重复，再加 {@code code}
     * 让同一 {@code sort_order} 下的顺序也是确定的——否则前端每次刷新看到的菜单次序会抖。
     */
    private static final String SELECT_MENUS = """
            SELECT DISTINCT m.id,
                            m.parent_id,
                            m.code,
                            m.label,
                            m.route,
                            m.sort_order,
                            m.required_permission_code
              FROM auth.user_role ur
              JOIN auth.role r  ON r.id = ur.role_id AND r.deleted = false
              JOIN auth.role_menu rm ON rm.role_id = ur.role_id
              JOIN auth.menu m  ON m.id = rm.menu_id AND m.deleted = false AND m.enabled = true
             WHERE ur.user_id = ?
             ORDER BY m.sort_order, m.code
            """;

    /**
     * 角色范围开关，<b>按角色各一行</b>。
     *
     * <p>这里刻意不再用 {@code bool_or} 把三列压成一行：范围是按
     * {@code (dataType, siteScope, ownOnly)} 元组授权的，一旦先合并维度，后面就只能对合并后的
     * 集合取交集，会把"角色甲给 PLATE@A、角色乙给 FACE@B"算成 {@code {PLATE,FACE}×{A,B}}
     * 的交叉扩权（评审 A01）。逐角色取回原始行，元组关系才不会丢。
     */
    private static final String SELECT_ROLE_SCOPES = """
            SELECT r.id AS role_id,
                   r.data_all_sites,
                   r.management_all_sites,
                   r.own_only
              FROM auth.user_role ur
              JOIN auth.role r ON r.id = ur.role_id AND r.deleted = false
             WHERE ur.user_id = ?
             ORDER BY r.role_code
            """;

    /** 角色授予的场所，带 {@code role_id}，供上层按角色还原元组。 */
    private static final String SELECT_ROLE_SITE_ALL = """
            SELECT rs.role_id, rs.site_id, rs.scope_kind
              FROM auth.user_role ur
              JOIN auth.role r ON r.id = ur.role_id AND r.deleted = false
              JOIN auth.role_site rs ON rs.role_id = ur.role_id
             WHERE ur.user_id = ?
             ORDER BY rs.role_id, rs.site_id, rs.scope_kind
            """;

    /** 角色授予的数据类型，带 {@code role_id}，供上层按角色还原元组。 */
    private static final String SELECT_ROLE_DATA_TYPE_ALL = """
            SELECT rdt.role_id, rdt.data_type
              FROM auth.user_role ur
              JOIN auth.role r ON r.id = ur.role_id AND r.deleted = false
              JOIN auth.role_data_type rdt ON rdt.role_id = ur.role_id
             WHERE ur.user_id = ?
             ORDER BY rdt.role_id, rdt.data_type
            """;

    private static final String SELECT_USER_SCOPE = """
            SELECT management_all_sites, data_all_sites, own_only, scope_version
              FROM auth.user_scope
             WHERE user_id = ?
            """;

    private static final String SELECT_USER_SCOPE_SITES = """
            SELECT site_id, scope_kind
              FROM auth.user_scope_site
             WHERE user_id = ?
            """;

    private static final String SELECT_USER_SCOPE_DATA_TYPES = """
            SELECT data_type
              FROM auth.user_scope_data_type
             WHERE user_id = ?
             ORDER BY data_type
            """;

    private final JdbcTemplate jdbc;

    public RbacRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 用户的角色 ID，按 {@code role_code} 排序。 */
    public List<UUID> roleIds(UUID userId) {
        return jdbc.query(SELECT_ROLE_IDS, (rs, rowNum) -> rs.getObject("role_id", UUID.class), userId);
    }

    /** 权限码并集，按字母序。 */
    public List<String> permissionCodes(UUID userId) {
        return jdbc.query(SELECT_PERMISSION_CODES, (rs, rowNum) -> rs.getString("permission_code"), userId);
    }

    /** 菜单并集，按 {@code sort_order, code} 排序。 */
    public List<MenuRow> menus(UUID userId) {
        return jdbc.query(SELECT_MENUS, (rs, rowNum) -> new MenuRow(
                rs.getObject("id", UUID.class),
                rs.getObject("parent_id", UUID.class),
                rs.getString("code"),
                rs.getString("label"),
                rs.getString("route"),
                rs.getLong("sort_order"),
                rs.getString("required_permission_code")), userId);
    }

    /** 每个角色一行的范围开关（不聚合），按 {@code role_code} 排序。 */
    public List<RoleScope> roleScopes(UUID userId) {
        return jdbc.query(SELECT_ROLE_SCOPES, (rs, rowNum) -> new RoleScope(
                rs.getObject("role_id", UUID.class),
                rs.getBoolean("data_all_sites"),
                rs.getBoolean("management_all_sites"),
                rs.getBoolean("own_only")), userId);
    }

    /** 角色授予的场所（含 {@code scope_kind}：MANAGEMENT/DATA），带 {@code roleId}。 */
    public List<RoleSite> roleSiteGrants(UUID userId) {
        return jdbc.query(SELECT_ROLE_SITE_ALL, (rs, rowNum) -> new RoleSite(
                rs.getObject("role_id", UUID.class),
                rs.getObject("site_id", UUID.class),
                rs.getString("scope_kind")), userId);
    }

    /** 角色授予的数据类型，带 {@code roleId}。 */
    public List<RoleDataType> roleDataTypes(UUID userId) {
        return jdbc.query(SELECT_ROLE_DATA_TYPE_ALL, (rs, rowNum) -> new RoleDataType(
                rs.getObject("role_id", UUID.class),
                rs.getString("data_type")), userId);
    }

    /**
     * 用户的显式范围行（{@code auth.user_scope} 一对一）。
     *
     * <p><b>存在与否是语义分界</b>：没有这一行表示"账号没有附加授权"，有效范围全部来自角色；
     * 有这一行则是**账号附加授权**，与角色授权按 {@code (dataType, siteScope, ownOnly)} 元组求**并集**
     * （契约 {@code userScopeMode = additive_union_of_typed_scope_tuples}，评审 A01）。
     * 旧实现把它当"角色是上限、用户行只能收窄"，会把账号本应获得的权限吞掉。
     * 依据：{@code data-dictionary.md} 说该表"保存输入而非猜测合并策略"，
     * {@code ADR-007} 与 SD06 要求 P-002 签认前**不自行合并扩大权限**——所以并集只在
     * 显式授权（角色行 + 用户行）之间做，不推断继承。
     */
    public Optional<UserScopeRow> userScope(UUID userId) {
        List<UserScopeRow> rows = jdbc.query(SELECT_USER_SCOPE, (rs, rowNum) -> new UserScopeRow(
                rs.getBoolean("management_all_sites"),
                rs.getBoolean("data_all_sites"),
                rs.getBoolean("own_only"),
                rs.getLong("scope_version")), userId);
        return rows.stream().findFirst();
    }

    /** 账号附加授权的 DATA/MANAGEMENT 场所（与角色授权按元组求并集）。 */
    public List<SiteGrant> userScopeSites(UUID userId) {
        return jdbc.query(SELECT_USER_SCOPE_SITES, (rs, rowNum) -> new SiteGrant(
                rs.getObject("site_id", UUID.class),
                rs.getString("scope_kind")), userId);
    }

    /** 账号附加授权的数据类型（与角色授权按元组求并集）。 */
    public List<String> userScopeDataTypes(UUID userId) {
        return jdbc.query(SELECT_USER_SCOPE_DATA_TYPES, (rs, rowNum) -> rs.getString("data_type"), userId);
    }

    /** 一个角色的范围开关（一行一角色；不再压成"全体角色 OR"的一行）。 */
    public record RoleScope(UUID roleId, boolean dataAllSites, boolean managementAllSites, boolean ownOnly) {
    }

    /** 角色 → 场所授权，带 {@code roleId} 以便按角色还原元组。 */
    public record RoleSite(UUID roleId, UUID siteId, String scopeKind) {
    }

    /** 角色 → 数据类型授权，带 {@code roleId} 以便按角色还原元组。 */
    public record RoleDataType(UUID roleId, String dataType) {
    }

    /** 用户显式范围行。 */
    public record UserScopeRow(boolean managementAllSites, boolean dataAllSites, boolean ownOnly,
                               long scopeVersion) {
    }

    /** 一条场所授权；{@code scopeKind} 为 {@code MANAGEMENT} 或 {@code DATA}。 */
    public record SiteGrant(UUID siteId, String scopeKind) {

        public static final String MANAGEMENT = "MANAGEMENT";
        public static final String DATA = "DATA";
    }

    /** 菜单行（{@code parentMenuId} 与 {@code requiredPermissionCode} 可为 null）。 */
    public record MenuRow(UUID menuId, UUID parentMenuId, String code, String label, String route,
                          long sortOrder, String requiredPermissionCode) {
    }
}
