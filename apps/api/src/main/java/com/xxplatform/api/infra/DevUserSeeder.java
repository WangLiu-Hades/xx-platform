package com.xxplatform.api.infra;

import com.xxplatform.api.config.DevSeedProperties;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 开发/联调用的种子账号与角色（任务 5 建档、任务 6 补齐角色，对应清单里的任务 9）。
 *
 * <p><b>为什么需要它</b>：{@code contracts/api/FRONTEND_INTEGRATION.md:59} 写明
 * "HTTP 模式使用后端提供的真实测试账号；{@code demo.admin/demo123} 仅是 Mock 内的账号，
 * 后端未创建时不能用来登录"。也就是说，前端切到真实后端后**没有任何账号可登录**，
 * 整条链路（csrf → login → me → client-config）在这里断掉。
 *
 * <p><b>为什么不是迁移脚本里的 INSERT</b>：种子口令需要哈希，而哈希必须在应用内用
 * 同一套算法算（迁移脚本里算等于把算法复制到 SQL 里，两边迟早不一致）。
 * 而且要能"按环境开/关"，迁移脚本做不到条件插入。
 *
 * <p><b>为什么种三个角色而不是一个</b>：{@code GET /me} 的价值全在"不同用户看到不同结果"。
 * 只有一个全权限账号时，{@code ownOnly}、数据类型收窄、权限码差集这些分支**一条都测不到**，
 * 联调只能证明"能登录"，证明不了"范围计算是对的"。三个角色分别覆盖：
 * <ul>
 *   <li>{@code SUPER_ADMIN}：全 58 个权限码 + 全场所 + {@code ownOnly=false} + 四种数据类型；</li>
 *   <li>{@code OPERATOR}：13 个只读码 + {@code ownOnly=true} + 仅 FENCE/PLATE
 *       —— 用来验"权限子集 + 类型收窄 + 本人数据"；</li>
 *   <li>{@code CLASSIFIED}：在 OPERATOR 之上加 {@code capture:decrypt}（HIGH 风险）与
 *       {@code audit:read}，四种数据类型 —— 用来验"同一份只读岗位上多一个高危权限"。</li>
 * </ul>
 *
 * <p><b>三个角色都设 {@code all_sites=true}</b>：否则要给它们插 {@code auth.role_site}，
 * 而那需要先有 {@code core.site} 行。种子阶段造场所会引入"假场所"，反而比空列表更麻烦
 * （场所列表走 {@code dataScope.siteIds}，{@code allSites=true} 时契约明确要求 {@code []}）。
 * 场所收窄的行为由 {@code ScopeCalculatorTests} 用纯单测覆盖，不依赖种子数据。
 *
 * <p><b>刻意不种的东西</b>：
 * <ul>
 *   <li>{@code auth.menu} / {@code auth.role_menu}：仓库里没有真实菜单目录
 *       （{@code listMenuCatalog} 尚无数据来源），塞假 label/route 等于把未签认的
 *       导航结构固化。空菜单是合法结果，{@code GET /me} 返回 {@code menus: []}。</li>
 *   <li>{@code auth.user_scope}：没有用户范围行 = 未收窄，直接取角色并集。
 *       种一行 {@code all_sites=false} 的空范围反而会让种子账号"什么都看不到"。</li>
 * </ul>
 *
 * <p><b>安全</b>：三重闸门见 {@link DevSeedProperties}（profile=db、enabled、显式口令）。
 * 本类只用 {@code log.warn} 暴露"创建了什么"，**从不打印口令或哈希**。
 */
