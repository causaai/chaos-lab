# Chaos Lab

Intentionally crash-prone Java applications for testing failure scenarios in Kubernetes environments.

## Overview

This repository provides production-realistic failure simulators built on Quarkus and Open Liberty. Each application is designed to trigger specific failure modes—helping you validate monitoring, alerting, and auto-remediation systems before real incidents occur.

## Available Scenarios

### Single-failure simulators

| Scenario | Failure Type | Use Case |
|---|---|---|
| [**heap-oom**](heap-oom/) | JVM heap exhaustion | Test heap monitoring and OOMKilled alerts. Configurable policies: request-bound, time-bound, realistic. |
| [**heap-oom-prom**](heap-oom-prom/) | Metric cardinality explosion → heap exhaustion | Test monitoring system limits; simulates unbounded Prometheus target discovery. |
| [**system-oom**](system-oom/) | Container memory limit breach (native memory) | Test cgroup OOM handling and pod eviction; allocates off-heap memory beyond container limits. |
| [**perf-impact**](perf-impact/) | GC-induced latency | Measure GC impact on throughput and SLOs; phase-based allocation patterns with observable degradation. |

### Multi-scenario benchmark apps

Both applications simulate a banking + airline-booking transactional backend and expose **tunable chaos knobs** as environment variables. All knobs are externalised to a ConfigMap — no image rebuilds required.

| App | Runtime | Health endpoint | Chaos scenarios |
|---|---|---|---|
| [**quarkus-perf**](quarkus-perf/) | Quarkus 3.x · Agroal · H2 | `/q/health` | DB connection leak, JVM heap/memory cache leak, HTTP large-response + idle-timeout pressure, slow query + thread starvation |
| [**liberty-perf**](liberty-perf/) | Open Liberty 24 · WLP ConnectionPool · H2 | `/health` | DB connection leak, JVM heap/memory cache leak, slow query + thread starvation |

#### Chaos knobs — shared (both apps)

| Environment Variable | Default | Effect |
|---|---|---|
| `CHAOS_DB_LEAK_ENABLED` | `false` | Connections acquired but never returned → pool exhaustion → HTTP 500 |
| `CHAOS_DB_SLOW_QUERY_MS` | `0` | Artificial per-query sleep in ms → threads hold connections longer → starvation |
| `CHAOS_MEMORY_CACHE_ENABLED` | `false` | Static `ConcurrentHashMap` never evicted → heap grows linearly with load |
| `CHAOS_MEMORY_OBJECTS_PER_TX` | `1` | 64 KB objects leaked per transaction (acceleration knob) |
| `CHAOS_HTTP_LARGE_RESPONSE_ENABLED` | `false` | Every response padded with a Base64 blob buffered in heap per connection |
| `CHAOS_HTTP_LARGE_RESPONSE_KB` | `256` | Size of padding per response in KB |
| `DB_MAX_POOL_SIZE` | `20` | Lower to trigger connection starvation without enabling the leak |
| `THREAD_POOL_MAX` | `200` | Lower to expose thread exhaustion under slow queries |

#### Chaos knobs — quarkus-perf only

| Environment Variable | Default | Effect |
|---|---|---|
| `CHAOS_HTTP_IDLE_TIMEOUT_ENABLED` | `false` | Extends Vert.x HTTP idle timeout; combined with large-response padding amplifies live heap |
| `CHAOS_HTTP_IDLE_TIMEOUT_S` | `60` | Idle timeout in seconds when the knob is enabled |

#### Connection pool knobs — liberty-perf only

| Environment Variable | Default | Effect |
|---|---|---|
| `DB_MIN_POOL_SIZE` | `2` | Minimum JDBC connections held open (eager creation) |
| `DB_CONNECTION_TIMEOUT_MS` | `5000` | Maximum wait (ms) for a free connection before HTTP 500 |
| `DB_MAX_IDLE_TIME_S` | `60` | Close idle connections after N seconds |
| `HTTP_MAX_KEEPALIVE_REQUESTS` | `200` | Requests per keep-alive connection |
| `HTTP_PERSIST_TIMEOUT_S` | `30` | Keep-alive socket timeout |

