package com.forgeopslabs.fluxgate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of evaluating every flag for one context.
 *
 * @param flags        evaluations by flag key; per-flag failures carry an
 *                     error code. Empty when the whole request failed.
 * @param version      the edge's flag-set version ({@code ETag}), or
 *                     {@code null}
 * @param changed      {@code false} when the version equals the previous
 *                     result's for this context (including a {@code 304 Not
 *                     Modified}, where {@code flags} is the previous result)
 * @param errorCode    why the bulk request failed, or {@code null}
 * @param errorMessage human-readable error detail, or {@code null}
 */
public record BulkEvaluation(
        Map<String, FlagEvaluation<Object>> flags,
        String version,
        boolean changed,
        ErrorCode errorCode,
        String errorMessage) {

    public BulkEvaluation {
        flags = Collections.unmodifiableMap(new LinkedHashMap<>(flags));
    }

    /**
     * @param errorCode    why the request failed
     * @param errorMessage human-readable detail
     * @return a failed bulk evaluation with no flags
     */
    public static BulkEvaluation error(ErrorCode errorCode, String errorMessage) {
        return new BulkEvaluation(Map.of(), null, false, errorCode, errorMessage);
    }

    /**
     * @return this result marked as unchanged (served after a 304)
     */
    public BulkEvaluation unchanged() {
        return new BulkEvaluation(flags, version, false, errorCode, errorMessage);
    }

    /**
     * @return {@code true} when the whole bulk request failed
     */
    public boolean isError() {
        return errorCode != null;
    }
}
