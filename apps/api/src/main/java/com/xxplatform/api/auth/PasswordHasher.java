package com.xxplatform.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 口令哈希与校验（{@code auth.app_user.password_hash}）。
 *
 * <h2>为什么是 PBKDF2-HMAC-SHA256，而不是 BCrypt/Argon2id</h2>
 *
 * <p>先说结论：契约与系统设计里**没有任何口令算法约定**（{@code contracts} 全目录只有
 * {@code password_hash varchar(255)} 一列，注释也是占位的 {@code 'password_hash'}），
 * 所以这是本轮自行选择并需要记录依据的地方。
 *
 * <p>选型依据（按重要性排序）：
 * <ol>
 *   <li><b>不引入新依赖</b>。本机 {@code ~/.m2} 只读、沙箱外的方法都不可写，
 *       新增依赖会让**默认离线构建**（{@code mvn -o compile}）直接失败。
 *       {@code PBKDF2WithHmacSHA256} 是 JDK（SunJCE）自带算法，零依赖；
 *       而 BCrypt/Argon2id 需要 {@code spring-security-crypto}（Argon2 还要 BouncyCastle）。</li>
 *   <li><b>没有 72 字节截断陷阱</b>。BCrypt 只使用口令前 72 字节，超长部分被静默忽略，
 *       于是"前 72 字节相同的两个不同口令"会互相通过校验。要么拒绝长口令（与契约
 *       未声明 maxLength 冲突），要么预处理摘要（另一个需要解释的构造）。
 *       PBKDF2 对任意长度输入都正确。</li>
 *   <li><b>迭代数可对齐 OWASP 建议值</b>。PBKDF2-HMAC-SHA256 的 OWASP 建议下限是
 *       600000 次迭代，本机实测约 <b>619 ms</b>（Java 17.0.12，见 {@link #ITERATIONS}）。
 *       这个成本只落在登录与改密路径上，可以接受；用到的所有端点都有限流兜底。</li>
 * </ol>
 *
 * <p><b>未来升级到 Argon2id</b>：{@link #hash} 的输出是自描述格式（算法名 + 迭代数 +
 * 盐 + 摘要都写在字符串里），校验时按存储值里的算法名分派。升级时新增一种前缀即可，
 * 老口令在用户下次改密时自动重算——不需要一次性重写全表，也不需要停机。
 *
 * <h2>存储格式</h2>
 * <pre>{@code pbkdf2-sha256$<iterations>$<salt-b64url>$<hash-b64url>}</pre>
 * 例：{@code pbkdf2-sha256$600000$<22 字符>$<43 字符>}，总长约 87 字符，列宽 varchar(255) 足够。
 * 盐每次随机、逐账号独立：同一口令在不同账号下哈希不同，彩虹表与"同口令用户"关联都失效。
 */
@Component
public class PasswordHasher {

    private static final Logger log = LoggerFactory.getLogger(PasswordHasher.class);

    /** 存储值前缀；升级算法时按它分派。 */
    private static final String PREFIX = "pbkdf2-sha256";

    /**
     * 迭代次数 = OWASP 2023 对 PBKDF2-HMAC-SHA256 的建议下限 600000。
     *
     * <p>为什么写死、不做成配置项：把它做成配置就等于给了"为了性能调到 1000"的入口，
     * 而这种降级在评审里往往只被看成一行 yml 改动。要提速就换算法（见类注释），
     * 不要降迭代数。
     *
     * <p>实测（本机 Java 17.0.12，单核）：100000→119 ms、210000→228 ms、
     * 310000→327 ms、600000→619 ms。仅登录/改密路径命中这个成本。
     */
    private static final int ITERATIONS = 600_000;

    /** 盐长度 16 字节（128 位）。 */
    private static final int SALT_BYTES = 16;

    /** 派生摘要长度 256 位。 */
    private static final int KEY_BITS = 256;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final SecureRandom random = new SecureRandom();

    /**
     * 用于"账号不存在"分支的占位哈希（惰性初始化）。
     *
     * <p>为什么需要它：如果账号不存在就直接返回 401，响应会**明显更快**（少一次 619 ms 的
     * 密钥派生），攻击者据此就能枚举出哪些登录名真实存在。所以未知账号也要完整跑一次
     * 同成本的校验，再丢弃结果。
     *
     * <p>为什么惰性而不是静态常量：静态初始化会在**每次启动**多做一次 619 ms 的派生，
     * 而只有真的出现登录失败时才需要它。
     */
    private volatile String dummyHash;

    /**
     * 计算口令哈希。
     *
     * @param rawPassword 口令原文（调用方不要落日志）
     * @return 自描述存储字符串
     */
    public String hash(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("口令不能为 null");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] derived = derive(rawPassword, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$" + B64.encodeToString(salt) + "$" + B64.encodeToString(derived);
    }

    /**
     * 校验口令。
     *
     * <p><b>失败一律返回 false</b>，不区分"格式非法/算法不支持/不匹配"：调用方据此只能
     * 得到一个 401，不给探测空间。格式非法会记 WARN —— 那说明库里的数据坏了或被手工改过，
     * 属于运维需要知道的事，但**不能**因此把异常抛给调用方。
     *
     * @param rawPassword 待校验口令
     * @param stored      库里的存储值（可能为 null/空）
     */
    public boolean verify(String rawPassword, String stored) {
        if (rawPassword == null || stored == null || stored.isBlank()) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            log.warn("口令哈希格式不可识别（前缀或段数不符），按校验失败处理");
            return false;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            log.warn("口令哈希迭代数不是整数，按校验失败处理");
            return false;
        }
        if (iterations < 1) {
            log.warn("口令哈希迭代数非法（{}），按校验失败处理", iterations);
            return false;
        }
        byte[] salt;
        byte[] expected;
        try {
            salt = B64D.decode(parts[2]);
            expected = B64D.decode(parts[3]);
        } catch (IllegalArgumentException e) {
            log.warn("口令哈希的盐或摘要不是合法 base64url，按校验失败处理");
            return false;
        }
        byte[] actual = derive(rawPassword, salt, iterations);
        // 常量时间比较：避免按响应时间逐字节猜摘要
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * 对"账号不存在"的场景跑一次等成本校验，用来拉平响应时间。
     *
     * @param rawPassword 本次提交的口令原文（只用于消耗等价 CPU，结果被丢弃）
     */
    public void burnEquivalentTime(String rawPassword) {
        verify(rawPassword == null ? "" : rawPassword, dummyHash());
    }

    /** 惰性构造占位哈希（内容无意义，只需要成本一致）。 */
    private String dummyHash() {
        String cached = dummyHash;
        if (cached == null) {
            synchronized (this) {
                cached = dummyHash;
                if (cached == null) {
                    cached = hash("dummy-password-for-timing-equalisation");
                    dummyHash = cached;
                }
            }
        }
        return cached;
    }

    /** PBKDF2 派生。 */
    private static byte[] derive(String rawPassword, byte[] salt, int iterations) {
        char[] chars = rawPassword.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JRE 缺少 " + ALGORITHM, e);
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("口令无法用于密钥派生", e);
        } finally {
            // 不留在堆里等 GC：明文口令在内存里待得越久，dump/swap 泄露面越大
            spec.clearPassword();
            java.util.Arrays.fill(chars, '\0');
        }
    }

    /** 便于测试断言使用的算法标识（不是安全边界）。 */
    static String prefix() {
        return PREFIX;
    }

    /** 便于测试与文档对齐的迭代数（不是安全边界）。 */
    static int iterations() {
        return ITERATIONS;
    }

    /** 供测试构造"已存在的哈希"，避免测试里硬编码算法串。 */
    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
