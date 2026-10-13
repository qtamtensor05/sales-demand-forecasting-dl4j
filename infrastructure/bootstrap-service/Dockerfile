FROM eclipse-temurin:11-jdk-jammy AS build
WORKDIR /src
COPY SimpleHealthServer.java .
RUN javac SimpleHealthServer.java

FROM eclipse-temurin:11-jre-jammy
ARG SERVICE_NAME=bootstrap-service
ENV SERVICE_NAME=${SERVICE_NAME} SERVER_PORT=8080
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/SimpleHealthServer.class .
USER 10001
ENTRYPOINT ["java", "SimpleHealthServer"]

