package com.fluxgate.starter;

import com.fluxgate.starter.ofrep.OfrepTransport;
import com.fluxgate.starter.openfeature.FluxGateProvider;
import com.fluxgate.starter.openfeature.OpenFeatureRegistrar;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the FluxGate starter. Disabled with
 * {@code fluxgate.enabled=false}.
 */
@AutoConfiguration
@EnableConfigurationProperties(FeatureToggleProperties.class)
@ConditionalOnProperty(prefix = "fluxgate", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeatureToggleAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(FeatureToggleAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public OfrepTransport fluxGateOfrepTransport(FeatureToggleProperties properties) {
        logger.debug("Configuring FluxGate OFREP transport: baseUrl={}, connectTimeout={}, readTimeout={}",
                properties.getBaseUrl(), properties.getConnectTimeout(), properties.getReadTimeout());
        return OfrepTransport.create(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public FluxGateClient fluxGateClient(OfrepTransport fluxGateOfrepTransport, FeatureToggleProperties properties) {
        logger.info("Creating FluxGateClient with base URL: {}", properties.getBaseUrl());
        return new DefaultFluxGateClient(fluxGateOfrepTransport, properties);
    }

    /**
     * Spring Boot Actuator health indicator, when Actuator is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnProperty(prefix = "fluxgate", name = "health-check-enabled", havingValue = "true", matchIfMissing = true)
    public static class FeatureToggleHealthConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public FeatureToggleHealthIndicator featureToggleHealthIndicator(FluxGateClient fluxGateClient) {
            logger.info("Enabling FeatureToggle health indicator");
            return new FeatureToggleHealthIndicator(fluxGateClient);
        }
    }

    /**
     * OpenFeature provider, when the OpenFeature SDK is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "dev.openfeature.sdk.FeatureProvider")
    public static class OpenFeatureConfiguration {

        @Bean(destroyMethod = "shutdown")
        @ConditionalOnMissingBean(name = "fluxGateProvider")
        public FluxGateProvider fluxGateProvider(FluxGateClient fluxGateClient, FeatureToggleProperties properties) {
            logger.info("Creating FluxGateProvider for OpenFeature integration");
            return new FluxGateProvider(fluxGateClient, properties.getPolling().getInterval());
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "fluxgate.openfeature", name = "auto-register", havingValue = "true")
        public OpenFeatureRegistrar fluxGateOpenFeatureRegistrar(FluxGateProvider fluxGateProvider,
                FeatureToggleProperties properties) {
            return new OpenFeatureRegistrar(OpenFeatureAPI.getInstance(), fluxGateProvider,
                    properties.getOpenfeature().getProviderName());
        }
    }
}
