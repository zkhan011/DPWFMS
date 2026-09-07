FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 fms
WORKDIR /app
COPY --from=build /src/fms-api/target/fms-api-*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
