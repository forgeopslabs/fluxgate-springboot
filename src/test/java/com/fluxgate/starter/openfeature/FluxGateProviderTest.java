package com.fluxgate.starter.openfeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxgate.starter.BulkEvaluation;
import com.fluxgate.starter.FlagEvaluation;
import com.fluxgate.starter.FluxGateClient;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.InvalidContextError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import dev.openfeature.sdk.exceptions.TargetingKeyMissingError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FluxGateProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final BulkEvaluation BULK = new BulkEvaluation(Map.of(), "v1", true, null, null);

    @Mock
    private FluxGateClient client;
    @Mock
    private ScheduledExecutorService scheduler;

    private FluxGateProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FluxGateProvider(client, Duration.ofSeconds(30), () -> scheduler);
    }

    private static JsonNode json(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Nested
    class Construction {

        @Test
        void rejectsNullClient() {
            assertThatThrownBy(() -> new FluxGateProvider(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullOrNegativeInterval() {
            assertThatThrownBy(() -> new FluxGateProvider(client, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FluxGateProvider(client, Duration.ofSeconds(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void metadataName() {
            assertThat(new FluxGateProvider(client).getMetadata().getName()).isEqualTo("FluxGate");
        }
    }

    @Nested
    class TypedEvaluations {

        @Test
        void booleanEvaluationMapsValueVariantAndReason() {
            when(client.evaluate(eq("f"), eq(false), eq(Boolean.class), any()))
                    .thenReturn(new FlagEvaluation<>("f", true, "on", "TARGETING_MATCH", null, null));

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation("f", false, null);

            assertThat(result.getValue()).isTrue();
            assertThat(result.getVariant()).isEqualTo("on");
            assertThat(result.getReason()).isEqualTo("TARGETING_MATCH");
            assertThat(result.getErrorCode()).isNull();
            assertThat(result.getFlagMetadata().isEmpty()).isTrue();
        }

        @Test
        void primitiveMetadataBecomesFlagMetadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("experiment", "banner-2026");
            metadata.put("sticky", true);
            metadata.put("cohort", 7);
            metadata.put("since", 1_790_000_000_000L);
            metadata.put("weight", 0.25);
            metadata.put("huge", new BigInteger("123456789012345678901234567890"));
            metadata.put("nested", Map.of("a", 1));
            metadata.put("list", List.of(1));
            metadata.put("none", null);
            when(client.evaluate(eq("f"), eq(false), eq(Boolean.class), any()))
                    .thenReturn(new FlagEvaluation<>("f", true, "on", "STATIC", null, null, metadata));

            ImmutableMetadata flagMetadata = provider.getBooleanEvaluation("f", false, null)
                    .getFlagMetadata();

            assertThat(flagMetadata.getString("experiment")).isEqualTo("banner-2026");
            assertThat(flagMetadata.getBoolean("sticky")).isTrue();
            assertThat(flagMetadata.getInteger("cohort")).isEqualTo(7);
            assertThat(flagMetadata.getLong("since")).isEqualTo(1_790_000_000_000L);
            assertThat(flagMetadata.getDouble("weight")).isEqualTo(0.25);
            assertThat(flagMetadata.getDouble("huge")).isEqualTo(1.2345678901234568E29);
            assertThat(flagMetadata.asUnmodifiableMap()).containsOnlyKeys("experiment", "sticky", "cohort", "since",
                    "weight", "huge");
        }

        @Test
        void stringIntegerAndDoubleDelegateWithTheirOwnType() {
            when(client.evaluate(eq("s"), eq("d"), eq(String.class), any()))
                    .thenReturn(new FlagEvaluation<>("s", "blue", "blue", "SPLIT", null, null));
            when(client.evaluate(eq("i"), eq(0), eq(Integer.class), any()))
                    .thenReturn(new FlagEvaluation<>("i", 42, "forty-two", "STATIC", null, null));
            when(client.evaluate(eq("d"), eq(0.0), eq(Double.class), any()))
                    .thenReturn(new FlagEvaluation<>("d", 0.15, "fifteen", "DISABLED", null, null));

            ProviderEvaluation<String> s = provider.getStringEvaluation("s", "d", null);
            ProviderEvaluation<Integer> i = provider.getIntegerEvaluation("i", 0, null);
            ProviderEvaluation<Double> d = provider.getDoubleEvaluation("d", 0.0, null);

            assertThat(s.getValue()).isEqualTo("blue");
            assertThat(s.getReason()).isEqualTo("SPLIT");
            assertThat(i.getValue()).isEqualTo(42);
            assertThat(i.getVariant()).isEqualTo("forty-two");
            assertThat(d.getValue()).isEqualTo(0.15);
            assertThat(d.getReason()).isEqualTo("DISABLED");
        }

        @Test
        void typeMismatchKeepsDefaultInsteadOfCoercing() {
            when(client.evaluate(eq("i"), eq("none"), eq(String.class), any()))
                    .thenReturn(FlagEvaluation.error("i", "none", com.fluxgate.starter.ErrorCode.TYPE_MISMATCH,
                            "Flag 'i' has a number value, expected String"));

            ProviderEvaluation<String> result = provider.getStringEvaluation("i", "none", null);

            assertThat(result.getValue()).isEqualTo("none");
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
            assertThat(result.getReason()).isEqualTo("ERROR");
        }
    }

    @Nested
    class ObjectEvaluations {

        private void edgeReturns(JsonNode value, String variant, String reason) {
            when(client.evaluate(eq("o"), eq(null), eq(JsonNode.class), any()))
                    .thenReturn(new FlagEvaluation<>("o", value, variant, reason, null, null));
        }

        @Test
        void objectBecomesStructureWithTypedMembers() {
            edgeReturns(json("{\"color\":\"dark\",\"fontSize\":14,\"ratio\":0.5,\"big\":5000000000,"
                    + "\"beta\":true,\"tags\":[\"a\",1],\"none\":null,\"nested\":{\"x\":1}}"), "dark", "SPLIT");

            ProviderEvaluation<Value> result = provider.getObjectEvaluation("o", new Value("fallback"), null);

            Structure theme = result.getValue().asStructure();
            assertThat(theme.getValue("color").asString()).isEqualTo("dark");
            assertThat(theme.getValue("fontSize").asObject()).isEqualTo(14);
            assertThat(theme.getValue("ratio").asDouble()).isEqualTo(0.5);
            assertThat(theme.getValue("big").asDouble()).isEqualTo(5.0E9);
            assertThat(theme.getValue("beta").asBoolean()).isTrue();
            assertThat(theme.getValue("tags").asList()).containsExactly(new Value("a"), new Value(1));
            assertThat(theme.getValue("none").isNull()).isTrue();
            assertThat(theme.getValue("nested").asStructure().getValue("x").asInteger()).isEqualTo(1);
            assertThat(result.getVariant()).isEqualTo("dark");
            assertThat(result.getReason()).isEqualTo("SPLIT");
            assertThat(result.getErrorCode()).isNull();
        }

        @Test
        void nonObjectJsonKeepsItsType() {
            edgeReturns(json("\"plain\""), "v", "STATIC");

            assertThat(provider.getObjectEvaluation("o", new Value(), null).getValue()).isEqualTo(new Value("plain"));
        }

        @Test
        void trueWithoutVariantIsAValueNotTheOffMarker() {
            edgeReturns(json("true"), null, "STATIC");

            assertThat(provider.getObjectEvaluation("o", new Value(), null).getValue()).isEqualTo(new Value(true));
        }

        @Test
        void offMarkerReturnsCallerDefaultWithEdgeReason() {
            edgeReturns(json("false"), null, "DISABLED");
            Value fallback = new Value(new ImmutableStructure(Map.of("color", new Value("light"))));

            ProviderEvaluation<Value> result = provider.getObjectEvaluation("o", fallback, null);

            assertThat(result.getValue()).isSameAs(fallback);
            assertThat(result.getReason()).isEqualTo("DISABLED");
            assertThat(result.getVariant()).isNull();
            assertThat(result.getErrorCode()).isNull();
        }

        @Test
        void falseWithVariantIsAValue() {
            edgeReturns(json("false"), "off", "TARGETING_MATCH");

            assertThat(provider.getObjectEvaluation("o", new Value("d"), null).getValue()).isEqualTo(new Value(false));
        }

        @Test
        void missingValueReturnsDefault() {
            edgeReturns(null, null, "DEFAULT");
            Value fallback = new Value("fallback");

            ProviderEvaluation<Value> result = provider.getObjectEvaluation("o", fallback, null);

            assertThat(result.getValue()).isSameAs(fallback);
            assertThat(result.getReason()).isEqualTo("DEFAULT");
        }

        @Test
        void errorKeepsDefault() {
            when(client.evaluate(eq("o"), eq(null), eq(JsonNode.class), any()))
                    .thenReturn(FlagEvaluation.error("o", null, com.fluxgate.starter.ErrorCode.FLAG_NOT_FOUND, "gone"));
            Value fallback = new Value("fallback");

            ProviderEvaluation<Value> result = provider.getObjectEvaluation("o", fallback, null);

            assertThat(result.getValue()).isSameAs(fallback);
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
            assertThat(result.getErrorMessage()).isEqualTo("gone");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "FLAG_NOT_FOUND, FLAG_NOT_FOUND",
            "TYPE_MISMATCH, TYPE_MISMATCH",
            "TARGETING_KEY_MISSING, TARGETING_KEY_MISSING",
            "INVALID_CONTEXT, INVALID_CONTEXT",
            "PROVIDER_NOT_READY, PROVIDER_NOT_READY",
            "UNAUTHORIZED, GENERAL",
            "GENERAL, GENERAL" })
    void mapsErrorCodes(com.fluxgate.starter.ErrorCode code, ErrorCode expected) {
        when(client.evaluate(eq("f"), eq(true), eq(Boolean.class), any()))
                .thenReturn(FlagEvaluation.error("f", true, code, "detail"));

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation("f", true, null);

        assertThat(result.getValue()).isTrue();
        assertThat(result.getReason()).isEqualTo("ERROR");
        assertThat(result.getErrorCode()).isEqualTo(expected);
        assertThat(result.getErrorMessage()).isEqualTo("detail");
    }

    @Test
    void flattensContextIncludingStructuresAndInstants() {
        when(client.evaluate(eq("f"), eq(false), eq(Boolean.class), any()))
                .thenReturn(new FlagEvaluation<>("f", true, null, "STATIC", null, null));
        Instant signedUp = Instant.parse("2026-10-01T12:30:00Z");

        provider.getBooleanEvaluation("f", false, new ImmutableContext("user-1", Map.of(
                "tier", new Value("gold"),
                "signedUpAt", new Value(signedUp),
                "plan", new Value(new ImmutableStructure(Map.of("seats", new Value(5)))),
                "roles", new Value(List.of(new Value("admin"))))));

        verify(client).evaluate(eq("f"), eq(false), eq(Boolean.class), argThat(ctx ->
                "user-1".equals(ctx.getTargetingKey())
                        && ctx.getAttributes().equals(Map.of("tier", "gold", "signedUpAt", signedUp,
                                "plan", Map.of("seats", 5), "roles", List.of("admin")))));
    }

    @Nested
    class Lifecycle {

        @Test
        void initializeChecksHealthRunsOneBulkAndStartsPolling() throws Exception {
            when(client.isHealthy()).thenReturn(true);
            when(client.bulkEvaluate(any())).thenReturn(BULK);

            provider.initialize(new ImmutableContext("tenant-a", Map.of("region", new Value("eu"))));

            verify(client).bulkEvaluate(argThat(ctx -> "tenant-a".equals(ctx.getTargetingKey())
                    && ctx.getAttributes().equals(Map.of("region", "eu"))));
            verify(scheduler).scheduleWithFixedDelay(any(), eq(30_000L), eq(30_000L), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        void initializeWithoutTargetingKeyPollsWithTheProbeKey() throws Exception {
            when(client.isHealthy()).thenReturn(true);
            when(client.bulkEvaluate(any())).thenReturn(BULK);

            provider.initialize(new ImmutableContext());

            verify(client).bulkEvaluate(argThat(ctx -> FluxGateProvider.PROBE_TARGETING_KEY.equals(ctx.getTargetingKey())));
        }

        @Test
        void unhealthyEdgeFailsWithProviderNotReadyButKeepsPolling() {
            when(client.isHealthy()).thenReturn(false);

            assertThatThrownBy(() -> provider.initialize(null))
                    .isInstanceOf(ProviderNotReadyError.class)
                    .hasMessageContaining("/health");

            verify(client, never()).bulkEvaluate(any());
            verify(scheduler).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
        }

        @Test
        void unauthorizedBulkFailsWithGeneralErrorCarryingTheEdgeMessage() {
            when(client.isHealthy()).thenReturn(true);
            when(client.bulkEvaluate(any())).thenReturn(BulkEvaluation.error(
                    com.fluxgate.starter.ErrorCode.UNAUTHORIZED, "Client credentials are missing or invalid"));

            assertThatThrownBy(() -> provider.initialize(null))
                    .isInstanceOf(GeneralError.class)
                    .hasMessageContaining("UNAUTHORIZED")
                    .hasMessageContaining("Client credentials are missing or invalid");
            verify(scheduler).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
        }

        @Test
        void bulkFailureCodesMapToMatchingExceptions() {
            when(client.isHealthy()).thenReturn(true);
            when(client.bulkEvaluate(any()))
                    .thenReturn(BulkEvaluation.error(com.fluxgate.starter.ErrorCode.TARGETING_KEY_MISSING, "tk"))
                    .thenReturn(BulkEvaluation.error(com.fluxgate.starter.ErrorCode.INVALID_CONTEXT, "ctx"))
                    .thenReturn(BulkEvaluation.error(com.fluxgate.starter.ErrorCode.PROVIDER_NOT_READY, "503"));

            assertThatThrownBy(() -> provider.initialize(null)).isInstanceOf(TargetingKeyMissingError.class);
            assertThatThrownBy(() -> provider.initialize(null)).isInstanceOf(InvalidContextError.class);
            assertThatThrownBy(() -> provider.initialize(null)).isInstanceOf(ProviderNotReadyError.class);
        }

        @Test
        void reinitializeStopsThePreviousPoller() throws Exception {
            when(client.isHealthy()).thenReturn(true);
            when(client.bulkEvaluate(any())).thenReturn(BULK);

            provider.initialize(null);
            provider.initialize(null);

            verify(scheduler).shutdownNow();
        }

        @Test
        void shutdownStopsPolling() throws Exception {
            when(client.isHealthy()).thenReturn(true);
            when(client.bulkEvaluate(any())).thenReturn(BULK);
            provider.initialize(null);

            provider.shutdown();
            provider.shutdown();

            verify(scheduler).shutdownNow();
        }

        @Test
        void zeroIntervalDisablesPolling() throws Exception {
            FluxGateProvider unpolled = new FluxGateProvider(client, Duration.ZERO, () -> scheduler);
            when(client.isHealthy()).thenReturn(true);
            when(client.bulkEvaluate(any())).thenReturn(BULK);

            unpolled.initialize(null);
            unpolled.shutdown();

            verifyNoInteractions(scheduler);
        }
    }
}
