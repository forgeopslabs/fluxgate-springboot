package com.fluxgate.starter.openfeature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FluxGateProviderOptions.
 */
class FluxGateProviderOptionsTest {

    @Test
    void defaults_shouldReturnDefaultValues() {
        FluxGateProviderOptions options = FluxGateProviderOptions.defaults();

        assertEquals("default", options.getDefaultEnvironmentId());
        assertEquals("environment", options.getEnvironmentContextKey());
    }

    @Test
    void builder_shouldSetCustomDefaultEnvironment() {
        FluxGateProviderOptions options = FluxGateProviderOptions.builder()
                .defaultEnvironmentId("production")
                .build();

        assertEquals("production", options.getDefaultEnvironmentId());
    }

    @Test
    void builder_shouldSetCustomEnvironmentContextKey() {
        FluxGateProviderOptions options = FluxGateProviderOptions.builder()
                .environmentContextKey("env")
                .build();

        assertEquals("env", options.getEnvironmentContextKey());
    }

    @Test
    void builder_shouldSetAllOptions() {
        FluxGateProviderOptions options = FluxGateProviderOptions.builder()
                .defaultEnvironmentId("staging")
                .environmentContextKey("environmentId")
                .build();

        assertEquals("staging", options.getDefaultEnvironmentId());
        assertEquals("environmentId", options.getEnvironmentContextKey());
    }

    @Test
    void builder_shouldUseDefaults_whenNullProvided() {
        FluxGateProviderOptions options = FluxGateProviderOptions.builder()
                .defaultEnvironmentId(null)
                .environmentContextKey(null)
                .build();

        assertEquals("default", options.getDefaultEnvironmentId());
        assertEquals("environment", options.getEnvironmentContextKey());
    }

    @Test
    void toString_shouldContainAllFields() {
        FluxGateProviderOptions options = FluxGateProviderOptions.builder()
                .defaultEnvironmentId("test-env")
                .environmentContextKey("env_key")
                .build();

        String str = options.toString();
        assertTrue(str.contains("test-env"));
        assertTrue(str.contains("env_key"));
        assertTrue(str.contains("FluxGateProviderOptions"));
    }
}
