FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /build
COPY backend-api/pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY backend-api/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:11-jre-jammy
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /build/target/backend-api-1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8084
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
