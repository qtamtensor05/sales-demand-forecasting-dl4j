FROM eclipse-temurin:11-jre
WORKDIR /opt/app
COPY target/forecast-service.jar /opt/app/forecast-service.jar
EXPOSE 8083
ENTRYPOINT ["java", "-Xms512m", "-Xmx1536m", "-jar", "/opt/app/forecast-service.jar"]
