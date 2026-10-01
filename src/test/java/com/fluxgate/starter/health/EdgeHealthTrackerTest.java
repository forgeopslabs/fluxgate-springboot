package com.fluxgate.starter.health;

import com.fluxgate.starter.BulkEvaluation;
import com.fluxgate.starter.ErrorCode;
import com.fluxgate.starter.FlagEvaluation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeHealthTrackerTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private final EdgeHealthTracker tracker = new EdgeHealthTracker(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void startsEmpty() {
        assertThat(new EdgeHealthTracker().lastOutcome()).isNull();
        assertThat(tracker.lastProbe()).isNull();
    }

    @Test
    void recordsSuccessfulEvaluations() {
        tracker.onEvaluation(new FlagEvaluation<>("f", true, null, "STATIC", null, null), false, 1);

        assertThat(tracker.lastOutcome())
                .isEqualTo(new EdgeHealthTracker.Outcome("evaluation", true, null, null, NOW));
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = { "UNAUTHORIZED", "PROVIDER_NOT_READY", "GENERAL" })
    void edgeFailuresAreFailures(ErrorCode code) {
        tracker.onEvaluation(FlagEvaluation.error("f", false, code, "detail"), false, 1);

        assertThat(tracker.lastOutcome())
                .isEqualTo(new EdgeHealthTracker.Outcome("evaluation", false, code, "detail", NOW));
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = { "FLAG_NOT_FOUND", "TYPE_MISMATCH", "TARGETING_KEY_MISSING" })
    void answersAboutTheRequestProveTheEdgeIsServing(ErrorCode code) {
        tracker.onEvaluation(FlagEvaluation.error("f", false, code, "detail"), false, 1);

        assertThat(tracker.lastOutcome().success()).isTrue();
        assertThat(tracker.lastOutcome().errorCode()).isNull();
    }

    @Test
    void ignoresCacheHitsAndInvalidContexts() {
        tracker.onEvaluation(FlagEvaluation.error("f", false, ErrorCode.GENERAL, "down"), false, 1);

        tracker.onEvaluation(new FlagEvaluation<>("f", true, null, "STATIC", null, null), true, 1);
        tracker.onEvaluation(FlagEvaluation.error("f", false, ErrorCode.INVALID_CONTEXT, "bad"), false, 1);

        assertThat(tracker.lastOutcome().success()).isFalse();
    }

    @Test
    void recordsBulkOutcomes() {
        tracker.onBulkEvaluation(BulkEvaluation.error(ErrorCode.UNAUTHORIZED, "rejected"));
        assertThat(tracker.lastOutcome())
                .isEqualTo(new EdgeHealthTracker.Outcome("bulk", false, ErrorCode.UNAUTHORIZED, "rejected", NOW));

        tracker.onBulkEvaluation(new BulkEvaluation(Map.of(), "v1", true, null, null));
        assertThat(tracker.lastOutcome().success()).isTrue();
    }

    @Test
    void recordsProbes() {
        tracker.recordProbe(false);

        assertThat(tracker.lastProbe()).isEqualTo(new EdgeHealthTracker.Probe(false, NOW));
    }
}
