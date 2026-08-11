FROM gradle:8.11.1-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle :backend:installDist --no-daemon

# Used by CI (deploy-backend.yml builds with `target: from-artifact`) -- skips the in-container Gradle build
# above entirely, using the distribution already built natively in the `test` job and downloaded as a build
# artifact. Has no RUN instructions, so building this stage for arm64 on an amd64 runner needs no QEMU emulation,
# unlike the `build` stage above would. Not reachable without an explicit --target, so it's never used by
# docker-compose.yml/local dev, which doesn't have that prebuilt artifact sitting around.
FROM eclipse-temurin:21-jre-alpine AS from-artifact
WORKDIR /app
COPY backend/build/install/backend .
EXPOSE 8080
CMD ["bin/backend"]

# Default target (docker-compose.yml, local `docker compose up --build`) -- builds from source inside the
# container via the `build` stage above, so it works standalone with nothing but Docker itself, no local
# JDK/Gradle needed.
FROM eclipse-temurin:21-jre-alpine AS from-source
WORKDIR /app
COPY --from=build /app/backend/build/install/backend .
EXPOSE 8080
CMD ["bin/backend"]
