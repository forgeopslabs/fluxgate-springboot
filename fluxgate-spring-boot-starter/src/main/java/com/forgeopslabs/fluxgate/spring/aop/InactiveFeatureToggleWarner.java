package com.forgeopslabs.fluxgate.spring.aop;

import com.forgeopslabs.fluxgate.spring.annotation.FeatureToggle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Registered when the {@link FeatureToggle} aspect cannot run (AspectJ is not on
 * the classpath, or {@code spring.aop.auto=false}). Collects the beans that use
 * the annotation and logs one warning after startup, since their methods would
 * otherwise run unconditionally without notice. Uses no AspectJ types.
 */
public class InactiveFeatureToggleWarner implements BeanPostProcessor, SmartInitializingSingleton {

    private static final Logger logger = LoggerFactory.getLogger(InactiveFeatureToggleWarner.class);

    private final Consumer<String> warning;
    private final Set<Class<?>> checked = ConcurrentHashMap.newKeySet();
    private final List<String> annotatedBeans = new CopyOnWriteArrayList<>();

    public InactiveFeatureToggleWarner() {
        this(logger::warn);
    }

    InactiveFeatureToggleWarner(Consumer<String> warning) {
        this.warning = warning;
    }

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
        Class<?> targetClass = ClassUtils.getUserClass(AopUtils.getTargetClass(bean));
        if (AnnotationUtils.isCandidateClass(targetClass, FeatureToggle.class) && checked.add(targetClass)
                && !MethodIntrospector.selectMethods(targetClass,
                        (MethodIntrospector.MetadataLookup<FeatureToggle>) method -> AnnotatedElementUtils
                                .findMergedAnnotation(method, FeatureToggle.class))
                        .isEmpty()) {
            annotatedBeans.add(beanName);
        }
        return bean;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!annotatedBeans.isEmpty()) {
            warning.accept("@FeatureToggle is used by " + annotatedBeans + " but the FluxGate aspect is inactive "
                    + "(add spring-boot-starter-aop and keep spring.aop.auto=true): those methods run regardless "
                    + "of their flags");
        }
    }
}
