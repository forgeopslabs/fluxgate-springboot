package com.fluxgate.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxgate.starter.ofrep.OfrepFlagResult;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns edge results into typed {@link FlagEvaluation}s.
 *
 * <p>
 * Only exact JSON type matches convert: boolean to {@link Boolean}, string to
 * {@link String}, integral numbers to {@link Integer} (when in range) or
 * {@link Long}, any number to {@link Double}, object to {@link Map}, array to
 * {@link List}. {@link JsonNode} and {@link Object} accept any value. Anything
 * else is a {@link ErrorCode#TYPE_MISMATCH}. Primitive classes are treated as
 * their wrappers.
 */
final class FlagValueConverter {

    private final ObjectMapper mapper;

    FlagValueConverter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    <T> FlagEvaluation<T> toEvaluation(OfrepFlagResult result, T defaultValue, Class<T> type) {
        if (!result.isSuccess()) {
            return FlagEvaluation.error(result.key(), defaultValue, result.errorCode(), result.errorDetails());
        }
        if (result.value() == null) {
            // The edge resolved to the code default.
            return new FlagEvaluation<>(result.key(), defaultValue, result.variant(), result.reason(), null, null);
        }
        Object converted = convert(result.value(), ClassUtils.resolvePrimitiveIfNecessary(type));
        if (converted == null) {
            return FlagEvaluation.error(result.key(), defaultValue, ErrorCode.TYPE_MISMATCH,
                    "Flag '" + result.key() + "' has a " + result.value().getNodeType().name().toLowerCase(Locale.ROOT)
                            + " value, expected " + type.getSimpleName());
        }
        @SuppressWarnings("unchecked") // convert() returns an instance of the (wrapped) requested type
        T value = (T) converted;
        return new FlagEvaluation<>(result.key(), value, result.variant(), result.reason(), null, null);
    }

    /**
     * @return the converted value, or {@code null} on a type mismatch
     */
    private Object convert(JsonNode node, Class<?> type) {
        if (type == Boolean.class) {
            return node.isBoolean() ? node.booleanValue() : null;
        }
        if (type == String.class) {
            return node.isTextual() ? node.textValue() : null;
        }
        if (type == Integer.class) {
            return node.isIntegralNumber() && node.canConvertToInt() ? node.intValue() : null;
        }
        if (type == Long.class) {
            return node.isIntegralNumber() && node.canConvertToLong() ? node.longValue() : null;
        }
        if (type == Double.class) {
            return node.isNumber() ? node.doubleValue() : null;
        }
        if (type == Map.class) {
            return node.isObject() ? mapper.convertValue(node, Map.class) : null;
        }
        if (type == List.class) {
            return node.isArray() ? mapper.convertValue(node, List.class) : null;
        }
        if (type == JsonNode.class) {
            return node.deepCopy();
        }
        if (type == Object.class) {
            return mapper.convertValue(node, Object.class);
        }
        return null;
    }
}
