package com.fluxgate.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureEvaluationResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateWithDefaultConstructor() {
        // When
        FeatureEvaluationResponse response = new FeatureEvaluationResponse();

        // Then
        assertFalse(response.isEnabled()); // default boolean value is false
    }

    @Test
    void shouldCreateWithEnabledTrue() {
        // When
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);

        // Then
        assertTrue(response.isEnabled());
    }

    @Test
    void shouldCreateWithEnabledFalse() {
        // When
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(false);

        // Then
        assertFalse(response.isEnabled());
    }

    @Test
    void shouldSetEnabledValue() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse();

        // When
        response.setEnabled(true);

        // Then
        assertTrue(response.isEnabled());

        // When
        response.setEnabled(false);

        // Then
        assertFalse(response.isEnabled());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
        // Given
        FeatureEvaluationResponse response1 = new FeatureEvaluationResponse(true);
        FeatureEvaluationResponse response2 = new FeatureEvaluationResponse(true);
        FeatureEvaluationResponse response3 = new FeatureEvaluationResponse(false);

        // Then
        assertEquals(response1, response2);
        assertNotEquals(response1, response3);
        assertNotEquals(response2, response3);
    }

    @Test
    void shouldImplementEqualsWithNull() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);

        // Then
        assertNotEquals(null, response);
    }

    @Test
    void shouldImplementEqualsWithDifferentClass() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);
        String otherObject = "not a response";

        // Then
        assertNotEquals(otherObject, response);
    }

    @Test
    void shouldImplementEqualsSelfReference() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);

        // Then
        assertEquals(response, response);
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
        // Given
        FeatureEvaluationResponse response1 = new FeatureEvaluationResponse(true);
        FeatureEvaluationResponse response2 = new FeatureEvaluationResponse(true);
        FeatureEvaluationResponse response3 = new FeatureEvaluationResponse(false);

        // Then
        assertEquals(response1.hashCode(), response2.hashCode());
        assertNotEquals(response1.hashCode(), response3.hashCode());
    }

    @Test
    void shouldImplementToStringCorrectly() {
        // Given
        FeatureEvaluationResponse enabledResponse = new FeatureEvaluationResponse(true);
        FeatureEvaluationResponse disabledResponse = new FeatureEvaluationResponse(false);

        // When
        String enabledString = enabledResponse.toString();
        String disabledString = disabledResponse.toString();

        // Then
        assertNotNull(enabledString);
        assertNotNull(disabledString);
        assertTrue(enabledString.contains("FeatureEvaluationResponse"));
        assertTrue(enabledString.contains("enabled=true"));
        assertTrue(disabledString.contains("FeatureEvaluationResponse"));
        assertTrue(disabledString.contains("enabled=false"));
    }

    @Test
    void shouldSerializeToJsonCorrectly() throws Exception {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(true);

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertTrue(json.contains("\"enabled\":true"));
    }

    @Test
    void shouldSerializeFalseToJsonCorrectly() throws Exception {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(false);

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertTrue(json.contains("\"enabled\":false"));
    }

    @Test
    void shouldDeserializeFromJsonCorrectly() throws Exception {
        // Given
        String json = "{\"enabled\":true}";

        // When
        FeatureEvaluationResponse response = objectMapper.readValue(json, FeatureEvaluationResponse.class);

        // Then
        assertTrue(response.isEnabled());
    }

    @Test
    void shouldDeserializeFalseFromJsonCorrectly() throws Exception {
        // Given
        String json = "{\"enabled\":false}";

        // When
        FeatureEvaluationResponse response = objectMapper.readValue(json, FeatureEvaluationResponse.class);

        // Then
        assertFalse(response.isEnabled());
    }

    @Test
    void shouldDeserializeEmptyJsonCorrectly() throws Exception {
        // Given
        String json = "{}";

        // When
        FeatureEvaluationResponse response = objectMapper.readValue(json, FeatureEvaluationResponse.class);

        // Then
        assertFalse(response.isEnabled()); // default value
    }

    @Test
    void shouldMaintainStateAfterSerialization() throws Exception {
        // Given
        FeatureEvaluationResponse originalResponse = new FeatureEvaluationResponse(true);

        // When
        String json = objectMapper.writeValueAsString(originalResponse);
        FeatureEvaluationResponse deserializedResponse = objectMapper.readValue(json, FeatureEvaluationResponse.class);

        // Then
        assertEquals(originalResponse, deserializedResponse);
        assertEquals(originalResponse.isEnabled(), deserializedResponse.isEnabled());
    }

    @Test
    void shouldHandlePropertyModificationAfterCreation() {
        // Given
        FeatureEvaluationResponse response = new FeatureEvaluationResponse(false);
        assertFalse(response.isEnabled());

        // When
        response.setEnabled(true);

        // Then
        assertTrue(response.isEnabled());

        // When
        response.setEnabled(false);

        // Then
        assertFalse(response.isEnabled());
    }

    @Test
    void shouldBeMutableObject() {
        // Given
        FeatureEvaluationResponse response1 = new FeatureEvaluationResponse(true);
        FeatureEvaluationResponse response2 = new FeatureEvaluationResponse(true);

        // When
        response1.setEnabled(false);

        // Then
        assertFalse(response1.isEnabled());
        assertTrue(response2.isEnabled()); // should not be affected
        assertNotEquals(response1, response2);
    }
}