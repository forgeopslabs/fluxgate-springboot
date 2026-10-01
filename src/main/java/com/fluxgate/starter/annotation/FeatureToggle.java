package com.fluxgate.starter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs the annotated method only while a boolean flag is on.
 *
 * <pre>{@code
 * @FeatureToggle(value = "new-checkout", fallbackMethod = "legacyCheckout")
 * public Receipt checkout(Cart cart) { ... }
 *
 * Receipt legacyCheckout(Cart cart) { ... }
 * }</pre>
 *
 * <p>
 * When the flag evaluates to {@code true} the method runs. Otherwise, including
 * when the evaluation fails, the fallback method runs if one is named, else the
 * call returns the default for the return type: {@code null},
 * {@code Optional.empty()}, {@code false} or zero for primitives. The flag is
 * evaluated synchronously on the calling thread; asynchronous or reactive return
 * types ({@code CompletableFuture}, {@code Mono}, ...) get {@code null} when the
 * flag is off without a fallback, so give them a fallback.
 *
 * <p>
 * The evaluation context comes from the {@link EvaluationContextResolver} bean;
 * without one the context is empty and, because the edge requires a targeting
 * key, the flag is always treated as off.
 *
 * <p>
 * This is Spring AOP: it needs {@code spring-boot-starter-aop} and applies only
 * to calls made through the Spring proxy of a bean. Self-invocation
 * ({@code this.checkout(...)}) and private, final or static methods are not
 * intercepted. Annotate the method on the implementation class, not on an
 * interface.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeatureToggle {

    /**
     * @return the boolean flag key
     */
    String value();

    /**
     * Name of a method on the same bean, with the same parameter types and a
     * return type assignable to the annotated method's, called when the flag is
     * off. It may be private. Empty (the default) means no fallback. A missing
     * or incompatible fallback fails application startup.
     *
     * @return the fallback method name, or empty
     */
    String fallbackMethod() default "";
}
