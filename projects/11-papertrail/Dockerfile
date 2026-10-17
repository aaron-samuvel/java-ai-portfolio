FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 appuser
COPY --from=build /workspace/target/papertrail-0.1.0-SNAPSHOT.jar /app/app.jar
USER appuser
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
