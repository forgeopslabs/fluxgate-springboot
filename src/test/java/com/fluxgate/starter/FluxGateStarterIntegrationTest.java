package com.fluxgate.starter;

import com.fluxgate.starter.annotation.EvaluationContextResolver;
import com.fluxgate.starter.annotation.FeatureToggle;
import com.fluxgate.starter.aop.FeatureToggleAspect;
import com.fluxgate.starter.health.FluxGateHealthIndicator;
import com.fluxgate.starter.metrics.FluxGateMetrics;
import com.fluxgate.starter.openfeature.FluxGateProvider;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;

import static com.fluxgate.starter.EdgeContract.FLAG_PATH;
import static com.fluxgate.starter.EdgeContract.SDK_KEY;
import static com.fluxgate.starter.EdgeContract.fixture;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The starter in a real Spring Boot application against a WireMock edge:
 * auto-configuration, property binding, the {@code @FeatureToggle} aspect,
 * metrics and health.
 */
@SpringBootTest(classes = FluxGateStarterIntegrationTest.TestApplication.class, properties = {
        "spring.main.banner-mode=off",
        "fluxgate.sdk-key=" + SDK_KEY,
        "fluxgate.retry.max-attempts=1",
        "fluxgate.read-timeout=2s",
        "fluxgate.cache.ttl=1m",
        "fluxgate.polling.interval=100ms" })
class FluxGateStarterIntegrationTest {

    @RegisterExtension
    static WireMockExtension edge = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void edgeUrl(DynamicPropertyRegistry registry) {
        registry.add("fluxgate.base-url", edge::baseUrl);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        EvaluationContextResolver evaluationContextResolver() {
            return (flagKey, method, arguments) -> EvaluationContext.builder()
                    .targetingKey("user-42")
                    .attribute("plan", "gold")
                    .build();
        }

        @Bean
        CheckoutService checkoutService() {
            return new CheckoutService();
        }
    }

    public static class CheckoutService {

        @FeatureToggle("aspect-on")
        public String newCheckout(String cart) {
            return "new:" + cart;
        }

        @FeatureToggle("aspect-off")
        public String darkLaunch(String cart) {
            return "dark:" + cart;
        }

        @FeatureToggle(value = "aspect-fallback", fallbackMethod = "legacyCheckout")
        public String checkoutWithFallback(String cart) {
            return "new:" + cart;
        }

        String legacyCheckout(String cart) {
            return "legacy:" + cart;
        }
    }

    @Autowired
    private FeatureToggleProperties properties;
    @Autowired
    private FluxGateClient client;
    @Autowired
    private CheckoutService checkout;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private HealthEndpoint healthEndpoint;
    @Autowired
    private FluxGateProvider provider;
    @Autowired
    private FluxGateMetrics metrics;
    @Autowired
    private FluxGateHealthIndicator healthIndicator;
    @Autowired
    private FeatureToggleAspect aspect;

    private static void stubFlag(String key, int status, String body) {
        edge.stubFor(post(urlEqualTo(FLAG_PATH + key))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Test
    void autoConfiguresTheStarterAndBindsProperties() {
        assertThat(client).isInstanceOf(DefaultFluxGateClient.class);
        assertThat(provider).isNotNull();
        assertThat(metrics).isNotNull();
        assertThat(healthIndicator).isNotNull();
        assertThat(aspect).isNotNull();
        assertThat(properties.getBaseUrl()).isEqualTo(edge.baseUrl());
        assertThat(properties.getSdkKey()).isEqualTo(SDK_KEY);
        assertThat(properties.getRetry().getMaxAttempts()).isOne();
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getCache().getTtl()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void aspectRunsTheMethodWhenTheFlagIsOn() {
        stubFlag("aspect-on", 200, fixture("flag-boolean.json"));

        assertThat(checkout.newCheckout("cart-1")).isEqualTo("new:cart-1");
        edge.verify(postRequestedFor(urlEqualTo(FLAG_PATH + "aspect-on"))
                .withHeader("Authorization", equalTo("Bearer " + SDK_KEY))
                .withRequestBody(equalToJson("{\"context\": {\"targetingKey\": \"user-42\", \"plan\": \"gold\"}}")));
    }

    @Test
    void aspectReturnsTheDefaultWhenTheFlagIsOff() {
        stubFlag("aspect-off", 200, fixture("flag-boolean-disabled.json"));

        assertThat(checkout.darkLaunch("cart-1")).isNull();
    }

    @Test
    void aspectCallsTheFallbackWhenTheFlagIsOff() {
        stubFlag("aspect-fallback", 200, fixture("flag-boolean-disabled.json"));

        assertThat(checkout.checkoutWithFallback("cart-1")).isEqualTo("legacy:cart-1");
    }

    @Test
    void recordsEvaluationTimerAndCacheCounters() {
        // flag-string.json with the key this test requests (the edge echoes the requested key).
        stubFlag("metrics-flag", 200, fixture("flag-string.json").replace("banner-color", "metrics-flag"));
        EvaluationContext user = EvaluationContext.of("user-7");
        double hitsBefore = meterRegistry.get(FluxGateMetrics.CACHE_COUNTER).tag("result", "hit").counter().count();

        client.evaluate("metrics-flag", "none", String.class, user);
        client.evaluate("metrics-flag", "none", String.class, user);

        client.evaluate("metrics-flag", "none", String.class, user);

        assertThat(meterRegistry.get(FluxGateMetrics.EVALUATION_TIMER)
                .tags("flag", "metrics-flag", "reason", "SPLIT", "variant", "blue", "error", "none")
                .timer().count()).isEqualTo(3);
        assertThat(meterRegistry.get(FluxGateMetrics.CACHE_COUNTER).tag("result", "hit").counter().count())
                .isEqualTo(hitsBefore + 2);
    }

    @Test
    void healthReflectsTheProbeAndTheLastEdgeOutcome() {
        edge.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200)));
        stubFlag("health-ok", 200, fixture("flag-boolean.json"));
        client.isEnabled("health-ok", EvaluationContext.of("user-1"));

        await().untilAsserted(() -> {
            Health health = (Health) healthEndpoint.healthForPath("fluxgate");
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("edgeHealth", "UP")
                    .containsEntry("lastCallStatus", "SUCCESS");
        });

        stubFlag("health-rejected", 401, fixture("error-unauthorized.json"));
        client.isEnabled("health-rejected", EvaluationContext.of("user-1"));

        Health health = (Health) healthEndpoint.healthForPath("fluxgate");
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("errorCode", "UNAUTHORIZED");
    }
}
