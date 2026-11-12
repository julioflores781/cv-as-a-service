#!/usr/bin/env bash
# Alta/cambio de la contraseña del administrador, desbloqueo y estado. Se ejecuta DENTRO del contenedor de la API
# (usa su QUARKUS_DATASOURCE_* y el mismo Argon2 que el login), sin publicar nada ni dejar la contraseña en el historial:
# se escribe en el terminal, sin eco y con confirmación.
#
# Uso:   ./admin-password.sh set-password | unlock | status
# Otro contenedor:   CV_API_CONTAINER=mi-api ./admin-password.sh status
#
# Hace falta terminal (docker exec -it). El classpath es el del fast-jar del Dockerfile (WORKDIR /app).
set -euo pipefail

CONTAINER="${CV_API_CONTAINER:-cv-api}"

exec docker exec -it "$CONTAINER" java -XX:MaxRAMPercentage=50 -XX:TieredStopAtLevel=1 \
  -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  -cp "app/*:lib/main/*:lib/boot/*" \
  dev.julioflores.cv.infrastructure.tools.AdminPasswordTool "$@"
