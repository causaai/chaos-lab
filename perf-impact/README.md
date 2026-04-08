A Quarkus-based application that demonstrates how Garbage Collection (GC) pressure impacts application performance and throughput. Unlike crash-inducing scenarios, this application runs continuously through phase-based allocation patterns to measure observable performance degradation without causing OOM.

## What It Does

This application simulates realistic GC pressure scenarios that degrade application performance over time. It cycles through four distinct phases every 60 seconds (configurable), allowing you to observe how different allocation patterns affect throughput, latency, and GC behavior.

**Real-world scenarios this simulates:**
- Traffic spikes causing allocation storms
- Memory leaks leading to promotion pressure
- Cache warming and live set bloat
- Performance degradation under sustained load

**Key difference from other chaos-lab projects:** This does NOT crash—it demonstrates measurable performance impact through GC pressure.

## How It Works

### Phase-Based Execution Model

The application operates in a continuous cycle of four phases:

| Phase | Duration | Purpose | Behavior |
|-------|----------|---------|----------|
| **BASELINE** | 60s | Normal operation | Low allocation rate, minimal GC |
| **RAMP** | 60s | Gradual increase | Moderate allocation, building pressure |
| **STRESS** | 60s | Peak pressure | High allocation, frequent GC, observable latency |
| **RECOVERY** | 60s | Cooldown | Reduced allocation, memory release |

After RECOVERY, the cycle repeats: BASELINE → RAMP → STRESS → RECOVERY → BASELINE...

### Architecture Components

1. **PhaseEngine**: Calculates current phase based on elapsed time
2. **ScenarioScheduler**: Executes active scenario every 1 second
3. **GcScenario Implementations**: Define allocation behavior per phase
4. **RetainedHeap**: Stores objects to survive GC cycles (promotes to old gen)
5. **WorkResource**: Lightweight endpoint to measure throughput impact

### Available Scenarios

#### 1. ALLOCATION_STORM
Simulates high allocation rate that stresses Young Generation GC.

**Phase behavior:**
- BASELINE: 0.1% heap/sec allocation
- RAMP: 1% heap/sec allocation
- STRESS: 10% heap/sec allocation
- RECOVERY: 0.05% heap/sec allocation

**Impact:** Frequent Young GC pauses, increased GC time percentage

#### 2. PROMOTION_PRESSURE
Simulates medium-lived objects that survive Young GC and promote to Old Generation.

**Phase behavior:**
- BASELINE: 2.5% allocation, 15% retention
- RAMP: 7% allocation, 50% retention
- STRESS: 10% allocation, 85% retention
- RECOVERY: 0.05% allocation, 2% retention + release 50% of retained objects

**Impact:** Old Gen fills up, triggers Full GC, significant latency spikes

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- `wrk` or `ab` (for load testing)
- `jq` (for JSON parsing)

### Run Locally with ALLOCATION_STORM

```bash
cd perf-impact

# Run with allocation storm scenario
GC_SCENARIO=ALLOCATION_STORM ./mvnw quarkus:dev
```

### Run with PROMOTION_PRESSURE (Default)

```bash
# Uses default from application.properties
./mvnw quarkus:dev
```

**What you'll see:**
```
INFO  [ScenarioScheduler] GC SCENARIO: PROMOTION_PRESSURE STARTED
```

The application runs continuously—no crash expected.

## Configuration

Configure via environment variables or system properties:

### Core Settings

| Property | Default | Description |
|----------|---------|-------------|
| `GC_SCENARIO` | `PROMOTION_PRESSURE` | Scenario to run: `ALLOCATION_STORM` or `PROMOTION_PRESSURE` |
| `gc.cycle.seconds` | `60` | Duration of each phase in seconds |
| `scenario.scheduler.delay` | `60s` | Delay before starting scenario (warmup period) |

### Example Configurations

