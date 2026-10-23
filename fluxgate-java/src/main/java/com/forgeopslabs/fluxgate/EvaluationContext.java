package com.forgeopslabs.fluxgate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable evaluation context sent to the edge: a targeting key plus
 * arbitrary JSON-serializable attributes.
 *
 * <pre>{@code
 * EvaluationContext ctx = EvaluationContext.builder()
 *         .targetingKey("user-42")
 *         .attribute("country", "LK")
 *         .attribute("beta", true)
 *         .build();
 * }</pre>
 *
 * <p>
 * The edge requires a non-empty targeting key; evaluations without one fail
 * with {@link ErrorCode#TARGETING_KEY_MISSING}.
 */
public final class EvaluationContext {

    /** Name of the targeting key in the OFREP context object. */
    public static final String TARGETING_KEY = "targetingKey";

    private static final EvaluationContext EMPTY = new EvaluationContext(null, Map.of());

    private final String targetingKey;
    private final Map<String, Object> attributes;

    private EvaluationContext(String targetingKey, Map<String, Object> attributes) {
        this.targetingKey = targetingKey;
        this.attributes = attributes;
    }

    /**
     * @return a context with no targeting key and no attributes
     */
    public static EvaluationContext empty() {
        return EMPTY;
    }

    /**
     * @param targetingKey the targeting key
     * @return a context with only a targeting key
     */
    public static EvaluationContext of(String targetingKey) {
        return builder().targetingKey(targetingKey).build();
    }

    /**
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the targeting key, or {@code null}
     */
    public String getTargetingKey() {
        return targetingKey;
    }

    /**
     * @return the unmodifiable attributes (never {@code null})
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EvaluationContext other)) {
            return false;
        }
        return Objects.equals(targetingKey, other.targetingKey) && attributes.equals(other.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetingKey, attributes);
    }

    @Override
    public String toString() {
        return "EvaluationContext{targetingKey='" + targetingKey + "', attributes=" + attributes.keySet() + '}';
    }

    /**
     * Builder for {@link EvaluationContext}.
     */
    public static final class Builder {

        private String targetingKey;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * @param targetingKey the targeting key (user or entity id)
         * @return this builder
         */
        public Builder targetingKey(String targetingKey) {
            this.targetingKey = targetingKey;
            return this;
        }

        /**
         * @param name  attribute name; must not be {@value #TARGETING_KEY}
         * @param value JSON-serializable value (may be {@code null})
         * @return this builder
         * @throws IllegalArgumentException if the name is null or reserved
         */
        public Builder attribute(String name, Object value) {
            if (name == null) {
                throw new IllegalArgumentException("Attribute name must not be null");
            }
            if (TARGETING_KEY.equals(name)) {
                throw new IllegalArgumentException("Use targetingKey(...) to set the targeting key");
            }
            attributes.put(name, value);
            return this;
        }

        /**
         * @param values attributes to add
         * @return this builder
         */
        public Builder attributes(Map<String, ?> values) {
            values.forEach(this::attribute);
            return this;
        }

        /**
         * @return the immutable context
         */
        public EvaluationContext build() {
            return new EvaluationContext(targetingKey,
                    Collections.unmodifiableMap(new LinkedHashMap<>(attributes)));
        }
    }
}
