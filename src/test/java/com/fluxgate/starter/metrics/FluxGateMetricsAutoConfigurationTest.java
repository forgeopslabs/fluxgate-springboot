package com.fluxgate.starter.metrics;

import com.fluxgate.starter.EvaluationContext;
import com.fluxgate.starter.FeatureToggleAutoConfiguration;
import com.fluxgate.starter.FluxGateClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FluxGateMetricsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeatureToggleAutoConfiguration.class,
                    FluxGateMetricsAutoConfiguration.class))
            .withPropertyValues("fluxgate.base-url=http://localhost:1", "fluxgate.sdk-key=client.key",
                    "fluxgate.retry.max-attempts=1");

    @Test
    void recordsClientEvaluationsInTheRegistry() {
        contextRunner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(FluxGateMetrics.class);
                    context.getBean(FluxGateClient.class).isEnabled("checkout", EvaluationContext.of("u"));

                    MeterRegistry registry = context.getBean(MeterRegistry.class);
                    assertThat(registry.get(FluxGateMetrics.EVALUATION_TIMER)
                            .tags("flag", "checkout", "error", "GENERAL").timer().count()).isOne();
                    assertThat(registry.get(FluxGateMetrics.CACHE_COUNTER).tag("result", "miss").counter()
                            .count()).isOne();
                });
    }

    @Test
    void backsOffWithoutARegistry() {
        contextRunner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(FluxGateMetrics.class));
    }

    @Test
    void canBeDisabled() {
        contextRunner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("fluxgate.metrics.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FluxGateMetrics.class));
    }

    @Test
    void backsOffWithoutMicrometer() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("io.micrometer.core"))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(FluxGateMetrics.class));
    }
}
