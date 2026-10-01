package com.fluxgate.starter.aop;

import com.fluxgate.starter.EvaluationContext;
import com.fluxgate.starter.FeatureToggleAutoConfiguration;
import com.fluxgate.starter.FluxGateClient;
import com.fluxgate.starter.annotation.EvaluationContextResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Enables {@link com.fluxgate.starter.annotation.FeatureToggle} when AspectJ
 * ({@code spring-boot-starter-aop}) is on the classpath. Spring Boot's AOP
 * auto-configuration ({@code spring.aop.auto=true}, the default) creates the
 * proxies.
 */
@AutoConfiguration(after = FeatureToggleAutoConfiguration.class)
@ConditionalOnClass(name = "org.aspectj.weaver.Advice")
@ConditionalOnBean(FluxGateClient.class)
@ConditionalOnProperty(prefix = "fluxgate", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeatureToggleAspectAutoConfiguration {

    /**
     * Static and dependency-free, as a {@code BeanPostProcessor} must be; it
     * uses its own resolver so that no other bean is created early.
     */
    @Bean
    public static FeatureToggleMethodValidator fluxGateFeatureToggleMethodValidator() {
        return new FeatureToggleMethodValidator(new FallbackMethodResolver());
    }

    @Bean
    @ConditionalOnMissingBean
    public FallbackMethodResolver fluxGateFallbackMethodResolver() {
        return new FallbackMethodResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public FeatureToggleAspect fluxGateFeatureToggleAspect(FluxGateClient fluxGateClient,
            ObjectProvider<EvaluationContextResolver> contextResolver, FallbackMethodResolver fallbackResolver) {
        return new FeatureToggleAspect(fluxGateClient,
                contextResolver.getIfAvailable(() -> (flagKey, method, arguments) -> EvaluationContext.empty()),
                fallbackResolver);
    }
}
