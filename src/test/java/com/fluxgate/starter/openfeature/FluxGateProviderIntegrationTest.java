package com.fluxgate.starter.openfeature;

import com.fluxgate.starter.FeatureToggleProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import com.fluxgate.starter.DefaultFeatureToggleClient;
import com.fluxgate.starter.FluxGateClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FluxGateProvider using WireMock to simulate
 * the FluxGate Edge Server and verifying full OpenFeature SDK flow.
 */
class FluxGateProviderIntegrationTest {

    private static WireMockServer wireMockServer;
    private FluxGateProvider provider;
    private Client openFeatureClient;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
                .dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        wireMockServer.resetAll();

        // Create properties pointing to WireMock
        FeatureToggleProperties properties = new FeatureToggleProperties();
        properties.setBaseUrl("http://localhost:" + wireMockServer.port());
        properties.setConnectionTimeout(Duration.ofSeconds(5));
        properties.setReadTimeout(Duration.ofSeconds(5));
        properties.setFallbackEnabled(true);

        // Create RestTemplate
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(properties.getConnectionTimeout())
                .setReadTimeout(properties.getReadTimeout())
                .build();

        // Create FluxGateClient
        FluxGateClient fluxGateClient = new DefaultFeatureToggleClient(
                restTemplate, properties, Executors.newSingleThreadExecutor());

        // Create provider options
        FluxGateProviderOptions options = FluxGateProviderOptions.builder()
                .defaultEnvironmentId("integration-test")
                .environmentContextKey("environment")
                .build();

        // Create provider
        provider = new FluxGateProvider(fluxGateClient, options);

        // Register with OpenFeature - use unique domain to avoid conflicts
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait("integration-test-domain", provider);
        openFeatureClient = api.getClient("integration-test-domain");
    }

    @AfterEach
    void tearDown() {
        OpenFeatureAPI.getInstance().shutdown();
    }

    @Test
    void fullFlow_shouldEvaluateBooleanFlagAsTrue() {
        // Stub the edge server response
        stubFor(post(urlEqualTo("/evaluate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"enabled\": true}")));

        // Evaluate flag via OpenFeature
        boolean result = openFeatureClient.getBooleanValue("feature-x", false);

        assertTrue(result);
        verify(postRequestedFor(urlEqualTo("/evaluate"))
                .withRequestBody(containing("\"feature_key\":\"feature-x\"")));
    }

    @Test
    void fullFlow_shouldEvaluateBooleanFlagAsFalse() {
        stubFor(post(urlEqualTo("/evaluate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"enabled\": false}")));

        boolean result = openFeatureClient.getBooleanValue("disabled-feature", true);

        assertFalse(result);
    }

    @Test
    void fullFlow_shouldPassContextToServer() {
        stubFor(post(urlEqualTo("/evaluate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"enabled\": true}")));

        Map<String, Value> attributes = new HashMap<>();
        attributes.put("userId", new Value("user-123"));
        attributes.put("tier", new Value("premium"));
        EvaluationContext ctx = new ImmutableContext("target-key", attributes);

        openFeatureClient.getBooleanValue("context-feature", false, ctx);

        verify(postRequestedFor(urlEqualTo("/evaluate"))
                .withRequestBody(containing("userId"))
                .withRequestBody(containing("user-123")));
    }

    @Test
    void fullFlow_shouldUseEnvironmentFromContext() {
        stubFor(post(urlEqualTo("/evaluate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"enabled\": true}")));

        Map<String, Value> attributes = new HashMap<>();
        attributes.put("environment", new Value("production"));
        EvaluationContext ctx = new ImmutableContext(attributes);

        openFeatureClient.getBooleanValue("env-feature", false, ctx);

        verify(postRequestedFor(urlEqualTo("/evaluate"))
                .withRequestBody(containing("\"environment_id\":\"production\"")));
    }

    @Test
    void fullFlow_shouldReturnDefaultOnServerError() {
        stubFor(post(urlEqualTo("/evaluate"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        // Should return default value on error
        boolean result = openFeatureClient.getBooleanValue("error-feature", true);

        assertTrue(result); // Default value
    }

    @Test
    void fullFlow_shouldReturnDefaultOnNotFound() {
        stubFor(post(urlEqualTo("/evaluate"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Feature not found")));

        boolean result = openFeatureClient.getBooleanValue("unknown-feature", false);

        assertFalse(result); // Default value
    }

    @Test
    void fullFlow_shouldGetFlagDetails() {
        stubFor(post(urlEqualTo("/evaluate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"enabled\": true}")));

        FlagEvaluationDetails<Boolean> details = openFeatureClient.getBooleanDetails(
                "detailed-feature", false);

        assertTrue(details.getValue());
        assertEquals("detailed-feature", details.getFlagKey());
        assertNotNull(details.getReason());
    }

    @Test
    void fullFlow_shouldEvaluateStringFlag() {
        stubFor(post(urlEqualTo("/evaluate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"enabled\": true}")));

        String result = openFeatureClient.getStringValue("string-feature", "default");

        assertEquals("true", result);
    }

    @Test
    void fullFlow_shouldEvaluateIntegerFlag() {
        stubFor(post(urlEqualTo("/evaluate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"enabled\": true}")));

        Integer result = openFeatureClient.getIntegerValue("int-feature", 0);

        assertEquals(1, result);
    }

    @Test
    void fullFlow_shouldEvaluateDoubleFlag() {
        stubFor(post(urlEqualTo("/evaluate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"enabled\": false}")));

        Double result = openFeatureClient.getDoubleValue("double-feature", 99.9);

        assertEquals(0.0, result);
    }
}
