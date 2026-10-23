package com.forgeopslabs.fluxgate.ofrep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgeopslabs.fluxgate.ErrorCode;
import com.forgeopslabs.fluxgate.EvaluationContext;
import com.forgeopslabs.fluxgate.FluxGateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
 * as {@link OfrepException} (mapped responses) or {@link UncheckedIOException}
 * (I/O errors and timeouts).
 */
public class OfrepTransport {

    static final String FLAG_PATH = "/ofrep/v1/evaluate/flags/";
    static final String BULK_PATH = "/ofrep/v1/evaluate/flags";
    static final String HEALTH_PATH = "/health";

    private static final Logger logger = LoggerFactory.getLogger(OfrepTransport.class);
    private static final int OK = 200;
    private static final int NOT_MODIFIED = 304;

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authorization;
    private final Duration readTimeout;
    private final RetryExecutor retry;
    private final ObjectMapper mapper;
    private final OfrepResponseParser parser;

    /**
     * @param httpClient  client with the connect timeout configured
     * @param baseUrl     edge base URL without a trailing slash
     * @param sdkKey      SDK key sent as a bearer token, or {@code null}
     * @param readTimeout timeout for one edge response
     * @param retry       retry policy for evaluation calls
     * @param mapper      JSON mapper for request and response bodies
     */
    public OfrepTransport(HttpClient httpClient, String baseUrl, String sdkKey, Duration readTimeout,
            RetryExecutor retry, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.authorization = sdkKey == null || sdkKey.isBlank() ? null : "Bearer " + sdkKey;
        this.readTimeout = readTimeout;
        this.retry = retry;
        this.mapper = mapper;
        this.parser = new OfrepResponseParser(mapper);
    }

    /**
     * Creates a transport from client options.
     *
     * @param options the client options
     * @return a configured transport
     */
    public static OfrepTransport create(FluxGateOptions options) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(options.getConnectTimeout())
                .build();
        return new OfrepTransport(httpClient, options.getBaseUrl(), options.getSdkKey(), options.getReadTimeout(),
                new RetryExecutor(options.getRetryMaxAttempts(), options.getRetryInitialBackoff()),
                OfrepObjectMapper.create());
    }

    /**
     * Evaluates one flag.
     *
     * @param key     the flag key
     * @param context the evaluation context
     * @return the edge's success object
     */
    public OfrepFlagResult evaluate(String key, EvaluationContext context) {
        HttpRequest request = jsonPost(FLAG_PATH + encodePathSegment(key), requestBody(context)).build();
        return retry.execute(() -> {
            HttpResponse<byte[]> response = send(request);
            if (response.statusCode() != OK) {
                throw parser.error(response.statusCode(), response.body());
            }
            return parser.parseFlag(response.body(), key);
        });
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
        HttpRequest.Builder builder = jsonPost(BULK_PATH, requestBody(context));
        if (ifNoneMatch != null) {
            builder.header("If-None-Match", ifNoneMatch);
        }
        HttpRequest request = builder.build();
        return retry.execute(() -> {
            HttpResponse<byte[]> response = send(request);
            int status = response.statusCode();
            String etag = response.headers().firstValue("ETag").orElse(null);
            if (status == NOT_MODIFIED) {
                return OfrepBulkResult.notModified(etag != null ? etag : ifNoneMatch);
            }
            if (status != OK) {
                throw parser.error(status, response.body());
            }
            return parser.parseBulk(response.body(), etag);
        });
    }

    /**
     * Probes {@code GET /health} once, without retries.
     *
     * @return {@code true} when the edge answers 2xx
     */
    public boolean health() {
        HttpRequest request = withAuthorization(HttpRequest.newBuilder(URI.create(baseUrl + HEALTH_PATH))
                .timeout(readTimeout)
                .GET())
                .build();
        try {
            int status = send(request).statusCode();
            return status >= 200 && status < 300;
        } catch (RuntimeException e) {
            logger.debug("Edge health check failed: {}", e.getMessage());
            return false;
        }
    }

    private HttpRequest.Builder jsonPost(String path, byte[] body) {
        return withAuthorization(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(readTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));
    }

    private HttpRequest.Builder withAuthorization(HttpRequest.Builder builder) {
        return authorization == null ? builder : builder.header("Authorization", authorization);
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("I/O error calling the edge: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling the edge", e);
        }
    }

    private static String encodePathSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
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
