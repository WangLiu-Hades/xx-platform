package com.xxplatform.api.infra;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code auth.app_user} 的最小读取面（任务 5：登录；M04：自助改密）。
 *
 * <p>为什么只有这几个方法：本轮需要"按登录名取账号 + 记录最后登录时间 + 改密"。
 * 用户管理（增删改、停用）属于 M01 的后续任务，届时扩展本类，
 * 不要提前把用不到的 CRUD 一起写进来——那样没人会去验证它们，等价于未测试代码。
 *
 * <p>软删除：{@code auth.app_user} 用 {@code deleted boolean}（不是 {@code deleted_at}），
 * 所以查询条件写 {@code deleted = false}。注意表上还有
 * {@code CHECK (NOT deleted OR status = 'DISABLED')}——软删必须同时把状态置为 DISABLED。
 *
 * <p><b>为什么读 {@code password_changed_at}</b>（M04）：它为 NULL 表示"仍在用初始口令"。
 * 这类账号只能看 {@code /me}、改密、退出、续期、取 CSRF，其它业务端点一律 403，
 * 否则"初始口令"就等于一把长期有效的万能钥匙。判定放在认证层（{@code SessionAuthenticator}），
 * 因为那是所有已认证请求的唯一入口。
 */
@Repository
public class UserAccountRepository {

    /**
     * 登录/认证读路径的列集。
     *
     * <p>{@code authz_version} 是权限版本（M14）：数据/场所范围由权限推导，导出等敏感操作
     * 必须确认"上下文冻结时的权限版本"与"当前权限版本"一致，否则用户可能拿着旧范围继续用。
     */
    private static final String SELECT_COLUMNS = """
            id, user_code, login_name, display_name, password_hash, status,
            password_changed_at, authz_version
            """;

    private static final String SELECT_BY_LOGIN_NAME = "SELECT " + SELECT_COLUMNS + """
              FROM auth.app_user
             WHERE login_name = ?
               AND deleted = false
            """;

    private static final String SELECT_BY_ID = "SELECT " + SELECT_COLUMNS + """
              FROM auth.app_user
             WHERE id = ?
            """;

    /**
     * 登录事务内锁住账号行（A02：单账号单会话）。
     *
     * <p><b>为什么必须锁在这一行</b>：签发会话要保证"撤销该账号全部旧会话 + 插入新会话"
     * 对同一账号是串行的。没有行锁时，两个浏览器同时登录会各自撤销一次、各自插入一行，
     * 结果是两条会话都有效——正是 A02 要修的现象。锁在 {@code auth.app_user} 上而不是
     * 进程内的 {@code synchronized}/{@code ReentrantLock}：多实例部署时进程内锁互不可见。
     *
     * <p>{@code deleted = false} 与读路径一致；软删账号（必然 {@code status='DISABLED'}）
     * 查不到，调用方按"账号不可用"处理。
     */
    private static final String LOCK_BY_ID = "SELECT " + SELECT_COLUMNS + """
              FROM auth.app_user
             WHERE id = ?
               AND deleted = false
               FOR UPDATE
            """;

    /**
     * 改密：只写口令哈希与改密时间。
     *
     * <p><b>为什么不动 {@code version}/{@code updated_at}</b>：这两个字段服务于"用户管理页的
     * 乐观锁编辑"。自助改密是本人对自己凭据的操作，如果它推进版本号，管理员正在编辑的用户表单
     * 会在保存时莫名 409。改密事实由 {@code password_changed_at} 与审计事件表达，足够了。
     *
     * <p><b>为什么带上 {@code deleted = false AND status = 'ACTIVE'}</b>：调用方在改密前刚
     * 校验过口令，但校验与写入之间账号可能被停用/软删。条件不满足时返回 0 行，调用方按
     * "账号不可用"处理并回滚，不会给停用账号写新口令。
     */
    private static final String UPDATE_PASSWORD = """
            UPDATE auth.app_user
               SET password_hash = ?, password_changed_at = ?
             WHERE id = ? AND deleted = false AND status = 'ACTIVE'
            """;

    private final JdbcTemplate jdbc;

    public UserAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 按登录名精确查一个未删除账号。
     *
     * <p><b>不过滤 status</b>：停用账号被查到后，由 {@link com.xxplatform.api.service.LoginService}
     * 决定用哪条日志、对外仍返回同一个 401。若这里直接过滤掉停用账号，服务层就无法区分
     * "口令错"与"账号已停用"，排障时只能猜。
     *
     * <p><b>大小写敏感</b>：{@code login_name} 有唯一索引，用 {@code lower()} 比较会与
     * "仅大小写不同的两个账号"冲突，也可能绕过索引。此限制已记入 README 已知限制。
     */
    public Optional<UserAccountRow> findByLoginName(String loginName) {
        List<UserAccountRow> rows = jdbc.query(SELECT_BY_LOGIN_NAME, ROW_MAPPER, loginName);
        return rows.stream().findFirst();
    }

