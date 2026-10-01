package com.fluxgate.starter.aop;

import java.util.Map;
import java.util.Optional;

/**
 * The value a {@code @FeatureToggle} method returns when its flag is off and it
 * has no fallback.
 */
final class ReturnTypeDefaults {

    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = Map.of(
            boolean.class, false,
            char.class, '\0',
            byte.class, (byte) 0,
            short.class, (short) 0,
            int.class, 0,
            long.class, 0L,
            float.class, 0f,
            double.class, 0d);

    private ReturnTypeDefaults() {
    }

    /**
     * @return {@code false} or zero for primitives, {@code Optional.empty()} for
     *         {@link Optional}, otherwise {@code null} (also for {@code void})
     */
    static Object defaultFor(Class<?> returnType) {
        if (returnType == Optional.class) {
            return Optional.empty();
        }
        return PRIMITIVE_DEFAULTS.get(returnType);
    }
}
