package com.forgeopslabs.fluxgate;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FluxGateOptionsTest {

    private static FluxGateOptions.Builder valid() {
        return FluxGateOptions.builder().baseUrl("https://edge.example.com").sdkKey("client-1.api-key-1");
    }

    @Test
    void defaultsMatchTheStarter() {
        FluxGateOptions options = valid().build();

        assertThat(options.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(options.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(options.getRetryMaxAttempts()).isEqualTo(3);
        assertThat(options.getRetryInitialBackoff()).isEqualTo(Duration.ofMillis(100));
        assertThat(options.isCacheEnabled()).isTrue();
        assertThat(options.getCacheTtl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(options.getCacheMaxSize()).isEqualTo(1000);
        assertThat(options.getCircuitBreakerFailureThreshold()).isEqualTo(5);
        assertThat(options.getCircuitBreakerOpenDuration()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void stripsTrailingSlashFromBaseUrl() {
        assertThat(valid().baseUrl("http://edge:8081/").build().getBaseUrl()).isEqualTo("http://edge:8081");
    }

    @Test
    void rejectsMissingOrNonHttpBaseUrl() {
        assertThatThrownBy(() -> valid().baseUrl(null).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid().baseUrl(" ").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid().baseUrl("edge:8081").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http://");
    }

    @Test
    void rejectsMissingOrMalformedSdkKeyWithoutEchoingIt() {
        assertThatThrownBy(() -> valid().sdkKey(null).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid().sdkKey("secret-without-dot").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret-without-dot");
        assertThatThrownBy(() -> valid().sdkKey(".key").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid().sdkKey("client.").build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidTimeoutsAndBreakerSettings() {
        assertThatThrownBy(() -> valid().readTimeout(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid().connectTimeout(null).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid().circuitBreakerFailureThreshold(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid().circuitBreakerOpenDuration(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(valid().circuitBreakerFailureThreshold(0).circuitBreakerOpenDuration(null).build()
                .getCircuitBreakerFailureThreshold()).isZero();
    }

    @Test
    void toStringRedactsSdkKey() {
        assertThat(valid().build().toString()).doesNotContain("api-key-1").contains("[REDACTED]");
    }
}
