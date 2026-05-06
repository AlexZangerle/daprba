FROM gradle:8.14-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle jar --no-daemon

FROM eclipse-temurin:21-jre-alpine
COPY --from=build /app/build/libs/*.jar /opt/building-service.jar
EXPOSE 8005
ENTRYPOINT ["java", "-jar", "/opt/building-service.jar"]
