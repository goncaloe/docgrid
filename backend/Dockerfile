# DocGrid backend container image.
# Multi-stage: Maven + JDK for the build, JRE Alpine for the runtime. The jar is
# extracted into layers (Spring Boot 3.2+) so a code change does not rebuild the
# dependency layer - see docs/adr/0017 (1 GB memory budget).

# --- Build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# Resolve dependencies before any code: the Docker layer cache reuses them while
# the pom does not change.
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -DskipTests package

# Extract the jar into layers: dependencies/, spring-boot-loader/,
# snapshot-dependencies/, application/. Copying each separately keeps the layer
# cache useful.
RUN java -Djarmode=tools -jar target/docgrid-backend-0.1.0-SNAPSHOT.jar extract --layers --destination extracted

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN adduser -S -u 1001 docgrid
WORKDIR /app

COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

# The AWS design runs on a 1 GB machine (t4g.micro): cap the heap at 40% of the
# container RAM and use the serial collector, the one that uses the least
# off-heap memory.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:+UseSerialGC"
EXPOSE 8080

# Numeric UID on purpose: it matches the adduser -u 1001 above, and hadolint
# (DL3066) wants the numeric form.
USER 1001
# Readiness does not depend on S3/SQS (see HealthProbesTest) - it is the right probe
# for "the process is ready to serve".
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "docgrid-backend-0.1.0-SNAPSHOT.jar"]