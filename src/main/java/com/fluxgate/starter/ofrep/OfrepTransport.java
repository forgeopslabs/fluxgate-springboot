package com.fluxgate.starter.ofrep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxgate.starter.ErrorCode;
import com.fluxgate.starter.EvaluationContext;
import com.fluxgate.starter.FeatureToggleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP transport for the edge's OFREP endpoints.
 *
 * <ul>
 * <li>{@code POST /ofrep/v1/evaluate/flags/{key}} - single flag</li>
 * <li>{@code POST /ofrep/v1/evaluate/flags} - all flags, with
 * {@code ETag}/{@code If-None-Match}</li>
 * <li>{@code GET /health} - edge readiness</li>
 * </ul>
 *
 * Evaluation calls are retried by the {@link RetryExecutor}; failures surface
 * as {@link OfrepException} (mapped responses) or
 * {@link org.springframework.web.client.ResourceAccessException} (I/O).
 */
public class OfrepTransport {

    static final String FLAG_PATH = "/ofrep/v1/evaluate/flags/{key}";
    static final String BULK_PATH = "/ofrep/v1/evaluate/flags";
    static final String HEALTH_PATH = "/health";

    private static final Logger logger = LoggerFactory.getLogger(OfrepTransport.class);
    private static final int OK = 200;
    private static final int NOT_MODIFIED = 304;

    private final RestClient restClient;
    private final RetryExecutor retry;
    private final ObjectMapper mapper;
    private final OfrepResponseParser parser;

    /**
     * @param restClient client with the edge base URL, timeouts and
     *                   {@code Authorization} header configured
     * @param retry      retry policy for evaluation calls
     * @param mapper     JSON mapper for request and response bodies
     */
    public OfrepTransport(RestClient restClient, RetryExecutor retry, ObjectMapper mapper) {
        this.restClient = restClient;
        this.retry = retry;
        this.mapper = mapper;
        this.parser = new OfrepResponseParser(mapper);
    }

    /**
     * Creates a transport from starter properties.
     *
     * @param properties the starter properties
     * @return a configured transport
     */
    public static OfrepTransport create(FeatureToggleProperties properties) {
        FeatureToggleProperties.Retry retryProperties = properties.getRetry();
        return new OfrepTransport(
                OfrepRestClientFactory.create(properties),
                new RetryExecutor(retryProperties.getMaxAttempts(), retryProperties.getInitialBackoff()),
                new ObjectMapper());
    }

    /**
     * Evaluates one flag.
     *
     * @param key     the flag key
     * @param context the evaluation context
     * @return the edge's success object
     */
    public OfrepFlagResult evaluate(String key, EvaluationContext context) {
        byte[] body = requestBody(context);
        return retry.execute(() -> restClient.post()
                .uri(FLAG_PATH, key)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    byte[] responseBody = response.getBody().readAllBytes();
                    if (status != OK) {
                        throw parser.error(status, responseBody);
                    }
                    return parser.parseFlag(responseBody, key);
                }));
    }

    /**
     * Evaluates all flags.
     *
     * @param context     the evaluation context
     * @param ifNoneMatch the previous version to revalidate, or {@code null}
     * @return the flags, or a not-modified marker when {@code ifNoneMatch} is
     *         still current
     */
    public OfrepBulkResult bulkEvaluate(EvaluationContext context, String ifNoneMatch) {
        byte[] body = requestBody(context);
        return retry.execute(() -> restClient.post()
                .uri(BULK_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (ifNoneMatch != null) {
                        headers.set(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
                    }
                })
                .body(body)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    String etag = response.getHeaders().getFirst(HttpHeaders.ETAG);
                    if (status == NOT_MODIFIED) {
                        return OfrepBulkResult.notModified(etag != null ? etag : ifNoneMatch);
                    }
                    byte[] responseBody = response.getBody().readAllBytes();
                    if (status != OK) {
                        throw parser.error(status, responseBody);
                    }
                    return parser.parseBulk(responseBody, etag);
                }));
    }

    /**
     * Probes {@code GET /health} once, without retries.
     *
     * @return {@code true} when the edge answers 2xx
     */
    public boolean health() {
        try {
            return restClient.get()
                    .uri(HEALTH_PATH)
                    .exchange((request, response) -> response.getStatusCode().is2xxSuccessful());
        } catch (RuntimeException e) {
            logger.debug("Edge health check failed: {}", e.getMessage());
            return false;
        }
    }

    private byte[] requestBody(EvaluationContext context) {
        Map<String, Object> ofrepContext = new LinkedHashMap<>();
        String targetingKey = context.getTargetingKey();
        ofrepContext.put(EvaluationContext.TARGETING_KEY, targetingKey == null ? "" : targetingKey);
        ofrepContext.putAll(context.getAttributes());
        try {
            return mapper.writeValueAsBytes(Map.of("context", ofrepContext));
        } catch (JsonProcessingException e) {
            throw new OfrepException(ErrorCode.INVALID_CONTEXT,
                    "Evaluation context is not JSON-serializable: " + e.getOriginalMessage(), -1, false);
        }
    }
}
