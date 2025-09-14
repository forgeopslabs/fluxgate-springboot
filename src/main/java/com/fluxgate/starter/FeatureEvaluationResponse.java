package com.fluxgate.starter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Response object for feature flag evaluation results from the FluxGate Edge Server.
 */
public class FeatureEvaluationResponse {

    @JsonProperty("enabled")
    private boolean enabled;

    public FeatureEvaluationResponse() {
    }

    public FeatureEvaluationResponse(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeatureEvaluationResponse that = (FeatureEvaluationResponse) o;
        return enabled == that.enabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled);
    }

    @Override
    public String toString() {
        return "FeatureEvaluationResponse{" +
                "enabled=" + enabled +
                '}';
    }
}