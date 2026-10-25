package com.xxplatform.api.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.xxplatform.api.generated.model.ReplayInput;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/** Model-specific strict input. Optional fields may be omitted, but may not be null. */
public final class IngestionReplayInputDeserializer extends JsonDeserializer<ReplayInput> {
    private static final Set<String> ALLOWED = Set.of("reason", "mappingVersion", "dedupVersion");

    @Override
    public ReplayInput deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || !node.isObject()) {
            throw JsonMappingException.from(parser, "An object request body is required");
        }
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            if (!ALLOWED.contains(fields.next())) {
                throw JsonMappingException.from(parser, "Unexpected replay request field");
            }
        }
        ReplayInput input = new ReplayInput();
        input.setReason(text(node, "reason", parser));
        input.setMappingVersion(text(node, "mappingVersion", parser));
        input.setDedupVersion(text(node, "dedupVersion", parser));
        return input;
    }

    private static String text(JsonNode node, String field, JsonParser parser) throws JsonMappingException {
        JsonNode value = node.get(field);
        if (value == null) return null;
        if (!value.isTextual()) {
            throw JsonMappingException.from(parser, field + " must be a non-null string when provided");
        }
        return value.textValue();
    }
}
