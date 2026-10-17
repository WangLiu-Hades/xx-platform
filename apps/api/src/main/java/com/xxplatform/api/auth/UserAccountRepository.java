package com.xxplatform.api.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code auth.app_user} 的最小读取面（任务 5：登录）。
 *
 * <p>为什么只有两个方法：本轮只需要"按登录名取账号 + 记录最后登录时间"。
 * 用户管理（增删改、改密、停用）属于 M01 的后续任务，届时扩展本类，
 * 不要提前把用不到的 CRUD 一起写进来——那样没人会去验证它们，等价于未测试代码。
 *
 * <p>软删除：{@code auth.app_user} 用 {@code deleted boolean}（不是 {@code deleted_at}），
 * 所以查询条件写 {@code deleted = false}。注意表上还有
 * {@code CHECK (NOT deleted OR status = 'DISABLED')}——软删必须同时把状态置为 DISABLED。
 */
@Repository
public class UserAccountRepository {

    private static final String SELECT_BY_LOGIN_NAME = """
            SELECT id, user_code, login_name, display_name, password_hash, status
              FROM auth.app_user
             WHERE login_name = ?
               AND deleted = false
            """;

    private final JdbcTemplate jdbc;

    public UserAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 按登录名精确查一个未删除账号。
     *
     * <p><b>不过滤 status</b>：停用账号被查到后，由 {@link LoginService} 决定用哪条日志、
     * 对外仍返回同一个 401。若这里直接过滤掉停用账号，服务层就无法区分
     * "口令错"与"账号已停用"，排障时只能猜。
     *
     * <p><b>大小写敏感</b>：{@code login_name} 有唯一索引，用 {@code lower()} 比较会与
     * "仅大小写不同的两个账号"冲突，也可能绕过索引。此限制已记入 README 已知限制。
     */
    public Optional<UserAccountRow> findByLoginName(String loginName) {
        List<UserAccountRow> rows = jdbc.query(SELECT_BY_LOGIN_NAME, (rs, rowNum) -> new UserAccountRow(
                rs.getObject("id", UUID.class),
                rs.getString("user_code"),
                rs.getString("login_name"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                rs.getString("status")), loginName);
        return rows.stream().findFirst();
    }

    /**
     * 记录最后登录时间。
     *
     * <p>只更新 {@code last_login_at}，不动 {@code updated_at}/{@code version}：
     * 登录不是"修改用户资料"，如果它推进版本号，用户管理页面的乐观锁会出现莫名其妙的冲突。
     *
     * @return 影响行数（正常为 1；0 说明账号在查询后被人删了，属于并发，记日志即可）
     */
    public int touchLastLogin(UUID userId, OffsetDateTime at) {
        return jdbc.update("UPDATE auth.app_user SET last_login_at = ? WHERE id = ?", at, userId);
    }

    /** 登录判定所需的最小列集。 */
    public record UserAccountRow(
            UUID id,
            String userCode,
            String loginName,
            String displayName,
            String passwordHash,
            String status) {

        /** 账号是否可用（{@code ACTIVE} 之外一律视为不可登录）。 */
        public boolean isActive() {
            return "ACTIVE".equals(status);
        }
    }
}
