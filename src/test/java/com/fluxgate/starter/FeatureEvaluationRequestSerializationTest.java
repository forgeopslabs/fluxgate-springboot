package com.fluxgate.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify FeatureEvaluationRequest serialization handles null context
 * correctly.
 */
class FeatureEvaluationRequestSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeContextAsEmptyArrayWhenNull() throws Exception {
        // Given
        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod");

        // When
        String json = objectMapper.writeValueAsString(request);

        // Then
        assertTrue(json.contains("\"context\":[]"),
                "Context should be serialized as empty array, not null. JSON: " + json);
        assertFalse(json.contains("\"context\":null"),
                "Context should not be serialized as null. JSON: " + json);
    }

    @Test
    void shouldSerializeContextWithValuesCorrectly() throws Exception {
        // Given
        Map<String, String> contextMap = Map.of("user.id", "123", "user.type", "premium");
        FeatureEvaluationRequest request = FeatureEvaluationRequest.of("test-feature", "prod", contextMap);

        // When
        String json = objectMapper.writeValueAsString(request);

        // Then
        assertTrue(json.contains("\"context\":["),
                "Context should be serialized as array with values. JSON: " + json);
        assertTrue(json.contains("\"key\":\"user.id\""),
                "Context should contain user.id key. JSON: " + json);
        assertTrue(json.contains("\"value\":\"123\""),
                "Context should contain user.id value. JSON: " + json);
    }

    @Test
    void shouldSerializeBuilderCreatedRequestCorrectly() throws Exception {
        // Given
        FeatureEvaluationRequest request = FeatureEvaluationRequest.builder()
                .featureKey("test-feature")
                .environmentId("prod")
                .build();

        // When
        String json = objectMapper.writeValueAsString(request);

        // Then
        assertTrue(json.contains("\"context\":[]"),
                "Builder-created request should have empty context array. JSON: " + json);
    }

    @Test
    void shouldDeserializeCorrectly() throws Exception {
        // Given
        String json = "{\"feature_key\":\"test\",\"environment_id\":\"prod\",\"context\":[],\"client_id\":null,\"client_secret\":null}";

        // When
        FeatureEvaluationRequest request = objectMapper.readValue(json, FeatureEvaluationRequest.class);

        // Then
        assertEquals("test", request.featureKey());
        assertEquals("prod", request.environmentId());
        assertNotNull(request.context());
        assertEquals(0, request.context().size());
    }

    @Test
    void shouldEnsureContextIsNeverNull() {
        // Test all ways to create FeatureEvaluationRequest

        // 1. Simple constructor
        FeatureEvaluationRequest request1 = FeatureEvaluationRequest.of("test", "prod");
        assertNotNull(request1.context());
        assertEquals(0, request1.context().size());

        // 2. Constructor with explicit null context
        FeatureEvaluationRequest request2 = new FeatureEvaluationRequest("test", "prod", null);
        assertNotNull(request2.context());
        assertEquals(0, request2.context().size());

        // 3. Builder without context
        FeatureEvaluationRequest request3 = FeatureEvaluationRequest.builder()
                .featureKey("test")
                .environmentId("prod")
                .build();
        assertNotNull(request3.context());
        assertEquals(0, request3.context().size());

        // 4. Primary constructor with null
        FeatureEvaluationRequest request4 = new FeatureEvaluationRequest("test", "prod", null, null, null);
        assertNotNull(request4.context());
        assertEquals(0, request4.context().size());
    }
}