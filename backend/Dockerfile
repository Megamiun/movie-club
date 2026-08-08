FROM gradle:8.11.1-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle :backend:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/backend/build/install/backend .
EXPOSE 8080
CMD ["bin/backend"]
