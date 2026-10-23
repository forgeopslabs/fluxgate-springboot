package com.forgeopslabs.fluxgate.spring.aop;

import com.forgeopslabs.fluxgate.spring.DisabledFluxGateAutoConfiguration;
import com.forgeopslabs.fluxgate.DisabledFluxGateClient;
import com.forgeopslabs.fluxgate.EvaluationContext;
import com.forgeopslabs.fluxgate.spring.FeatureToggleAutoConfiguration;
import com.forgeopslabs.fluxgate.FluxGateClient;
import com.forgeopslabs.fluxgate.spring.annotation.EvaluationContextResolver;
import com.forgeopslabs.fluxgate.spring.annotation.FeatureToggle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureToggleAspectAutoConfigurationTest {

    public static class Greeter {

        @FeatureToggle(value = "greeting", fallbackMethod = "plainHello")
        public String hello(String name) {
            return "Hey " + name + "!";
        }

        String plainHello(String name) {
            return "Hello " + name;
        }

        @FeatureToggle("ignored")
        private String hidden() {
            return "hidden";
        }

        @FeatureToggle("ignored")
        public final String sealed() {
            return hidden();
        }
    }

    public static class Broken {

        @FeatureToggle(value = "greeting", fallbackMethod = "nope")
        public String hello(String name) {
            return name;
        }
    }

    private final FluxGateClient client = mock(FluxGateClient.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class,
                    FeatureToggleAutoConfiguration.class, FeatureToggleAspectAutoConfiguration.class))
            .withPropertyValues("fluxgate.base-url=http://localhost:1", "fluxgate.sdk-key=client.key")
            .withBean(FluxGateClient.class, () -> client);

    @Test
    void annotatedBeansAreToggled() {
        when(client.isEnabled(eq("greeting"), any())).thenReturn(true, false);

        contextRunner.withBean(Greeter.class).run(context -> {
            assertThat(context).hasSingleBean(FeatureToggleAspect.class);
            Greeter greeter = context.getBean(Greeter.class);

            assertThat(greeter.hello("Ada")).isEqualTo("Hey Ada!");
            assertThat(greeter.hello("Ada")).isEqualTo("Hello Ada");
            verify(client, times(2)).isEnabled("greeting", EvaluationContext.empty());
        });
    }

    @Test
    void usesTheResolverBean() {
        when(client.isEnabled(eq("greeting"), any())).thenReturn(true);
        EvaluationContextResolver resolver = (flagKey, method, arguments) ->
                EvaluationContext.of((String) arguments[0]);

        contextRunner.withBean(Greeter.class).withBean(EvaluationContextResolver.class, () -> resolver)
                .run(context -> {
                    context.getBean(Greeter.class).hello("ada");

                    verify(client).isEnabled("greeting", EvaluationContext.of("ada"));
                });
    }

    @Test
    void missingFallbackFailsStartup() {
        contextRunner.withBean(Broken.class).run(context -> assertThat(context).getFailure()
                .rootCause().isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fallback method nope(String) not found"));
        contextRunner.withBean(Broken.class).run(context -> assertThat(context).getFailure()
                .hasCauseInstanceOf(BeanInitializationException.class)
                .hasStackTraceContaining("Invalid @FeatureToggle on bean"));
    }

    @Test
    void withoutAspectJRegistersOnlyTheInactiveWarner() {
        contextRunner.withClassLoader(new FilteredClassLoader("org.aspectj"))
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(FeatureToggleAspect.class)
                        .hasSingleBean(InactiveFeatureToggleWarner.class));
    }

    @Test
    void autoProxyOffRegistersOnlyTheInactiveWarner() {
        contextRunner.withPropertyValues("spring.aop.auto=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(FeatureToggleAspect.class)
                        .hasSingleBean(InactiveFeatureToggleWarner.class));
    }

    @Test
    void activeAspectNeedsNoWarner() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(InactiveFeatureToggleWarner.class));
    }

    @Test
    void disabledStarterKeepsTheAspectWithEveryFlagOff() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class,
                        FeatureToggleAutoConfiguration.class, DisabledFluxGateAutoConfiguration.class,
                        FeatureToggleAspectAutoConfiguration.class))
                .withPropertyValues("fluxgate.enabled=false")
                .withBean(Greeter.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FeatureToggleAspect.class);
                    assertThat(context.getBean(FluxGateClient.class)).isInstanceOf(DisabledFluxGateClient.class);
                    assertThat(context.getBean(Greeter.class).hello("Ada")).isEqualTo("Hello Ada");
                });
    }
}