@Component
@Profile("db")
public class DevUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    private static final String ROLE_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_OPERATOR = "OPERATOR";
    private static final String ROLE_CLASSIFIED = "CLASSIFIED";

    /** 四种数据类型，顺序与契约 {@code DataType} 枚举一致。 */
    private static final List<String> ALL_DATA_TYPES = List.of("FENCE", "MAC", "PLATE", "FACE");

    /** 操作员：只读/检索类，**不含任何写、删除、导出涉密、审批类权限**。 */
    private static final List<String> OPERATOR_PERMISSIONS = List.of(
            "dashboard:read",
            "capture:read",
            "capture:export",
            "map:read",
            "analysis:read",
            "watch:read",
            "notification:read",
            "device:read",
            "report:read",
            "task:read",
            "site:read",
            "context:use",
            "face:search");

    /** 涉密岗：操作员 + 解密与审计阅读（这两项都要求更高密级，见 {@code secret_clearance}）。 */
    private static final List<String> CLASSIFIED_PERMISSIONS = List.of(
            "dashboard:read",
            "capture:read",
            "capture:export",
            "map:read",
            "analysis:read",
            "watch:read",
            "notification:read",
            "device:read",
            "report:read",
            "task:read",
            "site:read",
            "context:use",
            "face:search",
            "capture:decrypt",
            "audit:read");

    private final DevSeedProperties properties;
    private final PasswordHasher passwordHasher;
    private final JdbcTemplate jdbc;

    public DevUserSeeder(DevSeedProperties properties, PasswordHasher passwordHasher, JdbcTemplate jdbc) {
        this.properties = properties;
        this.passwordHasher = passwordHasher;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.debug("开发种子账号未启用（xx.auth.dev-seed.enabled=false），跳过");
            return;
        }

        String password = properties.getPassword();
        // 启用开关却给不全参数，说明配置写错了。这里**启动即失败**：
        // 静默跳过会让人以为账号已建好，然后在登录页反复试密码。
        String adminLogin = requireName(properties.getLoginName(), "login-name", "demo.admin");
        String operatorLogin = requireName(properties.getOperatorLoginName(), "operator-login-name",
                "demo.operator");
        String classifiedLogin = requireName(properties.getClassifiedLoginName(), "classified-login-name",
                "demo.classified");
        if (password == null || password.length() < DevSeedProperties.MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("xx.auth.dev-seed.enabled=true 但 password 缺失或少于 "
                    + DevSeedProperties.MIN_PASSWORD_LENGTH + " 位（用环境变量 XX_AUTH_DEVSEED_PASSWORD 提供）");
        }

        UUID adminRole = ensureRole(ROLE_ADMIN, "超级管理员",
                "开发种子角色：全部接口权限、全场所、不限本人；仅本地/联调", true, true, false);
        UUID operatorRole = ensureRole(ROLE_OPERATOR, "操作员",
                "开发种子角色：只读检索类权限、全场所、仅本人数据、仅围栏与车牌", false, true, true);
        UUID classifiedRole = ensureRole(ROLE_CLASSIFIED, "涉密岗",
                "开发种子角色：操作员 + 解密/审计阅读、全场所、四种数据类型", false, true, false);

        grantAllPermissions(adminRole);
        grantPermissions(operatorRole, OPERATOR_PERMISSIONS);
        grantPermissions(classifiedRole, CLASSIFIED_PERMISSIONS);

        grantDataTypes(adminRole, ALL_DATA_TYPES);
        grantDataTypes(operatorRole, List.of("FENCE", "PLATE"));
        grantDataTypes(classifiedRole, ALL_DATA_TYPES);

        String adminDisplay = properties.getDisplayName() == null || properties.getDisplayName().isBlank()
                ? adminLogin : properties.getDisplayName().strip();
        UUID adminUser = ensureUser(adminLogin, adminDisplay, password);
        UUID operatorUser = ensureUser(operatorLogin, "演示操作员", password);
        UUID classifiedUser = ensureUser(classifiedLogin, "演示涉密岗", password);

        bindRole(adminUser, adminRole);
        bindRole(operatorUser, operatorRole);
        bindRole(classifiedUser, classifiedRole);

        log.warn("开发种子数据就绪：账号 {}/{}/{}，角色 {}/{}/{}（仅供本地/联调，禁止在生产启用本开关）",
                adminLogin, operatorLogin, classifiedLogin, ROLE_ADMIN, ROLE_OPERATOR, ROLE_CLASSIFIED);
    }

    private static String requireName(String value, String propertyName, String example) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("xx.auth.dev-seed.enabled=true 但未提供 " + propertyName
                    + "（例如 --xx.auth.dev-seed." + propertyName + "=" + example + "）");
        }
        return value.strip();
    }

    /**
     * 确保角色存在，返回其 id。
     *
     * <p>幂等：按 {@code role_code}（表上有 UNIQUE）查，存在就复用 id、不覆盖手工改过的
     * 范围标志。**不覆盖**是刻意的——否则每次重启都会把联调时手工调过的角色重置。
     */
    private UUID ensureRole(String roleCode, String name, String description,
                            boolean managementAllSites, boolean dataAllSites, boolean ownOnly) {
        List<UUID> existing = jdbc.query("SELECT id FROM auth.role WHERE role_code = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class), roleCode);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        UUID roleId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO auth.role
                    (id, role_code, name, description,
                     management_all_sites, data_all_sites, own_only, scope_policy_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING-P002')
                """, roleId, roleCode, name, description, managementAllSites, dataAllSites, ownOnly);
        log.warn("已创建开发种子角色 roleCode={} roleId={}", roleCode, roleId);
        return roleId;
    }

    /** 给角色授全部权限码（从 {@code auth.permission} 目录读，不在代码里复述 58 个码）。 */
    private void grantAllPermissions(UUID roleId) {
        int inserted = jdbc.update("""
                INSERT INTO auth.role_permission (role_id, permission_code)
                SELECT ?, code FROM auth.permission
                ON CONFLICT DO NOTHING
                """, roleId);
        if (inserted > 0) {
            log.info("角色 {} 新增 {} 个权限码（全量目录）", roleId, inserted);
        }
    }

    private void grantPermissions(UUID roleId, List<String> codes) {
        String sql = """
                INSERT INTO auth.role_permission (role_id, permission_code)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """;
        for (String code : codes) {
            jdbc.update(sql, roleId, code);
        }
    }

    private void grantDataTypes(UUID roleId, List<String> dataTypes) {
        String sql = """
                INSERT INTO auth.role_data_type (role_id, data_type)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """;
        for (String dataType : dataTypes) {
            jdbc.update(sql, roleId, dataType);
        }
    }

    /**
     * 确保账号存在，返回其 id。
     *
     * <p>幂等，且**默认不重置口令**：否则每次重启都会把联调时改过的密码冲掉。
     * 需要换口令时用 {@code xx.auth.dev-seed.reset-password=true} 显式打开一次，
     * 见 {@link DevSeedProperties#isResetPassword()}。
     *
     * <p>口令只在真正写入时才算哈希（600000 次迭代约 0.6 秒，三个账号只在首次启动付一次）。
     */
    private UUID ensureUser(String loginName, String displayName, String password) {
        List<UUID> existing = jdbc.query("SELECT id FROM auth.app_user WHERE login_name = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class), loginName);
        if (!existing.isEmpty()) {
            UUID userId = existing.get(0);
            if (properties.isResetPassword()) {
                jdbc.update("UPDATE auth.app_user SET password_hash = ? WHERE id = ?",
                        passwordHasher.hash(password), userId);
                // 改口令必须踢掉旧会话：否则别人手里已登录的会话在口令被换掉后照样能用。
                int revoked = jdbc.update("""
                        UPDATE auth.user_session SET revoked_at = now()
                         WHERE user_id = ? AND revoked_at IS NULL
                        """, userId);
                log.warn("已重置开发种子账号口令 loginName={} userId={}，并撤销 {} 个会话",
                        loginName, userId, revoked);
            }
            return userId;
        }
        UUID userId = UUID.randomUUID();
        // user_code 直接复用登录名：它在本系统里只是"人的编号"，种子账号不需要额外编号体系
        jdbc.update("""
                INSERT INTO auth.app_user
                    (id, user_code, login_name, display_name, password_hash, status, secret_clearance)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'STANDARD')
                """, userId, loginName, loginName, displayName, passwordHasher.hash(password));
        log.warn("已创建开发种子账号 loginName={} userId={}（仅供本地/联调，禁止在生产启用本开关）",
                loginName, userId);
        return userId;
    }

    private void bindRole(UUID userId, UUID roleId) {
        jdbc.update("""
                INSERT INTO auth.user_role (user_id, role_id) VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """, userId, roleId);
    }
}
