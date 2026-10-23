package com.forgeopslabs.fluxgate.openfeature;

import com.forgeopslabs.fluxgate.BulkEvaluation;
import com.forgeopslabs.fluxgate.ErrorCode;
import com.forgeopslabs.fluxgate.EvaluationContext;
import com.forgeopslabs.fluxgate.FlagEvaluation;
import com.forgeopslabs.fluxgate.FluxGateClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlagChangePollerTest {

    private static final EvaluationContext PROBE = EvaluationContext.of("probe");

    @Mock
    private FluxGateClient client;
    @Mock
    private ScheduledExecutorService scheduler;
    @Mock
    private FlagChangePoller.Listener listener;

    private static FlagEvaluation<Object> flag(String key, Object value, String variant) {
        return new FlagEvaluation<>(key, value, variant, "STATIC", null, null);
    }

    private static BulkEvaluation bulk(String version, FlagEvaluation<?>... flags) {
        Map<String, FlagEvaluation<Object>> map = new LinkedHashMap<>();
        for (FlagEvaluation<?> f : flags) {
            @SuppressWarnings("unchecked")
            FlagEvaluation<Object> typed = (FlagEvaluation<Object>) f;
            map.put(f.key(), typed);
        }
        return new BulkEvaluation(map, version, true, null, null);
    }

    private static final BulkEvaluation V1 = bulk("v1", flag("banner", "blue", "blue"), flag("max", 42, "forty-two"));

    private FlagChangePoller poller(BulkEvaluation baseline) {
        return new FlagChangePoller(client, PROBE, baseline, scheduler, listener);
    }

    @Test
    void unchangedVersionReportsNoChanges() {
        when(client.bulkEvaluate(PROBE)).thenReturn(V1.unchanged());

        poller(V1).poll();

        verify(listener).pollSucceeded(List.of());
    }

    @Test
    void newVersionReportsChangedAddedAndRemovedKeys() {
        when(client.bulkEvaluate(PROBE)).thenReturn(
                bulk("v2", flag("banner", "green", "green"), flag("max", 42, "forty-two"), flag("fresh", true, null)));
        FlagChangePoller poller = poller(bulk("v1", flag("banner", "blue", "blue"), flag("max", 42, "forty-two"),
                flag("gone", true, null)));

        poller.poll();

        verify(listener).pollSucceeded(List.of("banner", "fresh", "gone"));
    }

    @Test
    void newVersionWithoutVisibleDifferenceReportsEveryKey() {
        when(client.bulkEvaluate(PROBE)).thenReturn(
                bulk("v2", flag("banner", "blue", "blue"), flag("max", 42, "forty-two")));

        poller(V1).poll();

        verify(listener).pollSucceeded(List.of("banner", "max"));
    }

    @Test
    void comparesAgainstTheLastSuccessfulPoll() {
        BulkEvaluation v2 = bulk("v2", flag("banner", "green", "green"), flag("max", 42, "forty-two"));
        when(client.bulkEvaluate(PROBE))
                .thenReturn(v2)
                .thenReturn(BulkEvaluation.error(ErrorCode.GENERAL, "boom"))
                .thenReturn(v2.unchanged());
        FlagChangePoller poller = poller(V1);

        poller.poll();
        poller.poll();
        poller.poll();

        verify(listener).pollSucceeded(List.of("banner"));
        verify(listener).pollFailed(ErrorCode.GENERAL, "boom");
        verify(listener).pollSucceeded(List.of());
    }

    @Test
    void firstSuccessWithoutBaselineReportsNoChanges() {
        when(client.bulkEvaluate(PROBE)).thenReturn(V1);

        poller(null).poll();

        verify(listener).pollSucceeded(List.of());
    }

    @Test
    void failedPollReportsErrorCodeAndMessage() {
        when(client.bulkEvaluate(PROBE)).thenReturn(BulkEvaluation.error(ErrorCode.UNAUTHORIZED, "bad key"));

        poller(V1).poll();

        verify(listener).pollFailed(ErrorCode.UNAUTHORIZED, "bad key");
        verify(listener, never()).pollSucceeded(any());
    }

    @Test
    void startSchedulesWithFixedDelayAndScheduledTaskSurvivesExceptions() {
        when(client.bulkEvaluate(PROBE)).thenThrow(new IllegalStateException("unexpected")).thenReturn(V1.unchanged());
        FlagChangePoller poller = poller(V1);

        poller.start(Duration.ofSeconds(30));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleWithFixedDelay(task.capture(), eq(30_000L), eq(30_000L), eq(TimeUnit.MILLISECONDS));
        task.getValue().run();
        task.getValue().run();
        verify(client, times(2)).bulkEvaluate(PROBE);
        verify(listener).pollSucceeded(List.of());
    }

    @Test
    void stopShutsTheSchedulerDown() {
        poller(V1).stop();

        verify(scheduler).shutdownNow();
        verifyNoInteractions(client, listener);
    }

    @Test
    void defaultSchedulerUsesADaemonThread() throws Exception {
        ScheduledExecutorService real = FlagChangePoller.newScheduler();
        try {
            Thread thread = real.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
            assertThat(thread.isDaemon()).isTrue();
            assertThat(thread.getName()).isEqualTo("fluxgate-flag-poller");
        } finally {
            real.shutdownNow();
        }
    }
}
