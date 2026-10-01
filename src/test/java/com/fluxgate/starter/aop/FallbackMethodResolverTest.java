package com.fluxgate.starter.aop;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackMethodResolverTest {

    static class Base {

        protected Integer inherited(String a, int b) {
            return b;
        }
    }

    static class Service extends Base {

        public Number annotated(String a, int b) {
            return 1;
        }

        public int primitive(String a, int b) {
            return 1;
        }

        public void action(String a, int b) {
        }

        String wrongReturn(String a, int b) {
            return a;
        }

        Integer wrongParameters(int b, String a) {
            return b;
        }

        Object anything(String a, int b) {
            return a;
        }

        static Integer boxed(String a, int b) {
            return b;
        }

        public List<String> list(String a, int b) {
            return List.of();
        }
    }

    private final FallbackMethodResolver resolver = new FallbackMethodResolver();

    private static Method method(String name) throws NoSuchMethodException {
        return Service.class.getMethod(name, String.class, int.class);
    }

    @Test
    void findsInheritedFallbackWithAssignableReturnType() throws Exception {
        Method fallback = resolver.resolve(Service.class, method("annotated"), "inherited");

        assertThat(fallback.getDeclaringClass()).isEqualTo(Base.class);
        assertThat(fallback.invoke(new Service(), "a", 5)).isEqualTo(5);
    }

    @Test
    void cachesResolutions() throws Exception {
        assertThat(resolver.resolve(Service.class, method("annotated"), "inherited"))
                .isSameAs(resolver.resolve(Service.class, method("annotated"), "inherited"));
    }

    @Test
    void boxedFallbackSatisfiesPrimitiveReturnType() throws Exception {
        assertThat(resolver.resolve(Service.class, method("primitive"), "boxed").getName()).isEqualTo("boxed");
    }

    @Test
    void voidMethodAcceptsAnyFallbackReturnType() throws Exception {
        assertThat(resolver.resolve(Service.class, method("action"), "anything").getName()).isEqualTo("anything");
    }

    @Test
    void rejectsIncompatibleReturnType() {
        assertThatThrownBy(() -> resolver.resolve(Service.class, method("annotated"), "wrongReturn"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wrongReturn(String, int)")
                .hasMessageContaining("returns java.lang.String, which is not assignable to java.lang.Number");
    }

    @Test
    void rejectsDifferentParameterTypes() {
        assertThatThrownBy(() -> resolver.resolve(Service.class, method("annotated"), "wrongParameters"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wrongParameters(String, int) not found")
                .hasMessageContaining("same parameter types as annotated");
    }

    @Test
    void rejectsObjectFallbackForSpecificReturnType() {
        assertThatThrownBy(() -> resolver.resolve(Service.class, method("list"), "anything"))
                .isInstanceOf(IllegalStateException.class);
    }
}
