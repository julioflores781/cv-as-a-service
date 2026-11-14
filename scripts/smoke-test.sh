#!/usr/bin/env bash
# Comprueba que una instancia en marcha responde lo esencial.
# Uso: scripts/smoke-test.sh [URL_BASE]      (por defecto http://localhost:8080)
# En el pipeline se ejecuta contra el contenedor ANTES de publicar la imagen.
# Espera el CV ficticio de db/sample (Ada Ejemplo): aplícalo a la base antes de lanzarlo.
# Y que la instancia se haya arrancado con el admin activo y la URL pública de las imágenes (valores de prueba, no secretos):
#   CV_ADMIN_JWT_SECRET (≥ 32 caracteres), CV_ADMIN_EDGE_BLOCK=true y R2_PUBLIC_URL=https://media.example.test
# Sin el secreto el admin queda deshabilitado (404 en /api/v1/admin) y la comprobación del 401 falla a propósito.
set -euo pipefail

BASE="${1:-http://localhost:8080}"

fail() { echo "❌ $*" >&2; exit 1; }

for attempt in $(seq 1 60); do
  curl -fsS "$BASE/q/health/ready" >/dev/null 2>&1 && break
  [ "$attempt" -eq 60 ] && fail "la aplicación no está lista tras 60 s ($BASE/q/health/ready)"
  sleep 1
done
echo "✅ readiness OK"

# expect URL FRAGMENTO [ESTADO_HTTP]: el cuerpo debe contener FRAGMENTO y el estado ser el esperado.
expect() {
  local url=$1 fragment=$2 status=${3:-200} out body code
  out=$(curl -sS -w $'\n%{http_code}' "$BASE$url") || fail "$url no responde"
  code=${out##*$'\n'}
  body=${out%$'\n'*}
  [ "$code" = "$status" ] || fail "$url devolvió $code (esperado $status): $body"
  grep -qF -- "$fragment" <<<"$body" || fail "$url no contiene '$fragment': $body"
  echo "✅ $url -> $code"
}

expect "/api/v1/profile"                         "Ada Ejemplo"
expect "/api/v1/profile?lang=en"                 "backend focus"
expect "/api/v1/experience"                      "acme-corp"
expect "/api/v1/skills?group=backend"            "Quarkus"
expect "/api/v1/resume"                          '"experience"'
expect "/api/v1/experience/nope"                 "EXPERIENCE_NOT_FOUND" 404
expect "/api/v1/experience/nope?lang=en"         "Experience not found" 404

# Trazabilidad: toda respuesta lleva X-Request-Id. Sin cabecera de entrada la API genera uno de 32 hex; con uno válido
# (^[A-Za-z0-9._-]{8,64}$) devuelve el mismo; con uno inválido lo sustituye y nunca lo refleja.
# request_id_of URL [cabecera...]: imprime el valor de X-Request-Id de la respuesta (vacío si falta).
request_id_of() {
  local url=$1; shift
  curl -fsS -D - -o /dev/null "$@" "$BASE$url" | tr -d '\r' | awk -F': *' 'tolower($1) == "x-request-id" { print $2; exit }'
}

generated=$(request_id_of "/api/v1/profile") || fail "/api/v1/profile no responde"
[[ "$generated" =~ ^[0-9a-f]{32}$ ]] || fail "X-Request-Id generado inválido o ausente en /api/v1/profile: '$generated'"
echo "✅ X-Request-Id generado ($generated)"

echoed=$(request_id_of "/api/v1/profile" -H "X-Request-Id: smoke-test-0001") || fail "/api/v1/profile no responde"
[ "$echoed" = "smoke-test-0001" ] || fail "X-Request-Id válido no devuelto tal cual: '$echoed'"
echo "✅ X-Request-Id válido devuelto igual"

replaced=$(request_id_of "/api/v1/profile" -H "X-Request-Id: corto") || fail "/api/v1/profile no responde"
{ [[ "$replaced" =~ ^[0-9a-f]{32}$ ]] && [ "$replaced" != "corto" ]; } || fail "X-Request-Id inválido no sustituido: '$replaced'"
echo "✅ X-Request-Id inválido sustituido"

ready=$(request_id_of "/q/health/ready") || fail "/q/health/ready no responde"
[[ "$ready" =~ ^[0-9a-f]{32}$ ]] || fail "la respuesta de health no lleva X-Request-Id: '$ready'"
echo "✅ health responde 200 y lleva X-Request-Id"

# post URL CUERPO FRAGMENTO [ESTADO_HTTP]: igual que expect, pero con un POST text/plain.
post() {
  local url=$1 payload=$2 fragment=$3 status=${4:-200} ctype=${5:-text/plain} out body code
  out=$(curl -sS -w $'\n%{http_code}' -X POST -H "Content-Type: $ctype" --data-binary "$payload" "$BASE$url") \
    || fail "$url no responde"
  code=${out##*$'\n'}
  body=${out%$'\n'*}
  [ "$code" = "$status" ] || fail "POST $url devolvió $code (esperado $status): $body"
  grep -qF -- "$fragment" <<<"$body" || fail "POST $url no contiene '$fragment': $body"
  echo "✅ POST $url -> $code"
}

ADT=$'MSH|^~\\&|HIS|HOSPITAL|LAB|HOSPITAL|20260101120000||ADT^A01^ADT_A01|MSG1|P|2.5.1\nPID|1||12345^^^HOSP^MR||Perez^Juan||19800101|M'
post "/api/v1/hl7/translate"                     "$ADT"  '"familyName"'
post "/api/v1/hl7/translate?format=fhir"         "$ADT"  '"resourceType": "Bundle"'
post "/api/v1/hl7/translate"                     "hola"  "HL7_INVALID_MESSAGE" 422
# Contacto: solo el caso inválido, para no dejar mensajes en la base de datos.
post "/api/v1/contact/messages"                  '{"name":"","email":"x","message":"y"}' "CONTACT_INVALID" 400 application/json

expect "/api/v1/services"                        "Servicio de ejemplo"
expect "/api/v1/projects?lang=en"                "Sample anonymous project"
# El proyecto propio y público del ejemplo lleva una imagen: sale con la URL pública compuesta por el servidor.
expect "/api/v1/projects?lang=en"                "https://media.example.test/site/projects/00000000-0000-4000-8000-000000000001.webp"
expect "/api/v1/architecture"                    "PostgreSQL"
expect "/openapi.json"                           '"openapi"'
expect "/swagger/"                               "OpenAPI UI"

# Administración: sin token todo es 401 con su `code` estable (el login es lo único abierto y no se prueba aquí: haría falta la contraseña).
expect "/api/v1/admin/auth/session"              "ADMIN_UNAUTHORIZED" 401
expect "/api/v1/admin/content/services"          "ADMIN_UNAUTHORIZED" 401
expect "/api/v1/admin/images"                    "ADMIN_UNAUTHORIZED" 401

# Con CV_ADMIN_EDGE_BLOCK=true, lo que llegue con cabeceras de Cloudflare (CF-Ray) ni siquiera llega a pedir token: 404.
edge=$(curl -sS -o /dev/null -w '%{http_code}' -H 'CF-Ray: 0000000000000000-MAD' "$BASE/api/v1/admin/auth/session") \
  || fail "/api/v1/admin/auth/session no responde"
[ "$edge" = "404" ] || fail "/api/v1/admin con CF-Ray devolvió $edge (esperado 404: ¿falta CV_ADMIN_EDGE_BLOCK=true?)"
echo "✅ /api/v1/admin con CF-Ray -> 404"

echo "🎉 smoke test completo"
