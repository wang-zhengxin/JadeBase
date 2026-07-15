FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S jadebase && adduser -S jadebase -G jadebase
WORKDIR /app
COPY --from=build /workspace/target/jadebase-*.jar app.jar
USER jadebase
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
