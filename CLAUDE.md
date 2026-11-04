# CLAUDE.md

Guía para trabajar en este repositorio. Estado a **2026-11-03**.

## Qué es
El CV de Julio Flores como una **API REST de solo lectura**: Quarkus 3.40, Java 21, arquitectura hexagonal en tres módulos Maven
(`domain`, `application`, `infrastructure`). El contenido vive en PostgreSQL (tabla `resume_version`, JSONB versionado); el real no está en el repo. Es un portfolio de un perfil
senior (backend/full stack, nicho sanitario HL7/FHIR): la arquitectura es una demostración deliberada, pero **sin sobreingeniería**.
Diseño y decisiones: [ARCHITECTURE.md](ARCHITECTURE.md). Comandos, cobertura y CI: [README.md](README.md).

## Comandos
```bash
mvn -B verify                              # compila, ejecuta las pruebas y genera la cobertura
mvn quarkus:dev -pl infrastructure -am     # modo dev (http://localhost:8080, Swagger en /swagger/)
python3 scripts/ci-summary.py              # resumen de pruebas y cobertura tras verify
scripts/smoke-test.sh [URL]                # comprueba una instancia en marcha (con el CV de db/sample cargado)
deploy/docker-compose.yml                  # API + Postgres para el NAS, se pega en su editor de compose (ver README)
```
Ejecuta Maven desde la raíz. En la máquina de Julio hay un `.mvn/maven.config` local (ignorado por git) que evita su mirror corporativo.

## Reglas del proyecto
- **Capas:** `domain` y `application` no importan frameworks ni HTTP. `ArchitectureTest` (ArchUnit) lo impone; si falla, arregla el código, no la regla.
- **Un caso de uso = una interfaz** de entrada. El cableado está solo en `infrastructure/config/UseCaseConfig`.
- **Errores:** las excepciones de negocio extienden `ApplicationException(ErrorCode, mensajeEnInglés, args…)`. Para un error nuevo: constante en `ErrorCode` (si es de negocio) y en `ApiError` (estado HTTP), y `CODIGO.title` / `CODIGO.detail` en `problems_es.properties` y `problems_en.properties`. `ProblemCatalogTest` falla si falta algo. Los apóstrofos en los `.properties` se duplican (`''`).
- **Idioma:** `?lang` > `Accept-Language` > español. Solo se traducen el contenido y los mensajes de error, no Swagger ni los logs.
- **Swagger:** cada endpoint documenta todas sus salidas (200/404/500) con ejemplos. `OpenApiDocumentTest` falla si falta documentación.
- **Contenido:** el real está en `private/content/resume.*.json` (ignorado por git), lo edita su dueño a mano; léelo antes de tocarlo y mantén ES y EN con la **misma estructura**. Se publica con `scripts/make-seed.py` + `psql` (ver README). Nunca teléfono, documento de identidad ni fecha de nacimiento. Las pruebas usan el CV ficticio de `db/sample` (Ada Ejemplo); no pongas datos reales en el repo.
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

## Hecho
Dominio completo, 8 casos de uso, endpoints `GET /api/v1/{profile,impact,experience[/{id}],skills,education,contact,resume}`,
**demo HL7** `POST /api/v1/hl7/translate?format=json|fhir` (HAPI; ADT^A01 y ORU^R01 en v2.5.1; mapeo manual a FHIR R4),
errores RFC 7807 con `code` estable y textos ES/EN, Swagger completo, 127 pruebas (≈95 % de cobertura de líneas), pipeline
de GitHub Actions (pruebas + cobertura → imagen Docker con smoke test, amd64 y arm64).

## Siguiente (orden recomendado)
1. **ADRs** cortos en `docs/adr/` (multi-módulo vs paquetes, Quarkus vs NestJS, errores con código estable, alcance del i18n).
2. **Exportaciones:** `/llms.txt`, `/resume.pdf` (openhtmltopdf desde Qute), `/resume?format=jsonresume`, `ETag`.
3. **Front Next.js** estático (ES por defecto, SEO, lee `/api/v1/resume` en el build).
4. `contact_message` y `page_view_daily`, `/status`, Cloudflare Tunnel (Postgres, Flyway y docker-compose ya están).

Pendiente menor: los ids de experiencia distinguen mayúsculas y los grupos de skills no; unificar en una línea, sin tipos nuevos.
Pendiente del contenido (de su dueño): fechas de las fases dentro de Sale & Brick y confirmar las cifras de impacto.

## Cómo trabajar aquí
Definir antes de programar: propón el diseño y espera confirmación antes de crear módulos o esqueletos. Da una recomendación clara
con su porqué (no un abanico), y verifica de verdad (ejecuta contra la app real, mira GitHub con `gh`) diciendo con precisión lo que
no pudiste comprobar.
