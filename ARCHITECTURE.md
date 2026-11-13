# Arquitectura: CV como API

El CV (ES/EN) vive en PostgreSQL como documento JSON versionado; servicios, proyectos y arquitectura también, y estos se editan desde una zona de administración (sección 7). El dominio no conoce Quarkus, y cada salida (JSON, PDF, `llms.txt`) es un adaptador sobre el mismo modelo.

## 1. Contrato de la API (`/api/v1`)

El idioma se elige con `?lang=es|en`. Si falta, se usa `Accept-Language`, y si tampoco hay, `es`.

| Método | Ruta | Qué devuelve |
|---|---|---|
| GET | `/profile` | titular, resumen, ubicación y disponibilidad |
| GET | `/impact` | cifras de impacto |
| GET | `/experience` y `/experience/{id}` | trayectoria con fases y logros |
| GET | `/skills?group=backend` | stack agrupado |
| GET | `/education` | formación, certificaciones y cursos |
| GET | `/contact` | email y perfiles |
| GET | `/services`, `/projects`, `/architecture` | contenido editable de la web (precios, proyectos con `disclosure`, hexágono de puertos y adaptadores). **Implementado** |
| GET | `/resume` | CV completo en un solo documento (esquema propio; el formato JSON Resume estándar llegará como `?format=jsonresume` con las exportaciones) |
| GET | `/resume.pdf` | PDF apto para ATS |
| POST | `/hl7/translate?format=json\|fhir` | HL7 v2.5.1 (text/plain, `ADT^A01` y `ORU^R01`) → JSON o Bundle FHIR R4. **Implementado** |
| GET | `/projects` | además, en proyectos `own` + `public`, `images[]` (`url`, `alt`, `width`, `height`); en los demás, `[]` |
| POST | `/contact/messages` | guarda un mensaje (JSON `name`, `email`, `message` y el campo trampa `website`) y avisa por Telegram; límite de 5 por remitente y hora. **Implementado** |
| * | `/admin/**` | zona privada con sesión (JWT): auth, contenido versionado, imágenes en R2 y mensajes. Ver sección 7 |
| GET | `/status` | uptime real, versión y commit |
| GET | `/health/live`, `/health/ready` | sondas de salud |

Rutas fuera del prefijo: `/openapi.json`, `/swagger`, `/llms.txt`.

Convenciones:
- **Errores** siempre en RFC 7807 (`application/problem+json`) con `type`, `title`, `status`, `detail` y `code`.
  - `code` es un identificador **estable** (`EXPERIENCE_NOT_FOUND`, `NOT_FOUND`, `UNEXPECTED`…) que no cambia con el idioma. Es lo que debe usar un cliente para decidir.
  - `title` y `detail` van en el idioma de la petición (`?lang` > `Accept-Language` > español por defecto), sacados de `problems_es.properties` y `problems_en.properties`.
  - Los errores de negocio se lanzan como `ApplicationException(ErrorCode, mensajeEnInglés, args…)` desde `application`; esa capa no conoce HTTP ni textos de usuario. El mensaje en inglés es solo para logs.
  - Un único `ProblemExceptionMapper` (paquete `adapter.in.rest.error`) los traduce; `ApiError` es la tabla única código → estado HTTP. Los fallos inesperados (`500`) se registran con su traza y el cliente solo recibe un mensaje genérico.
  - **Añadir un error nuevo:** una constante en `ErrorCode` (si es de negocio) y en `ApiError`, y sus textos `CODIGO.title` / `CODIGO.detail` en los dos `.properties`. `ProblemCatalogTest` falla si falta algo. Los apóstrofos en los `.properties` se duplican (`''`) por `MessageFormat`.
