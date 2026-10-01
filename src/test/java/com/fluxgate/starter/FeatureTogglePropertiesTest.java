package com.fluxgate.starter;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureTogglePropertiesTest {

    @Test
    void defaults() {
        FeatureToggleProperties properties = new FeatureToggleProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getBaseUrl()).isNull();
        assertThat(properties.getSdkKey()).isNull();
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getRetry().getInitialBackoff()).isEqualTo(Duration.ofMillis(100));
        assertThat(properties.getCache().isEnabled()).isTrue();
        assertThat(properties.getCache().getTtl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getCache().getMaxSize()).isEqualTo(1000);
        assertThat(properties.getPolling().getInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getOpenfeature().isAutoRegister()).isFalse();
        assertThat(properties.getOpenfeature().getProviderName()).isNull();
        assertThat(properties.getHealth().isEnabled()).isTrue();
        assertThat(properties.getMetrics().isEnabled()).isTrue();
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
                Map.entry("fluxgate.metrics.enabled", "false"),
                Map.entry("fluxgate.health.enabled", "false"),
                Map.entry("fluxgate.polling.interval", "0"),
                Map.entry("fluxgate.openfeature.provider-name", "edge"),
                Map.entry("fluxgate.openfeature.auto-register", "true"));

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
        assertThat(properties.getMetrics().isEnabled()).isFalse();
        assertThat(properties.getHealth().isEnabled()).isFalse();
        assertThat(properties.getPolling().getInterval()).isZero();
        assertThat(properties.getOpenfeature().getProviderName()).isEqualTo("edge");
        assertThat(properties.getOpenfeature().isAutoRegister()).isTrue();
    }

    @Test
    void toStringRedactsSdkKey() {
        FeatureToggleProperties properties = new FeatureToggleProperties();
        assertThat(properties.toString()).contains("sdkKey=null");

        properties.setSdkKey("client.super-secret");

        assertThat(properties.toString())
                .contains("[REDACTED]", "maxAttempts=3", "ttl=PT30S", "polling={interval=PT30S}",
                        "openfeature={autoRegister=false, providerName=null}", "health={enabled=true}",
                        "metrics={enabled=true}")
                .doesNotContain("super-secret");
    }

    @Nested
    class Validation {

        private Errors validate(String baseUrl, String sdkKey, boolean enabled) {
            FeatureToggleProperties properties = new FeatureToggleProperties();
            properties.setBaseUrl(baseUrl);
            properties.setSdkKey(sdkKey);
            properties.setEnabled(enabled);
            Errors errors = new BeanPropertyBindingResult(properties, "fluxgate");
            assertThat(properties.supports(FeatureToggleProperties.class)).isTrue();
            properties.validate(properties, errors);
            return errors;
        }

        @Test
        void acceptsUrlAndWellFormedKey() {
            assertThat(validate("https://edge.example.com", "client.key", true).hasErrors()).isFalse();
            assertThat(validate("http://localhost:8081", "c.k.with.dots", true).hasErrors()).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        void rejectsMissingBaseUrlAndSdkKey(String value) {
            Errors errors = validate(value, value, true);

            assertThat(errors.getFieldError("baseUrl").getDefaultMessage())
                    .startsWith("fluxgate.base-url must be set");
            assertThat(errors.getFieldError("sdkKey").getDefaultMessage())
                    .startsWith("fluxgate.sdk-key must be set").contains("<clientId>.<apiKey>");
        }

        @Test
        void rejectsNonHttpBaseUrl() {
            Errors errors = validate("edge.example.com", "c.k", true);

            assertThat(errors.getFieldError("baseUrl").getDefaultMessage())
                    .isEqualTo("fluxgate.base-url must be an http:// or https:// URL");
        }

        @ParameterizedTest
        @ValueSource(strings = { "no-dot-key", ".key", "client." })
        void rejectsMalformedSdkKeyWithoutEchoingIt(String key) {
            Errors errors = validate("https://edge.example.com", key, true);

            assertThat(errors.getFieldErrors()).isEmpty();
            assertThat(errors.getGlobalError().getDefaultMessage())
                    .isEqualTo("fluxgate.sdk-key must have the form <clientId>.<apiKey>");
        }

        @Test
        void skipsValidationWhenDisabled() {
            assertThat(validate(null, null, false).hasErrors()).isFalse();
        }
    }
}
