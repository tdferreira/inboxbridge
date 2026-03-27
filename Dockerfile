FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/target/quarkus-app/quarkus/ /app/quarkus/
EXPOSE 8080 8443
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
