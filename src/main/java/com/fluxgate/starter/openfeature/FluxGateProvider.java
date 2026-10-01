package com.fluxgate.starter.openfeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fluxgate.starter.BulkEvaluation;
import com.fluxgate.starter.ErrorCode;
import com.fluxgate.starter.FlagEvaluation;
import com.fluxgate.starter.FluxGateClient;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * OpenFeature provider backed by the FluxGate edge (OFREP).
 *
 * <pre>{@code
 * OpenFeatureAPI api = OpenFeatureAPI.getInstance();
 * api.setProviderAndWait(new FluxGateProvider(fluxGateClient));
 * boolean enabled = api.getClient().getBooleanValue("new-checkout", false, new ImmutableContext("user-42"));
 * }</pre>
 *
 * <ul>
 * <li>Values keep their JSON type: an integer flag requested as a string is a
 * {@code TYPE_MISMATCH}, never a coerced value. Object flags become
 * {@link Value}s with {@link dev.openfeature.sdk.Structure}s.</li>
 * <li>The edge's {@code reason} and {@code variant} pass through unchanged.</li>
 * <li>{@code initialize} probes {@code /health} and runs one bulk evaluation. On
 * failure it throws, so the SDK moves the provider to {@code ERROR} and emits
 * {@code PROVIDER_ERROR}; the poller keeps retrying and emits
 * {@code PROVIDER_READY} once the edge answers.</li>
 * <li>A poller revalidates the flag set every {@code pollInterval}
 * ({@code fluxgate.polling.interval}, {@code 0} disables it). When the edge's
 * version changes it clears the evaluation cache and emits
 * {@code PROVIDER_CONFIGURATION_CHANGED} with the changed flag keys. A failed
 * poll, or an evaluation rejected with 401/403, emits {@code PROVIDER_ERROR}.</li>
 * </ul>
 *
 * <p>
 * The edge requires a targeting key for bulk evaluation too. The poller uses the
 * targeting key of the context passed to {@code initialize} (the OpenFeature
 * global context), or {@value #PROBE_TARGETING_KEY} when it has none.
 */
public class FluxGateProvider extends EventProvider {

    /** Default {@code fluxgate.polling.interval}. */
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(30);

    /** Targeting key the poller uses when the initialization context has none. */
    public static final String PROBE_TARGETING_KEY = "fluxgate-openfeature-provider";

    private static final Logger logger = LoggerFactory.getLogger(FluxGateProvider.class);
    private static final String PROVIDER_NAME = "FluxGate";

    /** Connection status as this provider reported it to the SDK. */
    private enum Status {
        NOT_READY, READY, ERROR
    }

    private final FluxGateClient client;
    private final Duration pollInterval;
    private final Supplier<ScheduledExecutorService> schedulerFactory;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.NOT_READY);
    private final AtomicReference<FlagChangePoller> poller = new AtomicReference<>();

    /**
     * Creates a provider that polls every {@link #DEFAULT_POLL_INTERVAL}.
     *
     * @param client the FluxGate client to evaluate with
     */
    public FluxGateProvider(FluxGateClient client) {
        this(client, DEFAULT_POLL_INTERVAL);
    }

    /**
     * @param client       the FluxGate client to evaluate with
     * @param pollInterval change-detection interval; {@link Duration#ZERO}
     *                     disables polling
     */
    public FluxGateProvider(FluxGateClient client, Duration pollInterval) {
        this(client, pollInterval, FlagChangePoller::newScheduler);
    }

    FluxGateProvider(FluxGateClient client, Duration pollInterval,
            Supplier<ScheduledExecutorService> schedulerFactory) {
        if (client == null) {
            throw new IllegalArgumentException("FluxGateClient cannot be null");
        }
        if (pollInterval == null || pollInterval.isNegative()) {
            throw new IllegalArgumentException("Poll interval must be zero or positive: " + pollInterval);
        }
        this.client = client;
        this.pollInterval = pollInterval;
        this.schedulerFactory = schedulerFactory;
    }

    @Override
    public Metadata getMetadata() {
        return () -> PROVIDER_NAME;
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        com.fluxgate.starter.EvaluationContext probe = probeContext(evaluationContext);
        BulkEvaluation baseline = null;
        OpenFeatureError failure = null;
        if (!client.isHealthy()) {
            failure = new ProviderNotReadyError("FluxGate edge /health is not ready");
        } else {
            BulkEvaluation bulk = client.bulkEvaluate(probe);
            if (bulk.isError()) {
                failure = OpenFeatureErrors.initializationFailure(bulk.errorCode(), bulk.errorMessage());
            } else {
                baseline = bulk;
            }
        }
        status.set(failure == null ? Status.READY : Status.ERROR);
        startPolling(probe, baseline);
        if (failure != null) {
            logger.warn("FluxGate provider initialization failed: {}", failure.getMessage());
            throw failure;
        }
        logger.info("FluxGate provider ready");
    }

    @Override
    public void shutdown() {
        status.set(Status.NOT_READY);
        FlagChangePoller current = poller.getAndSet(null);
        if (current != null) {
            current.stop();
        }
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue,
            EvaluationContext ctx) {
        return toProviderEvaluation(client.evaluate(key, defaultValue, Boolean.class, ContextMapper.toFluxGate(ctx)));
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue,
            EvaluationContext ctx) {
        return toProviderEvaluation(client.evaluate(key, defaultValue, String.class, ContextMapper.toFluxGate(ctx)));
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue,
            EvaluationContext ctx) {
        return toProviderEvaluation(client.evaluate(key, defaultValue, Integer.class, ContextMapper.toFluxGate(ctx)));
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue,
            EvaluationContext ctx) {
        return toProviderEvaluation(client.evaluate(key, defaultValue, Double.class, ContextMapper.toFluxGate(ctx)));
    }

    /**
     * Any JSON value converts to a {@link Value}. The edge's "off" marker
     * ({@code false} with no variant, sent for a disabled or unassigned flag of
     * any type) and a missing value both yield {@code defaultValue} with the
     * edge's reason.
     */
    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue,
            EvaluationContext ctx) {
        FlagEvaluation<JsonNode> evaluation = client.evaluate(key, null, JsonNode.class,
                ContextMapper.toFluxGate(ctx));
        JsonNode json = evaluation.value();
        Value value = evaluation.isError() || json == null || isOffMarker(json, evaluation.variant())
                ? defaultValue
                : ValueMapper.toValue(json);
        return toProviderEvaluation(new FlagEvaluation<>(evaluation.key(), value, evaluation.variant(),
                evaluation.reason(), evaluation.errorCode(), evaluation.errorMessage()));
    }

    private static boolean isOffMarker(JsonNode json, String variant) {
        return variant == null && json.isBoolean() && !json.booleanValue();
    }

    private <T> ProviderEvaluation<T> toProviderEvaluation(FlagEvaluation<T> evaluation) {
        var builder = ProviderEvaluation.<T>builder()
                .value(evaluation.value())
                .variant(evaluation.variant())
                .reason(evaluation.reason());
        if (evaluation.isError()) {
            builder.errorCode(OpenFeatureErrors.toErrorCode(evaluation.errorCode()))
                    .errorMessage(evaluation.errorMessage());
            if (evaluation.errorCode() == ErrorCode.UNAUTHORIZED) {
                reportError(evaluation.errorCode(), evaluation.errorMessage());
            }
        }
        return builder.build();
    }

    private com.fluxgate.starter.EvaluationContext probeContext(EvaluationContext evaluationContext) {
        com.fluxgate.starter.EvaluationContext context = ContextMapper.toFluxGate(evaluationContext);
        String targetingKey = context.getTargetingKey();
        if (targetingKey != null && !targetingKey.isEmpty()) {
            return context;
        }
        return com.fluxgate.starter.EvaluationContext.builder()
                .targetingKey(PROBE_TARGETING_KEY)
                .attributes(context.getAttributes())
                .build();
    }

    private void startPolling(com.fluxgate.starter.EvaluationContext probe, BulkEvaluation baseline) {
        FlagChangePoller next = null;
        if (!pollInterval.isZero()) {
            next = new FlagChangePoller(client, probe, baseline, schedulerFactory.get(), new PollListener());
            next.start(pollInterval);
        }
        FlagChangePoller previous = poller.getAndSet(next);
        if (previous != null) {
            previous.stop();
        }
    }

    /**
     * Moves READY to ERROR and emits {@code PROVIDER_ERROR}; a no-op in any
     * other status, so repeated failures emit once.
     */
    private void reportError(ErrorCode errorCode, String message) {
        if (status.compareAndSet(Status.READY, Status.ERROR)) {
            logger.warn("FluxGate provider error ({}): {}", errorCode, message);
            emitProviderError(ProviderEventDetails.builder()
                    .errorCode(OpenFeatureErrors.toErrorCode(errorCode))
                    .message(message)
                    .build());
        }
    }

    private final class PollListener implements FlagChangePoller.Listener {

        @Override
        public void pollSucceeded(List<String> changedFlags) {
            if (status.compareAndSet(Status.ERROR, Status.READY)) {
                logger.info("FluxGate provider recovered");
                emitProviderReady(ProviderEventDetails.builder().message("FluxGate edge is reachable").build());
            }
            if (changedFlags.isEmpty() || status.get() == Status.NOT_READY) {
                return;
            }
            // Stale cached evaluations must go even if an evaluation 401 moved
            // the provider to ERROR meanwhile; only the event needs READY.
            client.invalidateCache();
            if (status.get() == Status.READY) {
                emitProviderConfigurationChanged(ProviderEventDetails.builder()
                        .flagsChanged(changedFlags)
                        .message("FluxGate flag set changed")
                        .build());
            }
        }

        @Override
        public void pollFailed(ErrorCode errorCode, String message) {
            reportError(errorCode, message);
        }
    }
}
