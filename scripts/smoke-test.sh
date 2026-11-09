#!/usr/bin/env bash
# Comprueba que una instancia en marcha responde lo esencial.
# Uso: scripts/smoke-test.sh [URL_BASE]      (por defecto http://localhost:8080)
# En el pipeline se ejecuta contra el contenedor ANTES de publicar la imagen.
# Espera el CV ficticio de db/sample (Ada Ejemplo): aplícalo a la base antes de lanzarlo.
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

expect "/openapi.json"                           '"openapi"'
expect "/swagger/"                               "OpenAPI UI"

echo "🎉 smoke test completo"
