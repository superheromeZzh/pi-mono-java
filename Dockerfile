# CampusClaw Agent with Hybrid Execution (Local + Sandbox)
# Multi-stage build

# Stage 1: Build
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Copy gradle files first for better layer caching
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Copy modules
COPY modules ./modules

# Build the application
RUN gradle :modules:coding-agent-cli:bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Install Docker CLI (for DIND communication)
RUN apk add --no-cache docker-cli curl

WORKDIR /app

# Copy the built JAR
COPY --from=builder /app/modules/coding-agent-cli/build/libs/*.jar app.jar

# Create workspace directory
RUN mkdir -p /workspace

# Environment variables
ENV SPRING_PROFILES_ACTIVE=k8s
ENV DOCKER_HOST=tcp://localhost:2375
ENV WORKSPACE_PATH=/workspace
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

# Expose ports
EXPOSE 8080 9249

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
