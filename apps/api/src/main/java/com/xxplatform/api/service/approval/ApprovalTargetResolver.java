package com.xxplatform.api.service.approval;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.config.ApprovalProperties;
import com.xxplatform.api.infra.AnalysisContextRepository;
import com.xxplatform.api.infra.AnalysisContextRepository.ContextRow;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.CurrentDataScope;
import com.xxplatform.api.service.ScopeCalculator;
import com.xxplatform.api.service.task.TaskParameters;
import java.nio.charset.StandardCharsets;
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

/** Resolve real, visible targets before acceptance; re-resolve before approval consumption. */
@Component
public class ApprovalTargetResolver {
    private final JdbcTemplate jdbc;
    private final RbacRepository rbac;
    private final ApprovalProperties properties;
    private final CurrentDataScope live;
    private final AuthorizationBounds bounds;
    private final AnalysisContextRepository contexts;

    public ApprovalTargetResolver(JdbcTemplate jdbc, RbacRepository rbac, ApprovalProperties properties) {
        this.jdbc = jdbc; this.rbac = rbac; this.properties = properties;
        this.live = new CurrentDataScope(jdbc, rbac);
        this.bounds = new AuthorizationBounds(jdbc, rbac, live);
        this.contexts = new AnalysisContextRepository(jdbc);
    }

    public ScopeCalculator.ScopeResult scopeOf(UUID userId) { return live.scopeOf(userId); }
    public String authorizationStamp(UUID userId) { return live.stamp(userId); }
    public boolean active(UUID userId) { return live.active(userId); }

    public void lockAuthorizationUsers(Collection<UUID> userIds) {
        String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        jdbc.queryForList("SELECT id FROM auth.app_user WHERE id IN (" + placeholders + ") ORDER BY id FOR SHARE", userIds.toArray());
    }

