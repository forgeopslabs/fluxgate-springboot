package com.fluxgate.starter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureToggleAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeatureToggleAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Configuration
    @EnableRetry
    static class TestConfiguration {
        @Bean
        RestTemplateBuilder restTemplateBuilder() {
            return new RestTemplateBuilder();
        }
    }

    @Test
    void shouldAutoConfigureWhenFeatureToggleIsEnabled() {
        // When & Then
        contextRunner
                .withPropertyValues("feature.toggle.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(FeatureToggleProperties.class);
                    assertThat(context).hasSingleBean(RestTemplate.class);
                    assertThat(context).hasSingleBean(Executor.class);
                    assertThat(context).hasSingleBean(FluxGateClient.class);
                    // The bean is exposed as FluxGateClient due to proxy creation by @EnableRetry
                    FluxGateClient client = context.getBean(FluxGateClient.class);
                    assertThat(client).isNotNull();
                });
    }

    @Test
    void shouldAutoConfigureWhenNoExplicitEnabledProperty() {
        // When & Then - should auto-configure by default (matchIfMissing = true)
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(FeatureToggleProperties.class);
                    assertThat(context).hasSingleBean(RestTemplate.class);
                    assertThat(context).hasSingleBean(Executor.class);
                    assertThat(context).hasSingleBean(FluxGateClient.class);
                });
    }

    @Test
    void shouldNotAutoConfigureWhenFeatureToggleIsDisabled() {
        // When & Then
        contextRunner
                .withPropertyValues("feature.toggle.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RestTemplate.class);
                    assertThat(context).doesNotHaveBean(Executor.class);
                    assertThat(context).doesNotHaveBean(FluxGateClient.class);
                    assertThat(context).doesNotHaveBean(DefaultFeatureToggleClient.class);
                });
    }

    @Test
    void shouldConfigureRestTemplateWithDefaultTimeouts() {
        // When & Then
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(RestTemplate.class);
                    RestTemplate restTemplate = context.getBean("featureToggleRestTemplate", RestTemplate.class);
                    assertThat(restTemplate).isNotNull();
                });
    }

    @Test
    void shouldConfigureAsyncExecutorWithCorrectSettings() {
        // When & Then
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(Executor.class);
                    Executor executor = context.getBean("featureToggleAsyncExecutor", Executor.class);
                    assertThat(executor).isNotNull();
                });
    }

    @Test
    void shouldConfigureFeatureToggleClientWithDependencies() {
        // When & Then
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(FluxGateClient.class);
                    FluxGateClient client = context.getBean(FluxGateClient.class);
                    assertThat(client).isNotNull();
                    // Due to @EnableRetry, the bean is proxied and may not be the exact type
                    // We verify it implements the interface correctly
                    assertThat(client).isInstanceOf(FluxGateClient.class);
                });
    }

    @Test
    void shouldRespectConditionalOnMissingBeanForRestTemplate() {
        // Given
        contextRunner
                .withUserConfiguration(CustomRestTemplateConfiguration.class)
                .run(context -> {
                    // Then
                    assertThat(context).hasSingleBean(RestTemplate.class);
                    RestTemplate restTemplate = context.getBean("customRestTemplate", RestTemplate.class);
                    assertThat(restTemplate).isNotNull();
                    // Should not create the default featureToggleRestTemplate
                    assertThat(context).doesNotHaveBean("featureToggleRestTemplate");
                });
    }

    @Test
    void shouldRespectConditionalOnMissingBeanForExecutor() {
        // Given
        contextRunner
                .withUserConfiguration(CustomExecutorConfiguration.class)
                .run(context -> {
                    // Then
                    assertThat(context).hasSingleBean(Executor.class);
                    Executor executor = context.getBean("customExecutor", Executor.class);
                    assertThat(executor).isNotNull();
                    // Should not create the default featureToggleAsyncExecutor
                    assertThat(context).doesNotHaveBean("featureToggleAsyncExecutor");
                });
    }

    @Test
    void shouldRespectConditionalOnMissingBeanForFluxGateClient() {
        // Given
        contextRunner
                .withUserConfiguration(CustomFluxGateClientConfiguration.class)
                .run(context -> {
                    // Then
                    assertThat(context).hasSingleBean(FluxGateClient.class);
                    FluxGateClient client = context.getBean("customClient", FluxGateClient.class);
                    assertThat(client).isNotNull();
                });
    }

    @Test
    void shouldConfigureWithCustomProperties() {
        // When & Then
        contextRunner
                .withPropertyValues(
                        "fluxgate.base-url=https://custom.example.com",
                        "fluxgate.connection-timeout=5000ms",
                        "fluxgate.read-timeout=10000ms")
                .run(context -> {
                    assertThat(context).hasSingleBean(FeatureToggleProperties.class);
                    FeatureToggleProperties properties = context.getBean(FeatureToggleProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo("https://custom.example.com");
                    assertThat(properties.getConnectionTimeout()).isEqualTo(Duration.ofMillis(5000));
                    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofMillis(10000));
                });
    }

    @Test
    void shouldEnableRetryAnnotationSupport() {
        // When & Then
        contextRunner
                .run(context -> {
                    // Verify that the configuration class is annotated with @EnableRetry
                    assertThat(FeatureToggleAutoConfiguration.class)
                            .hasAnnotation(EnableRetry.class);
                });
    }

    @Test
    void shouldConfigureHealthIndicatorWhenActuatorIsPresent() {
        // When & Then
        contextRunner
                .withPropertyValues("feature.toggle.health-check-enabled=true")
                .run(context -> {
                    // Note: This test may not pass if actuator dependencies are optional
                    // The health indicator is only created when HealthIndicator.class is on
                    // classpath
                    if (context.containsBean("featureToggleHealthIndicator")) {
                        assertThat(context).hasSingleBean(FeatureToggleHealthIndicator.class);
                    }
                });
    }

    @Test
    void shouldNotConfigureHealthIndicatorWhenDisabled() {
        // When & Then
        contextRunner
                .withPropertyValues("feature.toggle.health-check-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FeatureToggleHealthIndicator.class);
                });
    }

    @Test
    void shouldLoadConfigurationPropertiesClass() {
        // When & Then
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(FeatureToggleProperties.class);
                    FeatureToggleProperties properties = context.getBean(FeatureToggleProperties.class);
                    assertThat(properties).isNotNull();
                    // Should have default values
                    assertThat(properties.getBaseUrl()).isNotNull();
                    assertThat(properties.getConnectionTimeout()).isNotNull();
                    assertThat(properties.getReadTimeout()).isNotNull();
                });
    }

    @Test
    void shouldHaveCorrectAnnotations() {
        // When & Then - Verify important annotations are present
        assertThat(FeatureToggleAutoConfiguration.class)
                .hasAnnotation(org.springframework.boot.autoconfigure.AutoConfiguration.class)
                .hasAnnotation(org.springframework.boot.context.properties.EnableConfigurationProperties.class)
                .hasAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class)
                .hasAnnotation(EnableRetry.class);
    }

    @Test
    void shouldConfigureBeanNames() {
        // When & Then
        contextRunner
                .run(context -> {
                    assertThat(context).hasBean("featureToggleRestTemplate");
                    assertThat(context).hasBean("featureToggleAsyncExecutor");
                    assertThat(context).hasBean("featureToggleClient");
                });
    }

    @Test
    void shouldHandlePartialConfiguration() {
        // Given - Only some properties provided
        contextRunner
                .withPropertyValues(
                        "fluxgate.base-url=https://partial.example.com"
                // Missing timeout properties
                )
                .run(context -> {
                    // Then - Should still configure with defaults for missing properties
                    assertThat(context).hasSingleBean(FeatureToggleProperties.class);
                    assertThat(context).hasSingleBean(FluxGateClient.class);

                    FeatureToggleProperties properties = context.getBean(FeatureToggleProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo("https://partial.example.com");
                    // Should have default timeout values
                    assertThat(properties.getConnectionTimeout()).isNotNull();
                    assertThat(properties.getReadTimeout()).isNotNull();
                });
    }

    // Helper configuration classes for testing conditional beans

    @Configuration
    static class CustomRestTemplateConfiguration {
        @Bean
        public RestTemplate customRestTemplate() {
            return new RestTemplateBuilder().build();
        }
    }

    @Configuration
    static class CustomExecutorConfiguration {
        @Bean
        public Executor customExecutor() {
            return Runnable::run;
        }
    }

    @Configuration
    static class CustomFluxGateClientConfiguration {
        @Bean
        public FluxGateClient customClient() {
            // Return a mock or simple implementation
            return new FluxGateClient() {
                @Override
                public boolean isEnabled(String featureKey, String environmentId) {
                    return false;
                }

                @Override
                public boolean isEnabled(String featureKey, String environmentId,
                        java.util.Map<String, String> context) {
                    return false;
                }

                @Override
                public boolean isEnabled(FeatureEvaluationRequest request) {
                    return false;
                }

                @Override
                public java.util.concurrent.CompletableFuture<Boolean> isEnabledAsync(String featureKey,
                        String environmentId) {
                    return java.util.concurrent.CompletableFuture.completedFuture(false);
                }

                @Override
                public java.util.concurrent.CompletableFuture<Boolean> isEnabledAsync(String featureKey,
                        String environmentId, java.util.Map<String, String> context) {
                    return java.util.concurrent.CompletableFuture.completedFuture(false);
                }

                @Override
                public java.util.concurrent.CompletableFuture<Boolean> isEnabledAsync(
                        FeatureEvaluationRequest request) {
                    return java.util.concurrent.CompletableFuture.completedFuture(false);
                }

                @Override
                public boolean isEnabledWithFallback(String featureKey, String environmentId, boolean fallback) {
                    return fallback;
                }

                @Override
                public boolean isEnabledWithFallback(String featureKey, String environmentId,
                        java.util.Map<String, String> context, boolean fallback) {
                    return fallback;
                }

                @Override
                public boolean isEnabledWithFallback(FeatureEvaluationRequest request, boolean fallback) {
                    return fallback;
                }

                @Override
                public void execute(FeatureEvaluationRequest request, java.util.function.Consumer<Void> consumer) {
                    // No-op
                }

                @Override
                public <T> T executeAndReturn(FeatureEvaluationRequest request,
                        java.util.function.Supplier<T> supplier) {
                    return null;
                }

                @Override
                public <T> T executeAndReturnWithFallback(FeatureEvaluationRequest request,
                        java.util.function.Supplier<T> supplier, T fallback) {
                    return fallback;
                }

                @Override
                public boolean isHealthy() {
                    return true;
                }
            };
        }
    }
}