package com.xxplatform.api.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xxplatform.api.generated.model.*;
import java.io.IOException;

/** Strict body validation before binding the generated discriminated DTO. */
public final class CaptureSearchInputDeserializer extends JsonDeserializer<CaptureSearchRequest> {
    private static final ObjectMapper TYPED = new ObjectMapper().registerModule(new JavaTimeModule());
    @Override public CaptureSearchRequest deserializeWithType(JsonParser parser, DeserializationContext context,
            com.fasterxml.jackson.databind.jsontype.TypeDeserializer typeDeserializer) throws IOException {
        return deserialize(parser, context);
    }
    @Override public CaptureSearchRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        var query = DataQueryInputMapper.capture(node, true);
        Class<? extends CaptureSearchRequest> type = switch (query.dataType()) {
            case "PLATE" -> PlateCaptureSearchRequest.class;
            case "FENCE" -> FenceCaptureSearchRequest.class;
            case "MAC" -> MacCaptureSearchRequest.class;
            case "FACE" -> FaceCaptureSearchRequest.class;
            default -> throw ApiInputValidator.invalid("dataType", "Unsupported dataType");
        };
        CaptureSearchRequest request = TYPED.treeToValue(node, type);
        // Generated optional arrays may default to empty lists. Restore the raw request's
        // validated presence: omitted -> null; supplied empty -> empty only where allowed.
        if (request instanceof PlateCaptureSearchRequest value) {
            value.setDataType(query.dataType());
            value.setSiteIds(query.siteIds());
            value.setTargetIds(query.targetIds());
            value.setDeviceIds(query.deviceIds());
        }
        else if (request instanceof FenceCaptureSearchRequest value) {
            value.setDataType(query.dataType());
            value.setSiteIds(query.siteIds());
            value.setTargetIds(query.targetIds());
            value.setDeviceIds(query.deviceIds());
        }
        else if (request instanceof MacCaptureSearchRequest value) {
            value.setDataType(query.dataType());
            value.setSiteIds(query.siteIds());
            value.setTargetIds(query.targetIds());
            value.setDeviceIds(query.deviceIds());
        }
        else if (request instanceof FaceCaptureSearchRequest value) {
            value.setDataType(query.dataType());
            value.setSiteIds(query.siteIds());
            value.setTargetIds(query.targetIds());
            value.setDeviceIds(query.deviceIds());
        }
        return request;
    }
}
