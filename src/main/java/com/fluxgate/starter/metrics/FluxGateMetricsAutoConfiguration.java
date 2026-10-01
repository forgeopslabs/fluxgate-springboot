package com.fluxgate.starter.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link FluxGateMetrics} with the auto-configured client when
 * Micrometer and a {@link MeterRegistry} bean are present. Disabled with
 * {@code fluxgate.metrics.enabled=false}.
 *
 * <p>
 * Ordered after Spring Boot's composite registry auto-configuration (Boot 3 and
 * Boot 4 class names) so that {@code @ConditionalOnBean(MeterRegistry.class)}
 * sees the registry.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration" })
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "fluxgate", name = { "enabled", "metrics.enabled" }, havingValue = "true",
        matchIfMissing = true)
public class FluxGateMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FluxGateMetrics fluxGateMetrics(MeterRegistry meterRegistry) {
        return new FluxGateMetrics(meterRegistry);
    }
}
