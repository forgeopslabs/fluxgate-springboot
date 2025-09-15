package com.fluxgate.starter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Client interface for interacting with the FluxGate Feature Toggle Edge
 * Server.
 * Provides methods to evaluate feature flags with various configuration
 * options.
 */
public interface FluxGateClient {

    /**
     * Evaluates whether a feature is enabled for the given feature key and
     * environment.
     *
     * @param featureKey    the unique identifier for the feature flag
     * @param environmentId the environment identifier (e.g., "prod", "staging",
     *                      "dev")
     * @return true if the feature is enabled, false otherwise
     * @throws FeatureToggleException if the evaluation fails
     */
    boolean isEnabled(String featureKey, String environmentId);

    /**
     * Evaluates whether a feature is enabled for the given feature key,
     * environment, and context.
     *
     * @param featureKey    the unique identifier for the feature flag
     * @param environmentId the environment identifier (e.g., "prod", "staging",
     *                      "dev")
     * @param context       additional context for feature evaluation (user
     *                      attributes, etc.)
     * @return true if the feature is enabled, false otherwise
     * @throws FeatureToggleException if the evaluation fails
     */
    boolean isEnabled(String featureKey, String environmentId, Map<String, String> context);

    /**
     * Evaluates whether a feature is enabled using a detailed request object.
     *
     * @param request the feature evaluation request containing all necessary
     *                parameters
     * @return true if the feature is enabled, false otherwise
     * @throws FeatureToggleException if the evaluation fails
     */
    boolean isEnabled(FeatureEvaluationRequest request);

    /**
     * Asynchronously evaluates whether a feature is enabled for the given feature
     * key and environment.
     *
     * @param featureKey    the unique identifier for the feature flag
     * @param environmentId the environment identifier (e.g., "prod", "staging",
     *                      "dev")
     * @return a CompletableFuture that resolves to true if the feature is enabled,
     *         false otherwise
     */
    CompletableFuture<Boolean> isEnabledAsync(String featureKey, String environmentId);

    /**
     * Asynchronously evaluates whether a feature is enabled for the given feature
     * key, environment, and context.
     *
     * @param featureKey    the unique identifier for the feature flag
     * @param environmentId the environment identifier (e.g., "prod", "staging",
     *                      "dev")
     * @param context       additional context for feature evaluation (user
     *                      attributes, etc.)
     * @return a CompletableFuture that resolves to true if the feature is enabled,
     *         false otherwise
     */
    CompletableFuture<Boolean> isEnabledAsync(String featureKey, String environmentId, Map<String, String> context);

    /**
     * Asynchronously evaluates whether a feature is enabled using a detailed
     * request object.
     *
     * @param request the feature evaluation request containing all necessary
     *                parameters
     * @return a CompletableFuture that resolves to true if the feature is enabled,
     *         false otherwise
     */
    CompletableFuture<Boolean> isEnabledAsync(FeatureEvaluationRequest request);

    /**
     * Evaluates a feature with a fallback value if the evaluation fails.
     * This method never throws exceptions and returns the fallback value on any
     * error.
     *
     * @param featureKey    the unique identifier for the feature flag
     * @param environmentId the environment identifier (e.g., "prod", "staging",
     *                      "dev")
     * @param fallback      the value to return if evaluation fails
     * @return the evaluation result or the fallback value
     */
    boolean isEnabledWithFallback(String featureKey, String environmentId, boolean fallback);

    /**
     * Evaluates a feature with a fallback value if the evaluation fails.
     * This method never throws exceptions and returns the fallback value on any
     * error.
     *
     * @param featureKey    the unique identifier for the feature flag
     * @param environmentId the environment identifier (e.g., "prod", "staging",
     *                      "dev")
     * @param context       additional context for feature evaluation (user
     *                      attributes, etc.)
     * @param fallback      the value to return if evaluation fails
     * @return the evaluation result or the fallback value
     */
    boolean isEnabledWithFallback(String featureKey, String environmentId, Map<String, String> context,
            boolean fallback);

    /**
     * Evaluates a feature with a fallback value if the evaluation fails.
     * This method never throws exceptions and returns the fallback value on any
     * error.
     *
     * @param request  the feature evaluation request containing all necessary
     *                 parameters
     * @param fallback the value to return if evaluation fails
     * @return the evaluation result or the fallback value
     */
    boolean isEnabledWithFallback(FeatureEvaluationRequest request, boolean fallback);

    /**
     * Executes a feature evaluation request and invoke the provided consumer
     * depending on the evaluation result.
     *
     * @param request  the feature evaluation request containing all necessary
     *                 parameters
     * @param consumer a consumer that processes the evaluation result
     */
    void execute(FeatureEvaluationRequest request, Consumer<Void> consumer);

    /**
     * Executes a feature evaluation request and invokes the provided supplier
     * if the feature is enabled, returning the supplier's result.
     * If the feature is disabled, returns null.
     *
     * @param <T>      the type of the result returned by the supplier
     * @param request  the feature evaluation request containing all necessary
     *                 parameters
     * @param supplier a supplier that produces the result when the feature is
     *                 enabled
     * @return the result from the supplier if the feature is enabled, null
     *         otherwise
     * @throws FeatureToggleException if the evaluation fails
     */
    <T> T executeAndReturn(FeatureEvaluationRequest request, Supplier<T> supplier);

    /**
     * Executes a feature evaluation request and invokes the provided supplier
     * if the feature is enabled, returning the supplier's result.
     * If the feature is disabled or evaluation fails, returns the fallback value.
     *
     * @param <T>      the type of the result returned by the supplier and fallback
     * @param request  the feature evaluation request containing all necessary
     *                 parameters
     * @param supplier a supplier that produces the result when the feature is
     *                 enabled
     * @param fallback the value to return if the feature is disabled or evaluation
     *                 fails
     * @return the result from the supplier if the feature is enabled, fallback
     *         otherwise
     */
    <T> T executeAndReturnWithFallback(FeatureEvaluationRequest request, Supplier<T> supplier, T fallback);

    /**
     * Checks the health of the FluxGate Edge Server.
     * This method can be used by monitoring systems and health checks.
     *
     * @return true if the edge server is healthy and reachable, false otherwise
     */
    boolean isHealthy();

}