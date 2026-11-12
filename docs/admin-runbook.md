# Administración: checklist de puesta en marcha

Pasos del dueño para activar la zona privada (`/api/v1/admin`) en el NAS. Lo que hace cada parte está en
[ARCHITECTURE.md](../ARCHITECTURE.md) (sección «Administración») y las variables en el [README](../README.md).
**El compose es el de la web: `deploy/docker-compose.yml` del repo `cv-web`** (el de este repo se retiró).

Orden obligatorio: 1 → 2 → 3 → 4. **No despliegues el admin sin el paso 2**: la API es pública (`cv-api.floreslab.dev`).

## 1. Crear el token de R2 propio de esta web

Solo si vas a subir imágenes. Sin él la API funciona igual y la subida responde `503 IMAGE_STORAGE_UNAVAILABLE`.

1. Cloudflare → R2 → *Manage API tokens* → *Create API token*.
2. Permiso **Object Read & Write**, limitado **solo al bucket** de las imágenes (no «todos los buckets»). Un token propio de esta web,
   distinto del de padelhub, para poder rotarlo sin afectar a la otra.
3. Anota: *Access Key ID*, *Secret Access Key* (solo se muestran una vez) y el endpoint `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`.
4. El bucket necesita un dominio público (p. ej. un dominio propio conectado al bucket): de ahí salen las `images[].url`.
   Las claves son `site/projects/<uuid>.<jpg|png|webp|gif>`; la API nunca toca nada fuera de `site/projects/`.

## 2. Bloquear `/api/v1/admin/*` en Cloudflare (R-1)

**Por qué:** la API es pública. Aunque el admin pide token, no hay motivo para que el login esté expuesto a Internet: el panel solo
llama a la API desde el servidor de `cv-web`, por la red interna del compose, y esa llamada no pasa por Cloudflare.

Cloudflare → el dominio `floreslab.dev` → Security → WAF → *Custom rules* → *Create rule*:

- Expresión: `(http.host eq "cv-api.floreslab.dev" and starts_with(lower(http.request.uri.path), "/api/v1/admin"))`
- Acción: **Block**.

(Alternativa equivalente: que el túnel no tenga ruta hacia `/api/v1/admin` para ese host.)

Defensa en profundidad, ya incluida: con `CV_ADMIN_EDGE_BLOCK: "true"` (**obligatorio** en el compose) la propia API responde
`404` a toda petición admin que traiga `CF-Ray` o `CF-Connecting-IP`, es decir, a todo lo que llegue por el túnel. No sustituye la regla:
si la variable se perdiera, solo quedaría el token. Swagger sigue mostrando las rutas admin (el código es público, ocultarlas no aporta).

## 3. Variables en el compose

En `cv-web/deploy/docker-compose.yml`, servicio `cv-api` (se pega tal cual en el editor del NAS; sin `.env`):

