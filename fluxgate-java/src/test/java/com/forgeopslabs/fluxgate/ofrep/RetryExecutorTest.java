package com.forgeopslabs.fluxgate.ofrep;

import com.forgeopslabs.fluxgate.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryExecutorTest {

    private final List<Duration> pauses = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    private RetryExecutor executor(int maxAttempts) {
        return new RetryExecutor(maxAttempts, Duration.ofMillis(100), pauses::add);
    }

    private static OfrepException status(int status, boolean retryable) {
        return new OfrepException(ErrorCode.GENERAL, "HTTP " + status, status, retryable);
    }

    @Test
    void backsOffExponentiallyUntilSuccess() {
        String result = executor(4).execute(() -> {
            if (calls.incrementAndGet() < 4) {
                throw new UncheckedIOException(new IOException("connection refused"));
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(4);
        assertThat(pauses).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofMillis(400));
    }

    @Test
    void rethrowsLastFailureWhenAttemptsAreExhausted() {
        OfrepException failure = status(503, true);

        assertThatThrownBy(() -> executor(3).execute(() -> {
            calls.incrementAndGet();
            throw failure;
        })).isSameAs(failure);
        assertThat(calls).hasValue(3);
        assertThat(pauses).hasSize(2);
    }

    @Test
    void doesNotRetryNonRetryableFailures() {
        assertThatThrownBy(() -> executor(3).execute(() -> {
            calls.incrementAndGet();
            throw status(404, false);
        })).isInstanceOf(OfrepException.class);
        assertThatThrownBy(() -> executor(3).execute(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("bug");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(calls).hasValue(2);
        assertThat(pauses).isEmpty();
    }

    @Test
    void maxAttemptsBelowOneMeansSingleAttempt() {
        assertThatThrownBy(() -> executor(0).execute(() -> {
            calls.incrementAndGet();
            throw new UncheckedIOException(new IOException("down"));
        })).isInstanceOf(UncheckedIOException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void interruptStopsRetryingAndKeepsTheFlag() {
        RetryExecutor executor = new RetryExecutor(3, Duration.ofMillis(100), duration -> {
            throw new InterruptedException();
        });

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw new UncheckedIOException(new IOException("down"));
        })).isInstanceOf(UncheckedIOException.class)
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));

        assertThat(calls).hasValue(1);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void realSleeperWaitsBetweenAttempts() {
        RetryExecutor executor = new RetryExecutor(2, Duration.ofMillis(1));

        String result = executor.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw status(502, true);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
    }
}
