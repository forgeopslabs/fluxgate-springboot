package com.fluxgate.starter;

import com.fluxgate.starter.ofrep.OfrepTransport;
import com.fluxgate.starter.openfeature.FluxGateProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeatureToggleAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeatureToggleAutoConfiguration.class));

    @Test
    void configuresClientByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FeatureToggleProperties.class);
            assertThat(context).hasSingleBean(OfrepTransport.class);
            assertThat(context).hasSingleBean(FluxGateClient.class);
            assertThat(context.getBean(FluxGateClient.class)).isInstanceOf(DefaultFluxGateClient.class);
            assertThat(context).hasSingleBean(FeatureToggleHealthIndicator.class);
            assertThat(context).hasSingleBean(FluxGateProvider.class);
        });
    }

    @Test
    void bindsFluxgateProperties() {
        contextRunner
                .withPropertyValues("fluxgate.base-url=https://edge.example.com", "fluxgate.sdk-key=c.k",
                        "fluxgate.retry.max-attempts=1", "fluxgate.cache.enabled=false")
                .run(context -> {
                    FeatureToggleProperties properties = context.getBean(FeatureToggleProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo("https://edge.example.com");
                    assertThat(properties.getSdkKey()).isEqualTo("c.k");
                    assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(1);
                    assertThat(properties.getCache().isEnabled()).isFalse();
                });
    }

    @Test
    void backsOffWhenDisabled() {
        contextRunner
                .withPropertyValues("fluxgate.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FluxGateClient.class);
                    assertThat(context).doesNotHaveBean(OfrepTransport.class);
                    assertThat(context).doesNotHaveBean(FeatureToggleProperties.class);
                });
    }

    @Test
    void healthIndicatorCanBeDisabled() {
        contextRunner
                .withPropertyValues("fluxgate.health-check-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(FluxGateClient.class);
                    assertThat(context).doesNotHaveBean(FeatureToggleHealthIndicator.class);
                });
    }

    @Test
    void userClientWins() {
        FluxGateClient custom = mock(FluxGateClient.class);

        contextRunner
                .withBean(FluxGateClient.class, () -> custom)
                .run(context -> {
                    assertThat(context.getBean(FluxGateClient.class)).isSameAs(custom);
                    assertThat(context.getBean(FeatureToggleHealthIndicator.class)).isNotNull();
                });
    }
}
