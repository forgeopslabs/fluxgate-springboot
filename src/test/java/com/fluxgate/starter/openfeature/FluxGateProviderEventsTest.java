package com.fluxgate.starter.openfeature;

import com.fluxgate.starter.BulkEvaluation;
import com.fluxgate.starter.ErrorCode;
import com.fluxgate.starter.FlagEvaluation;
import com.fluxgate.starter.FluxGateClient;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EventDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Provider lifecycle and events through the real OpenFeature SDK. Polls are
 * triggered by running the task captured from a mock scheduler, so nothing
 * depends on timing; only the SDK's asynchronous event handlers are awaited.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FluxGateProviderEventsTest {

    private static final String DOMAIN = "fluxgate-events-test";

    private static final BulkEvaluation V1 = new BulkEvaluation(Map.of(
            "banner", new FlagEvaluation<>("banner", "blue", "blue", "SPLIT", null, null),
            "max", new FlagEvaluation<>("max", 42, "forty-two", "STATIC", null, null)), "v1", true, null, null);
    private static final BulkEvaluation V2 = new BulkEvaluation(Map.of(
            "banner", new FlagEvaluation<>("banner", "green", "green", "SPLIT", null, null),
            "max", new FlagEvaluation<>("max", 42, "forty-two", "STATIC", null, null)), "v2", true, null, null);

    @Mock
    private FluxGateClient fluxGate;
    @Mock
    private ScheduledExecutorService scheduler;

    private final OpenFeatureAPI api = OpenFeatureAPI.getInstance();
    private final List<EventDetails> ready = new CopyOnWriteArrayList<>();
    private final List<EventDetails> errors = new CopyOnWriteArrayList<>();
    private final List<EventDetails> changes = new CopyOnWriteArrayList<>();
    private FluxGateProvider provider;
    private Client client;

    @BeforeEach
    void setUp() {
        provider = new FluxGateProvider(fluxGate, Duration.ofSeconds(30), () -> scheduler);
        client = api.getClient(DOMAIN);
        client.on(ProviderEvent.PROVIDER_READY, ready::add);
        client.on(ProviderEvent.PROVIDER_ERROR, errors::add);
        client.on(ProviderEvent.PROVIDER_CONFIGURATION_CHANGED, changes::add);
    }

    @AfterEach
    void tearDown() {
        api.shutdown();
    }

    private void edgeUp() {
        when(fluxGate.isHealthy()).thenReturn(true);
        when(fluxGate.bulkEvaluate(any())).thenReturn(V1);
    }

    private Runnable pollTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleWithFixedDelay(task.capture(), anyLong(), anyLong(), any());
        return task.getValue();
    }

    @Test
    void successfulInitializationMakesTheProviderReady() {
        edgeUp();

        api.setProviderAndWait(DOMAIN, provider);

        assertThat(client.getProviderState()).isEqualTo(ProviderState.READY);
        await().until(() -> ready.size() == 1);
        assertThat(errors).isEmpty();
    }

    @Test
    void failedInitializationEmitsErrorThenRecoversThroughThePoller() {
        when(fluxGate.isHealthy()).thenReturn(true);
        when(fluxGate.bulkEvaluate(any()))
                .thenReturn(BulkEvaluation.error(ErrorCode.UNAUTHORIZED, "Client credentials are missing or invalid"))
                .thenReturn(V1);

        assertThatThrownBy(() -> api.setProviderAndWait(DOMAIN, provider));

        assertThat(client.getProviderState()).isEqualTo(ProviderState.ERROR);
        await().until(() -> errors.size() == 1);
        assertThat(errors.get(0).getMessage()).contains("Client credentials are missing or invalid");
        assertThat(errors.get(0).getErrorCode()).isEqualTo(dev.openfeature.sdk.ErrorCode.GENERAL);
        assertThat(ready).isEmpty();

        pollTask().run();

        // SDK 1.23 emits events, and applies their state change, asynchronously.
        await().until(() -> client.getProviderState() == ProviderState.READY);
        await().until(() -> ready.size() == 1);
        assertThat(changes).isEmpty();
    }

    @Test
    void newFlagSetVersionEmitsConfigurationChangedAndClearsTheCache() {
        edgeUp();
        api.setProviderAndWait(DOMAIN, provider);
        Runnable poll = pollTask();

        when(fluxGate.bulkEvaluate(any())).thenReturn(V1.unchanged());
        poll.run();
        verify(fluxGate, never()).invalidateCache();

        when(fluxGate.bulkEvaluate(any())).thenReturn(V2);
        poll.run();

        await().until(() -> changes.size() == 1);
        assertThat(changes.get(0).getFlagsChanged()).containsExactly("banner");
        verify(fluxGate).invalidateCache();
    }

    @Test
    void changedFlagsClearTheCacheEvenWhenTheProviderLeavesReadyMeanwhile() {
        edgeUp();
        api.setProviderAndWait(DOMAIN, provider);
        await().until(() -> ready.size() == 1);
        Runnable poll = pollTask();
        when(fluxGate.evaluate(eq("f"), eq(false), eq(Boolean.class), any()))
                .thenReturn(FlagEvaluation.error("f", false, ErrorCode.UNAUTHORIZED, "Client is not permitted"));
        // An evaluation rejected with 401 lands between the poll's status checks.
        doAnswer(invocation -> provider.getBooleanEvaluation("f", false, new ImmutableContext("user-1")))
                .when(fluxGate).invalidateCache();

        when(fluxGate.bulkEvaluate(any())).thenReturn(V2);
        poll.run();

        verify(fluxGate).invalidateCache();
        await().until(() -> errors.size() == 1);
        await().during(Duration.ofMillis(200)).until(changes::isEmpty);
    }

    @Test
    void failedPollEmitsErrorOnceAndRecoveryEmitsReadyAndChanges() {
        edgeUp();
        api.setProviderAndWait(DOMAIN, provider);
        await().until(() -> ready.size() == 1);
        Runnable poll = pollTask();

        when(fluxGate.bulkEvaluate(any())).thenReturn(BulkEvaluation.error(ErrorCode.PROVIDER_NOT_READY, "503"));
        poll.run();
        poll.run();

        // SDK 1.23 emits events, and applies their state change, asynchronously.
        await().until(() -> client.getProviderState() == ProviderState.ERROR);
        await().until(() -> errors.size() == 1);
        assertThat(errors.get(0).getMessage()).isEqualTo("503");
        assertThat(errors.get(0).getErrorCode()).isEqualTo(dev.openfeature.sdk.ErrorCode.PROVIDER_NOT_READY);

        when(fluxGate.bulkEvaluate(any())).thenReturn(V2);
        poll.run();

        // SDK 1.23 emits events, and applies their state change, asynchronously.
        await().until(() -> client.getProviderState() == ProviderState.READY);
        await().until(() -> ready.size() == 2 && changes.size() == 1);
        assertThat(errors).hasSize(1);
    }

    @Test
    void rejectedSdkKeyDuringEvaluationMovesProviderToError() {
        edgeUp();
        api.setProviderAndWait(DOMAIN, provider);
        when(fluxGate.evaluate(eq("f"), eq(false), eq(Boolean.class), any()))
                .thenReturn(FlagEvaluation.error("f", false, ErrorCode.UNAUTHORIZED, "Client is not permitted"));

        boolean value = client.getBooleanValue("f", false, new ImmutableContext("user-1"));

        assertThat(value).isFalse();
        // SDK 1.23 emits events, and applies their state change, asynchronously.
        await().until(() -> client.getProviderState() == ProviderState.ERROR);
        await().until(() -> errors.size() == 1);
        assertThat(errors.get(0).getMessage()).isEqualTo("Client is not permitted");
        assertThat(errors.get(0).getErrorCode()).isEqualTo(dev.openfeature.sdk.ErrorCode.GENERAL);
    }

    @Test
    void shutdownStopsPollingAndSilencesLatePolls() {
        edgeUp();
        api.setProviderAndWait(DOMAIN, provider);
        Runnable poll = pollTask();

        api.shutdown();

        await().untilAsserted(() -> verify(scheduler).shutdownNow());
        when(fluxGate.bulkEvaluate(any())).thenReturn(V2);
        poll.run();
        verify(fluxGate, never()).invalidateCache();
    }
}
