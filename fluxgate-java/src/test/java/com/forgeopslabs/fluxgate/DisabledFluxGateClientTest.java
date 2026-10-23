package com.forgeopslabs.fluxgate;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DisabledFluxGateClientTest {

    private final FluxGateClient client = new DisabledFluxGateClient();

    @Test
    void returnsDefaultsWithDisabledReasonAndNoError() {
        FlagEvaluation<String> banner = client.evaluate("banner", "grey", String.class, EvaluationContext.of("u"));

        assertThat(banner).isEqualTo(new FlagEvaluation<>("banner", "grey", null, "DISABLED", null, null));
        assertThat(client.isEnabled("f", EvaluationContext.of("u"))).isFalse();
        assertThat(client.getInteger("n", 7)).isEqualTo(7);
        assertThat(client.getObject("o", Map.of("a", 1))).isEqualTo(Map.of("a", 1));
    }

    @Test
    void asyncCompletesImmediately() {
        assertThat(client.evaluateAsync("f", true, Boolean.class, null)).isCompletedWithValueMatching(
                e -> e.value() && "DISABLED".equals(e.reason()) && !e.isError());
    }

    @Test
    void bulkIsEmptyAndHealthIsFalse() {
        BulkEvaluation bulk = client.bulkEvaluate(null);

        assertThat(bulk.flags()).isEmpty();
        assertThat(bulk.isError()).isFalse();
        assertThat(client.isHealthy()).isFalse();
    }
}
