package com.xxplatform.api.service.approval;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.service.task.TaskParameters;

/**
 * {@code ActionApprovalProposal} 的结构校验与指纹计算。
 *
 * <p>契约把 30 个提案做成 {@code oneOf}（discriminator=operationId）。共同形状是
 * {@code operationId} + {@code requestBody} / {@code pathParameters}，另外允许一个
 * {@code ifMatch}（乐观锁版本号）随提案一起冻结。这里做四件事：
 *
 * <ol>
 *   <li><b>拒绝契约之外的键</b>——审批是安全边界，"顺手多带一个字段"不能被静默忽略：
 *       顶层键按白名单校验；{@code requestBody}/{@code pathParameters} 的键按**该操作在
 *       OpenAPI 里的请求 schema**校验（{@link #SPECS}）。这样"批的是 A、执行的是 B"里
 *       那个"B"不可能藏在没人看的字段里；</li>
 *   <li><b>路径参数必须与路径模板一致</b>：{@code /devices/{deviceId}} 只能带 {@code deviceId}，
 *       带别的键说明调用方把 body 字段塞错位置，或者路径拼错了；</li>
 *   <li>对规范化 JSON 求 SHA-256 作为 {@code requestDigest}：审批绑定的是**这次请求的完整
 *       内容**（含 {@code ifMatch}），执行时要用同一摘要比对；</li>
 *   <li>生成复核清单 {@code reviewItems} 与摘要文本。清单递归遍历嵌套对象与数组，对
 *       {@code password/secret/token/ciphertext/private/credential/otp/key} 一类键统一打码——
 *       审批人要看清"批什么"，但不能借审批页读走口令或密文。</li>
 * </ol>
 *
 * <p>规范化必须稳定：{@link TaskParameters#canonicalJson} 递归排序对象键，同一份语义内容
 * 无论字段顺序如何都得到同一摘要。数组顺序不排序（顺序可能本身有语义，例如批量设备顺序）。
 */
public final class ApprovalProposals {

    /**
     * 复核清单里被打码的键名片段（小写匹配，子串命中即打码）。
     *
     * <p>为什么用"包含"而不是"等于"：真实字段名是 {@code newPassword}、{@code accessToken}、
     * {@code privateKey}、{@code smsOtp} 这类组合词，用等值匹配会漏掉绝大部分。
     * 代价是 {@code monkey} 这类无关字段也会被打码——在审批页上"多打一次码"比"漏一个口令"便宜得多。
     */
    private static final List<String> SENSITIVE_KEY_PARTS = List.of(
            "password", "secret", "token", "ciphertext", "private", "credential", "otp", "key");

    /** 打码后的占位值；长度不暴露原值长度。 */
    private static final String MASKED = "***";

    /** 顶层允许的键；{@code ifMatch} 是 M10 新增。 */
    private static final Set<String> ALLOWED_KEYS =
            Set.of("operationId", "requestBody", "pathParameters", "ifMatch");

    /** 复核项上限（M11）：超过就拒，而不是悄悄截断——截断会让审批人以为看到的是全部。 */
    private static final int MAX_REVIEW_ITEMS = 10_000;

    /**
     * 单个展示值上限（M11）：契约 {@code ActionApprovalDetail.reviewItems[].displayValue}
     * 的 maxLength 就是 4096。超过不再"切段拼接"——切段仍是同一份超长内容塞进接口预算，
     * 契约明确要求"超上限拒绝并要求拆分申请"。
     */
    private static final int MAX_DISPLAY_VALUE = 4096;

    /** 全部展示值总字符预算（M11）：10000 项 × 4096 字符约 40MB，接口与前端都撑不住。 */
    private static final int MAX_REVIEW_TOTAL_CHARS = 4_000_000;

    /** 契约里 uuid 类型的字段格式；只做形状校验，存在性由 ApprovalTargetResolver 查库。 */
    private static final java.util.regex.Pattern UUID_PATTERN = java.util.regex.Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final int MAX_SUMMARY = 2000;

