package com.forgeopslabs.fluxgate.sample;

import com.forgeopslabs.fluxgate.spring.annotation.FeatureToggle;
import org.springframework.stereotype.Service;

/**
 * Service whose method runs only while the boolean flag {@code new-checkout}
 * is on for the caller; otherwise the fallback method runs.
 */
@Service
public class CheckoutService {

    @FeatureToggle(value = "new-checkout", fallbackMethod = "classicCheckoutPage")
    public String checkoutPage(String userId, String country) {
        return "new checkout";
    }

    String classicCheckoutPage(String userId, String country) {
        return "classic checkout";
    }
}
