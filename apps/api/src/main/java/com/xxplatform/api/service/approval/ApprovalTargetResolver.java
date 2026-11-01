package com.xxplatform.api.service.approval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.config.ApprovalProperties;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.ScopeCalculator;

/**
 * 审批目标的**服务端解析**（M09 / M10）。
 *
 * <p>为什么不能信客户端：{@code deviceIds.length} 决定要 1 票还是 2 票。如果由调用方自己数，
 * 它只要传一个 {@code groupId}（或声称 1 台）就能把"批量 ≥10 台"的双人审批降成单人；反过来
 * 也可以把目标集合缩小，获批后再换一批设备执行。所以这里做三件事：
 *
 * <ol>
 *   <li>把 {@code groupId} / {@code deviceIds} 解析成**真实存在的设备集合**（查
 *       {@code core.device_group_member} + {@code core.device}，过滤逻辑删除）；</li>
 *   <li>校验目标落在申请人当前数据范围内，越界 403 {@code PERMISSION_DATA_SCOPE}；</li>
 *   <li>把设备集合、资源版本、授权版本、策略版本冻结进审批快照，执行前再比对一次。</li>
 * </ol>
 */
@Component
public class ApprovalTargetResolver {

    private final JdbcTemplate jdbc;
    private final RbacRepository rbac;
    private final ApprovalProperties properties;

    public ApprovalTargetResolver(JdbcTemplate jdbc, RbacRepository rbac, ApprovalProperties properties) {
        this.jdbc = jdbc;
        this.rbac = rbac;
        this.properties = properties;
    }

    /** 当前用户的完整范围（数据站点/管理站点/类型/ownOnly/授权版本）。 */
    public ScopeCalculator.ScopeResult scopeOf(UUID userId) {
        return ScopeCalculator.resolve(rbac.roleScopes(userId), rbac.roleSiteGrants(userId),
                rbac.roleDataTypes(userId), rbac.userScope(userId).orElse(null),
                rbac.userScopeSites(userId), rbac.userScopeDataTypes(userId));
    }

