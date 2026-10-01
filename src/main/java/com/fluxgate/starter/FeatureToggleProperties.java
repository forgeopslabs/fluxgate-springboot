package com.fluxgate.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the FluxGate starter, bound from the
 * {@code fluxgate} prefix:
 *
 * <pre>
 * fluxgate:
 *   base-url: http://localhost:8081
 *   sdk-key: client-id.api-key
 *   connect-timeout: 5s
 *   read-timeout: 5s
 *   retry:
 *     max-attempts: 3
 *     initial-backoff: 100ms
 *   cache:
 *     enabled: true
 *     ttl: 30s
 *     max-size: 1000
 * </pre>
 */
@ConfigurationProperties(prefix = "fluxgate")
public class FeatureToggleProperties {

    /**
     * Whether the FluxGate starter is enabled.
     */
    private boolean enabled = true;

    /**
     * Base URL of the FluxGate edge server.
     */
    private String baseUrl = "http://localhost:8081";

    /**
     * SDK key in the form {@code <clientId>.<apiKey>}, sent as
     * {@code Authorization: Bearer <sdkKey>}.
     */
    private String sdkKey;

    /**
     * Connect timeout for edge requests.
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * Read timeout for edge requests.
     */
    private Duration readTimeout = Duration.ofSeconds(5);

    private final Retry retry = new Retry();

    private final Cache cache = new Cache();

    /**
     * Whether to enable metrics collection using Micrometer.
     */
    private boolean metricsEnabled = true;

    /**
     * Whether to enable the Actuator health indicator.
     */
    private boolean healthCheckEnabled = true;

    /**
     * Interval for health check pings to the edge server.
     */
    private Duration healthCheckInterval = Duration.ofSeconds(30);

    /**
     * Provider name for OpenFeature domain registration.
     */
    private String openfeatureProviderName = "fluxgate";

    /**
     * Whether to automatically register the FluxGate provider with
     * OpenFeatureAPI.
     */
    private boolean openfeatureAutoRegister = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getSdkKey() {
        return sdkKey;
    }

    public void setSdkKey(String sdkKey) {
        this.sdkKey = sdkKey;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Retry getRetry() {
        return retry;
    }

    public Cache getCache() {
        return cache;
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

    public String getOpenfeatureProviderName() {
        return openfeatureProviderName;
    }

    public void setOpenfeatureProviderName(String openfeatureProviderName) {
        this.openfeatureProviderName = openfeatureProviderName;
    }

    public boolean isOpenfeatureAutoRegister() {
        return openfeatureAutoRegister;
    }

    public void setOpenfeatureAutoRegister(boolean openfeatureAutoRegister) {
        this.openfeatureAutoRegister = openfeatureAutoRegister;
    }

    @Override
    public String toString() {
        return "FeatureToggleProperties{" +
                "enabled=" + enabled +
                ", baseUrl='" + baseUrl + '\'' +
                ", sdkKey=" + (sdkKey == null ? "null" : "'[REDACTED]'") +
                ", connectTimeout=" + connectTimeout +
                ", readTimeout=" + readTimeout +
                ", retry=" + retry +
                ", cache=" + cache +
                '}';
    }

    /**
     * Retry settings for evaluation calls. Only I/O errors and HTTP 502, 503
     * and 504 are retried.
     */
    public static class Retry {

        /**
         * Total attempts per evaluation, including the first.
         */
        private int maxAttempts = 3;

        /**
         * Pause before the first retry; doubles on each further retry.
         */
        private Duration initialBackoff = Duration.ofMillis(100);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        @Override
        public String toString() {
            return "{maxAttempts=" + maxAttempts + ", initialBackoff=" + initialBackoff + '}';
        }
    }

    /**
     * Evaluation cache settings.
     */
    public static class Cache {

        /**
         * Whether successful evaluations are cached per flag key and context.
         */
        private boolean enabled = true;

        /**
         * Time a cached evaluation stays valid after it is written.
         */
        private Duration ttl = Duration.ofSeconds(30);

        /**
         * Maximum number of cached evaluations.
         */
        private long maxSize = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        public String toString() {
            return "{enabled=" + enabled + ", ttl=" + ttl + ", maxSize=" + maxSize + '}';
        }
    }
}
