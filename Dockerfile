FROM eclipse-temurin:21.0.6_7-jdk

WORKDIR /gilded-sentinel-service-ilo-finder

# Download OpenTelemetry Java Agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

# Copy project JAR
COPY target/*.jar gilded-sentinel-service-ilo-finder.jar

EXPOSE 32500
EXPOSE 32550

ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-jar", "gilded-sentinel-service-ilo-finder.jar"]
