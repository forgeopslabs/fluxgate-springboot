package com.forgeopslabs.fluxgate.ofrep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgeopslabs.fluxgate.ErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses OFREP response bodies.
 */
final class OfrepResponseParser {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper mapper;

    OfrepResponseParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Parses a single-flag 200 body.
     */
    OfrepFlagResult parseFlag(byte[] body, String requestedKey) {
        return toFlag(readObject(body, "flag evaluation"), requestedKey);
    }

    /**
     * Parses a bulk 200 body. The version comes from the {@code ETag} header,
     * falling back to {@code metadata.version}.
     */
    OfrepBulkResult parseBulk(byte[] body, String etagHeader) {
        JsonNode root = readObject(body, "bulk evaluation");
        JsonNode flags = root.get("flags");
        if (flags == null || !flags.isArray()) {
            throw OfrepErrors.malformed("bulk evaluation", new IllegalStateException("missing 'flags' array"));
        }
        List<OfrepFlagResult> results = new ArrayList<>(flags.size());
        for (JsonNode flag : flags) {
            if (!flag.isObject()) {
                throw OfrepErrors.malformed("bulk evaluation", new IllegalStateException("flag entry is not an object"));
            }
            results.add(toFlag(flag, null));
        }
        String version = etagHeader != null ? etagHeader : text(root.path("metadata"), "version");
        return new OfrepBulkResult(results, version, false);
    }

    /**
     * Maps a non-success response to an exception. The body may be an OFREP
     * error object or plain text.
     */
    OfrepException error(int status, byte[] body) {
        String raw = new String(body, StandardCharsets.UTF_8).trim();
        try {
            JsonNode node = mapper.readTree(raw);
            if (node != null && node.isObject()) {
                return OfrepErrors.fromResponse(status, text(node, "errorCode"), text(node, "errorDetails"));
            }
        } catch (JsonProcessingException notJson) {
            // plain-text body, use it as the detail
        }
        return OfrepErrors.fromResponse(status, null, raw);
    }

    private JsonNode readObject(byte[] body, String what) {
        try {
            JsonNode node = mapper.readTree(body);
            if (node == null || !node.isObject()) {
                throw OfrepErrors.malformed(what, new IllegalStateException("body is not a JSON object"));
            }
            return node;
        } catch (IOException e) {
            throw OfrepErrors.malformed(what, e);
        }
    }

    private OfrepFlagResult toFlag(JsonNode node, String requestedKey) {
        String key = node.hasNonNull("key") ? node.get("key").asText() : requestedKey;
        String wireError = text(node, "errorCode");
        ErrorCode errorCode = wireError == null ? null : ErrorCode.fromWire(wireError, ErrorCode.GENERAL);
        JsonNode value = node.get("value");
        String reason = text(node, "reason");
        if (reason == null) {
            reason = errorCode == null ? "UNKNOWN" : "ERROR";
        }
        return new OfrepFlagResult(
                key,
                value == null || value.isNull() ? null : value,
                reason,
                text(node, "variant"),
                errorCode,
                text(node, "errorDetails"),
                metadata(node.get("metadata")));
    }

    private Map<String, Object> metadata(JsonNode node) {
        return node != null && node.isObject() ? mapper.convertValue(node, MAP_TYPE) : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }
}
