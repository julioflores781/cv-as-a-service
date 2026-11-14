# CV as a Service

[![CI](https://github.com/julioflores781/cv-as-a-service/actions/workflows/ci.yml/badge.svg)](https://github.com/julioflores781/cv-as-a-service/actions/workflows/ci.yml)

Backend Quarkus con arquitectura hexagonal en tres módulos Maven. Ver [ARCHITECTURE.md](ARCHITECTURE.md).

```
cv/
├── deploy/          copia de seguridad de la base de datos (el compose del NAS vive en el repo cv-web)
├── docs/            puesta en marcha del admin (admin-runbook.md) y guía de logs (observability.md)
├── db/sample/       CV ficticio (JSON + seed SQL) para pruebas y arranque sin datos privados
├── private/         (ignorado por git) CV real y su seed: content/ · seed.sql
├── domain/          modelo de dominio puro (sin frameworks)
├── application/     casos de uso y puertos (solo depende de domain)
└── infrastructure/  app Quarkus: adaptadores REST y de salida, ArchUnit
```

Además: `scripts/` (resumen del pipeline, smoke test, generador del seed y `admin-password.sh`), `Dockerfile` y `.github/workflows/ci.yml`.

## Logs
JSON en producción (access log, auditoría del admin, eventos de contacto y errores sin datos), con `X-Request-Id` en toda respuesta para cruzar `cv-web` y `cv-api`. Formato, qué no se registra jamás, cómo leerlos en el NAS y la rotación (10 MB x 5 por contenedor): [docs/observability.md](docs/observability.md).

## Comandos

```bash
mvn verify                                     # compila y ejecuta todas las pruebas
mvn quarkus:dev -pl infrastructure -am         # modo desarrollo en http://localhost:8080
curl 'localhost:8080/api/v1/profile?lang=en'

# Demo HL7: traduce un mensaje v2.5.1 a un Bundle FHIR R4 (solo mensajes sintéticos)
printf 'MSH|^~\\&|HIS|H|LAB|H|20260101||ADT^A01|M1|P|2.5.1\nPID|1||123^^^H^MR||Perez^Juan||19800101|M' |
  curl -X POST -H 'Content-Type: text/plain' --data-binary @- 'localhost:8080/api/v1/hl7/translate?format=fhir'
```

```bash
scripts/smoke-test.sh [http://localhost:8080]  # comprueba una instancia en marcha (health, API, errores, 401 del admin, Swagger)
# El smoke espera el CV de db/sample cargado y la instancia con CV_ADMIN_JWT_SECRET (≥ 32 caracteres), CV_ADMIN_EDGE_BLOCK=true y
# R2_PUBLIC_URL=https://media.example.test (valores de prueba; sin el secreto el admin está deshabilitado y el 401 no se puede comprobar).
python3 scripts/ci-summary.py                  # resumen de pruebas y cobertura tras un `mvn verify`
python3 -m unittest discover -s scripts -p 'test_*.py'   # pruebas de make-seed.py (las ejecuta también el CI)
```

## Base de datos y contenido

El CV vive en PostgreSQL (tabla `resume_version`, una fila por versión e idioma; la activa es la que sirve la API).
El esquema lo crea Flyway al arrancar. **El contenido real no está en el repositorio**: está en `private/` (ignorado por git).

```bash
# Publicar (o actualizar) el CV: genera el SQL y lo aplica. Cada ejecución crea la versión siguiente.
python3 scripts/make-seed.py private/content -o private/seed.sql
psql "$DATABASE_URL" --single-transaction -f private/seed.sql

# Volver a la versión anterior de un idioma
python3 scripts/make-seed.py --rollback es | psql "$DATABASE_URL" --single-transaction
```

Configuración de la API en producción (variables de entorno): `QUARKUS_DATASOURCE_JDBC_URL`,
`QUARKUS_DATASOURCE_USERNAME` y `QUARKUS_DATASOURCE_PASSWORD`. En desarrollo y en las pruebas Quarkus levanta un
PostgreSQL con Testcontainers por su cuenta (hace falta Docker); en dev arranca vacío: aplica un seed (el tuyo o `db/sample`).

**Formulario de contacto** (`POST /api/v1/contact/messages`): guarda el mensaje en `contact_message` y avisa por Telegram.
Variables: `CV_CONTACT_IP_SALT` (cadena larga y secreta con la que se hashea la IP; la IP nunca se guarda en claro),
`CV_CONTACT_TELEGRAM_BOT_TOKEN` (bot creado con @BotFather) y `CV_CONTACT_TELEGRAM_CHAT_ID` (tu chat con el bot; escríbele
primero y consulta `getUpdates`). Sin token o sin chat no se avisa, pero los mensajes se guardan igual. Límite: 5 mensajes por
remitente y hora (`cv.contact.rate-limit.max` / `.window`), en memoria: vale para una sola instancia. Para leer los mensajes:
`SELECT created_at, name, email, body FROM contact_message ORDER BY created_at DESC;`

### Servicios, proyectos y arquitectura (contenido editable)

Además del CV, la web lee tres contenidos de la base de datos (tabla `content_version`, mismo esquema versionado):
`GET /api/v1/services` (con precio «desde» opcional), `/projects` y `/architecture` (el hexágono: núcleo, puertos y adaptadores).
Se publican igual que el CV, con un archivo `<tipo>.<idioma>.json` en `private/content` (tipos: `services`, `projects`,
`architecture`); solo se publican los que haya en el directorio. Estructura: mira `db/sample`.

```bash
python3 scripts/make-seed.py private/content -o private/seed.sql && psql "$DATABASE_URL" --single-transaction -f private/seed.sql
python3 scripts/make-seed.py --rollback es --kind services | psql "$DATABASE_URL" --single-transaction   # volver atrás
```

- Cambiar un precio, quitar un adaptador del hexágono o añadir un proyecto es publicar una versión nueva: no se toca código ni la web.
  Con `make-seed.py` + `psql` la API tarda hasta 5 minutos en verlo (`cv.content.cache-ttl`); guardando desde el admin se ve **al instante** (la API invalida su caché).
- Sin nada publicado, los endpoints responden 200 con lista vacía (o sin puertos) y la web omite la sección.
- **Clientes:** un proyecto `anonymous` solo describe al cliente en `clientLabel`; el script **rechaza** que traiga `client`,
  así su nombre real nunca llega a la base de datos. Un proyecto `public` de una empresa (`employer`) debe traer `client`; uno propio (`own`) no necesita cliente.
- **Imágenes:** un proyecto `own` + `public` puede llevar `images` (hasta 10; `key` de la forma `site/projects/<uuid>.<jpg|png|webp|gif>`,
  `alt` de hasta 200 caracteres y `width`/`height` enteros de 1 a 20000; ver `db/sample/projects.*.json`). En proyectos de empleador o anónimos
  se rechazan al guardar y nunca salen en la API (una captura podría desvelar al cliente). Forma en `GET /api/v1/projects`:

  ```json
  { "id": "proyecto-publico", "disclosure": "public", "images": [
      { "url": "https://media.example.com/site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp",
        "alt": "Panel de reservas con la agenda semanal", "width": 1280, "height": 720 } ] }
  ```

  La API solo guarda la `key`; la `url` se compone con `R2_PUBLIC_URL` (sin esa variable, `images` sale `[]`). Un proyecto sin imágenes también da `"images": []`.
- Cada adaptador del hexágono lleva un `status` honesto: `in-this-api` (implementado aquí), `production` o `experiment`.

### Administración (`/api/v1/admin`)

Zona privada de un único administrador (solo contraseña) para editar servicios, proyectos y arquitectura sin tocar archivos, subir imágenes
de proyectos y leer los mensajes de contacto. La consume el panel de `cv-web` (fase 3) **desde su servidor**, nunca el navegador.
Swagger la muestra con la etiqueta **Admin** y `bearerAuth`; todas las respuestas llevan `Cache-Control: no-store`.

| Ruta | Para qué |
|---|---|
| `POST /admin/auth/login` | `{"password"}` → `{accessToken, tokenType, expiresAt}` (JWT HS256, 8 h). Lo único sin token |
| `GET /admin/auth/session` | sonda: `{authenticated, expiresAt, passwordChangedAt}` |
| `PUT /admin/auth/password` | `{currentPassword, newPassword}` (12 a 128 caracteres) → token nuevo; invalida los anteriores |
| `POST /admin/auth/logout-all` | 204; invalida todos los tokens |
| `GET /admin/content/{kind}` | lo activo en ES y EN (`version`, `source`, `comment`, `content`) y `parity`. `kind`: `services`, `projects`, `architecture` |
| `PUT /admin/content/{kind}` | `{baseVersions:{es,en}, comment?, documents:{es,en}}`: guardado bilingüe atómico (versión nueva en los dos idiomas o en ninguno) |
| `GET /admin/content/{kind}/{lang}/versions[?limit]` y `.../versions/{n}` | historial (límite 1 a 200) y una versión con su contenido |
| `POST /admin/content/{kind}/{lang}/rollback` | `{baseVersion, targetVersion}`: reactiva una versión (devuelve el mismo cuerpo que `GET /content/{kind}`) |
| `POST /admin/images` | multipart con una parte `file` (JPEG, PNG, WebP o GIF de hasta 5 MB, por sus bytes) → `201 {key, url, contentType, bytes, width, height}` |
| `GET /admin/images`, `DELETE /admin/images/{id}`, `POST /admin/images/cleanup?dryRun&olderThanHours` | listado (`referenced`), borrado (409 si alguna versión la usa) y limpieza de huérfanas (por defecto ensayo, gracia 24 h, mínimo 1) |
| `GET /admin/contact-messages?limit&before` | mensajes de contacto, nuevos primero, paginados por id (`next`); nunca el `ip_hash` |

(Todas bajo `/api/v1`.) Errores en `application/problem+json` con `code` estable. Los del admin: `ADMIN_INVALID_CREDENTIALS` y
`ADMIN_UNAUTHORIZED` (401), `ADMIN_CURRENT_PASSWORD_WRONG` (403), `ADMIN_LOGIN_LOCKED` (429 con `Retry-After`) y `ADMIN_LOGIN_RATE_LIMITED` (429),
`ADMIN_WEAK_PASSWORD` (422); `CONTENT_VERSION_CONFLICT` (409, `baseVersion` obsoleta), `CONTENT_VERSION_NOT_FOUND` (404),
`CONTENT_INVALID` y `CONTENT_PARITY_BROKEN` (422, con `errors: [{path, code}]`), `CONTENT_TOO_LARGE` (413, más de 96 KB);
`IMAGE_INVALID` (400), `IMAGE_TOO_LARGE` (413), `IMAGE_UNSUPPORTED_TYPE` (415), `IMAGE_IN_USE` (409), `IMAGE_NOT_FOUND` (404),
`IMAGE_STORAGE_UNAVAILABLE` (503); `NOT_FOUND` (404) para un tipo, idioma o ruta que no existen.

**Contraseña (en el NAS, no hay endpoint de alta):** `scripts/admin-password.sh set-password | unlock | status`. Ejecuta dentro del contenedor
`cv-api` (`CV_API_CONTAINER` para otro nombre; hace falta terminal) la herramienta `AdminPasswordTool`: lee la contraseña sin eco y con
confirmación, nunca como argumento. `set-password` crea o cambia la cuenta, invalida todas las sesiones y quita el bloqueo.

**Variables** (las trae el compose de `cv-web`; ninguna tiene secretos en el repo):

| Variable | Defecto | Notas |
|---|---|---|
| `CV_ADMIN_JWT_SECRET` | (ninguno) | ≥ 32 caracteres, `openssl rand -base64 48`. Ausente o corto: el admin queda deshabilitado (404 en `/api/v1/admin`, aviso en el log) y la API pública funciona |
| `CV_ADMIN_SESSION_LIFESPAN` | `PT8H` | duración del token |
| `CV_ADMIN_LOGIN_MAX_FAILURES` / `CV_ADMIN_LOGIN_LOCK` | `5` / `PT15M` | bloqueo por cuenta; además un límite global de 10 intentos por minuto |
| `CV_ADMIN_EDGE_BLOCK` | `false` (el compose lo pone a `"true"`, obligatorio) | 404 a toda petición admin con cabeceras `CF-Ray` o `CF-Connecting-IP` |
| `CV_ADMIN_CONTENT_MAX_BYTES` | `98304` | tope del cuerpo de `PUT /content` y rollback |
| `CV_ADMIN_ARGON2_MEMORY_KIB` / `_ITERATIONS` / `_PARALLELISM` | `65536` / `3` / `1` | medir en el NAS; el script de contraseña las lee igual |
| `R2_ENDPOINT`, `R2_BUCKET_NAME`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | vacías | las cuatro: subida activa; si falta alguna, `503 IMAGE_STORAGE_UNAVAILABLE` y lo demás sigue funcionando |
| `R2_PUBLIC_URL` | vacía | dominio público del bucket, sin barra final; compone `images[].url` |

La clave de las imágenes es siempre `site/projects/<uuid>.<ext>` (no hay `R2_KEY_PREFIX`). Los tiempos del cliente de R2 son `cv.r2.connect-timeout` y `cv.r2.request-timeout`.

**Puesta en marcha** (token de R2, regla de Cloudflare que bloquea `/api/v1/admin/*`, primera contraseña, prueba contra R2 real, rotación y medición de Argon2):
[docs/admin-runbook.md](docs/admin-runbook.md). La API es pública: **no despliegues el admin sin la regla de Cloudflare**.

### Despliegue en el NAS

El compose **único** del stack (`cv-db`, `cv-api` y `cv-web`) está en el repo `cv-web`: `deploy/docker-compose.yml`. Se pega tal cual en el editor de compose
del NAS (sin `.env`); cambia los valores `CAMBIA-…` (contraseña de la base de datos, `CV_CONTACT_IP_SALT`, `CV_ADMIN_JWT_SECRET`, R2). Aquí ya no hay compose.
La base de datos no publica puertos; `cv-api` y `cv-web` se unen a la red `padelhub_default`, donde ya corren los `cloudflared`.
La web llama a la API por la red interna (`http://cv-api:8080`), así que el compose no configura CORS.

```bash
# Publicar el CV (desde un terminal con acceso a Docker del NAS; copia antes private/seed.sql allí)
docker exec -i cv-db psql -U cv -d cv --single-transaction -v ON_ERROR_STOP=1 < seed.sql
deploy/backup-db.sh                           # pg_dump comprimido en deploy/backups (conserva 14); programar con cron
```

Swagger UI en `/swagger/` y el contrato en `/openapi.json` (con `Accept: application/json`; por defecto sirve YAML).

## Pruebas y cobertura

`mvn verify` ejecuta las pruebas de los tres módulos y genera los informes de JaCoCo:

| Módulo | Cómo se mide | Informe |
|---|---|---|
| `domain`, `application` | JaCoCo con pruebas unitarias | `*/target/site/jacoco/` |
| `infrastructure` | extensión `quarkus-jacoco` (el agente clásico no sirve para `@QuarkusTest`); solo cuenta sus clases | `infrastructure/target/jacoco-report/` |

Umbrales (los aplica `scripts/ci-summary.py`): **90 % de líneas en total y 80 % por módulo**.

## CI/CD

`.github/workflows/ci.yml` tiene dos trabajos:

1. **Pruebas y cobertura** (en cada push y pull request): `mvn verify`, las pruebas de Python de `scripts/` (`unittest`) y, en la pestaña *Summary* de la ejecución,
   el resultado de cada prueba, la cobertura por módulo y paquete, y las clases con líneas sin cubrir.
   Falla si hay pruebas rotas o la cobertura baja de los umbrales. Los informes quedan como artefacto.
2. **Imagen Docker** (solo en push a `main` o a una etiqueta `v*`, y solo si el trabajo anterior pasó):
   empaqueta **el mismo artefacto que se probó**, lo arranca junto a un PostgreSQL desechable con el CV de `db/sample` (y un secreto JWT de prueba, para activar el admin) y le pasa `scripts/smoke-test.sh`, que
   incluye el 401 del admin y el 404 con `CF-Ray`; si responde,
   publica `linux/amd64` y `linux/arm64` en Docker Hub.

Etiquetas de la imagen: `latest` (rama `main`), `sha-<corto>` y, al etiquetar `vX.Y.Z`, `X.Y.Z` y `X.Y`.

**Configuración necesaria** (Settings → Secrets and variables → Actions → *Repository secrets*):

| Secreto | Valor |
|---|---|
| `DOCKERHUB_USERNAME` | tu usuario de Docker Hub |
| `DOCKERHUB_TOKEN` | un *access token* de Docker Hub con permiso de lectura y escritura |

La imagen se publica como `<DOCKERHUB_USERNAME>/cv-api`. Construirla en local:

```bash
mvn -B package -DskipTests && docker build -t cv-api .
docker run --rm -p 8080:8080 cv-api
```

## Notas

- `.mvn/settings.xml` hace que el proyecto use Maven Central directamente, sin el mirror
  corporativo de `~/.m2/settings.xml`. `.mvn/maven.config` (ignorado por git, porque lleva una
  ruta absoluta de esta máquina) se lo pasa a Maven con `-s`, así funciona desde cualquier
  directorio y desde IntelliJ. En otra máquina o en CI no hace falta: basta con no tener ese mirror,
  o con crear el `maven.config` con la ruta local.
- Las reglas de arquitectura (`ArchitectureTest`) usan la API principal de ArchUnit con JUnit
  normal. El motor `archunit-junit5` 1.5.1 no es compatible con JUnit 6 (el que trae Quarkus 3.40)
  y ejecutaba 0 pruebas en silencio.
- En `@QuarkusTest` no hagas peticiones HTTP dentro de un `@BeforeAll`: según el orden de arranque puede dar 500,
  `ClassNotFound` o conexión rechazada. Cárgalo de forma perezosa dentro de los tests (ver `OpenApiDocumentTest`).
