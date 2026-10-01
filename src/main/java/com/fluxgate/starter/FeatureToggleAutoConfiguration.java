package com.fluxgate.starter;

import com.fluxgate.starter.ofrep.OfrepTransport;
import com.fluxgate.starter.openfeature.FluxGateProvider;
import com.fluxgate.starter.openfeature.OpenFeatureRegistrar;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
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

    /**
     * Name of an optional {@link Executor} bean that runs
     * {@link FluxGateClient#evaluateAsync} and background {@code /health}
     * probes. Without it, {@link ForkJoinPool#commonPool()} is used.
     */
    public static final String ASYNC_EXECUTOR_BEAN_NAME = "fluxGateAsyncExecutor";

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
    public FluxGateClient fluxGateClient(OfrepTransport fluxGateOfrepTransport, FeatureToggleProperties properties,
            ObjectProvider<FluxGateClientListener> listeners,
            @Qualifier(ASYNC_EXECUTOR_BEAN_NAME) ObjectProvider<Executor> asyncExecutor) {
        logger.info("Creating FluxGateClient with base URL: {}", properties.getBaseUrl());
        return new DefaultFluxGateClient(fluxGateOfrepTransport, properties, listeners.orderedStream().toList(),
                asyncExecutor.getIfAvailable(ForkJoinPool::commonPool));
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
