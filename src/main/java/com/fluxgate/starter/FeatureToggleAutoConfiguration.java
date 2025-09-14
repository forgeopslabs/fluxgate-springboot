package com.fluxgate.starter;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

/**
 * Auto-configuration for FluxGate Feature Toggle Spring Boot starter.
 * 
 * This configuration is automatically loaded when the starter is on the classpath
 * and provides all necessary beans for feature toggle functionality.
 */
@AutoConfiguration
@EnableConfigurationProperties(FeatureToggleProperties.class)
@ConditionalOnProperty(prefix = "feature.toggle", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableRetry
public class FeatureToggleAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(FeatureToggleAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate featureToggleRestTemplate(FeatureToggleProperties properties, RestTemplateBuilder builder) {
        logger.debug("Configuring RestTemplate for FeatureToggle with timeouts: connect={}ms, read={}ms",
                    properties.getConnectionTimeout().toMillis(), properties.getReadTimeout().toMillis());

        return builder
                .setConnectTimeout(properties.getConnectionTimeout())
                .setReadTimeout(properties.getReadTimeout())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public Executor featureToggleAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("feature-toggle-async-");
        executor.initialize();
        
        logger.debug("Configured async executor for FeatureToggle with 2-10 threads");
        return executor;
    }



    @Bean
    @ConditionalOnMissingBean
    public FluxGateClient featureToggleClient(RestTemplate featureToggleRestTemplate,
                                                  FeatureToggleProperties properties,
                                                  Executor featureToggleAsyncExecutor) {
        logger.info("Creating FeatureToggleClient with base URL: {}", properties.getBaseUrl());
        
        return new DefaultFeatureToggleClient(
                featureToggleRestTemplate,
                properties,
                featureToggleAsyncExecutor
        );
    }


    /**
     * Configuration for Spring Boot Actuator health indicator.
     * Only activated when Actuator is on the classpath.
     */
    @Configuration
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnProperty(prefix = "feature.toggle", name = "health-check-enabled", havingValue = "true", matchIfMissing = true)
    public static class FeatureToggleHealthConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public FeatureToggleHealthIndicator featureToggleHealthIndicator(DefaultFeatureToggleClient featureToggleClient) {
            logger.info("Enabling FeatureToggle health indicator");
            return new FeatureToggleHealthIndicator(featureToggleClient);
        }
    }
}