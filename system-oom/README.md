A Quarkus-based application that simulates container-level Out-of-Memory (OOM) by allocating native memory beyond cgroup limits. Designed to test container orchestrator responses, cgroup OOM handling, and pod eviction policies in Kubernetes environments.

## What It Does

Unlike JVM heap OOM, this application triggers **system-level memory exhaustion** by allocating native (off-heap) memory using `ByteBuffer.allocateDirect()`. This bypasses JVM heap limits and directly consumes container memory, causing the Linux kernel's OOM killer to terminate the process.

**Real-world scenarios this simulates:**
- Native memory leaks (JNI, direct buffers, memory-mapped files)
- Container memory limits breached by off-heap allocations
- Processes consuming more memory than their cgroup allows

## How It Works

The application reads container memory limits from cgroup filesystem (supports both v1 and v2), then progressively allocates native memory until the container's memory limit is breached.

### Key Differences from heap-oom

| Aspect | heap-oom | system-oom |
|--------|----------|------------|
| **Memory Type** | JVM Heap | Native (off-heap) |
| **Allocation Method** | `new byte[]` | `ByteBuffer.allocateDirect()` |
| **Limit Source** | `-Xmx` flag | Container cgroup limit |
| **Killer** | JVM throws OOMError | Linux OOM killer terminates process |
| **Exit Code** | 1 (JVM error) | 137 (SIGKILL) |
| **Kubernetes Status** | Error | OOMKilled |

### Architecture

1. **Cgroup Detection**: Auto-detects cgroup v1 or v2 at startup
2. **Baseline Capture**: Records initial memory usage before accepting requests
3. **Progressive Allocation**: Each request allocates native memory in 1MB chunks
4. **Page Touching**: Forces OS to commit memory (RSS) by writing to each page
5. **OOM Trigger**: When usage exceeds cgroup limit, kernel kills the process

### REST Endpoints

- `GET /alloc/hit` - Allocates native memory based on configured policy
- Returns allocation details and current system memory usage

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Linux environment with cgroup support (required for local testing)
- Docker or Kubernetes (recommended for realistic testing)

### Run Locally (Linux Only)

**Note:** This application requires cgroup support and will not work on macOS or Windows without Docker.

```bash
cd system-oom

# Run in Docker to simulate container environment
docker run --rm -it \
  --memory=256m \
  -p 8080:8080 \
  -v $(pwd):/app \
  -w /app \
  maven:3.8-openjdk-17 \
  bash -c "./mvnw quarkus:dev"
```

In another terminal, trigger the crash:

```bash
# Hit the endpoint 100 times (default config)
for i in {1..100}; do
  curl http://localhost:8080/alloc/hit
  echo ""
done
```

**Expected output:** Container is killed by OOM killer with exit code 137.

## Configuration

Configure via `application.properties` or system properties:

### Common Settings

| Property | Default | Description |
|----------|---------|-------------|
| `crash.oom-policy` | `request` | Currently only `request` policy is implemented |
| `crash.req.total` | `100` | Number of requests before OOM |
| `crash.system.safety-bytes` | `20971520` | Safety margin (20MB) to prevent premature OOM |
| `crash.system.chunk-bytes` | `1048576` | Allocation chunk size (1MB) |

### Request-Bound Policy

```properties
crash.oom-policy=request
crash.req.total=50
crash.system.safety-bytes=10485760
```

**How it works:**
1. Reads container memory limit from cgroup
2. Calculates available memory: `limit - current_usage - safety_bytes`
3. Divides available memory by remaining requests
4. Allocates that amount per request
5. On final request, allocates 10x remaining to guarantee OOM

## Hands-On Examples

### Example 1: Docker Container OOM (256MB Limit)

```bash
# Build the application
./mvnw package

# Run in container with 256MB limit
docker run --rm \
  --memory=256m \
  -p 8080:8080 \
  -e JAVA_OPTS="-Dcrash.req.total=10" \
  -v $(pwd)/target:/app \
  openjdk:17-slim \
  java -jar /app/quarkus-app/quarkus-run.jar
```

```bash
# Trigger OOM
for i in {1..10}; do
  curl http://localhost:8080/alloc/hit | jq
done
```

**What you'll see:**
```json
{
  "requestCount": 1,
  "bytesAllocatedThisRequest": 20971520,
  "retainedChunks": 20,
  "systemUsedBytes": 89128960,
  "systemLimitBytes": 268435456,
  "bytesRemainingToLimit": 158334976
}
```

After request 10, the container is killed:
```
Killed
```

Check Docker logs:
```bash
docker ps -a  # Find container ID
docker inspect <container-id> | grep OOMKilled
# "OOMKilled": true
```

### Example 2: Observe Cgroup Detection

```bash
docker run --rm \
  --memory=512m \
  -p 8080:8080 \
  -v $(pwd)/target:/app \
  openjdk:17-slim \
  java -jar /app/quarkus-app/quarkus-run.jar
```

**Startup output:**
```
Detected cgroup v2 | limit=512MB | baseline=45MB
```

This shows:
- Cgroup version detected (v1 or v2)
- Container memory limit (512MB)
- Baseline memory usage before accepting requests (45MB)

### Example 3: Adjust Safety Margin

```bash
# Smaller safety margin = faster OOM
docker run --rm \
  --memory=256m \
  -p 8080:8080 \
  -e JAVA_OPTS="-Dcrash.system.safety-bytes=5242880 -Dcrash.req.total=5" \
  -v $(pwd)/target:/app \
  openjdk:17-slim \
  java -jar /app/quarkus-app/quarkus-run.jar
```

With only 5MB safety margin, the application will OOM more aggressively.

## Kubernetes Deployment

### Deploy to Cluster

