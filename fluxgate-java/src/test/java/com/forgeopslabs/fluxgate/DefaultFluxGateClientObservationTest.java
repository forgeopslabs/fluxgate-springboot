package com.forgeopslabs.fluxgate;

import com.forgeopslabs.fluxgate.ofrep.OfrepTransport;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.forgeopslabs.fluxgate.EdgeContract.BULK_ETAG;
import static com.forgeopslabs.fluxgate.EdgeContract.BULK_PATH;
import static com.forgeopslabs.fluxgate.EdgeContract.FLAG_PATH;
import static com.forgeopslabs.fluxgate.EdgeContract.SDK_KEY;
import static com.forgeopslabs.fluxgate.EdgeContract.fixture;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Listener callbacks and {@code evaluateAsync} of {@link DefaultFluxGateClient}.
 */
class DefaultFluxGateClientObservationTest {

    @RegisterExtension
    static WireMockExtension edge = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final EvaluationContext USER = EvaluationContext.of("user-42");

    private final RecordingListener recorder = new RecordingListener();
    private FluxGateOptions.Builder options;

    @BeforeEach
    void setUp() {
        options = FluxGateOptions.builder();
        options.baseUrl(edge.baseUrl());
        options.sdkKey(SDK_KEY);
        options.retryMaxAttempts(1);
        edge.stubFor(post(urlEqualTo(FLAG_PATH + "new-checkout"))
                .willReturn(aResponse().withStatus(200).withBody(fixture("flag-boolean.json"))));
        edge.stubFor(post(urlEqualTo(FLAG_PATH + "missing"))
                .willReturn(aResponse().withStatus(404).withBody(fixture("error-flag-not-found.json"))));
    }

    private DefaultFluxGateClient client(Executor executor, FluxGateClientListener... listeners) {
        return new DefaultFluxGateClient(OfrepTransport.create(options.build()), options.build(),
                List.of(listeners), executor);
    }

    private DefaultFluxGateClient client(FluxGateClientListener... listeners) {
        return client(Runnable::run, listeners);
    }

    @Nested
    class Listeners {

        @Test
        void reportsEdgeEvaluationsThenCacheHits() {
            DefaultFluxGateClient client = client(recorder);

            client.evaluate("new-checkout", false, Boolean.class, USER);
            client.evaluate("new-checkout", false, Boolean.class, USER);

            assertThat(recorder.cacheLookups).containsExactly("new-checkout:miss", "new-checkout:hit");
            assertThat(recorder.evaluations).hasSize(2);
            assertThat(recorder.fromCache).containsExactly(false, true);
            assertThat(recorder.evaluations.get(0).reason()).isEqualTo("TARGETING_MATCH");
            assertThat(recorder.durations).allSatisfy(nanos -> assertThat(nanos).isPositive());
        }

        @Test
        void reportsErrorsAndSkipsCacheLookupsWhenTheCacheIsDisabled() {
            options.cacheEnabled(false);
            DefaultFluxGateClient client = client(recorder);

            client.evaluate("missing", false, Boolean.class, USER);

            assertThat(recorder.cacheLookups).isEmpty();
            assertThat(recorder.evaluations).singleElement()
                    .satisfies(e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND));
        }

        @Test
        void doesNotReportCallsRejectedBeforeAnyLookup() {
            DefaultFluxGateClient client = client(recorder);

            client.evaluate(" ", false, Boolean.class, USER);
            client.evaluate("new-checkout", false, null, USER);

            assertThat(recorder.evaluations).isEmpty();
            assertThat(recorder.cacheLookups).isEmpty();
        }

        @Test
        void reportsBulkEvaluations() {
            edge.stubFor(post(urlEqualTo(BULK_PATH)).willReturn(aResponse().withStatus(200)
                    .withHeader("ETag", BULK_ETAG).withBody(fixture("bulk-success.json"))));

            BulkEvaluation result = client(recorder).bulkEvaluate(USER);

            assertThat(recorder.bulks).containsExactly(result);
        }

