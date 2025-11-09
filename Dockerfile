# Multi-stage build for optimized Spring Boot deployment
# Stage 1: Build the application
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy Gradle files first (for better caching)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./
COPY gradlew.bat ./

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds)
RUN gradle build --no-daemon -x test

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port (Railway will set PORT env variable)
EXPOSE 8080

# Run the application
# Use exec form to handle signals properly
# Bind to 0.0.0.0 instead of localhost for Fly.io
# Set JVM memory limits for 512MB container:
# -Xmx384m: Maximum heap size (75% of 512MB)
# -Xms256m: Initial heap size
# -XX:MaxMetaspaceSize=96m: Limit for class metadata
# -XX:+UseContainerSupport: Detect container limits
ENTRYPOINT ["java", \
    "-Xmx384m", \
    "-Xms256m", \
    "-XX:MaxMetaspaceSize=96m", \
    "-XX:+UseContainerSupport", \
    "-Dserver.address=0.0.0.0", \
    "-jar", "app.jar"]
