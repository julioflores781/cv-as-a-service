# CV as a Service

[![CI](https://github.com/julioflores781/cv-as-a-service/actions/workflows/ci.yml/badge.svg)](https://github.com/julioflores781/cv-as-a-service/actions/workflows/ci.yml)

Backend Quarkus con arquitectura hexagonal en tres módulos Maven. Ver [ARCHITECTURE.md](ARCHITECTURE.md).

```
cv/
├── deploy/          docker-compose para el NAS (API + PostgreSQL) y copia de seguridad
├── db/sample/       CV ficticio (JSON + seed SQL) para pruebas y arranque sin datos privados
├── private/         (ignorado por git) CV real y su seed: content/ · seed.sql
├── domain/          modelo de dominio puro (sin frameworks)
├── application/     casos de uso y puertos (solo depende de domain)
└── infrastructure/  app Quarkus: adaptadores REST y de salida, ArchUnit
```

Además: `scripts/` (resumen del pipeline, smoke test y generador del seed), `Dockerfile` y `.github/workflows/ci.yml`.

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
scripts/smoke-test.sh [http://localhost:8080]  # comprueba una instancia en marcha (health, API, errores, Swagger)
python3 scripts/ci-summary.py                  # resumen de pruebas y cobertura tras un `mvn verify`
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

### Despliegue en el NAS

`deploy/docker-compose.yml` se pega tal cual en el editor de compose del NAS (sin `.env`): cambia las contraseñas, el
y `QUARKUS_HTTP_CORS_ORIGINS` (el dominio de la web; sin él el navegador no puede llamar a la API).
Levanta `cv-api` + `cv-db` (la base de datos no publica puertos). `cv-api` se une a la red `padelhub_default`, donde ya corren
los `cloudflared`: en Cloudflare Tunnel el servicio es `http://cv-api:8080`, sin IPs ni puertos publicados.

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

1. **Pruebas y cobertura** (en cada push y pull request): `mvn verify` y, en la pestaña *Summary* de la ejecución,
   el resultado de cada prueba, la cobertura por módulo y paquete, y las clases con líneas sin cubrir.
   Falla si hay pruebas rotas o la cobertura baja de los umbrales. Los informes quedan como artefacto.
2. **Imagen Docker** (solo en push a `main` o a una etiqueta `v*`, y solo si el trabajo anterior pasó):
   empaqueta **el mismo artefacto que se probó**, lo arranca junto a un PostgreSQL desechable con el CV de `db/sample` y le pasa `scripts/smoke-test.sh`; si responde,
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
