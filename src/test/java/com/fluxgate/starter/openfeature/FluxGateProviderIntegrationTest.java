package com.fluxgate.starter.openfeature;

import com.fluxgate.starter.DefaultFluxGateClient;
import com.fluxgate.starter.FeatureToggleProperties;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static com.fluxgate.starter.EdgeContract.FLAG_PATH;
import static com.fluxgate.starter.EdgeContract.SDK_KEY;
import static com.fluxgate.starter.EdgeContract.fixture;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full OpenFeature SDK flow against a WireMock edge speaking OFREP.
 */
class FluxGateProviderIntegrationTest {

    private static final String DOMAIN = "integration-test-domain";

    @RegisterExtension
    static WireMockExtension edge = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private Client openFeatureClient;

    @BeforeEach
    void setUp() {
        edge.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200)));
        FeatureToggleProperties properties = new FeatureToggleProperties();
        properties.setBaseUrl(edge.baseUrl());
        properties.setSdkKey(SDK_KEY);
        properties.getRetry().setMaxAttempts(1);
        properties.getCache().setEnabled(false);

        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(DOMAIN, new FluxGateProvider(DefaultFluxGateClient.create(properties)));
        openFeatureClient = api.getClient(DOMAIN);
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
    void evaluatesBooleanFlagWithContext() {
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
        stubFlag("banner-color", 200, fixture("flag-string.json"));

        FlagEvaluationDetails<String> details = openFeatureClient.getStringDetails("banner-color", "red",
                new ImmutableContext("user-1"));

        assertThat(details.getValue()).isEqualTo("blue");
        assertThat(details.getVariant()).isEqualTo("blue");
        assertThat(details.getReason()).isEqualTo("SPLIT");
    }

    @Test
    void evaluatesNumericAndObjectFlags() {
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
        stubFlag("missing-flag", 404, fixture("error-flag-not-found.json"));

        FlagEvaluationDetails<Boolean> details = openFeatureClient.getBooleanDetails("missing-flag", true,
                new ImmutableContext("user-1"));

        assertThat(details.getValue()).isTrue();
        assertThat(details.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
        assertThat(details.getReason()).isEqualTo("ERROR");
    }

    @Test
    void returnsDefaultOnTypeMismatch() {
        stubFlag("banner-color", 200, fixture("flag-string.json"));

        FlagEvaluationDetails<Boolean> details = openFeatureClient.getBooleanDetails("banner-color", false,
                new ImmutableContext("user-1"));

        assertThat(details.getValue()).isFalse();
        assertThat(details.getErrorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
    }
}