- **Documentación OpenAPI** completa: cada operación con descripción, parámetros, y todas sus salidas (200, 404 y 500) con ejemplos reales; esquemas con descripción por campo. `OpenApiDocumentTest` falla si un endpoint queda sin documentar.
- **Trazabilidad** (guía de uso en [docs/observability.md](docs/observability.md)): toda respuesta lleva `X-Request-Id` (el del cliente si cumple `^[A-Za-z0-9._-]{8,64}$`; si no, uno generado) y cada petición deja una línea de access log (`GET /ruta 200 12ms 534b rid=… err=… ip=…`, sin query ni cabeceras; la IP solo en `/api/v1/admin`, de `CF-Connecting-IP` o `X-Client-IP` si son IP válidas).
- **Caché:** `Cache-Control: public, max-age=300` y `Vary: Accept-Language` en los GET con 200 (implementado). `ETag` pendiente.
- **CORS** limitado al dominio propio.
- **Datos públicos**, solo lectura. La escritura vive aparte, bajo `/api/v1/admin` (sección 7), con sesión; el **CV** (`resume`) sigue sin editarse por la API: se publica con `make-seed.py`.
- **Cuerpos:** el límite global de Quarkus es 6 MB (para la subida de imágenes) y `RequestBodyLimits` lo recorta a 128 KB (`cv.http.max-body-size`) en todo salvo `POST /api/v1/admin/images`, también con `Transfer-Encoding: chunked`.
- **HL7:** máximo 64 KB, sin guardar ni loguear el cuerpo, con aviso de "solo mensajes sintéticos".

## 2. Modelo hexagonal

```
                    ┌─────────────── adaptadores de entrada ──────────────┐
  Next.js / curl ─► │ REST (JAX-RS + OpenAPI)   Comando de importación    │
                    └──────────────┬──────────────────────────────────────┘
                                   ▼  puertos de entrada (casos de uso)
                    ┌──────────── aplicación ─────────────┐
                    │ GetProfile · GetExperience · GetSkills · ExportResume │
                    │ TranslateHl7 · SubmitContactMessage · GetSystemStatus │
                    │ ImportResume                                          │
                    └──────────────┬──────────────────────┘
                                   ▼
                    ┌───────────── dominio ──────────────┐
                    │ Resume (agregado) · Profile · WorkExperience · Phase │
                    │ SkillGroup · Impact · Education · Period · Language  │
                    │ Hl7Message · ContactMessage                          │
                    └──────────────┬──────────────────────┘
                                   ▼  puertos de salida
   ResumeRepository · ContactMessageRepository · Hl7Translator · ResumeRenderer
   ContactNotifier · RateLimiter · StatusProbe · Clock
                                   ▼
                    ┌──────────── adaptadores de salida ──────────────┐
                    │ Postgres (Panache + Flyway) · HAPI HL7/FHIR     │
                    │ PDF (Qute → HTML → PDF) · llms.txt (Qute)       │
                    │ Telegram (aviso) · RateLimiter en memoria       │
                    │ Uptime Kuma (estado)                            │
                    └─────────────────────────────────────────────────┘
```

Decisiones de diseño:
- **Maven multi-módulo:** `domain`, `application` e `infrastructure` (la app Quarkus). El compilador impide que el dominio importe Quarkus. Los módulos de librería necesitan Jandex para que Quarkus descubra sus beans.
- **`domain` y `application` sin dependencias de framework.** Los casos de uso se conectan con `@Produces` en infraestructura.
- **`ResumeRenderer` es un puerto con varios adaptadores:** JSON Resume, PDF y `llms.txt`.
- **`Hl7Translator` es otro puerto,** con un único adaptador HAPI (`adapter/out/hl7`). Lee el mensaje v2.5.1 a un modelo común (`ClinicalMessage`) del que salen la vista JSON y el Bundle FHIR, así el mapeo HL7 se escribe una sola vez. `Hl7Translation` lleva el documento ya serializado (JSON o FHIR): es una decisión consciente, porque un modelo clínico neutro en `application` añadiría tipos y mapeos sin lógica de negocio que lo justifique. El mapeo a FHIR es manual y acotado (PID→Patient, PV1→Encounter, OBX→Observation); el límite de 64 KB lo aplica el caso de uso y un tope duro de 128 KB el servidor.
- **ArchUnit en CI** (API principal con pruebas JUnit normales; `archunit-junit5` 1.5.1 no funciona con JUnit 6 de Quarkus 3.40): el dominio no depende de nada, la aplicación solo del dominio, y los adaptadores no se importan entre sí.

## 3. Datos en PostgreSQL

