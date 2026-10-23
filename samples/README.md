# FluxGate Java SDK samples

Two small apps that evaluate flags against a FluxGate stack running on your machine, and a Docker
Compose file that starts that stack.

| Sample | Shows |
|---|---|
| [java-sample](java-sample/) | `fluxgate-java` without Spring: `FluxGateClient` and the OpenFeature provider. Prints one evaluation per sample user. |
| [spring-boot-sample](spring-boot-sample/) | `fluxgate-spring-boot-starter`: an injected `FluxGateClient`, `@FeatureToggle` with a fallback method, and the Actuator health indicator. |

You need Docker, Java 17+, Maven, `curl`, `jq` and `openssl`. Run the commands from the
repository root.

## 1. Start a local FluxGate stack

[docker-compose.yml](docker-compose.yml) runs Postgres, the backend, the admin UI and the edge
server on their own ports and volume, so it does not clash with another FluxGate install:

| Service | Address |
|---|---|
| Admin UI | http://localhost:18000 |
| Backend REST API | http://localhost:18080/api/v1 (gRPC on 15051) |
| Edge server | http://localhost:18081 (the samples talk to this) |

```bash
docker compose -f samples/docker-compose.yml up -d
```

On Apple Silicon, use the native images instead of x86 emulation:

```bash
printf 'FLUXGATE_BACKEND_TAG=v1.2.2-arm64\nFLUXGATE_EDGE_TAG=v1.2.2-arm64\n' >> samples/.env
docker compose -f samples/docker-compose.yml up -d
```

## 2. Create the sample data

[setup.sh](setup.sh) sets up the fresh stack through the backend REST API:

- the first admin `sample-admin`, the team **Samples** and the environment **Development**,
- the context keys `country` and `user_tier`,
- the Backend client `java-sdk-samples`,
- an approval policy and a second user `sample-approver`, because a deployment needs an approver
  other than the requester,
- two flags, with their Development stage requested, approved and deployed:

| Flag | Type | Development rules |
|---|---|---|
| `new-checkout` | Simple (boolean) | `true` when `country` equals `CA`, otherwise off. |
| `checkout-theme` | Contextual, string variants `classic` and `express` | `express` when `country` equals `CA` and `user_tier` equals `plus`; everyone else gets an 80/20 split of `classic` and `express`. |

```bash
samples/setup.sh
```

The script writes the client's SDK key (`FLUXGATE_SDK_KEY`) and the sample users' passwords to
`samples/.env`. The file is git-ignored and never printed. Sign in to the admin UI as
`sample-admin` with the password from that file to look at the flags and change their rules.

## 3. Start the edge server

The edge uses the same client, read from `FLUXGATE_SDK_KEY`, as its own identity:

```bash
docker compose -f samples/docker-compose.yml --profile edge up -d edge
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:18081/health
```

`200` means the edge is connected to the backend.

## 4. Run the samples

Build the SDK once:

```bash
mvn -q install -DskipTests
```

Both samples read their settings from environment variables:

| Variable | Default | Meaning |
|---|---|---|
| `FLUXGATE_BASE_URL` | `http://localhost:18081` | Edge server URL. |
| `FLUXGATE_SDK_KEY` | (required) | SDK key of a FluxGate client, `<clientId>.<apiKey>`. |
| `FLUXGATE_FLAG` | `new-checkout` | Flag the Java sample evaluates. |

### Plain Java

```bash
set -a; source samples/.env; set +a
mvn -q -f samples/java-sample exec:java
FLUXGATE_FLAG=checkout-theme mvn -q -f samples/java-sample exec:java
```

```text
FluxGateClient, flag 'checkout-theme':
  user-1  CA/plus -> value=express variant=express reason=TARGETING_MATCH
  user-2  US/free -> value=classic variant=classic reason=TARGETING_MATCH
  ...
```

For `new-checkout`, users outside Canada get `false` with the reason `UNKNOWN`: no rule matched.
Through OpenFeature, such an "off" answer returns the default value you pass in.

### Spring Boot

Runs on port 8090:

```bash
set -a; source samples/.env; set +a
mvn -q -f samples/spring-boot-sample spring-boot:run
```

```bash
curl -s 'http://localhost:8090/flags/checkout-theme?user=user-1&country=CA&tier=plus'
curl -s 'http://localhost:8090/checkout?user=user-5&country=CA'
curl -s http://localhost:8090/actuator/health
```

- `/flags/{key}` returns the value, variant and reason of any flag for the given user, `country`
  and `tier`.
- `/checkout` calls a method annotated with `@FeatureToggle("new-checkout")`. It answers
  `new checkout` for Canadian users and `classic checkout`, from the fallback method, for everyone
  else.
- `/actuator/health` includes a `fluxgate` component that reports whether the edge is reachable.

## Assignments are sticky

The edge remembers the variant it first served to a targeting key. If you change a flag's rules,
a user who already got an answer can keep it. Use new user IDs to see the new rules.

## Start over

```bash
docker compose -f samples/docker-compose.yml --profile edge down -v
rm samples/.env
```

## Troubleshooting

| Symptom | Cause |
|---|---|
| `error=UNAUTHORIZED` or `401` | Wrong `FLUXGATE_SDK_KEY`, or the client was deleted. |
| `error=FLAG_NOT_FOUND` | No flag with that key in the client's team. |
| `reason=DISABLED` for everyone | The flag's Development stage is not deployed. |
| `healthy: false` | The edge is not running on `FLUXGATE_BASE_URL`. Check `docker compose -f samples/docker-compose.yml logs edge`. |
| `setup.sh` fails at `POST /admins` | The stack is not fresh. Start over (see above). |
