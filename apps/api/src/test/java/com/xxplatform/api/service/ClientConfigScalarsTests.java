package com.xxplatform.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ClientConfigScalars} 的解析与校验（任务 7）。
 *
 * <p>纯 JUnit，不起 Spring、不碰 Mockito：本沙箱禁止 Mockito 自附加（见 README §9），
 * 而这些规则本来就是纯函数，没必要为了"看起来更集成"去挂整个上下文。
 */
class ClientConfigScalarsTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String VALID = """
            {
              "version": "1",
              "mapCoordinateSystem": "EPSG:4326",
              "taskPollIntervalSeconds": 3,
              "maxUploadBytes": 10485760,
              "institutionTimeZone": "Asia/Shanghai",
              "contextTtlSeconds": 1800,
              "maxOnlineQuerySeconds": 5,
              "maxOnlineWindowDays": 31
            }
            """;

    private static ClientConfigScalars parse(String json) {
        return ClientConfigScalars.parse(read(json));
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @Test
    @DisplayName("合法对象按契约字段逐项解析")
    void parsesValidObject() {
        ClientConfigScalars scalars = parse(VALID);

        assertEquals("1", scalars.version());
        assertEquals("EPSG:4326", scalars.mapCoordinateSystem());
        assertEquals(3, scalars.taskPollIntervalSeconds());
        assertEquals(10_485_760L, scalars.maxUploadBytes());
        assertEquals("Asia/Shanghai", scalars.institutionTimeZone());
        assertEquals(1800, scalars.contextTtlSeconds());
        assertEquals(5, scalars.maxOnlineQuerySeconds());
        assertEquals(31, scalars.maxOnlineWindowDays());
        assertTrue(ClientConfigScalars.unknownKeys(read(VALID)).isEmpty());
    }

    @Test
    @DisplayName("缺键时报错，且消息里带键名")
    void missingKeyNamesTheKey() {
        String json = VALID.replace("\"contextTtlSeconds\": 1800,", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parse(json));

        assertTrue(ex.getMessage().contains("contextTtlSeconds"),
                "错误消息必须点名缺失的键，否则运维只能靠猜：" + ex.getMessage());
    }

    @Test
    @DisplayName("类型错（字符串冒充整数）被拒")
    void wrongTypeIsRejected() {
        String json = VALID.replace("\"maxOnlineQuerySeconds\": 5", "\"maxOnlineQuerySeconds\": \"5\"");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parse(json));

        assertTrue(ex.getMessage().contains("maxOnlineQuerySeconds"), ex.getMessage());
    }

    @Test
    @DisplayName("枚举外时区被拒（契约只有一个合法值）")
    void foreignTimeZoneIsRejected() {
        String json = VALID.replace("Asia/Shanghai", "UTC");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parse(json));

        assertTrue(ex.getMessage().contains("institutionTimeZone"), ex.getMessage());
    }

    @Test
    @DisplayName("枚举外坐标系被拒")
    void foreignCoordinateSystemIsRejected() {
        String json = VALID.replace("EPSG:4326", "EPSG:3857");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parse(json));

        assertTrue(ex.getMessage().contains("mapCoordinateSystem"), ex.getMessage());
    }

    @Test
    @DisplayName("数值小于 1 被拒（契约 minimum: 1）")
    void zeroIsRejected() {
        String json = VALID.replace("\"taskPollIntervalSeconds\": 3", "\"taskPollIntervalSeconds\": 0");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parse(json));

        assertTrue(ex.getMessage().contains("taskPollIntervalSeconds"), ex.getMessage());
    }

    @Test
    @DisplayName("超过 int32 的上传上限被拒（否则序列化出去不符合契约 integer）")
    void oversizedUploadLimitIsRejected() {
        String json = VALID.replace("10485760", "4294967296");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parse(json));

        assertTrue(ex.getMessage().contains("maxUploadBytes"), ex.getMessage());
    }

    @Test
    @DisplayName("契约外的键被忽略（向前兼容），不影响解析")
    void unknownKeysAreTolerated() {
        String json = VALID.replace("\"version\": \"1\",", "\"version\": \"1\", \"_note\": \"运维备注\",");
        JsonNode root = read(json);

        ClientConfigScalars scalars = ClientConfigScalars.parse(root);

        assertEquals("1", scalars.version());
        assertEquals(List.of("_note"), ClientConfigScalars.unknownKeys(root));
    }

    @Test
    @DisplayName("value 不是 JSON 对象时报错")
    void nonObjectIsRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parse("[1,2,3]"));

        assertTrue(ex.getMessage().contains("JSON 对象"), ex.getMessage());
    }
}
