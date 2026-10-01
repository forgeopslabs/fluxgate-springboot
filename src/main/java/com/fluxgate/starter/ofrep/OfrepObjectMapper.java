package com.fluxgate.starter.ofrep;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The JSON mapper used for OFREP request and response bodies.
 *
 * <p>
 * {@code java.time} values in the evaluation context (for example an
 * OpenFeature {@code Instant} attribute) are written as ISO-8601 strings, such
 * as {@code "2026-10-01T12:00:00Z"} and {@code "2026-10-01"}, not as numeric
 * timestamps.
 */
public final class OfrepObjectMapper {

    private OfrepObjectMapper() {
    }

    /**
     * @return a new mapper with {@code java.time} support and ISO-8601 dates
     */
    public static ObjectMapper create() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
