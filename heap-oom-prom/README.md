A Quarkus-based application that simulates a Prometheus-like monitoring system experiencing unbounded metric cardinality explosion, leading to heap exhaustion. Designed to test monitoring system limits, cardinality alerts, and memory management in metric collection pipelines.

## What It Does

This application mimics the behavior of a Prometheus server that discovers and scrapes container metrics from a Kubernetes cluster. It simulates a **cardinality explosion scenario** where the number of unique metric time series grows uncontrollably, eventually exhausting heap memory.

**Real-world scenarios this simulates:**
- Misconfigured service discovery creating thousands of targets
- High-cardinality labels (pod names, container IDs) in dynamic environments
- Unbounded metric retention without proper limits
- Memory leaks in monitoring systems due to metric accumulation

## How It Works

The application runs three concurrent schedulers that simulate Prometheus operations:

### 1. Discovery Scheduler (Every 1 second)
Discovers new container targets to scrape:
- **Phase 1 (0-60s)**: Adds 50 targets/second (controlled growth)
- **Phase 2 (60s+)**: Adds 5,000 targets/second (explosion phase)

Each target represents a unique combination of:
- Namespace (2,000 possible values)
- Deployment (UUID-based, unlimited)
- Pod (UUID-based, unlimited)
- Container (10 possible values)

### 2. Scrape Scheduler (Every 5 seconds)
Collects metrics from all discovered targets:
- Scrapes 5 metric samples per target
- Each sample includes labels (namespace, pod, container)
- Samples accumulate in memory (never cleaned up)

### 3. Writer Scheduler (Every 5 seconds)
Simulates persisting metrics to disk:
- Writes all targets and samples to `/tmp/metrics.json`
- Simulates I/O slowness with 1ms delay per entry
- Logs heap usage after each write

**The Memory Leak:** Metrics are continuously added but never removed, causing unbounded heap growth until OOM.

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+

### Run Locally (Watch It Crash)

```bash
cd heap-oom-prom
./mvnw quarkus:dev
```

**What you'll see:**
```
INFO  [DiscoveryScheduler] Inserted 1000 targets. Current registry size=1000
INFO  [ScrapeScheduler] Scrape started. targets=1000
INFO  [WriterScheduler] Writer completed. wrote=1000 entries
INFO  [WriterScheduler] Heap used=245 MB
...
INFO  [DiscoveryScheduler] Inserted 50000 targets. Current registry size=50000
INFO  [WriterScheduler] Heap used=1024 MB
Exception in thread "executor-thread-1" java.lang.OutOfMemoryError: Java heap space
```

The application will crash after approximately 60-90 seconds as cardinality explodes.

## Configuration

Currently, the application has minimal configuration. All behavior is hardcoded in the schedulers:

| Setting | Value | Location |
|---------|-------|----------|
| Discovery rate (0-60s) | 50 targets/sec | `DiscoveryScheduler.java:32` |
| Discovery rate (60s+) | 5000 targets/sec | `DiscoveryScheduler.java:34` |
| Scrape interval | 5 seconds | `ScrapeScheduler.java:24` |
| Samples per target | 5 | `ScrapeScheduler.java:32` |
| Write interval | 5 seconds | `WriterScheduler.java:28` |

### Adjusting Heap Size

Control how quickly the application crashes by adjusting heap size:

```bash
# Crash faster (smaller heap)
java -Xms256m -Xmx256m -jar target/quarkus-app/quarkus-run.jar

# Crash slower (larger heap)
java -Xms2g -Xmx2g -jar target/quarkus-app/quarkus-run.jar
```

## Hands-On Examples

### Example 1: Observe Cardinality Growth

```bash
# Start with default heap
./mvnw quarkus:dev
```

Watch the logs to see the progression:

```
# First minute (controlled growth)
[DiscoveryScheduler] Inserted 1000 targets. Current registry size=1000
[WriterScheduler] Heap used=128 MB

# After 60 seconds (explosion begins)
[DiscoveryScheduler] Inserted 10000 targets. Current registry size=10000
[WriterScheduler] Heap used=512 MB

# Shortly after (OOM imminent)
[DiscoveryScheduler] Inserted 50000 targets. Current registry size=50000
[WriterScheduler] Heap used=1536 MB
java.lang.OutOfMemoryError: Java heap space
```

### Example 2: Monitor Memory Growth

