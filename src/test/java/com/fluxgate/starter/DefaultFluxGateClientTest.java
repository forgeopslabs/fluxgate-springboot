package com.fluxgate.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.fluxgate.starter.EdgeContract.BULK_ETAG;
import static com.fluxgate.starter.EdgeContract.BULK_PATH;
import static com.fluxgate.starter.EdgeContract.FLAG_PATH;
import static com.fluxgate.starter.EdgeContract.SDK_KEY;
import static com.fluxgate.starter.EdgeContract.fixture;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
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

class DefaultFluxGateClientTest {

    @RegisterExtension
    static WireMockExtension edge = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final EvaluationContext USER = EvaluationContext.builder()
            .targetingKey("user-42")
            .attribute("country", "LK")
            .attribute("plan", Map.of("tier", "gold"))
            .build();

    private FeatureToggleProperties properties;
    private DefaultFluxGateClient client;

    @BeforeEach
    void setUp() {
        properties = new FeatureToggleProperties();
        properties.setBaseUrl(edge.baseUrl() + "/");
        properties.setSdkKey(SDK_KEY);
        properties.setReadTimeout(Duration.ofMillis(300));
        properties.getRetry().setMaxAttempts(3);
        properties.getRetry().setInitialBackoff(Duration.ofMillis(1));
        properties.getCache().setEnabled(false);
        client = DefaultFluxGateClient.create(properties);
    }

