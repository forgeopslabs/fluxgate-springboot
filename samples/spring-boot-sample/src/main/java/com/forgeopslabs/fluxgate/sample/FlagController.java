package com.forgeopslabs.fluxgate.sample;

import com.forgeopslabs.fluxgate.EvaluationContext;
import com.forgeopslabs.fluxgate.FlagEvaluation;
import com.forgeopslabs.fluxgate.FluxGateClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP endpoints that show the starter's two styles: an injected
 * {@link FluxGateClient} and the {@code @FeatureToggle} annotation.
 */
@RestController
public class FlagController {

    private final FluxGateClient fluxGate;
    private final CheckoutService checkout;

    public FlagController(FluxGateClient fluxGate, CheckoutService checkout) {
        this.fluxGate = fluxGate;
        this.checkout = checkout;
    }

    /**
     * Evaluates any flag for a user, for example
     * {@code GET /flags/new-checkout?user=user-1&country=CA&tier=plus}.
     */
    @GetMapping("/flags/{key}")
    public Map<String, Object> evaluate(@PathVariable String key,
            @RequestParam(defaultValue = "user-1") String user,
            @RequestParam(defaultValue = "US") String country,
            @RequestParam(defaultValue = "free") String tier) {
        EvaluationContext context = EvaluationContext.builder()
                .targetingKey(user)
                .attribute("country", country)
                .attribute("user_tier", tier)
                .build();
        FlagEvaluation<Object> evaluation = fluxGate.evaluate(key, "default", Object.class, context);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("flag", evaluation.key());
        body.put("user", user);
        body.put("value", evaluation.value());
        body.put("variant", evaluation.variant());
        body.put("reason", evaluation.reason());
        body.put("errorCode", evaluation.errorCode());
        body.put("errorMessage", evaluation.errorMessage());
        return body;
    }

    /**
     * Runs a method guarded by {@code @FeatureToggle("new-checkout")}, for
     * example {@code GET /checkout?user=user-5&country=CA}.
     */
    @GetMapping("/checkout")
    public Map<String, String> checkout(@RequestParam(defaultValue = "user-1") String user,
            @RequestParam(defaultValue = "US") String country) {
        return Map.of("user", user, "country", country, "checkout", checkout.checkoutPage(user, country));
    }
}
