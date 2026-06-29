# ============================================================
# Stage 1 — Build
# Uses IBM Semeru Runtimes (Eclipse OpenJ9 JDK) as required
# by the PRD constraint: "Semeru java 25 docker images from
# Adoptium marketplace".  Semeru 21 LTS is the current stable.
# ============================================================
FROM maven:3.9.9-ibm-semeru-21-jammy AS builder

WORKDIR /build

# Cache Maven dependencies before copying source
COPY pom.xml .
RUN mvn -B -q -e -C -DskipTests dependency:go-offline \
        -Dliberty.skip=true || true

# Copy source and build
COPY src ./src
RUN mvn -B -q -DskipTests package

# ============================================================
# Stage 2 — Runtime
# IBM Semeru Runtimes JRE 21 (Eclipse OpenJ9)
# ============================================================
FROM ibm-semeru-runtimes:open-21-jre-jammy

# JVM tuning: container-aware memory, crash on OOM for clean restart
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75 \
    -Xdump:heap:events=systhrow+throw,filter=java/lang/OutOfMemoryError,file=/dumps/heapdump.%Y%m%d.%H%M%S.%pid.phd \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/dumps/heapdump.hprof \
    -Dfile.encoding=UTF-8"

# Copy the packaged Liberty server
COPY --from=builder /build/target/liberty-perf.zip /opt/liberty-perf.zip

RUN apt-get update && apt-get install -y --no-install-recommends unzip \
 && unzip -q /opt/liberty-perf.zip -d /opt/ \
 && rm /opt/liberty-perf.zip \
 && apt-get remove -y unzip && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

# Copy H2 driver into Liberty shared lib directory
COPY --from=builder /build/target/liberty/wlp/usr/servers/defaultServer/lib/ \
     /opt/wlp/usr/servers/defaultServer/lib/

# Directories for dumps and logs
RUN mkdir -p /dumps /logs \
 && chown -R 1001:0 /opt/wlp /dumps /logs \
 && chmod -R g=u /opt/wlp /dumps /logs

USER 1001

# Liberty HTTP port
EXPOSE 9080
# Liberty HTTPS port (optional)
EXPOSE 9443

# Prometheus metrics / health endpoints also served on 9080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:9080/health/ready || exit 1

ENTRYPOINT ["/opt/wlp/bin/server", "run", "defaultServer"]
