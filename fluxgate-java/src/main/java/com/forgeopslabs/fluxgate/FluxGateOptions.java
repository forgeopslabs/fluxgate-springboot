package com.forgeopslabs.fluxgate;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings for {@link DefaultFluxGateClient#create(FluxGateOptions)}: where the
 * edge is, how to authenticate, and the timeout, retry, cache and circuit
 * breaker behaviour. Defaults match the Spring Boot starter's.
 *
 * <pre>{@code
 * FluxGateOptions options = FluxGateOptions.builder()
 *         .baseUrl("https://edge.example.com")
 *         .sdkKey(System.getenv("FLUXGATE_SDK_KEY"))
 *         .build();
 * FluxGateClient client = DefaultFluxGateClient.create(options);
 * }</pre>
 */
public final class FluxGateOptions {

    private final String baseUrl;
    private final String sdkKey;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final int retryMaxAttempts;
    private final Duration retryInitialBackoff;
    private final boolean cacheEnabled;
    private final Duration cacheTtl;
    private final long cacheMaxSize;
    private final int circuitBreakerFailureThreshold;
    private final Duration circuitBreakerOpenDuration;

    private FluxGateOptions(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.sdkKey = builder.sdkKey;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.retryMaxAttempts = builder.retryMaxAttempts;
        this.retryInitialBackoff = builder.retryInitialBackoff;
        this.cacheEnabled = builder.cacheEnabled;
        this.cacheTtl = builder.cacheTtl;
        this.cacheMaxSize = builder.cacheMaxSize;
        this.circuitBreakerFailureThreshold = builder.circuitBreakerFailureThreshold;
        this.circuitBreakerOpenDuration = builder.circuitBreakerOpenDuration;
    }

    /**
     * @return a builder with the default settings and no base URL or SDK key
     */
    public static Builder builder() {
        return new Builder();
    }

    /** @return the edge base URL, without a trailing slash */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** @return the SDK key {@code <clientId>.<apiKey>} sent as a bearer token */
    public String getSdkKey() {
        return sdkKey;
    }

    /** @return the TCP connect timeout */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /** @return the timeout for one edge response */
    public Duration getReadTimeout() {
        return readTimeout;
    }

    /** @return total attempts per edge call, including the first */
    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    /** @return the pause before the first retry; doubles on each retry */
    public Duration getRetryInitialBackoff() {
        return retryInitialBackoff;
    }

    /** @return whether successful evaluations are cached */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    /** @return how long a cached evaluation is reused */
    public Duration getCacheTtl() {
        return cacheTtl;
    }

    /** @return the maximum number of cached evaluations and bulk snapshots */
    public long getCacheMaxSize() {
        return cacheMaxSize;
    }

    /** @return consecutive edge failures that open the circuit breaker; {@code 0} disables it */
    public int getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }

    /** @return how long the open breaker refuses edge calls before a trial call */
    public Duration getCircuitBreakerOpenDuration() {
        return circuitBreakerOpenDuration;
    }

    @Override
    public String toString() {
        return "FluxGateOptions{" +
                "baseUrl='" + baseUrl + '\'' +
                ", sdkKey='[REDACTED]'" +
                ", connectTimeout=" + connectTimeout +
                ", readTimeout=" + readTimeout +
                ", retryMaxAttempts=" + retryMaxAttempts +
                ", retryInitialBackoff=" + retryInitialBackoff +
                ", cacheEnabled=" + cacheEnabled +
                ", cacheTtl=" + cacheTtl +
                ", cacheMaxSize=" + cacheMaxSize +
                ", circuitBreakerFailureThreshold=" + circuitBreakerFailureThreshold +
                ", circuitBreakerOpenDuration=" + circuitBreakerOpenDuration +
                '}';
    }

    /**
     * Builder for {@link FluxGateOptions}. {@link #baseUrl(String)} and
     * {@link #sdkKey(String)} are required.
     */
    public static final class Builder {
        private String baseUrl;
        private String sdkKey;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(5);
        private int retryMaxAttempts = 3;
        private Duration retryInitialBackoff = Duration.ofMillis(100);
        private boolean cacheEnabled = true;
        private Duration cacheTtl = Duration.ofSeconds(30);
        private long cacheMaxSize = 1000;
        private int circuitBreakerFailureThreshold = 5;
        private Duration circuitBreakerOpenDuration = Duration.ofSeconds(30);

        private Builder() {
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder sdkKey(String sdkKey) {
            this.sdkKey = sdkKey;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder retryMaxAttempts(int retryMaxAttempts) {
            this.retryMaxAttempts = retryMaxAttempts;
            return this;
        }

        public Builder retryInitialBackoff(Duration retryInitialBackoff) {
            this.retryInitialBackoff = retryInitialBackoff;
            return this;
        }

        public Builder cacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
            return this;
        }

        public Builder cacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
            return this;
        }

        public Builder cacheMaxSize(long cacheMaxSize) {
            this.cacheMaxSize = cacheMaxSize;
            return this;
        }

        public Builder circuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
            this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
            return this;
        }

        public Builder circuitBreakerOpenDuration(Duration circuitBreakerOpenDuration) {
            this.circuitBreakerOpenDuration = circuitBreakerOpenDuration;
            return this;
        }

        /**
         * @return the options
         * @throws IllegalArgumentException when the base URL is not an
         *                                  http(s) URL, the SDK key is not
         *                                  {@code <clientId>.<apiKey>}, or a
         *                                  timeout or breaker setting is invalid
         */
        public FluxGateOptions build() {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl must be set to the FluxGate edge server URL");
            }
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                throw new IllegalArgumentException("baseUrl must be an http:// or https:// URL");
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            if (sdkKey == null || sdkKey.isBlank()) {
                throw new IllegalArgumentException("sdkKey must be set to the SDK key <clientId>.<apiKey>");
            }
            if (!isWellFormedSdkKey(sdkKey)) {
                // Never echo the key itself.
                throw new IllegalArgumentException("sdkKey must have the form <clientId>.<apiKey>");
            }
            requirePositive(connectTimeout, "connectTimeout");
            requirePositive(readTimeout, "readTimeout");
            if (circuitBreakerFailureThreshold < 0) {
                throw new IllegalArgumentException("circuitBreakerFailureThreshold must be 0 (disabled) or positive");
            }
            if (circuitBreakerFailureThreshold > 0) {
                requirePositive(circuitBreakerOpenDuration, "circuitBreakerOpenDuration");
            }
            Objects.requireNonNull(cacheTtl, "cacheTtl");
            return new FluxGateOptions(this);
        }

        private static boolean isWellFormedSdkKey(String key) {
            int dot = key.indexOf('.');
            return dot > 0 && dot < key.length() - 1;
        }

        private static void requirePositive(Duration duration, String name) {
            if (duration == null || duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }
    }
}
