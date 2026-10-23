# FluxGate Java SDK

Java libraries for the FluxGate feature toggle edge server. They talk to the edge over
[OFREP](https://openfeature.dev/specification/appendix-c) (the OpenFeature Remote Evaluation
Protocol), authenticated with a client's SDK key.

| Artifact | Use it for |
|---|---|
| `com.forgeopslabs:fluxgate-java` | Any Java 17+ application. The typed `FluxGateClient` (JDK `HttpClient`, evaluation cache, retries, circuit breaker) and the OpenFeature provider `FluxGateProvider` with change polling and provider events. No Spring. |
| `com.forgeopslabs:fluxgate-spring-boot-starter` | Spring Boot 3.5 applications. Configures `fluxgate-java` from `fluxgate.*` properties and adds the `@FeatureToggle` method annotation, Micrometer metrics and an Actuator health indicator. |

Requirements: Java 17+. The starter needs Spring Boot 3.5.

[samples/](samples/) holds a plain Java app and a Spring Boot app that run against a local FluxGate
stack.

## Installation

Spring Boot:

```xml
<dependency>
    <groupId>com.forgeopslabs</groupId>
    <artifactId>fluxgate-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Plain Java:

```xml
<dependency>
    <groupId>com.forgeopslabs</groupId>
    <artifactId>fluxgate-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
FluxGateClient client = DefaultFluxGateClient.create(FluxGateOptions.builder()
        .baseUrl("https://edge.example.com")
        .sdkKey(System.getenv("FLUXGATE_SDK_KEY"))   // <clientId>.<apiKey>
        .build());

boolean enabled = client.isEnabled("new-checkout", EvaluationContext.of("user-1"));
```

`FluxGateOptions` has the same timeout, retry, cache and circuit breaker settings as the starter
properties below, with the same defaults. To use OpenFeature without Spring, add `dev.openfeature:sdk`
and register `new FluxGateProvider(client)`.

The rest of this page describes the Spring Boot starter. The client API in
[Typed client](#typed-client) and [OpenFeature](#openfeature) is the same in both artifacts.

### Starter features

Each optional feature turns on when its dependency is on your classpath:

| Feature | Add |
|---|---|
| OpenFeature provider | `dev.openfeature:sdk` (tested with 1.23.0) |
| `@FeatureToggle` annotation | `org.springframework.boot:spring-boot-starter-aop` |
| Metrics | `io.micrometer:micrometer-core` and a `MeterRegistry`, for example through `spring-boot-starter-actuator` |
| Health indicator | `org.springframework.boot:spring-boot-starter-actuator` |

## Configuration

```yaml
fluxgate:
  base-url: https://edge.example.com
  sdk-key: ${FLUXGATE_SDK_KEY}   # <clientId>.<apiKey>
```

When the starter is enabled, `base-url` and `sdk-key` are required. If either is missing or
malformed, startup fails with a message that names the property. The message never includes the
key itself.

| Property | Default | Description |
|---|---|---|
| `fluxgate.enabled` | `true` | Turns the starter on or off. When `false`, no properties are required and nothing contacts the edge. The only bean is a no-op `FluxGateClient` that returns your default with reason `DISABLED` and no error. `@FeatureToggle` methods keep working with every flag off, so they run their fallback or return the default. |
| `fluxgate.base-url` | (required) | Edge server URL, `http://` or `https://`. |
| `fluxgate.sdk-key` | (required) | SDK key, `<clientId>.<apiKey>`. Sent as `Authorization: Bearer <sdkKey>`. |
| `fluxgate.connect-timeout` | `5s` | Connect timeout. |
| `fluxgate.read-timeout` | `5s` | Read timeout. |
| `fluxgate.retry.max-attempts` | `3` | Attempts per call, counting the first. Only I/O errors and HTTP 502, 503 and 504 are retried. |
| `fluxgate.retry.initial-backoff` | `100ms` | Wait before the first retry. It doubles after each retry. |
| `fluxgate.circuit-breaker.failure-threshold` | `5` | Consecutive transport failures (I/O errors, timeouts, HTTP 5xx) that open the circuit breaker. 4xx answers never count. `0` disables the breaker. |
| `fluxgate.circuit-breaker.open-duration` | `30s` | How long the breaker stays open before one trial call. |
| `fluxgate.cache.enabled` | `true` | Caches successful evaluations by flag key and context. |
| `fluxgate.cache.ttl` | `30s` | How long a cached evaluation lives after it is written. |
| `fluxgate.cache.max-size` | `1000` | Maximum number of cached evaluations. |
| `fluxgate.polling.interval` | `30s` | How often the OpenFeature provider checks the flag set for changes. `0` turns polling off. The health indicator also uses this as its minimum `/health` probe age (30s when `0`). |
| `fluxgate.openfeature.auto-register` | `false` | Registers the provider with `OpenFeatureAPI` at startup. |
| `fluxgate.openfeature.provider-name` | (unset) | OpenFeature domain to register under. Unset means the default provider. |
| `fluxgate.health.enabled` | `true` | Adds the `fluxgate` health indicator. |
| `fluxgate.metrics.enabled` | `true` | Records Micrometer metrics. |

