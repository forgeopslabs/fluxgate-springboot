package com.fluxgate.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.Duration;

/**
 * Configuration properties for the FluxGate starter, bound from the
 * {@code fluxgate} prefix:
 *
 * <pre>
 * fluxgate:
 *   base-url: https://edge.example.com   # required
 *   sdk-key: client-id.api-key           # required
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
 *   health:
 *     enabled: true
 *   metrics:
 *     enabled: true
 * </pre>
 *
 * <p>
 * When the starter is enabled, binding fails fast unless {@code base-url} and
 * {@code sdk-key} are set: the class validates itself (Spring Boot applies a
 * {@link Validator} implemented by a {@code @ConfigurationProperties} bean), so
 * no Bean Validation provider is needed.
 */
@ConfigurationProperties(prefix = "fluxgate")
public class FeatureToggleProperties implements Validator {

    /**
     * Whether the FluxGate starter is enabled.
     */
    private boolean enabled = true;

    /**
     * Base URL of the FluxGate edge server, for example
     * https://edge.example.com. Required.
     */
    private String baseUrl;

    /**
     * SDK key in the form clientId.apiKey, sent as a Bearer token in the
     * Authorization header. Required.
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

    private final Health health = new Health();

    private final Metrics metrics = new Metrics();

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

    public Polling getPolling() {
        return polling;
    }

    public OpenFeature getOpenfeature() {
        return openfeature;
    }

    public Health getHealth() {
        return health;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    @Override
    public boolean supports(@NonNull Class<?> clazz) {
        return FeatureToggleProperties.class.isAssignableFrom(clazz);
    }

    /**
     * Rejects a missing or malformed {@code base-url} or {@code sdk-key} while
     * the starter is enabled.
     */
    @Override
    public void validate(@NonNull Object target, @NonNull Errors errors) {
        FeatureToggleProperties properties = (FeatureToggleProperties) target;
        if (!properties.isEnabled()) {
            return;
        }
        String url = properties.getBaseUrl();
        if (!StringUtils.hasText(url)) {
            errors.rejectValue("baseUrl", "fluxgate.base-url.missing",
                    "fluxgate.base-url must be set to the FluxGate edge server URL, for example "
                            + "https://edge.example.com (or set fluxgate.enabled=false)");
        } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
            errors.rejectValue("baseUrl", "fluxgate.base-url.invalid",
                    "fluxgate.base-url must be an http:// or https:// URL");
        }
        String key = properties.getSdkKey();
        if (!StringUtils.hasText(key)) {
            errors.rejectValue("sdkKey", "fluxgate.sdk-key.missing",
                    "fluxgate.sdk-key must be set to the SDK key in the form <clientId>.<apiKey> "
                            + "(or set fluxgate.enabled=false)");
        } else if (!isWellFormedSdkKey(key)) {
            // Never echo the key itself: the failure analyzer prints the rejected value.
            errors.reject("fluxgate.sdk-key.invalid",
                    "fluxgate.sdk-key must have the form <clientId>.<apiKey>");
        }
    }

    private static boolean isWellFormedSdkKey(String key) {
        int dot = key.indexOf('.');
        return dot > 0 && dot < key.length() - 1;
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
                ", health=" + health +
                ", metrics=" + metrics +
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
         * conditional bulk evaluation. 0 disables polling, which also
         * disables recovery from the provider's ERROR state. Also the minimum
         * age before the health indicator re-probes /health (30s when 0).
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
         * Whether to register the FluxGate provider with OpenFeatureAPI
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

    /**
     * Actuator health indicator settings.
     */
    public static class Health {

        /**
         * Whether to contribute the fluxgate health indicator when
         * Actuator is on the classpath. It reports the last edge outcome and a
         * cached /health probe; it never calls the edge on every health
         * request.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return "{enabled=" + enabled + '}';
        }
    }

    /**
     * Micrometer metrics settings.
     */
    public static class Metrics {

        /**
         * Whether to record the fluxgate.evaluation timer and
         * fluxgate.cache counter when Micrometer and a
         * MeterRegistry are available.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return "{enabled=" + enabled + '}';
        }
    }
}
