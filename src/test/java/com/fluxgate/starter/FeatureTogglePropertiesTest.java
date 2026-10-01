package com.fluxgate.starter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureTogglePropertiesTest {

    @Test
    void defaults() {
        FeatureToggleProperties properties = new FeatureToggleProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8081");
        assertThat(properties.getSdkKey()).isNull();
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getRetry().getInitialBackoff()).isEqualTo(Duration.ofMillis(100));
        assertThat(properties.getCache().isEnabled()).isTrue();
        assertThat(properties.getCache().getTtl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getCache().getMaxSize()).isEqualTo(1000);
    }

    @Test
    void bindsFluxgatePrefix() {
        Map<String, String> source = Map.ofEntries(
                Map.entry("fluxgate.enabled", "false"),
                Map.entry("fluxgate.base-url", "https://edge.example.com"),
                Map.entry("fluxgate.sdk-key", "client.secret"),
                Map.entry("fluxgate.connect-timeout", "2s"),
                Map.entry("fluxgate.read-timeout", "750ms"),
                Map.entry("fluxgate.retry.max-attempts", "5"),
                Map.entry("fluxgate.retry.initial-backoff", "50ms"),
                Map.entry("fluxgate.cache.enabled", "false"),
                Map.entry("fluxgate.cache.ttl", "1m"),
                Map.entry("fluxgate.cache.max-size", "42"),
                Map.entry("fluxgate.metrics-enabled", "false"),
                Map.entry("fluxgate.health-check-enabled", "false"),
                Map.entry("fluxgate.health-check-interval", "10s"),
                Map.entry("fluxgate.openfeature-provider-name", "edge"),
                Map.entry("fluxgate.openfeature-auto-register", "true"));

        FeatureToggleProperties properties = new Binder(new MapConfigurationPropertySource(source))
                .bind("fluxgate", FeatureToggleProperties.class).get();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getBaseUrl()).isEqualTo("https://edge.example.com");
        assertThat(properties.getSdkKey()).isEqualTo("client.secret");
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofMillis(750));
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(5);
        assertThat(properties.getRetry().getInitialBackoff()).isEqualTo(Duration.ofMillis(50));
        assertThat(properties.getCache().isEnabled()).isFalse();
        assertThat(properties.getCache().getTtl()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.getCache().getMaxSize()).isEqualTo(42);
        assertThat(properties.isMetricsEnabled()).isFalse();
        assertThat(properties.isHealthCheckEnabled()).isFalse();
        assertThat(properties.getHealthCheckInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getOpenfeatureProviderName()).isEqualTo("edge");
        assertThat(properties.isOpenfeatureAutoRegister()).isTrue();
    }

    @Test
    void toStringRedactsSdkKey() {
        FeatureToggleProperties properties = new FeatureToggleProperties();
        assertThat(properties.toString()).contains("sdkKey=null");

        properties.setSdkKey("client.super-secret");

        assertThat(properties.toString())
                .contains("[REDACTED]", "maxAttempts=3", "ttl=PT30S")
                .doesNotContain("super-secret");
    }
}