        @Test
        void listenerFailuresNeverReachTheCaller() {
            FluxGateClientListener broken = new FluxGateClientListener() {
                @Override
                public void onEvaluation(FlagEvaluation<?> evaluation, boolean fromCache, long durationNanos) {
                    throw new IllegalStateException("boom");
                }

                @Override
                public void onCacheLookup(String flagKey, boolean hit) {
                    throw new IllegalStateException("boom");
                }
            };
            DefaultFluxGateClient client = client(broken, recorder);

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", false, Boolean.class, USER);

            assertThat(result.value()).isTrue();
            assertThat(recorder.evaluations).hasSize(1);
        }

        @Test
        void defaultCallbacksDoNothing() {
            FluxGateClientListener noop = new FluxGateClientListener() {
            };
            DefaultFluxGateClient client = client(noop);

            assertThat(client.evaluate("new-checkout", false, Boolean.class, USER).value()).isTrue();
            assertThat(client.bulkEvaluate(USER)).isNotNull();
        }
    }

    @Nested
    class Async {

        @Test
        void runsOnTheConfiguredExecutor() {
            AtomicReference<String> thread = new AtomicReference<>();
            Executor executor = task -> {
                Thread worker = new Thread(task, "fluxgate-test-async");
                worker.start();
            };
            FluxGateClientListener threadRecorder = new FluxGateClientListener() {
                @Override
                public void onEvaluation(FlagEvaluation<?> evaluation, boolean fromCache, long durationNanos) {
                    thread.set(Thread.currentThread().getName());
                }
            };

            FlagEvaluation<Boolean> result = client(executor, threadRecorder)
                    .evaluateAsync("new-checkout", false, Boolean.class, USER).join();

            assertThat(result.value()).isTrue();
            assertThat(thread).hasValue("fluxgate-test-async");
        }

        @Test
        void rejectedTaskCompletesWithTheDefault() {
            Executor rejecting = task -> {
                throw new RejectedExecutionException("saturated");
            };

            CompletableFuture<FlagEvaluation<String>> future = client(rejecting)
                    .evaluateAsync("banner", "none", String.class, USER);

            assertThat(future).isCompleted();
            FlagEvaluation<String> result = future.join();
            assertThat(result.value()).isEqualTo("none");
            assertThat(result.errorCode()).isEqualTo(ErrorCode.GENERAL);
            assertThat(result.errorMessage()).contains("saturated");
        }

        @Test
        void interfaceDefaultUsesTheCommonPool() {
            FluxGateClient minimal = new FluxGateClient() {
                @Override
                @SuppressWarnings("unchecked")
                public <T> FlagEvaluation<T> evaluate(String key, T defaultValue, Class<T> type,
                        EvaluationContext context) {
                    return new FlagEvaluation<>(key, (T) Thread.currentThread().getName(), null, "STATIC", null,
                            null);
                }

                @Override
                public BulkEvaluation bulkEvaluate(EvaluationContext context) {
                    return null;
                }

                @Override
                public boolean isHealthy() {
                    return true;
                }
            };

            String thread = minimal.evaluateAsync("k", "x", String.class, USER)
                    .orTimeout(5, TimeUnit.SECONDS).join().value();

            assertThat(thread).contains("ForkJoinPool.commonPool");
        }
    }

    static final class RecordingListener implements FluxGateClientListener {

        final List<FlagEvaluation<?>> evaluations = new CopyOnWriteArrayList<>();
        final List<Boolean> fromCache = new CopyOnWriteArrayList<>();
        final List<Long> durations = new CopyOnWriteArrayList<>();
        final List<String> cacheLookups = new CopyOnWriteArrayList<>();
        final List<BulkEvaluation> bulks = new CopyOnWriteArrayList<>();

        @Override
        public void onEvaluation(FlagEvaluation<?> evaluation, boolean cached, long durationNanos) {
            evaluations.add(evaluation);
            fromCache.add(cached);
            durations.add(durationNanos);
        }

        @Override
        public void onCacheLookup(String flagKey, boolean hit) {
            cacheLookups.add(flagKey + (hit ? ":hit" : ":miss"));
        }

        @Override
        public void onBulkEvaluation(BulkEvaluation evaluation) {
            bulks.add(evaluation);
        }
    }
}