The jar includes Spring Boot configuration metadata, so IDEs can complete and document these
properties.

### Worst-case latency and the circuit breaker

Evaluations are blocking HTTP calls. When the edge does not answer, one call can take up to:

`max-attempts × (connect-timeout + read-timeout) + backoff`

where backoff is `initial-backoff × (1 + 2 + … + 2^(max-attempts-2))`. With the defaults that is
3 × (5s + 5s) + 100ms + 200ms ≈ 30.3s. Lower the timeouts or the number of attempts if your
request path cannot wait that long.

The circuit breaker bounds this. Single-flag and bulk calls (including OpenFeature polls) share
one breaker:

1. After `failure-threshold` consecutive transport failures, the breaker opens. Until then, each
   failing call can take the full worst-case time.
2. While it is open, evaluations return your default with `PROVIDER_NOT_READY` at once, without
   any HTTP call.
3. After `open-duration`, one trial call goes to the edge. If it succeeds, the breaker closes. If
   it fails, the breaker stays open for another `open-duration`.

Any edge answer below 500 (200, or 4xx such as 401, 403, 404 and 400) proves the edge is
reachable. It never counts as a failure, and it resets the consecutive count.

### SDK key and edge compatibility

- The SDK key has the form `<clientId>.<apiKey>`: the FluxGate client's ID and its API key,
  joined by a dot.
- The edge picks the environment from the client. Callers never send one. **Do not set an
  `environment_id` context attribute.** The edge returns 401 if it does not match the client's
  environment.
- The edge must be a version with SDK-key OFREP authentication, meaning it accepts
  `Authorization: Bearer <clientId>.<apiKey>` on `/ofrep/v1/evaluate/flags`. Older edges treat
  the bearer token as a bare client ID, so they reject these requests.

## Typed client

Inject `FluxGateClient`. Evaluation methods never throw. On any failure they return your default,
and `evaluate` reports why.

```java
@Service
class CheckoutService {

    private final FluxGateClient flags;

    CheckoutService(FluxGateClient flags) {
        this.flags = flags;
    }

    Receipt checkout(User user, Cart cart) {
        EvaluationContext ctx = EvaluationContext.builder()
                .targetingKey(user.id())          // required by the edge
                .attribute("country", user.country())
                .attribute("beta", user.isBeta())
                .build();

        if (flags.isEnabled("new-checkout", ctx)) {
            return newCheckout(cart);
        }
        return legacyCheckout(cart);
    }
}
```

| Method | Returns |
|---|---|
| `isEnabled(key, ctx)`, `getBoolean(key, default, ctx)` | `boolean` |
| `getString`, `getInteger`, `getDouble`, `getObject` (a `Map`) | the typed value or your default |
| `evaluate(key, default, Class<T>, ctx)` | `FlagEvaluation<T>`: `value`, `variant`, `reason`, `errorCode`, `errorMessage` |
| `evaluateAsync(key, default, Class<T>, ctx)` | `CompletableFuture<FlagEvaluation<T>>`. The future always completes normally. |
| `bulkEvaluate(ctx)` | every flag for the context, revalidated with `ETag` / `If-None-Match` |

Rules for values:

