package com.forgeopslabs.fluxgate.spring.aop;

import com.forgeopslabs.fluxgate.EvaluationContext;
import com.forgeopslabs.fluxgate.FluxGateClient;
import com.forgeopslabs.fluxgate.spring.annotation.EvaluationContextResolver;
import com.forgeopslabs.fluxgate.spring.annotation.FeatureToggle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureToggleAspectTest {

    static class Checkout {

        final List<String> calls = new ArrayList<>();

        @FeatureToggle("new-checkout")
        public String plain(String cart) {
            calls.add("plain");
            return "new:" + cart;
        }

        @FeatureToggle(value = "new-checkout", fallbackMethod = "legacy")
        public CharSequence withFallback(String cart) {
            calls.add("withFallback");
            return "new:" + cart;
        }

        private String legacy(String cart) {
            calls.add("legacy");
            return "legacy:" + cart;
        }

        @FeatureToggle("counter")
        public int count() {
            return 7;
        }

        @FeatureToggle("maybe")
        public Optional<String> maybe() {
            return Optional.of("value");
        }

        @FeatureToggle("job")
        public void run() {
            calls.add("run");
        }

        @FeatureToggle(value = "job", fallbackMethod = "failingFallback")
        public String risky() throws IOException {
            return "ok";
        }

        String failingFallback() throws IOException {
            throw new IOException("fallback failed");
        }

        @FeatureToggle(value = "job", fallbackMethod = "absent")
        public String misconfigured() {
            return "ok";
        }
    }

    private final FluxGateClient client = mock(FluxGateClient.class);
    private final List<Object[]> resolved = new ArrayList<>();
    private EvaluationContextResolver resolver = (flagKey, method, arguments) -> {
        resolved.add(new Object[] { flagKey, method, arguments });
        return EvaluationContext.of("user-1");
    };
    private Checkout target;

    @BeforeEach
    void setUp() {
        target = new Checkout();
    }

    private Checkout proxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new FeatureToggleAspect(client, resolver, new FallbackMethodResolver()));
        return factory.getProxy();
    }

    private void flag(String key, boolean on) {
        when(client.isEnabled(eq(key), any())).thenReturn(on);
    }

    @Test
    void runsTheMethodWhenTheFlagIsOn() {
        flag("new-checkout", true);

        assertThat(proxy().plain("cart-1")).isEqualTo("new:cart-1");
        assertThat(target.calls).containsExactly("plain");
        verify(client).isEnabled("new-checkout", EvaluationContext.of("user-1"));
    }

    @Test
    void passesFlagMethodAndArgumentsToTheResolver() throws NoSuchMethodException {
        flag("new-checkout", true);

        proxy().plain("cart-1");

        assertThat(resolved).singleElement().satisfies(call -> {
            assertThat(call[0]).isEqualTo("new-checkout");
            assertThat((Method) call[1]).isEqualTo(Checkout.class.getMethod("plain", String.class));
            assertThat((Object[]) call[2]).containsExactly("cart-1");
        });
    }

    @Test
    void returnsNullWhenOffWithoutFallback() {
        flag("new-checkout", false);

        assertThat(proxy().plain("cart-1")).isNull();
        assertThat(target.calls).isEmpty();
    }

    @Test
    void callsThePrivateFallbackWithTheSameArgumentsWhenOff() {
        flag("new-checkout", false);

        assertThat(proxy().withFallback("cart-1")).isEqualTo("legacy:cart-1");
        assertThat(target.calls).containsExactly("legacy");
    }

    @Test
    void returnsTypeDefaultsWhenOff() {
        flag("counter", false);
        flag("maybe", false);
        flag("job", false);
        Checkout proxy = proxy();

        assertThat(proxy.count()).isZero();
        assertThat(proxy.maybe()).isEmpty();
        proxy.run();
        assertThat(target.calls).isEmpty();
    }

    @Test
    void fallbackExceptionsPropagateUnwrapped() {
        flag("job", false);

        assertThatThrownBy(() -> proxy().risky()).isInstanceOf(IOException.class).hasMessage("fallback failed");
    }

    @Test
    void missingFallbackFailsTheCallWithAClearMessage() {
        flag("job", false);

        assertThatThrownBy(() -> proxy().misconfigured())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fallback method absent() not found on " + Checkout.class.getName());
    }

    @Test
    void nullContextFromTheResolverMeansEmpty() {
        resolver = (flagKey, method, arguments) -> null;
        flag("new-checkout", true);

        proxy().plain("cart-1");

        verify(client).isEnabled("new-checkout", EvaluationContext.empty());
    }
}
