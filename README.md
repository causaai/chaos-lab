# Chaos Lab

Intentionally crash-prone Java applications for testing failure scenarios in Kubernetes environments.

## Overview

This repository provides production-realistic failure simulators built with Quarkus. Each application is designed to trigger specific failure modes—helping you validate monitoring, alerting, and auto-remediation systems before real incidents occur.

## Available Scenarios

| Scenario | Failure Type                                                   | Use Case | Details |
|----------|----------------------------------------------------------------|----------|---------|
| [**heap-oom**](heap-oom/) | JVM Heap Exhaustion                                            | Test heap monitoring and OOMKilled alerts | Configurable policies: request-bound, time-bound, realistic |
| [**heap-oom-prom**](heap-oom-prom/) | Metric Cardinality Explosion - Which results in Heap Exhausion | Test heap monitoring and OOMKilled alerts | Simulates unbounded Prometheus target discovery |
| [**system-oom**](system-oom/) | Container Memory Limit breach                                  | Test cgroup OOM handling and pod eviction | Native memory allocation beyond container limits |
| [**perf-impact**](perf-impact/) | GC-Induced Latency                                             | Measure GC impact on throughput and SLOs | Phase-based allocation patterns with observable degradation |

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker (for containerized deployments)
- Kubernetes cluster (optional, for realistic testing)

### Build & Run Locally

```bash
cd heap-oom
./mvnw quarkus:dev
```

### Build Container Image

```bash
./mvnw package
docker build -f src/main/docker/Dockerfile.jvm -t chaos-lab/heap-oom:latest .
```

### Deploy to Kubernetes

```bash
kubectl apply -f heap-oom/manifests/deploy.yaml
```

## Configuration

All scenarios support environment-based configuration:

```bash
# Example: heap-oom with request-bound policy
java -jar target/quarkus-app/quarkus-run.jar \
  -Dcrash.oom-policy=request \
  -Dcrash.req.total=100
```

See individual project READMEs for detailed configuration options.

## Common Use Cases

- **Validate Monitoring**: Ensure your observability stack detects memory issues before they cause outages
- **Test Auto-Remediation**: Verify that pod restarts, horizontal scaling, or circuit breakers activate correctly
- **Chaos Engineering**: Inject controlled failures to build confidence in system resilience
- **Performance Baselines**: Measure application behavior under GC pressure for capacity planning

## Architecture

Each application is a self-contained Quarkus service with:
- REST endpoints for triggering/monitoring failures
- Configurable failure policies
- Prometheus metrics (where applicable)
- Kubernetes manifests for easy deployment

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