- **Types are exact.** An integer flag read with `getString` returns your default with
  `TYPE_MISMATCH`. The value is never converted. Integers can be read as `Double`.
- **Disabled flags return your default.** For a disabled or unassigned flag of any type, the edge
  sends `false` with no variant. The client returns your default with the edge's reason
  (`DISABLED` or `DEFAULT`), and no error.
- **Evaluations without a targeting key return the default.** The edge requires a targeting key.
  When the context has none (`null` or blank), the client returns your default with
  `TARGETING_KEY_MISSING` without calling the edge. This includes `isEnabled(key)`,
  `getString(key, default)` and the other context-free shortcuts, so always pass a context that
  has a targeting key.
- **Metadata.** `FlagEvaluation.metadata()` holds the flag's OFREP `metadata` object as an
  immutable map. It is empty when the edge sent none, and on errors.

`errorCode` is one of `FLAG_NOT_FOUND`, `TYPE_MISMATCH`, `TARGETING_KEY_MISSING`,
`INVALID_CONTEXT`, `PROVIDER_NOT_READY` (edge returned 503, or the circuit breaker is open),
`UNAUTHORIZED` (edge returned 401 or 403) and `GENERAL`.

### Async evaluation

`evaluateAsync` and the health indicator's background `/health` probes run on a
`FluxGateAsyncExecutor` bean if you define one. Otherwise they use `ForkJoinPool.commonPool()`. If
the executor rejects a task, the future completes with your default and a `GENERAL` error.

**In production, define a dedicated executor.** Evaluations are blocking HTTP calls, and with
retries one call can take several read timeouts. On the common pool they can starve other users
of that pool, such as parallel streams and `CompletableFuture` stages.

```java
@Bean
FluxGateAsyncExecutor fluxGateAsyncExecutor() {
    return FluxGateAsyncExecutor.of(Executors.newVirtualThreadPerTaskExecutor()); // Java 21
}
```

`FluxGateAsyncExecutor` is deliberately not a `java.util.concurrent.Executor`. Declaring a plain
`Executor` bean makes Spring Boot drop its `applicationTaskExecutor`, which changes `@Async` and
MVC async handling across the whole application. A `FluxGateAsyncExecutor` bean leaves Boot's
executor alone. The starter ignores plain `Executor` beans.

### Caching

Successful single-flag evaluations are cached by `(flag key, context)` for `cache.ttl`. Errors are
never cached. Version-change clearing happens only when the OpenFeature provider has been
initialized, either registered with `OpenFeatureAPI` or through
`fluxgate.openfeature.auto-register=true`, and polling is on. Then a new flag-set version clears
the cache. In every other case, including the default setup, which does not register the provider,
a flag change can take up to `cache.ttl` to show up.

## OpenFeature

Add `dev.openfeature:sdk`. The starter creates a `FluxGateProvider` bean.

Register it yourself:

```java
@Bean
ApplicationRunner registerFluxGate(FluxGateProvider provider) {
    return args -> OpenFeatureAPI.getInstance().setProviderAndWait(provider); // fails startup if the edge is down
}
```

Or let the starter register it without blocking startup:

```yaml
fluxgate:
  openfeature:
    auto-register: true
    # provider-name: payments   # optional OpenFeature domain
```

Then evaluate through the OpenFeature API:

```java
Client client = OpenFeatureAPI.getInstance().getClient();
EvaluationContext ctx = new ImmutableContext("user-42", Map.of("country", new Value("LK")));

boolean enabled = client.getBooleanValue("new-checkout", false, ctx);
FlagEvaluationDetails<String> banner = client.getStringDetails("banner-color", "grey", ctx);
```

### Behavior

- **Values and errors.** Reason and variant pass through unchanged. The flag's OFREP metadata
  becomes `flagMetadata`. Only string, boolean and number entries are kept; nested objects,
  arrays and nulls are dropped. Error codes map to
  OpenFeature codes with the same names. `UNAUTHORIZED` maps to `GENERAL`, and the edge's message
  is kept.
- **Object flags.** These become `Value` structures. An integer that does not fit in an `int`
  becomes a `Double`, because OpenFeature `Value` has no `long`. Beyond 2^53 this loses
  precision. If you need exact large numbers, read them with the typed client
  (`evaluate(key, 0L, Long.class, ctx)`).