    private static final com.fasterxml.jackson.databind.ObjectMapper DISPLAY_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 30 个操作的请求形状（M10）。
     *
     * <p>数据来源是 {@code contracts/api/openapi.yaml} 的 {@code *ApprovalProposal} schema：
     * {@code pathParameters} 对应路径模板变量，{@code requestBody} 对应各 {@code *Command} 的
     * 顶层属性。**只允许列出的顶层键**——嵌套对象不在这里展开，因为提案冻结的是"这次请求的
     * 完整 body"，嵌套结构由执行端的业务校验负责。
     *
     * <p>维护约定：契约新增一个操作或改一个字段时，这里必须同步；{@code ApprovalOperation}
     * 与 {@link #SPECS} 的键集合不一致时，{@code parse} 会以"未登记的操作"或"不支持字段"
     * 直接拒绝，属于快速失败，不会静默放行。
     */
    private static final Map<String, OperationSpec> SPECS = buildSpecs();

    private ApprovalProposals() {
    }

    /**
     * 解析并校验提案。
     *
     * @param raw 请求体里的 {@code proposal} 对象
     * @return 冻结后的提案视图（含摘要、摘要文本与复核项）
     * @throws ApiException 400 {@code VALIDATION_ERROR}，{@code details[0].field="proposal"}
     */
    public static Proposal parse(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            throw invalid("proposal 不能为空");
        }
        for (String key : raw.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw invalid("proposal 不支持字段 " + key);
            }
        }
        Object operationValue = raw.get("operationId");
        if (!(operationValue instanceof String operationId)) {
            throw invalid("proposal.operationId 必填");
        }
        ApprovalOperation operation = ApprovalOperation.parse(operationId)
                .orElseThrow(() -> invalid("未登记的操作 " + operationId));
        OperationSpec spec = SPECS.get(operation.operationId());
        if (spec == null) {
            // ApprovalOperation 加了操作但这里没同步：宁可拒，也不能用"没有白名单"当放行。
            throw invalid("操作 " + operationId + " 缺少请求字段校验规则");
        }

        Map<String, Object> pathParameters = asObject(raw.get("pathParameters"), "pathParameters");
        Map<String, Object> requestBody = asObject(raw.get("requestBody"), "requestBody");
        requireAllowedKeys("pathParameters", pathParameters, spec.pathParameters());
        requireAllowedKeys("requestBody", requestBody, spec.requestBodyKeys());
        if (pathParameters.isEmpty() && requestBody.isEmpty()) {
            throw invalid("proposal 必须包含 requestBody 或 pathParameters");
        }

