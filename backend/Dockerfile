# ==============================================================================
# ApexTrade Core - Multi-Module Backend Container Build (Eclipse Temurin Java 25)
# Root Directory: backend/
# ==============================================================================

# Stage 1: Build the backend application reactor
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Copy parent POM, wrapper, and module trees
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
COPY apextrade-common apextrade-common
COPY apextrade-engine apextrade-engine
COPY apextrade-gateway apextrade-gateway

# Package production executable JAR across all modules
RUN mvn clean package -DskipTests -B && rm -f apextrade-gateway/target/*.original

# Stage 2: Minimal runtime image
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy packaged Gateway executable JAR
COPY --from=build /workspace/apextrade-gateway/target/*.jar app.jar

# Render automatically provides $PORT at runtime
ENV PORT=8080
EXPOSE 8080

# Run JVM with Java 25 Virtual Threads & low-latency GC flags
ENTRYPOINT ["sh", "-c", "java -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -jar app.jar"]
