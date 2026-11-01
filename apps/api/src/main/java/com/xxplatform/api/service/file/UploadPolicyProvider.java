package com.xxplatform.api.service.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.config.FileProperties;
import com.xxplatform.api.infra.ClientConfigRepository;
import com.xxplatform.api.infra.ClientConfigRepository.UploadPolicyRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 上传策略的<b>唯一计算入口</b>（M17）。
 *
 * <p>修复前存在两套规则：上传端读 {@link UploadPolicyCatalog} 的 Java 常量，下发端
 * {@code GET /client-config} 读 {@code core.upload_policy}（基线里没有种子行，恒为空）。
 * 运维改库不影响上传校验，改代码不影响下发，两边各自漂移——11 MiB 的普通导入因此
 * 被 10 MiB 的全局上限或旧的分类上限挡掉，而前端拿到的策略又是空的。
 *
 * <p>现在两端都走这里：{@link #policies()} 与 {@link #forCategory(FileCategory)}
 * 返回同一份数据，来源是 {@code core.upload_policy}（部署配置），只有"某分类没有配置行"
 * 时才回落到契约口径的内置默认值（{@link UploadPolicyCatalog}），避免空表把全部上传拒死。
 *
 * <p><b>物理上限 = min(全局, 分类)</b>：契约 api-conventions.md:53 明确
 * 「旧 maxUploadBytes 仅全局上限，实际限制取全局与分类 maxBytes 的较小值」。
 * {@link CategoryPolicy#maxBytes()} 是分类配置值（下发给前端的 {@code maxBytes}），
 * {@link CategoryPolicy#limitBytes()} 是真正执行的上限（两者取小）。
 */
@Service
public class UploadPolicyProvider {

    private static final Logger log = LoggerFactory.getLogger(UploadPolicyProvider.class);

    /** {@code core.config_item.value} 里全局上限的键名（契约 ClientConfig.maxUploadBytes）。 */
    private static final String GLOBAL_KEY = "maxUploadBytes";

    private final ClientConfigRepository config;
    private final FileProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public UploadPolicyProvider(ClientConfigRepository config, FileProperties properties,
                                ObjectMapper objectMapper) {
        this.config = config;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 只读内置目录的实例（单元测试用）。
     *
     * <p>生产环境永远走上面带 {@link ClientConfigRepository} 的构造：没有库就没有部署配置，
     * 也就不能在测试里伪造"读到了表"的证据。
     */
    public static UploadPolicyProvider builtInOnly(FileProperties properties, ObjectMapper objectMapper) {
        return new UploadPolicyProvider(null, properties, objectMapper);
    }

    /**
     * 分类上传策略（下发与执行共用）。
     *
     * @param category           分类
     * @param mimeTypes          允许的归一化媒体类型（稳定顺序，便于前端逐项比对）
     * @param extensions         允许的小写扩展名（含点）
     * @param maxBytes           分类自身的上限（契约 {@code UploadPolicy.maxBytes}）
     * @param effectiveMaxBytes  实际执行上限 {@code min(全局 maxUploadBytes, maxBytes)}
     * @param maxRows            行数上限；0 = 该分类不按行计
     * @param enabled            部署配置是否启用该分类
     */
    public record CategoryPolicy(
            String category,
            List<String> mimeTypes,
            List<String> extensions,
            long maxBytes,
            long effectiveMaxBytes,
            int maxRows,
            boolean enabled) {

        public boolean allowsMime(String normalizedMime) {
            return normalizedMime != null && mimeTypes.contains(normalizedMime);
        }

        public boolean allowsExtension(String extension) {
            return extension != null && extensions.contains(extension);
        }

        public String describe() {
            return String.join("/", mimeTypes);
        }
    }

    /** 契约口径的默认策略（未配置任何行时的回落），按分类名排序。 */
    public List<CategoryPolicy> policies() {
        long global = globalMaxBytes();
        Map<String, UploadPolicyRow> rows = configuredRows();
        Map<String, CategoryPolicy> result = new TreeMap<>();
        for (FileCategory category : FileCategory.values()) {
            UploadPolicyRow row = rows.get(category.name());
            if (row == null) {
                if (!category.uploadable()) {
                    continue;
                }
                result.put(category.name(), fromDefaults(category, global, true));
            } else {
                result.put(category.name(), fromRow(category, row, global));
            }
        }
        return List.copyOf(result.values());
    }

    /**
     * 取某分类的执行策略；分类不接受客户端上传时抛 {@link IllegalArgumentException}
     * （调用方翻成 400 VALIDATION_ERROR，保持修复前的错误语义）。
     */
    public CategoryPolicy forCategory(FileCategory category) {
        if (!category.uploadable()) {
            throw new IllegalArgumentException("该分类不接受客户端上传：" + category);
        }
        long global = globalMaxBytes();
        UploadPolicyRow row = configuredRows().get(category.name());
        return row == null ? fromDefaults(category, global, true) : fromRow(category, row, global);
    }

    /**
     * 全局上限：读 {@code core.config_item} 的 {@code maxUploadBytes}。
     *
     * <p>这一项是契约里的"旧全局上限"，不读库就无法与下发同源。表缺失/JSON 坏时不编造
     * 业务值，回落到 {@code xx.file.import-max-bytes}（与内置导入档一致）；
     * 生产环境里配置行缺失会由 {@code GET /client-config} 直接 500 暴露。
     */
    public long globalMaxBytes() {
        if (config == null) {
            return properties.getImportMaxBytes();
        }
        return config.findConfigItem()
                .map(item -> parseGlobal(item.valueJson()))
                .orElseGet(properties::getImportMaxBytes);
    }

    private long parseGlobal(String valueJson) {
        try {
            JsonNode root = objectMapper.readTree(valueJson);
            JsonNode node = root == null ? null : root.get(GLOBAL_KEY);
            if (node != null && node.canConvertToLong() && node.asLong() > 0) {
                return node.asLong();
            }
        } catch (Exception ex) {
            log.warn("client-config 的 {} 无法解析，回落到 xx.file.import-max-bytes", GLOBAL_KEY, ex);
        }
        return properties.getImportMaxBytes();
    }

    /** 读 {@code core.upload_policy}，只保留契约分类枚举内的行（其余行没有界面落点）。 */
    private Map<String, UploadPolicyRow> configuredRows() {
        if (config == null) {
            return Map.of();
        }
        Map<String, UploadPolicyRow> rows = new LinkedHashMap<>();
        for (UploadPolicyRow row : config.uploadPolicies()) {
            FileCategory category = parseOrNull(row.category());
            if (category == null) {
                log.warn("忽略契约枚举外的上传策略分类：category={}", row.category());
                continue;
            }
            rows.put(category.name(), row);
        }
        return rows;
    }

    private static FileCategory parseOrNull(String raw) {
        try {
            return FileCategory.parse(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private CategoryPolicy fromDefaults(FileCategory category, long global, boolean enabled) {
        UploadPolicyCatalog.UploadPolicy base = UploadPolicyCatalog.policyFor(
                category, properties.getImageMaxBytes(), properties.getImportMaxBytes());
        return new CategoryPolicy(
                category.name(),
                base.mimeTypes().stream().sorted().toList(),
                base.extensions().stream().sorted().toList(),
                base.maxBytes(),
                Math.min(global, base.maxBytes()),
                base.maxRows(),
                enabled);
    }

    /**
     * 用部署配置行覆盖默认值。
     *
     * <p>{@code max_bytes < 1} 的行被 DDL 允许、但契约要求 {@code minimum: 1}：这种行
     * 不是"该分类不能上传"的合法表达，回落到默认值并 warn，避免把脏数据变成 413。
     */
    private CategoryPolicy fromRow(FileCategory category, UploadPolicyRow row, long global) {
        UploadPolicyCatalog.UploadPolicy base = UploadPolicyCatalog.policyFor(
                category, properties.getImageMaxBytes(), properties.getImportMaxBytes());
        List<String> mimeTypes = row.mimeTypes() == null || row.mimeTypes().isEmpty()
                ? base.mimeTypes().stream().sorted().toList()
                : row.mimeTypes().stream().map(UploadPolicyCatalog::normalizeMime)
                        .filter(s -> !s.isBlank()).distinct().sorted().toList();
        List<String> extensions = row.extensions() == null || row.extensions().isEmpty()
                ? base.extensions().stream().sorted().toList()
                : row.extensions().stream().map(UploadPolicyProvider::normalizeExtension)
                        .filter(s -> !s.isBlank()).distinct().sorted().toList();
        long maxBytes = row.maxBytes() >= 1 ? row.maxBytes() : base.maxBytes();
        if (row.maxBytes() < 1) {
            log.warn("上传策略 max_bytes<1（契约要求 >=1），按内置上限执行：category={}", category);
        }
        return new CategoryPolicy(
                category.name(),
                mimeTypes,
                extensions,
                maxBytes,
                Math.min(global, maxBytes),
                base.maxRows(),
                row.enabled());
    }

    /** 统一扩展名写法：小写、去空白、补前导点（库里可能存 {@code .csv}，也可能存 {@code csv}）。 */
    static String normalizeExtension(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        return value.startsWith(".") ? value : "." + value;
    }
}
