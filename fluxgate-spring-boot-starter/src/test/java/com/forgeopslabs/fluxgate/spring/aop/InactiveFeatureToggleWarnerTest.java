package com.forgeopslabs.fluxgate.spring.aop;

import com.forgeopslabs.fluxgate.spring.annotation.FeatureToggle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InactiveFeatureToggleWarnerTest {

    static class Toggled {

        @FeatureToggle("f")
        public void run() {
        }
    }

    static class Plain {

        public void run() {
        }
    }

    private final List<String> warnings = new ArrayList<>();
    private final InactiveFeatureToggleWarner warner = new InactiveFeatureToggleWarner(warnings::add);

    @Test
    void logsOneWarningNamingTheAnnotatedBeans() {
        warner.postProcessAfterInitialization(new Toggled(), "first");
        warner.postProcessAfterInitialization(new Toggled(), "sameClassAgain");
        warner.postProcessAfterInitialization(new Plain(), "plain");
        warner.postProcessAfterInitialization("a string", "jdkType");

        warner.afterSingletonsInstantiated();

        assertThat(warnings).singleElement().asString()
                .contains("[first]", "aspect is inactive", "spring-boot-starter-aop", "spring.aop.auto=true");
    }

    @Test
    void staysSilentWithoutAnnotatedBeans() {
        warner.postProcessAfterInitialization(new Plain(), "plain");

        warner.afterSingletonsInstantiated();

        assertThat(warnings).isEmpty();
        assertThat(new InactiveFeatureToggleWarner()).isNotNull();
    }
}
