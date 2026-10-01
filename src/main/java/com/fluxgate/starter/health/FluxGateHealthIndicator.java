package com.fluxgate.starter.health;

import com.fluxgate.starter.ErrorCode;
import com.fluxgate.starter.FluxGateClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Actuator health for the FluxGate edge, built from cached state only:
 * <ul>
 * <li>{@code DOWN} when the last {@code /health} probe was not ready, or the
 * last edge outcome (evaluation or bulk poll) failed and is still current (see
 * below);</li>
 * <li>{@code UP} when there is at least one signal and none of the above
 * holds;</li>
 * <li>{@code UNKNOWN} before the first signal.</li>
 * </ul>
 *
 * <p>
 * A transient failure ({@code GENERAL}: timeouts, I/O errors, 5xx;
 * {@code PROVIDER_NOT_READY}: 503) is superseded by a healthy probe that
 * finished after it, so one blip does not keep the indicator {@code DOWN} while
 * cache hits hide later successful calls. An {@code UNAUTHORIZED} failure is a
 * configuration problem that {@code /health} cannot see; it stays {@code DOWN}
 * until a later call succeeds.
 *
 * <p>
 * A health request never waits on the edge. When the cached probe is older than
 * {@code probeInterval}, or older than a transient failure, the request starts
 * one background {@code /health} probe on {@code executor} and answers from the
 * current state; the next request sees the new result.
 */
public class FluxGateHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(FluxGateHealthIndicator.class);

    private final FluxGateClient client;
    private final EdgeHealthTracker tracker;
    private final Executor executor;
    private final Duration probeInterval;
    private final Clock clock;
    private final AtomicBoolean probing = new AtomicBoolean();

    /**
     * @param client        probes {@code /health}
     * @param tracker       holds the cached outcomes
     * @param executor      runs background probes
     * @param probeInterval minimum age of a probe before it is refreshed
     * @param clock         time source, the same as the tracker's
     */
    public FluxGateHealthIndicator(FluxGateClient client, EdgeHealthTracker tracker, Executor executor,
            Duration probeInterval, Clock clock) {
        this.client = client;
        this.tracker = tracker;
        this.executor = executor;
        this.probeInterval = probeInterval;
        this.clock = clock;
    }

    Duration probeInterval() {
        return probeInterval;
    }

    @Override
    public Health health() {
        EdgeHealthTracker.Probe probe = tracker.lastProbe();
        EdgeHealthTracker.Outcome outcome = tracker.lastOutcome();
        if (probe == null || !probe.at().plus(probeInterval).isAfter(clock.instant())
                || (isTransientFailure(outcome) && !probe.at().isAfter(outcome.at()))) {
            refreshProbe();
        }
        return describe(probe, outcome);
    }

    private void refreshProbe() {
        if (!probing.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::probe);
        } catch (RejectedExecutionException e) {
            probing.set(false);
            logger.debug("FluxGate /health probe rejected by executor", e);
        }
    }

    private void probe() {
        try {
            tracker.recordProbe(client.isHealthy());
        } catch (RuntimeException e) {
            logger.debug("FluxGate /health probe failed", e);
            tracker.recordProbe(false);
        } finally {
            probing.set(false);
        }
    }

    private static boolean isTransientFailure(EdgeHealthTracker.Outcome outcome) {
        return outcome != null && !outcome.success() && outcome.errorCode() != ErrorCode.UNAUTHORIZED;
    }

    /**
     * A failed outcome counts unless it is transient and a healthy probe
     * finished after it.
     */
    private static boolean isCurrentFailure(EdgeHealthTracker.Outcome outcome, EdgeHealthTracker.Probe probe) {
        if (outcome == null || outcome.success()) {
            return false;
        }
        boolean supersededByProbe = isTransientFailure(outcome) && probe != null && probe.healthy()
                && probe.at().isAfter(outcome.at());
        return !supersededByProbe;
    }

    private static Health describe(EdgeHealthTracker.Probe probe, EdgeHealthTracker.Outcome outcome) {
        Health.Builder builder;
        if (probe == null && outcome == null) {
            builder = Health.unknown();
        } else if ((probe != null && !probe.healthy()) || isCurrentFailure(outcome, probe)) {
            builder = Health.down();
        } else {
            builder = Health.up();
        }
        if (probe != null) {
            builder.withDetail("edgeHealth", probe.healthy() ? "UP" : "DOWN")
                    .withDetail("edgeHealthCheckedAt", probe.at().toString());
        }
        if (outcome != null) {
            builder.withDetail("lastCall", outcome.source())
                    .withDetail("lastCallStatus", outcome.success() ? "SUCCESS" : "FAILURE")
                    .withDetail("lastCallAt", outcome.at().toString());
            if (!outcome.success()) {
                builder.withDetail("errorCode", outcome.errorCode().name());
                if (outcome.errorMessage() != null) {
                    builder.withDetail("error", outcome.errorMessage());
                }
            }
        }
        return builder.build();
    }
}
