# CLAUDE.md

Guía para trabajar en este repositorio. Estado a **2026-11-12**.

## Qué es
El CV de Julio Flores como una **API REST** pública de solo lectura más una zona privada `/api/v1/admin` para editar servicios, proyectos y arquitectura, subir imágenes y leer mensajes: Quarkus 3.40, Java 21, arquitectura hexagonal en tres módulos Maven
(`domain`, `application`, `infrastructure`). El contenido vive en PostgreSQL (tabla `resume_version`, JSONB versionado); el real no está en el repo. Es un portfolio de un perfil
senior (backend/full stack, nicho sanitario HL7/FHIR): la arquitectura es una demostración deliberada, pero **sin sobreingeniería**.
Diseño y decisiones: [ARCHITECTURE.md](ARCHITECTURE.md). Comandos, cobertura y CI: [README.md](README.md).

## Comandos
```bash
mvn -B verify                              # compila, ejecuta las pruebas y genera la cobertura
mvn quarkus:dev -pl infrastructure -am     # modo dev (http://localhost:8080, Swagger en /swagger/)
python3 scripts/ci-summary.py              # resumen de pruebas y cobertura tras verify
scripts/smoke-test.sh [URL]                # (incluye X-Request-Id) comprueba una instancia en marcha (CV de db/sample cargado; CV_ADMIN_JWT_SECRET, CV_ADMIN_EDGE_BLOCK=true y R2_PUBLIC_URL=https://media.example.test en su entorno)
python3 -m unittest discover -s scripts -p 'test_*.py'   # pruebas de make-seed.py (también en el CI)
scripts/admin-password.sh set-password     # en el NAS, TTY: crea/cambia la contraseña del admin (también unlock y status)
```
El compose del NAS **no está aquí**: es `deploy/docker-compose.yml` del repo `cv-web` (único para `cv-db`, `cv-api` y `cv-web`). Aquí queda `deploy/backup-db.sh`.
Puesta en marcha del admin: [docs/admin-runbook.md](docs/admin-runbook.md).

Ejecuta Maven desde la raíz. En la máquina de Julio hay un `.mvn/maven.config` local (ignorado por git) que evita su mirror corporativo.

## Reglas del proyecto
- **Capas:** `domain` y `application` no importan frameworks ni HTTP. `ArchitectureTest` (ArchUnit) lo impone; si falla, arregla el código, no la regla.
- **Un caso de uso = una interfaz** de entrada. El cableado está solo en `infrastructure/config/UseCaseConfig`.
- **Errores:** las excepciones de negocio extienden `ApplicationException(ErrorCode, mensajeEnInglés, args…)`. Para un error nuevo: constante en `ErrorCode` (si es de negocio) y en `ApiError` (estado HTTP), y `CODIGO.title` / `CODIGO.detail` en `problems_es.properties` y `problems_en.properties`. `ProblemCatalogTest` falla si falta algo. Los apóstrofos en los `.properties` se duplican (`''`).
- **Idioma:** `?lang` > `Accept-Language` > español. Solo se traducen el contenido y los mensajes de error, no Swagger ni los logs.
- **Swagger:** cada endpoint documenta todas sus salidas (200/404/500) con ejemplos. `OpenApiDocumentTest` falla si falta documentación.
- **Contenido:** el real está en `private/content/resume.*.json` (ignorado por git), lo edita su dueño a mano; léelo antes de tocarlo y mantén ES y EN con la **misma estructura**. También `services.*.json`, `projects.*.json` y `architecture.*.json` (mismo directorio y misma regla ES/EN). Se publica con `scripts/make-seed.py` + `psql` (ver README). Nunca teléfono, documento de identidad ni fecha de nacimiento. Las pruebas usan el CV ficticio de `db/sample` (Ada Ejemplo); no pongas datos reales en el repo.
- **Admin:** todo lo de `/api/v1/admin` exige JWT salvo el login; endpoints, variables y diseño en [README](README.md) y [ARCHITECTURE.md](ARCHITECTURE.md) (sección 7). Un error o código nuevo del admin sigue la regla de **Errores**; cada operación admin documenta `bearerAuth`, 401 y sus errores en Swagger. Nada de secretos en el repo: `CV_ADMIN_JWT_SECRET` y `R2_*` solo por entorno, la contraseña solo por terminal (`admin-password.sh`). El repo es público y los tests usan solo el CV ficticio.
- **Logs:** JSON en producción (`quarkus-logging-json`, MDC plano con `request_id`, `client_ip` solo en rutas admin y `error_code`, `service=cv-api`) y texto con `[request_id]` en dev/test. `RequestContextHandler` (handler Vert.x en `config`) pone `X-Request-Id` en toda respuesta; el access log integrado de Quarkus (categoría `access`) usa un patrón propio y **nunca** `%q`, `%r`, `%U`, `%h`, `%a` ni cabeceras (`%U` incluye la query: la ruta es `%R`). Un fallo de BD no vuelca mensaje ni traza (`ErrorLogDescription`). En pruebas, captura con `LogRecords` y espera la línea de access con `awaitAccessLine` (sale al cerrar la respuesta).
- **Auditoría:** las acciones admin (login, contraseña, sesiones, contenido, imágenes, mensajes) se registran con el puerto `AuditLog` (`application`, no lanza) y el adaptador `LogAuditLog` (logger `audit`, `request_id` y `client_ip` por MDC). Un servicio nuevo del admin emite su `AuditEvent` tras el éxito; solo ids, contadores y banderas, nunca texto libre, comentarios, contraseñas ni datos de contacto. Tabla de eventos en ARCHITECTURE.md §7. Guía para leer y buscar logs (formato, exclusiones, rotación): [docs/observability.md](docs/observability.md); si cambias un campo, categoría o acción, actualízala. `ArchitectureTest` limita quién implementa `AuditLog`.
- **Pruebas:** cobertura mínima 90 % total y 80 % por módulo (la comprueba `ci-summary.py`). Todo código nuevo lleva pruebas.
- **Commits:** Conventional Commits en inglés, **sin** `Co-Authored-By` ni líneas de atribución.
- **CI:** un push a `main` publica una imagen en Docker Hub (`julioflores781/cv-api`). En cambios solo de documentación añade `[skip ci]` al mensaje.

