package com.fluxgate.starter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureToggleExceptionTest {

    @Test
    void shouldCreateExceptionWithMessageOnly() {
        // Given
        String message = "Test error message";

        // When
        FeatureToggleException exception = new FeatureToggleException(message);

        // Then
        assertEquals(message, exception.getMessage());
        assertNull(exception.getFeatureKey());
        assertNull(exception.getEnvironmentId());
        assertEquals(-1, exception.getStatusCode());
        assertFalse(exception.hasStatusCode());
        assertNull(exception.getCause());
    }

    @Test
    void shouldCreateExceptionWithMessageAndCause() {
        // Given
        String message = "Test error message";
        Throwable cause = new RuntimeException("Root cause");

        // When
        FeatureToggleException exception = new FeatureToggleException(message, cause);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertNull(exception.getFeatureKey());
        assertNull(exception.getEnvironmentId());
        assertEquals(-1, exception.getStatusCode());
        assertFalse(exception.hasStatusCode());
    }

    @Test
    void shouldCreateExceptionWithMessageFeatureKeyAndEnvironment() {
        // Given
        String message = "Test error message";
        String featureKey = "test-feature";
        String environmentId = "prod";

        // When
        FeatureToggleException exception = new FeatureToggleException(message, featureKey, environmentId);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(featureKey, exception.getFeatureKey());
        assertEquals(environmentId, exception.getEnvironmentId());
        assertEquals(-1, exception.getStatusCode());
        assertFalse(exception.hasStatusCode());
        assertNull(exception.getCause());
    }

    @Test
    void shouldCreateExceptionWithMessageFeatureKeyEnvironmentAndStatusCode() {
        // Given
        String message = "Test error message";
        String featureKey = "test-feature";
        String environmentId = "prod";
        int statusCode = 404;

        // When
        FeatureToggleException exception = new FeatureToggleException(message, featureKey, environmentId, statusCode);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(featureKey, exception.getFeatureKey());
        assertEquals(environmentId, exception.getEnvironmentId());
        assertEquals(statusCode, exception.getStatusCode());
        assertTrue(exception.hasStatusCode());
        assertNull(exception.getCause());
    }

    @Test
    void shouldCreateExceptionWithMessageCauseFeatureKeyAndEnvironment() {
        // Given
        String message = "Test error message";
        Throwable cause = new RuntimeException("Root cause");
        String featureKey = "test-feature";
        String environmentId = "prod";

        // When
        FeatureToggleException exception = new FeatureToggleException(message, cause, featureKey, environmentId);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(featureKey, exception.getFeatureKey());
        assertEquals(environmentId, exception.getEnvironmentId());
        assertEquals(-1, exception.getStatusCode());
        assertFalse(exception.hasStatusCode());
    }

    @Test
    void shouldCreateExceptionWithAllParameters() {
        // Given
        String message = "Test error message";
        Throwable cause = new RuntimeException("Root cause");
        String featureKey = "test-feature";
        String environmentId = "prod";
        int statusCode = 500;

        // When
        FeatureToggleException exception = new FeatureToggleException(message, cause, featureKey, environmentId,
                statusCode);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(featureKey, exception.getFeatureKey());
        assertEquals(environmentId, exception.getEnvironmentId());
        assertEquals(statusCode, exception.getStatusCode());
        assertTrue(exception.hasStatusCode());
    }

    @Test
    void shouldReturnFalseForHasStatusCodeWhenStatusCodeIsNegative() {
        // Given
        FeatureToggleException exception = new FeatureToggleException("test", "feature", "env", -1);

        // Then
        assertFalse(exception.hasStatusCode());
    }

    @Test
    void shouldReturnFalseForHasStatusCodeWhenStatusCodeIsZero() {
        // Given
        FeatureToggleException exception = new FeatureToggleException("test", "feature", "env", 0);

        // Then
        assertFalse(exception.hasStatusCode());
    }

    @Test
    void shouldReturnTrueForHasStatusCodeWhenStatusCodeIsPositive() {
        // Given
        FeatureToggleException exception = new FeatureToggleException("test", "feature", "env", 200);

        // Then
        assertTrue(exception.hasStatusCode());
    }

    @Test
    void shouldGenerateCorrectToStringWithMinimalInformation() {
        // Given
        String message = "Simple error";
        FeatureToggleException exception = new FeatureToggleException(message);

        // When
        String result = exception.toString();

        // Then
        assertEquals("FeatureToggleException{message='Simple error'}", result);
    }

    @Test
    void shouldGenerateCorrectToStringWithFeatureKeyAndEnvironment() {
        // Given
        String message = "Feature error";
        String featureKey = "my-feature";
        String environmentId = "staging";
        FeatureToggleException exception = new FeatureToggleException(message, featureKey, environmentId);

        // When
        String result = exception.toString();

        // Then
        assertEquals(
                "FeatureToggleException{message='Feature error', featureKey='my-feature', environmentId='staging'}",
                result);
    }

    @Test
    void shouldGenerateCorrectToStringWithAllInformation() {
        // Given
        String message = "Complete error";
        String featureKey = "complete-feature";
        String environmentId = "production";
        int statusCode = 401;
        FeatureToggleException exception = new FeatureToggleException(message, featureKey, environmentId, statusCode);

        // When
        String result = exception.toString();

        // Then
        assertEquals(
                "FeatureToggleException{message='Complete error', featureKey='complete-feature', environmentId='production', statusCode=401}",
                result);
    }

    @Test
    void shouldGenerateCorrectToStringWithOnlyFeatureKey() {
        // Given
        String message = "Feature only error";
        String featureKey = "feature-only";
        FeatureToggleException exception = new FeatureToggleException(message, featureKey, null);

        // When
        String result = exception.toString();

        // Then
        assertEquals("FeatureToggleException{message='Feature only error', featureKey='feature-only'}", result);
    }

    @Test
    void shouldGenerateCorrectToStringWithOnlyEnvironment() {
        // Given
        String message = "Environment only error";
        String environmentId = "env-only";
        FeatureToggleException exception = new FeatureToggleException(message, null, environmentId);

        // When
        String result = exception.toString();

        // Then
        assertEquals("FeatureToggleException{message='Environment only error', environmentId='env-only'}", result);
    }

    @Test
    void shouldGenerateCorrectToStringWithOnlyStatusCode() {
        // Given
        String message = "Status code error";
        int statusCode = 404;
        FeatureToggleException exception = new FeatureToggleException(message, null, null, statusCode);

        // When
        String result = exception.toString();

        // Then
        assertEquals("FeatureToggleException{message='Status code error', statusCode=404}", result);
    }

    @Test
    void shouldHandleNullMessageInToString() {
        // Given
        FeatureToggleException exception = new FeatureToggleException(null, "feature", "env", 500);

        // When
        String result = exception.toString();

        // Then
        assertEquals(
                "FeatureToggleException{message='null', featureKey='feature', environmentId='env', statusCode=500}",
                result);
    }

    @Test
    void shouldBeInstanceOfRuntimeException() {
        // Given
        FeatureToggleException exception = new FeatureToggleException("test");

        // Then
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void shouldPreserveCauseInExceptionChain() {
        // Given
        RuntimeException rootCause = new RuntimeException("Root cause message");
        FeatureToggleException exception = new FeatureToggleException("Wrapper message", rootCause, "feature", "env",
                500);

        // Then
        assertEquals(rootCause, exception.getCause());
        assertEquals("Root cause message", exception.getCause().getMessage());
    }

    @Test
    void shouldAllowNullFeatureKeyAndEnvironment() {
        // Given & When
        FeatureToggleException exception = new FeatureToggleException("test", null, null, 404);

        // Then
        assertNull(exception.getFeatureKey());
        assertNull(exception.getEnvironmentId());
        assertEquals(404, exception.getStatusCode());
        assertTrue(exception.hasStatusCode());
    }
}