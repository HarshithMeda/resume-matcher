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
# Result: app.jar plus lib/, which also lets the JVM use a class-data-sharing archive (below).
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted


# ---- Runtime stage: small JRE, non-root user ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app

COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

# Settings for Render's free plan (512 MB RAM, a small slice of one CPU):
# - Heap capped at 45% (~230 MB), leaving room for metaspace, code cache, thread stacks and native
#   memory; Serial GC suits a small heap and a single CPU.
# - TieredStopAtLevel=1 uses only the quick JIT compiler. On a tenth of a CPU this more than halves
#   startup (measured: 285 s -> 113 s) at the cost of some peak speed this app doesn't need.
# - Exit on OOM so the platform restarts the app instead of leaving it half-broken.
# - The "prod" profile: Swagger off, Render/Cloudflare client-IP rules. PORT is replaced by the
#   platform's own value (Render sets 10000).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=45 -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError" \
    SERVER_TOMCAT_THREADS_MAX=50 \
    PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod

# Class-data-sharing archive: start the app once at build time (it stops as soon as Spring is ready)
# and save the loaded classes, so real starts can skip that work. No database is needed: Flyway is
# off and Hibernate doesn't look at the database during this run. If the archive can't be used, the
# JVM simply starts without it.
RUN java -XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh \
        -Dapp.jwt.secret=cds-training-run-only-not-a-real-secret-0123456789 \
        -Dspring.flyway.enabled=false \
        -Dspring.jpa.hibernate.ddl-auto=none \
        -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
        -jar app.jar > /dev/null \
    && test -s app.jsa

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-jar", "app.jar"]
