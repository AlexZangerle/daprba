FROM gradle:8.14-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle jar --no-daemon

FROM eclipse-temurin:21-jre-alpine
COPY --from=build /app/build/libs/*.jar /opt/simulation.jar
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "/opt/simulation.jar"]
