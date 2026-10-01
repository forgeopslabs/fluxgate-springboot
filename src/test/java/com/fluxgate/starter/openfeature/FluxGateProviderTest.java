package com.fluxgate.starter.openfeature;

import com.fluxgate.starter.FlagEvaluation;
import com.fluxgate.starter.FluxGateClient;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FluxGateProviderTest {

    @Mock
    private FluxGateClient client;

    private FluxGateProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FluxGateProvider(client);
    }

    @Test
    void rejectsNullClient() {
        assertThatThrownBy(() -> new FluxGateProvider(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void metadataName() {
        assertThat(provider.getMetadata().getName()).isEqualTo("FluxGate");
    }

    @Test
    void initializeChecksHealthAndShutdownIsQuiet() throws Exception {
        when(client.isHealthy()).thenReturn(false);

        provider.initialize(new ImmutableContext());
        provider.shutdown();

        verify(client).isHealthy();
    }

    @Test
    void booleanEvaluationMapsValueVariantAndReason() {
        when(client.evaluate(eq("f"), eq(false), eq(Boolean.class), any()))
                .thenReturn(new FlagEvaluation<>("f", true, "on", "TARGETING_MATCH", null, null));

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation("f", false, null);

        assertThat(result.getValue()).isTrue();
        assertThat(result.getVariant()).isEqualTo("on");
        assertThat(result.getReason()).isEqualTo("TARGETING_MATCH");
        assertThat(result.getErrorCode()).isNull();
    }

    @Test
    void typedEvaluationsDelegateWithTheirType() {
        when(client.evaluate(eq("s"), eq("d"), eq(String.class), any()))
                .thenReturn(new FlagEvaluation<>("s", "blue", "blue", "SPLIT", null, null));
        when(client.evaluate(eq("i"), eq(0), eq(Integer.class), any()))
                .thenReturn(new FlagEvaluation<>("i", 42, null, "STATIC", null, null));
        when(client.evaluate(eq("d"), eq(0.0), eq(Double.class), any()))
                .thenReturn(new FlagEvaluation<>("d", 0.15, null, "STATIC", null, null));

        assertThat(provider.getStringEvaluation("s", "d", null).getValue()).isEqualTo("blue");
        assertThat(provider.getIntegerEvaluation("i", 0, null).getValue()).isEqualTo(42);
        assertThat(provider.getDoubleEvaluation("d", 0.0, null).getValue()).isEqualTo(0.15);
    }

    @Test
    void objectEvaluationConvertsToValue() {
        when(client.evaluate(eq("o"), eq(null), eq(Object.class), any()))
                .thenReturn(new FlagEvaluation<>("o", Map.of("color", "dark"), "dark", "SPLIT", null, null));

        ProviderEvaluation<Value> result = provider.getObjectEvaluation("o", new Value("fallback"), null);

        assertThat(result.getValue().asStructure().getValue("color").asString()).isEqualTo("dark");
        assertThat(result.getVariant()).isEqualTo("dark");
    }

    @Test
    void objectEvaluationErrorKeepsDefault() {
        when(client.evaluate(eq("o"), eq(null), eq(Object.class), any()))
                .thenReturn(FlagEvaluation.error("o", null, com.fluxgate.starter.ErrorCode.FLAG_NOT_FOUND, "gone"));
        Value fallback = new Value("fallback");

        ProviderEvaluation<Value> result = provider.getObjectEvaluation("o", fallback, null);

        assertThat(result.getValue()).isSameAs(fallback);
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
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
    void convertsContextWithoutEnvironmentHandling() {
        when(client.evaluate(eq("f"), eq(false), eq(Boolean.class), any()))
                .thenReturn(new FlagEvaluation<>("f", true, null, "STATIC", null, null));

        provider.getBooleanEvaluation("f", false, new ImmutableContext("user-1",
                Map.of("environment", new Value("prod"), "tier", new Value("gold"))));

        verify(client).evaluate(eq("f"), eq(false), eq(Boolean.class), argThat(ctx -> "user-1".equals(ctx.getTargetingKey())
                && ctx.getAttributes().equals(Map.of("environment", "prod", "tier", "gold"))));
    }
}
