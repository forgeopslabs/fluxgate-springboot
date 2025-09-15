package com.fluxgate.starter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultFeatureToggleClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Executor asyncExecutor;

    private FeatureToggleProperties properties;
    private DefaultFeatureToggleClient client;

    @BeforeEach
    void setUp() {
        properties = new FeatureToggleProperties();
        properties.setBaseUrl("http://localhost:8081");
        properties.setFallbackEnabled(true);

        client = new DefaultFeatureToggleClient(restTemplate, properties, asyncExecutor);
    }

    @Test
    void isEnabled_shouldReturnTrue_whenFeatureIsEnabled() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        // When
        boolean result = client.isEnabled("test-feature", "prod");

        // Then
        assertTrue(result);
    }

    @Test
    void isEnabled_shouldReturnFalse_whenFeatureIsDisabled() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(false);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        // When
        boolean result = client.isEnabled("test-feature", "prod");

        // Then
        assertFalse(result);
    }

    @Test
    void isEnabled_withContext_shouldReturnTrue_whenFeatureIsEnabled() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        Map<String, String> context = new HashMap<>();
        context.put("userId", "123");
        context.put("region", "us-east");

        // When
        boolean result = client.isEnabled("test-feature", "prod", context);

        // Then
        assertTrue(result);
    }

    @Test
    void isEnabled_withRequest_shouldReturnTrue_whenFeatureIsEnabled() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");

        // When
        boolean result = client.isEnabled(request);

        // Then
        assertTrue(result);
    }

    @Test
    void isEnabledAsync_shouldReturnCompletedFuture_whenFeatureIsEnabled() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        // Mock executor to execute immediately
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(asyncExecutor).execute(any(Runnable.class));

        // When
        CompletableFuture<Boolean> future = client.isEnabledAsync("test-feature", "prod");

        // Then
        assertTrue(future.isDone());
        assertTrue(future.join());
    }

    @Test
    void isEnabledAsync_withContext_shouldReturnCompletedFuture_whenFeatureIsEnabled() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        // Mock executor to execute immediately
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(asyncExecutor).execute(any(Runnable.class));

        Map<String, String> context = new HashMap<>();
        context.put("userId", "456");

        // When
        CompletableFuture<Boolean> future = client.isEnabledAsync("test-feature", "prod", context);

        // Then
        assertTrue(future.isDone());
        assertTrue(future.join());
    }

    @Test
    void isEnabledAsync_withRequest_shouldReturnCompletedFuture_whenFeatureIsEnabled() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        // Mock executor to execute immediately
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(asyncExecutor).execute(any(Runnable.class));

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");

        // When
        CompletableFuture<Boolean> future = client.isEnabledAsync(request);

        // Then
        assertTrue(future.isDone());
        assertTrue(future.join());
    }

    @Test
    void isEnabled_shouldThrowException_whenFeatureKeyIsNull() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> client.isEnabled(null, "prod"));
    }

    @Test
    void isEnabled_shouldThrowException_whenEnvironmentIdIsNull() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> client.isEnabled("test-feature", null));
    }

    @Test
    void isEnabled_shouldThrowException_whenFeatureKeyIsEmpty() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> client.isEnabled("", "prod"));
    }

    @Test
    void isEnabled_shouldThrowException_whenEnvironmentIdIsEmpty() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> client.isEnabled("test-feature", ""));
    }

    @Test
    void isEnabledWithFallback_shouldReturnFallback_whenExceptionOccurs() {
        // Given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenThrow(new RuntimeException("Network error"));

        // When
        boolean result = client.isEnabledWithFallback("test-feature", "prod", true);

        // Then
        assertTrue(result);
    }

    @Test
    void isEnabledWithFallback_withContext_shouldReturnFallback_whenExceptionOccurs() {
        // Given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenThrow(new RuntimeException("Network error"));

        Map<String, String> context = new HashMap<>();
        context.put("userId", "789");

        // When
        boolean result = client.isEnabledWithFallback("test-feature", "prod", context, false);

        // Then
        assertFalse(result);
    }

    @Test
    void isEnabledWithFallback_withRequest_shouldReturnFallback_whenExceptionOccurs() {
        // Given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenThrow(new RuntimeException("Network error"));

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");

        // When
        boolean result = client.isEnabledWithFallback(request, true);

        // Then
        assertTrue(result);
    }

    @Test
    void execute_shouldInvokeConsumer_whenCalled() {
        // Arrange
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class)))
                .thenReturn(ResponseEntity.ok(new FeatureEvaluationResponse(true)));

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");
        @SuppressWarnings("unchecked")
        Consumer<Void> mockConsumer = mock(Consumer.class);

        // Act
        client.execute(request, mockConsumer);

        // Assert
        verify(mockConsumer).accept(null);
    }

    @Test
    void execute_shouldThrowException_whenRequestIsNull() {
        // Given
        @SuppressWarnings("unchecked")
        Consumer<Void> consumer = mock(Consumer.class);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> client.execute(null, consumer));
    }

    @Test
    void execute_shouldThrowException_whenConsumerIsNull() {
        // Given
        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> client.execute(request, null));
    }

    @Test
    void isHealthy_shouldReturnTrue_whenHealthEndpointReturnsOk() {
        // Given
        ResponseEntity<String> healthResponse = new ResponseEntity<>("OK", HttpStatus.OK);
        when(restTemplate.getForEntity("http://localhost:8081/health", String.class))
                .thenReturn(healthResponse);

        // When
        boolean result = client.isHealthy();

        // Then
        assertTrue(result);
    }

    @Test
    void isHealthy_shouldReturnFalse_whenHealthEndpointFails() {
        // Given
        when(restTemplate.getForEntity("http://localhost:8081/health", String.class))
                .thenThrow(new RuntimeException("Connection failed"));

        // When
        boolean result = client.isHealthy();

        // Then
        assertFalse(result);
    }

    @Test
    void isHealthy_shouldReturnFalse_whenHealthEndpointReturnsNon2xxStatus() {
        // Given
        ResponseEntity<String> healthResponse = new ResponseEntity<>("Service Unavailable",
                HttpStatus.SERVICE_UNAVAILABLE);
        when(restTemplate.getForEntity("http://localhost:8081/health", String.class))
                .thenReturn(healthResponse);

        // When
        boolean result = client.isHealthy();

        // Then
        assertFalse(result);
    }

    // Tests for executeAndReturn method
    @Test
    void executeAndReturn_shouldReturnSupplierResult_whenFeatureIsEnabled() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");
        Supplier<String> supplier = () -> "feature-result";

        // When
        String result = client.executeAndReturn(request, supplier);

        // Then
        assertEquals("feature-result", result);
    }

    @Test
    void executeAndReturn_shouldReturnNull_whenFeatureIsDisabled() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(false);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");
        Supplier<String> supplier = () -> "feature-result";

        // When
        String result = client.executeAndReturn(request, supplier);

        // Then
        assertNull(result);
    }

    @Test
    void executeAndReturn_shouldThrowException_whenRequestIsNull() {
        // Given
        Supplier<String> supplier = () -> "feature-result";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> client.executeAndReturn(null, supplier));
    }

    @Test
    void executeAndReturn_shouldThrowException_whenSupplierIsNull() {
        // Given
        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> client.executeAndReturn(request, null));
    }

    @Test
    void executeAndReturn_shouldThrowException_whenFeatureEvaluationFails() {
        // Given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenThrow(new RuntimeException("Network error"));

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");
        Supplier<String> supplier = () -> "feature-result";

        // When & Then
        assertThrows(FeatureToggleException.class, () -> client.executeAndReturn(request, supplier));
    }

    // Tests for executeAndReturnWithFallback method
    @Test
    void executeAndReturnWithFallback_shouldReturnSupplierResult_whenFeatureIsEnabled() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");
        Supplier<String> supplier = () -> "feature-result";
        String fallback = "fallback-result";

        // When
        String result = client.executeAndReturnWithFallback(request, supplier, fallback);

        // Then
        assertEquals("feature-result", result);
    }

    @Test
    void executeAndReturnWithFallback_shouldReturnFallback_whenFeatureIsDisabled() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(false);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");
        Supplier<String> supplier = () -> "feature-result";
        String fallback = "fallback-result";

        // When
        String result = client.executeAndReturnWithFallback(request, supplier, fallback);

        // Then
        assertEquals("fallback-result", result);
    }

    @Test
    void executeAndReturnWithFallback_shouldReturnFallback_whenEvaluationFails() {
        // Given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenThrow(new RuntimeException("Network error"));

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");
        Supplier<String> supplier = () -> "feature-result";
        String fallback = "fallback-result";

        // When
        String result = client.executeAndReturnWithFallback(request, supplier, fallback);

        // Then
        assertEquals("fallback-result", result);
    }

    @Test
    void executeAndReturnWithFallback_shouldThrowException_whenEvaluationFailsAndFallbackDisabled() {
        // Given
        properties.setFallbackEnabled(false);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenThrow(new RuntimeException("Network error"));

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");
        Supplier<String> supplier = () -> "feature-result";
        String fallback = "fallback-result";

        // When & Then
        assertThrows(FeatureToggleException.class,
                () -> client.executeAndReturnWithFallback(request, supplier, fallback));
    }

    @Test
    void executeAndReturnWithFallback_shouldThrowException_whenRequestIsNull() {
        // Given
        Supplier<String> supplier = () -> "feature-result";
        String fallback = "fallback-result";

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> client.executeAndReturnWithFallback(null, supplier, fallback));
    }

    @Test
    void executeAndReturnWithFallback_shouldThrowException_whenSupplierIsNull() {
        // Given
        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");
        String fallback = "fallback-result";

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> client.executeAndReturnWithFallback(request, null, fallback));
    }

    @Test
    void executeAndReturnWithFallback_shouldHandleNullFallback() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(false);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");
        Supplier<String> supplier = () -> "feature-result";

        // When
        String result = client.executeAndReturnWithFallback(request, supplier, null);

        // Then
        assertNull(result);
    }

    @Test
    void executeAndReturn_shouldReturnComplexObject_whenFeatureIsEnabled() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                eq("http://localhost:8081/evaluate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");
        Supplier<TestObject> supplier = () -> new TestObject("test", 42);

        // When
        TestObject result = client.executeAndReturn(request, supplier);

        // Then
        assertNotNull(result);
        assertEquals("test", result.name);
        assertEquals(42, result.value);
    }

    @Test
    void shouldConstructCorrectHttpHeaders() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        // When
        client.isEnabled("test-feature", "prod");

        // Then
        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                argThat(httpEntity -> {
                    HttpHeaders headers = httpEntity.getHeaders();
                    return MediaType.APPLICATION_JSON.equals(headers.getContentType());
                }),
                eq(FeatureEvaluationResponse.class));
    }

    @Test
    void shouldHandleNullResponseFromServer() {
        // Given
        ResponseEntity<FeatureEvaluationResponse> responseEntity = ResponseEntity.ok(null);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        // When & Then
        assertThrows(FeatureToggleException.class, () -> client.isEnabled("test-feature", "prod"));
    }

    @Test
    void shouldHandleNullContextGracefully() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);
        ResponseEntity<FeatureEvaluationResponse> responseEntity = new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FeatureEvaluationResponse.class))).thenReturn(responseEntity);

        // When
        boolean result = client.isEnabled("test-feature", "prod", (Map<String, String>) null);

        // Then
        assertTrue(result);
    }

    // Helper class for testing complex objects
    private static class TestObject {
        final String name;
        final int value;

        TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}