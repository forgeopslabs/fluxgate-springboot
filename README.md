# FluxGate Spring Boot Starter

A Spring Boot starter for integrating with the FluxGate Feature Toggle Edge Server. This starter provides easy integration with FluxGate's feature flag system, allowing you to evaluate feature flags in your Spring Boot applications.

## Features

- 🚀 **Easy Integration**: Auto-configuration with Spring Boot
- 🔄 **Retry Logic**: Built-in retry with exponential backoff
- 📊 **Metrics**: Optional Micrometer metrics integration
- 🏥 **Health Checks**: Spring Boot Actuator health indicator
- ⚡ **Async Support**: Asynchronous feature flag evaluation
- 🛡️ **Fallback Support**: Graceful degradation when edge server is unavailable
- 🎯 **Context Support**: User attributes and contextual evaluations

## Quick Start

### 1. Add Dependency

Add the starter to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.keaz</groupId>
    <artifactId>fluxgate-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configuration

Configure the edge server connection in your `application.yml`:

```yaml
feature:
  toggle:
    base-url: http://localhost:8081
    client-id: your-client-id
    client-secret: your-client-secret
    connection-timeout: PT5S
    read-timeout: PT5S
    retry-attempts: 3
    retry-delay: PT1S
    fallback-enabled: true
```

### 3. Basic Usage

Inject and use the `FluxGateClient`:

```java
@RestController
public class MyController {

    private final FluxGateClient fluxGateClient;

    public MyController(FluxGateClient fluxGateClient) {
        this.fluxGateClient = fluxGateClient;
    }

    @GetMapping("/api/data")
    public ResponseEntity<String> getData() {
        boolean newFeatureEnabled = fluxGateClient.isEnabled("new-feature", "prod");
        
        if (newFeatureEnabled) {
            return ResponseEntity.ok("New feature is enabled!");
        } else {
            return ResponseEntity.ok("Using legacy implementation");
        }
    }
}
```

## Configuration Properties

| Property | Description | Default | Type |
|----------|-------------|---------|------|
| `feature.toggle.base-url` | Base URL of the FluxGate Edge Server | `http://localhost:8081` | String |
| `feature.toggle.client-id` | Client ID for authentication | - | String |
| `feature.toggle.client-secret` | Client secret for authentication | - | String |
| `feature.toggle.connection-timeout` | Connection timeout | `PT5S` | Duration |
| `feature.toggle.read-timeout` | Read timeout | `PT5S` | Duration |
| `feature.toggle.retry-attempts` | Number of retry attempts | `3` | Integer |
| `feature.toggle.retry-delay` | Initial retry delay | `PT1S` | Duration |
| `feature.toggle.max-retry-delay` | Maximum retry delay | `PT10S` | Duration |
| `feature.toggle.retry-multiplier` | Retry delay multiplier | `2.0` | Double |
| `feature.toggle.fallback-enabled` | Enable fallback behavior | `true` | Boolean |
| `feature.toggle.default-fallback-value` | Default fallback value | `false` | Boolean |
| `feature.toggle.metrics-enabled` | Enable Micrometer metrics | `true` | Boolean |
| `feature.toggle.health-check-enabled` | Enable health indicator | `true` | Boolean |

## API Reference

### FeatureToggleClient Interface

#### Basic Methods

```java
// Simple evaluation
boolean isEnabled(String featureKey, String environmentId);

// With context
boolean isEnabled(String featureKey, String environmentId, Map<String, String> context);

// With request object
boolean isEnabled(FeatureEvaluationRequest request);
```

### Execution Methods

```java
fluxGateClient.execute(request, unused ->result.append("✅ Special action executed successfully!"));
```

### Execute and Return Result

```java
Double calculationResult = fluxGateClient.executeAndReturn(request, this::performComplexCalculation);
```

### Execute with Fallback

```java
List<String> recommendations = fluxGateClient.executeAndReturnWithFallback(
                request,
                this::generateAIRecommendations, // Only called if feature enabled
                getDefaultRecommendations() // Fallback value
        );
```

#### Async Methods

```java
// Async variants
CompletableFuture<Boolean> isEnabledAsync(String featureKey, String environmentId);
CompletableFuture<Boolean> isEnabledAsync(String featureKey, String environmentId, Map<String, String> context);
CompletableFuture<Boolean> isEnabledAsync(FeatureEvaluationRequest request);
```

#### Fallback Methods

```java
// With fallback values (never throws exceptions)
boolean isEnabledWithFallback(String featureKey, String environmentId, boolean fallback);
boolean isEnabledWithFallback(String featureKey, String environmentId, Map<String, String> context, boolean fallback);
boolean isEnabledWithFallback(FeatureEvaluationRequest request, boolean fallback);
```

## Usage Examples

### Simple Feature Flag Check

```java
@Service
public class UserService {

    private final FeatureToggleClient featureToggleClient;

    public UserService(FeatureToggleClient featureToggleClient) {
        this.featureToggleClient = featureToggleClient;
    }

    public User createUser(CreateUserRequest request) {
        boolean emailVerificationEnabled = featureToggleClient.isEnabled("email-verification", "prod");
        
        User user = new User(request);
        
        if (emailVerificationEnabled) {
            emailService.sendVerificationEmail(user);
        }
        
        return userRepository.save(user);
    }
}
```

### Context-Based Evaluation

