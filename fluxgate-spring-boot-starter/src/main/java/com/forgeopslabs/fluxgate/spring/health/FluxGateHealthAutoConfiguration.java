package com.forgeopslabs.fluxgate.spring.health;

import com.forgeopslabs.fluxgate.spring.FeatureToggleAutoConfiguration;
import com.forgeopslabs.fluxgate.spring.FeatureToggleProperties;
import com.forgeopslabs.fluxgate.FluxGateAsyncExecutor;
import com.forgeopslabs.fluxgate.FluxGateClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Duration;

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
            ObjectProvider<FluxGateAsyncExecutor> executor) {
        Duration polling = properties.getPolling().getInterval();
        Duration probeInterval = polling.isZero() ? DEFAULT_PROBE_INTERVAL : polling;
        return new FluxGateHealthIndicator(fluxGateClient, tracker, FeatureToggleAutoConfiguration.asyncExecutor(executor),
                probeInterval, Clock.systemUTC());
    }
}
