package com.forgeopslabs.fluxgate.openfeature;

import com.forgeopslabs.fluxgate.BulkEvaluation;
import com.forgeopslabs.fluxgate.ErrorCode;
import com.forgeopslabs.fluxgate.EvaluationContext;
import com.forgeopslabs.fluxgate.FlagEvaluation;
import com.forgeopslabs.fluxgate.FluxGateClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Revalidates the flag set on a fixed delay with a conditional bulk evaluation
 * ({@code If-None-Match}, handled by the client) and reports what changed.
 *
 * <p>
 * Polls run on the given scheduler, one at a time ({@code scheduleWithFixedDelay}
 * never overlaps runs), so the last successful result needs no locking beyond
 * visibility. {@link #stop()} shuts the scheduler down without waiting for a
 * running poll.
 */
final class FlagChangePoller {

    /**
     * Receives poll outcomes on the poller thread.
     */
    interface Listener {

        /**
         * @param changedFlags flag keys whose evaluation changed since the last
         *                     successful poll; empty when the flag-set version
         *                     is unchanged or there was no earlier result
         */
        void pollSucceeded(List<String> changedFlags);

        /**
         * @param errorCode why the bulk evaluation failed
         * @param message   human-readable detail
         */
        void pollFailed(ErrorCode errorCode, String message);
    }

    private static final Logger logger = LoggerFactory.getLogger(FlagChangePoller.class);

    private final FluxGateClient client;
    private final EvaluationContext context;
    private final ScheduledExecutorService scheduler;
    private final Listener listener;
    private volatile BulkEvaluation last;

    /**
     * @param client    the client used for bulk evaluations
     * @param context   the context every poll evaluates
     * @param baseline  the last known good result, or {@code null}
     * @param scheduler runs the polls; owned by the poller from now on
     * @param listener  receives the outcomes
     */
    FlagChangePoller(FluxGateClient client, EvaluationContext context, BulkEvaluation baseline,
            ScheduledExecutorService scheduler, Listener listener) {
        this.client = client;
        this.context = context;
        this.last = baseline;
        this.scheduler = scheduler;
        this.listener = listener;
    }

    /**
     * @return a single daemon thread scheduler for one poller
     */
    static ScheduledExecutorService newScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fluxgate-flag-poller");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Schedules polls every {@code interval}, the first one after
     * {@code interval}.
     */
    void start(Duration interval) {
        long millis = interval.toMillis();
        scheduler.scheduleWithFixedDelay(this::pollSafely, millis, millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops polling. A poll already running is interrupted, not awaited.
     */
    void stop() {
        scheduler.shutdownNow();
    }

    /**
     * Runs one poll on the calling thread.
     */
    void poll() {
        BulkEvaluation current = client.bulkEvaluate(context);
        if (current.isError()) {
            listener.pollFailed(current.errorCode(), current.errorMessage());
            return;
        }
        BulkEvaluation previous = last;
        last = current;
        if (previous == null || Objects.equals(previous.version(), current.version())) {
            listener.pollSucceeded(List.of());
        } else {
            listener.pollSucceeded(changedFlags(previous.flags(), current.flags()));
        }
    }

    private void pollSafely() {
        try {
            poll();
        } catch (RuntimeException e) {
            // An exception escaping a scheduled task would cancel all later polls.
            logger.warn("FluxGate flag poll failed", e);
        }
    }

    /**
     * Keys added, removed or evaluated differently. A new version can change
     * rules that do not affect the polled context; then every known key is
     * reported, so listeners never miss a change.
     */
    static List<String> changedFlags(Map<String, FlagEvaluation<Object>> previous,
            Map<String, FlagEvaluation<Object>> current) {
        Set<String> keys = new LinkedHashSet<>(current.keySet());
        keys.addAll(previous.keySet());
        List<String> changed = new ArrayList<>();
        for (String key : keys) {
            if (!Objects.equals(previous.get(key), current.get(key))) {
                changed.add(key);
            }
        }
        return changed.isEmpty() ? List.copyOf(keys) : changed;
    }
}