```bash
# Run with JVM monitoring flags
java -Xms512m -Xmx512m \
  -Xlog:gc*:file=gc.log \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/tmp/heap-oom-prom.hprof \
  -jar target/quarkus-app/quarkus-run.jar
```

This will:
- Log all GC activity to `gc.log`
- Create a heap dump at `/tmp/heap-oom-prom.hprof` when OOM occurs
- Allow post-mortem analysis with tools like Eclipse MAT

### Example 3: Delay the Crash

```bash
# Use larger heap to observe behavior longer
java -Xms4g -Xmx4g -jar target/quarkus-app/quarkus-run.jar
```

With 4GB heap, the application will run for several minutes before crashing, allowing you to:
- Observe the full cardinality explosion
- Test monitoring alerts at different thresholds
- Analyze GC behavior under pressure

## Kubernetes Deployment

### Deploy to Cluster

```bash
kubectl apply -f manifests/deploy.yaml
```

### Monitor the Crash

```bash
# Watch pod status
kubectl get pods -w

# Stream logs
kubectl logs -f deployment/heap-oom-prom

# Check for OOMKilled status
kubectl describe pod <pod-name>
```

**Expected behavior:**
- Pod runs normally for ~60 seconds
- Heap usage grows rapidly after 60 seconds
- Pod crashes with `OOMKilled` status
- Kubernetes restarts the pod (cycle repeats)

### Configure Memory Limits

Edit `manifests/deploy.yaml` to test different memory configurations:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: heap-oom-prom
spec:
  template:
    spec:
      containers:
      - name: heap-oom-prom
        image: your-registry/heap-oom-prom:latest
        env:
        - name: JAVA_OPTS
          value: "-Xms512m -Xmx512m"
        resources:
          limits:
            memory: "1Gi"
          requests:
            memory: "512Mi"
```

## Observability

### Key Metrics to Monitor

Monitor these metrics to detect cardinality explosion:

```text
# Heap usage
(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100

# GC pressure
rate(jvm_gc_pause_seconds_sum[1m])
```

### The Math Behind the Explosion

```
Phase 1 (0-60s):
- 50 targets/sec × 60 sec = 3,000 targets
- 3,000 targets × 5 samples = 15,000 metric samples
- Heap usage: ~200-300 MB

Phase 2 (60-90s):
- 5,000 targets/sec × 30 sec = 150,000 new targets
- Total: 153,000 targets × 5 samples = 765,000 metric samples
- Heap usage: 1-2 GB (OOM with default heap)
```

### Memory Breakdown

Each `TargetSamples` object contains:
- 1 `ContainerTarget` (4 strings: namespace, deployment, pod, container)
- List of `MetricSample` objects (5 per scrape, growing every 5 seconds)
- Each `MetricSample` has: metric name, value, timestamp, labels map

With 150,000 targets and continuous scraping, memory consumption grows exponentially.

## Troubleshooting

### Application Crashes Before 60 Seconds

**Cause:** Heap size too small for initial phase.

**Solution:** Increase heap size:

```bash
java -Xms1g -Xmx1g -jar target/quarkus-app/quarkus-run.jar
```

### Want to Observe Longer Without Crashing

**Cause:** Need to study behavior without OOM.

**Solution:** Use very large heap:

```bash
java -Xms8g -Xmx8g -jar target/quarkus-app/quarkus-run.jar
```

### Heap Dump Analysis

After OOM, analyze the heap dump:

```bash
# Using Eclipse MAT
mat /tmp/heap-oom-prom.hprof

# Using jhat (built-in)
jhat -J-Xmx4g /tmp/heap-oom-prom.hprof
```

Look for:
- Large `ConcurrentHashMap` (the Registry)
- Thousands of `TargetSamples` objects
- Growing `ArrayList` of `MetricSample` objects

## Building the Application

### Package the Application

```bash
./mvnw package
```

This creates `target/quarkus-app/quarkus-run.jar` which can be run with:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

### Build Docker Image

```bash
./mvnw package
docker build -f Dockerfile -t heap-oom-prom:latest .
```

Run the container:

```bash
docker run -p 8080:8080 \
  -e JAVA_OPTS="-Xmx512m" \
  heap-oom-prom:latest
```


## License

Licensed under the Apache License 2.0. See [LICENSE](../LICENSE) for details.