| Variable | Qué poner |
|---|---|
| `CV_ADMIN_JWT_SECRET` | `openssl rand -base64 48`. Mínimo 32 caracteres. El marcador del compose es más corto a propósito: sin cambiarlo, el admin queda deshabilitado |
| `CV_ADMIN_EDGE_BLOCK` | `"true"` (ya viene así; no lo quites) |
| `R2_ENDPOINT`, `R2_BUCKET_NAME` | los del paso 1 |
| `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | los del paso 1 (vacíos = subida deshabilitada) |
| `R2_PUBLIC_URL` | dominio público del bucket, **sin barra final**. Vacío: el GET público no devuelve imágenes |
| `CV_ADMIN_SESSION_LIFESPAN`, `CV_ADMIN_LOGIN_MAX_FAILURES`, `CV_ADMIN_LOGIN_LOCK` | opcionales (`PT8H`, `5`, `PT15M`) |

La contraseña del admin **no** va en el compose. No pongas límite de memoria al contenedor `cv-api` por debajo de 512 MB (Argon2 usa 64 MiB por hash).

## 4. Desplegar y crear la contraseña

1. Despliega el stack. Comprueba `docker logs cv-api`: no debe aparecer el aviso `Admin disabled`. Flyway aplica `V4` y `V5` solo.
2. En el NAS, con terminal (hace falta TTY, `docker exec -it`), desde una copia de `scripts/admin-password.sh`:

   ```bash
   ./admin-password.sh set-password     # pide la contraseña dos veces, sin eco; 12 a 128 caracteres
   ./admin-password.sh status           # existe la cuenta, bloqueada hasta cuándo, último cambio (nunca el hash)
   ```

   Otro nombre de contenedor: `CV_API_CONTAINER=nombre ./admin-password.sh status`. Equivale a ejecutar dentro del contenedor
   `java -cp "app/*:lib/main/*:lib/boot/*" dev.julioflores.cv.infrastructure.tools.AdminPasswordTool set-password`.

## 5. Prueba manual contra el bucket real de R2 (R-3, obligatoria)

La subida solo se probó contra MinIO. R2 puede comportarse distinto con los *checksums* del SDK de AWS. Desde un equipo que llegue a
la API (p. ej. el NAS; recuerda que desde fuera el paso 2 la bloquea), con `API=http://cv-api:8080` o `http://localhost:8085` si publicaste el puerto:

```bash
TOKEN=$(curl -s -X POST $API/api/v1/admin/auth/login -H 'Content-Type: application/json' -d '{"password":"…"}' | jq -r .accessToken)
# 1) Subir: 201 con key y url. Abre la url en el navegador y mira: curl -I <url> debe dar el Content-Type correcto.
curl -i -H "Authorization: Bearer $TOKEN" -F "file=@captura.png" $API/api/v1/admin/images
# 2) Listar (usa ListObjectsV2)
curl -H "Authorization: Bearer $TOKEN" $API/api/v1/admin/images
# 3) Asociar la key a un proyecto own + public (PUT /content/projects, ES y EN con la misma key, width y height) y mirar GET /api/v1/projects: images[0].url
# 4) DELETE /images/<uuid> de la usada -> 409 IMAGE_IN_USE; sube otra y DELETE -> 204
# 5) Limpieza: primero el ensayo y luego en serio (usa DeleteObjects)
curl -X POST -H "Authorization: Bearer $TOKEN" "$API/api/v1/admin/images/cleanup?dryRun=true&olderThanHours=1"
curl -X POST -H "Authorization: Bearer $TOKEN" "$API/api/v1/admin/images/cleanup?dryRun=false&olderThanHours=1"
```

Si algo da `503 IMAGE_STORAGE_UNAVAILABLE`, mira `docker logs cv-api` (la causa del SDK se registra) y prueba, en este orden
(los ajustes están en `infrastructure/.../config/R2ClientConfig.java`):

| Síntoma | Ajuste |
|---|---|
| 400/403 `SignatureDoesNotMatch` o `XAmzContentSHA256Mismatch` al subir | comprobar `chunkedEncodingEnabled(false)` y `pathStyleAccessEnabled(true)` (ya puestos); si persiste, cambiar el cliente HTTP / `Expect: 100-continue` |
| 400 `InvalidRequest`, `Unsupported header 'x-amz-checksum-*'` o 403 en subir/borrar | los checksums ya van en `WHEN_REQUIRED`; respaldo: variables de entorno `AWS_REQUEST_CHECKSUM_CALCULATION=when_required` y `AWS_RESPONSE_CHECKSUM_VALIDATION=when_required` en el servicio `cv-api` |
| `cleanup` falla pero borrar una imagen funciona | es `DeleteObjects`: sustituir `R2FileStorageAdapter.deleteAll` por un bucle de `delete` |
| 403 `AccessDenied` solo al listar o borrar | el token no tiene lectura y escritura sobre el bucket |
| tiempo agotado | subir `cv.r2.connect-timeout` / `cv.r2.request-timeout` (`PT5S` / `PT10S`) |
| `NoSuchBucket` / 404 | `R2_BUCKET_NAME` mal o endpoint de otra cuenta |

