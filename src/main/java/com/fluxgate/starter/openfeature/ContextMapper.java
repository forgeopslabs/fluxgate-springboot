package com.fluxgate.starter.openfeature;

import com.fluxgate.starter.EvaluationContext;

import java.util.Map;

/**
 * Maps an OpenFeature evaluation context to the edge's context: the targeting
 * key plus every other attribute, flattened to plain Java values (structures
 * become maps, lists become lists, instants stay {@link java.time.Instant} and
 * are sent as ISO-8601 strings).
 */
final class ContextMapper {

    private ContextMapper() {
    }

    static EvaluationContext toFluxGate(dev.openfeature.sdk.EvaluationContext context) {
        if (context == null) {
            return EvaluationContext.empty();
        }
        EvaluationContext.Builder builder = EvaluationContext.builder().targetingKey(context.getTargetingKey());
        for (Map.Entry<String, Object> entry : context.asObjectMap().entrySet()) {
            if (!EvaluationContext.TARGETING_KEY.equals(entry.getKey())) {
                builder.attribute(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }
}
