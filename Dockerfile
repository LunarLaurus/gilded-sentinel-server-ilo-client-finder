FROM eclipse-temurin:21.0.4_7-jdk
WORKDIR /gilded-sentinel-server
COPY target/*.jar gilded-sentinel-server.jar
EXPOSE 32500
EXPOSE 32550
ENTRYPOINT ["java", "-jar", "gilded-sentinel-server.jar"]
