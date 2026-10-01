package com.fluxgate.starter;

import java.util.concurrent.Executor;

/**
 * Runs {@link FluxGateClient#evaluateAsync} tasks and the health indicator's
 * background {@code /health} probes. Declare one as a bean to replace the
 * default, {@link java.util.concurrent.ForkJoinPool#commonPool()}:
 *
 * <pre>{@code
 * @Bean
 * FluxGateAsyncExecutor fluxGateAsyncExecutor() {
 *     return FluxGateAsyncExecutor.of(Executors.newFixedThreadPool(4));
 * }
 * }</pre>
 *
 * <p>
 * This deliberately does not extend {@link Executor}: a raw {@code Executor}
 * bean would make Spring Boot back off its {@code applicationTaskExecutor},
 * changing {@code @Async} and MVC async handling for the whole application.
 */
@FunctionalInterface
public interface FluxGateAsyncExecutor {

    /**
     * Runs {@code task} asynchronously.
     *
     * @param task the task
     * @throws java.util.concurrent.RejectedExecutionException if the task
     *                                                         cannot be accepted
     */
    void execute(Runnable task);

    /**
     * @param executor the executor to delegate to
     * @return a FluxGate executor backed by {@code executor}
     */
    static FluxGateAsyncExecutor of(Executor executor) {
        return executor::execute;
    }

    /**
     * @return this as a plain {@link Executor}
     */
    default Executor asExecutor() {
        return this::execute;
    }
}