    /**
     * 按主键取账号（任务 6：{@code GET /me} 由会话反查用户）。
     *
     * <p>与 {@link #findByLoginName} 的差别只在查询条件：这里**不过滤
     * {@code deleted}**。会话有效期内账号被人软删，必须能被查到并判为不可用；
     * 如果查询层直接过滤掉，结果会退化成"账号不存在"，日志里就看不出
     * "这个人的会话还在但账号没了"这一真实原因。
     *
     * <p>软删账号仍会被判为不可用，靠的是表上的
     * {@code CHECK (NOT deleted OR status = 'DISABLED')}：软删必然伴随
     * {@code status='DISABLED'}，所以 {@link UserAccountRow#isActive()} 已经覆盖了两种情况，
     * 这里不需要再单独读 {@code deleted} 列。
     */
    public Optional<UserAccountRow> findById(UUID userId) {
        List<UserAccountRow> rows = jdbc.query(SELECT_BY_ID, ROW_MAPPER, userId);
        return rows.stream().findFirst();
    }

    /**
     * 在登录事务里锁住账号行并读回状态（A02：单账号单会话）。
     *
     * <p>必须与"撤销旧会话 + 插入新会话"在同一个事务里调用：锁随事务提交/回滚释放。
     * 调用方拿到行后要**再判一次** {@link UserAccountRow#isActive()}——口令校验在事务外，
     * 校验通过到拿锁之间账号可能已被停用。
     */
    public Optional<UserAccountRow> lockById(UUID userId) {
        List<UserAccountRow> rows = jdbc.query(LOCK_BY_ID, ROW_MAPPER, userId);
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

    /**
     * 写入新口令哈希并把 {@code password_changed_at} 置为当前时刻（M04）。
     *
     * <p>哈希本身由 {@link PasswordHasher#hash(String)} 生成；本方法只负责落库，
     * 不做任何口令强度判断——那是 {@code PasswordService} 的职责，放在这里会让
     * "能用 SQL 改口令"这种运维操作被同样的规则挡住。
     *
     * @return 影响行数；0 表示账号在改密过程中被停用/软删，调用方应回滚并提示账号不可用
     */
    public int updatePassword(UUID userId, String passwordHash, OffsetDateTime changedAt) {
        if (userId == null || passwordHash == null || changedAt == null) {
            throw new IllegalArgumentException("userId/passwordHash/changedAt 不能为空");
        }
        return jdbc.update(UPDATE_PASSWORD, passwordHash, changedAt, userId);
    }

    private static final org.springframework.jdbc.core.RowMapper<UserAccountRow> ROW_MAPPER =
            (rs, rowNum) -> new UserAccountRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("user_code"),
                    rs.getString("login_name"),
                    rs.getString("display_name"),
                    rs.getString("password_hash"),
                    rs.getString("status"),
                    rs.getObject("password_changed_at", OffsetDateTime.class),
                    rs.getLong("authz_version"));

    /**
     * 登录/认证判定所需的最小列集。
     *
     * <p>{@code passwordChangedAt} 为 null 表示"仍在用初始口令"（M04）；
     * {@code authzVersion} 是权限版本（M14），用于确认冻结的权限范围是否仍然有效。
     */
    public record UserAccountRow(
            UUID id,
            String userCode,
            String loginName,
            String displayName,
            String passwordHash,
            String status,
            OffsetDateTime passwordChangedAt,
            long authzVersion) {

        /**
         * 兼容既有调用（只关心身份与口令）。
         *
         * <p>{@code passwordChangedAt=null} 在认证层表示"需要先改初始口令"，
         * 但只有真正经由本仓库查询出来的行才会进入认证链路；这个构造器只给
         * 单测/内存桩用，不会绕过任何真实判定。
         */
        public UserAccountRow(UUID id, String userCode, String loginName, String displayName,
                              String passwordHash, String status) {
            this(id, userCode, loginName, displayName, passwordHash, status, null, 0L);
        }

        /** 账号是否可用（{@code ACTIVE} 之外一律视为不可登录）。 */
        public boolean isActive() {
            return "ACTIVE".equals(status);
        }

        /** 是否仍在使用初始口令（M04：只允许改密等极少数端点）。 */
        public boolean hasInitialPassword() {
            return passwordChangedAt == null;
        }
    }
}
