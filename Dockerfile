# --- Stage 1: build the jar ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first so Docker can cache the dependency download layer
# separately from source code changes (much faster rebuilds).
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# --- Stage 2: run the jar on a lightweight JRE ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/distributed-cache-0.1.0.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
