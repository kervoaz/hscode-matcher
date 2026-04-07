# Image Spring Boot pour Azure (Container Apps, Web App for Containers, AKS, etc.)
# Prérequis : si vous utilisez ONNX embarqué, placez le fichier .onnx dans
# src/main/resources/onnx/ avant le build (non versionné par défaut — voir DEPLOY-AZURE.md).

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./
COPY src ./src
RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/target/hscode-matcher-api-*.jar /app/app.jar
RUN chown app:app /app/app.jar
USER app
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=azure
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
