package com.forgeopslabs.fluxgate;

import com.forgeopslabs.fluxgate.cache.BulkSnapshotStore;
import com.forgeopslabs.fluxgate.cache.EvaluationCache;
import com.forgeopslabs.fluxgate.ofrep.OfrepTransport;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import static com.forgeopslabs.fluxgate.EdgeContract.BULK_PATH;
import static com.forgeopslabs.fluxgate.EdgeContract.FLAG_PATH;
import static com.forgeopslabs.fluxgate.EdgeContract.SDK_KEY;
import static com.forgeopslabs.fluxgate.EdgeContract.fixture;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The circuit breaker inside {@link DefaultFluxGateClient} against a WireMock
 * edge (threshold 3, cool-down 30s, one attempt per call).
 */
class DefaultFluxGateClientCircuitBreakerTest {

    @RegisterExtension
    static WireMockExtension edge = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final Duration OPEN = Duration.ofSeconds(30);
    private static final EvaluationContext USER = EvaluationContext.of("user-1");

    private final EdgeCircuitBreakerTest.TestClock clock = new EdgeCircuitBreakerTest.TestClock();
    private DefaultFluxGateClient client;

    @BeforeEach
    void setUp() {
        client = client(3);
    }

    private DefaultFluxGateClient client(int threshold) {
        FluxGateOptions.Builder options = FluxGateOptions.builder();
        options.baseUrl(edge.baseUrl());
        options.sdkKey(SDK_KEY);
        options.retryMaxAttempts(1);
        options.cacheEnabled(false);
        return new DefaultFluxGateClient(OfrepTransport.create(options.build()),
                EvaluationCache.from(options.build()), new BulkSnapshotStore(10),
                new EdgeCircuitBreaker(threshold, OPEN, clock), List.of(), ForkJoinPool.commonPool());
    }

    private static void stubFlag(int status, String body) {
        edge.stubFor(post(urlEqualTo(FLAG_PATH + "f")).willReturn(aResponse().withStatus(status).withBody(body)));
    }

    private static int flagRequests() {
        return edge.findAll(postRequestedFor(urlEqualTo(FLAG_PATH + "f"))).size();
    }

    private FlagEvaluation<Boolean> evaluate() {
        return client.evaluate("f", true, Boolean.class, USER);
    }

    private void openTheBreaker() {
        stubFlag(503, "");
        for (int i = 0; i < 3; i++) {
            evaluate();
        }
    }

    @Test
    void opensAfterThresholdFailuresAndShortCircuitsWithoutHttp() {
        openTheBreaker();
        assertThat(flagRequests()).isEqualTo(3);

        FlagEvaluation<Boolean> result = evaluate();

        assertThat(result.value()).isTrue();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.PROVIDER_NOT_READY);
        assertThat(result.errorMessage()).contains("Circuit breaker open");
        assertThat(flagRequests()).isEqualTo(3);
    }

    @Test
    void transportFaultsCountAsFailures() {
        edge.stubFor(post(urlEqualTo(FLAG_PATH + "f"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        for (int i = 0; i < 3; i++) {
            assertThat(evaluate().errorCode()).isEqualTo(ErrorCode.GENERAL);
        }

        evaluate();

        assertThat(flagRequests()).isEqualTo(3);
    }

    @Test
    void halfOpenSuccessCloses() {
        openTheBreaker();
        clock.advance(OPEN);
        stubFlag(200, fixture("flag-boolean-disabled.json").replace("new-checkout", "f"));

        assertThat(evaluate().isError()).isFalse();
        assertThat(evaluate().isError()).isFalse();

        assertThat(flagRequests()).isEqualTo(5);
    }

    @Test
    void halfOpenFailureReopens() {
        openTheBreaker();
        clock.advance(OPEN);
        stubFlag(500, "");

        assertThat(evaluate().errorCode()).isEqualTo(ErrorCode.GENERAL);
        assertThat(evaluate().errorMessage()).contains("Circuit breaker open");

        assertThat(flagRequests()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(ints = { 400, 401, 403, 404 })
    void clientErrorsNeverCount(int status) {
        stubFlag(status, "{}");

        for (int i = 0; i < 6; i++) {
            evaluate();
        }

        assertThat(flagRequests()).isEqualTo(6);
    }

    @Test
    void clientErrorBetweenFailuresResetsTheCount() {
        stubFlag(503, "");
        evaluate();
        evaluate();
        stubFlag(404, fixture("error-flag-not-found.json"));
        evaluate();
        stubFlag(503, "");
        evaluate();
        evaluate();

        evaluate();

        assertThat(flagRequests()).isEqualTo(6);
    }

    @Test
    void thresholdZeroDisables() {
        client = client(0);
        stubFlag(503, "");

        for (int i = 0; i < 6; i++) {
            evaluate();
        }

        assertThat(flagRequests()).isEqualTo(6);
    }

    @Test
    void bulkCallsShareTheBreaker() {
        openTheBreaker();

        BulkEvaluation bulk = client.bulkEvaluate(USER);

        assertThat(bulk.errorCode()).isEqualTo(ErrorCode.PROVIDER_NOT_READY);
        assertThat(edge.findAll(postRequestedFor(urlEqualTo(BULK_PATH)))).isEmpty();
    }

    @Test
    void bulkFailuresOpenTheBreakerForSingleEvaluations() {
        edge.stubFor(post(urlEqualTo(BULK_PATH)).willReturn(aResponse().withStatus(502)));
        for (int i = 0; i < 3; i++) {
            client.bulkEvaluate(USER);
        }
        stubFlag(200, fixture("flag-boolean.json"));

        assertThat(evaluate().errorCode()).isEqualTo(ErrorCode.PROVIDER_NOT_READY);
        assertThat(flagRequests()).isZero();
    }

    @Test
    void unserializableContextDuringHalfOpenFreesTheTrial() {
        openTheBreaker();
        clock.advance(OPEN);
        EvaluationContext bad = EvaluationContext.builder().targetingKey("u").attribute("x", new Object()).build();

        assertThat(client.evaluate("f", true, Boolean.class, bad).errorCode()).isEqualTo(ErrorCode.INVALID_CONTEXT);
        stubFlag(200, fixture("flag-boolean.json"));

        assertThat(evaluate().isError()).isFalse();
    }
}
