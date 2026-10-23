package com.forgeopslabs.fluxgate.ofrep;

import com.fasterxml.jackson.databind.JsonNode;
import com.forgeopslabs.fluxgate.ErrorCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One flag as returned by the edge: an OFREP success object or a per-flag
 * error object.
 *
 * @param key          the flag key
 * @param value        the raw JSON value, or {@code null} when the edge sent
 *                     none (use the code default)
 * @param reason       the evaluation reason
 * @param variant      the served variant, or {@code null}
 * @param errorCode    the error code for an error object, otherwise
 *                     {@code null}
 * @param errorDetails error detail for an error object, otherwise
 *                     {@code null}
 * @param metadata     flag metadata (never {@code null})
 */
public record OfrepFlagResult(
        String key,
        JsonNode value,
        String reason,
        String variant,
        ErrorCode errorCode,
        String errorDetails,
        Map<String, Object> metadata) {

    public OfrepFlagResult {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * @return {@code true} for a success object
     */
    public boolean isSuccess() {
        return errorCode == null;
    }
}
