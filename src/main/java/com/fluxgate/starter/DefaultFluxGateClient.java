package com.fluxgate.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxgate.starter.cache.BulkSnapshotStore;
import com.fluxgate.starter.cache.EvaluationCache;
import com.fluxgate.starter.ofrep.OfrepBulkResult;
import com.fluxgate.starter.ofrep.OfrepException;
import com.fluxgate.starter.ofrep.OfrepFlagResult;
import com.fluxgate.starter.ofrep.OfrepTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link FluxGateClient}: OFREP transport plus evaluation cache and
 * bulk {@code ETag} handling.
 */
public class DefaultFluxGateClient implements FluxGateClient {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFluxGateClient.class);

    private final OfrepTransport transport;
    private final EvaluationCache cache;
    private final BulkSnapshotStore snapshots;
    private final FlagValueConverter converter = new FlagValueConverter(new ObjectMapper());

    /**
     * @param transport  the edge transport
     * @param properties starter properties (cache settings)
     */
    public DefaultFluxGateClient(OfrepTransport transport, FeatureToggleProperties properties) {
        this(transport, EvaluationCache.from(properties.getCache()),
                new BulkSnapshotStore(properties.getCache().getMaxSize()));
    }

    /**
     * @param transport the edge transport
     * @param cache     the evaluation cache
     * @param snapshots the bulk snapshot store
     */
    public DefaultFluxGateClient(OfrepTransport transport, EvaluationCache cache, BulkSnapshotStore snapshots) {
        this.transport = transport;
        this.cache = cache;
        this.snapshots = snapshots;
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
        EvaluationContext ctx = context == null ? EvaluationContext.empty() : context;
        try {
            return converter.toEvaluation(fetch(key, ctx), defaultValue, type);
        } catch (OfrepException e) {
            logger.debug("Flag '{}' evaluation failed ({}): {}", key, e.getErrorCode(), e.getMessage());
            return FlagEvaluation.error(key, defaultValue, e.getErrorCode(), e.getMessage());
        } catch (RuntimeException e) {
            logger.debug("Flag '{}' evaluation failed", key, e);
            return FlagEvaluation.error(key, defaultValue, ErrorCode.GENERAL, describe(e));
        }
    }

    @Override
    public BulkEvaluation bulkEvaluate(EvaluationContext context) {
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

    private OfrepFlagResult fetch(String key, EvaluationContext ctx) {
        OfrepFlagResult cached = cache.get(key, ctx);
        if (cached != null) {
            return cached;
        }
        OfrepFlagResult result = transport.evaluate(key, ctx);
        cache.put(key, ctx, result);
        return result;
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
