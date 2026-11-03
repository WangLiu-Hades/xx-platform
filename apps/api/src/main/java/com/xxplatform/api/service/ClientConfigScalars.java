package com.xxplatform.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code core.config_item.value} 里那 8 个标量的强类型视图（任务 7）。
 *
 * <p><b>为什么不用 {@code objectMapper.treeToValue(value, ClientConfigScalars.class)}</b>：
 * 全局 ObjectMapper 配了 {@code failOnUnknownProperties(true)}（那是给请求体用的，防止前端
 * 多传字段被静默忽略）。若拿它反序列化下发配置，运维在 {@code value} 里多留一个备注键
 * 就会让整个 {@code /client-config} 变成 500。这里改成逐键显式读取：未知键忽略（向前兼容），
 * 缺失/类型错/越界则带上**键名**报错，便于运维直接定位到 JSON 里的哪一段。
 *
 * <p>校验强度按契约来：两个单值枚举必须精确匹配（写错了契约根本表达不了），
 * 四个"秒/天"类数值必须 ≥1（契约 {@code minimum: 1}），
 * 且不得超过 {@link Integer#MAX_VALUE}——契约类型是 OpenAPI {@code integer}（int32），
 * 超出的值序列化出去就不符合契约了。
 *
 * @param version                 配置版本，前端用它判断是否需要刷新本地缓存
 * @param mapCoordinateSystem     坐标系，契约枚举只有 {@code EPSG:4326}
 * @param taskPollIntervalSeconds 异步任务轮询间隔（秒）
 * @param maxUploadBytes          全局上传上限（字节）；单类上限还要与 uploadPolicies 取小
 * @param institutionTimeZone     时间展示时区，契约枚举只有 {@code Asia/Shanghai}
 * @param contextTtlSeconds       Context 会话存活时间（秒）
 * @param maxOnlineQuerySeconds   在线查询的服务端时间预算（秒）
 * @param maxOnlineWindowDays     在线查询允许回溯的最大天数
 */
public record ClientConfigScalars(
        String version,
        String mapCoordinateSystem,
        int taskPollIntervalSeconds,
        long maxUploadBytes,
        String institutionTimeZone,
        int contextTtlSeconds,
        int maxOnlineQuerySeconds,
        int maxOnlineWindowDays) {

    /** 契约 {@code ClientConfig.mapCoordinateSystem} 的唯一合法值。 */
    public static final String COORDINATE_SYSTEM = "EPSG:4326";

    /** 契约 {@code ClientConfig.institutionTimeZone} 的唯一合法值。 */
    public static final String TIME_ZONE = "Asia/Shanghai";

    private static final Set<String> KNOWN_KEYS = Set.of(
            "version",
            "mapCoordinateSystem",
            "taskPollIntervalSeconds",
            "maxUploadBytes",
            "institutionTimeZone",
            "contextTtlSeconds",
            "maxOnlineQuerySeconds",
            "maxOnlineWindowDays");

    /**
     * 解析并校验。
     *
     * @throws IllegalStateException 缺键、类型错、枚举不符或数值越界；消息里带键名，
     *                               由调用方转成 {@code SYSTEM_INTERNAL}（这是部署故障，
     *                               不是客户端错误——所以不抛 {@code ApiException(400)}）
     */
    public static ClientConfigScalars parse(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("client-config 的 value 必须是 JSON 对象");
        }
        return new ClientConfigScalars(
                requireText(root, "version"),
                requireExactText(root, "mapCoordinateSystem", COORDINATE_SYSTEM),
                requirePositiveInt(root, "taskPollIntervalSeconds"),
                requirePositiveLong(root, "maxUploadBytes"),
                requireExactText(root, "institutionTimeZone", TIME_ZONE),
                requirePositiveInt(root, "contextTtlSeconds"),
                requirePositiveInt(root, "maxOnlineQuerySeconds"),
                requirePositiveInt(root, "maxOnlineWindowDays"));
    }

    /**
     * 契约里不存在的键名（只用于日志）。
     *
     * <p>忽略而不是报错：契约只规定"必须有哪些键"，运维在 {@code value} 里多留一个备注键
     * 不该让整个配置接口挂掉。这里只负责把它们挑出来，由调用方记 debug 日志。
     */
    public static List<String> unknownKeys(JsonNode root) {
        if (root == null || !root.isObject()) {
            return List.of();
        }
        List<String> unknown = new ArrayList<>();
        root.fieldNames().forEachRemaining(name -> {
            if (!KNOWN_KEYS.contains(name)) {
                unknown.add(name);
            }
        });
        return List.copyOf(unknown);
    }

    private static JsonNode require(JsonNode root, String key) {
        JsonNode node = root.get(key);
        if (node == null || node.isNull()) {
            throw new IllegalStateException("client-config 缺少配置项 " + key);
        }
        return node;
    }

    private static String requireText(JsonNode root, String key) {
        JsonNode node = require(root, key);
        if (!node.isTextual()) {
            throw new IllegalStateException("client-config 的 " + key + " 必须是字符串");
        }
        String text = node.asText();
        if (text.isBlank()) {
            throw new IllegalStateException("client-config 的 " + key + " 不能为空");
        }
        return text;
    }

    private static String requireExactText(JsonNode root, String key, String expected) {
        String text = requireText(root, key);
        if (!expected.equals(text)) {
            throw new IllegalStateException(
                    "client-config 的 " + key + " 只允许 " + expected + "，实际是 " + text
                            + "（契约枚举只有这一个值）");
        }
        return text;
    }

    private static int requirePositiveInt(JsonNode root, String key) {
        JsonNode node = require(root, key);
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new IllegalStateException("client-config 的 " + key + " 必须是 32 位整数");
        }
        int value = node.asInt();
        if (value < 1) {
            throw new IllegalStateException("client-config 的 " + key + " 必须 >= 1，实际是 " + value);
        }
        return value;
    }

    private static long requirePositiveLong(JsonNode root, String key) {
        JsonNode node = require(root, key);
        if (!node.isIntegralNumber()) {
            throw new IllegalStateException("client-config 的 " + key + " 必须是整数");
        }
        long value = node.asLong();
        if (value < 1) {
            throw new IllegalStateException("client-config 的 " + key + " 必须 >= 1，实际是 " + value);
        }
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "client-config 的 " + key + " 超过契约类型上限（integer/int32）：" + value);
        }
        return value;
    }
}
