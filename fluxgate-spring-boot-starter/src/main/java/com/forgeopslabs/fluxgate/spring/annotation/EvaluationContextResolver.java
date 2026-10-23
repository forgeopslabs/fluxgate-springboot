package com.forgeopslabs.fluxgate.spring.annotation;

import com.forgeopslabs.fluxgate.EvaluationContext;

import java.lang.reflect.Method;

/**
 * Supplies the evaluation context for {@link FeatureToggle} methods. Declare one
 * as a bean; without it the context is empty.
 *
 * <p>
 * Example resolving the targeting key from the Spring Security principal:
 *
 * <pre>{@code
 * @Bean
 * EvaluationContextResolver securityContextResolver() {
 *     return (flagKey, method, arguments) -> {
 *         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *         return auth == null || !auth.isAuthenticated()
 *                 ? EvaluationContext.empty()
 *                 : EvaluationContext.of(auth.getName());
 *     };
 * }
 * }</pre>
 */
@FunctionalInterface
public interface EvaluationContextResolver {

    /**
     * @param flagKey   the flag being evaluated
     * @param method    the annotated method
     * @param arguments the call's arguments
     * @return the context to evaluate with; {@code null} means empty
     */
    EvaluationContext resolve(String flagKey, Method method, Object[] arguments);
}
