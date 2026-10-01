package com.fluxgate.starter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of evaluating one flag.
 *
 * <p>
 * On failure {@code value} is the caller's default, {@code reason} is
 * {@value #ERROR_REASON} and {@code errorCode} says why.
 *
 * @param key          the flag key
 * @param value        the resolved value, or the default on error
 * @param variant      the variant served by the edge, or {@code null}
 * @param reason       the edge reason ({@code STATIC}, {@code TARGETING_MATCH},
 *                     {@code SPLIT}, {@code DISABLED}, {@code DEFAULT},
 *                     {@code UNKNOWN}) or {@value #ERROR_REASON}
 * @param errorCode    why the default was returned, or {@code null} on success
 * @param errorMessage human-readable error detail, or {@code null} on success
 * @param metadata     the flag's OFREP {@code metadata} object; immutable,
 *                     empty when the edge sent none and on errors
 * @param <T>          the value type
 */
public record FlagEvaluation<T>(
        String key,
        T value,
        String variant,
        String reason,
        ErrorCode errorCode,
        String errorMessage,
        Map<String, Object> metadata) {

    /**
     * Copies {@code metadata} into an immutable map ({@code null} becomes
     * empty).
     */
    public FlagEvaluation {
        metadata = metadata == null || metadata.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * Creates an evaluation without metadata.
     *
     * @param key          the flag key
     * @param value        the resolved value, or the default on error
     * @param variant      the variant, or {@code null}
     * @param reason       the reason
     * @param errorCode    the error code, or {@code null}
     * @param errorMessage the error detail, or {@code null}
     */
    public FlagEvaluation(String key, T value, String variant, String reason, ErrorCode errorCode,
            String errorMessage) {
        this(key, value, variant, reason, errorCode, errorMessage, Map.of());
    }

    /** Reason reported for every failed evaluation. */
    public static final String ERROR_REASON = "ERROR";

    /**
     * Creates a failed evaluation that carries the default value.
     *
     * @param key          the flag key
     * @param defaultValue the caller's default value
     * @param errorCode    why the evaluation failed
     * @param errorMessage human-readable detail
     * @param <T>          the value type
     * @return the failed evaluation
     */
    public static <T> FlagEvaluation<T> error(String key, T defaultValue, ErrorCode errorCode, String errorMessage) {
        return new FlagEvaluation<>(key, defaultValue, null, ERROR_REASON, errorCode, errorMessage);
    }

    /**
     * @return {@code true} when the evaluation failed and {@code value} is the
     *         default
     */
    public boolean isError() {
        return errorCode != null;
    }
}