    /** 冻结快照的服务端事实部分。 */
    public record Frozen(List<UUID> deviceIds,
                         Map<String, String> versions,
                         Set<UUID> siteIds,
                         long scopeVersion,
                         String policyVersion,
                         String dataScope) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("deviceIds", deviceIds.stream().map(UUID::toString).toList());
            map.put("versions", new LinkedHashMap<>(versions));
            map.put("siteIds", siteIds.stream().map(UUID::toString).sorted().toList());
            map.put("scopeVersion", scopeVersion);
            map.put("policyVersion", policyVersion);
            map.put("dataScope", dataScope);
            return map;
        }
    }

    /**
     * 解析目标并冻结。
     *
     * @param actorScope 申请人当前范围；为 {@code null} 时只做存在性解析（系统调用场景）
     */
    public Frozen freeze(ApprovalOperation operation,
                         Map<String, Object> pathParameters,
                         Map<String, Object> requestBody,
                         ScopeCalculator.ScopeResult actorScope,
                         Object ifMatch) {
        if (!isDeviceBatch(operation)) {
            // 非设备类操作：没有可解析的目标集合，冻结策略/授权版本即可。
            String dataScope = actorScope == null ? "" : describeScope(actorScope);
            return new Frozen(List.of(), Map.of(), Set.of(), actorScope == null ? 0L : actorScope.scopeVersion(),
                    properties.getPolicyVersion(), dataScope);
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        Object groupId = requestBody.get("groupId");
        if (groupId instanceof String text && !text.isBlank()) {
            ids.addAll(groupDevices(UUID.fromString(text)));
        }
        Object explicit = requestBody.containsKey("deviceIds")
                ? requestBody.get("deviceIds") : pathParameters.get("deviceIds");
        if (explicit instanceof List<?> list) {
            for (Object item : list) {
                ids.add(UUID.fromString(String.valueOf(item)));
            }
        }
        if (ids.isEmpty()) {
            // 例如 updateDeviceParameters 走 path.deviceId；这里补上单设备目标。
            Object single = pathParameters.get("deviceId");
            if (single instanceof String text && !text.isBlank()) {
                ids.add(UUID.fromString(text));
            }
        }
        List<DeviceRow> devices = loadDevices(ids);
        if (devices.size() != ids.size()) {
            Set<UUID> found = devices.stream().map(DeviceRow::id).collect(Collectors.toSet());
            List<UUID> missing = ids.stream().filter(id -> !found.contains(id)).toList();
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "目标设备不存在或已删除：" + missing,
                    List.of(new com.xxplatform.api.common.ApiResponses.FieldErrorDetail(
                            "proposal.requestBody.deviceIds", "设备不存在")), null);
        }
        Set<UUID> sites = devices.stream().map(DeviceRow::siteId).collect(Collectors.toCollection(
                LinkedHashSet::new));
        if (actorScope != null && !covers(actorScope, sites)) {
            throw new ApiException(ErrorCode.PERMISSION_DATA_SCOPE,
                    "目标设备不在当前数据范围内，不能发起该审批");
        }
        Map<String, String> versions = new LinkedHashMap<>();
        for (DeviceRow device : devices) {
            versions.put("device:" + device.id(), String.valueOf(device.version()));
        }
        if (ifMatch != null) {
            Object single = pathParameters.get("deviceId");
            if (single instanceof String text) {
                DeviceRow device = devices.stream()
                        .filter(row -> row.id().toString().equals(text))
                        .findFirst()
                        .orElseThrow(() -> new ApiException(ErrorCode.DATA_NOT_FOUND, "目标设备不存在"));
                if (!String.valueOf(device.version()).equals(String.valueOf(ifMatch))) {
                    throw new ApiException(ErrorCode.RESOURCE_VERSION_CONFLICT,
                            "设备参数版本已变化（If-Match=" + ifMatch + "，当前=" + device.version() + "）");
                }
            }
        }
        String dataScope = actorScope == null ? "" : describeScope(actorScope);
        return new Frozen(List.copyOf(ids), versions, sites,
                actorScope == null ? 0L : actorScope.scopeVersion(), properties.getPolicyVersion(), dataScope);
    }

    /**
     * 执行前复核：目标集合、资源版本、策略版本、执行人范围任一变化都拒绝（M10）。
     */
    public void verifyUnchanged(ApprovalOperation operation,
                                Map<String, Object> frozen,
                                UUID actorId) {
        Object policyVersion = frozen.get("policyVersion");
        if (policyVersion != null && !String.valueOf(policyVersion).equals(properties.getPolicyVersion())) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                    "审批冻结的策略版本已变化，请重新申请");
        }
        Object rawIds = frozen.get("deviceIds");
        if (!(rawIds instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<UUID> expected = list.stream().map(item -> UUID.fromString(String.valueOf(item))).toList();
        List<DeviceRow> current = loadDevices(expected);
        Set<UUID> currentIds = current.stream().map(DeviceRow::id).collect(Collectors.toSet());
        if (!currentIds.containsAll(expected)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                    "审批冻结的目标设备已被删除或不可见，请重新申请");
        }
        Object rawVersions = frozen.get("versions");
        if (rawVersions instanceof Map<?, ?> versions) {
            for (DeviceRow device : current) {
                Object expectedVersion = versions.get("device:" + device.id());
                if (expectedVersion != null
                        && !String.valueOf(expectedVersion).equals(String.valueOf(device.version()))) {
                    throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                            "目标设备版本已变化，请重新申请");
                }
            }
        }
        ScopeCalculator.ScopeResult scope = scopeOf(actorId);
        Set<UUID> sites = current.stream().map(DeviceRow::siteId).collect(Collectors.toSet());
        if (!covers(scope, sites)) {
            throw new ApiException(ErrorCode.PERMISSION_DATA_SCOPE,
                    "当前范围已不覆盖审批目标，不能执行");
        }
    }

    /** 审批人范围是否覆盖目标站点集合。 */
    public boolean covers(ScopeCalculator.ScopeResult scope, Collection<UUID> siteIds) {
        if (siteIds == null || siteIds.isEmpty()) {
            // 没有可解析目标的审批（例如角色授权）要求审批人至少拥有一个可管理的站点或全局范围。
            return scope.managementAllSites() || scope.dataAllSites()
                    || !scope.managementSiteIds().isEmpty() || !scope.dataSiteIds().isEmpty();
        }
        if (scope.managementAllSites() || scope.dataAllSites()) {
            return true;
        }
        Set<UUID> allowed = new LinkedHashSet<>(scope.managementSiteIds());
        allowed.addAll(scope.dataSiteIds());
        return allowed.containsAll(siteIds);
    }

    /**
     * 是否为设备批量类操作（决定是否需要解析并冻结真实设备集合）。
     *
     * <p>{@link ApprovalOperation} 的 deviceBatch 标志没有暴露 getter，而 30 个操作里只有
     * createDeviceCommand / updateDeviceParameters / retryDeviceCommand / reviseDeviceCommand
     * 的 operationId 含 "Device"，用这一点判定，避免为了一个 getter 去改枚举（该文件不在本组改动范围）。
     */
    static boolean isDeviceBatch(ApprovalOperation operation) {
        return operation.operationId().contains("Device");
    }

    private List<DeviceRow> loadDevices(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>(ids);
        return jdbc.query("SELECT id, site_id, version FROM core.device"
                        + " WHERE deleted = false AND id IN (" + placeholders + ")",
                (rs, rowNum) -> new DeviceRow(rs.getObject("id", UUID.class),
                        rs.getObject("site_id", UUID.class), rs.getLong("version")),
                args.toArray());
    }

    private List<UUID> groupDevices(UUID groupId) {
        Integer exists = jdbc.query("SELECT 1 FROM core.device_group WHERE id = ? AND deleted = false",
                rs -> rs.next() ? 1 : null, groupId);
        if (exists == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "设备组不存在或已删除",
                    List.of(new com.xxplatform.api.common.ApiResponses.FieldErrorDetail(
                            "proposal.requestBody.groupId", "设备组不存在")), null);
        }
        return jdbc.query("SELECT device_id FROM core.device_group_member WHERE group_id = ?",
                (rs, rowNum) -> rs.getObject("device_id", UUID.class), groupId);
    }

    /** 范围的可读快照（写进复核清单，审批人能看到"批的是多大范围"）。 */
    public static String describeScope(ScopeCalculator.ScopeResult scope) {
        String sites = scope.dataAllSites() && scope.managementAllSites()
                ? "ALL"
                : scope.dataSiteIds().stream().map(UUID::toString).sorted()
                        .collect(Collectors.joining(",", "[", "]"));
        return "dataAllSites=" + scope.dataAllSites()
                + ",managementAllSites=" + scope.managementAllSites()
                + ",sites=" + sites
                + ",dataTypes=" + scope.dataTypes()
                + ",ownOnly=" + scope.ownOnly();
    }

    private record DeviceRow(UUID id, UUID siteId, long version) {
        private DeviceRow {
            Objects.requireNonNull(id);
        }
    }
}