Cualquier cambio de código para esto es un slug nuevo; esta lista solo orienta el diagnóstico.

## 6. Comprobaciones tras desplegar

| Qué | Cómo | Esperado |
|---|---|---|
| Login | `POST /api/v1/admin/auth/login` con la contraseña | 200 con `accessToken` |
| Sesión | `GET /api/v1/admin/auth/session` con el token | 200 |
| Guardar | `PUT /api/v1/admin/content/services` con el documento de `GET` y las dos `baseVersions` | 200 con versiones nuevas |
| Lectura pública inmediata | `GET /api/v1/services` justo después | ya trae lo guardado (sin esperar 5 min) |
| Subida | paso 5 | 201 |
| 401 sin token | `GET /api/v1/admin/auth/session` sin cabecera | 401 `ADMIN_UNAUTHORIZED` |
| Bloqueo en Cloudflare | `curl -i https://cv-api.floreslab.dev/api/v1/admin/auth/session` | bloqueado por Cloudflare (no un JSON de la API). Si responde 404 JSON de la API, lo ha parado `CV_ADMIN_EDGE_BLOCK` pero falta la regla del paso 2 |
| Público intacto | `GET https://cv-api.floreslab.dev/api/v1/projects` | 200 con `Cache-Control: public, max-age=300` |

El smoke test (`scripts/smoke-test.sh`) hace las comprobaciones de 401 y de `CF-Ray` contra una instancia con el admin activo.

## 7. Rotación y recuperación

- **Contraseña olvidada o sospecha de filtración:** `./admin-password.sh set-password` (no pide la anterior). Sube `session_version`, así que
  **todos** los tokens emitidos dejan de valer, y quita cualquier bloqueo.
- **Cuenta bloqueada** (5 fallos seguidos = 15 min; cualquiera que llegue al login puede provocarlo): espera o `./admin-password.sh unlock`.
- **Cerrar sesiones sin cambiar la contraseña:** `POST /api/v1/admin/auth/logout-all` o cambiar la contraseña. Borrar la cookie del panel no invalida el token.
- **Rotar `CV_ADMIN_JWT_SECRET`:** cámbialo en el compose y redespliega `cv-api`; cierra todas las sesiones, no hay que tocar la contraseña.
- **Rotar el token de R2:** crea uno nuevo (paso 1), cambia `R2_ACCESS_KEY_ID` y `R2_SECRET_ACCESS_KEY`, redespliega y revoca el anterior. Las imágenes ya
  subidas siguen sirviéndose igual: la URL pública no depende del token.
- **Sospecha de que el secreto o el token se filtraron:** rota ambos y la contraseña; revisa en Cloudflare la actividad del token de R2.

## 8. Medir Argon2 y ajustar (R-12)

Por defecto la contraseña usa Argon2id con **64 MiB, 3 iteraciones y 1 hilo**. En un Mac medí ≈ 240 ms por hash en frío; en la CPU del NAS no está medido.
Mide con el login (con la contraseña **correcta**: una mala cuenta para el bloqueo):

```bash
for i in 1 2 3; do curl -s -o /dev/null -w '%{time_total}s\n' -X POST $API/api/v1/admin/auth/login -H 'Content-Type: application/json' -d '{"password":"…"}'; done
```

Si pasa de ~1 s, baja las iteraciones o la memoria con `CV_ADMIN_ARGON2_ITERATIONS` / `CV_ADMIN_ARGON2_MEMORY_KIB` (vienen comentadas en el compose);
si sobra margen, súbelas. Los parámetros van dentro de cada hash: los antiguos siguen valiendo y, tras un login correcto, el hash se reescribe con los nuevos.
`admin-password.sh` lee las mismas variables. La API hace como mucho 2 hashes a la vez para limitar la memoria.
