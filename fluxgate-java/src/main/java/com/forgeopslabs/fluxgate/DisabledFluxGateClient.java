package com.forgeopslabs.fluxgate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The {@link FluxGateClient} registered when {@code fluxgate.enabled=false}:
 * every evaluation returns the caller's default with reason
 * {@value #REASON} and no error, without contacting the edge.
 */
public final class DisabledFluxGateClient implements FluxGateClient {

    /** Reason reported for every evaluation. */
    public static final String REASON = "DISABLED";

    @Override
    public <T> FlagEvaluation<T> evaluate(String key, T defaultValue, Class<T> type, EvaluationContext context) {
        return new FlagEvaluation<>(key, defaultValue, null, REASON, null, null);
    }

    @Override
    public <T> CompletableFuture<FlagEvaluation<T>> evaluateAsync(String key, T defaultValue, Class<T> type,
            EvaluationContext context) {
        return CompletableFuture.completedFuture(evaluate(key, defaultValue, type, context));
    }

    /**
     * @return an empty, unversioned flag set
     */
    @Override
    public BulkEvaluation bulkEvaluate(EvaluationContext context) {
        return new BulkEvaluation(Map.of(), null, false, null, null);
    }

    /**
     * @return {@code false}: the edge is never contacted
     */
    @Override
    public boolean isHealthy() {
        return false;
    }
}
