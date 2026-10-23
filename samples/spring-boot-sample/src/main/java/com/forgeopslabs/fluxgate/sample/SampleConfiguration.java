package com.forgeopslabs.fluxgate.sample;

import com.forgeopslabs.fluxgate.EvaluationContext;
import com.forgeopslabs.fluxgate.spring.annotation.EvaluationContextResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SampleConfiguration {

    /**
     * Tells {@code @FeatureToggle} who the caller is: the sample's guarded
     * methods take the user ID and the country as their first two arguments.
     */
    @Bean
    EvaluationContextResolver evaluationContextResolver() {
        return (flagKey, method, arguments) -> {
            EvaluationContext.Builder context = EvaluationContext.builder();
            if (arguments.length > 0 && arguments[0] instanceof String userId) {
                context.targetingKey(userId);
            }
            if (arguments.length > 1 && arguments[1] instanceof String country) {
                context.attribute("country", country);
            }
            return context.build();
        };
    }
}