    public record Frozen(List<UUID> deviceIds, Map<String, String> versions, Set<UUID> siteIds,
                         long scopeVersion, String policyVersion, String dataScope,
                         Map<String, Object> selection, boolean keyChange) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("deviceIds", deviceIds.stream().map(UUID::toString).sorted().toList());
            map.put("versions", versions);
            map.put("siteIds", siteIds.stream().map(UUID::toString).sorted().toList());
            map.put("scopeVersion", scopeVersion);
            map.put("policyVersion", policyVersion);
            map.put("dataScope", dataScope);
            map.put("selection", selection);
            map.put("keyChange", keyChange);
            return map;
        }
    }

    public Frozen freeze(ApprovalOperation operation, Map<String, Object> path, Map<String, Object> body,
                         ScopeCalculator.ScopeResult actorScope, Object ifMatch) {
        return freeze(operation, path, body, actorScope, ifMatch, null);
    }

    public Frozen freeze(ApprovalOperation operation, Map<String, Object> path, Map<String, Object> body,
                         ScopeCalculator.ScopeResult actorScope, Object ifMatch, UUID actorId) {
        Map<String, String> versions = new LinkedHashMap<>();
        Map<String, Object> selection = new LinkedHashMap<>();
        selection.put("pathParameters", path);
        selection.put("requestBody", body);
        if (ifMatch != null) selection.put("ifMatch", ifMatch);
        if (actorId != null) {
            if (!live.active(actorId)) throw new ApiException(ErrorCode.PERMISSION_DENIED, "账号不可用");
            if (identityOperation(operation)) bounds.check(actorId, operation, path, body);
        }
        if (operation == ApprovalOperation.CREATE_EXPORT) {
            UUID id = UUID.fromString(String.valueOf(body.get("contextId")));
            ContextRow context = contexts.findById(id)
                    .filter(c -> actorId != null && actorId.equals(c.ownerUserId()))
                    .orElseThrow(() -> new ApiException(ErrorCode.DATA_NOT_FOUND, "上下文不存在"));
            if (!context.expiresAt().isAfter(Instants.nowUtc()))
                throw new ApiException(ErrorCode.CONTEXT_EXPIRED, "上下文已过期");
            if (!live.contextAllowed(actorId, context)) throw new ApiException(ErrorCode.PERMISSION_DATA_SCOPE, "上下文不在当前授权范围");
            selection.put("contextFilter", live.filter(context));
            versions.put("context:" + id, digest(Map.of("filter", context.filterSnapshot(),
                    "permissionVersion", context.permissionVersion(), "expiresAt", context.expiresAt().toString())));
            return frozen(List.of(), versions, Set.of(), actorScope, selection, false);
        }
        if (identityOperation(operation)) {
            for (UUID role : CurrentDataScope.uuids(body.get("roleIds")))
                versions.put("role:" + role, resourceVersion("auth.role", role));
            if (path.get("roleId") instanceof String text) {
                UUID id = UUID.fromString(text);
                versions.put("role:" + id, resourceVersion("auth.role", id));
                matchVersion(ifMatch, versions.get("role:" + id));
            }
            if (path.get("userId") instanceof String text) {
                UUID id = UUID.fromString(text);
                versions.put("user:" + id, resourceVersion("auth.app_user", id));
                versions.put("userAuthorization:" + id, live.stamp(id));
                matchVersion(ifMatch, versions.get("user:" + id));
            }
            return frozen(List.of(), versions, Set.of(), actorScope, selection, false);
        }
        if (operation != ApprovalOperation.CREATE_DEVICE_COMMAND && operation != ApprovalOperation.UPDATE_DEVICE_PARAMETERS) {
            // These domains have not connected their complete resource/execution transactions.
            // In particular, decryption must bind its real recipient and reuse the domain grant.
            throw new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, "该操作尚未接入完整领域资源和执行事务，审批不可用");
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (body.get("groupId") instanceof String groupId) {
            UUID group = UUID.fromString(groupId);
            List<Long> groups = jdbc.queryForList("SELECT version FROM core.device_group WHERE id=? AND deleted=false FOR SHARE", Long.class, group);
            if (groups.size() != 1) throw new ApiException(ErrorCode.DATA_NOT_FOUND, "设备组不存在");
            versions.put("deviceGroup:" + group, String.valueOf(groups.get(0)));
            ids.addAll(jdbc.queryForList("SELECT device_id FROM core.device_group_member WHERE group_id=? ORDER BY device_id", UUID.class, group));
        }
        ids.addAll(CurrentDataScope.uuids(body.get("deviceIds")));
        if (path.get("deviceId") instanceof String text) ids.add(UUID.fromString(text));
        if (ids.isEmpty()) throw new ApiException(ErrorCode.VALIDATION_ERROR, "目标设备集合不能为空");
        List<DeviceRow> devices = loadDevices(ids);
        if (devices.size() != ids.size()) throw new ApiException(ErrorCode.DATA_NOT_FOUND, "目标设备不存在或不可见");
        Set<UUID> sites = devices.stream().map(DeviceRow::siteId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (actorScope != null && !covers(actorScope, sites))
            throw new ApiException(ErrorCode.PERMISSION_DATA_SCOPE, "设备超出当前管理范围");
        boolean keyChange = false;
        Set<String> sensitiveCodes = new LinkedHashSet<>();
        String command = operation == ApprovalOperation.UPDATE_DEVICE_PARAMETERS ? "APPLY_PARAMETERS" : String.valueOf(body.get("commandType"));
        if (actorId != null && "RESET".equals(command) && !rbac.permissionCodes(actorId).contains("device:reset"))
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "缺少设备重置权限");
        Object rawChanges = body.get(operation == ApprovalOperation.UPDATE_DEVICE_PARAMETERS ? "changes" : "parameterChanges");
        List<?> changes = rawChanges instanceof List<?> list ? list : List.of();
        for (DeviceRow device : devices) {
            versions.put("device:" + device.id(), String.valueOf(device.version()));
            versions.put("deviceParameters:" + device.id(), String.valueOf(device.parameterVersion()));
            ApprovalProperties.DeviceProfile profile = properties.getDeviceProfiles().stream()
                    .filter(p -> Objects.equals(p.manufacturer(), device.manufacturer()) && Objects.equals(p.model(), device.model())
                            && Objects.equals(p.protocolVersion(), device.protocolVersion()) && p.serialNumbers() != null
                            && p.serialNumbers().contains(device.serialNumber()) && p.commands() != null && p.commands().contains(command))
                    .findFirst().orElseThrow(() -> new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, "设备未绑定支持该操作的DeviceProfile"));
            versions.put("deviceProfile:" + device.id(), digest(Map.of("manufacturer", profile.manufacturer(), "model", profile.model(),
                    "protocolVersion", profile.protocolVersion(), "serialNumbers", profile.serialNumbers(), "commands", profile.commands(),
                    "parameterKinds", profile.parameterKinds() == null ? Map.of() : profile.parameterKinds())));
            for (Object raw : changes) {
                Map<String, Object> change = CurrentDataScope.object(raw);
                String code = String.valueOf(change.get("parameterCode"));
                String kind = profile.parameterKinds() == null ? null : profile.parameterKinds().get(code);
                if (!List.of("STANDARD", "NETWORK", "KEY").contains(kind == null ? "" : kind))
                    throw new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, "参数缺少已绑定的敏感类别定义");
                keyChange |= "KEY".equals(kind);
                if ("KEY".equals(kind)) sensitiveCodes.add(code);
                List<Map<String, Object>> definitions = jdbc.queryForList("""
                        SELECT id,value_type,minimum,maximum,allowed_values::text AS allowed_values
                          FROM core.device_parameter_definition
                         WHERE manufacturer=? AND model=? AND protocol_version=? AND parameter_code=?
                           AND enabled=true AND read_only=false
                        """, device.manufacturer(), device.model(), device.protocolVersion(), code);
                if (definitions.size() != 1 || !Objects.equals(definitions.get(0).get("value_type"), change.get("valueType")))
                    throw new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, "参数定义不可写或与DeviceProfile不匹配");
                validateParameter(change, definitions.get(0));
                versions.put("parameterDefinition:" + device.id() + ":" + code, digest(definitions.get(0)));
            }
        }
        selection.put("sensitiveParameterCodes", List.copyOf(sensitiveCodes));
        if (path.containsKey("deviceId")) matchVersion(ifMatch, String.valueOf(devices.get(0).version()));
        return frozen(ids.stream().sorted().toList(), versions, sites, actorScope, selection, keyChange);
    }

    private Frozen frozen(List<UUID> devices, Map<String, String> versions, Set<UUID> sites,
                          ScopeCalculator.ScopeResult scope, Map<String, Object> selection, boolean keyChange) {
        return new Frozen(devices, versions, sites, scope == null ? 0 : scope.scopeVersion(),
                properties.getPolicyVersion(), scope == null ? "" : describeScope(scope), selection, keyChange);
    }

    public void verifyUnchanged(ApprovalOperation operation, Map<String, Object> facts, UUID actorId) {
        if (!Objects.equals(properties.getPolicyVersion(), facts.get("policyVersion"))) conflict();
        if (!(facts.get("selection") instanceof Map<?, ?>)) conflict();
        if (facts.get("actorAuthorization") instanceof String stamp && !stamp.equals(live.stamp(actorId))) conflict();
        if (!(facts.get("scopeVersion") instanceof Number v) || v.longValue() != scopeOf(actorId).scopeVersion()) conflict();
        Map<String, Object> selection = CurrentDataScope.object(facts.get("selection"));
        Frozen current = freeze(operation, CurrentDataScope.object(selection.get("pathParameters")),
                CurrentDataScope.object(selection.get("requestBody")), scopeOf(actorId), selection.get("ifMatch"), actorId);
        if (!Objects.equals(facts.get("versions"), current.versions())
                || !new java.util.HashSet<>(CurrentDataScope.uuids(facts.get("deviceIds"))).equals(new java.util.HashSet<>(current.deviceIds()))
                || !Objects.equals(facts.get("keyChange"), current.keyChange())) conflict();
    }

    public boolean reviewerCovers(UUID reviewer, ApprovalOperation operation, Map<String, Object> facts) {
        return viewerCovers(reviewer, operation, facts, false);
    }

    public boolean viewerCovers(UUID viewer, ApprovalOperation operation, Map<String, Object> facts) {
        return viewerCovers(viewer, operation, facts, true);
    }

    private boolean viewerCovers(UUID reviewer, ApprovalOperation operation, Map<String, Object> facts, boolean owner) {
        if (!live.active(reviewer)) return false;
        Map<String, Object> selection = CurrentDataScope.object(facts.get("selection"));
        if (selection.isEmpty()) return false;
        Map<String, Object> body = CurrentDataScope.object(selection.get("requestBody"));
        try {
            if (operation == ApprovalOperation.CREATE_EXPORT) {
                UUID contextId = UUID.fromString(String.valueOf(body.get("contextId")));
                ContextRow original = contexts.findById(contextId).orElse(null);
                if (original == null) return false;
                ContextRow review = new ContextRow(original.id(), reviewer, original.sourceModule(), original.filterType(),
                        original.filterSchemaVersion(), original.filterSnapshot(), live.authzVersion(reviewer), original.expiresAt());
                return live.contextAllowed(reviewer, review)
                        && (owner || scopeOf(reviewer).grants().stream().anyMatch(g -> !g.ownOnly()));
            }
            if (identityOperation(operation)) {
                bounds.check(reviewer, operation, CurrentDataScope.object(selection.get("pathParameters")), body);
                return true;
            }
            if ("RESET".equals(body.get("commandType")) && !rbac.permissionCodes(reviewer).contains("device:reset")) return false;
            return covers(scopeOf(reviewer), CurrentDataScope.uuids(facts.get("siteIds")));
        } catch (ApiException | IllegalArgumentException rejected) { return false; }
    }

    public boolean covers(ScopeCalculator.ScopeResult scope, Collection<UUID> sites) {
        return CurrentDataScope.managementCovers(scope, sites);
    }
    static boolean isDeviceBatch(ApprovalOperation operation) { return operation.operationId().contains("Device"); }
    private static boolean identityOperation(ApprovalOperation operation) {
        return operation.permission().startsWith("user:") || operation.permission().equals("role:grant");
    }
    private String resourceVersion(String table, UUID id) {
        if (!Set.of("auth.role", "auth.app_user").contains(table)) throw new IllegalArgumentException();
        List<Long> versions = jdbc.queryForList("SELECT version FROM " + table + " WHERE id=? AND deleted=false FOR SHARE", Long.class, id);
        if (versions.size() != 1) throw new ApiException(ErrorCode.DATA_NOT_FOUND, "资源不存在或不可见");
        return String.valueOf(versions.get(0));
    }
    private static void matchVersion(Object ifMatch, String version) {
        if (ifMatch != null && !("\"" + version + "\"").equals(ifMatch))
            throw new ApiException(ErrorCode.RESOURCE_VERSION_CONFLICT, "资源版本已变化");
    }
    private List<DeviceRow> loadDevices(Collection<UUID> ids) {
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        return jdbc.query("SELECT id,site_id,version,parameter_version,serial_number,manufacturer,model,protocol_version"
                        + " FROM core.device WHERE deleted=false AND id IN (" + placeholders + ") ORDER BY id FOR SHARE",
                (rs, n) -> new DeviceRow(rs.getObject("id", UUID.class), rs.getObject("site_id", UUID.class),
                        rs.getLong("version"), rs.getLong("parameter_version"), rs.getString("serial_number"),
                        rs.getString("manufacturer"), rs.getString("model"), rs.getString("protocol_version")), ids.toArray());
    }
    private static String digest(Map<String, Object> value) {
        return Sha256.hex(Sha256.ofBytes(TaskParameters.canonicalJson(value).getBytes(StandardCharsets.UTF_8)));
    }

    private static void validateParameter(Map<String, Object> change, Map<String, Object> definition) {
        String field = switch (String.valueOf(change.get("valueType"))) {
            case "TEXT" -> "textValue";
            case "INTEGER" -> "integerValue";
            case "NUMBER" -> "numberValue";
            case "BOOLEAN" -> "booleanValue";
            default -> throw new ApiException(ErrorCode.VALIDATION_ERROR, "参数类型不支持");
        };
        Object value = change.get(field);
        if (value instanceof Number number) {
            java.math.BigDecimal numeric = new java.math.BigDecimal(number.toString());
            if (definition.get("minimum") instanceof Number min && numeric.compareTo(new java.math.BigDecimal(min.toString())) < 0
                    || definition.get("maximum") instanceof Number max && numeric.compareTo(new java.math.BigDecimal(max.toString())) > 0)
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "参数值超出已绑定定义的范围");
        }
        if (definition.get("allowed_values") instanceof String json) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode allowed = mapper.readTree(json);
                if (!allowed.isArray() || !java.util.stream.StreamSupport.stream(allowed.spliterator(), false)
                        .anyMatch(item -> item.equals(mapper.valueToTree(value))))
                    throw new ApiException(ErrorCode.VALIDATION_ERROR, "参数值不在已绑定定义的允许目录中");
            } catch (java.io.IOException e) { throw new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, "参数定义不可用"); }
        }
    }
    private static void conflict() { throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "审批目标、版本或授权已变化，请重新申请"); }
    public static String describeScope(ScopeCalculator.ScopeResult scope) {
        return "managementAllSites=" + scope.managementAllSites() + ",managementSites=" + scope.managementSiteIds()
                + ",scopeGrants=" + scope.grants();
    }
    private record DeviceRow(UUID id, UUID siteId, long version, long parameterVersion, String serialNumber,
                             String manufacturer, String model, String protocolVersion) { }
}
