package com.fluxgate.starter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Request object for feature flag evaluation containing all necessary
 * parameters
 * to communicate with the FluxGate Edge Server.
 */
public record FeatureEvaluationRequest(
        @JsonProperty("feature_key") String featureKey,
        @JsonProperty("environment_id") String environmentId,
        @JsonProperty("context") List<FeatureContext> context,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("client_secret") String clientSecret) {

    public FeatureEvaluationRequest {
        // Ensure context is never null - use empty list instead
        context = context != null ? context : List.of();
    }

    public FeatureEvaluationRequest(String featureKey, String environmentId) {
        this(featureKey, environmentId, null, null, null);
    }

    public FeatureEvaluationRequest(String featureKey, String environmentId, List<FeatureContext> context) {
        this(featureKey, environmentId, context, null, null);
    }

    /**
     * Creates a builder for constructing FeatureEvaluationRequest instances.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a simple request with just feature key and environment ID.
     *
     * @param featureKey    the feature key
     * @param environmentId the environment ID
     * @return a new request instance
     */
    public static FeatureEvaluationRequest of(String featureKey, String environmentId) {
        return new FeatureEvaluationRequest(featureKey, environmentId);
    }

    /**
     * Creates a request with feature key, environment ID, and context.
     *
     * @param featureKey    the feature key
     * @param environmentId the environment ID
     * @param context       the evaluation context as a map
     * @return a new request instance
     */
    public static FeatureEvaluationRequest of(String featureKey, String environmentId, Map<String, String> context) {
        List<FeatureContext> contextList = context.entrySet().stream()
                .map(entry -> new FeatureContext(entry.getKey(), entry.getValue()))
                .toList();
        return new FeatureEvaluationRequest(featureKey, environmentId, contextList);
    }

    /**
     * Override toString to redact the client secret for security.
     */
    @Override
    public String toString() {
        return "FeatureEvaluationRequest{" +
                "featureKey='" + featureKey + '\'' +
                ", environmentId='" + environmentId + '\'' +
                ", context=" + context +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='[REDACTED]'" +
                '}';
    }

    /**
     * Builder class for creating FeatureEvaluationRequest instances.
     */
    public static class Builder {
        private String featureKey;
        private String environmentId;
        private List<FeatureContext> context;
        private String clientId;
        private String clientSecret;

        private Builder() {
        }

        public Builder featureKey(String featureKey) {
            this.featureKey = featureKey;
            return this;
        }

        public Builder environmentId(String environmentId) {
            this.environmentId = environmentId;
            return this;
        }

        public Builder context(List<FeatureContext> context) {
            this.context = context;
            return this;
        }

        public Builder context(Map<String, String> context) {
            this.context = context.entrySet().stream()
                    .map(entry -> new FeatureContext(entry.getKey(), entry.getValue()))
                    .toList();
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public FeatureEvaluationRequest build() {
            return new FeatureEvaluationRequest(featureKey, environmentId, context, clientId, clientSecret);
        }
    }
}