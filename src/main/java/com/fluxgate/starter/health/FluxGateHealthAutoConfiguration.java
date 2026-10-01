package com.fluxgate.starter.health;

import com.fluxgate.starter.FeatureToggleAutoConfiguration;
import com.fluxgate.starter.FeatureToggleProperties;
import com.fluxgate.starter.FluxGateClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Contributes the {@code fluxgate} Actuator health indicator. Disabled with
 * {@code fluxgate.health.enabled=false}.
 */
@AutoConfiguration(after = FeatureToggleAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(FluxGateClient.class)
@ConditionalOnProperty(prefix = "fluxgate", name = { "enabled", "health.enabled" }, havingValue = "true",
        matchIfMissing = true)
public class FluxGateHealthAutoConfiguration {

    /** How often {@code /health} is re-probed when polling is disabled. */
    static final Duration DEFAULT_PROBE_INTERVAL = Duration.ofSeconds(30);

    @Bean
    @ConditionalOnMissingBean
    public EdgeHealthTracker fluxGateEdgeHealthTracker() {
        return new EdgeHealthTracker();
    }

    /**
     * Bean name {@code fluxgateHealthIndicator} makes the contributor name
     * {@code fluxgate}.
     */
    @Bean
    @ConditionalOnMissingBean(name = "fluxgateHealthIndicator")
    public FluxGateHealthIndicator fluxgateHealthIndicator(FluxGateClient fluxGateClient, EdgeHealthTracker tracker,
            FeatureToggleProperties properties,
            @Qualifier(FeatureToggleAutoConfiguration.ASYNC_EXECUTOR_BEAN_NAME) ObjectProvider<Executor> executor) {
        Duration polling = properties.getPolling().getInterval();
        Duration probeInterval = polling.isZero() ? DEFAULT_PROBE_INTERVAL : polling;
        return new FluxGateHealthIndicator(fluxGateClient, tracker, executor.getIfAvailable(ForkJoinPool::commonPool),
                probeInterval, Clock.systemUTC());
    }
}
