package com.fluxgate.starter;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class FeatureTogglePropertiesTest {

    @Test
    void shouldHaveCorrectDefaultValues() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();

        // Then
        assertEquals("http://localhost:8081", properties.getBaseUrl());
        assertNull(properties.getClientId());
        assertNull(properties.getClientSecret());
        assertEquals(Duration.ofSeconds(5), properties.getConnectionTimeout());
        assertEquals(Duration.ofSeconds(5), properties.getReadTimeout());
        assertEquals(3, properties.getRetryAttempts());
        assertEquals(Duration.ofSeconds(1), properties.getRetryDelay());
        assertEquals(Duration.ofSeconds(10), properties.getMaxRetryDelay());
        assertEquals(2.0, properties.getRetryMultiplier());
        assertTrue(properties.isFallbackEnabled());
        assertFalse(properties.isDefaultFallbackValue());
        assertTrue(properties.isMetricsEnabled());
        assertTrue(properties.isHealthCheckEnabled());
        assertEquals(Duration.ofSeconds(30), properties.getHealthCheckInterval());
        assertFalse(properties.isCachingEnabled());
        assertEquals(Duration.ofMinutes(5), properties.getCacheEntryTtl());
        assertEquals(1000, properties.getCacheMaxSize());
    }

    @Test
    void shouldSetAndGetBaseUrl() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        String newBaseUrl = "https://api.example.com";

        // When
        properties.setBaseUrl(newBaseUrl);

        // Then
        assertEquals(newBaseUrl, properties.getBaseUrl());
    }

    @Test
    void shouldSetAndGetClientId() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        String clientId = "test-client-id";

        // When
        properties.setClientId(clientId);

        // Then
        assertEquals(clientId, properties.getClientId());
    }

    @Test
    void shouldSetAndGetClientSecret() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        String clientSecret = "test-client-secret";

        // When
        properties.setClientSecret(clientSecret);

        // Then
        assertEquals(clientSecret, properties.getClientSecret());
    }

    @Test
    void shouldSetAndGetConnectionTimeout() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        Duration timeout = Duration.ofSeconds(10);

        // When
        properties.setConnectionTimeout(timeout);

        // Then
        assertEquals(timeout, properties.getConnectionTimeout());
    }

    @Test
    void shouldSetAndGetReadTimeout() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        Duration timeout = Duration.ofSeconds(15);

        // When
        properties.setReadTimeout(timeout);

        // Then
        assertEquals(timeout, properties.getReadTimeout());
    }

    @Test
    void shouldSetAndGetRetryAttempts() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        int retryAttempts = 5;

        // When
        properties.setRetryAttempts(retryAttempts);

        // Then
        assertEquals(retryAttempts, properties.getRetryAttempts());
    }

    @Test
    void shouldSetAndGetRetryDelay() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        Duration delay = Duration.ofSeconds(2);

        // When
        properties.setRetryDelay(delay);

        // Then
        assertEquals(delay, properties.getRetryDelay());
    }

    @Test
    void shouldSetAndGetMaxRetryDelay() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        Duration maxDelay = Duration.ofSeconds(30);

        // When
        properties.setMaxRetryDelay(maxDelay);

        // Then
        assertEquals(maxDelay, properties.getMaxRetryDelay());
    }

    @Test
    void shouldSetAndGetRetryMultiplier() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        double multiplier = 1.5;

        // When
        properties.setRetryMultiplier(multiplier);

        // Then
        assertEquals(multiplier, properties.getRetryMultiplier());
    }

    @Test
    void shouldSetAndGetFallbackEnabled() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();

        // When
        properties.setFallbackEnabled(false);

        // Then
        assertFalse(properties.isFallbackEnabled());

        // When
        properties.setFallbackEnabled(true);

        // Then
        assertTrue(properties.isFallbackEnabled());
    }

    @Test
    void shouldSetAndGetDefaultFallbackValue() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();

        // When
        properties.setDefaultFallbackValue(true);

        // Then
        assertTrue(properties.isDefaultFallbackValue());

        // When
        properties.setDefaultFallbackValue(false);

        // Then
        assertFalse(properties.isDefaultFallbackValue());
    }

    @Test
    void shouldSetAndGetMetricsEnabled() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();

        // When
        properties.setMetricsEnabled(false);

        // Then
        assertFalse(properties.isMetricsEnabled());

        // When
        properties.setMetricsEnabled(true);

        // Then
        assertTrue(properties.isMetricsEnabled());
    }

    @Test
    void shouldSetAndGetHealthCheckEnabled() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();

        // When
        properties.setHealthCheckEnabled(false);

        // Then
        assertFalse(properties.isHealthCheckEnabled());

        // When
        properties.setHealthCheckEnabled(true);

        // Then
        assertTrue(properties.isHealthCheckEnabled());
    }

    @Test
    void shouldSetAndGetHealthCheckInterval() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        Duration interval = Duration.ofMinutes(1);

        // When
        properties.setHealthCheckInterval(interval);

        // Then
        assertEquals(interval, properties.getHealthCheckInterval());
    }

    @Test
    void shouldSetAndGetCachingEnabled() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();

        // When
        properties.setCachingEnabled(true);

        // Then
        assertTrue(properties.isCachingEnabled());

        // When
        properties.setCachingEnabled(false);

        // Then
        assertFalse(properties.isCachingEnabled());
    }

    @Test
    void shouldSetAndGetCacheEntryTtl() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        Duration ttl = Duration.ofMinutes(10);

        // When
        properties.setCacheEntryTtl(ttl);

        // Then
        assertEquals(ttl, properties.getCacheEntryTtl());
    }

    @Test
    void shouldSetAndGetCacheMaxSize() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        int maxSize = 2000;

        // When
        properties.setCacheMaxSize(maxSize);

        // Then
        assertEquals(maxSize, properties.getCacheMaxSize());
    }

    @Test
    void shouldRedactClientSecretInToString() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        properties.setClientId("test-client");
        properties.setClientSecret("super-secret-password");

        // When
        String toString = properties.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("clientId='test-client'"));
        assertTrue(toString.contains("clientSecret='[REDACTED]'"));
        assertFalse(toString.contains("super-secret-password"));
    }

    @Test
    void shouldIncludeAllFieldsInToString() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        properties.setBaseUrl("https://custom.example.com");
        properties.setClientId("custom-client");
        properties.setRetryAttempts(5);

        // When
        String toString = properties.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("FeatureToggleProperties{"));
        assertTrue(toString.contains("baseUrl='https://custom.example.com'"));
        assertTrue(toString.contains("clientId='custom-client'"));
        assertTrue(toString.contains("retryAttempts=5"));
        assertTrue(toString.contains("fallbackEnabled=true"));
        assertTrue(toString.contains("metricsEnabled=true"));
        assertTrue(toString.contains("cachingEnabled=false"));
    }

    @Test
    void shouldHandleNullValuesInToString() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();
        // clientId and clientSecret are null by default

        // When
        String toString = properties.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("clientId='null'"));
        assertTrue(toString.contains("clientSecret='[REDACTED]'"));
    }

    @Test
    void shouldSetAllPropertiesIndependently() {
        // Given
        FeatureToggleProperties properties = new FeatureToggleProperties();

        // When - Set all properties to non-default values
        properties.setBaseUrl("https://prod.example.com");
        properties.setClientId("prod-client");
        properties.setClientSecret("prod-secret");
        properties.setConnectionTimeout(Duration.ofSeconds(20));
        properties.setReadTimeout(Duration.ofSeconds(25));
        properties.setRetryAttempts(7);
        properties.setRetryDelay(Duration.ofSeconds(3));
        properties.setMaxRetryDelay(Duration.ofSeconds(60));
        properties.setRetryMultiplier(3.0);
        properties.setFallbackEnabled(false);
        properties.setDefaultFallbackValue(true);
        properties.setMetricsEnabled(false);
        properties.setHealthCheckEnabled(false);
        properties.setHealthCheckInterval(Duration.ofMinutes(2));
        properties.setCachingEnabled(true);
        properties.setCacheEntryTtl(Duration.ofMinutes(15));
        properties.setCacheMaxSize(5000);

        // Then - Verify all properties are set correctly
        assertEquals("https://prod.example.com", properties.getBaseUrl());
        assertEquals("prod-client", properties.getClientId());
        assertEquals("prod-secret", properties.getClientSecret());
        assertEquals(Duration.ofSeconds(20), properties.getConnectionTimeout());
        assertEquals(Duration.ofSeconds(25), properties.getReadTimeout());
        assertEquals(7, properties.getRetryAttempts());
        assertEquals(Duration.ofSeconds(3), properties.getRetryDelay());
        assertEquals(Duration.ofSeconds(60), properties.getMaxRetryDelay());
        assertEquals(3.0, properties.getRetryMultiplier());
        assertFalse(properties.isFallbackEnabled());
        assertTrue(properties.isDefaultFallbackValue());
        assertFalse(properties.isMetricsEnabled());
        assertFalse(properties.isHealthCheckEnabled());
        assertEquals(Duration.ofMinutes(2), properties.getHealthCheckInterval());
        assertTrue(properties.isCachingEnabled());
        assertEquals(Duration.ofMinutes(15), properties.getCacheEntryTtl());
        assertEquals(5000, properties.getCacheMaxSize());
    }
}