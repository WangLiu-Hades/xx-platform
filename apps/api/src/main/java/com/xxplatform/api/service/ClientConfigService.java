package com.xxplatform.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.ClientConfigRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /client-config} 的组装逻辑（任务 7）。
 *
 * <p>四张表拼成一个 12 字段的响应。这里没有任何"业务计算"，全部是投影与校验；
 * 真正的数值来自部署配置（{@code core.config_item}），能力开关来自
 * {@code core.feature_config}——代码不内置任何阈值，改上限不需要发版。
 *
 * <p><b>失败策略分两类，刻意不对齐</b>：
 * <ul>
 *   <li><b>配置行缺失/停用/JSON 损坏</b> → {@code 500 SYSTEM_INTERNAL}。
 *       这是部署故障，返回一个"看起来正常但没有阈值"的响应会让前端用默认值继续跑，
 *       比直接报错危险得多。</li>
 *   <li><b>单行数据不符合契约枚举</b>（上传分类不在 9 值内、字典绑定字段不在 4 值内、
 *       上限为 0）→ <b>跳过该行并 warn</b>。这些行在界面上没有落点，丢掉它们不会
 *       隐藏任何可见能力；若因此整接口 500，反而会让前端连首页都打不开。</li>
 * </ul>
 *
 * <p>唯一的例外是 {@code core.feature_config.feature_code}：它不在契约 12 值枚举内时
 * <b>直接 500</b>。理由：{@code features} 是契约规定的唯一能力目录，多出一个无法表达的
 * code 说明有人写入了契约之外的数据；静默丢弃会让一个可能已签认的能力凭空消失，
 * 而这类错误一旦上线就很难被发现（前端只是"少了个按钮"）。
 */
@Service
public class ClientConfigService {

    private static final Logger log = LoggerFactory.getLogger(ClientConfigService.class);

    /** 契约 {@code FeatureAvailability.featureCode} 的 12 个合法值。 */
    static final Set<String> FEATURE_CODES = Set.of(
            "FACE_SEARCH",
            "DECRYPTION",
            "COMPANION_ANALYSIS",
            "COLLISION_ANALYSIS",
            "REGION_STATISTICS",
            "PROFILE_ASSOCIATION",
            "RESIDENCE_MODEL",
            "WATCH_ACTIVATION",
            "NOTIFICATION_SEND",
            "DEVICE_COMMAND",
            "REPORT_GENERATION",
            "RAW_INGESTION");

    /** 契约 {@code UploadPolicy.category} 的 9 个合法值。 */
    static final Set<String> UPLOAD_CATEGORIES = Set.of(
            "FACE_SEARCH",
            "DATASET_IMPORT",
            "DEVICE_IMPORT",
            "PERSON_LIBRARY_IMPORT",
            "PROFILE_PORTRAIT",
            "REPORT",
            "EXPORT",
            "CAPTURE_IMAGE",
            "RAW_INGESTION");

    /** 契约 {@code CatalogBinding.fieldKey} 的 4 个合法值。 */
    static final Set<String> CATALOG_FIELD_KEYS = Set.of(
            "DeviceInput.category",
            "PersonLibraryInput.category",
            "PersonProfileCorrection.ethnicity",
            "PersonProfileCorrection.gender");

    private final ClientConfigRepository repository;
    private final ObjectMapper objectMapper;