```bash
# Fast cycle for quick testing (15s per phase)
GC_SCENARIO=ALLOCATION_STORM gc.cycle.seconds=15 ./mvnw quarkus:dev

# Allocation storm with longer phases
GC_SCENARIO=ALLOCATION_STORM gc.cycle.seconds=120 ./mvnw quarkus:dev

# Promotion pressure (default)
GC_SCENARIO=PROMOTION_PRESSURE ./mvnw quarkus:dev
```

## Hands-On Examples

### Example 1: Measure Throughput Degradation

**Step 1:** Start the application with GC logging

```bash
java -Xms512m -Xmx512m \
  -Xlog:gc*:file=gc.log \
  -DGC_SCENARIO=PROMOTION_PRESSURE \
  -jar target/quarkus-app/quarkus-run.jar
```

**Step 2:** Generate constant load using wrk

```bash
# Run for 5 minutes (covers full cycle)
wrk -t4 -c100 -d300s --latency http://localhost:8080/work
```

**Step 3:** Observe throughput changes

```
Phase 1 (BASELINE):  ~50,000 req/sec
Phase 2 (RAMP):      ~40,000 req/sec
Phase 3 (STRESS):    ~25,000 req/sec  ← 50% degradation
Phase 4 (RECOVERY):  ~45,000 req/sec
```

**Step 4:** Analyze GC logs

```bash
./scripts/gc-parser.sh gc.log
```

**Expected output:**
```
GC PRESSURE REPORT
-------------------
Runtime              : 300 sec
GC events            : 1247
Total GC time        : 15234 ms
GC time %            : 5.08 %

Pause stats
Min pause            : 2.1 ms
Avg pause            : 12.2 ms
Max pause            : 156.3 ms
P50 pause            : 8.5 ms
P95 pause            : 45.2 ms
P99 pause            : 89.7 ms
```

### Example 2: Observe Phase Transitions

**Step 1:** Monitor current phase

```bash
watch -n 1 'curl -s http://localhost:8080/status | jq'
```

**Output:**
```json
{
  "scenario": "PROMOTION_PRESSURE",
  "phase": "BASELINE",
  "cycleSeconds": "60",
  "time": 1712589234567
}
```

After 60 seconds, phase changes to RAMP, then STRESS, then RECOVERY.

**Step 2:** Correlate with heap usage

```bash
# Monitor heap in real-time
watch -n 1 'curl -s http://localhost:8080/q/metrics | grep jvm_memory_used_bytes | grep heap'
```

You'll see heap usage grow during STRESS phase and drop during RECOVERY.

### Example 3: Compare Scenarios Side-by-Side

**Terminal 1: ALLOCATION_STORM**
```bash
java -Xms512m -Xmx512m \
  -Xlog:gc*:file=gc-storm.log \
  -DGC_SCENARIO=ALLOCATION_STORM \
  -Dquarkus.http.port=8080 \
  -jar target/quarkus-app/quarkus-run.jar
```

**Terminal 2: PROMOTION_PRESSURE**
```bash
java -Xms512m -Xmx512m \
  -Xlog:gc*:file=gc-promotion.log \
  -DGC_SCENARIO=PROMOTION_PRESSURE \
  -Dquarkus.http.port=8081 \
  -jar target/quarkus-app/quarkus-run.jar
```

**Load both:**
```bash
wrk -t2 -c50 -d180s http://localhost:8080/work &
wrk -t2 -c50 -d180s http://localhost:8081/work &
```

**Compare results:**
```bash
./scripts/gc-parser.sh gc-storm.log > storm-report.txt
./scripts/gc-parser.sh gc-promotion.log > promotion-report.txt
diff storm-report.txt promotion-report.txt
```

**Key differences:**
- ALLOCATION_STORM: More frequent, shorter pauses (Young GC)
- PROMOTION_PRESSURE: Less frequent, longer pauses (Full GC)

### Example 4: Visualize with Prometheus + Grafana

**Step 1:** Start application with Prometheus metrics enabled (already configured)

