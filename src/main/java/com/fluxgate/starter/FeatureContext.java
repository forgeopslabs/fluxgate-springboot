package com.fluxgate.starter;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Context object representing a key-value pair used in feature flag evaluation.
 * This typically contains user attributes, environment variables, or other
 * contextual information that affects feature flag behavior.
 */
public record FeatureContext(@JsonProperty("key") String key,
                @JsonProperty("value") String value) {

}