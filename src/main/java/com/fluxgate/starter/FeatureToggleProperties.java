package com.fluxgate.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for FluxGate Feature Toggle integration.
 * 
 * Configure these properties using the prefix "feature.toggle" in your
 * application.yml or application.properties:
 * 
 * <pre>
 * feature:
 *   toggle:
 *     base-url: http://localhost:8081
 *     client-id: your-client-id
 *     client-secret: your-client-secret
 *     connection-timeout: PT5S
 *     read-timeout: PT5S
 *     retry-attempts: 3
 *     retry-delay: PT1S
 *     fallback-enabled: true
 * </pre>
 */
@ConfigurationProperties(prefix = "fluxgate")
public class FeatureToggleProperties {

    /**
     * Base URL of the FluxGate Edge Server.
     * Default: http://localhost:8081
     */
    private String baseUrl = "http://localhost:8081";

    /**
     * Client ID for authentication with the Edge Server.
     * This is optional and can be overridden per request.
     */
    private String clientId;

    /**
     * Client secret for authentication with the Edge Server.
     * This is optional and can be overridden per request.
     */
    private String clientSecret;

    /**
     * Connection timeout for HTTP requests to the Edge Server.
     * Default: 5 seconds
     */
    private Duration connectionTimeout = Duration.ofSeconds(5);

    /**
     * Read timeout for HTTP requests to the Edge Server.
     * Default: 5 seconds
     */
    private Duration readTimeout = Duration.ofSeconds(5);

    /**
     * Number of retry attempts for failed requests.
     * Default: 3
     */
    private int retryAttempts = 3;

    /**
     * Initial delay between retry attempts.
     * Default: 1 second
     */
    private Duration retryDelay = Duration.ofSeconds(1);

    /**
     * Maximum delay between retry attempts when using exponential backoff.
     * Default: 10 seconds
     */
    private Duration maxRetryDelay = Duration.ofSeconds(10);

    /**
     * Multiplier for exponential backoff retry delay.
     * Default: 2.0
     */
    private double retryMultiplier = 2.0;

    /**
     * Whether to enable fallback behavior when the Edge Server is unavailable.
     * When enabled, feature flags will default to false if the server is
     * unreachable.
     * Default: true
     */
    private boolean fallbackEnabled = true;

    /**
     * Default fallback value when fallbackEnabled is true and the server is
     * unreachable.
     * Default: false (feature disabled)
     */
    private boolean defaultFallbackValue = false;

    /**
     * Whether to enable metrics collection using Micrometer.
     * Only effective if Micrometer is on the classpath.
     * Default: true
     */
    private boolean metricsEnabled = true;

    /**
     * Whether to enable health indicator for Spring Boot Actuator.
     * Only effective if Actuator is on the classpath.
     * Default: true
     */
    private boolean healthCheckEnabled = true;

    /**
     * Interval for health check pings to the Edge Server.
     * Default: 30 seconds
     */
    private Duration healthCheckInterval = Duration.ofSeconds(30);

    /**
     * Whether to cache feature evaluation results to reduce server load.
     * Default: false (no caching)
     */
    private boolean cachingEnabled = false;

    /**
     * TTL for cached feature evaluation results.
     * Only effective if caching is enabled.
     * Default: 5 minutes
     */
    private Duration cacheEntryTtl = Duration.ofMinutes(5);

    /**
     * Maximum number of entries in the evaluation cache.
     * Only effective if caching is enabled.
     * Default: 1000
     */
    private int cacheMaxSize = 1000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    public void setMaxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = maxRetryDelay;
    }

    public double getRetryMultiplier() {
        return retryMultiplier;
    }

    public void setRetryMultiplier(double retryMultiplier) {
        this.retryMultiplier = retryMultiplier;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public boolean isDefaultFallbackValue() {
        return defaultFallbackValue;
    }

    public void setDefaultFallbackValue(boolean defaultFallbackValue) {
        this.defaultFallbackValue = defaultFallbackValue;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    public void setHealthCheckEnabled(boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }

    public Duration getHealthCheckInterval() {
        return healthCheckInterval;
    }

    public void setHealthCheckInterval(Duration healthCheckInterval) {
        this.healthCheckInterval = healthCheckInterval;
    }

    public boolean isCachingEnabled() {
        return cachingEnabled;
    }

    public void setCachingEnabled(boolean cachingEnabled) {
        this.cachingEnabled = cachingEnabled;
    }

    public Duration getCacheEntryTtl() {
        return cacheEntryTtl;
    }

    public void setCacheEntryTtl(Duration cacheEntryTtl) {
        this.cacheEntryTtl = cacheEntryTtl;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }

    @Override
    public String toString() {
        return "FeatureToggleProperties{" +
                "baseUrl='" + baseUrl + '\'' +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='[REDACTED]'" +
                ", connectionTimeout=" + connectionTimeout +
                ", readTimeout=" + readTimeout +
                ", retryAttempts=" + retryAttempts +
                ", retryDelay=" + retryDelay +
                ", maxRetryDelay=" + maxRetryDelay +
                ", retryMultiplier=" + retryMultiplier +
                ", fallbackEnabled=" + fallbackEnabled +
                ", defaultFallbackValue=" + defaultFallbackValue +
                ", metricsEnabled=" + metricsEnabled +
                ", healthCheckEnabled=" + healthCheckEnabled +
                ", healthCheckInterval=" + healthCheckInterval +
                ", cachingEnabled=" + cachingEnabled +
                ", cacheEntryTtl=" + cacheEntryTtl +
                ", cacheMaxSize=" + cacheMaxSize +
                '}';
    }
}