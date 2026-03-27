# ===== Build =====
FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package


# ===== Run =====
FROM eclipse-temurin:21-jre

# Default JVM + app envs (safe defaults, overridable at runtime)
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=80.0 \
-Xlog:gc*,gc+heap=info,gc+age=trace"
ENV GC_SCENARIO="PROMOTION_PRESSURE"
ENV SCENARIO_SCHEDULER_DELAY="0s"

WORKDIR /app
COPY --from=build /build/target/quarkus-app ./quarkus-app

EXPOSE 8080

RUN mkdir -p /dumps && chown -R nobody:nogroup /app /dumps
USER nobody

ENTRYPOINT ["java","-jar","quarkus-app/quarkus-run.jar"]