- `resume_version(id, version, lang, content jsonb, published_at, active)`: instantáneas inmutables por versión e idioma. El dominio las mapea al agregado `Resume`.
- `contact_message(id, name, email, body, created_at, ip_hash)`: la IP se guarda hasheada, nunca en claro. El admin lista los mensajes sin `ip_hash`.
- `admin_account(id, password_hash, session_version, failed_attempts, locked_until, password_changed_at, last_login_at, created_at)`: una sola fila (`id = 1`) (un único administrador), hash Argon2id en formato PHC (migración `V4`).
- `content_version(kind, lang, version, content jsonb, active, source, comment…)`: servicios, proyectos y arquitectura, versionados como el CV (un activo por tipo e idioma, rollback con `make-seed.py --rollback IDIOMA --kind TIPO` o desde el admin; `V5` añade `source` = `seed`|`admin` y `comment`). `PostgresSiteContentRepository` los cachea 5 min y, si la base de datos falla, sirve lo último leído. Un contenido sin publicar se sirve vacío, no como error. En proyectos `anonymous` el dominio descarta el nombre del cliente.
- `page_view_daily(day, path, count)`: contadores agregados, sin rastreo individual.

Flujo de publicación: el contenido real vive **fuera del repositorio** (`private/content/resume.{es,en}.json`). `scripts/make-seed.py` genera un SQL que desactiva la versión activa de cada idioma e inserta la siguiente; se aplica con `psql`. La API lee la versión activa por JDBC (`PostgresResumeRepository`) y la mantiene en memoria 5 minutos (`cv.resume.cache-ttl`); un CV no encontrado se recuerda solo 10 s. Decisión: documento JSONB versionado y no tablas normalizadas, porque nadie edita campos sueltos y así se reutiliza el mapper y se puede volver a una versión anterior (`make-seed.py --rollback <idioma>`). Si la base de datos falla al refrescar, se sigue sirviendo el último CV leído.

## 4. Pruebas

- **Dominio:** tests unitarios puros.
- **Casos de uso:** con puertos falsos (fakes), sin Quarkus.
- **Arquitectura:** ArchUnit.
- **REST:** RestAssured con Testcontainers (Postgres real).
- **Contrato:** snapshot de `/openapi.json` en CI. Si cambia sin querer, el build falla.

## 5. Cómo lo consume Next.js

- Durante el **build**, Next llama a `/api/v1/resume` y genera el HTML estático. La web funciona aunque el NAS esté apagado.
- La vista **API** de la web usa la API en vivo, con un aviso y datos embebidos como respaldo si no responde.

## 6. Decisiones

1. ~~Motor de PDF~~ **Decidido: openhtmltopdf** desde HTML de Qute, porque reutiliza el diseño en HTML.
2. ~~HL7~~ **Decidido: HAPI** (librería estándar del sector) en lugar de un parser propio.
3. ~~Maven multi-módulo o paquetes con ArchUnit~~ **Decidido: Maven multi-módulo** (`domain`, `application`, `infrastructure`), con ArchUnit para las reglas dentro de `infrastructure`.

## 7. Administración (`/api/v1/admin`)

Un único administrador, solo contraseña, sin roles. Los endpoints y las variables están en el [README](README.md); la puesta en marcha en
[docs/admin-runbook.md](docs/admin-runbook.md). Aquí, el diseño y sus trampas.

**Capas.** Casos de uso en `application` (`LoginAdmin`, `SaveEditableContent`, `RollbackContent`, `UploadProjectImage`, `ListContactMessages`…, uno por interfaz) con puertos
de salida nuevos: `AdminAccountRepository`, `PasswordHasher`, `SessionTokenIssuer`, `ContentVersionRepository`, `ContentCacheInvalidator`, `FileStoragePort`, `ImageReferenceRepository`.
Argon2 y JWT (BouncyCastle, SmallRye JWT) y el SDK de AWS solo existen en `adapter.out`/`config`; `ArchitectureTest` lo impone. La validación del contenido (`ContentValidator`, `ContentParity`)
y los bytes mágicos de las imágenes (`ImageFormat`) son lógica pura, sin HTTP ni JSON.

**Sesión.** Login con contraseña → JWT HS256 (`iss`, `aud=cv-admin`, `sub`, `sv`, `jti`, `exp`; 8 h). No hay refresh tokens. El claim **`sv`** es la *versión de sesión*:
tras validar la firma, `AdminSessionFilter` la compara por clave primaria con `admin_account.session_version` **en cada petición**; cambiar la contraseña, `logout-all` y
`admin-password.sh set-password` la suben y todos los tokens anteriores dan 401 al instante. Sin el secreto JWT (o con menos de 32 caracteres) el admin queda deshabilitado: 404, y la API pública sigue.
La contraseña es Argon2id (64 MiB, t=3, p=1, configurable, dentro del hash PHC; como mucho 2 hashes a la vez) y la verificación sin cuenta usa un hash falso para igualar tiempos.

