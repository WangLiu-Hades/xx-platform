package com.xxplatform.api.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.xxplatform.api.generated.model.IngestionBatchInput;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/** Strict rules for this input only; shared defaults and generated sources are untouched. */
public final class IngestionBatchInputDeserializer extends JsonDeserializer<IngestionBatchInput> {
    private static final Set<String> ALLOWED = Set.of(
            "source", "sourceFileId", "mappingVersion", "dedupVersion");

    @Override
    public IngestionBatchInput deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || !node.isObject()) {
            throw JsonMappingException.from(parser, "An object request body is required");
        }
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            if (!ALLOWED.contains(fields.next())) {
                throw JsonMappingException.from(parser, "Unexpected ingestion request field");
            }
        }
        IngestionBatchInput input = new IngestionBatchInput();
        input.setSource(text(node, "source", parser));
        input.setMappingVersion(text(node, "mappingVersion", parser));
        input.setDedupVersion(text(node, "dedupVersion", parser));
        String fileId = text(node, "sourceFileId", parser);
        if (fileId != null) {
            try {
                UUID parsed = UUID.fromString(fileId);
                if (!parsed.toString().equalsIgnoreCase(fileId)) throw new IllegalArgumentException();
                input.setSourceFileId(parsed);
            } catch (IllegalArgumentException error) {
                throw JsonMappingException.from(parser, "sourceFileId must be a UUID");
            }
        }
        return input;
    }

    private static String text(JsonNode node, String field, JsonParser parser) throws JsonMappingException {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw JsonMappingException.from(parser, "Request fields must be strings");
        return value.textValue();
    }
}
