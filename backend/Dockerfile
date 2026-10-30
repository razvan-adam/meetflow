# Backend image for staging and production (docs/deployment.md). Build the jar first with
# `./mvnw package`; the image only copies it, so CI tests and ships the same jar.
FROM eclipse-temurin:25-jre

RUN groupadd --system app && useradd --system --gid app --no-create-home app

WORKDIR /app
COPY target/meetflow-*.jar app.jar

# Size the heap from the container's memory limit rather than the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
