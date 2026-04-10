# Image runtime : le contexte de build doit contenir app.jar (voir azure-pipelines/azure-pipelines.yml, stage Container).
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app

COPY app.jar app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
