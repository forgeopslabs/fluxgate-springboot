package com.forgeopslabs.fluxgate.openfeature;

import com.forgeopslabs.fluxgate.DefaultFluxGateClient;
import com.forgeopslabs.fluxgate.FluxGateOptions;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EventDetails;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;

import static com.forgeopslabs.fluxgate.EdgeContract.BULK_ETAG;
import static com.forgeopslabs.fluxgate.EdgeContract.BULK_PATH;
import static com.forgeopslabs.fluxgate.EdgeContract.FLAG_PATH;
import static com.forgeopslabs.fluxgate.EdgeContract.SDK_KEY;
import static com.forgeopslabs.fluxgate.EdgeContract.fixture;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Full OpenFeature SDK flow against a WireMock edge speaking OFREP.
 */
class FluxGateProviderIntegrationTest {

    private static final String DOMAIN = "integration-test-domain";

    @RegisterExtension
    static WireMockExtension edge = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final OpenFeatureAPI api = OpenFeatureAPI.getInstance();
    private DefaultFluxGateClient fluxGate;
    private Client openFeatureClient;

    @BeforeEach
    void setUp() {
        edge.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200)));
        edge.stubFor(post(urlEqualTo(BULK_PATH)).atPriority(10)
                .willReturn(aResponse().withStatus(200).withHeader("ETag", BULK_ETAG)
                        .withBody(fixture("bulk-success.json"))));
        FluxGateOptions.Builder options = FluxGateOptions.builder();
        options.baseUrl(edge.baseUrl());
        options.sdkKey(SDK_KEY);
        options.retryMaxAttempts(1);
        options.cacheEnabled(false);
        fluxGate = DefaultFluxGateClient.create(options.build());
        openFeatureClient = api.getClient(DOMAIN);
    }

    private void registerUnpolled() {
        api.setProviderAndWait(DOMAIN, new FluxGateProvider(fluxGate, Duration.ZERO));
    }

    @AfterEach
    void tearDown() {
        OpenFeatureAPI.getInstance().shutdown();
    }

    private static void stubFlag(String key, int status, String body) {
        edge.stubFor(post(urlEqualTo(FLAG_PATH + key))
                .willReturn(aResponse().withStatus(status).withBody(body)));
    }

    @Test
    void initializationRunsOneBulkEvaluationWithTheProbeKey() {
        registerUnpolled();

        assertThat(openFeatureClient.getProviderState()).isEqualTo(ProviderState.READY);
        edge.verify(1, postRequestedFor(urlEqualTo(BULK_PATH))
                .withHeader("Authorization", equalTo("Bearer " + SDK_KEY))
                .withRequestBody(equalToJson(
                        "{\"context\": {\"targetingKey\": \"" + FluxGateProvider.PROBE_TARGETING_KEY + "\"}}")));
    }

    @Test
    void rejectedSdkKeyLeavesProviderInErrorState() {
        edge.stubFor(post(urlEqualTo(BULK_PATH)).atPriority(1)
                .willReturn(aResponse().withStatus(401).withBody(fixture("error-unauthorized.json"))));

        assertThatThrownBy(this::registerUnpolled);

        assertThat(openFeatureClient.getProviderState()).isEqualTo(ProviderState.ERROR);
    }

    @Test
    void evaluatesBooleanFlagWithContext() {
        registerUnpolled();
        stubFlag("new-checkout", 200, fixture("flag-boolean.json"));

        boolean result = openFeatureClient.getBooleanValue("new-checkout", false,
                new ImmutableContext("user-123", Map.of("tier", new Value("premium"))));

        assertThat(result).isTrue();
        edge.verify(postRequestedFor(urlEqualTo(FLAG_PATH + "new-checkout"))
                .withHeader("Authorization", equalTo("Bearer " + SDK_KEY))
                .withRequestBody(equalToJson("{\"context\": {\"targetingKey\": \"user-123\", \"tier\": \"premium\"}}")));
    }

    @Test
    void passesReasonAndVariantThrough() {
        registerUnpolled();
        stubFlag("banner-color", 200, fixture("flag-string.json"));

        FlagEvaluationDetails<String> details = openFeatureClient.getStringDetails("banner-color", "red",
                new ImmutableContext("user-1"));

        assertThat(details.getValue()).isEqualTo("blue");
        assertThat(details.getVariant()).isEqualTo("blue");
        assertThat(details.getReason()).isEqualTo("SPLIT");
        assertThat(details.getFlagMetadata().getString("experiment")).isEqualTo("banner-2026");
    }

    @Test
    void evaluatesNumericAndObjectFlags() {
        registerUnpolled();
        stubFlag("max-items", 200, fixture("flag-integer.json"));
        stubFlag("discount-rate", 200, fixture("flag-double.json"));
        stubFlag("theme", 200, fixture("flag-object.json"));
        ImmutableContext ctx = new ImmutableContext("user-1");

        assertThat(openFeatureClient.getIntegerValue("max-items", 0, ctx)).isEqualTo(42);
        assertThat(openFeatureClient.getDoubleValue("discount-rate", 0.0, ctx)).isEqualTo(0.15);
        Value theme = openFeatureClient.getObjectValue("theme", new Value(), ctx);
        assertThat(theme.asStructure().getValue("color").asString()).isEqualTo("dark");
    }

    @Test
    void returnsDefaultWithErrorCodeOnNotFound() {
        registerUnpolled();
        stubFlag("missing-flag", 404, fixture("error-flag-not-found.json"));

        FlagEvaluationDetails<Boolean> details = openFeatureClient.getBooleanDetails("missing-flag", true,
                new ImmutableContext("user-1"));

        assertThat(details.getValue()).isTrue();
        assertThat(details.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
        assertThat(details.getReason()).isEqualTo("ERROR");
    }

    @Test
    void returnsDefaultOnTypeMismatch() {
        registerUnpolled();
        stubFlag("banner-color", 200, fixture("flag-string.json"));

        FlagEvaluationDetails<Boolean> details = openFeatureClient.getBooleanDetails("banner-color", false,
                new ImmutableContext("user-1"));

        assertThat(details.getValue()).isFalse();
        assertThat(details.getErrorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
    }

    @Test
    void disabledObjectFlagReturnsCallerDefaultWithEdgeReason() {
        registerUnpolled();
        stubFlag("theme", 200, fixture("flag-object-unassigned.json"));
        Value fallback = new Value(new ImmutableStructure(Map.of("color", new Value("light"))));

        FlagEvaluationDetails<Value> details = openFeatureClient.getObjectDetails("theme", fallback,
                new ImmutableContext("user-1"));

        assertThat(details.getValue()).isEqualTo(fallback);
        assertThat(details.getReason()).isEqualTo("DEFAULT");
        assertThat(details.getErrorCode()).isNull();
    }

    @Test
    void instantAttributesAreSentAsIso8601() {
        registerUnpolled();
        stubFlag("new-checkout", 200, fixture("flag-boolean.json"));

        FlagEvaluationDetails<Boolean> details = openFeatureClient.getBooleanDetails("new-checkout", false,
                new ImmutableContext("user-1", Map.of("signedUpAt", new Value(Instant.parse("2026-10-01T12:30:00Z")))));

        assertThat(details.getValue()).isTrue();
        assertThat(details.getErrorCode()).isNull();
        edge.verify(postRequestedFor(urlEqualTo(FLAG_PATH + "new-checkout"))
                .withRequestBody(equalToJson(
                        "{\"context\": {\"targetingKey\": \"user-1\", \"signedUpAt\": \"2026-10-01T12:30:00Z\"}}")));
    }

    @Test
    void pollerDetectsNewFlagSetVersionAndStopsOnShutdown() {
        String v2Etag = "60303ae22b998861bce3b28f33eec1be758a213c86c93c076dbe9f558c11c752";
        edge.stubFor(post(urlEqualTo(BULK_PATH)).atPriority(1)
                .withHeader("If-None-Match", equalTo(v2Etag))
                .willReturn(aResponse().withStatus(304).withHeader("ETag", v2Etag)));
        edge.stubFor(post(urlEqualTo(BULK_PATH)).atPriority(2)
                .withHeader("If-None-Match", equalTo(BULK_ETAG))
                .willReturn(aResponse().withStatus(200).withHeader("ETag", v2Etag)
                        .withBody(fixture("bulk-success-v2.json"))));
        List<EventDetails> changes = new CopyOnWriteArrayList<>();
        openFeatureClient.onProviderConfigurationChanged(changes::add);
        List<ScheduledExecutorService> schedulers = new CopyOnWriteArrayList<>();
        FluxGateProvider provider = new FluxGateProvider(fluxGate, Duration.ofMillis(20), () -> {
            ScheduledExecutorService scheduler = FlagChangePoller.newScheduler();
            schedulers.add(scheduler);
            return scheduler;
        });

        api.setProviderAndWait(DOMAIN, provider);

        await().until(() -> !changes.isEmpty());
        assertThat(changes.get(0).getFlagsChanged()).containsExactly("banner-color");
        await().until(() -> !edge.findAll(postRequestedFor(urlEqualTo(BULK_PATH))
                .withHeader("If-None-Match", equalTo(v2Etag))).isEmpty());
        assertThat(changes).hasSize(1);

        api.shutdown();

        assertThat(schedulers).hasSize(1);
        await().until(() -> schedulers.get(0).isTerminated());
    }
}
