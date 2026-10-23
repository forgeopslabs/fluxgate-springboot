package com.forgeopslabs.fluxgate.ofrep;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Retries a call with exponential backoff on I/O errors and on retryable
 * {@link OfrepException}s (HTTP 502, 503, 504). Everything else, including
 * 4xx responses, fails immediately.
 */
public final class RetryExecutor {

    /** Pauses between attempts; replaceable in tests. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Sleeper sleeper;

    /**
     * @param maxAttempts    total attempts including the first (values below 1
     *                       mean 1)
     * @param initialBackoff pause before the second attempt; doubles each retry
     */
    public RetryExecutor(int maxAttempts, Duration initialBackoff) {
        this(maxAttempts, initialBackoff, duration -> Thread.sleep(duration.toMillis()));
    }

    RetryExecutor(int maxAttempts, Duration initialBackoff, Sleeper sleeper) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoff = initialBackoff == null || initialBackoff.isNegative() ? Duration.ZERO : initialBackoff;
        this.sleeper = sleeper;
    }

    /**
     * Runs {@code call}, retrying retryable failures.
     *
     * @param call the call to run
     * @param <T>  the result type
     * @return the call's result
     * @throws RuntimeException the last failure when attempts are exhausted or
     *                          the failure is not retryable
     */
    public <T> T execute(Supplier<T> call) {
        Duration backoff = initialBackoff;
        for (int attempt = 1;; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                if (attempt >= maxAttempts || !isRetryable(e)) {
                    throw e;
                }
                pause(backoff, e);
                backoff = backoff.multipliedBy(2);
            }
        }
    }

    private void pause(Duration backoff, RuntimeException failure) {
        try {
            sleeper.sleep(backoff);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failure.addSuppressed(interrupted);
            throw failure;
        }
    }

    private static boolean isRetryable(RuntimeException e) {
        if (e instanceof OfrepException ofrep) {
            return ofrep.isRetryable();
        }
        return e instanceof UncheckedIOException;
    }
}