- **Initialization.** The provider calls `GET /health` and then runs one bulk evaluation. If
  either fails, the provider goes to `ERROR` and emits `PROVIDER_ERROR`. Bulk evaluation needs a
  targeting key, so the provider uses the targeting key of the OpenFeature global context, or
  `fluxgate-openfeature-provider` when the global context has none.
- **Polling.** Every `polling.interval` the provider re-runs the bulk evaluation with
  `If-None-Match`. When the version changes, it clears the evaluation cache and emits
  `PROVIDER_CONFIGURATION_CHANGED` with the changed flag keys.
- **Errors.** A failed poll, or an evaluation the edge rejects with 401 or 403, moves the provider
  from `READY` to `ERROR` and emits `PROVIDER_ERROR`.
- **Recovery.** A provider in `ERROR` returns to `READY` and emits `PROVIDER_READY` after the next
  successful bulk poll. `/health` must answer at initialization, but recovery needs only a
  successful poll.
- **With `polling.interval=0`, `ERROR` is permanent.** No poll runs, so the provider never
  recovers on its own after a failed initialization or an evaluation rejected with 401 or 403.
  Keep a non-zero interval unless you manage provider lifecycle yourself.

## `@FeatureToggle`

Requires `spring-boot-starter-aop`. If the aspect is inactive and beans use the annotation,
the starter logs one warning at startup. The aspect is inactive when AspectJ is missing or
`spring.aop.auto=false`, and in that case annotated methods run regardless of their flags. With
`fluxgate.enabled=false` the aspect stays active and every flag is off.

```java
@Service
public class PricingService {

    @FeatureToggle(value = "new-pricing", fallbackMethod = "legacyPrice")
    public Money price(Cart cart) {
        return newPricingEngine.price(cart);
    }

    private Money legacyPrice(Cart cart) {    // same parameters, compatible return type
        return legacyEngine.price(cart);
    }

    @FeatureToggle("audit-log")
    public void audit(Order order) { ... }    // skipped while the flag is off
}
```

- **Flag on.** The method runs.
- **Flag off.** "Off" includes a failed evaluation. The method named by `fallbackMethod` runs with
  the same arguments. With no fallback, the call returns `null`, `Optional.empty()`, or `false`/`0`
  for primitive return types.
- **Async and reactive methods.** The flag is evaluated synchronously and blocks the calling
  thread. A method returning `CompletableFuture`, `CompletionStage`, `Mono` or `Flux` gets `null`
  when the flag is off and there is no fallback, so give such methods a fallback.
- **Fallback requirements.** The fallback must be on the same class or a superclass, take exactly
  the same parameter types, and return a type assignable to the annotated method's return type. It
  may be private. If it is missing or incompatible, **application startup fails**.
- **Proxy limits.** The annotation works only through Spring AOP proxies:
  - It applies only to calls made through the bean's proxy. Self-invocation (`this.price(...)`)
    is not intercepted.
  - Private, final and static methods are not intercepted. The starter logs a warning when it
    finds one annotated.
  - Put the annotation on the implementation class, not on an interface method.
  - `spring.aop.auto` must stay `true`, which is the default.

### Evaluation context

The edge requires a targeting key. Declare an `EvaluationContextResolver` bean to supply one.
Without a resolver, the context is empty, so every annotated flag is treated as off.

For example, to use the Spring Security principal:

```java
@Bean
EvaluationContextResolver securityContextResolver() {
    return (flagKey, method, arguments) -> {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return EvaluationContext.empty();
        }
        return EvaluationContext.builder()
                .targetingKey(auth.getName())
                .attribute("roles", auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .build();
    };
}
```

## Metrics

Metrics are recorded when Micrometer and a `MeterRegistry` bean are present, unless you set
`fluxgate.metrics.enabled=false`.

| Meter | Type | Tags |
|---|---|---|
| `fluxgate.evaluation` | timer, one sample per flag evaluation | `flag`, `reason`, `variant` (`none` when absent), `error` (error code or `none`) |
| `fluxgate.cache` | counter, one per cache lookup while the cache is on | `result` = `hit` or `miss` |

