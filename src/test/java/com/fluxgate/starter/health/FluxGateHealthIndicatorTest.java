package com.fluxgate.starter.health;

import com.fluxgate.starter.ErrorCode;
import com.fluxgate.starter.FlagEvaluation;
import com.fluxgate.starter.FluxGateClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FluxGateHealthIndicatorTest {

    private static final Duration INTERVAL = Duration.ofSeconds(30);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));
    private final FluxGateClient client = mock(FluxGateClient.class);
    private final EdgeHealthTracker tracker = new EdgeHealthTracker(clock);
    private final List<Runnable> queued = new ArrayList<>();
    private final FluxGateHealthIndicator indicator = new FluxGateHealthIndicator(client, tracker, queued::add,
            INTERVAL, clock);

    private void runQueuedProbes() {
        List<Runnable> tasks = new ArrayList<>(queued);
        queued.clear();
        tasks.forEach(Runnable::run);
    }

    @Test
    void unknownBeforeAnySignalAndNeverBlocksOnTheEdge() {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).isEmpty();
        verify(client, never()).isHealthy();
        assertThat(queued).hasSize(1);
    }

    @Test
    void backgroundProbeResultIsServedOnTheNextRequest() {
        when(client.isHealthy()).thenReturn(true);
        indicator.health();
        runQueuedProbes();

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("edgeHealth", "UP")
                .containsEntry("edgeHealthCheckedAt", "2026-10-01T12:00:00Z");
    }

    @Test
    void freshProbeIsNotRepeatedAndStaleProbeIsRefreshedOnce() {
        when(client.isHealthy()).thenReturn(true);
        indicator.health();
        runQueuedProbes();

        clock.advance(INTERVAL.minusSeconds(1));
        indicator.health();
        assertThat(queued).isEmpty();

        clock.advance(Duration.ofSeconds(1));
        indicator.health();
        indicator.health();
        assertThat(queued).hasSize(1);
        runQueuedProbes();
        verify(client, times(2)).isHealthy();
    }

    @Test
    void notReadyProbeIsDown() {
        when(client.isHealthy()).thenReturn(false);
        indicator.health();
        runQueuedProbes();

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void probeExceptionCountsAsNotReady() {
        when(client.isHealthy()).thenThrow(new IllegalStateException("boom"));
        indicator.health();
        runQueuedProbes();

        assertThat(indicator.health().getDetails()).containsEntry("edgeHealth", "DOWN");
    }

    @Test
    void failedLastCallIsDownEvenWhenTheProbeIsUp() {
        tracker.recordProbe(true);
        tracker.onEvaluation(FlagEvaluation.error("f", false, ErrorCode.UNAUTHORIZED, "Client credentials are "
                + "missing or invalid"), false, 1);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("lastCall", "evaluation")
                .containsEntry("lastCallStatus", "FAILURE")
                .containsEntry("errorCode", "UNAUTHORIZED")
                .containsEntry("error", "Client credentials are missing or invalid");
    }

    @Test
    void failureWithoutMessageOmitsTheErrorDetail() {
        tracker.onBulkEvaluation(com.fluxgate.starter.BulkEvaluation.error(ErrorCode.GENERAL, null));

        assertThat(indicator.health().getDetails()).containsEntry("errorCode", "GENERAL").doesNotContainKey("error");
    }

    @Test
    void successfulLastCallWithoutProbeIsUp() {
        tracker.onEvaluation(new FlagEvaluation<>("f", true, null, "STATIC", null, null), false, 1);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("lastCallStatus", "SUCCESS")
                .containsEntry("lastCallAt", "2026-10-01T12:00:00Z").doesNotContainKey("errorCode");
    }

    @Test
    void rejectedProbeIsRetriedOnTheNextRequest() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("full");
        };
        FluxGateHealthIndicator saturated = new FluxGateHealthIndicator(client, tracker, rejecting, INTERVAL, clock);

        assertThat(saturated.health().getStatus()).isEqualTo(Status.UNKNOWN);

        FluxGateHealthIndicator direct = new FluxGateHealthIndicator(client, tracker, Runnable::run, INTERVAL, clock);
        when(client.isHealthy()).thenReturn(true);
        direct.health();
        assertThat(direct.health().getStatus()).isEqualTo(Status.UP);
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
