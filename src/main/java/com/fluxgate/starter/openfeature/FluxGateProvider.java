package com.fluxgate.starter.openfeature;

import com.fluxgate.starter.FeatureEvaluationRequest;
import com.fluxgate.starter.FeatureToggleException;
import com.fluxgate.starter.FluxGateClient;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenFeature provider implementation for FluxGate Feature Toggle system.
 * 
 * <p>
 * This provider enables integration with the OpenFeature Java SDK, allowing
 * applications to consume FluxGate feature flags through the standard
 * OpenFeature
 * API. It wraps the existing {@link FluxGateClient} and adapts it to the
 * {@link FeatureProvider} interface.
 * 
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * FluxGateClient client = ...; // Obtain from Spring context
 * FluxGateProvider provider = new FluxGateProvider(client);
 * 
 * OpenFeatureAPI api = OpenFeatureAPI.getInstance();
 * api.setProviderAndWait(provider);
 * 
 * Client openFeatureClient = api.getClient();
 * boolean enabled = openFeatureClient.getBooleanValue("my-feature", false);
 * }</pre>
 * 
 * <p>
 * <b>Environment Handling:</b> Since OpenFeature doesn't have a built-in
 * concept
 * of "environment", this provider extracts the environment ID from the
 * {@link EvaluationContext}. Configure the context key using
 * {@link FluxGateProviderOptions#getEnvironmentContextKey()}.
 * 
 * <p>
 * <b>Non-Boolean Flags:</b> The FluxGate API returns boolean-only flag values.
 * For non-boolean evaluations (String, Integer, Double, Object), this provider
 * converts the boolean result to an appropriate type representation.
 *
 * @see FeatureProvider
 * @see FluxGateClient
 * @see FluxGateProviderOptions
 */
public class FluxGateProvider implements FeatureProvider {

    private static final Logger logger = LoggerFactory.getLogger(FluxGateProvider.class);
    private static final String PROVIDER_NAME = "FluxGate";

    private final FluxGateClient fluxGateClient;
    private final FluxGateProviderOptions options;

    /**
     * Creates a new FluxGateProvider with default options.
     *
     * @param fluxGateClient the FluxGate client to use for flag evaluation
     */
    public FluxGateProvider(FluxGateClient fluxGateClient) {
        this(fluxGateClient, FluxGateProviderOptions.defaults());
    }

    /**
     * Creates a new FluxGateProvider with custom options.
     *
     * @param fluxGateClient the FluxGate client to use for flag evaluation
     * @param options        configuration options for the provider
     */
    public FluxGateProvider(FluxGateClient fluxGateClient, FluxGateProviderOptions options) {
        if (fluxGateClient == null) {
            throw new IllegalArgumentException("FluxGateClient cannot be null");
        }
        if (options == null) {
            throw new IllegalArgumentException("FluxGateProviderOptions cannot be null");
        }
        this.fluxGateClient = fluxGateClient;
        this.options = options;
        logger.info("FluxGateProvider initialized with options: {}", options);
    }

    @Override
    public Metadata getMetadata() {
        return () -> PROVIDER_NAME;
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        logger.info("Initializing FluxGateProvider");
        // Verify connectivity by checking health
        if (!fluxGateClient.isHealthy()) {
            logger.warn("FluxGate Edge Server is not healthy during initialization");
        }
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down FluxGateProvider");
        // No explicit cleanup needed - the underlying RestTemplate/client will be
        // managed by Spring
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue,
            EvaluationContext ctx) {
        try {
            String environmentId = extractEnvironmentId(ctx);
            Map<String, String> context = convertContext(ctx);

            logger.debug("Evaluating boolean flag: key={}, environment={}", key, environmentId);

            FeatureEvaluationRequest request = FeatureEvaluationRequest.builder()
                    .featureKey(key)
                    .environmentId(environmentId)
                    .context(context)
                    .build();

            boolean result = fluxGateClient.isEnabled(request);

            return ProviderEvaluation.<Boolean>builder()
                    .value(result)
                    .reason(Reason.TARGETING_MATCH.toString())
                    .build();

        } catch (FeatureToggleException e) {
            return handleError(key, defaultValue, e);
        }
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue,
            EvaluationContext ctx) {
        try {
            String environmentId = extractEnvironmentId(ctx);
            Map<String, String> context = convertContext(ctx);

            logger.debug("Evaluating string flag: key={}, environment={}", key, environmentId);

            FeatureEvaluationRequest request = FeatureEvaluationRequest.builder()
                    .featureKey(key)
                    .environmentId(environmentId)
                    .context(context)
                    .build();

            boolean result = fluxGateClient.isEnabled(request);

            // Convert boolean to string representation
            return ProviderEvaluation.<String>builder()
                    .value(String.valueOf(result))
                    .reason(Reason.TARGETING_MATCH.toString())
                    .build();

        } catch (FeatureToggleException e) {
            return handleError(key, defaultValue, e);
        }
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue,
            EvaluationContext ctx) {
        try {
            String environmentId = extractEnvironmentId(ctx);
            Map<String, String> context = convertContext(ctx);

            logger.debug("Evaluating integer flag: key={}, environment={}", key, environmentId);

            FeatureEvaluationRequest request = FeatureEvaluationRequest.builder()
                    .featureKey(key)
                    .environmentId(environmentId)
                    .context(context)
                    .build();

            boolean result = fluxGateClient.isEnabled(request);

            // Convert boolean to integer representation (1 for true, 0 for false)
            return ProviderEvaluation.<Integer>builder()
                    .value(result ? 1 : 0)
                    .reason(Reason.TARGETING_MATCH.toString())
                    .build();

        } catch (FeatureToggleException e) {
            return handleError(key, defaultValue, e);
        }
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue,
            EvaluationContext ctx) {
        try {
            String environmentId = extractEnvironmentId(ctx);
            Map<String, String> context = convertContext(ctx);

            logger.debug("Evaluating double flag: key={}, environment={}", key, environmentId);

            FeatureEvaluationRequest request = FeatureEvaluationRequest.builder()
                    .featureKey(key)
                    .environmentId(environmentId)
                    .context(context)
                    .build();

            boolean result = fluxGateClient.isEnabled(request);

            // Convert boolean to double representation (1.0 for true, 0.0 for false)
            return ProviderEvaluation.<Double>builder()
                    .value(result ? 1.0 : 0.0)
                    .reason(Reason.TARGETING_MATCH.toString())
                    .build();

        } catch (FeatureToggleException e) {
            return handleError(key, defaultValue, e);
        }
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue,
            EvaluationContext ctx) {
        try {
            String environmentId = extractEnvironmentId(ctx);
            Map<String, String> context = convertContext(ctx);

            logger.debug("Evaluating object flag: key={}, environment={}", key, environmentId);

            FeatureEvaluationRequest request = FeatureEvaluationRequest.builder()
                    .featureKey(key)
                    .environmentId(environmentId)
                    .context(context)
                    .build();

            boolean result = fluxGateClient.isEnabled(request);

            // Convert boolean to Value representation
            return ProviderEvaluation.<Value>builder()
                    .value(new Value(result))
                    .reason(Reason.TARGETING_MATCH.toString())
                    .build();

        } catch (FeatureToggleException e) {
            return handleError(key, defaultValue, e);
        }
    }

    /**
     * Extracts the environment ID from the evaluation context.
     * Falls back to the default environment ID if not found.
     */
    private String extractEnvironmentId(EvaluationContext ctx) {
        if (ctx == null) {
            return options.getDefaultEnvironmentId();
        }

        Value envValue = ctx.getValue(options.getEnvironmentContextKey());
        if (envValue != null && envValue.isString()) {
            return envValue.asString();
        }

        return options.getDefaultEnvironmentId();
    }

    /**
     * Converts OpenFeature EvaluationContext to FluxGate's context Map.
     */
    private Map<String, String> convertContext(EvaluationContext ctx) {
        if (ctx == null) {
            return new HashMap<>();
        }

        Map<String, String> result = new HashMap<>();

        // Add targeting key if present
        String targetingKey = ctx.getTargetingKey();
        if (targetingKey != null && !targetingKey.isEmpty()) {
            result.put("targetingKey", targetingKey);
        }

        // Convert all context attributes to string values
        Map<String, Value> attributes = ctx.asMap();
        if (attributes != null) {
            for (Map.Entry<String, Value> entry : attributes.entrySet()) {
                String key = entry.getKey();
                Value value = entry.getValue();

                // Skip the environment key as it's handled separately
                if (key.equals(options.getEnvironmentContextKey())) {
                    continue;
                }

                if (value != null) {
                    result.put(key, valueToString(value));
                }
            }
        }

        return result;
    }

    /**
     * Converts an OpenFeature Value to a String representation.
     */
    private String valueToString(Value value) {
        if (value.isNull()) {
            return "";
        } else if (value.isBoolean()) {
            return String.valueOf(value.asBoolean());
        } else if (value.isNumber()) {
            // Handle both integer and double
            if (value.asDouble() == Math.floor(value.asDouble())) {
                return String.valueOf(value.asInteger());
            }
            return String.valueOf(value.asDouble());
        } else if (value.isString()) {
            return value.asString();
        } else {
            // For complex types (Structure, List), convert to string representation
            return value.asObject().toString();
        }
    }

    /**
     * Handles errors during flag evaluation and creates an appropriate
     * ProviderEvaluation.
     */
    private <T> ProviderEvaluation<T> handleError(String key, T defaultValue,
            FeatureToggleException e) {
        logger.warn("Error evaluating flag '{}': {}", key, e.getMessage());

        ErrorCode errorCode;
        if (e.getStatusCode() == 404) {
            errorCode = ErrorCode.FLAG_NOT_FOUND;
        } else if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
            errorCode = ErrorCode.PROVIDER_NOT_READY;
        } else {
            errorCode = ErrorCode.GENERAL;
        }

        return ProviderEvaluation.<T>builder()
                .value(defaultValue)
                .errorCode(errorCode)
                .errorMessage(e.getMessage())
                .reason(Reason.ERROR.toString())
                .build();
    }
}
