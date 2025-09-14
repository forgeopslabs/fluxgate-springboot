package com.fluxgate.starter;

/**
 * Exception thrown when feature toggle operations fail.
 * This can include network errors, authentication failures, or server errors.
 */
public class FeatureToggleException extends RuntimeException {

    private final String featureKey;
    private final String environmentId;
    private final int statusCode;

    public FeatureToggleException(String message) {
        super(message);
        this.featureKey = null;
        this.environmentId = null;
        this.statusCode = -1;
    }

    public FeatureToggleException(String message, Throwable cause) {
        super(message, cause);
        this.featureKey = null;
        this.environmentId = null;
        this.statusCode = -1;
    }

    public FeatureToggleException(String message, String featureKey, String environmentId) {
        super(message);
        this.featureKey = featureKey;
        this.environmentId = environmentId;
        this.statusCode = -1;
    }

    public FeatureToggleException(String message, String featureKey, String environmentId, int statusCode) {
        super(message);
        this.featureKey = featureKey;
        this.environmentId = environmentId;
        this.statusCode = statusCode;
    }

    public FeatureToggleException(String message, Throwable cause, String featureKey, String environmentId) {
        super(message, cause);
        this.featureKey = featureKey;
        this.environmentId = environmentId;
        this.statusCode = -1;
    }

    public FeatureToggleException(String message, Throwable cause, String featureKey, String environmentId, int statusCode) {
        super(message, cause);
        this.featureKey = featureKey;
        this.environmentId = environmentId;
        this.statusCode = statusCode;
    }

    public String getFeatureKey() {
        return featureKey;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean hasStatusCode() {
        return statusCode > 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FeatureToggleException{");
        sb.append("message='").append(getMessage()).append('\'');
        if (featureKey != null) {
            sb.append(", featureKey='").append(featureKey).append('\'');
        }
        if (environmentId != null) {
            sb.append(", environmentId='").append(environmentId).append('\'');
        }
        if (hasStatusCode()) {
            sb.append(", statusCode=").append(statusCode);
        }
        sb.append('}');
        return sb.toString();
    }
}