## Trampas conocidas
- En `@QuarkusTest` **no hagas peticiones HTTP dentro de un `@BeforeAll`**: según el orden de arranque da 500, `ClassNotFound` o conexión rechazada. Carga de forma perezosa dentro de los tests.
- `archunit-junit5` 1.5.1 no funciona con JUnit 6 (el de Quarkus): ejecuta 0 pruebas sin avisar. Usa la API principal de ArchUnit.
- `quarkus.swagger-ui.path` lleva barra inicial (`/swagger`); sin ella Quarkus lo cuelga de `/q/`.
- Las pruebas necesitan Docker (Testcontainers levanta PostgreSQL). `db/sample/V1000__sample_resume.sql` se **genera**: si editas `db/sample/*.json`, regénerala con `scripts/make-seed.py`.
- Flyway va con `placeholder-replacement=false` (el JSON del CV puede llevar `${`) y el seed usa `$resume$` seguido de salto de línea (`$resume${` sería un marcador).
- El smoke test (`scripts/smoke-test.sh`) espera el CV ficticio de `db/sample` (Ada Ejemplo): el CI levanta un Postgres, aplica `V1000__sample_resume.sql` y luego lo ejecuta. Si cambias el sample, ajusta los fragmentos esperados.
- Las pruebas de adaptadores de `infrastructure` deben ser `@QuarkusTest` (con `@Inject`): `quarkus-jacoco` solo mide lo que se carga dentro de Quarkus, y una prueba JUnit plana no suma cobertura.
- HL7: solo v2.5.1 (`hapi-structures-v251`) y los tipos `ADT^A01`/`ORU^R01`; otros dan `422 HL7_UNSUPPORTED_MESSAGE`. Un OBX sin su OBR no encaja en la estructura ORU y se descarta sin error. El cuerpo nunca se registra en logs. Las versiones de HAPI están en el `pom` raíz.
- La cobertura de `infrastructure` se limita a sus propias clases (`quarkus.jacoco.includes`); si no, `domain`/`application` salen con 0 %.

