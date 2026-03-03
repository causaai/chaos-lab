# ================================
# Stage 1 — Build
# ================================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# copy only pom first for dependency caching
COPY pom.xml .
RUN mvn -B -q -e -C -DskipTests dependency:go-offline

# copy source
COPY src ./src

# build app
RUN mvn -B -q -DskipTests package


# ================================
# Stage 2 — Runtime
# ================================
FROM eclipse-temurin:21-jre-jammy

# JVM tuning for container + deterministic crash
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=80 -XX:+CrashOnOutOfMemoryError"

WORKDIR /app

# copy quarkus fast-jar layout
COPY --from=builder /build/target/quarkus-app/ /app/

# dump directory for RCA artifacts
RUN mkdir /dumps \
 && chown -R 1001:0 /app /dumps

USER 1001

EXPOSE 8080

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar quarkus-run.jar"]