FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /build
COPY backend-api/pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY backend-api/src ./src
RUN mvn -B -DskipTests package

FROM maven:3.9.9-eclipse-temurin-11 AS etl-build
WORKDIR /build
COPY spark-etl/pom.xml .
RUN mvn -B -P spark-runtime -DskipTests dependency:go-offline
COPY spark-etl/src ./src
RUN mvn -B -P spark-runtime -DskipTests package

FROM apache/spark:3.5.9-scala2.12-java11-python3-r-ubuntu AS spark-runtime

FROM eclipse-temurin:11-jre-jammy
COPY --from=spark-runtime /opt/spark /opt/spark
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /build/target/backend-api-1.0-SNAPSHOT.jar app.jar
COPY --from=etl-build /build/target/spark-etl.jar /opt/spark-jobs/spark-etl.jar
ENV SPARK_HOME=/opt/spark
ENV PATH=/opt/spark/bin:$PATH
USER app
EXPOSE 8084
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