        Object ifMatch = raw.get("ifMatch");
        if (ifMatch != null && !(ifMatch instanceof String) && !(ifMatch instanceof Number)) {
            // ifMatch 是资源版本号：字符串或数字都接受，但不接受对象/数组——那说明调用方
            // 把别的东西塞进了这个键，冻结进摘要后只会制造一个无法解释的版本值。
            throw invalid("proposal.ifMatch 必须是字符串或数字");
        }
        if (ifMatch != null) {
            String text = String.valueOf(ifMatch).trim();
            if (text.isEmpty() || text.length() > 200) {
                throw invalid("proposal.ifMatch 长度必须在 1 到 200 之间");
            }
            ifMatch = text;
        }
        // M10：键名白名单只能挡住"多带字段"，挡不住"字段类型错了"或"必填项没给"。
        // 这里按 operationId 对应的 OpenAPI 请求 schema 做类型/必填/枚举校验：审批一旦落库
        // 就被冻结成"已核验"的申请，错误内容不能让审批人替调用方兜底。
        validateOperation(operation, pathParameters, requestBody, ifMatch);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operation.operationId());
        if (!pathParameters.isEmpty()) {
            payload.put("pathParameters", pathParameters);
        }
        if (!requestBody.isEmpty()) {
            payload.put("requestBody", requestBody);
        }
        if (ifMatch != null) {
            // 参与摘要：否则"批的时候版本是 3、执行的时候版本是 5"会共享同一个指纹。
            payload.put("ifMatch", ifMatch);
        }
        String canonicalJson = TaskParameters.canonicalJson(payload);
        String digest = Sha256.hex(Sha256.ofBytes(canonicalJson.getBytes(StandardCharsets.UTF_8)));
        int batchSize = batchSize(operation, requestBody, pathParameters);
        List<ReviewItem> reviewItems = reviewItems(payload);
        return new Proposal(
                operation,
                Collections.unmodifiableMap(pathParameters),
                Collections.unmodifiableMap(requestBody),
                canonicalJson,
                digest,
                summary(operation, requestBody, pathParameters),
                reviewItems,
                operation.requiredApprovals(batchSize));
    }

    /** 从已解封的规范化 JSON 还原复核项（读取审批时不需要重新走校验）。 */
    public static List<ReviewItem> reviewItems(String canonicalJson) {
        return reviewItems(JsonMaps.parse(canonicalJson));
    }

    private static List<ReviewItem> reviewItems(Map<String, Object> payload) {
        List<ReviewItem> items = new ArrayList<>();
        Object pathParameters = payload.get("pathParameters");
        if (pathParameters instanceof Map<?, ?> map) {
            collect(items, "path", map);
        }
        Object requestBody = payload.get("requestBody");
        if (requestBody instanceof Map<?, ?> map) {
            collect(items, "request", map);
        }
        if (items.isEmpty()) {
            String operationId = String.valueOf(payload.get("operationId"));
            items.add(new ReviewItem("proposal", "申请内容", operationId));
        }
        if (items.size() > MAX_REVIEW_ITEMS) {
            // M11：不截断。截断会让审批人以为"就这些"，而实际被执行的内容可能在被砍掉的部分里。
            // 契约要求"超上限拒绝并要求拆分申请"，所以这里的错误信息要直接告诉调用方怎么做。
            throw invalid("审批展示项超过 " + MAX_REVIEW_ITEMS + " 项上限，请拆分申请后重新提交");
        }
        int totalChars = items.stream().mapToInt(item -> item.displayValue().length()).sum();
        if (totalChars > MAX_REVIEW_TOTAL_CHARS) {
            // 数组/对象之外还有"总预算"：10000 项 × 4096 字符约 40MB，接口和前端都撑不住。
            // 同样是拒绝 + 要求拆分，而不是把内容砍掉一部分继续批。
            throw invalid("审批展示内容超过 " + MAX_REVIEW_TOTAL_CHARS + " 字符预算，请拆分申请后重新提交");
        }
        return List.copyOf(items);
    }

    /**
     * 按操作在 OpenAPI 里的请求 schema 做严格校验（M10）。
     *
     * <p>为什么不能只做键名白名单：审批一旦落库，状态就从"客户端提交的草稿"变成"服务端已核验的
     * 申请"。如果 {@code createUser} 的 {@code roleIds} 是字符串、{@code initialPassword} 缺失，
     * 或者 {@code updateDeviceParameters} 没有 {@code If-Match}，审批人批的就不是一个可执行的动作，
     * 而是执行时才会炸掉的半成品——那等于把校验成本转嫁给审批人。
     *
     * <p>这里覆盖契约里写明的必填、类型与枚举；uuid 的**存在性**校验放在
     * {@code ApprovalTargetResolver}（要查库），本方法只保证形状。
     */
    private static void validateOperation(ApprovalOperation operation,
                                          Map<String, Object> path,
                                          Map<String, Object> body,
                                          Object ifMatch) {
        switch (operation.operationId()) {
            case "createDeviceCommand", "reviseDeviceCommand" -> {
                // 契约：deviceIds 与 groupId 二选一（或同时给，服务端取并集）。
                Object deviceIds = body.get("deviceIds");
                Object groupId = body.get("groupId");
                if (deviceIds == null && groupId == null) {
                    throw invalid("proposal.requestBody 必须包含 deviceIds 或 groupId");
                }
                if (deviceIds != null) {
                    requireUuidList("requestBody.deviceIds", deviceIds, true, 10_000);
                }
                if (groupId != null) {
                    requireUuid("requestBody.groupId", groupId);
                }
                if (body.get("parameterChanges") != null) {
                    requireObject("requestBody.parameterChanges", body.get("parameterChanges"));
                }
                optionalReason(body);
            }
            case "updateDeviceParameters" -> {
                requireUuid("pathParameters.deviceId", path.get("deviceId"));
                requireObject("requestBody.changes", body.get("changes"));
                // 契约把 ifMatch 列为必填：没有版本号就冻结不了"批的是哪一个版本"。
                requireIfMatch(ifMatch);
                optionalReason(body);
            }
            case "retryDeviceCommand" -> {
                requireUuid("pathParameters.taskId", path.get("taskId"));
                requireUuidList("requestBody.failedDeviceIds", body.get("failedDeviceIds"), false, 10_000);
                optionalReason(body);
            }
            case "splitProfile" -> {
                requireUuid("pathParameters.profileId", path.get("profileId"));
                requireUuid("requestBody.sourceProfileId", body.get("sourceProfileId"));
                requireUuidList("requestBody.movingTargetLinkIds", body.get("movingTargetLinkIds"), true);
                requireUuidList("requestBody.movingEvidenceIds", body.get("movingEvidenceIds"), true);
                requireIfMatch(ifMatch);
                optionalReason(body);
            }
            case "createExport" -> {
                requireUuid("requestBody.contextId", body.get("contextId"));
                requireEnum("requestBody.format", body.get("format"), Set.of("XLSX", "CSV", "ZIP"));
                Object columns = body.get("columns");
                if (columns != null) {
                    requireStringList("requestBody.columns", columns, 1, 500);
                }
                Object purpose = body.get("purpose");
                if (purpose != null) {
                    requireText("requestBody.purpose", purpose, 10, 1000);
                }
            }
            case "createUser" -> {
                requireText("requestBody.userCode", body.get("userCode"), 1, 200);
                requireText("requestBody.loginName", body.get("loginName"), 1, 200);
                requireText("requestBody.displayName", body.get("displayName"), 1, 200);
                // 与 PasswordService.MIN_LENGTH/MAX_LENGTH 对齐：提案层不能放过一个执行时必然失败的口令，
                // 否则审批人批准的是一个不可执行的动作（M04 要求口令 15-128 个 Unicode 码点）。
                requireText("requestBody.initialPassword", body.get("initialPassword"), 15, 128);
                requireUuidList("requestBody.roleIds", body.get("roleIds"), true);
                optionalScope(body);
            }
            case "updateUser" -> {
                requireUuid("pathParameters.userId", path.get("userId"));
                requireUuidList("requestBody.roleIds", body.get("roleIds"), true);
                optionalScope(body);
            }
            case "disableUser", "enableUser", "deleteUser" -> {
                requireUuid("pathParameters.userId", path.get("userId"));
                if (!"deleteUser".equals(operation.operationId())) {
                    requireText("requestBody.reason", body.get("reason"), 10, 1000);
                }
            }
            case "changeUserPassword" -> {
                requireUuid("pathParameters.userId", path.get("userId"));
                requireText("requestBody.currentPassword", body.get("currentPassword"), 1, 128);
                requireText("requestBody.newPassword", body.get("newPassword"), 15, 128);
            }
            case "setRolePermissions", "setRoleScopes", "updateRole" -> requireUuid("pathParameters.roleId",
                    path.get("roleId"));
            case "deleteRole" -> requireUuid("pathParameters.roleId", path.get("roleId"));
            case "readDecryptionResult" -> requireUuid("pathParameters.requestId", path.get("requestId"));
            case "exportProfile" -> requireUuid("pathParameters.profileId", path.get("profileId"));
            case "exportDataset" -> requireUuid("pathParameters.datasetId", path.get("datasetId"));
            case "exportPersonLibrary" -> requireUuid("pathParameters.libraryId", path.get("libraryId"));
            case "exportAnalysisResults" -> requireUuid("pathParameters.analysisTaskId",
                    path.get("analysisTaskId"));
            case "replayIngestionBatch" -> requireUuid("pathParameters.batchId", path.get("batchId"));
            default -> {
                // 其余操作的表单形状由各自键白名单兜底；通用检查仍在下面统一执行。
            }
        }
        requireNoNulls("pathParameters", path);
        requireNoNulls("requestBody", body);
    }

    private static void requireNoNulls(String prefix, Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() == null) {
                throw invalid("proposal." + prefix + "." + entry.getKey() + " 不能为 null");
            }
            if (entry.getValue() instanceof String text && text.isBlank()) {
                throw invalid("proposal." + prefix + "." + entry.getKey() + " 不能为空字符串");
            }
        }
    }

    private static void requireIfMatch(Object ifMatch) {
        if (ifMatch == null || String.valueOf(ifMatch).isBlank()) {
            throw invalid("proposal.ifMatch 必填（该操作要求资源版本号）");
        }
    }

    private static void requireText(String field, Object value, int min, int max) {
        if (!(value instanceof String text)) {
            throw invalid("proposal." + field + " 必须是字符串");
        }
        String trimmed = text.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw invalid("proposal." + field + " 长度必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private static void requireEnum(String field, Object value, Set<String> allowed) {
        if (!(value instanceof String text) || !allowed.contains(text)) {
            throw invalid("proposal." + field + " 只能是 " + allowed);
        }
    }

    private static void requireObject(String field, Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            throw invalid("proposal." + field + " 必须是非空对象");
        }
    }

    private static void requireUuid(String field, Object value) {
        if (!(value instanceof String text) || !UUID_PATTERN.matcher(text).matches()) {
            throw invalid("proposal." + field + " 必须是 UUID");
        }
    }

    private static void requireUuidList(String field, Object value, boolean allowEmpty) {
        requireUuidList(field, value, allowEmpty, 500);
    }

    /**
     * 设备集合类字段允许更大上限：一次批量命令可能点名上千台设备，而复核清单本身
     * 还有 10000 项的硬预算兜底；500 只适合角色/证据这类关联 id 数组。
     */
    private static void requireUuidList(String field, Object value, boolean allowEmpty, int max) {
        if (!(value instanceof List<?> list)) {
            throw invalid("proposal." + field + " 必须是数组");
        }
        if (!allowEmpty && list.isEmpty()) {
            throw invalid("proposal." + field + " 不能为空数组");
        }
        if (list.size() > max) {
            throw invalid("proposal." + field + " 最多 " + max + " 项");
        }
        for (Object item : list) {
            if (!(item instanceof String text) || !UUID_PATTERN.matcher(text).matches()) {
                throw invalid("proposal." + field + " 的每一项都必须是 UUID");
            }
        }
    }

    private static void requireStringList(String field, Object value, int min, int max) {
        if (!(value instanceof List<?> list)) {
            throw invalid("proposal." + field + " 必须是数组");
        }
        if (list.size() < min || list.size() > max) {
            throw invalid("proposal." + field + " 项数必须在 " + min + " 到 " + max + " 之间");
        }
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw invalid("proposal." + field + " 的每一项都必须是非空字符串");
            }
        }
    }

    private static void optionalReason(Map<String, Object> body) {
        Object reason = body.get("reason");
        if (reason != null) {
            requireText("requestBody.reason", reason, 10, 1000);
        }
    }

    private static void optionalScope(Map<String, Object> body) {
        for (String key : List.of("siteScope", "dataScope")) {
            Object value = body.get(key);
            if (value != null && !(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
                throw invalid("proposal.requestBody." + key + " 必须是对象或数组");
            }
        }
    }

    /**
     * 递归展开一个对象：敏感键打码、嵌套对象继续下钻、数组按下标展开。
     *
     * <p>为什么要递归：敏感值几乎从不放在第一层。真实载荷是
     * {@code {requestBody:{command:{parameters:{password:"..."}}}}} 这种形状，只打码第一层等于没打。
     *
     * @param fieldPrefix 字段码前缀（{@code request}/{@code path}），逐层拼上 key 与下标
     */
    private static void collect(List<ReviewItem> items, String fieldPrefix, Map<?, ?> map) {
        if (map.isEmpty()) {
            addItem(items, fieldPrefix, fieldPrefix, "{}");
            return;
        }
        for (Map.Entry<?, ?> entry : sortedEntries(map)) {
            String key = String.valueOf(entry.getKey());
            String fieldCode = fieldPrefix + "." + key;
            if (isSensitive(key)) {
                // 打码后不再下钻：敏感子结构（例如 privateKey 里的字段）整体不展示。
                addItem(items, fieldCode, key, MASKED);
                continue;
            }
            collectValue(items, fieldCode, key, entry.getValue());
        }
    }

    /** 递归处理单个值；标量/空集合直接成为一项，长值拒绝并要求拆分申请。 */
    private static void collectValue(List<ReviewItem> items, String fieldCode, String label, Object value) {
        if (value instanceof Map<?, ?> nested) {
            collect(items, fieldCode, nested);
            return;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                addItem(items, fieldCode, label, "[]");
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                collectValue(items, fieldCode + "[" + i + "]", label + " " + (i + 1), list.get(i));
            }
            return;
        }
        if (value instanceof String text) {
            // M11：值为"序列化 JSON 字符串"时（例如调用方把整个参数对象再 JSON.stringify 了一次，
            // 或上游把 {password:...} 塞进一个 text 字段），按标量展示会把里面的明文原样漏给审批人，
            // 而按标量递归打码又永远看不到子键。这里尝试解析：能解析成对象/数组就继续递归，
            // 于是 password/secret 这类嵌套键照样被打码；解析失败才当普通文本。
            Object embedded = parseEmbeddedJson(text);
            if (embedded instanceof Map<?, ?> nested) {
                collect(items, fieldCode, nested);
                return;
            }
            if (embedded instanceof List<?> list) {
                if (list.isEmpty()) {
                    addItem(items, fieldCode, label, "[]");
                    return;
                }
                for (int i = 0; i < list.size(); i++) {
                    collectValue(items, fieldCode + "[" + i + "]", label + " " + (i + 1), list.get(i));
                }
                return;
            }
        }
        addItem(items, fieldCode, label, value);
    }

    /**
     * 尝试把一个字符串当作 JSON 对象/数组解析。
     *
     * <p>只接受以 {@code {} 或 {@code [} 开头的字符串，避免把 {@code 18500000000} 这种手机号
     * 或普通文本误判成结构；解析失败返回 {@code null}，由调用方按普通标量处理。
     */
    private static Object parseEmbeddedJson(String text) {
        String trimmed = text.trim();
        if (trimmed.length() < 2) {
            return null;
        }
        char first = trimmed.charAt(0);
        if (first != '{' && first != '[') {
            return null;
        }
        try {
            return DISPLAY_MAPPER.readValue(trimmed, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 加一项复核项（M11）。
     *
     * <p>为什么不截断也不切段：审批人必须能看到完整内容才能为它负责；截断会让"没看到的尾巴"
     * 跟着批准一起生效，切段则把同一份超长内容继续塞进接口预算。契约要求超上限时
     * 明确拒绝并让调用方拆分申请，所以这里直接 4xx。
     */
    private static void addItem(List<ReviewItem> items, String fieldCode, String label, Object value) {
        String text = display(value);
        if (text.length() > MAX_DISPLAY_VALUE) {
            throw invalid("审批展示值 " + fieldCode + " 超过 " + MAX_DISPLAY_VALUE
                    + " 字符，请拆分申请（例如减少单次设备数或拆开长文本）后重新提交");
        }
        items.add(new ReviewItem(fieldCode, label, text));
    }

    /**
     * 按键名排序后再生成复核项。
     *
     * <p>载荷来自 Jackson 反序列化，HashMap 的迭代顺序取决于哈希，同一份申请可能每次渲染出不同的
     * 字段顺序。审批页要让人"两次看到同一屏"，因此这里显式排序；顺序与 {@code canonicalJson}
     * 的按键排序一致，重建结果也能与原始清单逐项对齐。
     */
    private static List<Map.Entry<?, ?>> sortedEntries(Map<?, ?> map) {
        List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
        return entries;
    }

    private static String display(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return json(value);
        }
        return String.valueOf(value);
    }

    /** 复核清单里的结构化值用紧凑 JSON 展示；失败就退回 {@code toString}，不因为展示问题失败。 */
    private static String json(Object value) {
        try {
            return DISPLAY_MAPPER.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static String summary(ApprovalOperation operation,
                                  Map<String, Object> requestBody,
                                  Map<String, Object> pathParameters) {
        List<String> fields = new ArrayList<>();
        pathParameters.keySet().forEach(key -> fields.add("path." + key));
        requestBody.keySet().forEach(key -> fields.add("request." + key));
        String text = operation.label() + "（" + operation.operationId() + "），涉及字段："
                + String.join("、", fields);
        return text.length() <= MAX_SUMMARY ? text : text.substring(0, MAX_SUMMARY);
    }

    /**
     * 服务端从载荷里数"这次涉及多少台设备"（M09）。
     *
     * <p>为什么必须在服务端数，而不是信调用方传来的票数：{@code required_approvals} 决定
     * 要几个人批，如果由客户端决定，它只要声称"1 台设备"就能把双人审批降成单人。
     *
     * <p>三个来源按顺序取：{@code deviceIds}（下发/修订/批量设备命令）、
     * {@code failedDeviceIds}（重试失败设备）、{@code path.deviceIds}（老形状）。
     * 都取不到时按 0 处理——宁可少算成 1 票，也不要把"没写清楚"当成"批量"。
     */
    private static int batchSize(ApprovalOperation operation,
                                 Map<String, Object> requestBody,
                                 Map<String, Object> pathParameters) {
        if (!operation.name().contains("DEVICE")) {
            return 0;
        }
        int size = listSize(requestBody.get("deviceIds"));
        if (size < 0) {
            size = listSize(requestBody.get("failedDeviceIds"));
        }
        if (size < 0) {
            size = listSize(pathParameters.get("deviceIds"));
        }
        return Math.max(size, 0);
    }

    /** 列表元素个数；不是列表时返回 -1（表示"这个来源没给"）。 */
    private static int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : -1;
    }

    private static boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        for (String part : SENSITIVE_KEY_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static void requireAllowedKeys(String which, Map<String, Object> values, Set<String> allowed) {
        for (String key : values.keySet()) {
            if (!allowed.contains(key)) {
                throw invalid("proposal." + which + " 不支持字段 " + key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String field) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid("proposal." + field + " 必须是对象");
        }
        return (Map<String, Object>) map;
    }

    private static ApiException invalid(String reason) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, reason,
                List.of(new ApiResponses.FieldErrorDetail("proposal", reason)), null);
    }

    /**
     * 供测试核对"枚举与请求形状表没有漂移"。
     *
     * <p>两张表分开维护是刻意的：{@link ApprovalOperation} 管权限与票数，本类管字段白名单。
     * 漂移的后果是某个操作在审批层永远解析失败（新增操作忘了加 spec），所以用一条单测钉住。
     */
    static Set<String> supportedOperationIds() {
        return SPECS.keySet();
    }

    /**
     * 30 个操作的请求形状。
     *
     * @param pathParameters 路径模板变量集合；无路径参数的操作是空集合
     * @param requestBodyKeys 请求体顶层键集合；无可选/必填 body 的操作是空集合
     */
    private record OperationSpec(Set<String> pathParameters, Set<String> requestBodyKeys) {
    }

    private static Map<String, OperationSpec> buildSpecs() {
        Map<String, OperationSpec> specs = new LinkedHashMap<>();
        // 设备命令族：createDeviceCommand 没有路径参数；其余三个按路径模板带 id。
        Set<String> deviceCommandBody = Set.of("commandType", "deviceIds", "groupId", "parameterChanges",
                "reason", "name", "regionCode");
        specs.put("createDeviceCommand", new OperationSpec(Set.of(), deviceCommandBody));
        specs.put("updateDeviceParameters", new OperationSpec(Set.of("deviceId"),
                Set.of("changes", "reason")));
        specs.put("retryDeviceCommand", new OperationSpec(Set.of("taskId"),
                Set.of("failedDeviceIds", "reason")));
        specs.put("reviseDeviceCommand", new OperationSpec(Set.of("taskId"),
                Set.of("commandType", "deviceIds", "groupId", "parameterChanges", "reason", "name",
                        "regionCode")));

        // 权限/角色/用户族
        specs.put("setRolePermissions", new OperationSpec(Set.of("roleId"),
                Set.of("permissionCodes", "menuIds", "reason")));
        specs.put("setRoleScopes", new OperationSpec(Set.of("roleId"),
                Set.of("siteScope", "dataScope", "reason")));
        specs.put("createUser", new OperationSpec(Set.of(),
                Set.of("userCode", "loginName", "displayName", "initialPassword", "roleIds", "siteScope",
                        "dataScope")));
        specs.put("updateUser", new OperationSpec(Set.of("userId"),
                Set.of("displayName", "roleIds", "siteScope", "dataScope")));
        specs.put("deleteUser", new OperationSpec(Set.of("userId"), Set.of()));
        specs.put("disableUser", new OperationSpec(Set.of("userId"), Set.of("reason")));
        specs.put("enableUser", new OperationSpec(Set.of("userId"), Set.of("reason")));
        Set<String> roleBody = Set.of("roleCode", "name", "description", "permissionCodes", "menuIds",
                "siteScope", "dataScope");
        specs.put("createRole", new OperationSpec(Set.of(), roleBody));
        specs.put("updateRole", new OperationSpec(Set.of("roleId"), roleBody));
        specs.put("deleteRole", new OperationSpec(Set.of("roleId"), Set.of()));
        specs.put("changeUserPassword", new OperationSpec(Set.of("userId"),
                Set.of("currentPassword", "newPassword", "reason")));

        // 接入/档案族
        specs.put("replayIngestionBatch", new OperationSpec(Set.of("batchId"),
                Set.of("reason", "mappingVersion", "dedupVersion")));
        specs.put("createIngestionBackfill", new OperationSpec(Set.of(),
                Set.of("source", "timeRange", "reason")));
        specs.put("mergeProfiles", new OperationSpec(Set.of(),
                Set.of("sourceProfileIds", "retainedProfileId", "evidenceIds", "reason")));
        specs.put("splitProfile", new OperationSpec(Set.of("profileId"),
                Set.of("sourceProfileId", "movingTargetLinkIds", "movingEvidenceIds", "reason")));

        // 导出族
        specs.put("createExport", new OperationSpec(Set.of(),
                Set.of("contextId", "format", "columns", "purpose")));
        specs.put("exportAnalysisResults", new OperationSpec(Set.of("analysisTaskId"),
                Set.of("format", "columns", "resultIds", "dataTypes")));
        specs.put("exportAnalysisTasks", new OperationSpec(Set.of(),
                Set.of("analysisTaskIds", "format", "reason")));
        specs.put("exportDataset", new OperationSpec(Set.of("datasetId"),
                Set.of("format", "columns", "resultIds", "dataTypes")));
        specs.put("exportPersonLibrary", new OperationSpec(Set.of("libraryId"),
                Set.of("format", "reason")));
        specs.put("exportPersonLibraries", new OperationSpec(Set.of(),
                Set.of("libraryIds", "format", "reason")));
        specs.put("exportProfile", new OperationSpec(Set.of("profileId"), Set.of("format", "reason")));
        specs.put("exportMetrics", new OperationSpec(Set.of(),
                Set.of("source", "metricCodes", "dataType", "range", "siteIds", "regionCode", "format")));
        specs.put("exportAuditEvents", new OperationSpec(Set.of(),
                Set.of("scope", "format", "reason", "filter")));
        specs.put("exportSmsLogs", new OperationSpec(Set.of(),
                Set.of("search", "format", "reason", "scope", "deliveryIds")));
        specs.put("readDecryptionResult", new OperationSpec(Set.of("requestId"), Set.of()));
        return Collections.unmodifiableMap(specs);
    }

    /** 复核项。 */
    public record ReviewItem(String fieldCode, String label, String displayValue) {
    }

    /** 冻结后的提案。 */
    public record Proposal(ApprovalOperation operation,
                           Map<String, Object> pathParameters,
                           Map<String, Object> requestBody,
                           String canonicalJson,
                           String digestHex,
                           String summary,
                           List<ReviewItem> reviewItems,
                           int requiredApprovals) {
    }

    /** 把 Jackson 给出的通用 Map/List 收敛为 {@code Map<String,Object>}，供规范化使用。 */
    static final class JsonMaps {

        private JsonMaps() {
        }

        static Map<String, Object> parse(String json) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        });
            } catch (java.io.IOException e) {
                throw new IllegalStateException("审批载荷不是合法 JSON", e);
            }
        }
    }
}
