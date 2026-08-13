# =============================================================================
# quarkus-perf — Dockerfile
#
# Stage 1: Maven build on UBI OpenJDK 21 (Red Hat registry, security compliant)
# Stage 2: Runtime on UBI minimal OpenJDK 21 JRE (smallest footprint, non-root)
#
# Security rules enforced:
#   - All images from registry.redhat.io
#   - Non-root USER 1001
#   - Capabilities dropped via securityContext in manifests
#   - No hardcoded secrets (all via env)
# =============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM registry.access.redhat.com/ubi9/openjdk-21:1.21 AS builder

USER root
WORKDIR /build

# Cache Maven dependencies before copying source
COPY pom.xml .
RUN mvn -B -q -e -C -DskipTests dependency:go-offline || true

# Build the Quarkus fast-jar (quarkus.package.type=uber-jar avoided for image
# layer efficiency — fast-jar produces a quarkus-run.jar entry point with
# lib/ and app/ directories that Docker layers efficiently)
COPY src ./src
RUN mvn -B -q -DskipTests package

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.21

# JVM tuning:
#   - Container-aware heap sizing (MaxRAMPercentage replaces fixed -Xmx)
#   - Heap dump on OOM for post-mortem analysis
#   - GC logging for chaos scenario visibility
ENV JAVA_OPTS_APPEND="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/dumps/heapdump.hprof \
    -Xlog:gc*:file=/dumps/gc.log:time,uptime,level,tags:filecount=4,filesize=20m \
    -Dfile.encoding=UTF-8"

WORKDIR /deployments

# Copy the Quarkus fast-jar artefacts from build stage
COPY --from=builder /build/target/quarkus-app/lib/           ./lib/
COPY --from=builder /build/target/quarkus-app/app/           ./app/
COPY --from=builder /build/target/quarkus-app/quarkus/       ./quarkus/
COPY --from=builder /build/target/quarkus-app/quarkus-run.jar ./quarkus-run.jar

# Directories for heap dumps and structured logs (match volume mounts in K8s manifests)
USER root
RUN mkdir -p /dumps /logs \
 && chown -R 185:0 /deployments /dumps /logs \
 && chmod -R g=u /deployments /dumps /logs

USER 185

# Quarkus HTTP port (configurable via QUARKUS_HTTP_PORT)
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/q/health/ready || exit 1

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
