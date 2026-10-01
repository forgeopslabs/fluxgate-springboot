package com.fluxgate.starter;

import com.fluxgate.starter.ofrep.OfrepTransport;
import com.fluxgate.starter.openfeature.FluxGateProvider;
import com.fluxgate.starter.openfeature.OpenFeatureRegistrar;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureToggleAutoConfigurationTest {

    /** Unreachable edge; retries off so failing calls return at once. */
    static final String[] REQUIRED = { "fluxgate.base-url=http://localhost:1", "fluxgate.sdk-key=client.key",
            "fluxgate.retry.max-attempts=1" };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeatureToggleAutoConfiguration.class))
            .withPropertyValues(REQUIRED);

    @Test
    void configuresClientByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FeatureToggleProperties.class);
            assertThat(context).hasSingleBean(OfrepTransport.class);
            assertThat(context).hasSingleBean(FluxGateClient.class);
            assertThat(context.getBean(FluxGateClient.class)).isInstanceOf(DefaultFluxGateClient.class);
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
    void failsFastWithoutBaseUrlAndSdkKey() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FeatureToggleAutoConfiguration.class))
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("fluxgate.base-url must be set")
                        .hasMessageContaining("fluxgate.sdk-key must be set"));
    }

    @Test
    void failsFastOnMalformedSdkKeyWithoutLeakingIt() {
        contextRunner
                .withPropertyValues("fluxgate.sdk-key=secret-without-dot")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .hasMessageContaining("fluxgate.sdk-key must have the form <clientId>.<apiKey>")
                        .hasMessageNotContaining("secret-without-dot"));
    }

    @Test
    void disabledStarterNeedsNoProperties() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FeatureToggleAutoConfiguration.class))
                .withPropertyValues("fluxgate.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(FluxGateClient.class));
    }

    @Test
    void registersListenerBeansWithTheClient() {
        List<FlagEvaluation<?>> seen = new CopyOnWriteArrayList<>();
        FluxGateClientListener listener = new FluxGateClientListener() {
            @Override
            public void onEvaluation(FlagEvaluation<?> evaluation, boolean fromCache, long durationNanos) {
                seen.add(evaluation);
            }
        };

        contextRunner
                .withBean(FluxGateClientListener.class, () -> listener)
                .run(context -> {
                    context.getBean(FluxGateClient.class).isEnabled("f", EvaluationContext.of("u"));

                    assertThat(seen).singleElement().satisfies(e -> {
                        assertThat(e.key()).isEqualTo("f");
                        assertThat(e.errorCode()).isEqualTo(ErrorCode.GENERAL);
                    });
                });
    }

    @Test
    void evaluateAsyncUsesTheNamedExecutorBean() {
        AtomicInteger named = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        contextRunner
                .withBean(FeatureToggleAutoConfiguration.ASYNC_EXECUTOR_BEAN_NAME, Executor.class,
                        () -> task -> {
                            named.incrementAndGet();
                            task.run();
                        })
                .withBean("taskExecutor", Executor.class, () -> task -> {
                    other.incrementAndGet();
                    task.run();
                })
                .run(context -> {
                    FlagEvaluation<Boolean> result = context.getBean(FluxGateClient.class)
                            .evaluateAsync("f", true, Boolean.class, EvaluationContext.of("u")).join();

                    assertThat(result.value()).isTrue();
                    assertThat(named).hasValue(1);
                    assertThat(other).hasValue(0);
                });
    }

    @Test
    void evaluateAsyncIgnoresUnnamedExecutors() {
        AtomicInteger other = new AtomicInteger();

        contextRunner
                .withBean("taskExecutor", Executor.class, () -> task -> {
                    other.incrementAndGet();
                    task.run();
                })
                .run(context -> {
                    FlagEvaluation<Boolean> result = context.getBean(FluxGateClient.class)
                            .evaluateAsync("f", true, Boolean.class, EvaluationContext.of("u")).join();

                    assertThat(result.errorCode()).isEqualTo(ErrorCode.GENERAL);
                    assertThat(other).hasValue(0);
                });
    }

    @Test
    void worksWithoutOpenFeatureOnTheClasspath() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("dev.openfeature.sdk"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(FluxGateClient.class);
                    assertThat(context).doesNotHaveBean("fluxGateProvider");
                });
    }

    @Test
    void userClientWins() {
        FluxGateClient custom = mock(FluxGateClient.class);

        contextRunner
                .withBean(FluxGateClient.class, () -> custom)
                .run(context -> assertThat(context.getBean(FluxGateClient.class)).isSameAs(custom));
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