Tag values come from flag keys, variants, reasons and error codes. Cardinality stays bounded as
long as flag keys are static strings in your code, not built from user input. The targeting key
and context attributes are never tags.

## Health

With Actuator, the starter adds a health indicator named `fluxgate`
(`/actuator/health/fluxgate`). It never calls the edge while answering a health request. Status
comes from cached state:

- **`DOWN`.** The last `/health` probe returned not ready, or the last edge call failed and that
  failure still counts. A failed call is an evaluation or bulk poll that ended with
  `UNAUTHORIZED`, `PROVIDER_NOT_READY` or a transport/5xx error.
  - A transient failure (`PROVIDER_NOT_READY`, transport/5xx) stops counting once a healthy
    `/health` probe finishes after it. The indicator starts that probe as soon as it sees a
    failure newer than its last probe, so a single blip does not hold the status at `DOWN`.
    Cache hits never reach the edge, so they cannot clear a failure.
  - `UNAUTHORIZED` (401/403) keeps the status `DOWN` until a later call succeeds, because `/health`
    cannot detect a bad SDK key.
- **`UP`.** At least one signal exists and none failed. Answers such as `FLAG_NOT_FOUND` count as
  success, because they show the edge is serving.
- **`UNKNOWN`.** Nothing has been observed yet.

If the cached `/health` probe is older than `polling.interval` (30s when polling is off), or older
than a transient failure, a health request starts one background probe on the async executor and
answers immediately. The next request sees the new result. Details include `edgeHealth`, `lastCall`, `lastCallStatus`,
`errorCode` and `error`.

Keep the `fluxgate` indicator out of readiness and load-balancer health groups. Your application
keeps serving with default flag values while the edge is down. If the indicator is in such a
group, an edge outage takes every instance out of rotation at once. For example:

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState   # without fluxgate
```

## Extending

- **Custom beans.** Defining your own `FluxGateClient`, `OfrepTransport`, `FluxGateProvider`
  (named `fluxGateProvider`), `FluxGateMetrics` or `EdgeHealthTracker` bean replaces the starter's.
- **Listeners.** Implement `FluxGateClientListener` and declare it as a bean to observe
  evaluations, cache lookups and bulk results. Metrics and health are built this way. Listeners
  attach only to the auto-configured `DefaultFluxGateClient`.
- **Without Spring.** `DefaultFluxGateClient.create(FluxGateOptions)` builds a client; see [Installation](#installation).
- **Internal packages.** `com.forgeopslabs.fluxgate.ofrep.*` and `com.forgeopslabs.fluxgate.cache.*`
  are internal, with no compatibility guarantee between releases. Depend on `FluxGateClient`,
  `FlagEvaluation`, `EvaluationContext`, `FluxGateOptions` and the other types in
  `com.forgeopslabs.fluxgate` and `com.forgeopslabs.fluxgate.openfeature`, and on the starter's
  `com.forgeopslabs.fluxgate.spring` packages.

## Building

```bash
mvn clean verify
```

The build enforces 80% line coverage per package with JaCoCo. CI builds on JDK 17, 21 and 25.

## Releasing

`.github/workflows/release.yml` publishes `fluxgate-parent`, `fluxgate-java` and
`fluxgate-spring-boot-starter` to Maven Central through the
[Central Portal](https://central.sonatype.com) when a GitHub release is published. The samples are
never published. The release tag sets the version: tag `v1.2.0` publishes `1.2.0`. The POMs on
`main` stay on a `-SNAPSHOT` version.

One-time setup:

1. Sign in to the Central Portal and add the namespace `com.forgeopslabs`. Verify it with the DNS
   TXT record the portal shows for `forgeopslabs.com`.
2. Generate a Central Portal user token (View Account > Generate User Token).
3. Create a GPG key for signing, publish the public key to a key server (for example
   `gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>`), and export the private key with
   `gpg --armor --export-secret-keys <KEY_ID>`.
4. Add the repository secrets `CENTRAL_USERNAME`, `CENTRAL_TOKEN`, `GPG_PRIVATE_KEY` and
   `GPG_PASSPHRASE`.

Maven Central releases are permanent: a published version can never be changed or deleted.

## License

Apache License 2.0
