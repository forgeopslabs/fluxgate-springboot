package com.fluxgate.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * With {@code fluxgate.enabled=false}, registers a {@link DisabledFluxGateClient}
 * so that code injecting {@link FluxGateClient}, and {@code @FeatureToggle}
 * methods, keep working with every flag off. No properties are required.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "fluxgate", name = "enabled", havingValue = "false")
public class DisabledFluxGateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FluxGateClient fluxGateClient() {
        return new DisabledFluxGateClient();
    }
}
