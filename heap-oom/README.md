A Quarkus-based application that simulates JVM heap exhaustion through controlled memory allocation. Designed to test heap monitoring, OOMKilled alerts, and auto-remediation systems in Kubernetes environments.

## What It Does

This application progressively allocates heap memory until it triggers an `OutOfMemoryError`, causing the JVM to crash. Unlike random crashes, heap-oom provides **predictable, configurable failure modes** that let you validate your observability stack under controlled conditions.

**Real-world scenarios this simulates:**
- Memory leaks in production applications
- Unbounded cache growth

## How It Works

The application exposes two REST endpoints:

- `GET /alloc/hit` - Allocates memory based on the configured policy
- `GET /alloc/status` - Returns current memory usage and allocation state

Memory is allocated in ~1MB chunks and retained in a list to prevent garbage collection. Each request progressively fills the heap until the JVM runs out of memory.

### Three Allocation Policies

| Policy | Behavior | Use Case |
|--------|----------|----------|
| **request** | OOM after N requests | Test request-based monitoring thresholds |
| **time** | OOM within T seconds | Test time-based SLOs and deadline monitoring |
| **realistic** | Probabilistic alloc/dealloc | Simulate real application memory patterns |

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+

### Run Locally (Crash in 10 Requests)

```bash
cd heap-oom
./mvnw quarkus:dev -Dcrash.oom-policy=request -Dcrash.req.total=10
```

In another terminal, trigger the crash:

```bash
# Hit the endpoint 10 times
for i in {1..10}; do
  curl http://localhost:8080/alloc/hit
  echo ""
done
```

**Expected output:** Application crashes with `java.lang.OutOfMemoryError: Java heap space` after the 10th request.

## Configuration

All configuration is done via system properties or environment variables:

### Common Settings

| Property | Default | Description |
|----------|---------|-------------|
| `crash.oom-policy` | `request` | Policy to use: `request`, `time`, or `realistic` |
| `crash.touch-pages` | `true` | Force OS to commit memory pages (more realistic) |
| `crash.max-retained-chunks` | `2147483647` | Maximum number of 1MB chunks to retain |

### Request-Bound Policy

```bash
-Dcrash.oom-policy=request
-Dcrash.req.total=100
```

| Property | Default | Description |
|----------|---------|-------------|
| `crash.req.total` | `100` | Number of requests before OOM |

**How it works:** Divides remaining heap space by requests left, allocating evenly until the final request triggers OOM.

### Time-Bound Policy

```bash
-Dcrash.oom-policy=time
-Dcrash.time.duration-seconds=30
-Dcrash.time.target-rps=10000
-Dcrash.time.auto-allocate-on-deadline=true
```

| Property | Default | Description |
|----------|---------|-------------|
| `crash.time.duration-seconds` | `30` | Time window to reach OOM |
| `crash.time.target-rps` | `10000` | Expected requests per second |
| `crash.time.auto-allocate-on-deadline` | `true` | Auto-trigger OOM if no traffic by deadline |
| `crash.time.tick-millis` | `500` | Scheduler check interval (100, 500, or 1000) |

**How it works:** Calculates "virtual requests" based on elapsed time and target RPS. Allocates memory to match expected progress, ensuring OOM occurs at the deadline regardless of actual traffic.

### Realistic Policy

```bash
-Dcrash.oom-policy=realistic
-Dcrash.realistic.alloc-prob=0.7
-Dcrash.realistic.alloc-size-bytes=1048576
-Dcrash.realistic.dealloc-prob=0.3
-Dcrash.realistic.dealloc-size-bytes=262144
```

| Property | Default | Description |
|----------|---------|-------------|
| `crash.realistic.alloc-prob` | `0.7` | Probability of allocating memory per request |
| `crash.realistic.alloc-size-bytes` | `1048576` | Bytes to allocate when triggered (1MB) |
| `crash.realistic.dealloc-prob` | `0.3` | Probability of deallocating memory per request |
| `crash.realistic.dealloc-size-bytes` | `262144` | Bytes to deallocate when triggered (256KB) |

**How it works:** Each request randomly allocates or deallocates memory based on probabilities, simulating real application behavior with memory churn.

## Hands-On Examples

### Example 1: Crash After 5 Requests

```bash
# Start with 128MB heap
java -Xms128m -Xmx128m \
  -Dcrash.oom-policy=request \
  -Dcrash.req.total=5 \
  -jar target/quarkus-app/quarkus-run.jar
```

```bash
# Trigger crash
for i in {1..5}; do
  curl http://localhost:8080/alloc/hit | jq
done
```

