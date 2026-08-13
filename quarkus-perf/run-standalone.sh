#!/usr/bin/env bash
# =============================================================================
# run-standalone.sh — Build and run quarkus-perf locally using Maven
#
# Usage:
#   ./run-standalone.sh                  # Run in dev mode (hot reload)
#   ./run-standalone.sh build            # Build only (produces uber-jar)
#   ./run-standalone.sh prod             # Run the packaged jar
#   ./run-standalone.sh test             # Run with a chaos scenario enabled
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

MODE="${1:-dev}"

case "$MODE" in
  dev)
    echo ">>> Starting quarkus-perf in DEV mode (hot reload) on http://localhost:8080"
    mvn quarkus:dev \
      -Dquarkus.http.port=8080 \
      -Dchaos.db.leak.enabled=false \
      -Dchaos.memory.cache.enabled=false \
      -Dchaos.http.large-response.enabled=false
    ;;

  build)
    echo ">>> Building quarkus-perf (fast-jar)"
    mvn -B -DskipTests clean package
    echo ">>> Build complete: target/quarkus-app/quarkus-run.jar"
    ;;

  prod)
    echo ">>> Building and running quarkus-perf in PROD mode"
    mvn -B -DskipTests clean package
    java \
      -XX:+UseContainerSupport \
      -XX:MaxRAMPercentage=75.0 \
      -XX:+HeapDumpOnOutOfMemoryError \
      -XX:HeapDumpPath=/tmp/heapdump.hprof \
      -jar target/quarkus-app/quarkus-run.jar
    ;;

  test)
    echo ">>> Running quarkus-perf in TEST mode with chaos knobs active"
    echo "    - Memory cache leak: ON"
    echo "    - DB slow query:     200ms"
    echo "    - HTTP padding:      128 KB"
    mvn -B -DskipTests clean package
    CHAOS_MEMORY_CACHE_ENABLED=true \
    CHAOS_DB_SLOW_QUERY_MS=200 \
    CHAOS_HTTP_LARGE_RESPONSE_ENABLED=true \
    CHAOS_HTTP_LARGE_RESPONSE_KB=128 \
    java \
      -XX:+UseContainerSupport \
      -XX:MaxRAMPercentage=75.0 \
      -jar target/quarkus-app/quarkus-run.jar
    ;;

  *)
    echo "Unknown mode: $MODE"
    echo "Usage: $0 [dev|build|prod|test]"
    exit 1
    ;;
esac
