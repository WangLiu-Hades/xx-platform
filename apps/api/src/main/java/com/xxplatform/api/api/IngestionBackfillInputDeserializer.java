package com.xxplatform.api.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.xxplatform.api.generated.model.BackfillInput;
import com.xxplatform.api.generated.model.TimeRange;
import java.io.IOException;
import java.util.Set;
import static com.xxplatform.api.api.ApiInputValidator.*;

public final class IngestionBackfillInputDeserializer extends JsonDeserializer<BackfillInput> {
    @Override public BackfillInput deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        object(node, "body", Set.of("source", "timeRange", "reason"));
        var rangeNode = node.get("timeRange");
        object(rangeNode, "timeRange", Set.of("from", "to"));
        var range = new TimeRange();
        range.setFrom(instant(text(rangeNode, "from", true), "timeRange.from"));
        range.setTo(instant(text(rangeNode, "to", true), "timeRange.to"));
        var input = new BackfillInput();
        input.setSource(text(node, "source", true));
        input.setReason(text(node, "reason", true));
        input.setTimeRange(range);
        return input;
    }
}
