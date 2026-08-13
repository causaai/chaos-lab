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

| App | Runtime | Health endpoint | Chaos scenarios | Docs |
|---|---|---|---|---|
| [**quarkus-perf**](quarkus-perf/) | Quarkus 3.x · Agroal · H2 | `/q/health` | DB connection leak, JVM heap/memory cache leak, HTTP large-response + idle-timeout pressure, slow query + thread starvation | [README →](quarkus-perf/README.md) |
| [**liberty-perf**](liberty-perf/) | Open Liberty 24 · WLP ConnectionPool · H2 | `/health` | DB connection leak, JVM heap/memory cache leak, slow query + thread starvation | [README →](liberty-perf/README.md) |

See the per-app READMEs for chaos knob references, build instructions, deployment manifests, load generator usage, and troubleshooting guides.

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

### quarkus-perf — quick start

```bash
cd quarkus-perf
./run-standalone.sh dev          # local dev mode (H2 embedded, hot reload)
```

See [quarkus-perf/README.md](quarkus-perf/README.md) for Docker build, kind and OpenShift deployment, chaos activation, metrics, and load generator instructions.

### liberty-perf — quick start

```bash
cd liberty-perf
mvn liberty:run                  # local dev mode (H2 embedded)
# Health: http://localhost:9080/health
# API:    http://localhost:9080/api/accounts
```

See [liberty-perf/README.md](liberty-perf/README.md) for Docker build, kind and OpenShift deployment, chaos activation, metrics, and load generator instructions.

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
