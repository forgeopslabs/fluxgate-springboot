# FluxGate Spring Boot Starter

A Spring Boot starter for the FluxGate feature toggle edge server. It talks to the edge over
[OFREP](https://openfeature.dev/specification/appendix-c) (the OpenFeature Remote Evaluation
Protocol) and provides:

- a typed `FluxGateClient` with a short-lived evaluation cache and retries,
- an OpenFeature provider (`FluxGateProvider`) with change polling and provider events,
- a `@FeatureToggle` method annotation,
- Micrometer metrics and an Actuator health indicator.

Requirements: Java 17+, Spring Boot 3.5.

## Installation

```xml
<dependency>
    <groupId>io.github.keaz</groupId>
    <artifactId>fluxgate-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

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
| `fluxgate.enabled` | `true` | Turns the whole starter on or off. When `false`, no beans are created and no properties are required. |
| `fluxgate.base-url` | (required) | Edge server URL, `http://` or `https://`. |
| `fluxgate.sdk-key` | (required) | SDK key, `<clientId>.<apiKey>`. Sent as `Authorization: Bearer <sdkKey>`. |
| `fluxgate.connect-timeout` | `5s` | Connect timeout. |
| `fluxgate.read-timeout` | `5s` | Read timeout. |
| `fluxgate.retry.max-attempts` | `3` | Attempts per call, counting the first. Only I/O errors and HTTP 502, 503 and 504 are retried. |
| `fluxgate.retry.initial-backoff` | `100ms` | Wait before the first retry. It doubles after each retry. |
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
- **Evaluations without a context return the default.** `isEnabled(key)`,
  `getString(key, default)` and the other context-free shortcuts send no targeting key. The edge
  requires one, so these calls always return your default with `TARGETING_KEY_MISSING`. Always
  pass a context that has a targeting key.

`errorCode` is one of `FLAG_NOT_FOUND`, `TYPE_MISMATCH`, `TARGETING_KEY_MISSING`,
`INVALID_CONTEXT`, `PROVIDER_NOT_READY` (edge returned 503), `UNAUTHORIZED` (edge returned 401 or
403) and `GENERAL`.

### Async evaluation

`evaluateAsync` runs on the `Executor` bean named `fluxGateAsyncExecutor` if you define one.
Otherwise it uses `ForkJoinPool.commonPool()`. If the executor rejects the task, the future
completes with your default and a `GENERAL` error.

```java
@Bean(FeatureToggleAutoConfiguration.ASYNC_EXECUTOR_BEAN_NAME)   // "fluxGateAsyncExecutor"
Executor fluxGateAsyncExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();           // Java 21
}
```

### Caching

Successful single-flag evaluations are cached by `(flag key, context)` for `cache.ttl`. Errors are
never cached. When the OpenFeature provider sees a new flag-set version, it clears the cache.
Without polling, a flag change can take up to `cache.ttl` to show up.

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

- **Values and errors.** Reason and variant pass through unchanged. Error codes map to
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

Requires `spring-boot-starter-aop`.

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

Tag values come from flag keys, variants, reasons and error codes, so cardinality stays bounded.
The targeting key and context attributes are never tags.

## Health

With Actuator, the starter adds a health indicator named `fluxgate`
(`/actuator/health/fluxgate`). It never calls the edge while answering a health request. Status
comes from cached state:

- **`DOWN`.** The last edge call failed, or the last `/health` probe returned not ready. A failed
  call is an evaluation or bulk poll that ended with `UNAUTHORIZED`, `PROVIDER_NOT_READY` or a
  transport/5xx error.
- **`UP`.** At least one signal exists and none failed. Answers such as `FLAG_NOT_FOUND` count as
  success, because they show the edge is serving.
- **`UNKNOWN`.** Nothing has been observed yet.

If the cached `/health` probe is older than `polling.interval` (30s when polling is off), a health
request starts one background probe on the async executor and answers immediately. The next
request sees the new result. Details include `edgeHealth`, `lastCall`, `lastCallStatus`,
`errorCode` and `error`.

## Extending

- **Custom beans.** Defining your own `FluxGateClient`, `OfrepTransport`, `FluxGateProvider`
  (named `fluxGateProvider`), `FluxGateMetrics` or `EdgeHealthTracker` bean replaces the starter's.
- **Listeners.** Implement `FluxGateClientListener` and declare it as a bean to observe
  evaluations, cache lookups and bulk results. Metrics and health are built this way. Listeners
  attach only to the auto-configured `DefaultFluxGateClient`.
- **Without Spring.** `DefaultFluxGateClient.create(properties)` builds a client.

## Building

```bash
mvn clean verify
```

The build enforces 80% line coverage per package with JaCoCo.

## License

Apache License 2.0
