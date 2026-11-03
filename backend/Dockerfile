# ==============================================================================
# ApexTrade Core - Backend Container Build (Eclipse Temurin Java 25)
# Root Directory: backend/
# ==============================================================================

# Stage 1: Build the backend application
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Copy backend configuration and source
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
COPY src src

# Package production executable JAR without running tests during image build
RUN mvn clean package -DskipTests -B && rm -f target/*.original

# Stage 2: Minimal runtime image
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy packaged JAR
COPY --from=build /workspace/target/*.jar app.jar

# Render automatically provides $PORT at runtime
ENV PORT=8080
EXPOSE 8080

# Run JVM with low-latency flags
ENTRYPOINT ["sh", "-c", "java -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -jar app.jar"]

