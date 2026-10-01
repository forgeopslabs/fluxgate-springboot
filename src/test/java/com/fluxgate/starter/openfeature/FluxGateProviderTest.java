package com.fluxgate.starter.openfeature;

import com.fluxgate.starter.FeatureEvaluationRequest;
import com.fluxgate.starter.FeatureToggleException;
import com.fluxgate.starter.FluxGateClient;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FluxGateProvider - OpenFeature FeatureProvider implementation.
 */
@ExtendWith(MockitoExtension.class)
class FluxGateProviderTest {

    @Mock
    private FluxGateClient fluxGateClient;

    private FluxGateProvider provider;
    private FluxGateProviderOptions options;

    @BeforeEach
    void setUp() {
        options = FluxGateProviderOptions.builder()
                .defaultEnvironmentId("test-env")
                .environmentContextKey("environment")
                .build();
        provider = new FluxGateProvider(fluxGateClient, options);
    }

    // ==================== Constructor Tests ====================

    @Test
    void constructor_shouldThrowException_whenClientIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new FluxGateProvider(null));
    }

    @Test
    void constructor_shouldThrowException_whenOptionsIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new FluxGateProvider(fluxGateClient, null));
    }

    @Test
    void constructor_shouldCreateProvider_withDefaultOptions() {
        FluxGateProvider defaultProvider = new FluxGateProvider(fluxGateClient);
        assertNotNull(defaultProvider);
        assertEquals("FluxGate", defaultProvider.getMetadata().getName());
    }

    // ==================== Metadata Tests ====================

    @Test
    void getMetadata_shouldReturnProviderName() {
        assertEquals("FluxGate", provider.getMetadata().getName());
    }

    // ==================== Boolean Evaluation Tests ====================

    @Test
    void getBooleanEvaluation_shouldReturnTrue_whenFeatureEnabled() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(true);

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "test-feature", false, null);

        assertTrue(result.getValue());
        assertEquals(Reason.TARGETING_MATCH.toString(), result.getReason());
        assertNull(result.getErrorCode());
    }

    @Test
    void getBooleanEvaluation_shouldReturnFalse_whenFeatureDisabled() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(false);

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "test-feature", true, null);

        assertFalse(result.getValue());
        assertEquals(Reason.TARGETING_MATCH.toString(), result.getReason());
    }

    @Test
    void getBooleanEvaluation_shouldUseEnvironmentFromContext() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(true);

        Map<String, Value> attributes = new HashMap<>();
        attributes.put("environment", new Value("production"));
        EvaluationContext ctx = new ImmutableContext(attributes);

        provider.getBooleanEvaluation("test-feature", false, ctx);

        verify(fluxGateClient).isEnabled(argThat(request -> "production".equals(request.environmentId())));
    }

    @Test
    void getBooleanEvaluation_shouldUseDefaultEnvironment_whenNotInContext() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(true);

        provider.getBooleanEvaluation("test-feature", false, null);

        verify(fluxGateClient).isEnabled(argThat(request -> "test-env".equals(request.environmentId())));
    }

    @Test
    void getBooleanEvaluation_shouldPassContextAttributes() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(true);

        Map<String, Value> attributes = new HashMap<>();
        attributes.put("userId", new Value("user123"));
        attributes.put("region", new Value("us-east"));
        EvaluationContext ctx = new ImmutableContext("targeting-key", attributes);

        provider.getBooleanEvaluation("test-feature", false, ctx);

        verify(fluxGateClient).isEnabled(argThat(request -> {
            var context = request.context();
            return context.stream().anyMatch(c -> "userId".equals(c.key()) && "user123".equals(c.value())) &&
                    context.stream().anyMatch(c -> "region".equals(c.key()) && "us-east".equals(c.value())) &&
                    context.stream().anyMatch(c -> "targetingKey".equals(c.key()) && "targeting-key".equals(c.value()));
        }));
    }

    @Test
    void getBooleanEvaluation_shouldReturnDefaultWithError_whenExceptionThrown() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class)))
                .thenThrow(new FeatureToggleException("Network error", "test-feature", "test-env"));

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "test-feature", true, null);

        assertTrue(result.getValue()); // Default value
        assertEquals(ErrorCode.GENERAL, result.getErrorCode());
        assertEquals(Reason.ERROR.toString(), result.getReason());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void getBooleanEvaluation_shouldReturnFlagNotFound_when404() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class)))
                .thenThrow(new FeatureToggleException("Not found", "test-feature", "test-env", 404));

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "unknown-feature", false, null);

        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.getErrorCode());
    }

    @Test
    void getBooleanEvaluation_shouldReturnProviderNotReady_when401() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class)))
                .thenThrow(new FeatureToggleException("Unauthorized", "test-feature", "test-env", 401));

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "test-feature", false, null);

        assertEquals(ErrorCode.PROVIDER_NOT_READY, result.getErrorCode());
    }

    // ==================== String Evaluation Tests ====================

    @Test
    void getStringEvaluation_shouldReturnTrueAsString_whenFeatureEnabled() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(true);

        ProviderEvaluation<String> result = provider.getStringEvaluation(
                "test-feature", "default", null);

        assertEquals("true", result.getValue());
        assertEquals(Reason.TARGETING_MATCH.toString(), result.getReason());
    }

    @Test
    void getStringEvaluation_shouldReturnFalseAsString_whenFeatureDisabled() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(false);

        ProviderEvaluation<String> result = provider.getStringEvaluation(
                "test-feature", "default", null);

        assertEquals("false", result.getValue());
    }

    @Test
    void getStringEvaluation_shouldReturnDefaultWithError_whenExceptionThrown() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class)))
                .thenThrow(new FeatureToggleException("Error", "test-feature", "test-env"));

        ProviderEvaluation<String> result = provider.getStringEvaluation(
                "test-feature", "fallback", null);

        assertEquals("fallback", result.getValue());
        assertEquals(ErrorCode.GENERAL, result.getErrorCode());
    }

    // ==================== Integer Evaluation Tests ====================

    @Test
    void getIntegerEvaluation_shouldReturn1_whenFeatureEnabled() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(true);

        ProviderEvaluation<Integer> result = provider.getIntegerEvaluation(
                "test-feature", 0, null);

        assertEquals(1, result.getValue());
        assertEquals(Reason.TARGETING_MATCH.toString(), result.getReason());
    }

    @Test
    void getIntegerEvaluation_shouldReturn0_whenFeatureDisabled() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(false);

        ProviderEvaluation<Integer> result = provider.getIntegerEvaluation(
                "test-feature", 99, null);

        assertEquals(0, result.getValue());
    }

    @Test
    void getIntegerEvaluation_shouldReturnDefaultWithError_whenExceptionThrown() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class)))
                .thenThrow(new FeatureToggleException("Error", "test-feature", "test-env"));

        ProviderEvaluation<Integer> result = provider.getIntegerEvaluation(
                "test-feature", 42, null);

        assertEquals(42, result.getValue());
        assertEquals(ErrorCode.GENERAL, result.getErrorCode());
    }

    // ==================== Double Evaluation Tests ====================

    @Test
    void getDoubleEvaluation_shouldReturn1Point0_whenFeatureEnabled() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(true);

        ProviderEvaluation<Double> result = provider.getDoubleEvaluation(
                "test-feature", 0.0, null);

        assertEquals(1.0, result.getValue());
        assertEquals(Reason.TARGETING_MATCH.toString(), result.getReason());
    }

    @Test
    void getDoubleEvaluation_shouldReturn0Point0_whenFeatureDisabled() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(false);

        ProviderEvaluation<Double> result = provider.getDoubleEvaluation(
                "test-feature", 99.9, null);

        assertEquals(0.0, result.getValue());
    }

    @Test
    void getDoubleEvaluation_shouldReturnDefaultWithError_whenExceptionThrown() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class)))
                .thenThrow(new FeatureToggleException("Error", "test-feature", "test-env"));

        ProviderEvaluation<Double> result = provider.getDoubleEvaluation(
                "test-feature", 3.14, null);

        assertEquals(3.14, result.getValue());
        assertEquals(ErrorCode.GENERAL, result.getErrorCode());
    }

    // ==================== Object Evaluation Tests ====================

    @Test
    void getObjectEvaluation_shouldReturnValueTrue_whenFeatureEnabled() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(true);

        ProviderEvaluation<Value> result = provider.getObjectEvaluation(
                "test-feature", new Value(false), null);

        assertTrue(result.getValue().asBoolean());
        assertEquals(Reason.TARGETING_MATCH.toString(), result.getReason());
    }

    @Test
    void getObjectEvaluation_shouldReturnValueFalse_whenFeatureDisabled() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(false);

        ProviderEvaluation<Value> result = provider.getObjectEvaluation(
                "test-feature", new Value(true), null);

        assertFalse(result.getValue().asBoolean());
    }

    @Test
    void getObjectEvaluation_shouldReturnDefaultWithError_whenExceptionThrown() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class)))
                .thenThrow(new FeatureToggleException("Error", "test-feature", "test-env"));

        Value defaultValue = new Value("custom-default");
        ProviderEvaluation<Value> result = provider.getObjectEvaluation(
                "test-feature", defaultValue, null);

        assertEquals(defaultValue, result.getValue());
        assertEquals(ErrorCode.GENERAL, result.getErrorCode());
    }

    // ==================== Initialization and Shutdown Tests ====================

    @Test
    void initialize_shouldCallHealthCheck() throws Exception {
        when(fluxGateClient.isHealthy()).thenReturn(true);

        provider.initialize(null);

        verify(fluxGateClient).isHealthy();
    }

    @Test
    void initialize_shouldNotThrow_whenUnhealthy() throws Exception {
        when(fluxGateClient.isHealthy()).thenReturn(false);

        // Should not throw
        assertDoesNotThrow(() -> provider.initialize(null));
    }

    @Test
    void shutdown_shouldCompleteWithoutError() {
        assertDoesNotThrow(() -> provider.shutdown());
    }

    // ==================== Context Conversion Tests ====================

    @Test
    void getBooleanEvaluation_shouldHandleNumericContext() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(true);

        Map<String, Value> attributes = new HashMap<>();
        attributes.put("count", new Value(42));
        attributes.put("price", new Value(19.99));
        EvaluationContext ctx = new ImmutableContext(attributes);

        provider.getBooleanEvaluation("test-feature", false, ctx);

        verify(fluxGateClient).isEnabled(argThat(request -> {
            var context = request.context();
            return context.stream().anyMatch(c -> "count".equals(c.key())) &&
                    context.stream().anyMatch(c -> "price".equals(c.key()));
        }));
    }

    @Test
    void getBooleanEvaluation_shouldHandleBooleanContext() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(true);

        Map<String, Value> attributes = new HashMap<>();
        attributes.put("premium", new Value(true));
        EvaluationContext ctx = new ImmutableContext(attributes);

        provider.getBooleanEvaluation("test-feature", false, ctx);

        verify(fluxGateClient).isEnabled(argThat(request -> {
            var context = request.context();
            return context.stream().anyMatch(c -> "premium".equals(c.key()) && "true".equals(c.value()));
        }));
    }

    @Test
    void getBooleanEvaluation_shouldSkipEnvironmentKeyInContext() {
        when(fluxGateClient.isEnabled(any(FeatureEvaluationRequest.class))).thenReturn(true);

        Map<String, Value> attributes = new HashMap<>();
        attributes.put("environment", new Value("production"));
        attributes.put("userId", new Value("user123"));
        EvaluationContext ctx = new ImmutableContext(attributes);

        provider.getBooleanEvaluation("test-feature", false, ctx);

        verify(fluxGateClient).isEnabled(argThat(request -> {
            var context = request.context();
            // environment should be used as environmentId, not in context
            return "production".equals(request.environmentId()) &&
                    context.stream().noneMatch(c -> "environment".equals(c.key())) &&
                    context.stream().anyMatch(c -> "userId".equals(c.key()));
        }));
    }
}
