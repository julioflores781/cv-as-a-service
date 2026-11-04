#!/usr/bin/env bash
# Copia de seguridad de la base de datos (pg_dump comprimido). Uso: deploy/backup-db.sh [DIRECTORIO]  (por defecto deploy/backups)
# Conserva las 14 últimas. Pensado para cron en el NAS:  0 3 * * *  /ruta/deploy/backup-db.sh
set -euo pipefail
dir="${1:-$(dirname "$0")/backups}"
mkdir -p "$dir"
file="$dir/cv-$(date +%Y%m%d-%H%M%S).sql.gz"
# Se vuelca a un temporal y solo se publica si pg_dump terminó bien: un fallo nunca deja una copia vacía que cuente en la rotación.
trap 'rm -f "$file.tmp"' EXIT
docker exec cv-db pg_dump -U cv -d cv --no-owner | gzip > "$file.tmp"
mv "$file.tmp" "$file"
ls -1t "$dir"/cv-*.sql.gz | tail -n +15 | xargs -r rm --
echo "✅ $file"
