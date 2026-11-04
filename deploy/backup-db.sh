#!/usr/bin/env bash
# Copia de seguridad de la base de datos (pg_dump comprimido). Uso: deploy/backup-db.sh [DIRECTORIO]  (por defecto ./backups)
# Conserva las 14 últimas. Pensado para cron en el NAS:  0 3 * * *  /ruta/deploy/backup-db.sh
set -euo pipefail
cd "$(dirname "$0")"
dir="${1:-backups}"
mkdir -p "$dir"
file="$dir/cv-$(date +%Y%m%d-%H%M%S).sql.gz"
docker compose exec -T db pg_dump -U cv -d cv --no-owner | gzip > "$file"
ls -1t "$dir"/cv-*.sql.gz | tail -n +15 | xargs -r rm --
echo "✅ $file"
