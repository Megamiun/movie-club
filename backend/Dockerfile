FROM gradle:8.11.1-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle :backend:buildFatJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/backend/build/libs/backend-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
