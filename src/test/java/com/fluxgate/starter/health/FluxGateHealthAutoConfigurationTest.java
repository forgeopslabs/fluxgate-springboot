package com.fluxgate.starter.health;

import com.fluxgate.starter.EvaluationContext;
import com.fluxgate.starter.FeatureToggleAutoConfiguration;
import com.fluxgate.starter.FluxGateClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FluxGateHealthAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeatureToggleAutoConfiguration.class,
                    FluxGateHealthAutoConfiguration.class))
            .withPropertyValues("fluxgate.base-url=http://localhost:1", "fluxgate.sdk-key=client.key",
                    "fluxgate.retry.max-attempts=1");

    @Test
    void registersIndicatorNamedFluxgateFedByTheClient() {
        AtomicInteger probes = new AtomicInteger();
        contextRunner
                .withBean(FeatureToggleAutoConfiguration.ASYNC_EXECUTOR_BEAN_NAME, Executor.class,
                        () -> task -> probes.incrementAndGet())
                .run(context -> {
                    assertThat(context).hasBean("fluxgateHealthIndicator").hasSingleBean(EdgeHealthTracker.class);
                    FluxGateHealthIndicator indicator = context.getBean(FluxGateHealthIndicator.class);

                    context.getBean(FluxGateClient.class).isEnabled("f", EvaluationContext.of("u"));

                    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
                    assertThat(indicator.health().getDetails()).containsEntry("errorCode", "GENERAL");
                    assertThat(probes).hasValue(1);
                });
    }

    @Test
    void probeIntervalFollowsPollingAndDefaultsWhenPollingIsOff() {
        contextRunner.withPropertyValues("fluxgate.polling.interval=10s")
                .run(context -> assertThat(context.getBean(FluxGateHealthIndicator.class).probeInterval())
                        .hasSeconds(10));
        contextRunner.withPropertyValues("fluxgate.polling.interval=0")
                .run(context -> assertThat(context.getBean(FluxGateHealthIndicator.class).probeInterval())
                        .hasSeconds(30));
    }

    @Test
    void canBeDisabled() {
        contextRunner.withPropertyValues("fluxgate.health.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FluxGateHealthIndicator.class)
                        .doesNotHaveBean(EdgeHealthTracker.class));
    }

    @Test
    void backsOffWithoutActuator() {
        contextRunner.withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate"))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EdgeHealthTracker.class));
    }

    @Test
    void worksWithAUserClient() {
        FluxGateClient custom = mock(FluxGateClient.class);
        contextRunner.withBean(FluxGateClient.class, () -> custom)
                .run(context -> assertThat(context).hasSingleBean(FluxGateHealthIndicator.class));
    }
}
