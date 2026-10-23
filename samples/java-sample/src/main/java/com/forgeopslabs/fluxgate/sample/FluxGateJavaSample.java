package com.forgeopslabs.fluxgate.sample;

import com.forgeopslabs.fluxgate.DefaultFluxGateClient;
import com.forgeopslabs.fluxgate.EvaluationContext;
import com.forgeopslabs.fluxgate.FlagEvaluation;
import com.forgeopslabs.fluxgate.FluxGateClient;
import com.forgeopslabs.fluxgate.FluxGateOptions;
import com.forgeopslabs.fluxgate.openfeature.FluxGateProvider;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;

import java.util.List;
import java.util.Map;

/**
 * Evaluates one flag against a FluxGate edge server, first with the
 * {@link FluxGateClient} and then through OpenFeature.
 *
 * <p>
 * Settings come from environment variables:
 * <ul>
 * <li>{@code FLUXGATE_BASE_URL}: edge server URL, default {@code http://localhost:18081}</li>
 * <li>{@code FLUXGATE_SDK_KEY}: SDK key {@code <clientId>.<apiKey>} of a client (required)</li>
 * <li>{@code FLUXGATE_FLAG}: flag key to evaluate, default {@code new-checkout}</li>
 * </ul>
 *
 * Exits with status 1 when the edge is unreachable or an evaluation fails.
 */
public final class FluxGateJavaSample {

    private record SampleUser(String id, String country, String tier) {
    }

    private static final List<SampleUser> USERS = List.of(
            new SampleUser("user-1", "CA", "plus"),
            new SampleUser("user-2", "US", "free"),
            new SampleUser("user-3", "US", "plus"),
            new SampleUser("user-4", "DE", "free"));

    private FluxGateJavaSample() {
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = env("FLUXGATE_BASE_URL", "http://localhost:18081");
        String sdkKey = env("FLUXGATE_SDK_KEY", null);
        String flag = env("FLUXGATE_FLAG", "new-checkout");
        if (sdkKey == null) {
            System.err.println("Set FLUXGATE_SDK_KEY to the SDK key of a FluxGate client (<clientId>.<apiKey>).");
            System.exit(2);
        }

        FluxGateClient client = DefaultFluxGateClient.create(FluxGateOptions.builder()
                .baseUrl(baseUrl)
                .sdkKey(sdkKey)
                .build());

        System.out.printf("Edge %s healthy: %s%n", baseUrl, client.isHealthy());
        boolean failed = false;

        System.out.printf("%nFluxGateClient, flag '%s':%n", flag);
        for (SampleUser user : USERS) {
            EvaluationContext context = EvaluationContext.builder()
                    .targetingKey(user.id())
                    .attribute("country", user.country())
                    .attribute("user_tier", user.tier())
                    .build();
            // Object.class accepts any flag type: boolean, string, number or JSON
            FlagEvaluation<Object> evaluation = client.evaluate(flag, "default", Object.class, context);
            System.out.printf("  %-7s %s/%-4s -> value=%s variant=%s reason=%s%s%n",
                    user.id(), user.country(), user.tier(), evaluation.value(), evaluation.variant(),
                    evaluation.reason(), evaluation.isError() ? " error=" + evaluation.errorCode() : "");
            failed |= evaluation.isError();
        }

        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(new FluxGateProvider(client));
        Client openFeature = api.getClient();

        System.out.printf("%nOpenFeature, flag '%s':%n", flag);
        for (SampleUser user : USERS) {
            ImmutableContext context = new ImmutableContext(user.id(), Map.of(
                    "country", new Value(user.country()),
                    "user_tier", new Value(user.tier())));
            FlagEvaluationDetails<Value> details = openFeature.getObjectDetails(flag, new Value("default"), context);
            System.out.printf("  %-7s -> value=%s variant=%s reason=%s%s%n",
                    user.id(), details.getValue().asObject(), details.getVariant(), details.getReason(),
                    details.getErrorCode() != null ? " error=" + details.getErrorCode() : "");
            failed |= details.getErrorCode() != null;
        }

        api.shutdown();
        if (failed) {
            System.err.println("\nSome evaluations failed. Check FLUXGATE_SDK_KEY, the flag key and the edge logs.");
            System.exit(1);
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
