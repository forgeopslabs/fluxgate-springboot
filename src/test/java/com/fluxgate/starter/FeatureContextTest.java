package com.fluxgate.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureContextTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateFeatureContextWithKeyAndValue() {
        // Given
        String key = "user.id";
        String value = "12345";

        // When
        FeatureContext context = new FeatureContext(key, value);

        // Then
        assertEquals(key, context.key());
        assertEquals(value, context.value());
    }

    @Test
    void shouldAllowNullKey() {
        // Given
        String value = "test-value";

        // When
        FeatureContext context = new FeatureContext(null, value);

        // Then
        assertNull(context.key());
        assertEquals(value, context.value());
    }

    @Test
    void shouldAllowNullValue() {
        // Given
        String key = "test-key";

        // When
        FeatureContext context = new FeatureContext(key, null);

        // Then
        assertEquals(key, context.key());
        assertNull(context.value());
    }

    @Test
    void shouldAllowBothNullKeyAndValue() {
        // When
        FeatureContext context = new FeatureContext(null, null);

        // Then
        assertNull(context.key());
        assertNull(context.value());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
        // Given
        FeatureContext context1 = new FeatureContext("key1", "value1");
        FeatureContext context2 = new FeatureContext("key1", "value1");
        FeatureContext context3 = new FeatureContext("key2", "value1");
        FeatureContext context4 = new FeatureContext("key1", "value2");

        // Then
        assertEquals(context1, context2);
        assertNotEquals(context1, context3);
        assertNotEquals(context1, context4);
        assertNotEquals(context3, context4);
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
        // Given
        FeatureContext context1 = new FeatureContext("key1", "value1");
        FeatureContext context2 = new FeatureContext("key1", "value1");
        FeatureContext context3 = new FeatureContext("key2", "value1");

        // Then
        assertEquals(context1.hashCode(), context2.hashCode());
        assertNotEquals(context1.hashCode(), context3.hashCode());
    }

    @Test
    void shouldImplementToStringCorrectly() {
        // Given
        FeatureContext context = new FeatureContext("user.type", "premium");

        // When
        String toString = context.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("user.type"));
        assertTrue(toString.contains("premium"));
        assertTrue(toString.contains("FeatureContext"));
    }

    @Test
    void shouldSerializeToJsonCorrectly() throws Exception {
        // Given
        FeatureContext context = new FeatureContext("user.id", "123");

        // When
        String json = objectMapper.writeValueAsString(context);

        // Then
        assertTrue(json.contains("\"key\":\"user.id\""));
        assertTrue(json.contains("\"value\":\"123\""));
    }

    @Test
    void shouldDeserializeFromJsonCorrectly() throws Exception {
        // Given
        String json = "{\"key\":\"user.type\",\"value\":\"admin\"}";

        // When
        FeatureContext context = objectMapper.readValue(json, FeatureContext.class);

        // Then
        assertEquals("user.type", context.key());
        assertEquals("admin", context.value());
    }

    @Test
    void shouldHandleNullValuesInSerialization() throws Exception {
        // Given
        FeatureContext context = new FeatureContext("test-key", null);

        // When
        String json = objectMapper.writeValueAsString(context);

        // Then
        assertTrue(json.contains("\"key\":\"test-key\""));
        assertTrue(json.contains("\"value\":null"));
    }

    @Test
    void shouldHandleNullKeyInSerialization() throws Exception {
        // Given
        FeatureContext context = new FeatureContext(null, "test-value");

        // When
        String json = objectMapper.writeValueAsString(context);

        // Then
        assertTrue(json.contains("\"key\":null"));
        assertTrue(json.contains("\"value\":\"test-value\""));
    }

    @Test
    void shouldWorkWithEmptyStrings() {
        // Given
        FeatureContext context = new FeatureContext("", "");

        // Then
        assertEquals("", context.key());
        assertEquals("", context.value());
    }

    @Test
    void shouldWorkWithSpecialCharacters() {
        // Given
        FeatureContext context = new FeatureContext("user.特殊字符", "值@#$%");

        // Then
        assertEquals("user.特殊字符", context.key());
        assertEquals("值@#$%", context.value());
    }

    @Test
    void shouldWorkWithLongStrings() {
        // Given
        String longKey = "a".repeat(1000);
        String longValue = "b".repeat(1000);
        FeatureContext context = new FeatureContext(longKey, longValue);

        // Then
        assertEquals(longKey, context.key());
        assertEquals(longValue, context.value());
    }
}