```java
@Service
public class RecommendationService {

    private final FeatureToggleClient featureToggleClient;

    public List<Product> getRecommendations(User user) {
        Map<String, String> context = Map.of(
            "user.id", user.getId().toString(),
            "user.tier", user.getTier().name(),
            "user.country", user.getCountry()
        );

        boolean mlRecommendationsEnabled = featureToggleClient.isEnabled(
            "ml-recommendations", "prod", context);

        if (mlRecommendationsEnabled) {
            return mlRecommendationEngine.getRecommendations(user);
        } else {
            return simpleRecommendationEngine.getRecommendations(user);
        }
    }
}
```

### Using Request Builder

```java
@Service
public class PaymentService {

    private final FeatureToggleClient featureToggleClient;

    public void processPayment(Payment payment) {
        FeatureEvaluationRequest request = FeatureEvaluationRequest.builder()
            .featureKey("new-payment-gateway")
            .environmentId("prod")
            .context(Map.of(
                "user.id", payment.getUserId().toString(),
                "payment.amount", payment.getAmount().toString(),
                "payment.currency", payment.getCurrency()
            ))
            .build();

        boolean useNewGateway = featureToggleClient.isEnabled(request);
        
        if (useNewGateway) {
            newPaymentGateway.processPayment(payment);
        } else {
            legacyPaymentGateway.processPayment(payment);
        }
    }
}
```

### Async Evaluation

```java
@Service
public class AsyncFeatureService {

    private final FeatureToggleClient featureToggleClient;

    public CompletableFuture<String> processDataAsync() {
        CompletableFuture<Boolean> featureCheck = featureToggleClient
            .isEnabledAsync("async-processing", "prod");

        return featureCheck.thenApply(enabled -> {
            if (enabled) {
                return processWithNewAlgorithm();
            } else {
                return processWithLegacyAlgorithm();
            }
        });
    }
}
```

### Fallback Handling

```java
@Service
public class ResilientService {

    private final FeatureToggleClient featureToggleClient;

    public void performOperation() {
        // Never throws exceptions - uses fallback if edge server is down
        boolean featureEnabled = featureToggleClient.isEnabledWithFallback(
            "experimental-feature", "prod", false);

        if (featureEnabled) {
            tryExperimentalFeature();
        } else {
            useStableImplementation();
        }
    }
}
```

## Monitoring and Observability

### Metrics

When Micrometer is on the classpath, the starter automatically provides metrics:

- `feature_toggle.evaluations.total` - Total number of evaluations
- `feature_toggle.evaluations.successful` - Successful evaluations  
- `feature_toggle.evaluations.failed` - Failed evaluations
- `feature_toggle.fallbacks.used` - Fallback usage count
- `feature_toggle.evaluation.duration` - Evaluation duration

### Health Checks

When Spring Boot Actuator is available, the starter provides a health indicator:

```bash
GET /actuator/health/featureToggle
```

Response:
```json
{
  "status": "UP",
  "details": {
    "status": "Edge server is reachable",
    "service": "fluxgate-edge-server"
  }
}
```

### Logging

The starter uses SLF4J for logging. Configure logging levels:

```yaml
logging:
  level:
    com.fluxgate.starter: DEBUG
```

Log levels:
- `DEBUG`: Request/response details, enrichment info
- `INFO`: Client initialization, metrics setup
- `WARN`: Retries, client/server errors
- `ERROR`: Unexpected errors

## Error Handling

The starter provides comprehensive error handling:

### Exception Types

- `FeatureToggleException`: Base exception for all feature toggle operations
  - Contains feature key, environment ID, and HTTP status code when applicable
  - Supports both checked and unchecked scenarios

### Error Scenarios

1. **Network Issues**: Automatic retry with exponential backoff
2. **Authentication Errors**: No retry, immediate failure
3. **Server Errors**: Retry with backoff
4. **Client Errors**: No retry, immediate failure

### Fallback Behavior

```java
// Global fallback configuration
feature:
  toggle:
    fallback-enabled: true
    default-fallback-value: false

// Per-request fallback
boolean result = featureToggleClient.isEnabledWithFallback("feature", "env", true);
```

## Testing

### Mock the Client

```java
@SpringBootTest
public class MyServiceTest {

    @MockBean
    private FeatureToggleClient featureToggleClient;

    @Test
    public void testFeatureEnabled() {
        when(featureToggleClient.isEnabled("my-feature", "test"))
            .thenReturn(true);

        // Your test logic here
    }
}
```

### Test Properties

```yaml
# application-test.yml
feature:
  toggle:
    base-url: http://localhost:8081
    fallback-enabled: true
    default-fallback-value: false
```

## Advanced Configuration

### Custom RestTemplate

```java
@Configuration
public class FeatureToggleConfig {

    @Bean
    @Primary
    public RestTemplate featureToggleRestTemplate() {
        return new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(3))
            .additionalInterceptors(new LoggingClientHttpRequestInterceptor())
            .build();
    }
}
```

### Custom Async Executor

```java
@Configuration
public class AsyncConfig {

    @Bean
    @Primary
    public Executor featureToggleAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("feature-toggle-");
        executor.initialize();
        return executor;
    }
}
```

## Requirements

- Java 17 or higher
- Spring Boot 3.x
- FluxGate Edge Server running and accessible

## Optional Dependencies

- **Micrometer**: For metrics collection
- **Spring Boot Actuator**: For health indicators
- **Spring Retry**: For retry functionality (auto-configured)

## License

This project is licensed under the MIT License.

## Support

For issues and questions:
1. Check the [FluxGate documentation](docs/edge-server-api.md)
2. Review the health endpoint: `/actuator/health/featureToggle`
3. Enable debug logging: `logging.level.com.fluxgate.starter=DEBUG`