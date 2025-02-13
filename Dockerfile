FROM gradle:8.12.1-jdk17-alpine as build
COPY gradle /app/gradle/
COPY src /app/src/
COPY config /app/config/
COPY build.gradle settings.gradle gradle.properties dependencies.gradle /app/
RUN cd /app && gradle -Dorg.gradle.welcome=never --no-daemon shadowJar

FROM eclipse-temurin:17-jdk
COPY --from=build /app/build/libs/micrometer-release.jar /opt/action/micrometer-release.jar
# Set JAVA_HOME explicitly to match the container's Java location
ENV JAVA_HOME=/opt/java/openjdk
RUN (type -p wget >/dev/null || (apt update && apt-get install wget -y)) \
	&& mkdir -p -m 755 /etc/apt/keyrings \
        && out=$(mktemp) && wget -nv -O$out https://cli.github.com/packages/githubcli-archive-keyring.gpg \
        && cat $out | tee /etc/apt/keyrings/githubcli-archive-keyring.gpg > /dev/null \
	&& chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg \
	&& echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | tee /etc/apt/sources.list.d/github-cli.list > /dev/null \
	&& apt update \
	&& apt install gh -y
ENTRYPOINT ["java", "-jar", "/opt/action/micrometer-release.jar"]
