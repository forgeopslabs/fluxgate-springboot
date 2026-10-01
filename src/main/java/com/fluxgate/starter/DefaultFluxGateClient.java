package com.fluxgate.starter;

import com.fluxgate.starter.cache.BulkSnapshotStore;
import com.fluxgate.starter.cache.EvaluationCache;
import com.fluxgate.starter.ofrep.OfrepBulkResult;
import com.fluxgate.starter.ofrep.OfrepException;
import com.fluxgate.starter.ofrep.OfrepFlagResult;
import com.fluxgate.starter.ofrep.OfrepObjectMapper;
import com.fluxgate.starter.ofrep.OfrepTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Default {@link FluxGateClient}: OFREP transport plus evaluation cache and
 * bulk {@code ETag} handling. Reports evaluations, cache lookups and bulk
 * results to its {@link FluxGateClientListener}s.
 */
public class DefaultFluxGateClient implements FluxGateClient {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFluxGateClient.class);

    private final OfrepTransport transport;
    private final EvaluationCache cache;
    private final BulkSnapshotStore snapshots;
    private final ClientListeners listeners;
    private final Executor asyncExecutor;
    private final FlagValueConverter converter = new FlagValueConverter(OfrepObjectMapper.create());

    /**
     * Creates a client without listeners that runs {@link #evaluateAsync} on
     * {@link ForkJoinPool#commonPool()}.
     *
     * @param transport  the edge transport
     * @param properties starter properties (cache settings)
     */
    public DefaultFluxGateClient(OfrepTransport transport, FeatureToggleProperties properties) {
        this(transport, properties, List.of(), ForkJoinPool.commonPool());
    }

    /**
     * @param transport     the edge transport
     * @param properties    starter properties (cache settings)
     * @param listeners     observers of evaluations and bulk results
     * @param asyncExecutor runs {@link #evaluateAsync}
     */
    public DefaultFluxGateClient(OfrepTransport transport, FeatureToggleProperties properties,
            List<FluxGateClientListener> listeners, Executor asyncExecutor) {
        this(transport, EvaluationCache.from(properties.getCache()),
                new BulkSnapshotStore(properties.getCache().getMaxSize()), listeners, asyncExecutor);
    }

    /**
     * @param transport the edge transport
     * @param cache     the evaluation cache
     * @param snapshots the bulk snapshot store
     */
    public DefaultFluxGateClient(OfrepTransport transport, EvaluationCache cache, BulkSnapshotStore snapshots) {
        this(transport, cache, snapshots, List.of(), ForkJoinPool.commonPool());
    }

    /**
     * @param transport     the edge transport
     * @param cache         the evaluation cache
     * @param snapshots     the bulk snapshot store
     * @param listeners     observers of evaluations and bulk results
     * @param asyncExecutor runs {@link #evaluateAsync}
     */
    public DefaultFluxGateClient(OfrepTransport transport, EvaluationCache cache, BulkSnapshotStore snapshots,
            List<FluxGateClientListener> listeners, Executor asyncExecutor) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.listeners = new ClientListeners(Objects.requireNonNull(listeners, "listeners"));
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
    }

    /**
     * Creates a client for non-Spring use.
     *
     * @param properties starter properties
     * @return a configured client
     */
    public static DefaultFluxGateClient create(FeatureToggleProperties properties) {
        return new DefaultFluxGateClient(OfrepTransport.create(properties), properties);
    }

    @Override
    public <T> FlagEvaluation<T> evaluate(String key, T defaultValue, Class<T> type, EvaluationContext context) {
        if (key == null || key.isBlank()) {
            return FlagEvaluation.error(key, defaultValue, ErrorCode.GENERAL, "Flag key must not be empty");
        }
        if (type == null) {
            return FlagEvaluation.error(key, defaultValue, ErrorCode.GENERAL, "Value type must not be null");
        }
        long start = System.nanoTime();
        EvaluationContext ctx = context == null ? EvaluationContext.empty() : context;
        boolean fromCache = false;
        FlagEvaluation<T> evaluation;
        try {
            Fetched fetched = fetch(key, ctx);
            fromCache = fetched.fromCache();
            evaluation = converter.toEvaluation(fetched.result(), defaultValue, type);
        } catch (OfrepException e) {
            logger.debug("Flag '{}' evaluation failed ({}): {}", key, e.getErrorCode(), e.getMessage());
            evaluation = FlagEvaluation.error(key, defaultValue, e.getErrorCode(), e.getMessage());
        } catch (RuntimeException e) {
            logger.debug("Flag '{}' evaluation failed", key, e);
            evaluation = FlagEvaluation.error(key, defaultValue, ErrorCode.GENERAL, describe(e));
        }
        if (!listeners.isEmpty()) {
            FlagEvaluation<T> result = evaluation;
            boolean cached = fromCache;
            long duration = System.nanoTime() - start;
            listeners.publish(listener -> listener.onEvaluation(result, cached, duration));
        }
        return evaluation;
    }

    @Override
    public <T> CompletableFuture<FlagEvaluation<T>> evaluateAsync(String key, T defaultValue, Class<T> type,
            EvaluationContext context) {
        return AsyncEvaluations.supply(() -> evaluate(key, defaultValue, type, context), asyncExecutor, key,
                defaultValue);
    }

    @Override
    public BulkEvaluation bulkEvaluate(EvaluationContext context) {
        BulkEvaluation evaluation = doBulkEvaluate(context);
        listeners.publish(listener -> listener.onBulkEvaluation(evaluation));
        return evaluation;
    }

    private BulkEvaluation doBulkEvaluate(EvaluationContext context) {
        EvaluationContext ctx = context == null ? EvaluationContext.empty() : context;
        BulkEvaluation previous = snapshots.get(ctx);
        try {
            OfrepBulkResult result = transport.bulkEvaluate(ctx, previous == null ? null : previous.version());
            if (result.notModified()) {
                if (previous == null) {
                    return BulkEvaluation.error(ErrorCode.GENERAL, "Edge answered 304 to an unconditional request");
                }
                return previous.unchanged();
            }
            boolean changed = previous == null || !Objects.equals(previous.version(), result.etag());
            BulkEvaluation evaluation = new BulkEvaluation(toEvaluations(result), result.etag(), changed, null, null);
            snapshots.put(ctx, evaluation);
            return evaluation;
        } catch (OfrepException e) {
            logger.debug("Bulk evaluation failed ({}): {}", e.getErrorCode(), e.getMessage());
            return BulkEvaluation.error(e.getErrorCode(), e.getMessage());
        } catch (RuntimeException e) {
            logger.debug("Bulk evaluation failed", e);
            return BulkEvaluation.error(ErrorCode.GENERAL, describe(e));
        }
    }

    @Override
    public boolean isHealthy() {
        return transport.health();
    }

    @Override
    public void invalidateCache() {
        cache.invalidateAll();
    }

    private record Fetched(OfrepFlagResult result, boolean fromCache) {
    }

    private Fetched fetch(String key, EvaluationContext ctx) {
        if (cache.isEnabled()) {
            OfrepFlagResult cached = cache.get(key, ctx);
            listeners.publish(listener -> listener.onCacheLookup(key, cached != null));
            if (cached != null) {
                return new Fetched(cached, true);
            }
        }
        OfrepFlagResult result = transport.evaluate(key, ctx);
        cache.put(key, ctx, result);
        return new Fetched(result, false);
    }

    private static String describe(RuntimeException e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getName();
    }

    private Map<String, FlagEvaluation<Object>> toEvaluations(OfrepBulkResult result) {
        Map<String, FlagEvaluation<Object>> flags = new LinkedHashMap<>();
        for (OfrepFlagResult flag : result.flags()) {
            flags.put(flag.key(), converter.toEvaluation(flag, null, Object.class));
        }
        return flags;
    }
}
