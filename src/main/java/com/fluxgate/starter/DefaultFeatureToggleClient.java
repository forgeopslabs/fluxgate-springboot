package com.fluxgate.starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Default implementation of FeatureToggleClient using Spring's RestTemplate
 * to communicate with the FluxGate Edge Server.
 */
public class DefaultFeatureToggleClient implements FluxGateClient {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFeatureToggleClient.class);
    private static final String EVALUATE_ENDPOINT = "/evaluate";

    private final RestTemplate restTemplate;
    private final FeatureToggleProperties properties;
    private final Executor asyncExecutor;

    public DefaultFeatureToggleClient(RestTemplate restTemplate,
            FeatureToggleProperties properties,
            Executor asyncExecutor) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public boolean isEnabled(String featureKey, String environmentId) {
        return isEnabled(FeatureEvaluationRequest.of(featureKey, environmentId));
    }

    @Override
    public boolean isEnabled(String featureKey, String environmentId, Map<String, String> context) {
        return isEnabled(FeatureEvaluationRequest.of(featureKey, environmentId, context));
    }

    @Override
    @Retryable(retryFor = { ResourceAccessException.class, HttpServerErrorException.class }, noRetryFor = {
            HttpClientErrorException.BadRequest.class, HttpClientErrorException.Unauthorized.class,
            HttpClientErrorException.Forbidden.class,
            HttpClientErrorException.NotFound.class }, maxAttemptsExpression = "#{@featureToggleProperties.retryAttempts}", backoff = @Backoff(delayExpression = "#{@featureToggleProperties.retryDelay.toMillis()}", maxDelayExpression = "#{@featureToggleProperties.maxRetryDelay.toMillis()}", multiplierExpression = "#{@featureToggleProperties.retryMultiplier}"))
    public boolean isEnabled(FeatureEvaluationRequest request) {
        validateRequest(request);

        long startTime = System.currentTimeMillis();
        String featureKey = request.featureKey();
        String environmentId = request.environmentId();

        try {
            logger.debug("Evaluating feature flag: feature={}, environment={}, context={}",
                    featureKey, environmentId, request.context());

            // Enrich request with default credentials if not provided
            FeatureEvaluationRequest enrichedRequest = enrichRequest(request);

            // Make HTTP request to edge server
            String url = properties.getBaseUrl() + EVALUATE_ENDPOINT;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<FeatureEvaluationRequest> entity = new HttpEntity<>(enrichedRequest, headers);

            ResponseEntity<FeatureEvaluationResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, FeatureEvaluationResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                boolean result = response.getBody().isEnabled();
                long duration = System.currentTimeMillis() - startTime;

                logger.debug("Feature evaluation successful: feature={}, environment={}, result={}, duration={}ms",
                        featureKey, environmentId, result, duration);

                return result;
            } else {
                throw new FeatureToggleException(
                        "Invalid response from edge server: " + response.getStatusCode(),
                        featureKey, environmentId, response.getStatusCode().value());
            }

        } catch (HttpClientErrorException e) {
            throw new FeatureToggleException(
                    "Client error from edge server: " + e.getMessage(),
                    e, featureKey, environmentId, e.getStatusCode().value());

        } catch (HttpServerErrorException e) {

            throw new FeatureToggleException(
                    "Server error from edge server: " + e.getMessage(),
                    e, featureKey, environmentId, e.getStatusCode().value());

        } catch (ResourceAccessException e) {
            throw new FeatureToggleException(
                    "Network error connecting to edge server: " + e.getMessage(),
                    e, featureKey, environmentId);

        } catch (Exception e) {
            throw new FeatureToggleException(
                    "Unexpected error evaluating feature flag: " + e.getMessage(),
                    e, featureKey, environmentId);
        }
    }

    @Override
    public CompletableFuture<Boolean> isEnabledAsync(String featureKey, String environmentId) {
        return CompletableFuture.supplyAsync(() -> isEnabled(featureKey, environmentId), asyncExecutor);
    }

    @Override
    public CompletableFuture<Boolean> isEnabledAsync(String featureKey, String environmentId,
            Map<String, String> context) {
        return CompletableFuture.supplyAsync(() -> isEnabled(featureKey, environmentId, context), asyncExecutor);
    }

    @Override
    public CompletableFuture<Boolean> isEnabledAsync(FeatureEvaluationRequest request) {
        return CompletableFuture.supplyAsync(() -> isEnabled(request), asyncExecutor);
    }

    @Override
    public boolean isEnabledWithFallback(String featureKey, String environmentId, boolean fallback) {
        return isEnabledWithFallback(FeatureEvaluationRequest.of(featureKey, environmentId), fallback);
    }

    @Override
    public boolean isEnabledWithFallback(String featureKey, String environmentId, Map<String, String> context,
            boolean fallback) {
        return isEnabledWithFallback(FeatureEvaluationRequest.of(featureKey, environmentId, context), fallback);
    }

    @Override
    public boolean isEnabledWithFallback(FeatureEvaluationRequest request, boolean fallback) {
        try {
            return isEnabled(request);
        } catch (FeatureToggleException e) {
            if (properties.isFallbackEnabled()) {
                logger.debug("Using fallback value {} for feature={}, environment={} due to error: {}",
                        fallback, request.featureKey(), request.environmentId(), e.getMessage());
                return fallback;
            } else {
                // Re-throw if fallback is disabled
                throw e;
            }
        }
    }

    @Override
    public void execute(FeatureEvaluationRequest request, Consumer<Void> consumer) {
        validateRequest(request);
        try {
            if (isEnabled(request)) {
                consumer.accept(null);
            }
        } catch (FeatureToggleException e) {
            logger.error("Error executing feature evaluation request: {}", e.getMessage());
        }
    }

    @Override
    public <T> T executeAndReturn(FeatureEvaluationRequest request, Supplier<T> supplier) {
        validateRequest(request);
        if (supplier == null) {
            throw new IllegalArgumentException("Supplier cannot be null");
        }

        try {
            if (isEnabled(request)) {
                logger.debug("Feature is enabled, executing supplier for feature={}, environment={}",
                        request.featureKey(), request.environmentId());
                return supplier.get();
            } else {
                logger.debug("Feature is disabled, returning null for feature={}, environment={}",
                        request.featureKey(), request.environmentId());
                return null;
            }
        } catch (FeatureToggleException e) {
            logger.error("Error executing feature evaluation request: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public <T> T executeAndReturnWithFallback(FeatureEvaluationRequest request, Supplier<T> supplier, T fallback) {
        validateRequest(request);
        if (supplier == null) {
            throw new IllegalArgumentException("Supplier cannot be null");
        }

        try {
            if (isEnabled(request)) {
                logger.debug("Feature is enabled, executing supplier for feature={}, environment={}",
                        request.featureKey(), request.environmentId());
                return supplier.get();
            } else {
                logger.debug("Feature is disabled, returning fallback for feature={}, environment={}",
                        request.featureKey(), request.environmentId());
                return fallback;
            }
        } catch (FeatureToggleException e) {
            if (properties.isFallbackEnabled()) {
                logger.debug("Using fallback value for feature={}, environment={} due to error: {}",
                        request.featureKey(), request.environmentId(), e.getMessage());
                return fallback;
            } else {
                logger.error("Error executing feature evaluation request and fallback is disabled: {}", e.getMessage());
                throw e;
            }
        }
    }

    private void validateRequest(FeatureEvaluationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("FeatureEvaluationRequest cannot be null");
        }
        if (!StringUtils.hasText(request.featureKey())) {
            throw new IllegalArgumentException("Feature key cannot be null or empty");
        }
        if (!StringUtils.hasText(request.environmentId())) {
            throw new IllegalArgumentException("Environment ID cannot be null or empty");
        }
    }

    private FeatureEvaluationRequest enrichRequest(FeatureEvaluationRequest request) {
        // If request already has credentials, return as-is
        if (StringUtils.hasText(request.clientId()) && StringUtils.hasText(request.clientSecret())) {
            return request;
        }

        // If global credentials are configured, use them
        if (StringUtils.hasText(properties.getClientId()) && StringUtils.hasText(properties.getClientSecret())) {
            FeatureEvaluationRequest enriched = FeatureEvaluationRequest.builder()
                    .featureKey(request.featureKey())
                    .environmentId(request.environmentId())
                    .context(request.context())
                    .clientId(properties.getClientId())
                    .clientSecret(properties.getClientSecret())
                    .build();

            logger.debug("Enriched request with global client credentials");
            return enriched;
        }

        // Return original request if no global credentials
        return request;
    }

    /**
     * Health check method to verify connectivity to the edge server.
     * This method is called by the health indicator.
     * 
     * @return true if the edge server is reachable, false otherwise
     */
    public boolean isHealthy() {
        try {
            String url = properties.getBaseUrl() + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.debug("Health check failed: {}", e.getMessage());
            return false;
        }
    }
}