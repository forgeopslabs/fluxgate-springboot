package com.forgeopslabs.fluxgate.spring.aop;

import com.forgeopslabs.fluxgate.spring.annotation.FeatureToggle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks every bean's {@link FeatureToggle} methods at startup: a missing or
 * incompatible fallback fails the bean's initialization, and a method Spring AOP
 * cannot intercept (private, final or static) is logged as a warning.
 */
public class FeatureToggleMethodValidator implements BeanPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FeatureToggleMethodValidator.class);

    private final FallbackMethodResolver fallbackResolver;
    private final Set<Class<?>> checked = ConcurrentHashMap.newKeySet();

    /**
     * @param fallbackResolver resolves fallbacks the same way the aspect does
     */
    public FeatureToggleMethodValidator(FallbackMethodResolver fallbackResolver) {
        this.fallbackResolver = fallbackResolver;
    }

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
        Class<?> targetClass = ClassUtils.getUserClass(AopUtils.getTargetClass(bean));
        if (!AnnotationUtils.isCandidateClass(targetClass, FeatureToggle.class) || !checked.add(targetClass)) {
            return bean;
        }
        Map<Method, FeatureToggle> methods = MethodIntrospector.selectMethods(targetClass,
                (MethodIntrospector.MetadataLookup<FeatureToggle>) method -> AnnotatedElementUtils
                        .findMergedAnnotation(method, FeatureToggle.class));
        methods.forEach((method, toggle) -> validate(beanName, targetClass, method, toggle));
        return bean;
    }

    private void validate(String beanName, Class<?> targetClass, Method method, FeatureToggle toggle) {
        int modifiers = method.getModifiers();
        if (Modifier.isPrivate(modifiers) || Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers)) {
            logger.warn("@FeatureToggle(\"{}\") on {}.{} has no effect: Spring AOP cannot intercept private, "
                    + "final or static methods", toggle.value(), targetClass.getName(), method.getName());
        }
        if (toggle.fallbackMethod().isEmpty()) {
            return;
        }
        try {
            fallbackResolver.resolve(targetClass, method, toggle.fallbackMethod());
        } catch (IllegalStateException e) {
            checked.remove(targetClass);
            throw new BeanInitializationException("Invalid @FeatureToggle on bean '" + beanName + "': "
                    + e.getMessage(), e);
        }
    }
}
