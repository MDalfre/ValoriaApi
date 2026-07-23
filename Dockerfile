FROM gradle:9.0-jdk21-alpine AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon clean bootJar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache postgresql18-client
WORKDIR /app
RUN addgroup -S valoria && adduser -S valoria -G valoria \
    && mkdir -p /backups /restore-staging \
    && chown -R valoria:valoria /app /backups /restore-staging
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER valoria
EXPOSE 8090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