**Fuerza bruta.** Bloqueo por cuenta en la base de datos (5 fallos → 15 min, 429 `ADMIN_LOGIN_LOCKED` aun con la contraseña buena) y un límite global en memoria de 10 intentos por minuto.
No se limita por IP: detrás de `cv-web` no es fiable. Contrapartida conocida: cualquiera con acceso al login puede bloquear al administrador 15 min; mitigado con el bloqueo corto, `unlock` y el aislamiento de red.

**Contenido versionado.** `PUT /content/{kind}` crea una versión nueva en ES y EN **en una transacción** (con `pg_advisory_xact_lock` por tipo e idioma, ES antes que EN), comparando `baseVersions`
dentro del candado: una base obsoleta es 409 y no cambia nada; dos guardados simultáneos con la misma base dan un ganador y un 409. Lo guardado pasa antes por lector → dominio → escritor
(`SiteContentJsonWriter`), así que siempre es algo que la API pública sabe leer. ES y EN deben tener paridad (mismos ids y orden y mismos campos neutros: `price`, `relation`, `disclosure`, `client`,
fechas, `status`, y de las imágenes `key`/`width`/`height`); el texto es traducible. Los documentos que traen campos desconocidos se rechazan (`UNKNOWN_FIELD`). Un `anonymous` con `client` se rechaza
(`ANONYMOUS_CLIENT_FORBIDDEN`) y el dominio además lo descarta al leer: dos barreras. El rollback reactiva una versión existente; un guardado posterior toma `MAX(version)+1`, igual que `make-seed.py`.

**Imágenes.** Solo `own` + `public`. Se aceptan JPEG, PNG, WebP y GIF de hasta 5 MB por sus **bytes** (el nombre y el `Content-Type` del cliente se ignoran; SVG se rechaza); la clave
`site/projects/<uuid>.<ext>` la genera el servidor y en el JSON solo se guarda la `key`: la `url` pública se compone en la capa REST con `R2_PUBLIC_URL`. No se quitan metadatos EXIF (fuera de alcance por decisión). Cliente S3 con región `auto`,
estilo *path*, `chunkedEncodingEnabled(false)` y checksums `WHEN_REQUIRED` (R-3: solo probado con MinIO; la prueba con R2 real es manual). Borrar es 409 si **cualquier** versión (activa o histórica) referencia la
imagen (`jsonb_path_exists` sobre todas las filas); `cleanup` borra solo huérfanas con más de la gracia y por lotes de 1000. Con R2 caído o sin configurar solo falla la subida (503); guardar contenido no depende de R2.
Al guardar no se comprueba que la imagen exista en el bucket.

**Contrato para el panel (fase 3, repo `cv-web`).** El panel llama a la API **solo desde sus route handlers** (servidor Node), nunca desde el navegador. Guarda el `accessToken` en una cookie httpOnly
(`cv_admin_token`, `Secure`, `SameSite=Strict`, `Path=/admin`); `proxy.ts` solo mira que la cookie exista, la autoridad es la API. Un 401 `ADMIN_UNAUTHORIZED` borra la cookie y vuelve al login; un 429 `ADMIN_LOGIN_LOCKED`
muestra el `Retry-After`; un 409 `CONTENT_VERSION_CONFLICT` recarga. Siempre se reenvía la `baseVersion`/`baseVersions`. Mensajes según el `code`, no el texto. Comprobar `Origin` en los handlers que mutan (CSRF).
**No reenviar cabeceras `CF-*`** a la API (rompería `AdminEdgeGuard`). La subida reenvía el `multipart` tal cual (los route handlers no tienen el tope de 1 MB de las server actions; comprobarlo en el despliegue standalone).
La web pública lee `project.images[].url` del endpoint público.

