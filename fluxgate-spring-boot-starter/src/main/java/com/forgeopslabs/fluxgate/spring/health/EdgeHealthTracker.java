package com.forgeopslabs.fluxgate.spring.health;

import com.forgeopslabs.fluxgate.BulkEvaluation;
import com.forgeopslabs.fluxgate.ErrorCode;
import com.forgeopslabs.fluxgate.FlagEvaluation;
import com.forgeopslabs.fluxgate.FluxGateClientListener;

import java.time.Clock;
import java.time.Instant;

/**
 * Remembers the last edge outcome seen by the client and the last
 * {@code /health} probe result. Reading the state never contacts the edge.
 *
 * <p>
 * An outcome is a failure when the edge could not be used: {@code UNAUTHORIZED}
 * (401/403), {@code PROVIDER_NOT_READY} (503) or {@code GENERAL} (transport
 * errors, other 5xx, malformed responses, and calls skipped by the open circuit
 * breaker, reported as {@code PROVIDER_NOT_READY}). Answers about the request
 * itself ({@code FLAG_NOT_FOUND}, {@code TYPE_MISMATCH}) prove the edge is
 * serving and count as successes. Cache hits, {@code INVALID_CONTEXT} and
 * {@code TARGETING_KEY_MISSING} (both rejected locally, before any request) are
 * ignored.
 */
public class EdgeHealthTracker implements FluxGateClientListener {

    /**
     * The last edge call outcome.
     *
     * @param source       {@code "evaluation"} or {@code "bulk"}
     * @param success      whether the edge was usable
     * @param errorCode    the error code of a failure, else {@code null}
     * @param errorMessage the error detail of a failure, else {@code null}
     * @param at           when the outcome was recorded
     */
    public record Outcome(String source, boolean success, ErrorCode errorCode, String errorMessage, Instant at) {
    }

    /**
     * The last {@code /health} probe.
     *
     * @param healthy whether {@code /health} answered 200
     * @param at      when the probe finished
     */
    public record Probe(boolean healthy, Instant at) {
    }

    private final Clock clock;
    private volatile Outcome lastOutcome;
    private volatile Probe lastProbe;

    public EdgeHealthTracker() {
        this(Clock.systemUTC());
    }

    /**
     * @param clock time source for timestamps
     */
    public EdgeHealthTracker(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void onEvaluation(FlagEvaluation<?> evaluation, boolean fromCache, long durationNanos) {
        if (!fromCache) {
            record("evaluation", evaluation.errorCode(), evaluation.errorMessage());
        }
    }

    @Override
    public void onBulkEvaluation(BulkEvaluation evaluation) {
        record("bulk", evaluation.errorCode(), evaluation.errorMessage());
    }

    /**
     * @param healthy the result of a {@code /health} probe
     */
    public void recordProbe(boolean healthy) {
        lastProbe = new Probe(healthy, clock.instant());
    }

    /**
     * @return the last outcome, or {@code null} before the first edge call
     */
    public Outcome lastOutcome() {
        return lastOutcome;
    }

    /**
     * @return the last probe, or {@code null} before the first probe
     */
    public Probe lastProbe() {
        return lastProbe;
    }

    private void record(String source, ErrorCode errorCode, String errorMessage) {
        if (errorCode == ErrorCode.INVALID_CONTEXT || errorCode == ErrorCode.TARGETING_KEY_MISSING) {
            return;
        }
        boolean failure = isEdgeFailure(errorCode);
        lastOutcome = new Outcome(source, !failure, failure ? errorCode : null, failure ? errorMessage : null,
                clock.instant());
    }

    private static boolean isEdgeFailure(ErrorCode errorCode) {
        return errorCode == ErrorCode.UNAUTHORIZED
                || errorCode == ErrorCode.PROVIDER_NOT_READY
                || errorCode == ErrorCode.GENERAL;
    }
}
