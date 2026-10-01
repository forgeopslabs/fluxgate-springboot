package com.fluxgate.starter.aop;

import com.fluxgate.starter.EvaluationContext;
import com.fluxgate.starter.FluxGateClient;
import com.fluxgate.starter.annotation.EvaluationContextResolver;
import com.fluxgate.starter.annotation.FeatureToggle;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Implements {@link FeatureToggle}: proceeds when the flag is on, otherwise
 * calls the fallback method on the target object or returns the return type's
 * default. Exceptions from the method or the fallback propagate unchanged.
 */
@Aspect
public class FeatureToggleAspect {

    private final FluxGateClient client;
    private final EvaluationContextResolver contextResolver;
    private final FallbackMethodResolver fallbackResolver;

    /**
     * @param client           evaluates the flags
     * @param contextResolver  supplies the evaluation context
     * @param fallbackResolver finds fallback methods
     */
    public FeatureToggleAspect(FluxGateClient client, EvaluationContextResolver contextResolver,
            FallbackMethodResolver fallbackResolver) {
        this.client = client;
        this.contextResolver = contextResolver;
        this.fallbackResolver = fallbackResolver;
    }

    @Around("@annotation(featureToggle)")
    public Object toggle(ProceedingJoinPoint joinPoint, FeatureToggle featureToggle) throws Throwable {
        Object target = joinPoint.getTarget();
        Class<?> targetClass = ClassUtils.getUserClass(AopUtils.getTargetClass(target));
        Method method = AopUtils.getMostSpecificMethod(((MethodSignature) joinPoint.getSignature()).getMethod(),
                targetClass);
        Object[] arguments = joinPoint.getArgs();

        EvaluationContext context = contextResolver.resolve(featureToggle.value(), method, arguments);
        if (client.isEnabled(featureToggle.value(), context == null ? EvaluationContext.empty() : context)) {
            return joinPoint.proceed();
        }
        if (featureToggle.fallbackMethod().isEmpty()) {
            return ReturnTypeDefaults.defaultFor(method.getReturnType());
        }
        Method fallback = fallbackResolver.resolve(targetClass, method, featureToggle.fallbackMethod());
        try {
            return fallback.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