    public ClientConfigService(ClientConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 读四张表并组装响应。
     *
     * <p>整个方法放在一个只读事务里：四张表分别读，若不在同一事务，可能出现
     * "能力开关已更新、上传策略还是旧的"这种半新半旧的组合，前端据此做出的判断会自相矛盾。
     */
    @Transactional(readOnly = true)
    public ClientConfigView load() {
        ClientConfigRepository.ConfigItemRow item = repository.findConfigItem()
                .orElseThrow(() -> deploymentFailure(
                        "core.config_item 里缺少可用的 client-config 配置行"
                                + "（config_key='client-config' 且 enabled=true 且 public_readable=true）。"
                                + "这是部署故障：请执行 db/migration/V2__seed_deployment_client_config.sql"
                                + " 里的种子语句，或检查运维是否把这个键停用了。"));

        ClientConfigScalars scalars = parseScalars(item);

        List<ClientConfigView.FeatureView> features = features();
        List<ClientConfigView.FeatureAvailabilityItem> projection = new ArrayList<>(features.size());
        for (ClientConfigView.FeatureView feature : features) {
            // 契约：旧别名"仅由 features 投影为 {code,enabled}，不得单独计算或覆写"
            projection.add(new ClientConfigView.FeatureAvailabilityItem(
                    feature.featureCode(), feature.enabled()));
        }

        return new ClientConfigView(
                scalars.version(),
                scalars.mapCoordinateSystem(),
                scalars.taskPollIntervalSeconds(),
                scalars.maxUploadBytes(),
                List.copyOf(projection),
                uploadPolicies(),
                scalars.institutionTimeZone(),
                scalars.contextTtlSeconds(),
                scalars.maxOnlineQuerySeconds(),
                scalars.maxOnlineWindowDays(),
                features,
                catalogBindings());
    }

    private ClientConfigScalars parseScalars(ClientConfigRepository.ConfigItemRow item) {
        JsonNode root;
        try {
            root = objectMapper.readTree(item.valueJson());
        } catch (JsonProcessingException ex) {
            throw deploymentFailure("client-config 的 value 不是合法 JSON：" + ex.getOriginalMessage());
        }
        List<String> unknownKeys = ClientConfigScalars.unknownKeys(root);
        if (!unknownKeys.isEmpty()) {
            log.debug("client-config 的 value 含契约外的键（已忽略）：{}", unknownKeys);
        }
        try {
            return ClientConfigScalars.parse(root);
        } catch (IllegalStateException ex) {
            throw deploymentFailure(ex.getMessage());
        }
    }

    /**
     * 能力开关：返回**全部**行，不只是启用的。
     *
     * <p>契约里 {@code pendingReason} 是 required 且 nullable：未启用的能力必须带原因，
     * 前端才能给出"该功能待确认"的提示，而不是把入口藏起来让人以为系统坏了。
     */
    private List<ClientConfigView.FeatureView> features() {
        List<ClientConfigRepository.FeatureRow> rows = repository.features();
        List<String> invalid = new ArrayList<>();
        List<ClientConfigView.FeatureView> views = new ArrayList<>(rows.size());
        for (ClientConfigRepository.FeatureRow row : rows) {
            if (!FEATURE_CODES.contains(row.featureCode())) {
                invalid.add(row.featureCode());
                continue;
            }
            views.add(new ClientConfigView.FeatureView(
                    row.featureCode(), row.enabled(), row.pendingReason()));
        }
        if (!invalid.isEmpty()) {
            throw deploymentFailure("core.feature_config 里出现契约枚举之外的 feature_code："
                    + invalid + "。契约 FeatureAvailability.featureCode 只允许 " + FEATURE_CODES
                    + " 这 12 个值，请修正数据（不能静默丢弃，否则已启用的能力会从能力目录里消失）。");
        }
        return List.copyOf(views);
    }

    /**
     * 上传策略：跳过不符合契约的行。
     *
     * <p>两类跳过都对应一处契约-DDL 不一致（见 README 的 D7/D8 记录）：
     * <ul>
     *   <li>{@code max_bytes = 0}：DDL 允许，但契约 {@code maxBytes} 是 {@code minimum: 1}，
     *       发出去前端会把它当成"该分类不能上传"的合法策略，语义就变了；</li>
     *   <li>{@code category} 不在 9 值枚举内：契约表达不了，界面上也没有落点。</li>
     * </ul>
     */
    private List<ClientConfigView.UploadPolicyView> uploadPolicies() {
        List<ClientConfigRepository.UploadPolicyRow> rows = repository.uploadPolicies();
        List<ClientConfigView.UploadPolicyView> views = new ArrayList<>(rows.size());
        for (ClientConfigRepository.UploadPolicyRow row : rows) {
            if (!UPLOAD_CATEGORIES.contains(row.category())) {
                log.warn("跳过契约枚举外的上传分类：category={}", row.category());
                continue;
            }
            if (row.maxBytes() < 1) {
                log.warn("跳过上限制为 0 的上传策略（契约要求 maxBytes >= 1）：category={}", row.category());
                continue;
            }
            views.add(new ClientConfigView.UploadPolicyView(
                    row.category(), row.mimeTypes(), row.extensions(), row.maxBytes(), row.enabled()));
        }
        return List.copyOf(views);
    }

    /**
     * 字典绑定：跳过契约枚举外的 {@code field_key}（D8）。
     *
     * <p>{@code core.dictionary_binding.field_key} 没有 CHECK 约束，所以数据库允许写入
     * 契约 4 值之外的字段名。前端拿它做 switch 分支，遇到未知键只会忽略——但契约
     * {@code CatalogBinding.fieldKey} 是闭集，所以这里就不该发出去。
     */
    private List<ClientConfigView.CatalogBindingView> catalogBindings() {
        List<ClientConfigRepository.CatalogBindingRow> rows = repository.catalogBindings();
        List<ClientConfigView.CatalogBindingView> views = new ArrayList<>(rows.size());
        for (ClientConfigRepository.CatalogBindingRow row : rows) {
            if (!CATALOG_FIELD_KEYS.contains(row.fieldKey())) {
                log.warn("跳过契约枚举外的字典绑定字段：fieldKey={}", row.fieldKey());
                continue;
            }
            views.add(new ClientConfigView.CatalogBindingView(
                    row.fieldKey(), row.dictionaryCode(), row.label()));
        }
        return List.copyOf(views);
    }

    private static ApiException deploymentFailure(String message) {
        log.error("client-config 部署配置不可用：{}", message);
        return new ApiException(ErrorCode.SYSTEM_INTERNAL,
                "前端配置不可用，请联系运维检查部署配置");
    }
}
