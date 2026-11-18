FROM maven:3.10-eclipse-temurin-25 AS build
# CVE-2026-114938: disable the affected intrinsic until patched Temurin images ship.
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:25-jre
# CVE-2026-114938: disable the affected intrinsic until patched Temurin images ship.
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"
WORKDIR /app
COPY --from=build /workspace/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/target/quarkus-app/quarkus/ /app/quarkus/
USER 10001:10001
EXPOSE 8443
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
