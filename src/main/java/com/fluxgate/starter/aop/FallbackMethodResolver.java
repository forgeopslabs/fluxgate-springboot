package com.fluxgate.starter.aop;

import com.fluxgate.starter.annotation.FeatureToggle;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Finds the {@link FeatureToggle#fallbackMethod()} of an annotated method: a
 * method of the target class (or a superclass) with the given name, exactly the
 * same parameter types, and a return type assignable to the annotated method's
 * (any return type when the annotated method is {@code void}). Results are
 * cached per target class and method.
 */
public class FallbackMethodResolver {

    private record Key(Class<?> targetClass, Method method, String fallbackName) {
    }

    private final Map<Key, Method> cache = new ConcurrentHashMap<>();

    /**
     * @param targetClass  the user class of the bean
     * @param method       the annotated method
     * @param fallbackName the fallback method name
     * @return the accessible fallback method
     * @throws IllegalStateException when no compatible fallback exists
     */
    public Method resolve(Class<?> targetClass, Method method, String fallbackName) {
        return cache.computeIfAbsent(new Key(targetClass, method, fallbackName), FallbackMethodResolver::find);
    }

    private static Method find(Key key) {
        Method method = key.method();
        Method fallback = ReflectionUtils.findMethod(key.targetClass(), key.fallbackName(),
                method.getParameterTypes());
        if (fallback == null) {
            throw new IllegalStateException(String.format(
                    "@FeatureToggle fallback method %s(%s) not found on %s; it must have the same parameter types as %s",
                    key.fallbackName(), parameterList(method), key.targetClass().getName(), method.getName()));
        }
        if (method.getReturnType() != void.class
                && !ClassUtils.isAssignable(method.getReturnType(), fallback.getReturnType())) {
            throw new IllegalStateException(String.format(
                    "@FeatureToggle fallback method %s(%s) on %s returns %s, which is not assignable to %s returned by %s",
                    key.fallbackName(), parameterList(method), key.targetClass().getName(),
                    fallback.getReturnType().getName(), method.getReturnType().getName(), method.getName()));
        }
        ReflectionUtils.makeAccessible(fallback);
        return fallback;
    }

    private static String parameterList(Method method) {
        return Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", "));
    }
}
