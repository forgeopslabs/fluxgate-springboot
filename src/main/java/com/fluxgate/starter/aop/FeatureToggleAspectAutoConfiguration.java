package com.fluxgate.starter.aop;

import com.fluxgate.starter.DisabledFluxGateAutoConfiguration;
import com.fluxgate.starter.EvaluationContext;
import com.fluxgate.starter.FeatureToggleAutoConfiguration;
import com.fluxgate.starter.FluxGateClient;
import com.fluxgate.starter.annotation.EvaluationContextResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition;

/**
 * Enables {@link com.fluxgate.starter.annotation.FeatureToggle}. Active whenever
 * a {@link FluxGateClient} exists, including the no-op client of
 * {@code fluxgate.enabled=false} (every flag off).
 *
 * <ul>
 * <li>With AspectJ ({@code spring-boot-starter-aop}) and
 * {@code spring.aop.auto=true} (the default), registers the aspect and the
 * startup fallback check; Spring Boot's AOP auto-configuration creates the
 * proxies.</li>
 * <li>Otherwise registers {@link InactiveFeatureToggleWarner}, which logs one
 * warning when annotated beans exist.</li>
 * </ul>
 */
@AutoConfiguration(after = { FeatureToggleAutoConfiguration.class, DisabledFluxGateAutoConfiguration.class })
@ConditionalOnBean(FluxGateClient.class)
public class FeatureToggleAspectAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.aspectj.weaver.Advice")
    @ConditionalOnProperty(prefix = "spring.aop", name = "auto", havingValue = "true", matchIfMissing = true)
    static class AspectConfiguration {

        /**
         * Static and dependency-free, as a {@code BeanPostProcessor} must be; it
         * uses its own resolver so that no other bean is created early.
         */
        @Bean
        static FeatureToggleMethodValidator fluxGateFeatureToggleMethodValidator() {
            return new FeatureToggleMethodValidator(new FallbackMethodResolver());
        }

        @Bean
        @ConditionalOnMissingBean
        FallbackMethodResolver fluxGateFallbackMethodResolver() {
            return new FallbackMethodResolver();
        }

        @Bean
        @ConditionalOnMissingBean
        FeatureToggleAspect fluxGateFeatureToggleAspect(FluxGateClient fluxGateClient,
                ObjectProvider<EvaluationContextResolver> contextResolver, FallbackMethodResolver fallbackResolver) {
            return new FeatureToggleAspect(fluxGateClient,
                    contextResolver.getIfAvailable(() -> (flagKey, method, arguments) -> EvaluationContext.empty()),
                    fallbackResolver);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(AspectInactiveCondition.class)
    static class InactiveConfiguration {

        @Bean
        static InactiveFeatureToggleWarner fluxGateInactiveFeatureToggleWarner() {
            return new InactiveFeatureToggleWarner();
        }
    }

    /**
     * AspectJ is missing or {@code spring.aop.auto=false}.
     */
    static class AspectInactiveCondition extends AnyNestedCondition {

        AspectInactiveCondition() {
            super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnMissingClass("org.aspectj.weaver.Advice")
        static class NoAspectJ {
        }

        @ConditionalOnProperty(prefix = "spring.aop", name = "auto", havingValue = "false")
        static class AutoProxyOff {
        }
    }
}
