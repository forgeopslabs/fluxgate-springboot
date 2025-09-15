package com.fluxgate.starter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureToggleHealthIndicatorTest {

    @Mock
    private DefaultFeatureToggleClient featureToggleClient;

    private FeatureToggleHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new FeatureToggleHealthIndicator(featureToggleClient);
    }

    @Test
    void shouldReportUpWhenClientIsHealthy() {
        // Given
        when(featureToggleClient.isHealthy()).thenReturn(true);

        // When
        var health = healthIndicator.health();

        // Then
        assertEquals("UP", health.getStatus().getCode());
        assertTrue(health.getDetails().containsKey("status"));
        assertEquals("Edge server is reachable", health.getDetails().get("status"));
        assertTrue(health.getDetails().containsKey("service"));
        assertEquals("fluxgate-edge-server", health.getDetails().get("service"));
    }

    @Test
    void shouldReportDownWhenClientIsNotHealthy() {
        // Given
        when(featureToggleClient.isHealthy()).thenReturn(false);

        // When
        var health = healthIndicator.health();

        // Then
        assertEquals("DOWN", health.getStatus().getCode());
        assertTrue(health.getDetails().containsKey("status"));
        assertEquals("Edge server is not reachable", health.getDetails().get("status"));
        assertTrue(health.getDetails().containsKey("service"));
        assertEquals("fluxgate-edge-server", health.getDetails().get("service"));
    }

    @Test
    void shouldReportDownWhenClientThrowsException() {
        // Given
        RuntimeException exception = new RuntimeException("Connection timeout");
        when(featureToggleClient.isHealthy()).thenThrow(exception);

        // When
        var health = healthIndicator.health();

        // Then
        assertEquals("DOWN", health.getStatus().getCode());
        assertTrue(health.getDetails().containsKey("status"));
        assertEquals("Health check failed", health.getDetails().get("status"));
        assertTrue(health.getDetails().containsKey("service"));
        assertEquals("fluxgate-edge-server", health.getDetails().get("service"));
        assertTrue(health.getDetails().containsKey("error"));
        assertEquals("Connection timeout", health.getDetails().get("error"));
    }

    @Test
    void shouldCallClientExactlyOncePerHealthCheck() {
        // Given
        when(featureToggleClient.isHealthy()).thenReturn(true);

        // When
        healthIndicator.health();

        // Then
        verify(featureToggleClient, times(1)).isHealthy();
    }

    @Test
    void shouldNotCacheHealthResults() {
        // Given
        when(featureToggleClient.isHealthy())
                .thenReturn(true)
                .thenReturn(false);

        // When
        var firstHealth = healthIndicator.health();
        var secondHealth = healthIndicator.health();

        // Then
        assertEquals("UP", firstHealth.getStatus().getCode());
        assertEquals("DOWN", secondHealth.getStatus().getCode());
        verify(featureToggleClient, times(2)).isHealthy();
    }

    @Test
    void shouldCreateHealthIndicatorWithValidClient() {
        // When
        FeatureToggleHealthIndicator indicator = new FeatureToggleHealthIndicator(featureToggleClient);

        // Then
        assertNotNull(indicator);
    }

    @Test
    void shouldHandleFeatureToggleException() {
        // Given
        FeatureToggleException exception = new FeatureToggleException("Service unavailable");
        when(featureToggleClient.isHealthy()).thenThrow(exception);

        // When
        var health = healthIndicator.health();

        // Then
        assertEquals("DOWN", health.getStatus().getCode());
        assertTrue(health.getDetails().containsKey("error"));
        assertEquals("Service unavailable", health.getDetails().get("error"));
    }

    @Test
    void shouldHandleExceptionWithNullMessage() {
        // Arrange
        RuntimeException exception = new RuntimeException((String) null);
        when(featureToggleClient.isHealthy()).thenThrow(exception);

        // Act
        org.springframework.boot.actuate.health.Health health = healthIndicator.health();

        // Assert
        assertEquals(org.springframework.boot.actuate.health.Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().containsKey("status"));
        assertEquals("Health check failed", health.getDetails().get("status"));
        assertTrue(health.getDetails().containsKey("error"));
        assertEquals("Unknown error", health.getDetails().get("error"));
    }

    @Test
    void shouldIncludeServiceNameInAllHealthResponses() {
        // Given
        when(featureToggleClient.isHealthy()).thenReturn(true);

        // When
        var health = healthIndicator.health();

        // Then
        assertTrue(health.getDetails().containsKey("service"));
        assertEquals("fluxgate-edge-server", health.getDetails().get("service"));
    }

    @Test
    void shouldIncludeStatusInAllHealthResponses() {
        // Given
        when(featureToggleClient.isHealthy()).thenReturn(true);

        // When
        var health = healthIndicator.health();

        // Then
        assertTrue(health.getDetails().containsKey("status"));
        assertNotNull(health.getDetails().get("status"));
    }

    @Test
    void shouldPropagateExceptionDetailsInHealthDown() {
        // Given
        RuntimeException exception = new RuntimeException("Network error");
        when(featureToggleClient.isHealthy()).thenThrow(exception);

        // When
        var health = healthIndicator.health();

        // Then
        assertEquals("DOWN", health.getStatus().getCode());
        assertTrue(health.getDetails().containsKey("error"));
        assertEquals("Network error", health.getDetails().get("error"));
    }

    @Test
    void shouldHandleMultipleHealthChecksIndependently() {
        // Given
        when(featureToggleClient.isHealthy())
                .thenReturn(true)
                .thenThrow(new RuntimeException("Temporary error"))
                .thenReturn(false)
                .thenReturn(true);

        // When & Then
        var health1 = healthIndicator.health();
        assertEquals("UP", health1.getStatus().getCode());

        var health2 = healthIndicator.health();
        assertEquals("DOWN", health2.getStatus().getCode());

        var health3 = healthIndicator.health();
        assertEquals("DOWN", health3.getStatus().getCode());

        var health4 = healthIndicator.health();
        assertEquals("UP", health4.getStatus().getCode());

        verify(featureToggleClient, times(4)).isHealthy();
    }

    @Test
    void shouldAlwaysIncludeRequiredHealthDetails() {
        // Given
        when(featureToggleClient.isHealthy()).thenReturn(true);

        // When
        var health = healthIndicator.health();

        // Then
        assertNotNull(health);
        assertNotNull(health.getStatus());
        assertNotNull(health.getDetails());
        assertFalse(health.getDetails().isEmpty());
        assertTrue(health.getDetails().containsKey("service"));
        assertTrue(health.getDetails().containsKey("status"));
    }

    @Test
    void shouldImplementHealthIndicatorInterface() {
        // Given
        FeatureToggleHealthIndicator indicator = new FeatureToggleHealthIndicator(featureToggleClient);

        // When & Then
        // Verify that the health method exists and returns a Health object
        assertNotNull(indicator);
        when(featureToggleClient.isHealthy()).thenReturn(true);

        var health = indicator.health();
        assertNotNull(health);
        assertNotNull(health.getStatus());
    }
}