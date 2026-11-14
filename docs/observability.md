# Observabilidad: logs de `cv-api` y `cv-web`

Guía para leer y entender los logs del stack del NAS (`cv-db`, `cv-api`, `cv-web`). Todo sale por la consola del contenedor (stdout/stderr) y Docker lo guarda
y lo rota. No hay métricas ni tabla de auditoría (ver [límites](#7-límites-conocidos)).

Los ejemplos son ficticios (ids, IP de documentación `203.0.113.x`/`198.51.100.x`), pero tienen el formato real.

## 1. Cómo leerlos en el NAS

- **Portainer:** Containers → `cv-api` (o `cv-web`) → **Logs** (activa *Auto-refresh* y, si quieres, *Timestamps*; el buscador filtra por texto).
- **Terminal:**
  ```bash
  docker logs -f --tail 200 cv-api                       # seguir en vivo
  docker logs cv-api 2>&1 | grep '"logger":"audit"'      # solo auditoría de la API
  docker logs cv-api 2>&1 | grep '"logger":"access"'     # solo access log de la API
  docker logs cv-web 2>&1 | grep '"event":"contact"'     # solo el formulario de contacto de la web
  docker logs --since 1h cv-api 2>&1 | grep -E 'LOGIN_FAILED|LOGIN_LOCKED'
  ```
  `2>&1` hace falta porque Docker separa stdout y stderr y `grep` solo lee stdout.
- **Buscar por `request_id` entre la web y la API** (la web genera un id por petición, lo envía a la API en `X-Request-Id` y lo reutiliza en los reintentos):
  ```bash
  ID=914b2d5af6a145eb96f720cda52d507f
  docker logs cv-web 2>&1 | grep "$ID"
  docker logs cv-api 2>&1 | grep "$ID"
  ```
  Una petición del navegador a la API devuelve la cabecera `X-Request-Id`; para saber el id de una petición concreta, míralo en la respuesta (`curl -i`).

## 2. Formato

- **`cv-api` en producción:** una línea JSON por registro, con estos campos: `timestamp`, `logger` (categoría o clase), `level`, `message`, `threadName`, `service` (`cv-api`),
  `request_id` (si hay petición), `client_ip` (solo admin, ver §3.1), `error_code` (solo respuestas de problema) y, en errores con traza, `stackTrace`.
  Se omiten `sequence`, `loggerClassName`, `processName`, `processId`, `threadId`, `hostName` y `ndc`.
- **`cv-api` en desarrollo y tests:** texto, `HH:mm:ss.SSS NIVEL [request_id] logger mensaje` (el `[request_id]` va vacío fuera de una petición).
- **`cv-web`:** una línea JSON por evento: `timestamp`, `level` (`info`/`warn`/`error`), `service` (`cv-web`), `event` y campos propios del evento. INFO por stdout, WARN y ERROR por stderr.
  La única salida es `lib/log.ts`.

Qué ve cada herramienta: Portainer muestra la línea tal cual; `docker logs` también. Un 500 de la API con traza ocupa una línea larga (la traza va en `stackTrace`).

## 3. `cv-api`

### 3.1 Access log (`"logger":"access"`)

Una línea por petición HTTP, incluidos los 404 de enrutado y el 413 de un cuerpo demasiado grande. El `message` es:

```
MÉTODO RUTA ESTADO DURACIÓNms BYTESb rid=<request_id> err=<error_code o -> ip=<IP o ->
```

```json
{"timestamp":"2026-11-12T17:41:47.064536+01:00","logger":"access","level":"INFO","message":"GET /api/v1/profile 200 95ms 278b rid=25c9d82645804c6c9d976a780ca9fcbc err=- ip=-","threadName":"executor-thread-1","request_id":"25c9d82645804c6c9d976a780ca9fcbc","service":"cv-api"}
```

Dentro del `message` (los ejemplos son solo esa parte):

```
GET /api/v1/nada 404 12ms 123b rid=b7c3… err=NOT_FOUND ip=-
POST /api/v1/hl7/translate 413 0ms 0b rid=c296… err=- ip=-
POST /api/v1/admin/auth/login 401 112ms 147b rid=124d… err=ADMIN_INVALID_CREDENTIALS ip=2001:db8::7
POST /api/v1/admin/auth/login 404 1ms 123b rid=b8c1… err=NOT_FOUND ip=203.0.113.9
```

- **`rid=`**: el `request_id` (32 hex generado, o el `X-Request-Id` del cliente si cumple `^[A-Za-z0-9._-]{8,64}$`; uno inválido se sustituye y nunca se refleja).
  Es el mismo valor que lleva la respuesta en la cabecera `X-Request-Id` (también en los 404 y el 413).
- **`err=`**: el `code` estable del error (`EXPERIENCE_NOT_FOUND`, `ADMIN_UNAUTHORIZED`, `CONTACT_RATE_LIMITED`, `UNEXPECTED`…); `-` si la respuesta no es un problema.
  Los 404 de rutas inexistentes salen como `NOT_FOUND`. Un 404 de `/api/v1/admin` con `CV_ADMIN_EDGE_BLOCK=true` (sonda desde el túnel) sale también así y con su IP.
- **`ip=`**: solo en rutas `/api/v1/admin`, tomada de `CF-Connecting-IP` y, si falta o no es una IP válida, de `X-Client-IP`. En rutas públicas es siempre `-`.
- Duración en ms (`%D`) y bytes de la respuesta (`%B`).
- **No genera línea:** `/q/health/*`, `/swagger*`, `/q/swagger-ui*` y `/openapi.json` (el healthcheck de Docker no ensucia el log).

### 3.2 Auditoría (`"logger":"audit"`)

Una línea por acción del administrador, después de que la operación tuvo éxito o, en los fallos de seguridad, justo antes de responder. El `message` es
`action=<ACCIÓN> outcome=<success|failure> clave=valor…` con los detalles **ordenados por clave** (por eso `en_version` va antes que `es_version`). Éxito = INFO; fallo de seguridad = WARN.
`request_id` y `client_ip` salen como campos del JSON (en el formato de texto de dev el `client_ip` no se muestra, el `ip=` está en la línea de access).

```json
{"timestamp":"2026-11-12T18:27:03.464684+01:00","logger":"audit","level":"INFO","message":"action=LOGIN_SUCCESS outcome=success","threadName":"executor-thread-1","request_id":"107cbfd7d7b84da5a3a23eae804dee89","client_ip":"198.51.100.77","service":"cv-api"}
```

Dentro del `message`:

```
INFO  action=LOGIN_SUCCESS outcome=success
WARN  action=LOGIN_FAILED outcome=failure reason=bad_credentials
WARN  action=LOGIN_LOCKED outcome=failure retry_after_s=899
INFO  action=CONTENT_SAVED outcome=success comment_length=32 comment_present=true en_version=2 es_version=2 kind=services
INFO  action=CONTENT_ROLLED_BACK outcome=success from_version=2 kind=services lang=es to_version=1
INFO  action=IMAGE_UPLOADED outcome=success bytes=149 content_type=image/png height=48 key=site/projects/dafed7e9-7cb5-4e05-ae37-c72e04e627a5.png width=64
INFO  action=IMAGES_CLEANUP outcome=success deleted=0 dry_run=true kept=1 older_than_hours=24 would_delete=0
INFO  action=MESSAGES_READ outcome=success limit=10 paged=false returned=1
```

| `action` | `outcome` | Campos | Qué significa |
|---|---|---|---|
| `LOGIN_SUCCESS` | success | – | login correcto |
| `LOGIN_FAILED` | failure | `reason=bad_credentials` o `no_account` | contraseña errónea, o no hay cuenta creada |
| `LOGIN_LOCKED` | failure | `retry_after_s` | cuenta bloqueada (5 fallos = 15 min); segundos que faltan |
| `LOGIN_RATE_LIMITED` | failure | – | límite global (10 intentos por minuto) |
| `PASSWORD_CHANGED` | success | – | contraseña cambiada (cierra todas las sesiones) |
| `PASSWORD_CHANGE_FAILED` | failure | `reason=wrong_current`, `locked` o `weak` | actual incorrecta, cuenta bloqueada o contraseña nueva débil |
| `SESSIONS_REVOKED` | success | – | `logout-all` |
| `CONTENT_SAVED` | success | `kind`, `es_version`, `en_version`, `comment_present`, `comment_length` | contenido publicado; si la relectura posterior falla, sin las versiones |
| `CONTENT_ROLLED_BACK` | success | `kind`, `lang`, `from_version`, `to_version` | vuelta a una versión anterior |
| `IMAGE_UPLOADED` | success | `key`, `content_type`, `bytes`, `width`, `height` | imagen subida a R2 |
| `IMAGE_DELETED` | success | `id` | imagen borrada |
| `IMAGES_CLEANUP` | success | `dry_run`, `older_than_hours`, `deleted`, `would_delete`, `kept` | limpieza de huérfanas (`older_than_hours` es la gracia efectiva, mínimo 1) |
| `MESSAGES_READ` | success | `limit`, `returned`, `paged` | lectura de los mensajes de contacto |

**No se audita:** las lecturas de contenido, versiones, sesión o imágenes; los fallos de validación, conflicto (`CONTENT_VERSION_CONFLICT`) o not-found del contenido y de las imágenes
(esos quedan en el access log con `err=`); un 401 por token (`ADMIN_UNAUTHORIZED`); y el 404 del edge-guard con `CV_ADMIN_EDGE_BLOCK=true` (solo access).

### 3.3 Eventos de negocio y errores

Los emiten clases concretas, así que `logger` es el nombre completo de la clase (`dev.julioflores.cv.infrastructure.adapter.out…`). Llevan `request_id` y nunca IP en rutas públicas.

| Nivel | Clase | `message` |
|---|---|---|
| INFO | `PostgresContactMessageRepository` | `Contact message stored id=41` (solo el id; un honeypot, un 400 o un 429 **no** dejan esta línea) |
| INFO | `TelegramContactNotifier` | `Telegram notification sent` (el `request_id` es el del mensaje aunque salga de otro hilo) |
| WARN | `TelegramContactNotifier` | `Telegram notification rejected with HTTP 401`, `Telegram notification failed: ConnectException`, `Telegram notification could not be sent: …` (sin URL ni token) |
| INFO | `PostgresSiteContentRepository` | `Content cache invalidated kind=services lang=es` (una por idioma al guardar; una al hacer rollback) |
| WARN | `PostgresResumeRepository`, `PostgresSiteContentRepository` | `Cannot refresh the 'es' resume (…); serving the last one read` (la BD falla y se sirve lo último leído) |
| WARN | `SaveEditableContentService`, `RollbackContentService` | `Saved services/es but could not invalidate its cache; it expires with its TTL: …` |
| ERROR | `ProblemExceptionMapper` | `Unexpected database error (SQLState=57P01, class=PSQLException)` si la causa es SQL/JDBC, sin mensaje ni traza; si no, `Unexpected error` con `stackTrace` |

```json
{"timestamp":"…","logger":"dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresContactMessageRepository","level":"INFO","message":"Contact message stored id=1","threadName":"executor-thread-1","request_id":"real-valid-0001","service":"cv-api"}
{"timestamp":"…","logger":"dev.julioflores.cv.infrastructure.adapter.out.telegram.TelegramContactNotifier","level":"INFO","message":"Telegram notification sent","threadName":"ForkJoinPool.commonPool-worker-1","request_id":"real-valid-0001","service":"cv-api"}
```

Un 500 deja dos líneas con el mismo `request_id`: el ERROR (sin `error_code`) y la línea de access con `err=UNEXPECTED`.

## 4. `cv-web`

Cada línea es un JSON con `timestamp`, `level`, `service:"cv-web"`, `event` y los campos de la tabla. Las rutas de la API se registran **sin query**.

| `event` | Nivel | Campos | Cuándo |
|---|---|---|---|
| `api_call` | warn | `request_id`, `path`, `outcome` (`http_error`, `timeout`, `unreachable`, `invalid_response`), `status`, `attempt`, `duration_ms` | un intento fallido de leer la API (uno por intento; los reintentos comparten `request_id`). Las lecturas correctas **no** se registran: ya las registra la API |
| `contact` | info o warn | `request_id`, `outcome`, `status`, `code`, `reason`, `duration_ms` | cada `POST /api/contact`. `outcome`: `sent` (201), `rejected` (la API respondió 400/429, con `code`), `invalid` (validación propia: 415, 413, JSON roto o campo inválido; `reason` = `content_type`, `too_large`, `malformed_body` o el nombre del campo), `upstream_error` (502), `timeout` y `unreachable` (WARN) |
| `health` | warn | `request_id`, `outcome:"degraded"`, `status:503` | `/api/health/` solo cuando la API no responde; sano = silencio |
| `render_error` | error | `route_path`, `route_type`, `method`, `path`, `digest`, `error` (solo la clase) | un error de render o de ruta; **sin `request_id`** y duplicado (mismo `digest`) |
| `pdf_render_failed` | warn | `lang`, `error` (clase) | el PDF del CV no se pudo generar (la ruta responde 404) |

```json
{"timestamp":"2026-11-12T18:47:17.952Z","level":"info","service":"cv-web","event":"contact","request_id":"1487cba8faf449cb96c6827de1d63075","outcome":"sent","status":201,"duration_ms":7}
{"timestamp":"2026-11-12T18:47:17.979Z","level":"info","service":"cv-web","event":"contact","request_id":"ae9cbae92c31412ab6d3695d5571589e","outcome":"rejected","status":429,"code":"CONTACT_RATE_LIMITED","duration_ms":1}
{"timestamp":"2026-11-12T18:47:18.008Z","level":"warn","service":"cv-web","event":"api_call","request_id":"914b2d5af6a145eb96f720cda52d507f","path":"/q/health/ready","outcome":"http_error","status":503,"attempt":1,"duration_ms":1}
{"timestamp":"2026-11-12T18:47:18.008Z","level":"warn","service":"cv-web","event":"health","request_id":"914b2d5af6a145eb96f720cda52d507f","outcome":"degraded","status":503}
{"timestamp":"2026-11-12T18:47:18.671Z","level":"error","service":"cv-web","event":"render_error","route_path":"/[[...slug]]","route_type":"render","method":"GET","path":"/","digest":"2800301975","error":"ResumeFetchError"}
```

Cuando una página da 500 porque la API no contesta: busca los `api_call` justo antes del `render_error` (se unen por el momento y la ruta, no por id) y sigue el `request_id` del
`api_call` en `cv-api`. Next imprime además por su cuenta el error de render (multilínea, no JSON); solo contiene el código del fallo (p. ej. `request failed (ECONNREFUSED)`), sin host ni URL.

La web sirve `X-Request-Id` en la respuesta de `POST /api/contact` (para cruzar un mensaje concreto con la API).

## 5. Qué NO se registra jamás

- **API, todas las categorías:** contraseñas (actual, nueva o errónea), tokens JWT, hashes, cabecera `Authorization`, cuerpos de petición o respuesta, texto del comentario de un guardado
  (solo `comment_present` y `comment_length`), nombre, correo y mensaje de los contactos, el token del bot de Telegram y su URL, la query string (`?lang=…`), `User-Agent`, `Referer` y cookies.
- **IP:** nunca en rutas públicas (aunque llegue `CF-Connecting-IP`); en `/api/v1/admin` solo si es una IP válida.
- **Errores de BD:** sin mensaje ni traza (los mensajes de PostgreSQL pueden traer la fila completa); solo `SQLState` y clase.
- **Web:** `name`, `email`, `message` y `website` del formulario, IP, `CF-Connecting-IP`, cookies, cabeceras, mensajes de error de causa (pueden llevar URLs), cuerpos de la API y la URL base de la API.
- Los valores de los eventos de auditoría se sanean: sin saltos de línea ni espacios y como máximo 120 caracteres, para que nadie pueda falsear una línea.

## 6. Qué hacer ante sospecha de fuerza bruta

1. Cuenta los fallos y mira desde qué IP:
   ```bash
   docker logs --since 24h cv-api 2>&1 | grep -E 'LOGIN_FAILED|LOGIN_LOCKED|LOGIN_RATE_LIMITED'
   ```
   Cinco `LOGIN_FAILED` seguidos y luego un `LOGIN_LOCKED retry_after_s=899` es el bloqueo por cuenta funcionando. `LOGIN_RATE_LIMITED` es el límite global (10 por minuto).
2. Mira la IP del campo `client_ip` de esas líneas y su `request_id`; en el access log (`grep <request_id>`) tienes la ruta, el estado y `ip=`. Una ráfaga de `POST /api/v1/admin/auth/login` con `ip=` de
   Cloudflare es alguien llegando por Internet: el login nunca debería estar expuesto (regla de Cloudflare sobre `/api/v1/admin/*`, [admin-runbook.md](admin-runbook.md)).
   Los 404 de `/api/v1/admin` con IP son el edge-guard parando sondas.
3. Cualquiera que llegue al login puede bloquear la cuenta 15 min. Si hace falta: `./admin-password.sh unlock`; si crees que hubo un acierto (`LOGIN_SUCCESS` que no reconoces),
   `./admin-password.sh set-password` (cierra todas las sesiones) y rota `CV_ADMIN_JWT_SECRET` (ver el runbook, §7).
4. Cierra el hueco en Cloudflare (bloqueo o regla de la IP) y no dependas del log: `LOGIN_FAILED` no dice cuántos fallos van; se deduce contando.

## 7. Límites conocidos

- **Rotación y retención:** el compose usa `json-file` con `max-size: "10m"` y `max-file: "5"` por contenedor (`cv-db`, `cv-api`, `cv-web`): como máximo 50 MB cada uno. Al llenarse se borra lo más antiguo, así
  que un audit de hace semanas puede haberse perdido (la retención depende del volumen de tráfico). No hay tabla de auditoría ni copia externa. Recrear el contenedor (`Redeploy` en Portainer) borra su log.
  Si necesitas guardar algo, copia las líneas antes: `docker logs cv-api 2>&1 | grep '"logger":"audit"' > audit.log`.
- **Sin log por visita en la web:** el servidor standalone de Next no da un gancho con estado y duración sin `proxy.ts`; las visitas se ven en el access log de la API (cada render lee la API)
  y en Cloudflare. `render_error` aparece dos veces por error y sin `request_id`.
- **IP del administrador:** `ip=`/`client_ip` sale de `CF-Connecting-IP` o `X-Client-IP`, cabeceras que fija quien llega al contenedor. Valen para investigar, no son prueba. Con `CV_ADMIN_EDGE_BLOCK=true`, lo que
  llega por el túnel recibe 404 (sin audit, solo access). Las llamadas del panel (`cv-web` → API por la red interna) salen con `ip=-` mientras el panel no envíe `X-Client-IP`.
- El `request_id` entrante (`X-Request-Id`) también lo puede fijar cualquiera: solo afecta a la fidelidad del log.
- **Métricas:** diferidas (sin Prometheus ni `/q/metrics`); la gestión de Quarkus sigue desactivada y el healthcheck va por el puerto 8080.
- Un 400 que rechaza el propio parser HTTP antes del enrutado no deja línea ni cabecera.
- Un fallo del propio logger de auditoría no rompe la operación: queda `audit log failed: <clase>` por stderr.
