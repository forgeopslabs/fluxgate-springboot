package com.fluxgate.starter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * Typed client for the FluxGate edge server (OFREP).
 *
 * <p>
 * Evaluation methods never throw: on any failure they return the caller's
 * default value, and {@link #evaluate} reports {@code reason = "ERROR"} with an
 * {@link ErrorCode}.
 *
 * <p>
 * The edge requires a targeting key. The shortcuts without a context
 * ({@code isEnabled(key)}, {@code getString(key, default)}, ...) have none, so
 * {@link DefaultFluxGateClient} returns the default with
 * {@link ErrorCode#TARGETING_KEY_MISSING} without calling the edge. Pass an
 * {@link EvaluationContext}.
 *
 * <pre>{@code
 * EvaluationContext ctx = EvaluationContext.of("user-42");
 * boolean checkout = client.isEnabled("new-checkout", ctx);
 * FlagEvaluation<String> banner = client.evaluate("banner", "none", String.class, ctx);
 * }</pre>
 */
public interface FluxGateClient {

    /**
     * Evaluates a flag as {@code type}.
     *
     * @param key          the flag key
     * @param defaultValue returned on any error
     * @param type         the expected value type ({@code Boolean},
     *                     {@code String}, {@code Integer}, {@code Long},
     *                     {@code Double}, {@code Map}, {@code List},
     *                     {@code JsonNode} or {@code Object})
     * @param context      the evaluation context, may be {@code null}
     * @param <T>          the value type
     * @return the evaluation; never {@code null}
     */
    <T> FlagEvaluation<T> evaluate(String key, T defaultValue, Class<T> type, EvaluationContext context);

    /**
     * Runs {@link #evaluate} on an executor. The future always completes
     * normally; failures are reported in the {@link FlagEvaluation}, as for
     * {@link #evaluate}.
     *
     * <p>
     * This default uses {@link ForkJoinPool#commonPool()}.
     * {@link DefaultFluxGateClient} uses its configured executor: in Spring, an
     * {@link FluxGateAsyncExecutor} bean when present.
     *
     * @param key          the flag key
     * @param defaultValue returned on any error
     * @param type         the expected value type
     * @param context      the evaluation context, may be {@code null}
     * @param <T>          the value type
     * @return the pending evaluation; never {@code null}
     */
    default <T> CompletableFuture<FlagEvaluation<T>> evaluateAsync(String key, T defaultValue, Class<T> type,
            EvaluationContext context) {
        return AsyncEvaluations.supply(() -> evaluate(key, defaultValue, type, context), ForkJoinPool.commonPool(),
                key, defaultValue);
    }

    /**
     * Evaluates every flag for {@code context}. Repeated calls for the same
     * context send {@code If-None-Match}; on {@code 304} the previous flags are
     * returned with {@link BulkEvaluation#changed()} {@code false}.
     *
     * @param context the evaluation context, may be {@code null}
     * @return the bulk result; never {@code null}
     */
    BulkEvaluation bulkEvaluate(EvaluationContext context);

    /**
     * Probes the edge's {@code /health} endpoint.
     *
     * @return {@code true} when the edge is connected and serving
     */
    boolean isHealthy();

    /**
     * Drops cached evaluations so the next evaluation asks the edge again. The
     * OpenFeature provider calls this when the flag-set version changes. The
     * default does nothing, for clients without a cache.
     */
    default void invalidateCache() {
    }

    default boolean isEnabled(String key) {
        return getBoolean(key, false);
    }

    default boolean isEnabled(String key, EvaluationContext context) {
        return getBoolean(key, false, context);
    }

    default boolean getBoolean(String key, boolean defaultValue) {
        return getBoolean(key, defaultValue, null);
    }

    default boolean getBoolean(String key, boolean defaultValue, EvaluationContext context) {
        return evaluate(key, defaultValue, Boolean.class, context).value();
    }

    default String getString(String key, String defaultValue) {
        return getString(key, defaultValue, null);
    }

    default String getString(String key, String defaultValue, EvaluationContext context) {
        return evaluate(key, defaultValue, String.class, context).value();
    }

    default int getInteger(String key, int defaultValue) {
        return getInteger(key, defaultValue, null);
    }

    default int getInteger(String key, int defaultValue, EvaluationContext context) {
        return evaluate(key, defaultValue, Integer.class, context).value();
    }

    default double getDouble(String key, double defaultValue) {
        return getDouble(key, defaultValue, null);
    }

    default double getDouble(String key, double defaultValue, EvaluationContext context) {
        return evaluate(key, defaultValue, Double.class, context).value();
    }

    default Map<String, Object> getObject(String key, Map<String, Object> defaultValue) {
        return getObject(key, defaultValue, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    default Map<String, Object> getObject(String key, Map<String, Object> defaultValue, EvaluationContext context) {
        // JSON objects always have string keys.
        return (Map<String, Object>) evaluate(key, (Map) defaultValue, Map.class, context).value();
    }
}
