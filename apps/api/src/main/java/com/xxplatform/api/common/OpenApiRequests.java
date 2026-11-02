package com.xxplatform.api.common;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Request validation against the original OpenAPI 3.0 schemas packaged by Maven. */
public final class OpenApiRequests {
    private static final Map<String, Object> SCHEMAS = load();

    private OpenApiRequests() { }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load() {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(8_000_000);
        options.setAllowDuplicateKeys(false);
        try (InputStream stream = OpenApiRequests.class.getResourceAsStream("/contracts/api/openapi.yaml")) {
            if (stream == null) throw new IllegalStateException("Authoritative OpenAPI resource missing");
            Map<String, Object> document = new Yaml(new SafeConstructor(options)).load(stream);
            return (Map<String, Object>) ((Map<?, ?>) document.get("components")).get("schemas");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read OpenAPI", e);
        }
    }

    public static void validate(String schema, Object value, String field) {
        check(map(SCHEMAS.get(schema)), value, field, 0);
    }

    public static java.util.Set<String> approvalOperations() {
        return java.util.Set.copyOf(map(map(map(SCHEMAS.get("ActionApprovalProposal"))
                .get("discriminator")).get("mapping")).keySet());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object object) {
        if (!(object instanceof Map<?, ?>)) throw new IllegalStateException("Invalid OpenAPI schema");
        return (Map<String, Object>) object;
    }

    private static void check(Map<String, Object> schema, Object value, String field, int depth) {
        if (depth > 80) throw invalid(field, "嵌套层数过多");
        if (schema.containsKey("$ref")) {
            String ref = String.valueOf(schema.get("$ref"));
            if (!ref.startsWith("#/components/schemas/")) throw new IllegalStateException("External schema reference");
            check(map(SCHEMAS.get(ref.substring("#/components/schemas/".length()))), value, field, depth + 1);
            return;
        }
        if (value == null) {
            if (Boolean.TRUE.equals(schema.get("nullable"))) return;
            throw invalid(field, "不能为null");
        }
        if (schema.get("allOf") instanceof List<?> all) {
            for (Object item : all) check(map(item), value, field, depth + 1);
        }
        for (String union : List.of("oneOf", "anyOf")) {
            if (schema.get(union) instanceof List<?> alternatives) {
                if (schema.get("discriminator") instanceof Map<?, ?> discriminator && value instanceof Map<?, ?> object
                        && discriminator.get("mapping") instanceof Map<?, ?> mapping) {
                    Object ref = mapping.get(object.get(discriminator.get("propertyName")));
                    if (ref instanceof String text) {
                        check(Map.of("$ref", text), value, field, depth + 1);
                        continue;
                    }
                }
                int matched = 0;
                for (Object item : alternatives) {
                    try { check(map(item), value, field, depth + 1); matched++; }
                    catch (ApiException rejected) { /* This alternative does not match. */ }
                }
                if (matched == 0 || ("oneOf".equals(union) && matched != 1))
                    throw invalid(field, "不符合主契约规定的请求类型");
            }
        }
        if (schema.get("not") instanceof Map<?, ?> prohibited) {
            boolean matches = true;
            try { check(map(prohibited), value, field, depth + 1); }
            catch (ApiException rejected) { matches = false; }
            if (matches) throw invalid(field, "不允许此值");
        }
        String type = String.valueOf(schema.getOrDefault("type", ""));
        boolean correct = switch (type) {
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            case "string" -> value instanceof String;
            case "boolean" -> value instanceof Boolean;
            case "integer" -> value instanceof Number n && decimal(n).stripTrailingZeros().scale() <= 0;
            case "number" -> value instanceof Number;
            case "" -> true;
            default -> throw new IllegalStateException("Unsupported OpenAPI type: " + type);
        };
        if (!correct) throw invalid(field, "字段类型不符合主契约");
        if (schema.get("enum") instanceof List<?> allowed && allowed.stream().noneMatch(v -> equivalent(v, value)))
            throw invalid(field, "不在允许值目录内");
        if (schema.containsKey("x-const-value") && !equivalent(schema.get("x-const-value"), value))
            throw invalid(field, "判别字段不匹配");
        if (value instanceof Map<?, ?> object) {
            if (schema.get("required") instanceof List<?> required)
                for (Object key : required)
                    if (!object.containsKey(key)) throw invalid(field + "." + key, "必填");
            Map<String, Object> properties = schema.containsKey("properties") ? map(schema.get("properties")) : Map.of();
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (properties.containsKey(key)) check(map(properties.get(key)), entry.getValue(), field + "." + key, depth + 1);
                else if (Boolean.FALSE.equals(schema.get("additionalProperties"))) throw invalid(field + "." + key, "不支持此字段");
                else if (schema.get("additionalProperties") instanceof Map<?, ?> extra)
                    check(map(extra), entry.getValue(), field + "." + key, depth + 1);
            }
        }
        if (value instanceof List<?> array) {
            bound(schema, "minItems", array.size(), true, field);
            bound(schema, "maxItems", array.size(), false, field);
            if (Boolean.TRUE.equals(schema.get("uniqueItems")) && new HashSet<>(array).size() != array.size())
                throw invalid(field, "不允许重复项");
            if (schema.containsKey("items"))
                for (int i = 0; i < array.size(); i++) check(map(schema.get("items")), array.get(i), field + "[" + i + "]", depth + 1);
        }
        if (value instanceof String text) {
            int size = text.codePointCount(0, text.length());
            bound(schema, "minLength", size, true, field);
            bound(schema, "maxLength", size, false, field);
            if (schema.get("pattern") instanceof String pattern && !Pattern.compile(pattern).matcher(text).find())
                throw invalid(field, "格式不符合主契约");
            try {
                if ("uuid".equals(schema.get("format")) && !UUID.fromString(text).toString().equalsIgnoreCase(text))
                    throw new IllegalArgumentException();
                if ("date-time".equals(schema.get("format"))) OffsetDateTime.parse(text);
            } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
                throw invalid(field, "格式不符合主契约");
            }
        }
        if (value instanceof Number n) {
            BigDecimal number = decimal(n);
            if (schema.get("minimum") instanceof Number min &&
                    (number.compareTo(decimal(min)) < 0 || Boolean.TRUE.equals(schema.get("exclusiveMinimum")) && number.compareTo(decimal(min)) == 0))
                throw invalid(field, "低于最小值");
            if (schema.get("maximum") instanceof Number max &&
                    (number.compareTo(decimal(max)) > 0 || Boolean.TRUE.equals(schema.get("exclusiveMaximum")) && number.compareTo(decimal(max)) == 0))
                throw invalid(field, "超过最大值");
        }
    }

    private static BigDecimal decimal(Number value) { return new BigDecimal(value.toString()); }
    private static boolean equivalent(Object a, Object b) {
        return a instanceof Number x && b instanceof Number y ? decimal(x).compareTo(decimal(y)) == 0 : Objects.equals(a, b);
    }
    private static void bound(Map<String, Object> schema, String key, int size, boolean minimum, String field) {
        if (schema.get(key) instanceof Number limit && (minimum ? size < limit.intValue() : size > limit.intValue()))
            throw invalid(field, "长度或数量不符合主契约");
    }
    private static ApiException invalid(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, field + "：" + reason,
                List.of(new ApiResponses.FieldErrorDetail(field, reason)));
    }
}
