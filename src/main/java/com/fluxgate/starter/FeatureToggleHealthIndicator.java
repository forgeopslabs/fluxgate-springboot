package com.fluxgate.starter;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator for the FluxGate Feature Toggle Edge Server connectivity.
 * This integrates with Spring Boot Actuator to provide health check
 * information.
 */
public class FeatureToggleHealthIndicator implements HealthIndicator {

    private final FluxGateClient featureToggleClient;

    public FeatureToggleHealthIndicator(FluxGateClient featureToggleClient) {
        this.featureToggleClient = featureToggleClient;
    }

    @Override
    public Health health() {
        try {
            boolean isHealthy = featureToggleClient.isHealthy();

            if (isHealthy) {
                return Health.up()
                        .withDetail("status", "Edge server is reachable")
                        .withDetail("service", "fluxgate-edge-server")
                        .build();
            } else {
                return Health.down()
                        .withDetail("status", "Edge server is not reachable")
                        .withDetail("service", "fluxgate-edge-server")
                        .build();
            }

        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
            return Health.down()
                    .withDetail("status", "Health check failed")
                    .withDetail("service", "fluxgate-edge-server")
                    .withDetail("error", errorMessage)
                    .build();
        }
    }
}