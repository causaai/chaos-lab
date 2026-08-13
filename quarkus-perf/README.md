# quarkus-perf

> **Quarkus Performance Benchmark** — a Quarkus / OpenJDK 21 application that simulates transactional HTTP workloads (banking + airline booking) with **tunable chaos knobs** for JVM heap pressure, connection-pool exhaustion, slow-query latency, and HTTP keep-alive misconfiguration. Exports **OpenTelemetry (OTEL) metrics and structured JSON logging**. Runs on both **OpenShift** and **kind** clusters.

---

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Chaos Knobs](#chaos-knobs)
4. [API Reference](#api-reference)
5. [Health Checks](#health-checks)
6. [Metrics (Prometheus / OTEL)](#metrics-prometheus--otel)
7. [Logging (Structured JSON / OTEL)](#logging-structured-json--otel)
8. [Building the Application](#building-the-application)
9. [Docker Build](#docker-build)
10. [Kubernetes Deployment — OpenShift](#kubernetes-deployment--openshift)
11. [Kubernetes Deployment — kind](#kubernetes-deployment--kind)
12. [Load Generator](#load-generator)
13. [Troubleshooting](#troubleshooting)
14. [Configuration Reference](#configuration-reference)

---

## Overview

`quarkus-perf` is a benchmark application that mimics a high-concurrency transactional backend — an online banking system combined with an airline reservation engine. Its primary purpose is to **demonstrate and diagnose memory-related and connection-pool scaling issues** that arise from poor HTTP/database configuration, even under low-to-moderate load.

The app deliberately ships with configurable "chaos knobs" so that operators and SRE teams can:

- Reproduce production-grade connection-pool exhaustion on demand
- Observe heap growth caused by an unbounded in-process response cache
- Simulate large HTTP responses under long keep-alive windows
- Tune server parameters to measure the remediation effect
- Observe everything via OTEL-compatible metrics and structured logs

---

## Architecture

```
                  ┌───────────────────────────────────────────────┐
 HTTP Clients ──► │  Quarkus (RESTEasy Reactive / Vert.x)         │
                  │  ┌────────────┐  ┌──────────────────────────┐ │
                  │  │ /api/      │  │ /q/health  /q/metrics     │ │
                  │  │ accounts   │  │ (SmallRye Health          │ │
                  │  │ bookings   │  │  + Micrometer/Prometheus  │ │
                  │  └─────┬──────┘  │  + OTEL exporter)         │ │
                  │        │         └──────────────────────────┘ │
                  │  ┌─────▼──────────────────────────────────┐   │
                  │  │  TransactionService / BookingService    │   │
                  │  │  (chaos memory-leak cache lives here)   │   │
                  │  └─────┬──────────────────────────────────┘   │
                  │        │  JDBC (Agroal pool)                   │
                  │  ┌─────▼──────────────────────────────────┐   │
                  │  │  AccountRepository / BookingRepository  │   │
                  │  │  (chaos connection-leak lives here)     │   │
                  │  └─────┬──────────────────────────────────┘   │
                  │        │                                       │
                  └────────┼───────────────────────────────────────┘
                           │  JDBC
                  ┌────────▼──────────────────────────────────┐
                  │  H2 (embedded, dev) / PostgreSQL (prod)   │
                  └───────────────────────────────────────────┘
```

### Package structure

```
src/main/java/ai/causa/quarkusperf/
├── chaos/            # ChaosConfig (ConfigMapping) + ResponsePaddingService
├── health/           # SmallRye Health checks (liveness, readiness, startup)
├── load/             # LoadGenerator — standalone load driver (K8s Job)
├── metrics/          # Custom Micrometer gauge registration
├── model/            # Domain objects: Account, Transaction, Booking, ApiResponse
├── repository/       # JDBC repositories (Agroal) with chaos instrumentation
├── rest/             # JAX-RS resources (AccountResource, BookingResource)
└── service/          # Business logic + chaos simulation
```

---

## Chaos Knobs

All chaos settings are **externalized as environment variables** (no code changes required).

### JVM / Heap knobs

| Environment Variable           | Default | Effect when `true` / non-zero                                      |
|--------------------------------|---------|--------------------------------------------------------------------|
| `CHAOS_MEMORY_CACHE_ENABLED`   | `false` | A static `ConcurrentHashMap` accumulates transaction responses and is **never evicted**. Heap grows linearly with request throughput. |
| `CHAOS_MEMORY_OBJECTS_PER_TX`  | `1`     | Number of 64 KB byte-arrays leaked per transaction when the cache is enabled. Increase to accelerate heap exhaustion. |

### HTTP knobs (Vert.x / Quarkus HTTP layer)

| Environment Variable                  | Default | Effect                                                            |
|---------------------------------------|---------|-------------------------------------------------------------------|
| `CHAOS_HTTP_LARGE_RESPONSE_ENABLED`   | `false` | Pads every HTTP response with Base64 data. Combined with a long idle timeout, live heap ≈ `concurrent_users × padding_kb × 2.33`. |
| `CHAOS_HTTP_LARGE_RESPONSE_KB`        | `256`   | Padding size per response in KB.                                  |
| `CHAOS_HTTP_IDLE_TIMEOUT_ENABLED`     | `false` | Signals the operator to extend Quarkus HTTP idle timeout (see `CHAOS_HTTP_IDLE_TIMEOUT_S`). |
| `CHAOS_HTTP_IDLE_TIMEOUT_S`           | `60`    | Idle timeout in seconds when the knob is enabled.                 |

### DB / Connection pool knobs

| Environment Variable           | Default | Effect                                                             |
|--------------------------------|---------|--------------------------------------------------------------------|
| `CHAOS_DB_LEAK_ENABLED`        | `false` | DB connections are acquired but **never returned** to the Agroal pool. Under sustained load the pool is exhausted and requests start failing or timing out. |
| `CHAOS_DB_SLOW_QUERY_MS`       | `0`     | Each JDBC call sleeps for this many milliseconds before executing. Forces threads to hold connections longer, amplifying starvation. |
| `DB_MAX_POOL_SIZE`             | `20`    | Maximum Agroal JDBC connections. Set to `5` or lower to trigger starvation even without leak mode. |
| `THREAD_POOL_MAX`              | `200`   | Quarkus thread pool upper bound. Reducing this reveals thread starvation. |

### Typical chaos scenarios

**Scenario 1 — Slow connection exhaustion (pool leak)**
```sh
CHAOS_DB_LEAK_ENABLED=true
DB_MAX_POOL_SIZE=10
CONCURRENT_USERS=20   # load generator
```
Expected: after ~10 requests the pool is empty; subsequent requests receive HTTP 500 / timeout. Readiness probe fails and Kubernetes stops routing traffic.

**Scenario 2 — Memory pressure from response cache**
```sh
CHAOS_MEMORY_CACHE_ENABLED=true
CHAOS_MEMORY_OBJECTS_PER_TX=5
```
Expected: heap grows at ~320 KB/request. JVM OOM-kills (configured with `-XX:+HeapDumpOnOutOfMemoryError`). Liveness probe reports DOWN before restart.

**Scenario 3 — HTTP large-response + keep-alive**
```sh
CHAOS_HTTP_LARGE_RESPONSE_ENABLED=true
CHAOS_HTTP_LARGE_RESPONSE_KB=256
CHAOS_HTTP_IDLE_TIMEOUT_ENABLED=true
CHAOS_HTTP_IDLE_TIMEOUT_S=60
```
Expected: live heap ≈ `concurrent_users × 256 KB × 2.33` held during serialisation. At 50 users ≈ 30 MB per cycle. Triggers GC pressure → eventual OOM.

**Scenario 4 — Slow backend amplifies thread starvation**
```sh
CHAOS_DB_SLOW_QUERY_MS=500
THREAD_POOL_MAX=50
```
Expected: with 500ms/query, threads pile up; with 50 threads the system stalls under moderate load.

---

## API Reference

All endpoints return `application/json` with the `ApiResponse<T>` envelope:
```json
{
  "success": true,
  "correlationId": "uuid",
  "processingTimeMs": 12,
  "data": { ... }
}
```

OpenAPI spec available at: `GET /openapi` or `GET /swagger-ui`

### Accounts & Transactions

| Method | Path                                              | Description                           |
|--------|---------------------------------------------------|---------------------------------------|
| `GET`  | `/api/accounts`                                   | List all accounts (up to 100)         |
| `GET`  | `/api/accounts/{accountId}`                       | Get account details                   |
| `GET`  | `/api/accounts/{accountId}/transactions?limit=20` | Transaction history                   |
| `POST` | `/api/accounts/{accountId}/transactions`          | Submit a transaction                  |

**Submit transaction body:**
```json
{
  "type": "DEBIT",
  "amount": 250.00,
  "currency": "USD",
  "description": "ATM withdrawal"
}
```

### Flight Bookings

| Method | Path                            | Description                      |
|--------|---------------------------------|----------------------------------|
| `POST` | `/api/bookings`                 | Create a booking                 |
| `GET`  | `/api/bookings/{bookingRef}`    | Retrieve booking by reference    |
| `GET`  | `/api/bookings?passengerId=PAX-0001` | List bookings for passenger |

**Create booking body:**
```json
{
  "passengerId": "PAX-0001",
  "passengerName": "Alice Johnson",
  "origin": "JFK",
  "destination": "LAX"
}
```

---

## Health Checks

Quarkus SmallRye Health endpoints:

| Endpoint            | Probe type | Fails when                                               |
|---------------------|------------|----------------------------------------------------------|
| `GET /q/health/live`    | Liveness   | Heap usage > 90% — Kubernetes restarts the pod       |
| `GET /q/health/ready`   | Readiness  | DB ping fails (pool exhausted) — traffic stops       |
| `GET /q/health/started` | Startup    | Always UP once Quarkus finishes startup              |
| `GET /q/health`         | Combined   | Aggregated status for all probes                     |

---

## Metrics (Prometheus / OTEL)

### Scrape endpoint

Prometheus text format at `GET /q/metrics`.

OTEL metrics are exported to the configured collector endpoint via the Micrometer OTEL bridge (set `OTEL_EXPORTER_OTLP_ENDPOINT`).

### Key custom metrics

| Metric                                    | Type    | Description                            |
|-------------------------------------------|---------|----------------------------------------|
| `quarkus_perf_heap_used_bytes`            | Gauge   | Current JVM heap usage                 |
| `quarkus_perf_heap_max_bytes`             | Gauge   | Configured JVM max heap                |
| `quarkus_perf_heap_used_ratio`            | Gauge   | Heap utilisation (0–1)                 |
| `quarkus_perf_leak_cache_entries`         | Gauge   | Leaked objects in memory cache         |
| `quarkus_perf_leak_cache_bytes`           | Gauge   | Estimated bytes held by leak cache     |
| `quarkus_perf_http_padding_total_bytes`   | Gauge   | Cumulative bytes padded into responses |
| `quarkus_perf_http_padding_enabled`       | Gauge   | 1.0 if HTTP chaos knob is active       |
| `quarkus_perf_transactions_submitted_total` | Counter | Total transactions submitted          |
| `quarkus_perf_bookings_created_total`     | Counter | Total bookings created                 |
| `quarkus_perf_transaction_process_*`      | Timer   | Transaction processing latency         |
| `quarkus_perf_booking_create_*`           | Timer   | Booking creation latency               |
| `quarkus_perf_account_lookup_*`           | Timer   | Account lookup latency                 |
| `agroal_acquired_count`                   | Gauge   | Active Agroal connections (built-in)   |

---

## Logging (Structured JSON / OTEL)

All logs are emitted in **structured JSON format** via `quarkus-logging-json`:

```json
{
  "timestamp": "2024-06-10T12:34:56.789Z",
  "level": "WARN",
  "loggerName": "ai.causa.quarkusperf.repository.AccountRepository",
  "message": "[CHAOS] Connection acquired and intentionally NOT returned to Agroal pool",
  "traceId": "abc123...",
  "spanId": "def456..."
}
```

`traceId` and `spanId` are automatically injected by the Quarkus OTEL integration when a trace context is active. Log shipping to any aggregator (Loki, OpenSearch, Elastic) can use these fields for trace-log correlation.

---

## Building the Application

### Prerequisites
- Java 21+ (Eclipse Temurin or OpenJDK)
- Maven 3.9+

```bash
cd quarkus-perf

# Build (fast-jar)
mvn clean package -DskipTests

# Run in dev mode (hot reload, H2 embedded)
./run-standalone.sh dev

# Test health
curl http://localhost:8080/q/health
curl http://localhost:8080/api/accounts

# Test a transaction
curl -X POST http://localhost:8080/api/accounts/ACC-001/transactions \
  -H 'Content-Type: application/json' \
  -d '{"type":"CREDIT","amount":100,"currency":"USD","description":"Test"}'

# Run with chaos knobs active
./run-standalone.sh test
```

---

## Docker Build

```bash
cd quarkus-perf

# Build image using UBI OpenJDK 21 from Red Hat registry
docker build -t quarkus-perf:1.0.0 .

# Run locally
docker run -p 8080:8080 \
  -e CHAOS_DB_LEAK_ENABLED=false \
  -e CHAOS_MEMORY_CACHE_ENABLED=false \
  quarkus-perf:1.0.0
```

---

## Kubernetes Deployment — OpenShift

```bash
# Apply all manifests (namespace, configmap, deployment, service, route, hpa)
kubectl apply -f manifests/deploy.yaml

# The OpenShift Route is included in deploy.yaml for TLS-terminated external access.
# Verify 3 replicas are running
kubectl get pods -n chaos-test -l app=quarkus-perf

# Check application health
kubectl port-forward svc/quarkus-perf-svc 8080:8080 -n chaos-test &
curl http://localhost:8080/q/health

# Apply monitoring (requires prometheus-operator)
kubectl apply -f manifests/monitoring.yaml

# Run the load generator
kubectl apply -f manifests/load-gen-job.yaml

# Follow load generator logs
kubectl logs -n chaos-test -l app=quarkus-perf-load-gen -f
```

### Activating chaos scenarios via ConfigMap patch

```bash
# Enable connection leak chaos
kubectl patch configmap quarkus-perf-config -n chaos-test \
  --type merge -p '{"data":{"CHAOS_DB_LEAK_ENABLED":"true","DB_MAX_POOL_SIZE":"5"}}'

# Rolling restart to pick up new config
kubectl rollout restart deployment/quarkus-perf -n chaos-test

# Watch pods and readiness
kubectl get pods -n chaos-test -w

# Restore normal operation
kubectl patch configmap quarkus-perf-config -n chaos-test \
  --type merge -p '{"data":{"CHAOS_DB_LEAK_ENABLED":"false","DB_MAX_POOL_SIZE":"20"}}'
kubectl rollout restart deployment/quarkus-perf -n chaos-test
```

---

## Kubernetes Deployment — kind

```bash
# 1. Create a kind cluster with port mapping
cat <<'EOF' > /tmp/kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 80
        hostPort: 8080
        protocol: TCP
EOF
kind create cluster --config /tmp/kind-config.yaml

# 2. Install nginx ingress controller (required for Ingress in deploy.yaml)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s

# 3. Load the image into kind (no registry push required)
kind load docker-image quarkus-perf:1.0.0

# 4. Apply manifests — the Route resource will produce a "no matches for kind"
#    warning on kind (it's OpenShift-specific); the Ingress takes over instead.
kubectl apply -f manifests/deploy.yaml

# 5. Verify
kubectl get pods -n chaos-test -l app=quarkus-perf

# 6. Access via Ingress (add quarkus-perf.local → 127.0.0.1 to /etc/hosts)
echo "127.0.0.1 quarkus-perf.local" | sudo tee -a /etc/hosts
curl http://quarkus-perf.local/q/health

# 7. Or use port-forward
kubectl port-forward svc/quarkus-perf-svc 8080:8080 -n chaos-test &
curl http://localhost:8080/q/health
```

---

## Load Generator

The load generator is a self-contained Java application ([`LoadGenerator.java`](src/main/java/ai/causa/quarkusperf/load/LoadGenerator.java)) that runs as a Kubernetes Job using the same container image.

### Environment variables

| Variable           | Default              | Description                                            |
|--------------------|----------------------|--------------------------------------------------------|
| `TARGET_HOST`      | `quarkus-perf-svc`   | Kubernetes service hostname                            |
| `TARGET_PORT`      | `8080`               | HTTP port                                              |
| `CONCURRENT_USERS` | `20`                 | Parallel virtual threads                               |
| `DURATION_SECONDS` | `300`                | Total run duration                                     |
| `REQUEST_DELAY_MS` | `100`                | Pause between requests per thread (0 = no delay)       |
| `BOOKING_RATIO`    | `50`                 | % of requests that are booking (rest are transactions) |

---

## Troubleshooting

### Connection pool exhaustion

**Symptoms:** HTTP 500 errors, readiness probe DOWN, Agroal timeout errors in logs.

```bash
# Check chaos log lines
kubectl logs deployment/quarkus-perf -n chaos-test | grep '\[CHAOS\]'

# Check pool utilisation via Prometheus metrics
kubectl port-forward svc/quarkus-perf-svc 8080:8080 -n chaos-test &
curl http://localhost:8080/q/metrics | grep agroal
```

**Resolution:** Set `DB_MAX_POOL_SIZE` to a higher value, or set `CHAOS_DB_LEAK_ENABLED=false` and restart the deployment.

### Memory pressure / OOM

**Symptoms:** Liveness probe DOWN (`heap.used.pct > 90%`), pod restarting, heap dump in `/dumps/`.

```bash
# Check heap metrics
curl http://localhost:8080/q/metrics | grep quarkus_perf_heap

# Check liveness data
curl http://localhost:8080/q/health/live | jq .

# Copy heap dump from pod
kubectl cp chaos-test/<pod>:/dumps/heapdump.hprof ./heapdump.hprof
```

**Resolution:** Set `CHAOS_MEMORY_CACHE_ENABLED=false` and restart; or increase pod memory limit.

### Performance degradation (slow queries / thread starvation)

```bash
# Check slow query chaos setting
kubectl get configmap quarkus-perf-config -n chaos-test -o yaml | grep SLOW

# Disable
kubectl patch configmap quarkus-perf-config -n chaos-test \
  --type merge -p '{"data":{"CHAOS_DB_SLOW_QUERY_MS":"0"}}'
kubectl rollout restart deployment/quarkus-perf -n chaos-test
```

---

## Configuration Reference

All Quarkus settings are configured via **environment variables** that override defaults in [`application.properties`](src/main/resources/application.properties).

| Environment Variable                   | Default                   | Description                                      |
|----------------------------------------|---------------------------|--------------------------------------------------|
| `QUARKUS_HTTP_PORT`                    | `8080`                    | HTTP listen port                                 |
| `THREAD_POOL_CORE`                     | `10`                      | Quarkus thread pool core threads                 |
| `THREAD_POOL_MAX`                      | `200`                     | Quarkus thread pool max threads                  |
| `DB_MAX_POOL_SIZE`                     | `20`                      | Agroal JDBC connection pool max                  |
| `CHAOS_DB_LEAK_ENABLED`                | `false`                   | Enable connection-leak chaos mode                |
| `CHAOS_DB_SLOW_QUERY_MS`               | `0`                       | Per-query artificial delay in ms                 |
| `CHAOS_MEMORY_CACHE_ENABLED`           | `false`                   | Enable unbounded response-cache memory leak      |
| `CHAOS_MEMORY_OBJECTS_PER_TX`          | `1`                       | 64 KB objects leaked per transaction             |
| `CHAOS_HTTP_LARGE_RESPONSE_ENABLED`    | `false`                   | Enable HTTP large-response padding               |
| `CHAOS_HTTP_LARGE_RESPONSE_KB`         | `256`                     | Padding size per response (KB)                   |
| `CHAOS_HTTP_IDLE_TIMEOUT_ENABLED`      | `false`                   | Extend Vert.x HTTP idle timeout (amplifies heap) |
| `CHAOS_HTTP_IDLE_TIMEOUT_S`            | `60`                      | Idle timeout in seconds when knob is enabled     |
| `OTEL_EXPORTER_OTLP_ENDPOINT`          | `http://localhost:4317`   | OTEL collector gRPC endpoint                     |
| `OTEL_SDK_DISABLED`                    | `false`                   | Set `true` to disable OTEL export entirely       |
| `OTEL_TRACES_SAMPLER_ARG`              | `1.0`                     | Trace sampling rate (0.0–1.0)                    |

---

*Made with IBM Bob*
