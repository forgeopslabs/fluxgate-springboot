package com.forgeopslabs.fluxgate.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot app that evaluates FluxGate flags through the starter.
 * See {@code samples/README.md} for how to run it against a local edge server.
 */
@SpringBootApplication
public class FluxGateSpringBootSample {

    public static void main(String[] args) {
        SpringApplication.run(FluxGateSpringBootSample.class, args);
    }
}
