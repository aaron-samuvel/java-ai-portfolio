FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
COPY docker ./docker
RUN mvn -B -DskipTests package
RUN javac --release 21 docker/Healthcheck.java -d /workspace/healthcheck

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser && mkdir -p /app/data/documind/uploads \
    && chown -R appuser:appuser /app
COPY --from=build /workspace/target/documind-0.1.0-SNAPSHOT.jar /app/app.jar
COPY --from=build /workspace/healthcheck/Healthcheck.class /app/healthcheck/Healthcheck.class
USER appuser
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD ["java", "-cp", "/app/healthcheck", "Healthcheck"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