```bash
kubectl apply -f manifests/deploy.yaml
```

This creates:
- Deployment with 256Mi memory limit
- Service exposing port 8080

### Trigger and Observe OOM

```bash
# Port-forward to the pod
kubectl port-forward deployment/system-oom 8080:8080

# Trigger OOM (default: 100 requests)
for i in {1..100}; do
  curl http://localhost:8080/alloc/hit
done

# Watch pod status
kubectl get pods -w
```

**Expected behavior:**
```
NAME                          READY   STATUS    RESTARTS   AGE
system-oom-7d8f9c5b6d-x7k2p   1/1     Running   0          10s
system-oom-7d8f9c5b6d-x7k2p   0/1     OOMKilled 0          45s
system-oom-7d8f9c5b6d-x7k2p   0/1     CrashLoopBackOff 1   46s
system-oom-7d8f9c5b6d-x7k2p   1/1     Running   1          47s
```

### Check OOM Events

```bash
# Describe pod to see OOM event
kubectl describe pod -l app=system-oom

# Look for:
# Last State:     Terminated
#   Reason:       OOMKilled
#   Exit Code:    137
```

### Configure Memory Limits

Edit `manifests/deploy.yaml`:

```yaml
resources:
  requests:
    memory: "128Mi"
  limits:
    memory: "512Mi"  # Increase to delay OOM
```

```bash
kubectl apply -f manifests/deploy.yaml
```

## Understanding Cgroup Memory Control

### Cgroup v1 vs v2

| Aspect | Cgroup v1 | Cgroup v2 |
|--------|-----------|-----------|
| **Limit File** | `/sys/fs/cgroup/memory/memory.limit_in_bytes` | `/sys/fs/cgroup/memory.max` |
| **Usage File** | `/sys/fs/cgroup/memory/memory.usage_in_bytes` | `/sys/fs/cgroup/memory.current` |
| **Kubernetes** | Older versions | 1.25+ (default) |
| **Detection** | Auto-detected at startup | Auto-detected at startup |

### How OOM Killer Works

When a process exceeds its cgroup memory limit:

1. **Kernel detects breach**: Memory usage > cgroup limit
2. **OOM killer selects victim**: Usually the process consuming most memory
3. **SIGKILL sent**: Process is immediately terminated (exit code 137)
4. **Container runtime notified**: Marks container as OOMKilled
5. **Kubernetes responds**: Restarts pod based on restart policy

### Memory Accounting

The cgroup limit includes:
- JVM heap (`-Xmx`)
- Native memory (direct buffers, JNI)
- Thread stacks
- Code cache
- Metaspace

This is why a container with 256Mi limit can OOM even with `-Xmx128m`.

## Observability

### Key Metrics to Monitor

```promql
# Container memory usage
container_memory_usage_bytes{pod="system-oom"}

# Container memory limit
container_spec_memory_limit_bytes{pod="system-oom"}

# Memory usage percentage
(container_memory_usage_bytes / container_spec_memory_limit_bytes) * 100

# OOMKill events
kube_pod_container_status_last_terminated_reason{reason="OOMKilled"}
```

### Sample Alert Rules

```yaml
- alert: ContainerMemoryHigh
  expr: (container_memory_usage_bytes / container_spec_memory_limit_bytes) > 0.9
  for: 1m
  annotations:
    summary: "Container memory usage above 90%"

- alert: PodOOMKilled
  expr: kube_pod_container_status_last_terminated_reason{reason="OOMKilled"} == 1
  annotations:
    summary: "Pod was killed by OOM killer"

- alert: FrequentOOMKills
  expr: rate(kube_pod_container_status_restarts_total{reason="OOMKilled"}[5m]) > 0
  annotations:
    summary: "Pod experiencing frequent OOM kills"
```

## Troubleshooting

### Application Won't Start Locally

**Cause:** No cgroup support on host OS.

**Solution:** Run in Docker container:

```bash
docker run --rm -it \
  --memory=256m \
  -p 8080:8080 \
  -v $(pwd):/app \
  -w /app \
  maven:3.8-openjdk-17 \
  ./mvnw quarkus:dev
```

### Error: "No cgroup memory controller detected"

**Cause:** Running outside container or cgroup not mounted.

**Solution:** Ensure running in Docker/Kubernetes with memory limits set.

### Container Killed Immediately on Startup

**Cause:** Memory limit too small for JVM startup.

**Solution:** Increase container memory limit:

```yaml
resources:
  limits:
    memory: "512Mi"  # Increase from 256Mi
```

### Want to Prevent OOM for Testing

**Cause:** Need to observe behavior without actual OOM.

**Solution:** Set very high `crash.req.total` or large memory limit:

```bash
-Dcrash.req.total=10000
```

Or increase container memory:

```bash
docker run --memory=2g ...
```

### OOM Happens Too Quickly

**Cause:** Safety margin too small.

**Solution:** Increase safety bytes:

```bash
-Dcrash.system.safety-bytes=52428800  # 50MB
```

## Building the Application

### Package the Application

```bash
./mvnw package
```

This creates `target/quarkus-app/quarkus-run.jar`.

### Build Docker Image

```bash
./mvnw package
docker build -f Dockerfile -t system-oom:latest .
```

Run the container:

```bash
docker run --rm \
  --memory=256m \
  -p 8080:8080 \
  -e JAVA_OPTS="-Dcrash.req.total=20" \
  system-oom:latest
```

### Recommended Memory Sizing

```
Container Limit = JVM Heap + Native Memory + Overhead

Example:
-Xmx512m (heap) + 256m (native) + 256m (overhead) = 1024Mi container limit
```


## License

Licensed under the Apache License 2.0. See [LICENSE](../LICENSE) for details.
