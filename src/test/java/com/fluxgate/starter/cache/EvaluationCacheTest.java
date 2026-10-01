package com.fluxgate.starter.cache;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fluxgate.starter.ErrorCode;
import com.fluxgate.starter.EvaluationContext;
import com.fluxgate.starter.FeatureToggleProperties;
import com.fluxgate.starter.ofrep.OfrepFlagResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationCacheTest {

    private static final EvaluationContext CTX = EvaluationContext.of("user-1");
    private static final OfrepFlagResult HIT =
            new OfrepFlagResult("f", BooleanNode.TRUE, "STATIC", null, null, null, null);

    private static FeatureToggleProperties.Cache settings(boolean enabled, Duration ttl) {
        FeatureToggleProperties.Cache cache = new FeatureToggleProperties.Cache();
        cache.setEnabled(enabled);
        cache.setTtl(ttl);
        return cache;
    }

    @Test
    void keysByFlagAndContext() {
        EvaluationCache cache = EvaluationCache.from(settings(true, Duration.ofMinutes(1)));

        cache.put("f", CTX, HIT);

        assertThat(cache.get("f", EvaluationContext.of("user-1"))).isSameAs(HIT);
        assertThat(cache.get("f", EvaluationContext.of("user-2"))).isNull();
        assertThat(cache.get("g", CTX)).isNull();
    }

    @Test
    void entriesExpireAfterTtl() throws InterruptedException {
        EvaluationCache cache = EvaluationCache.from(settings(true, Duration.ofMillis(1)));

        cache.put("f", CTX, HIT);
        Thread.sleep(20);

        assertThat(cache.get("f", CTX)).isNull();
    }

    @Test
    void invalidateAllDropsEveryEntry() {
        EvaluationCache cache = EvaluationCache.from(settings(true, Duration.ofMinutes(1)));
        cache.put("f", CTX, HIT);
        cache.put("g", CTX, HIT);

        cache.invalidateAll();

        assertThat(cache.get("f", CTX)).isNull();
        assertThat(cache.get("g", CTX)).isNull();
        EvaluationCache.from(settings(false, Duration.ofMinutes(1))).invalidateAll();
    }

    @Test
    void disabledCacheStoresNothing() {
        EvaluationCache cache = EvaluationCache.from(settings(false, Duration.ofMinutes(1)));

        cache.put("f", CTX, HIT);

        assertThat(cache.get("f", CTX)).isNull();
    }

    @Test
    void errorResultsAreNotStored() {
        EvaluationCache cache = EvaluationCache.from(settings(true, Duration.ofMinutes(1)));

        cache.put("f", CTX, new OfrepFlagResult("f", null, "ERROR", null, ErrorCode.FLAG_NOT_FOUND, "gone", null));

        assertThat(cache.get("f", CTX)).isNull();
    }
}
