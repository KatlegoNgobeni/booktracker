# ============================================================
# Stage 1: Build the Spring Boot fat jar
# Eclipse Temurin is the official open-source build of OpenJDK.
# Maven 3.9 ships with the official maven:3.9-temurin-21 image.
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy dependency descriptor first for layer caching:
# if pom.xml hasn't changed, the mvn dependency:go-offline
# layer is reused even when source files change.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build the fat jar, skipping tests.
# (Tests that require a live database run via Testcontainers
#  in CI; we don't run them at image-build time.)
COPY src ./src
RUN mvn -DskipTests package -q

# ============================================================
# Stage 2: Runtime image — minimal JRE 21 base
# ============================================================
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Copy only the executable fat jar from the build stage.
# The *-SNAPSHOT.jar glob matches the versioned artifact name
# produced by spring-boot-maven-plugin.
COPY --from=build /workspace/target/*.jar app.jar

# Spring Boot embedded Tomcat listens on 8080 by default.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
