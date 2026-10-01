# syntax=docker/dockerfile:1
# One Dockerfile for every service in the reactor:
#   docker build --build-arg SERVICE=ride-service -t rideshare/ride-service ./backend

FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY . .
# The cache mount keeps ~/.m2 between builds so dependencies download once.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${SERVICE} -am -DskipTests package \
 && cp ${SERVICE}/target/${SERVICE}-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
RUN groupadd --system spring && useradd --system --gid spring --uid 1001 spring
WORKDIR /app
# Least-frequently-changing layers first, so a code change only rebuilds the last layer.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER spring
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