- **Admin, trampas:** (1) `CV_ADMIN_EDGE_BLOCK=true` en producción y la regla de Cloudflare que bloquea `/api/v1/admin/*` (la API es pública). (2) El límite de cuerpo es 128 KB en todo salvo `POST /api/v1/admin/images` (`RequestBodyLimits`; usa una clave interna de Quarkus que vigila T137). (3) `sv` (versión de sesión) se compara con la base de datos en cada petición: no lo caches. (4) Tras guardar o hacer rollback, la caché de contenido se invalida con una **época** (un refresco anterior no repone lo viejo). (5) Una sola instancia de API (limitador y caché en memoria). (6) Autenticación diferida (`proactive=false`): un Bearer inválido en ruta pública se ignora y una ruta admin inexistente es 404. (7) Sin `CV_ADMIN_JWT_SECRET` ≥ 32 caracteres el admin está deshabilitado (404), no falla el arranque. (8) La clave de imagen es siempre `site/projects/<uuid>.<ext>`: no existe `R2_KEY_PREFIX`. (9) El smoke y el CI necesitan el secreto de prueba y `R2_PUBLIC_URL` (ver Comandos).
- El único compose del NAS está en `cv-web/deploy/docker-compose.yml`; si añades una variable de entorno a la API, actualízalo allí (y su test) además del README.

## Hecho
Dominio completo, 8 casos de uso, endpoints `GET /api/v1/{profile,impact,experience[/{id}],skills,education,contact,resume}`,
**demo HL7** `POST /api/v1/hl7/translate?format=json|fhir` (HAPI; ADT^A01 y ORU^R01 en v2.5.1; mapeo manual a FHIR R4),
errores RFC 7807 con `code` estable y textos ES/EN, Swagger completo, 188 pruebas (≈96,6 % de cobertura de líneas), pipeline
de GitHub Actions (pruebas + cobertura → imagen Docker con smoke test, amd64 y arm64).
**Contacto** `POST /api/v1/contact/messages` (rama `feat/contact-messages`): valida, limita por remitente (hash de la IP con sal), guarda en `contact_message` y avisa por Telegram (`ContactNotifier`); campo trampa `website` para bots. Variables `CV_CONTACT_*` (ver README).
**Contenido editable** `GET /api/v1/{services,projects,architecture}` desde `content_version` (JSONB versionado; rama `feat/site-content`): proyectos con `disclosure` (`public`/`anonymous`; el dominio descarta el cliente de los anónimos y `make-seed.py` rechaza que lo traigan), adaptadores del hexágono con `status` honesto. Sin nada publicado responden vacío. Falta el contenido real en `private/content`.
**Administración** (`/api/v1/admin`, slugs `api-admin-*` de `cv-web/progress`): auth con JWT y Argon2id (`scripts/admin-password.sh` para la contraseña), contenido versionado bilingüe con rollback y paridad ES/EN, imágenes de proyectos en R2 (`images[]` en `GET /projects`, solo `own` + `public`), lectura de mensajes de contacto, invalidación inmediata de la caché y filtro de Cloudflare. Pruebas, cobertura y smoke con el CV de ejemplo; pendiente del dueño: regla de Cloudflare, token de R2 y prueba contra el bucket real (ver `docs/admin-runbook.md`). El panel `/admin` y los tipos de `images[]` en la web son de la fase 3 (repo `cv-web`).

## Siguiente (orden recomendado)
1. **ADRs** cortos en `docs/adr/` (multi-módulo vs paquetes, Quarkus vs NestJS, errores con código estable, alcance del i18n).
2. **Exportaciones:** `/llms.txt`, `/resume.pdf` (openhtmltopdf desde Qute), `/resume?format=jsonresume`, `ETag`.
3. **Front Next.js** estático (ES por defecto, SEO, lee `/api/v1/resume` en el build).
4. `page_view_daily`, `/status`, Cloudflare Tunnel (Postgres y Flyway ya están; el compose está en cv-web).
5. `GET /api/v1/{services,projects,architecture}` desde la base de datos (JSONB versionado como el CV) para la web; los proyectos llevan `disclosure` (`public`/`anonymous`) y el nombre del cliente no sale si es anónimo.

Pendiente menor: los ids de experiencia distinguen mayúsculas y los grupos de skills no; unificar en una línea, sin tipos nuevos.
Pendiente del contenido (de su dueño): fechas de las fases dentro de Sale & Brick y confirmar las cifras de impacto.

## Cómo trabajar aquí
Definir antes de programar: propón el diseño y espera confirmación antes de crear módulos o esqueletos. Da una recomendación clara
con su porqué (no un abanico), y verifica de verdad (ejecuta contra la app real, mira GitHub con `gh`) diciendo con precisión lo que
no pudiste comprobar.
