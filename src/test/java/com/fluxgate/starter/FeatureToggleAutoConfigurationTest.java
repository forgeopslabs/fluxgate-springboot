package com.fluxgate.starter;

import com.fluxgate.starter.ofrep.OfrepTransport;
import com.fluxgate.starter.openfeature.FluxGateProvider;
import com.fluxgate.starter.openfeature.OpenFeatureRegistrar;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void providerIsNotRegisteredWithOpenFeatureByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(OpenFeatureRegistrar.class));
    }

    @Test
    void autoRegisterBindsTheProviderToTheConfiguredDomain() {
        FluxGateClient client = mock(FluxGateClient.class);
        when(client.isHealthy()).thenReturn(true);
        when(client.bulkEvaluate(any())).thenReturn(new BulkEvaluation(Map.of(), "v1", true, null, null));

        try {
            contextRunner
                    .withBean(FluxGateClient.class, () -> client)
                    .withPropertyValues("fluxgate.openfeature.auto-register=true",
                            "fluxgate.openfeature.provider-name=autoconfig-test", "fluxgate.polling.interval=0")
                    .run(context -> {
                        assertThat(context).hasSingleBean(OpenFeatureRegistrar.class);
                        FluxGateProvider provider = context.getBean(FluxGateProvider.class);
                        assertThat(OpenFeatureAPI.getInstance().getProvider("autoconfig-test")).isSameAs(provider);
                    });
        } finally {
            OpenFeatureAPI.getInstance().shutdown();
        }
    }

    @Test
    void providerIsShutDownWithTheContext() {
        contextRunner.run(context -> assertThat(context.getSourceApplicationContext(ConfigurableApplicationContext.class)
                .getBeanFactory().getBeanDefinition("fluxGateProvider").getDestroyMethodName())
                .isEqualTo("shutdown"));
    }
}
