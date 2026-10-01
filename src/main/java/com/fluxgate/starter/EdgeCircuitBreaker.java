package com.fluxgate.starter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Consecutive-failure circuit breaker for edge calls, shared by single-flag and
 * bulk evaluations.
 *
 * <ul>
 * <li><b>Closed:</b> calls go through. After {@code failureThreshold}
 * consecutive transport failures (I/O errors, timeouts, HTTP 5xx) it opens.</li>
 * <li><b>Open:</b> {@link #tryAcquire()} refuses calls for
 * {@code openDuration}.</li>
 * <li><b>Half-open:</b> after the cool-down exactly one trial call is allowed.
 * Success closes the breaker; failure re-opens it for another
 * {@code openDuration}.</li>
 * </ul>
 *
 * <p>
 * Any edge answer below 500 (200, 4xx such as 401, 403, 404, 400) proves the edge
 * is reachable and counts as a success. A threshold of {@code 0} disables the
 * breaker. Methods are synchronized; they run only around edge calls, never on
 * cache hits.
 */
public final class EdgeCircuitBreaker {

    private enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;

    /**
     * @param failureThreshold consecutive failures that open the breaker;
     *                         {@code 0} disables it
     * @param openDuration     how long the breaker stays open before a trial
     * @param clock            time source
     */
    public EdgeCircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        if (failureThreshold < 0) {
            throw new IllegalArgumentException("failureThreshold must be >= 0: " + failureThreshold);
        }
        if (failureThreshold > 0 && (openDuration == null || openDuration.isNegative() || openDuration.isZero())) {
            throw new IllegalArgumentException("openDuration must be positive: " + openDuration);
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @return a breaker that never opens
     */
    public static EdgeCircuitBreaker disabled() {
        return new EdgeCircuitBreaker(0, Duration.ZERO, Clock.systemUTC());
    }

    /**
     * @param properties breaker settings
     * @return a breaker honouring {@code failure-threshold} and
     *         {@code open-duration}
     */
    public static EdgeCircuitBreaker from(FeatureToggleProperties.CircuitBreaker properties) {
        return new EdgeCircuitBreaker(properties.getFailureThreshold(), properties.getOpenDuration(),
                Clock.systemUTC());
    }

    /**
     * Asks to make an edge call. Every {@code true} must be followed by
     * {@link #onSuccess()}, {@link #onFailure()} or {@link #release()}.
     *
     * @return {@code false} while the breaker is open, or while another call is
     *         the half-open trial
     */
    public synchronized boolean tryAcquire() {
        if (failureThreshold == 0 || state == State.CLOSED) {
            return true;
        }
        if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openDuration))) {
            state = State.HALF_OPEN;
            return true;
        }
        return false;
    }

    /**
     * The edge answered (any status below 500): closes the breaker.
     */
    public synchronized void onSuccess() {
        consecutiveFailures = 0;
        state = State.CLOSED;
    }

    /**
     * The call failed in transport or with a 5xx: counts towards opening, and
     * re-opens a half-open breaker.
     */
    public synchronized void onFailure() {
        if (failureThreshold == 0) {
            return;
        }
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = clock.instant();
        }
    }

    /**
     * The acquired call never reached the edge (for example the context could
     * not be serialized): frees a half-open trial without changing the state.
     */
    public synchronized void release() {
        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            openedAt = clock.instant().minus(openDuration);
        }
    }

    /**
     * @return {@code true} while calls are refused or limited to a trial
     */
    public synchronized boolean isOpen() {
        return state != State.CLOSED;
    }

    /**
     * @return the configured cool-down
     */
    public Duration openDuration() {
        return openDuration;
    }
}