```bash
./mvnw quarkus:dev
```

**Step 2:** Scrape metrics endpoint

```bash
curl http://localhost:8080/q/metrics
```

**Key metrics to monitor:**
```promql
# Heap usage
jvm_memory_used_bytes{area="heap"}

# GC pause time
jvm_gc_pause_seconds_sum

# GC frequency
rate(jvm_gc_pause_seconds_count[1m])

# Request latency (during load test)
http_server_requests_seconds_bucket
```

**Step 3:** Create Grafana dashboard with panels for:
- Heap usage over time (shows phase transitions)
- GC pause duration (spikes during STRESS)
- Request throughput (drops during STRESS)
- Request latency P99 (increases during STRESS)

## Kubernetes Deployment

### Deploy to Cluster

```bash
kubectl apply -f manifests/deploy.yaml
```

This creates a deployment named `auth-cache` (example service name) with:
- 650Mi memory limit
- 378m heap size (-Xmx378m)
- GC logging enabled
- JMX monitoring on port 9091

### Generate Load

```bash
# Deploy wrk runner
kubectl apply -f manifests/wrk-runner.yaml

# Check wrk pod logs for results
kubectl logs -f wrk-runner
```

### Monitor Performance

```bash
# Watch phase transitions
kubectl exec -it deployment/auth-cache -- \
  watch -n 1 'curl -s localhost:8080/status | jq'

# Stream GC logs
kubectl logs -f deployment/auth-cache | grep "Pause"

# Check Prometheus metrics
kubectl port-forward deployment/auth-cache 8080:8080
curl http://localhost:8080/q/metrics | grep jvm_gc
```

### Observe in Production Monitoring

If you have Prometheus + Grafana:

```promql
# Throughput degradation during STRESS phase
rate(http_server_requests_seconds_count{job="auth-cache"}[1m])

# GC time percentage
rate(jvm_gc_pause_seconds_sum[1m]) * 100

# Latency increase
histogram_quantile(0.99, 
  rate(http_server_requests_seconds_bucket{job="auth-cache"}[1m])
)
```

## Understanding the Performance Impact

### Why GC Affects Performance

1. **Stop-The-World Pauses**: Application threads freeze during GC
2. **CPU Overhead**: GC threads compete for CPU with application threads
3. **Memory Bandwidth**: Copying/moving objects consumes memory bandwidth
4. **Cache Pollution**: GC activity evicts hot data from CPU caches

### Allocation Storm Impact

```
High allocation rate → Frequent Young GC → Many short pauses

Example:
- 1000 Young GC events in 60s
- Average pause: 5ms
- Total GC time: 5000ms (8.3% of time)
- Throughput loss: ~8-10%
```

### Promotion Pressure Impact

```
Objects survive Young GC → Promote to Old Gen → Old Gen fills → Full GC

Example:
- 100 Full GC events in 60s
- Average pause: 150ms
- Total GC time: 15000ms (25% of time)
- But: P99 latency spikes to 150ms+ (unacceptable for many services)
```

### The Math Behind Phase Transitions

**ALLOCATION_STORM with 512MB heap:**

```
BASELINE:  512MB × 0.001 = 512KB/sec
RAMP:      512MB × 0.01  = 5.12MB/sec
STRESS:    512MB × 0.10  = 51.2MB/sec  ← Young Gen fills in ~1 second
RECOVERY:  512MB × 0.0005 = 256KB/sec
```

**PROMOTION_PRESSURE with 512MB heap:**

```
STRESS phase:
- Allocate: 512MB × 0.10 = 51.2MB/sec
- Retain: 85% of allocations
- Retained per second: 43.5MB
- Old Gen fills in: ~10-15 seconds
- Result: Multiple Full GCs during 60s STRESS phase
```

## Observability

### Key Metrics to Monitor