**What you'll see:**
- Each response shows increasing `heapUsedBytes` and decreasing `bytesRemainingToMax`
- Request 5 triggers OOM and crashes the JVM
- Process exits with code 1

### Example 2: Time-Bound OOM (30 Seconds)

```bash
java -Xms256m -Xmx256m \
  -Dcrash.oom-policy=time \
  -Dcrash.time.duration-seconds=30 \
  -Dcrash.time.target-rps=100 \
  -jar target/quarkus-app/quarkus-run.jar
```

```bash
# Send requests slowly
while true; do
  curl http://localhost:8080/alloc/hit | jq '.uptimeMillis'
  sleep 1
done
```

**What you'll see:**
- Application allocates memory to "catch up" with expected progress
- Even with slow traffic, OOM occurs around 30 seconds
- If no traffic, auto-allocation triggers OOM at deadline

### Example 3: Realistic Mode with Monitoring

```bash
java -Xms512m -Xmx512m \
  -Dcrash.oom-policy=realistic \
  -Dcrash.realistic.alloc-prob=0.8 \
  -Dcrash.realistic.dealloc-prob=0.2 \
  -jar target/quarkus-app/quarkus-run.jar
```

```bash
# Monitor status while sending traffic
watch -n 1 'curl -s http://localhost:8080/alloc/status | jq'

# In another terminal, generate load
while true; do curl http://localhost:8080/alloc/hit > /dev/null; done
```

**What you'll see:**
- `heapUsedBytes` fluctuates as memory is allocated and deallocated
- `retainedChunks` grows over time (net positive allocation)
- Eventually triggers OOM (timing is non-deterministic)

## Kubernetes Deployment

### Deploy to Cluster

```bash
kubectl apply -f manifests/deploy.yaml
```

### Configure via Environment Variables

Edit `manifests/deploy.yaml` to customize behavior:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: heap-oom
spec:
  template:
    spec:
      containers:
      - name: heap-oom
        image: your-registry/heap-oom:latest
        env:
        - name: JAVA_OPTS
          value: "-Xms256m -Xmx256m -Dcrash.oom-policy=request -Dcrash.req.total=50"
        resources:
          limits:
            memory: "512Mi"
          requests:
            memory: "256Mi"
```

Apply the updated manifest:

```bash
kubectl apply -f manifests/deploy.yaml
```

### Trigger and Observe

```bash
# Port-forward to the pod
kubectl port-forward deployment/heap-oom 8080:8080

# Trigger OOM
for i in {1..50}; do curl http://localhost:8080/alloc/hit; done

# Watch pod restart
kubectl get pods -w
```

**Expected behavior:**
- Pod crashes with `OOMKilled` status
- Kubernetes restarts the pod automatically
- CrashLoopBackOff if crash happens immediately on startup

## Observability

### Key Metrics to Monitor

Monitor these JVM metrics to detect heap exhaustion:

```promql
# Heap usage percentage
(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100

# Heap usage growth rate
rate(jvm_memory_used_bytes{area="heap"}[1m])
```

### Sample Alert Rules

```yaml
- alert: HeapUsageHigh
  expr: (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) > 0.9
  for: 1m
  annotations:
    summary: "Heap usage above 90%"

- alert: PodOOMKilled
  expr: kube_pod_container_status_last_terminated_reason{reason="OOMKilled"} == 1
  annotations:
    summary: "Pod was OOMKilled"
```

## Troubleshooting

### Application Crashes Immediately

**Cause:** Heap size too small for initial allocation.

**Solution:** Increase `-Xmx` or reduce `crash.req.total`:

```bash
java -Xms512m -Xmx512m -Dcrash.req.total=100 -jar target/quarkus-app/quarkus-run.jar
```

### Time-Bound Policy Doesn't Crash on Time

**Cause:** `crash.time.tick-millis` not matching scheduler interval.

**Solution:** Use supported values (100, 500, or 1000):

```bash
-Dcrash.time.tick-millis=500
```

### Realistic Mode Takes Too Long to Crash

**Cause:** Deallocation probability too high.

**Solution:** Increase allocation probability or decrease deallocation:

```bash
-Dcrash.realistic.alloc-prob=0.9
-Dcrash.realistic.dealloc-prob=0.1
```

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
docker build -f Dockerfile -t heap-oom:latest .
```

Run the container:

```bash
docker run -p 8080:8080 \
  -e JAVA_OPTS="-Xmx256m -Dcrash.req.total=10" \
  heap-oom:latest
```

## License

Licensed under the Apache License 2.0. See [LICENSE](../LICENSE) for details.
