package com.forgeopslabs.fluxgate.openfeature;

import com.fasterxml.jackson.databind.JsonNode;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts an edge JSON value to an OpenFeature {@link Value}: objects become
 * {@link dev.openfeature.sdk.Structure}s, arrays become lists, integral numbers
 * that fit an {@code int} become integers and other numbers become doubles.
 */
final class ValueMapper {

    private ValueMapper() {
    }

    static Value toValue(JsonNode node) {
        return switch (node.getNodeType()) {
            case BOOLEAN -> new Value(node.booleanValue());
            case STRING -> new Value(node.textValue());
            case NUMBER -> node.isIntegralNumber() && node.canConvertToInt()
                    ? new Value(node.intValue())
                    : new Value(node.doubleValue());
            case ARRAY -> toList(node);
            case OBJECT -> toStructure(node);
            // NULL and MISSING; BINARY and POJO never come out of parsed JSON.
            default -> new Value();
        };
    }

    private static Value toList(JsonNode array) {
        List<Value> values = new ArrayList<>(array.size());
        array.forEach(element -> values.add(toValue(element)));
        return new Value(values);
    }

    private static Value toStructure(JsonNode object) {
        Map<String, Value> attributes = new LinkedHashMap<>();
        object.fields().forEachRemaining(field -> attributes.put(field.getKey(), toValue(field.getValue())));
        return new Value(new ImmutableStructure(attributes));
    }
}
