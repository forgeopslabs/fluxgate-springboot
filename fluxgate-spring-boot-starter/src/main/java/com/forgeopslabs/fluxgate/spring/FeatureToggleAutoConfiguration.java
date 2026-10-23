package com.forgeopslabs.fluxgate.spring;

import com.forgeopslabs.fluxgate.DefaultFluxGateClient;
import com.forgeopslabs.fluxgate.FluxGateAsyncExecutor;
import com.forgeopslabs.fluxgate.FluxGateClient;
import com.forgeopslabs.fluxgate.FluxGateClientListener;
import com.forgeopslabs.fluxgate.ofrep.OfrepTransport;
import com.forgeopslabs.fluxgate.openfeature.FluxGateProvider;
import com.forgeopslabs.fluxgate.spring.openfeature.OpenFeatureRegistrar;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Auto-configuration for the FluxGate client and OpenFeature provider. Disabled
 * with {@code fluxgate.enabled=false}. Metrics, health and the
 * {@code @FeatureToggle} aspect have their own auto-configurations.
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
        return OfrepTransport.create(properties.toOptions());
    }

    @Bean
    @ConditionalOnMissingBean
    public FluxGateClient fluxGateClient(OfrepTransport fluxGateOfrepTransport, FeatureToggleProperties properties,
            ObjectProvider<FluxGateClientListener> listeners,
            ObjectProvider<FluxGateAsyncExecutor> asyncExecutor) {
        logger.info("Creating FluxGateClient with base URL: {}", properties.getBaseUrl());
        return new DefaultFluxGateClient(fluxGateOfrepTransport, properties.toOptions(), listeners.orderedStream().toList(),
                asyncExecutor(asyncExecutor));
    }

    /**
     * @return the {@link FluxGateAsyncExecutor} bean as an {@link Executor}, or
     *         {@link ForkJoinPool#commonPool()} when there is none
     */
    public static Executor asyncExecutor(ObjectProvider<FluxGateAsyncExecutor> asyncExecutor) {
        FluxGateAsyncExecutor executor = asyncExecutor.getIfAvailable();
        return executor == null ? ForkJoinPool.commonPool() : executor.asExecutor();
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
        @ConditionalOnProperty(prefix = "fluxgate", name = "openfeature.auto-register", havingValue = "true")
        public OpenFeatureRegistrar fluxGateOpenFeatureRegistrar(FluxGateProvider fluxGateProvider,
                FeatureToggleProperties properties) {
            return new OpenFeatureRegistrar(OpenFeatureAPI.getInstance(), fluxGateProvider,
                    properties.getOpenfeature().getProviderName());
        }
    }
}
