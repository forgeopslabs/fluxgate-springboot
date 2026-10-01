package com.fluxgate.starter;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationContextTest {

    @Test
    void builderCopiesAttributes() {
        Map<String, Object> source = new HashMap<>();
        source.put("country", "LK");
        source.put("nickname", null);

        EvaluationContext ctx = EvaluationContext.builder()
                .targetingKey("user-1")
                .attributes(source)
                .attribute("beta", true)
                .build();
        source.put("country", "US");

        assertThat(ctx.getTargetingKey()).isEqualTo("user-1");
        assertThat(ctx.getAttributes()).containsEntry("country", "LK").containsEntry("beta", true)
                .containsEntry("nickname", null);
        assertThatThrownBy(() -> ctx.getAttributes().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equalityUsesTargetingKeyAndAttributes() {
        EvaluationContext a = EvaluationContext.builder().targetingKey("u").attribute("k", 1).build();
        EvaluationContext b = EvaluationContext.builder().targetingKey("u").attribute("k", 1).build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(EvaluationContext.of("u"));
        assertThat(a).isEqualTo(a).isNotEqualTo("u");
        assertThat(EvaluationContext.of("u")).isNotEqualTo(EvaluationContext.of("v"));
        assertThat(EvaluationContext.empty().getTargetingKey()).isNull();
        assertThat(EvaluationContext.empty().getAttributes()).isEmpty();
    }

    @Test
    void rejectsReservedOrNullAttributeNames() {
        EvaluationContext.Builder builder = EvaluationContext.builder();

        assertThatThrownBy(() -> builder.attribute("targetingKey", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.attribute(null, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringHidesAttributeValues() {
        EvaluationContext ctx = EvaluationContext.builder().targetingKey("u").attribute("email", "a@b.c").build();

        assertThat(ctx.toString()).contains("u", "email").doesNotContain("a@b.c");
    }
}