    private static void stubFlag(String key, int status, String body) {
        edge.stubFor(post(urlEqualTo(FLAG_PATH + key))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static int flagRequests(String key) {
        return edge.findAll(postRequestedFor(urlEqualTo(FLAG_PATH + key))).size();
    }

    @Nested
    class Success {

        @Test
        void sendsSdkKeyAndOfrepContext() {
            stubFlag("new-checkout", 200, fixture("flag-boolean.json"));

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", false, Boolean.class, USER);

            assertThat(result).isEqualTo(
                    new FlagEvaluation<>("new-checkout", true, null, "TARGETING_MATCH", null, null));
            edge.verify(postRequestedFor(urlEqualTo(FLAG_PATH + "new-checkout"))
                    .withHeader("Authorization", equalTo("Bearer " + SDK_KEY))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson(
                            "{\"context\": {\"targetingKey\": \"user-42\", \"country\": \"LK\","
                                    + " \"plan\": {\"tier\": \"gold\"}}}")));
        }

        @Test
        void javaTimeAttributesAreSentAsIso8601Strings() {
            stubFlag("new-checkout", 200, fixture("flag-boolean.json"));
            EvaluationContext ctx = EvaluationContext.builder()
                    .targetingKey("user-42")
                    .attribute("signedUpAt", Instant.parse("2026-10-01T12:30:00Z"))
                    .attribute("birthday", LocalDate.of(1990, 5, 17))
                    .build();

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", false, Boolean.class, ctx);

            assertThat(result.isError()).isFalse();
            assertThat(result.value()).isTrue();
            edge.verify(postRequestedFor(urlEqualTo(FLAG_PATH + "new-checkout"))
                    .withRequestBody(equalToJson("{\"context\": {\"targetingKey\": \"user-42\","
                            + " \"signedUpAt\": \"2026-10-01T12:30:00Z\", \"birthday\": \"1990-05-17\"}}")));
        }

        @Test
        void disabledFlagReturnsFalseWithDisabledReason() {
            stubFlag("new-checkout", 200, fixture("flag-boolean-disabled.json"));

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", true, Boolean.class, USER);

            assertThat(result.value()).isFalse();
            assertThat(result.reason()).isEqualTo("DISABLED");
            assertThat(result.isError()).isFalse();
        }

        @Test
        void stringFlagCarriesVariantAndReason() {
            stubFlag("banner-color", 200, fixture("flag-string.json"));

            FlagEvaluation<String> result = client.evaluate("banner-color", "red", String.class, USER);

            assertThat(result.value()).isEqualTo("blue");
            assertThat(result.variant()).isEqualTo("blue");
            assertThat(result.reason()).isEqualTo("SPLIT");
            assertThat(result.metadata()).containsExactly(Map.entry("experiment", "banner-2026"));
            assertThat(client.getString("banner-color", "red", USER)).isEqualTo("blue");
        }

        @Test
        void metadataIsImmutableAndEmptyWhenAbsentOrOnError() {
            stubFlag("banner-color", 200, fixture("flag-string.json"));
            stubFlag("new-checkout", 200, fixture("flag-boolean.json"));
            stubFlag("missing", 404, fixture("error-flag-not-found.json"));

            Map<String, Object> metadata = client.evaluate("banner-color", "red", String.class, USER).metadata();

            assertThatThrownBy(() -> metadata.put("x", 1))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(client.evaluate("new-checkout", false, Boolean.class, USER).metadata()).isEmpty();
            assertThat(client.evaluate("missing", false, Boolean.class, USER).metadata()).isEmpty();
        }

        @Test
        void integerFlag() {
            stubFlag("max-items", 200, fixture("flag-integer.json"));

            assertThat(client.getInteger("max-items", 1, USER)).isEqualTo(42);
            assertThat(client.evaluate("max-items", 1L, Long.class, USER).value()).isEqualTo(42L);
            assertThat(client.evaluate("max-items", 1, int.class, USER).value()).isEqualTo(42);
        }

        @Test
        void integerFlagWidensToDouble() {
            stubFlag("max-items", 200, fixture("flag-integer.json"));

            assertThat(client.getDouble("max-items", 0.0, USER)).isEqualTo(42.0);
        }

        @Test
        void longFlag() {
            stubFlag("quota-bytes", 200, fixture("flag-long.json"));

            assertThat(client.evaluate("quota-bytes", 0L, Long.class, USER).value()).isEqualTo(5_000_000_000L);
        }

        @Test
        void doubleFlag() {
            stubFlag("discount-rate", 200, fixture("flag-double.json"));

            FlagEvaluation<Double> result = client.evaluate("discount-rate", 0.0, Double.class, USER);

            assertThat(result.value()).isEqualTo(0.15);
            assertThat(result.variant()).isEqualTo("fifteen");
            assertThat(client.getDouble("discount-rate", 0.0, USER)).isEqualTo(0.15);
        }

        @Test
        void objectFlagAsMapAndJsonNode() {
            stubFlag("theme", 200, fixture("flag-object.json"));

            assertThat(client.getObject("theme", Map.of(), USER))
                    .containsExactlyInAnyOrderEntriesOf(Map.of("color", "dark", "fontSize", 14, "beta", true));
            JsonNode node = client.evaluate("theme", null, JsonNode.class, USER).value();
            assertThat(node.get("color").asText()).isEqualTo("dark");
        }

        @Test
        void listFlag() {
            stubFlag("regions", 200, "{\"key\": \"regions\", \"value\": [\"eu\", \"us\"], \"reason\": \"STATIC\"}");

            assertThat(client.evaluate("regions", List.of(), List.class, USER).value()).isEqualTo(List.of("eu", "us"));
            assertThat(client.evaluate("regions", null, Object.class, USER).value()).isEqualTo(List.of("eu", "us"));
        }

        @Test
        void missingValueResolvesToCodeDefault() {
            stubFlag("new-checkout", 200, fixture("flag-code-default.json"));

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", true, Boolean.class, USER);

            assertThat(result).isEqualTo(new FlagEvaluation<>("new-checkout", true, null, "DEFAULT", null, null));
        }

        @Test
        void isEnabledShortcuts() {
            stubFlag("new-checkout", 200, fixture("flag-boolean.json"));

            assertThat(client.isEnabled("new-checkout", USER)).isTrue();
            assertThat(client.getBoolean("new-checkout", false, USER)).isTrue();
            // Without a context there is no targeting key: defaults, decided locally.
            assertThat(client.isEnabled("new-checkout")).isFalse();
            assertThat(client.getBoolean("new-checkout", true)).isTrue();
            assertThat(client.getString("new-checkout", "x")).isEqualTo("x");
            assertThat(client.getInteger("new-checkout", 7)).isEqualTo(7);
            assertThat(client.getDouble("new-checkout", 1.5)).isEqualTo(1.5);
            assertThat(client.getObject("new-checkout", Map.of("a", 1))).isEqualTo(Map.of("a", 1));
            assertThat(flagRequests("new-checkout")).isEqualTo(2);
        }

        @Test
        void omitsAuthorizationWithoutSdkKey() {
            properties.setSdkKey(null);
            stubFlag("new-checkout", 200, fixture("flag-boolean.json"));

            DefaultFluxGateClient.create(properties).isEnabled("new-checkout", USER);

            edge.verify(postRequestedFor(urlEqualTo(FLAG_PATH + "new-checkout"))
                    .withHeader("Authorization", absent()));
        }
    }

    @Nested
    class Errors {

        @Test
        void flagNotFound() {
            stubFlag("missing-flag", 404, fixture("error-flag-not-found.json"));

            FlagEvaluation<Boolean> result = client.evaluate("missing-flag", true, Boolean.class, USER);

            assertThat(result.value()).isTrue();
            assertThat(result.reason()).isEqualTo("ERROR");
            assertThat(result.errorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
            assertThat(result.errorMessage()).contains("The requested feature flag does not exist");
            assertThat(flagRequests("missing-flag")).isEqualTo(1);
        }

        @Test
        void edgeTargetingKeyMissingIsMapped() {
            stubFlag("new-checkout", 400, fixture("error-targeting-key-missing.json"));

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", false, Boolean.class, USER);

            assertThat(result.errorCode()).isEqualTo(ErrorCode.TARGETING_KEY_MISSING);
            assertThat(result.reason()).isEqualTo("ERROR");
            assertThat(flagRequests("new-checkout")).isEqualTo(1);
        }

        @ParameterizedTest
        @ValueSource(strings = { "", "  " })
        void missingTargetingKeyFailsLocallyWithoutARequest(String targetingKey) {
            stubFlag("new-checkout", 200, fixture("flag-boolean.json"));

            FlagEvaluation<Boolean> blank = client.evaluate("new-checkout", true, Boolean.class,
                    EvaluationContext.of(targetingKey));
            FlagEvaluation<Boolean> none = client.evaluate("new-checkout", true, Boolean.class, null);
            BulkEvaluation bulk = client.bulkEvaluate(EvaluationContext.empty());

            assertThat(blank.errorCode()).isEqualTo(ErrorCode.TARGETING_KEY_MISSING);
            assertThat(blank.value()).isTrue();
            assertThat(none.errorCode()).isEqualTo(ErrorCode.TARGETING_KEY_MISSING);
            assertThat(none.errorMessage()).contains("no targeting key");
            assertThat(bulk.errorCode()).isEqualTo(ErrorCode.TARGETING_KEY_MISSING);
            assertThat(edge.getAllServeEvents()).isEmpty();
        }

        @Test
        void badRequestWithoutOfrepBodyIsInvalidContext() {
            stubFlag("new-checkout", 400, "Json deserialize error: missing field `targetingKey`");

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", false, Boolean.class, USER);

            assertThat(result.errorCode()).isEqualTo(ErrorCode.INVALID_CONTEXT);
            assertThat(result.errorMessage()).contains("missing field");
        }

        @Test
        void unauthorized() {
            stubFlag("new-checkout", 401, fixture("error-unauthorized.json"));

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", false, Boolean.class, USER);

            assertThat(result.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
            assertThat(result.errorMessage()).contains("Client credentials are missing or invalid");
            assertThat(flagRequests("new-checkout")).isEqualTo(1);
        }

        @Test
        void forbidden() {
            stubFlag("new-checkout", 403, fixture("error-forbidden.json"));

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", true, Boolean.class, USER);

            assertThat(result.value()).isTrue();
            assertThat(result.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
            assertThat(result.errorMessage()).contains("Client is not permitted to evaluate flags");
            assertThat(flagRequests("new-checkout")).isEqualTo(1);
        }

        @ParameterizedTest
        @ValueSource(ints = { 502, 503, 504 })
        void retriesGatewayErrorsThenSucceeds(int status) {
            edge.stubFor(post(urlEqualTo(FLAG_PATH + "new-checkout")).inScenario("flaky")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(status))
                    .willSetStateTo("recovered"));
            edge.stubFor(post(urlEqualTo(FLAG_PATH + "new-checkout")).inScenario("flaky")
                    .whenScenarioStateIs("recovered")
                    .willReturn(aResponse().withStatus(200).withBody(fixture("flag-boolean.json"))));

            assertThat(client.isEnabled("new-checkout", USER)).isTrue();
            assertThat(flagRequests("new-checkout")).isEqualTo(2);
        }

        @Test
        void gatewayErrorAfterMaxAttemptsReturnsDefault() {
            stubFlag("new-checkout", 502, "Failed to fetch feature from backend");

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", true, Boolean.class, USER);

            assertThat(result.value()).isTrue();
            assertThat(result.errorCode()).isEqualTo(ErrorCode.GENERAL);
            assertThat(result.errorMessage()).contains("502", "Failed to fetch feature from backend");
            assertThat(flagRequests("new-checkout")).isEqualTo(3);
        }

        @Test
        void serviceUnavailableAfterMaxAttemptsIsProviderNotReady() {
            stubFlag("new-checkout", 503, "");

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", false, Boolean.class, USER);

            assertThat(result.errorCode()).isEqualTo(ErrorCode.PROVIDER_NOT_READY);
            assertThat(flagRequests("new-checkout")).isEqualTo(3);
        }

        @Test
        void internalServerErrorIsNotRetried() {
            stubFlag("new-checkout", 500, "{\"errorCode\": \"GENERAL\", \"errorDetails\": \"boom\"}");

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", false, Boolean.class, USER);

            assertThat(result.errorCode()).isEqualTo(ErrorCode.GENERAL);
            assertThat(flagRequests("new-checkout")).isEqualTo(1);
        }

        @Test
        void readTimeoutIsRetriedThenReturnsDefault() {
            edge.stubFor(post(urlEqualTo(FLAG_PATH + "new-checkout"))
                    .willReturn(aResponse().withStatus(200).withBody(fixture("flag-boolean.json"))
                            .withFixedDelay(1_000)));
            properties.getRetry().setMaxAttempts(2);

            FlagEvaluation<Boolean> result = DefaultFluxGateClient.create(properties)
                    .evaluate("new-checkout", false, Boolean.class, USER);

            assertThat(result.value()).isFalse();
            assertThat(result.errorCode()).isEqualTo(ErrorCode.GENERAL);
            assertThat(result.reason()).isEqualTo("ERROR");
            assertThat(flagRequests("new-checkout")).isEqualTo(2);
        }

        @Test
        void connectionResetIsRetriedThenReturnsDefault() {
            edge.stubFor(post(urlEqualTo(FLAG_PATH + "new-checkout"))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", true, Boolean.class, USER);

            assertThat(result.value()).isTrue();
            assertThat(result.errorCode()).isEqualTo(ErrorCode.GENERAL);
            assertThat(flagRequests("new-checkout")).isEqualTo(3);
        }

        @Test
        void malformedSuccessBodyIsGeneralAndNotRetried() {
            stubFlag("new-checkout", 200, "not json");

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", false, Boolean.class, USER);

            assertThat(result.errorCode()).isEqualTo(ErrorCode.GENERAL);
            assertThat(result.errorMessage()).startsWith("Malformed flag evaluation");
            assertThat(flagRequests("new-checkout")).isEqualTo(1);
        }

        @Test
        void unserializableContextIsInvalidContext() {
            EvaluationContext ctx = EvaluationContext.builder()
                    .targetingKey("user-42")
                    .attribute("self", new Object())
                    .build();

            FlagEvaluation<Boolean> result = client.evaluate("new-checkout", false, Boolean.class, ctx);

            assertThat(result.errorCode()).isEqualTo(ErrorCode.INVALID_CONTEXT);
            assertThat(flagRequests("new-checkout")).isZero();
        }

        @Test
        void blankKeyAndNullTypeNeverThrow() {
            assertThat(client.evaluate(" ", true, Boolean.class, USER).errorCode()).isEqualTo(ErrorCode.GENERAL);
            assertThat(client.evaluate(null, true, Boolean.class, USER).value()).isTrue();
            assertThat(client.evaluate("new-checkout", "x", null, USER).errorCode()).isEqualTo(ErrorCode.GENERAL);
        }
    }

    @Nested
    class OffMarker {

        @Test
        void disabledStringFlagReturnsDefaultWithDisabledReason() {
            stubFlag("banner-color", 200, fixture("flag-string-disabled.json"));

            FlagEvaluation<String> result = client.evaluate("banner-color", "red", String.class, USER);

            assertThat(result).isEqualTo(new FlagEvaluation<>("banner-color", "red", null, "DISABLED", null, null));
            assertThat(client.getString("banner-color", "red", USER)).isEqualTo("red");
            assertThat(client.getInteger("banner-color", 7, USER)).isEqualTo(7);
            assertThat(client.getDouble("banner-color", 1.5, USER)).isEqualTo(1.5);
        }

        @Test
        void unassignedObjectFlagReturnsDefaultWithoutError() {
            stubFlag("theme", 200, fixture("flag-object-unassigned.json"));

            FlagEvaluation<Map> result = client.evaluate("theme", Map.of("color", "light"), Map.class, USER);

            assertThat(result.value()).isEqualTo(Map.of("color", "light"));
            assertThat(result.reason()).isEqualTo("DEFAULT");
            assertThat(result.isError()).isFalse();
        }

        @Test
        void booleanAndUntypedRequestsStillSeeFalse() {
            stubFlag("banner-color", 200, fixture("flag-string-disabled.json"));

            FlagEvaluation<Boolean> asBoolean = client.evaluate("banner-color", true, Boolean.class, USER);
            assertThat(asBoolean.value()).isFalse();
            assertThat(asBoolean.reason()).isEqualTo("DISABLED");
            assertThat(client.evaluate("banner-color", null, Object.class, USER).value()).isEqualTo(false);
        }

        @Test
        void falseWithVariantIsStillATypeMismatch() {
            stubFlag("banner-color", 200,
                    "{\"key\": \"banner-color\", \"value\": false, \"reason\": \"SPLIT\", \"variant\": \"off\"}");

            assertThat(client.evaluate("banner-color", "red", String.class, USER).errorCode())
                    .isEqualTo(ErrorCode.TYPE_MISMATCH);
        }
    }

    @Nested
    class TypeMismatch {

        @Test
        void stringRequestedAsBoolean() {
            stubFlag("banner-color", 200, fixture("flag-string.json"));

            FlagEvaluation<Boolean> result = client.evaluate("banner-color", true, Boolean.class, USER);

            assertThat(result.value()).isTrue();
            assertThat(result.reason()).isEqualTo("ERROR");
            assertThat(result.errorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
            assertThat(result.errorMessage()).contains("string", "Boolean");
        }

        @Test
        void doubleRequestedAsInteger() {
            stubFlag("discount-rate", 200, fixture("flag-double.json"));

            assertThat(client.evaluate("discount-rate", 3, Integer.class, USER).errorCode())
                    .isEqualTo(ErrorCode.TYPE_MISMATCH);
            assertThat(client.getInteger("discount-rate", 3, USER)).isEqualTo(3);
        }

        @Test
        void longOutOfIntRangeRequestedAsInteger() {
            stubFlag("quota-bytes", 200, fixture("flag-long.json"));

            assertThat(client.evaluate("quota-bytes", 0, Integer.class, USER).errorCode())
                    .isEqualTo(ErrorCode.TYPE_MISMATCH);
        }

        @Test
        void booleanRequestedAsStringOrObject() {
            stubFlag("new-checkout", 200, fixture("flag-boolean.json"));

            assertThat(client.evaluate("new-checkout", "off", String.class, USER).errorCode())
                    .isEqualTo(ErrorCode.TYPE_MISMATCH);
            assertThat(client.getObject("new-checkout", Map.of(), USER)).isEmpty();
        }

        @Test
        void unsupportedTypeIsMismatch() {
            stubFlag("theme", 200, fixture("flag-object.json"));

            assertThat(client.evaluate("theme", null, StringBuilder.class, USER).errorCode())
                    .isEqualTo(ErrorCode.TYPE_MISMATCH);
        }
    }

    @Nested
    class Caching {

        @BeforeEach
        void enableCache() {
            properties.getCache().setEnabled(true);
            client = DefaultFluxGateClient.create(properties);
        }

        @Test
        void secondEvaluationForSameContextIsServedFromCache() {
            stubFlag("new-checkout", 200, fixture("flag-boolean.json"));

            assertThat(client.isEnabled("new-checkout", USER)).isTrue();
            assertThat(client.isEnabled("new-checkout", EvaluationContext.builder()
                    .targetingKey("user-42").attribute("country", "LK").attribute("plan", Map.of("tier", "gold"))
                    .build())).isTrue();

            assertThat(flagRequests("new-checkout")).isEqualTo(1);
        }

        @Test
        void invalidateCacheForcesANewEdgeCall() {
            stubFlag("new-checkout", 200, fixture("flag-boolean.json"));
            client.isEnabled("new-checkout", USER);

            client.invalidateCache();
            client.isEnabled("new-checkout", USER);

            assertThat(flagRequests("new-checkout")).isEqualTo(2);
        }

        @Test
        void cachedResultIsRetypedPerCall() {
            stubFlag("new-checkout", 200, fixture("flag-boolean.json"));

            client.isEnabled("new-checkout", USER);
            FlagEvaluation<String> asString = client.evaluate("new-checkout", "x", String.class, USER);

            assertThat(asString.errorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
            assertThat(flagRequests("new-checkout")).isEqualTo(1);
        }

        @Test
        void differentContextIsAMiss() {
            stubFlag("new-checkout", 200, fixture("flag-boolean.json"));

            client.isEnabled("new-checkout", USER);
            client.isEnabled("new-checkout", EvaluationContext.of("user-43"));

            assertThat(flagRequests("new-checkout")).isEqualTo(2);
        }

        @Test
        void errorsAreNotCached() {
            stubFlag("missing-flag", 404, fixture("error-flag-not-found.json"));

            client.isEnabled("missing-flag", USER);
            client.isEnabled("missing-flag", USER);

            assertThat(flagRequests("missing-flag")).isEqualTo(2);
        }

        @Test
        void disabledCacheAlwaysCallsTheEdge() {
            properties.getCache().setEnabled(false);
            DefaultFluxGateClient uncached = DefaultFluxGateClient.create(properties);
            stubFlag("new-checkout", 200, fixture("flag-boolean.json"));

            uncached.isEnabled("new-checkout", USER);
            uncached.isEnabled("new-checkout", USER);

            assertThat(flagRequests("new-checkout")).isEqualTo(2);
        }
    }

    @Nested
    class Bulk {

        @Test
        void returnsAllFlagsWithVersion() {
            edge.stubFor(post(urlEqualTo(BULK_PATH))
                    .withHeader("If-None-Match", absent())
                    .willReturn(aResponse().withStatus(200).withHeader("ETag", BULK_ETAG)
                            .withBody(fixture("bulk-success.json"))));

            BulkEvaluation result = client.bulkEvaluate(USER);

            assertThat(result.isError()).isFalse();
            assertThat(result.changed()).isTrue();
            assertThat(result.version()).isEqualTo(BULK_ETAG);
            assertThat(result.flags()).containsOnlyKeys("banner-color", "max-items", "new-checkout", "old-flag");
            assertThat(result.flags().get("banner-color"))
                    .isEqualTo(new FlagEvaluation<Object>("banner-color", "blue", "blue", "SPLIT", null, null));
            assertThat(result.flags().get("max-items").value()).isEqualTo(42);
            assertThat(result.flags().get("new-checkout").value()).isEqualTo(true);
            assertThat(result.flags().get("old-flag").errorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
            edge.verify(postRequestedFor(urlEqualTo(BULK_PATH))
                    .withHeader("Authorization", equalTo("Bearer " + SDK_KEY))
                    .withRequestBody(equalToJson(
                            "{\"context\": {\"targetingKey\": \"user-42\", \"country\": \"LK\","
                                    + " \"plan\": {\"tier\": \"gold\"}}}")));
        }

        @Test
        void revalidatesWithIfNoneMatchAndReusesFlagsOn304() {
            edge.stubFor(post(urlEqualTo(BULK_PATH))
                    .withHeader("If-None-Match", absent())
                    .willReturn(aResponse().withStatus(200).withHeader("ETag", BULK_ETAG)
                            .withBody(fixture("bulk-success.json"))));
            edge.stubFor(post(urlEqualTo(BULK_PATH))
                    .withHeader("If-None-Match", equalTo(BULK_ETAG))
                    .willReturn(aResponse().withStatus(304).withHeader("ETag", BULK_ETAG)));

            BulkEvaluation first = client.bulkEvaluate(USER);
            BulkEvaluation second = client.bulkEvaluate(USER);

            assertThat(second.changed()).isFalse();
            assertThat(second.version()).isEqualTo(BULK_ETAG);
            assertThat(second.flags()).isEqualTo(first.flags());
            edge.verify(1, postRequestedFor(urlEqualTo(BULK_PATH)).withHeader("If-None-Match", equalTo(BULK_ETAG)));
        }

        @Test
        void sameVersionWithoutA304IsUnchanged() {
            edge.stubFor(post(urlEqualTo(BULK_PATH))
                    .willReturn(aResponse().withStatus(200).withHeader("ETag", BULK_ETAG)
                            .withBody(fixture("bulk-success.json"))));

            assertThat(client.bulkEvaluate(USER).changed()).isTrue();
            assertThat(client.bulkEvaluate(USER).changed()).isFalse();
            assertThat(client.bulkEvaluate(EvaluationContext.of("user-43")).changed()).isTrue();
        }

        @Test
        void usesMetadataVersionWhenEtagHeaderIsMissing() {
            edge.stubFor(post(urlEqualTo(BULK_PATH))
                    .willReturn(aResponse().withStatus(200).withBody(fixture("bulk-success.json"))));

            assertThat(client.bulkEvaluate(USER).version()).isEqualTo(BULK_ETAG);
        }

        @Test
        void unexpected304WithoutSnapshotIsAnError() {
            edge.stubFor(post(urlEqualTo(BULK_PATH)).willReturn(aResponse().withStatus(304)));

            BulkEvaluation result = client.bulkEvaluate(USER);

            assertThat(result.errorCode()).isEqualTo(ErrorCode.GENERAL);
            assertThat(result.flags()).isEmpty();
        }

        @Test
        void unauthorizedBulkReturnsError() {
            edge.stubFor(post(urlEqualTo(BULK_PATH))
                    .willReturn(aResponse().withStatus(401).withBody(fixture("error-unauthorized.json"))));

            BulkEvaluation result = client.bulkEvaluate(USER);

            assertThat(result.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
            assertThat(result.flags()).isEmpty();
            assertThat(result.changed()).isFalse();
        }

        @Test
        void malformedBulkBodyReturnsError() {
            edge.stubFor(post(urlEqualTo(BULK_PATH))
                    .willReturn(aResponse().withStatus(200).withBody("{\"metadata\": {}}")));

            assertThat(client.bulkEvaluate(USER).errorCode()).isEqualTo(ErrorCode.GENERAL);
        }

        @Test
        void bulkRetriesGatewayErrors() {
            edge.stubFor(post(urlEqualTo(BULK_PATH)).willReturn(aResponse().withStatus(503)));

            assertThat(client.bulkEvaluate(USER).errorCode()).isEqualTo(ErrorCode.PROVIDER_NOT_READY);
            edge.verify(3, postRequestedFor(urlEqualTo(BULK_PATH)));
        }

        @Test
        void bulkTransportFailureReturnsError() {
            edge.stubFor(post(urlEqualTo(BULK_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            BulkEvaluation result = client.bulkEvaluate(USER);

            assertThat(result.errorCode()).isEqualTo(ErrorCode.GENERAL);
            assertThat(result.errorMessage()).isNotBlank();
            edge.verify(3, postRequestedFor(urlEqualTo(BULK_PATH)));
        }
    }

    @Nested
    class Health {

        @Test
        void healthyWhenEdgeAnswers200() {
            edge.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200).withBody("OK")));

            assertThat(client.isHealthy()).isTrue();
        }

        @Test
        void unhealthyWhenEdgeAnswers503() {
            edge.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(503).withBody("UNAVAILABLE")));

            assertThat(client.isHealthy()).isFalse();
        }

        @Test
        void unhealthyWhenEdgeIsUnreachable() {
            edge.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

            assertThat(client.isHealthy()).isFalse();
        }
    }
}
