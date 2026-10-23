package com.forgeopslabs.fluxgate.spring.metrics;

import com.forgeopslabs.fluxgate.FlagEvaluation;
import com.forgeopslabs.fluxgate.FluxGateClientListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * Records FluxGate client metrics with Micrometer:
 * <ul>
 * <li>{@value #EVALUATION_TIMER} timer, one sample per flag evaluation, tagged
 * {@code flag}, {@code reason}, {@code variant} ({@value #NONE} when the edge
 * sent none) and {@code error} (the {@link com.forgeopslabs.fluxgate.ErrorCode} name,
 * or {@value #NONE}).</li>
 * <li>{@value #CACHE_COUNTER} counter, tagged {@code result=hit|miss}, one
 * increment per evaluation-cache lookup while the cache is enabled.</li>
 * </ul>
 *
 * <p>
 * Tag values come from the flag set, never from the evaluation context, so
 * cardinality is bounded by flags x variants x reasons x error codes. The
 * targeting key is never a tag.
 */
public class FluxGateMetrics implements FluxGateClientListener {

    /** Timer name for flag evaluations. */
    public static final String EVALUATION_TIMER = "fluxgate.evaluation";

    /** Counter name for evaluation-cache lookups. */
    public static final String CACHE_COUNTER = "fluxgate.cache";

    /** Tag value used when a tag has no value. */
    public static final String NONE = "none";

    private final MeterRegistry registry;
    private final Counter cacheHits;
    private final Counter cacheMisses;

    /**
     * @param registry the registry to record into
     */
    public FluxGateMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.cacheHits = cacheCounter(registry, "hit");
        this.cacheMisses = cacheCounter(registry, "miss");
    }

    private static Counter cacheCounter(MeterRegistry registry, String result) {
        return Counter.builder(CACHE_COUNTER)
                .description("FluxGate evaluation cache lookups")
                .tag("result", result)
                .register(registry);
    }

    @Override
    public void onEvaluation(FlagEvaluation<?> evaluation, boolean fromCache, long durationNanos) {
        Timer.builder(EVALUATION_TIMER)
                .description("FluxGate flag evaluations")
                .tag("flag", orNone(evaluation.key()))
                .tag("reason", orNone(evaluation.reason()))
                .tag("variant", orNone(evaluation.variant()))
                .tag("error", evaluation.errorCode() == null ? NONE : evaluation.errorCode().name())
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void onCacheLookup(String flagKey, boolean hit) {
        (hit ? cacheHits : cacheMisses).increment();
    }

    private static String orNone(String value) {
        return value == null || value.isEmpty() ? NONE : value;
    }
}
