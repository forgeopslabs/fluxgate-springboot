package com.forgeopslabs.fluxgate.cache;

import com.forgeopslabs.fluxgate.EvaluationContext;
import com.forgeopslabs.fluxgate.FluxGateOptions;
import com.forgeopslabs.fluxgate.ofrep.OfrepFlagResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caffeine cache of successful edge evaluations, keyed by flag key and
 * evaluation context. A disabled cache stores nothing.
 */
public final class EvaluationCache {

    private record Key(String flagKey, EvaluationContext context) {
    }

    private static final EvaluationCache DISABLED = new EvaluationCache(null);

    private final Cache<Key, OfrepFlagResult> cache;

    private EvaluationCache(Cache<Key, OfrepFlagResult> cache) {
        this.cache = cache;
    }

    /**
     * @param options client settings
     * @return a cache honouring the cache enabled flag, TTL and maximum size
     */
    public static EvaluationCache from(FluxGateOptions options) {
        if (!options.isCacheEnabled()) {
            return DISABLED;
        }
        return new EvaluationCache(Caffeine.newBuilder()
                .expireAfterWrite(options.getCacheTtl())
                .maximumSize(options.getCacheMaxSize())
                .build());
    }

    /**
     * @return {@code true} unless {@code fluxgate.cache.enabled=false}
     */
    public boolean isEnabled() {
        return cache != null;
    }

    /**
     * @return the cached result, or {@code null}
     */
    public OfrepFlagResult get(String flagKey, EvaluationContext context) {
        return cache == null ? null : cache.getIfPresent(new Key(flagKey, context));
    }

    /**
     * Caches {@code result} when it is a success; error results are never
     * cached.
     */
    public void put(String flagKey, EvaluationContext context, OfrepFlagResult result) {
        if (cache != null && result.isSuccess()) {
            cache.put(new Key(flagKey, context), result);
        }
    }

    /**
     * Drops every cached evaluation, for example after the edge reports a new
     * flag-set version.
     */
    public void invalidateAll() {
        if (cache != null) {
            cache.invalidateAll();
        }
    }
}
