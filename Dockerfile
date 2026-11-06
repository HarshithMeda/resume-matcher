# syntax=docker/dockerfile:1

# ---- Frontend stage: build the React app ----
# Debian-based (not Alpine): Vite's and TypeScript's native binaries are published for glibc.
FROM node:26-slim AS frontend
WORKDIR /frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY frontend/ ./
RUN npm run build


# ---- Backend stage: compile and package with the project's Maven wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Dependencies first, so they're cached until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Spring Boot serves the built React app from static/ (see SpaController).
COPY --from=frontend /frontend/dist/ src/main/resources/static/
# Tests run in CI (they need Docker for Testcontainers), not during the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && cp target/resume-matcher-*.jar app.jar

# Split the jar into layers (libraries change rarely, app code often) for faster rebuilds and pulls.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted


# ---- Runtime stage: small JRE, non-root user ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app

COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

USER app

# Memory for a 512 MB container: heap capped at 45% (~230 MB), leaving room for metaspace,
# code cache, thread stacks and native memory. Serial GC suits a small heap and one or two CPUs.
# Exit on OOM so the platform restarts the app instead of leaving it half-broken.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=45 -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError" \
    SERVER_TOMCAT_THREADS_MAX=50 \
    PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
