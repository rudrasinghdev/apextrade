# ==============================================================================
# ApexTrade Core - Matching Engine (S2) Web Service Container
# Context: backend/
# ==============================================================================

# Stage 1: Build Engine and Common dependency
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Copy parent POM, wrapper, and module trees
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
COPY apextrade-common apextrade-common
COPY apextrade-gateway apextrade-gateway
COPY apextrade-engine apextrade-engine

# Package standalone fat JAR with all dependencies bundled
RUN mvn clean package -pl apextrade-engine -am -DskipTests -B

# Stage 2: Minimal runtime image
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the built engine JAR
COPY --from=build /workspace/apextrade-engine/target/apextrade-engine-*.jar engine.jar

# Dynamic Web Service HTTP port ($PORT) and Raw TCP Matching port (9000)
ENV PORT=8080
EXPOSE 8080
EXPOSE 9000

# Low-latency JVM pause tuning
ENTRYPOINT ["sh", "-c", "java -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -jar engine.jar"]