## Quick Start

### Prerequisites

- Java 21+ (IBM Semeru or Eclipse Temurin recommended)
- Maven 3.9+
- Docker or Podman (for containerised deployments)
- Kubernetes or kind cluster (for realistic testing)

> **heap-oom**, **heap-oom-prom**, **system-oom**, and **perf-impact** target Java 17+ and Quarkus dev mode.  
> **quarkus-perf** and **liberty-perf** require Java 21 and are deployed as container images.

### Single-failure simulators — run locally

```bash
cd heap-oom
./mvnw quarkus:dev
```

### quarkus-perf — build and deploy to kind

```bash
cd quarkus-perf

# Build image
podman build -t quay.io/<your-registry>/quarkus-perf:1.0.0 .

# Load into kind
podman save quay.io/<your-registry>/quarkus-perf:1.0.0 | \
  kind load image-archive /dev/stdin --name <cluster>

# Deploy (kind-specific manifest: 1 replica, NodePort 30080, OTEL disabled)
kubectl apply -f manifests/deploy-kind.yaml

# Verify
kubectl exec -n chaos-test deploy/quarkus-perf -- \
  curl -sf http://localhost:8080/q/health | python3 -m json.tool
```

### liberty-perf — run locally

```bash
cd liberty-perf
mvn liberty:run
# Health: http://localhost:9080/health
# API:    http://localhost:9080/api/accounts
```

### liberty-perf — build and deploy to kind

```bash
cd liberty-perf

# Build WAR + Liberty server ZIP
mvn clean package

# Build image
podman build -t quay.io/<your-registry>/liberty-perf:1.0.0 .

# Load into kind and deploy
podman save quay.io/<your-registry>/liberty-perf:1.0.0 | \
  kind load image-archive /dev/stdin --name <cluster>
kubectl apply -f manifests/deploy-kind.yaml

# Verify
kubectl exec -n chaos-test deploy/liberty-perf -- \
  curl -sf http://localhost:9080/health | python3 -m json.tool
```

### liberty-perf — deploy to OpenShift

```bash
cd liberty-perf

# Apply all manifests (namespace, configmap, deployment, service, route, HPA, monitoring)
kubectl apply -f manifests/deploy.yaml

# Run the load generator
kubectl apply -f manifests/load-gen-job.yaml
```

### Activating a chaos scenario

```bash
# Example: enable DB connection leak on quarkus-perf
kubectl patch configmap quarkus-perf-config -n chaos-test \
  --type merge -p '{"data":{"CHAOS_DB_LEAK_ENABLED":"true","DB_MAX_POOL_SIZE":"5"}}'
kubectl rollout restart deployment/quarkus-perf -n chaos-test

# Restore clean baseline
kubectl patch configmap quarkus-perf-config -n chaos-test \
  --type merge -p '{"data":{"CHAOS_DB_LEAK_ENABLED":"false","DB_MAX_POOL_SIZE":"20"}}'
kubectl rollout restart deployment/quarkus-perf -n chaos-test
```

See each app's README for the full scenario catalogue and expected log/probe behaviour.

## Common Use Cases

- **Validate monitoring**: ensure your observability stack detects memory and connection issues before they cause outages
- **Test auto-remediation**: verify that pod restarts, horizontal scaling, or circuit breakers activate correctly
- **Chaos engineering**: inject controlled failures to build confidence in system resilience
- **Performance baselines**: measure application behaviour under GC pressure, thread starvation, or connection exhaustion for capacity planning
- **Runtime comparison**: run the same chaos scenarios against Quarkus and Open Liberty side-by-side to compare failure signatures

## Repository Structure

```
chaos-lab/
├── heap-oom/          # JVM heap exhaustion simulator (Quarkus)
├── heap-oom-prom/     # Prometheus cardinality explosion → heap OOM (Quarkus)
├── system-oom/        # Native memory / cgroup OOM simulator (Quarkus)
├── perf-impact/       # GC-pressure latency simulator (Quarkus)
├── quarkus-perf/      # Multi-scenario benchmark — Quarkus runtime
└── liberty-perf/      # Multi-scenario benchmark — Open Liberty runtime
```

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
