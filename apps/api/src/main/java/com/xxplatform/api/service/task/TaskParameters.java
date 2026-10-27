package com.xxplatform.api.service.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.Sha256;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 任务参数快照的规范化与指纹（{@code parameter_snapshot}／{@code parameter_hash}）。
 *
 * <p>两个用途：
 * <ol>
 *   <li>把参数按**确定性**序列化后落库，重放时能逐字节比对；</li>
 *   <li>算出 {@code parameter_hash}，幂等键复用时用它判断"同一份输入"还是"换了参数"。</li>
 * </ol>
 *
 * <p><b>为什么不能直接 {@code ObjectMapper.writeValueAsString(map)}</b>：{@link HashMap} 的迭代顺序
 * 取决于哈希与容量，两个内容相同的 map 可能序列化成不同字符串，指纹随之不同——同一个
 * {@code Idempotency-Key} 会被误判成"换了参数"并回 409，变成一个只在生产规模下偶发的 bug。
 * 因此先递归地把所有 map 换成 {@link TreeMap}（按键排序），再序列化。
 *
 * <p><b>为什么把 schema 版本也算进指纹</b>：{@code parameter_schema_version} 变了意味着参数语义
 * 可能变了（同样的字段名、不同的含义）。指纹覆盖它之后，跨版本重放会被判成冲突而不是静默复用。
 *
 * <p>已知边界：JSON 数字 {@code 1} 与 {@code 1.0} 序列化不同、指纹也不同。这在本平台是可接受的
 * ——参数来自请求体的 JSON 解析，同一个客户端重试会给出同样的字面量。
 */
public final class TaskParameters {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TaskParameters() {
    }

    /**
     * 确定性 JSON：键按字典序递归排序，紧凑无空白。
     *
     * @throws IllegalArgumentException 参数为 null
     */
    public static String canonicalJson(Map<String, Object> parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters 不能为空");
        }
        try {
            return MAPPER.writeValueAsString(sorted(parameters));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("参数快照无法序列化", e);
        }
    }

    /**
     * 参数指纹：{@code sha256(schemaVersion + '\n' + canonicalJson)} 的小写十六进制。
     *
     * <p>长度固定 64，正好对上 {@code parameter_hash char(64)}。用 SHA-256 而不是"存整份 JSON
     * 再比对"：指纹可以进唯一索引/日志，不泄露参数内容（参数里可能有手机号、车牌等业务数据）。
     */
    public static String hash(String schemaVersion, Map<String, Object> parameters) {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("parameterSchemaVersion 不能为空");
        }
        return hex(Sha256.of(schemaVersion + "\n" + canonicalJson(parameters)));
    }

    private static Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> ordered = new TreeMap<>();
            map.forEach((key, v) -> ordered.put(String.valueOf(key), sorted(v)));
            return ordered;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(sorted(item)));
            return copy;
        }
        return value;
    }

    private static String hex(byte[] digest) {
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    /** 便于测试与日志：把参数复制成不可变视图。 */
    public static Map<String, Object> copyOf(Map<String, Object> parameters) {
        return parameters == null ? Map.of() : new LinkedHashMap<>(parameters);
    }
}
