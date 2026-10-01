package com.fluxgate.starter;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EdgeCircuitBreakerTest {

    private static final Duration OPEN = Duration.ofSeconds(30);

    private final TestClock clock = new TestClock();
    private final EdgeCircuitBreaker breaker = new EdgeCircuitBreaker(3, OPEN, clock);

    private void fail(int times) {
        for (int i = 0; i < times; i++) {
            assertThat(breaker.tryAcquire()).isTrue();
            breaker.onFailure();
        }
    }

    @Test
    void opensAfterThresholdConsecutiveFailures() {
        fail(2);
        assertThat(breaker.isOpen()).isFalse();

        fail(1);

        assertThat(breaker.isOpen()).isTrue();
        assertThat(breaker.tryAcquire()).isFalse();
    }

    @Test
    void successResetsTheConsecutiveCount() {
        fail(2);
        breaker.tryAcquire();
        breaker.onSuccess();
        fail(2);

        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    void allowsExactlyOneHalfOpenTrialAfterTheCoolDown() {
        fail(3);
        clock.advance(OPEN.minusMillis(1));
        assertThat(breaker.tryAcquire()).isFalse();

        clock.advance(Duration.ofMillis(1));

        assertThat(breaker.tryAcquire()).isTrue();
        assertThat(breaker.tryAcquire()).isFalse();
    }

    @Test
    void halfOpenSuccessCloses() {
        fail(3);
        clock.advance(OPEN);
        breaker.tryAcquire();

        breaker.onSuccess();

        assertThat(breaker.isOpen()).isFalse();
        assertThat(breaker.tryAcquire()).isTrue();
        assertThat(breaker.tryAcquire()).isTrue();
    }

    @Test
    void halfOpenFailureReopensForAnotherCoolDown() {
        fail(3);
        clock.advance(OPEN);
        breaker.tryAcquire();

        breaker.onFailure();

        assertThat(breaker.tryAcquire()).isFalse();
        clock.advance(OPEN);
        assertThat(breaker.tryAcquire()).isTrue();
    }

    @Test
    void releaseFreesTheTrialWithoutClosing() {
        fail(3);
        clock.advance(OPEN);
        breaker.tryAcquire();

        breaker.release();

        assertThat(breaker.isOpen()).isTrue();
        assertThat(breaker.tryAcquire()).isTrue();
        breaker.release();
        breaker.onSuccess();
        breaker.release();
        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    void thresholdZeroDisables() {
        EdgeCircuitBreaker off = new EdgeCircuitBreaker(0, Duration.ZERO, clock);
        for (int i = 0; i < 100; i++) {
            off.onFailure();
        }

        assertThat(off.tryAcquire()).isTrue();
        assertThat(off.isOpen()).isFalse();
        assertThat(EdgeCircuitBreaker.disabled().tryAcquire()).isTrue();
    }

    @Test
    void fromPropertiesAndValidation() {
        FeatureToggleProperties.CircuitBreaker properties = new FeatureToggleProperties().getCircuitBreaker();
        EdgeCircuitBreaker fromProperties = EdgeCircuitBreaker.from(properties);
        assertThat(fromProperties.openDuration()).isEqualTo(Duration.ofSeconds(30));

        assertThatThrownBy(() -> new EdgeCircuitBreaker(-1, OPEN, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EdgeCircuitBreaker(1, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EdgeCircuitBreaker(1, null, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EdgeCircuitBreaker(1, OPEN.negated(), clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static final class TestClock extends Clock {

        private Instant now = Instant.parse("2026-10-01T12:00:00Z");

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
