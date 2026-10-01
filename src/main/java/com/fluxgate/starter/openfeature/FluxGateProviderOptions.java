package com.fluxgate.starter.openfeature;

/**
 * Configuration options for the FluxGate OpenFeature provider.
 * 
 * <p>
 * This class provides configuration settings that control how the provider
 * maps OpenFeature concepts to FluxGate's API, particularly around environment
 * handling.
 * 
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * FluxGateProviderOptions options = FluxGateProviderOptions.builder()
 *         .defaultEnvironmentId("production")
 *         .environmentContextKey("env")
 *         .build();
 * 
 * FluxGateProvider provider = new FluxGateProvider(fluxGateClient, options);
 * }</pre>
 */
public class FluxGateProviderOptions {

    private static final String DEFAULT_ENVIRONMENT_CONTEXT_KEY = "environment";
    private static final String DEFAULT_ENVIRONMENT_ID = "default";

    private final String defaultEnvironmentId;
    private final String environmentContextKey;

    private FluxGateProviderOptions(Builder builder) {
        this.defaultEnvironmentId = builder.defaultEnvironmentId != null
                ? builder.defaultEnvironmentId
                : DEFAULT_ENVIRONMENT_ID;
        this.environmentContextKey = builder.environmentContextKey != null
                ? builder.environmentContextKey
                : DEFAULT_ENVIRONMENT_CONTEXT_KEY;
    }

    /**
     * Returns the default environment ID to use when no environment is specified
     * in the evaluation context.
     *
     * @return the default environment ID
     */
    public String getDefaultEnvironmentId() {
        return defaultEnvironmentId;
    }

    /**
     * Returns the context key used to extract the environment ID from the
     * OpenFeature evaluation context.
     *
     * @return the environment context key
     */
    public String getEnvironmentContextKey() {
        return environmentContextKey;
    }

    /**
     * Creates a new builder for FluxGateProviderOptions.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates default options with standard settings.
     *
     * @return default FluxGateProviderOptions
     */
    public static FluxGateProviderOptions defaults() {
        return builder().build();
    }

    /**
     * Builder for FluxGateProviderOptions.
     */
    public static class Builder {
        private String defaultEnvironmentId;
        private String environmentContextKey;

        private Builder() {
        }

        /**
         * Sets the default environment ID to use when no environment is specified
         * in the evaluation context.
         *
         * @param defaultEnvironmentId the default environment ID
         * @return this builder
         */
        public Builder defaultEnvironmentId(String defaultEnvironmentId) {
            this.defaultEnvironmentId = defaultEnvironmentId;
            return this;
        }

        /**
         * Sets the context key used to extract the environment ID from the
         * OpenFeature evaluation context.
         *
         * @param environmentContextKey the environment context key
         * @return this builder
         */
        public Builder environmentContextKey(String environmentContextKey) {
            this.environmentContextKey = environmentContextKey;
            return this;
        }

        /**
         * Builds the FluxGateProviderOptions instance.
         *
         * @return the built options
         */
        public FluxGateProviderOptions build() {
            return new FluxGateProviderOptions(this);
        }
    }

    @Override
    public String toString() {
        return "FluxGateProviderOptions{" +
                "defaultEnvironmentId='" + defaultEnvironmentId + '\'' +
                ", environmentContextKey='" + environmentContextKey + '\'' +
                '}';
    }
}
