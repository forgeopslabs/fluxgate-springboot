package com.fluxgate.starter.openfeature;

import com.fluxgate.starter.FlagEvaluation;
import com.fluxgate.starter.FluxGateClient;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * OpenFeature provider implementation for FluxGate Feature Toggle system.
 *
 * <p>
 * This provider enables integration with the OpenFeature Java SDK, allowing
 * applications to consume FluxGate feature flags through the standard
 * OpenFeature API. It delegates to {@link FluxGateClient}, which talks to the
 * edge over OFREP.
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
 * The edge decides the environment from the SDK key; the evaluation context
 * carries only the targeting key and attributes.
 *
 * @see FeatureProvider
 * @see FluxGateClient
 */
public class FluxGateProvider implements FeatureProvider {

    private static final Logger logger = LoggerFactory.getLogger(FluxGateProvider.class);
    private static final String PROVIDER_NAME = "FluxGate";

    private final FluxGateClient fluxGateClient;

    /**
     * Creates a new FluxGateProvider.
     *
     * @param fluxGateClient the FluxGate client to use for flag evaluation
     */
    public FluxGateProvider(FluxGateClient fluxGateClient) {
        if (fluxGateClient == null) {
            throw new IllegalArgumentException("FluxGateClient cannot be null");
        }
        this.fluxGateClient = fluxGateClient;
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
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue,
            EvaluationContext ctx) {
        return toProviderEvaluation(fluxGateClient.evaluate(key, defaultValue, Boolean.class, convertContext(ctx)));
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue,
            EvaluationContext ctx) {
        return toProviderEvaluation(fluxGateClient.evaluate(key, defaultValue, String.class, convertContext(ctx)));
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue,
            EvaluationContext ctx) {
        return toProviderEvaluation(fluxGateClient.evaluate(key, defaultValue, Integer.class, convertContext(ctx)));
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue,
            EvaluationContext ctx) {
        return toProviderEvaluation(fluxGateClient.evaluate(key, defaultValue, Double.class, convertContext(ctx)));
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue,
            EvaluationContext ctx) {
        FlagEvaluation<Object> evaluation = fluxGateClient.evaluate(key, null, Object.class, convertContext(ctx));
        Value value = evaluation.value() == null ? defaultValue : Value.objectToValue(evaluation.value());
        return toProviderEvaluation(new FlagEvaluation<>(evaluation.key(), value, evaluation.variant(),
                evaluation.reason(), evaluation.errorCode(), evaluation.errorMessage()));
    }

    /**
     * Converts the OpenFeature context to a FluxGate context.
     */
    private com.fluxgate.starter.EvaluationContext convertContext(EvaluationContext ctx) {
        if (ctx == null) {
            return com.fluxgate.starter.EvaluationContext.empty();
        }
        com.fluxgate.starter.EvaluationContext.Builder builder = com.fluxgate.starter.EvaluationContext.builder()
                .targetingKey(ctx.getTargetingKey());
        for (Map.Entry<String, Object> entry : ctx.asObjectMap().entrySet()) {
            if (!EvaluationContext.TARGETING_KEY.equals(entry.getKey())) {
                builder.attribute(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    private static <T> ProviderEvaluation<T> toProviderEvaluation(FlagEvaluation<T> evaluation) {
        ProviderEvaluation.ProviderEvaluationBuilder<T> builder = ProviderEvaluation.<T>builder()
                .value(evaluation.value())
                .variant(evaluation.variant())
                .reason(evaluation.reason());
        if (evaluation.isError()) {
            builder.errorCode(toErrorCode(evaluation.errorCode())).errorMessage(evaluation.errorMessage());
        }
        return builder.build();
    }

    private static ErrorCode toErrorCode(com.fluxgate.starter.ErrorCode code) {
        return switch (code) {
            case FLAG_NOT_FOUND -> ErrorCode.FLAG_NOT_FOUND;
            case TYPE_MISMATCH -> ErrorCode.TYPE_MISMATCH;
            case TARGETING_KEY_MISSING -> ErrorCode.TARGETING_KEY_MISSING;
            case INVALID_CONTEXT -> ErrorCode.INVALID_CONTEXT;
            case PROVIDER_NOT_READY -> ErrorCode.PROVIDER_NOT_READY;
            case UNAUTHORIZED, GENERAL -> ErrorCode.GENERAL;
        };
    }
}
