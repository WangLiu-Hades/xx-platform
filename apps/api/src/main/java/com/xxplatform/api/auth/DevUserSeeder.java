package com.xxplatform.api.auth;

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
 * 开发/联调用的种子账号（任务 5 的配套件）。
 *
 * <p><b>为什么需要它</b>：{@code contracts/api/FRONTEND_INTEGRATION.md:59} 写明
 * "HTTP 模式使用后端提供的真实测试账号；{@code demo.admin/demo123} 仅是 Mock 内的账号，
 * 后端未创建时不能用来登录"。也就是说，前端切到真实后端后**没有任何账号可登录**，
 * 整条链路（csrf → login → me → client-config）在这里断掉。本类补上那个账号。
 *
 * <p><b>为什么不是迁移脚本里的 INSERT</b>：种子口令需要哈希，而哈希必须在应用内用
 * 同一套算法算（迁移脚本里算等于把算法复制到 SQL 里，两边迟早不一致）。
 * 而且要能"按环境开/关"，迁移脚本做不到条件插入。
 *
 * <p><b>安全</b>：三重闸门见 {@link DevSeedProperties}（profile=db、enabled、显式口令）。
 * 本类只用 {@code log.warn} 暴露"创建了账号"，**从不打印口令或哈希**。
 */
@Component
@Profile("db")
public class DevUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

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

        String loginName = properties.getLoginName();
        String password = properties.getPassword();
        // 启用开关却给不全参数，说明配置写错了。这里**启动即失败**：
        // 静默跳过会让人以为账号已建好，然后在登录页反复试密码。
        if (loginName == null || loginName.isBlank()) {
            throw new IllegalStateException(
                    "xx.auth.dev-seed.enabled=true 但未提供 login-name（用 --xx.auth.dev-seed.login-name=demo.admin 指定）");
        }
        if (password == null || password.length() < DevSeedProperties.MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("xx.auth.dev-seed.enabled=true 但 password 缺失或少于 "
                    + DevSeedProperties.MIN_PASSWORD_LENGTH + " 位（用环境变量 XX_AUTH_DEVSEED_PASSWORD 提供）");
        }
        loginName = loginName.strip();

        List<UUID> existing = jdbc.query(
                "SELECT id FROM auth.app_user WHERE login_name = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class), loginName);
        if (!existing.isEmpty()) {
            // 幂等：已存在就不动。不重置口令是刻意的——否则每次重启都会把用户改过的密码冲掉。
            log.info("开发种子账号已存在，跳过创建 loginName={} userId={}", loginName, existing.get(0));
            return;
        }

        String displayName = properties.getDisplayName() == null || properties.getDisplayName().isBlank()
                ? loginName
                : properties.getDisplayName().strip();

        UUID userId = UUID.randomUUID();
        // user_code 直接复用登录名：它在本系统里只是"人的编号"，种子账号不需要额外编号体系
        jdbc.update("""
                INSERT INTO auth.app_user
                    (id, user_code, login_name, display_name, password_hash, status, secret_clearance)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'STANDARD')
                """, userId, loginName, loginName, displayName, passwordHasher.hash(password));

        log.warn("已创建开发种子账号 loginName={} userId={}（仅供本地/联调，禁止在生产启用本开关）",
                loginName, userId);
    }
}
