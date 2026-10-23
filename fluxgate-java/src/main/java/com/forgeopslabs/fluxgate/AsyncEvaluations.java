package com.forgeopslabs.fluxgate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Runs evaluations on an executor without ever failing the returned future.
 */
final class AsyncEvaluations {

    private AsyncEvaluations() {
    }

    /**
     * @return a future of {@code evaluation}; when the executor rejects the
     *         task, an already completed future with a {@link ErrorCode#GENERAL}
     *         error carrying {@code defaultValue}
     */
    static <T> CompletableFuture<FlagEvaluation<T>> supply(Supplier<FlagEvaluation<T>> evaluation, Executor executor,
            String key, T defaultValue) {
        try {
            return CompletableFuture.supplyAsync(evaluation, executor);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(FlagEvaluation.error(key, defaultValue, ErrorCode.GENERAL,
                    "Async evaluation rejected by executor: " + e.getMessage()));
        }
    }
}
