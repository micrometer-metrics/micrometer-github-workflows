FROM gradle:8.5.0-jdk17-alpine as build
COPY gradle /app/gradle/
COPY src /app/src/
COPY config /app/config/
COPY build.gradle settings.gradle gradle.properties dependencies.gradle /app/
RUN cd /app && gradle -Dorg.gradle.welcome=never --no-daemon shadowJar

FROM ghcr.io/bell-sw/liberica-openjre-debian:17.0.10-13
COPY --from=build /app/build/libs/micrometer-release.jar /opt/action/micrometer-release.jar
ENTRYPOINT ["java", "-jar", "/opt/action/micrometer-release.jar"]
