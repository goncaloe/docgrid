# Imagem de container do backend DocGrid.
# Multi-stage: build com Maven + JDK, runtime com JRE Alpine. O jar é extraído
# em camadas (Spring Boot 3.2+) para que uma alteração de código não reconstrua
# a camada das dependências — ver docs/adr/0017 (orçamento de memória de 1 GB).

# --- Fase de build -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# Resolve as dependências antes do código: o cache de camadas do Docker reutiliza-as
# enquanto o pom não mudar.
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -DskipTests package

# Extrai o jar em camadas: dependencies/, spring-boot-loader/, snapshot-dependencies/,
# application/. Copiar cada uma em separado mantém o cache de camadas útil.
RUN java -Djarmode=tools -jar target/docgrid-backend-0.1.0-SNAPSHOT.jar extract --layers --destination extracted

# --- Fase de runtime ---------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN adduser -S -u 1001 docgrid
WORKDIR /app

COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

# O desenho AWS corre numa máquina de 1 GB (t4g.micro): limite a heap a 40% da RAM
# do container e use o coletor serial, que é o que menos memória fora da heap usa.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:+UseSerialGC"
EXPOSE 8080

USER docgrid
# A readiness não depende de S3/SQS (ver HealthProbesTest) — é a sonda certa para
# "o processo está pronto a servir".
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "docgrid-backend-0.1.0-SNAPSHOT.jar"]
