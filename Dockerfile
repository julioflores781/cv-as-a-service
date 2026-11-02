# Empaqueta el artefacto que YA pasó las pruebas (infrastructure/target/quarkus-app); no recompila.
# Así la imagen que se publica contiene exactamente lo que se probó.
#
# En local:   mvn -B package -DskipTests && docker build -t cv-api .
#             docker run --rm -p 8080:8080 cv-api   y   scripts/smoke-test.sh
FROM eclipse-temurin:21-jre

RUN groupadd --system app && useradd --system --gid app --no-create-home app

WORKDIR /app
COPY --chown=app:app infrastructure/target/quarkus-app/ ./

USER app
EXPOSE 8080

# Forma exec (sin shell): la JVM recibe SIGTERM y Quarkus cierra de forma ordenada.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "quarkus-run.jar"]
