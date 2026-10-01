package com.fluxgate.starter.metrics;

import com.fluxgate.starter.ErrorCode;
import com.fluxgate.starter.FlagEvaluation;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FluxGateMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final FluxGateMetrics metrics = new FluxGateMetrics(registry);

    @Test
    void timesSuccessfulEvaluationsByFlagReasonAndVariant() {
        metrics.onEvaluation(new FlagEvaluation<>("banner", "blue", "blue", "SPLIT", null, null), false, 2_000_000);
        metrics.onEvaluation(new FlagEvaluation<>("banner", "blue", "blue", "SPLIT", null, null), true, 1_000_000);

        Timer timer = registry.get(FluxGateMetrics.EVALUATION_TIMER)
                .tags("flag", "banner", "reason", "SPLIT", "variant", "blue", "error", "none").timer();
        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(3.0);
    }

    @Test
    void usesNoneForMissingVariantAndErrorCodeNameForFailures() {
        metrics.onEvaluation(new FlagEvaluation<>("checkout", true, null, "STATIC", null, null), false, 1);
        metrics.onEvaluation(FlagEvaluation.error("ghost", false, ErrorCode.FLAG_NOT_FOUND, "missing"), false, 1);
        metrics.onEvaluation(new FlagEvaluation<>(null, true, "", null, null, null), false, 1);

        assertThat(registry.get(FluxGateMetrics.EVALUATION_TIMER)
                .tags("flag", "checkout", "variant", "none", "error", "none").timer().count()).isOne();
        assertThat(registry.get(FluxGateMetrics.EVALUATION_TIMER)
                .tags("flag", "ghost", "reason", "ERROR", "variant", "none", "error", "FLAG_NOT_FOUND")
                .timer().count()).isOne();
        assertThat(registry.get(FluxGateMetrics.EVALUATION_TIMER)
                .tags("flag", "none", "reason", "none", "variant", "none").timer().count()).isOne();
    }

    @Test
    void countsCacheHitsAndMisses() {
        assertThat(registry.get(FluxGateMetrics.CACHE_COUNTER).tag("result", "hit").counter().count()).isZero();

        metrics.onCacheLookup("banner", false);
        metrics.onCacheLookup("banner", true);
        metrics.onCacheLookup("other", true);

        assertThat(registry.get(FluxGateMetrics.CACHE_COUNTER).tag("result", "hit").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get(FluxGateMetrics.CACHE_COUNTER).tag("result", "miss").counter().count())
                .isEqualTo(1.0);
    }
}