```promql
# GC frequency (events per second)
rate(jvm_gc_pause_seconds_count[1m])

# GC time percentage
rate(jvm_gc_pause_seconds_sum[1m]) * 100

# Heap usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

# Old Gen usage (promotion pressure indicator)
jvm_memory_used_bytes{area="heap",id="G1 Old Gen"}

# Request throughput
rate(http_server_requests_seconds_count[1m])

# Request latency P99
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[1m]))
```

### Sample Alert Rules

```yaml
- alert: HighGCPressure
  expr: rate(jvm_gc_pause_seconds_sum[5m]) * 100 > 10
  for: 2m
  annotations:
    summary: "Application spending >10% time in GC"

- alert: FrequentFullGC
  expr: rate(jvm_gc_pause_seconds_count{action="end of major GC"}[5m]) > 0.1
  for: 2m
  annotations:
    summary: "Full GC occurring more than once per 10 seconds"

- alert: LatencyDegradation
  expr: histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m])) > 0.1
  for: 2m
  annotations:
    summary: "P99 latency above 100ms"
```

## Troubleshooting

### Scenario Not Starting

**Cause:** Missing or invalid `GC_SCENARIO` environment variable.

**Solution:** Set valid scenario name:

```bash
GC_SCENARIO=ALLOCATION_STORM ./mvnw quarkus:dev
```

Valid values: `ALLOCATION_STORM`, `PROMOTION_PRESSURE`

### No Observable Performance Impact

**Cause:** Heap size too large for allocation rates.

**Solution:** Reduce heap size to increase GC pressure:

```bash
java -Xms256m -Xmx256m -jar target/quarkus-app/quarkus-run.jar
```

### Application Crashes with OOM

**Cause:** Heap size too small or retention limit too high.

**Solution:** Increase heap size:

```bash
java -Xms1g -Xmx1g -jar target/quarkus-app/quarkus-run.jar
```

Or adjust retention limit in `Constants.java` (default: 70% of heap).

### Want Faster Phase Transitions

**Cause:** Default 60s per phase too slow for testing.

**Solution:** Reduce cycle time:

```bash
java -Dgc.cycle.seconds=15 -jar target/quarkus-app/quarkus-run.jar
```

Now each phase lasts 15 seconds (full cycle = 60 seconds).

## Building the Application

### Package the Application

```bash
./mvnw package
```

This creates `target/quarkus-app/quarkus-run.jar`.

### Build Docker Image

```bash
./mvnw package
docker build -f Dockerfile -t perf-impact:latest .
```

Run the container:

```bash
docker run --rm \
  -p 8080:8080 \
  -e GC_SCENARIO=ALLOCATION_STORM \
  -e JAVA_OPTS="-Xms512m -Xmx512m -Xlog:gc" \
  perf-impact:latest
```

## Real-World Lessons

This simulator demonstrates why production applications should:

1. **Monitor GC Metrics**: Track GC time percentage, pause duration, frequency
2. **Set SLOs for GC**: e.g., "GC time < 5%", "P99 pause < 50ms"
3. **Right-Size Heap**: Balance between GC frequency and pause duration
4. **Choose Appropriate GC**: G1GC for low latency, ZGC/Shenandoah for ultra-low latency
5. **Implement Backpressure**: Reject requests when under GC pressure
6. **Use Object Pooling**: Reduce allocation rate for hot paths
7. **Profile Allocations**: Identify and optimize allocation hotspots

### GC Tuning Guidelines

```bash
# For throughput-oriented workloads
-XX:+UseParallelGC -Xms4g -Xmx4g

# For latency-sensitive workloads (default in Java 17+)
-XX:+UseG1GC -Xms4g -Xmx4g -XX:MaxGCPauseMillis=50

# For ultra-low latency (requires Java 15+)
-XX:+UseZGC -Xms4g -Xmx4g

# Always enable GC logging
-Xlog:gc*:file=gc.log:time,uptime,level,tags
```

## License

Licensed under the Apache License 2.0. See [LICENSE](../LICENSE) for details.
