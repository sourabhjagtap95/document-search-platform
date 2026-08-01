# Deliberately avoids BuildKit-only features (syntax directive, cache mounts) so it
# builds with the legacy docker builder too. Dependency caching is done the portable
# way instead: resolve against pom.xml in its own layer, then copy sources.

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn -B --no-transfer-progress clean package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build --chown=app:app /build/target/*.jar app.jar

USER app
EXPOSE 8080

# Respect the container's cgroup memory limit rather than the host's total RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
