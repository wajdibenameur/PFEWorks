FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV ML_TORCHSCRIPT_ENABLED=false

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8099

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