**Auditoría.** Puerto de salida `AuditLog` (`record(AuditEvent)`, **nunca lanza**) con `AuditAction`/`AuditOutcome`/`AuditEvent` en `application.audit`; el único adaptador es `LogAuditLog` (`adapter.out.audit`), que escribe en el logger `audit`
`action=… outcome=… clave=valor…` (detalles ordenados; INFO si éxito, WARN si fallo de seguridad). El `request_id` y el `client_ip` salen del MDC. Los nueve servicios emiten el evento tras el éxito (o justo antes de lanzar, en los fallos de seguridad):
`LOGIN_SUCCESS`, `LOGIN_FAILED` (`reason=bad_credentials|no_account`), `LOGIN_LOCKED` (`retry_after_s`), `LOGIN_RATE_LIMITED`, `PASSWORD_CHANGED`, `PASSWORD_CHANGE_FAILED` (`reason=wrong_current|locked|weak`), `SESSIONS_REVOKED`,
`CONTENT_SAVED` (`kind`, `es_version`, `en_version`, `comment_present`, `comment_length`), `CONTENT_ROLLED_BACK` (`kind`, `lang`, `from_version`, `to_version`), `IMAGE_UPLOADED` (`key`, `content_type`, `bytes`, `width`, `height`),
`IMAGE_DELETED` (`id`), `IMAGES_CLEANUP` (`dry_run`, `older_than_hours`, `deleted`, `would_delete`, `kept`) y `MESSAGES_READ` (`limit`, `returned`, `paged`). Un fallo de validación, conflicto o not-found del contenido no se audita (ya está en el access log con `err=`),
ni las lecturas de contenido, versiones, sesión o imágenes. Nunca van contraseñas, tokens, hashes, comentarios, cuerpos ni datos de mensajes: `AuditEvent` solo admite pares de texto y su constructor sanea claves y valores (sin saltos de línea ni espacios, 120 caracteres como máximo)
para que nadie falsee una línea. No hay tabla de auditoría: la retención es la rotación de logs de Docker (`json-file`, 10 MB x 5 por contenedor, ver [docs/observability.md](docs/observability.md)).

**Trampas.**
- **Protección de red.** La API es pública. La regla de Cloudflare que bloquea `/api/v1/admin/*` es tarea del dueño (runbook). `AdminEdgeGuard` es defensa en profundidad: con `CV_ADMIN_EDGE_BLOCK=true` responde 404 a lo que lleve
  `CF-Ray` o `CF-Connecting-IP` (todo lo que pasa por el túnel); es un filtro previo, así que el 404 sale incluso sin token.
- **Límite de cuerpo solo en `admin/images`.** Quarkus solo tiene un límite global (`quarkus.http.limits.max-body-size=6M`). `RequestBodyLimits` rechaza con 413 por `Content-Length` y, para `chunked`, sustituye la clave interna
  `io.quarkus.max-request-size` por 128 KB, en todo menos `POST /api/v1/admin/images`. Esa clave es interna de Quarkus: `Hl7ResourceTest`/`ContactMessagesResourceTest` (T137) fallan si una actualización la cambia.
  Entre 5 y 6 MB la imagen da el problema `IMAGE_TOO_LARGE`; por encima de 6 MB corta el servidor con un 413 sin cuerpo. El contenido admin tiene su propio tope (96 KB) para que el rechazo sea un problema.
- **Autenticación diferida.** `quarkus.http.auth.proactive=false` y las políticas `applies-to=JAXRS`: así los fallos pasan por `ProblemExceptionMapper` y salen como `problem+json` con `WWW-Authenticate: Bearer`.
  Consecuencias: un `Authorization: Bearer` inválido en una ruta **pública** se ignora (200), y una ruta admin inexistente da 404 genérico. `POST /images` no declara `@Consumes`: con él, un cuerpo de otro tipo daría 415 antes que 401.
- **`sv` y una sola instancia.** El filtro de sesión hace una lectura JDBC por petición (corre en un hilo de trabajo, no en el de eventos). El limitador de login y la invalidación de caché son **en memoria**: valen con una réplica.
  Con varias habría que pasar a `LISTEN/NOTIFY` de Postgres o a Redis.
- **Época de caché.** Tras guardar o hacer rollback, `PostgresSiteContentRepository.invalidate` sube un contador (época) de la clave y borra la entrada; un refresco que empezó antes y termina después no repone lo viejo
  (y si la base de datos falla justo tras invalidar, no se sirve lo ya invalidado: se propaga el error). Los GET públicos conservan `max-age=300`: quien pase por el CDN de Cloudflare ve el cambio con hasta 5 min de retraso; la web lee sin caché.
  El CV (`cv.resume.cache-ttl`) no se invalida: no se edita desde el admin.
- **Un CV vs. contenido.** `resume` queda fuera del admin por esquema y privacidad; `ContentCacheInvalidator` es genérico por si algún día se quiere.
- **Tests.** Las imágenes de MinIO que usan los tests son `cgr.dev/chainguard/minio:latest` (`-Dcv.test.minio-image=` para cambiarla): `minio/minio` ya no se descarga.
