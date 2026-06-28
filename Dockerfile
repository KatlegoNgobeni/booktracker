# ============================================================
# Stage 1: Build the React/Vite frontend
# node:20-slim is ~100 MB smaller than node:20 and sufficient
# for a pure build stage with no system-level tools needed.
# ============================================================
FROM node:20-slim AS frontend-build

WORKDIR /app/frontend

# Layer cache: copy manifests first so npm ci is only re-run
# when package-lock.json changes, not on every source change.
COPY frontend/package*.json ./
RUN npm ci

# Copy the rest of the frontend source and build.
# npm run build = tsc -b && vite build (see frontend/package.json)
# Output: /app/frontend/dist
COPY frontend/ ./
RUN npm run build

# ============================================================
# Stage 2: Build the Spring Boot fat JAR
# Eclipse Temurin is the official open-source build of OpenJDK.
# Maven 3.9 ships with the official maven:3.9-eclipse-temurin-21 image.
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Layer cache: resolve dependencies before copying source so
# this layer is reused on source-only changes.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy Java source.
COPY src ./src

# CRITICAL ORDERING (Risk 2): COPY the Vite output into
# src/main/resources/static/ BEFORE mvn package.
# spring-boot-maven-plugin packages src/main/resources/ at
# package time — if this COPY runs after mvn package the SPA
# is absent from the JAR and GET / returns 404.
COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static/

# Package the fat JAR, skipping tests.
# Integration tests that require a live database run via
# Testcontainers in CI (07-03); not at image-build time.
RUN mvn -DskipTests package -q

# ============================================================
# Stage 3: Minimal JRE runtime image
# ============================================================
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Copy only the executable fat JAR from the build stage.
# The *.jar glob matches the versioned artifact produced by
# spring-boot-maven-plugin.
COPY --from=build /app/target/*.jar app.jar

# Spring Boot embedded Tomcat listens on 8080 by default.
EXPOSE 8080

# No ENV or ARG carries secret values — DATABASE_URL / JWT_SECRET
# are injected at runtime by Render (D-06). See docker-compose.yml
# for the local development env-var shape.
ENTRYPOINT ["java", "-jar", "app.jar"]
