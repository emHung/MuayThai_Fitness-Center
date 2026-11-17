FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/ .
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -q package

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
WORKDIR /app
COPY --from=build /build/${SERVICE}/target/service.jar service.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "service.jar"]
