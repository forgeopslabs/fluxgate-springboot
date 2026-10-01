package com.fluxgate.starter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads edge contract fixtures from {@code src/test/resources/edge-contract}.
 */
public final class EdgeContract {

    public static final String SDK_KEY = "client-1.api-key-1";
    public static final String FLAG_PATH = "/ofrep/v1/evaluate/flags/";
    public static final String BULK_PATH = "/ofrep/v1/evaluate/flags";
    public static final String BULK_ETAG = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    private EdgeContract() {
    }

    public static String fixture(String name) {
        try (InputStream in = EdgeContract.class.getResourceAsStream("/edge-contract/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No edge-contract fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
