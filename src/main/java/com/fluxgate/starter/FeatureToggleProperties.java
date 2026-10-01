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
 *   polling:
 *     interval: 30s
 *   openfeature:
 *     auto-register: false
 *     provider-name: # unset: default provider
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

    private final Polling polling = new Polling();

    private final OpenFeature openfeature = new OpenFeature();

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

    public Polling getPolling() {
        return polling;
    }

    public OpenFeature getOpenfeature() {
        return openfeature;
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
                ", polling=" + polling +
                ", openfeature=" + openfeature +
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

    /**
     * Change detection for the OpenFeature provider.
     */
    public static class Polling {

        /**
         * How often the OpenFeature provider revalidates the flag set with a
         * conditional bulk evaluation. {@code 0} disables polling, which also
         * disables recovery after a failed initialization.
         */
        private Duration interval = Duration.ofSeconds(30);

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }

        @Override
        public String toString() {
            return "{interval=" + interval + '}';
        }
    }

    /**
     * OpenFeature integration settings.
     */
    public static class OpenFeature {

        /**
         * Whether to register the FluxGate provider with {@code OpenFeatureAPI}
         * at startup.
         */
        private boolean autoRegister = false;

        /**
         * OpenFeature domain to register the provider under. When unset the
         * provider becomes the default provider.
         */
        private String providerName;

        public boolean isAutoRegister() {
            return autoRegister;
        }

        public void setAutoRegister(boolean autoRegister) {
            this.autoRegister = autoRegister;
        }

        public String getProviderName() {
            return providerName;
        }

        public void setProviderName(String providerName) {
            this.providerName = providerName;
        }

        @Override
        public String toString() {
            return "{autoRegister=" + autoRegister + ", providerName=" + providerName + '}';
        }
    }
}
