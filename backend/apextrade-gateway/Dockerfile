# ==============================================================================
# ApexTrade Core - Gateway Service Container Build for Render
# Context: backend/ (Root Directory on Render: backend)
# Target: apextrade-gateway (Isolated microservice packaging)
# ==============================================================================

# Stage 1: Build Gateway and its shared common dependency
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Copy parent POM, wrapper, and module trees needed for Gateway
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
COPY apextrade-common apextrade-common
COPY apextrade-engine apextrade-engine
COPY apextrade-gateway apextrade-gateway

# Package only apextrade-gateway and also-make apextrade-common (engine is excluded)
RUN mvn clean package -pl apextrade-gateway -am -DskipTests -B \
    && rm -f apextrade-gateway/target/*.original

# Stage
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the packaged Gateway Spring Boot executable JAR
COPY --from=build /workspace/apextrade-gateway/target/apextrade-gateway-*.jar app.jar

# Render automatically provides dynamic $PORT environment variable at runtime
ENV PORT=8080
EXPOSE 8080

# Low-latency JVM pause tuning with dynamic Render port binding
ENTRYPOINT ["sh", "-c", "java -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -Dserver.port=${PORT:-8080} -jar app.jar"]

