# Multi-stage build for the Momento API.
#   docker build -t momento-server:1.0 server
#   docker run -d -p 9000:8080 momento-server:1.0

# ── Stage 1: build the fat JAR with Gradle + JDK (these tools never reach the final image) ──
FROM gradle:8.10.2-jdk17 AS build
WORKDIR /src
# Build files first so the dependency download layer is cached when only code changes.
COPY settings.gradle.kts build.gradle.kts ./
RUN gradle --no-daemon dependencies > /dev/null
COPY src ./src
RUN gradle --no-daemon buildFatJar -x test

# ── Stage 2: slim runtime with only a JRE and the JAR ──
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 momento
WORKDIR /app
COPY --from=build /src/build/libs/momento-server.jar /app/momento-server.jar
ENV PORT=8080 \
    APP_ENV=container
EXPOSE 8080
USER 10001
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/momento-server.jar"]
