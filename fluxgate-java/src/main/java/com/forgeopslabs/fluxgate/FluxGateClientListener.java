package com.forgeopslabs.fluxgate;

/**
 * Observes a {@link DefaultFluxGateClient}. The starter registers every
 * listener bean with the auto-configured client; metrics and the health
 * indicator are implemented this way.
 *
 * <p>
 * Callbacks run synchronously on the evaluating thread, so they must be cheap.
 * An exception thrown by a listener is logged and ignored; it never reaches the
 * caller of the evaluation.
 */
public interface FluxGateClientListener {

    /**
     * Called after each flag evaluation, successful or not. Evaluations
     * rejected before any lookup (blank flag key or {@code null} type) are not
     * reported.
     *
     * @param evaluation    the result returned to the caller
     * @param fromCache     {@code true} when the result was served from the
     *                      evaluation cache without calling the edge
     * @param durationNanos time spent in {@code evaluate}, in nanoseconds
     */
    default void onEvaluation(FlagEvaluation<?> evaluation, boolean fromCache, long durationNanos) {
    }

    /**
     * Called for each evaluation-cache lookup while the cache is enabled.
     *
     * @param flagKey the flag key
     * @param hit     {@code true} on a cache hit
     */
    default void onCacheLookup(String flagKey, boolean hit) {
    }

    /**
     * Called after each bulk evaluation, including the OpenFeature provider's
     * change polls.
     *
     * @param evaluation the result returned to the caller
     */
    default void onBulkEvaluation(BulkEvaluation evaluation) {
    }
}
