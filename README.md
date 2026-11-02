# CV as a Service

Backend Quarkus con arquitectura hexagonal en tres módulos Maven. Ver [ARCHITECTURE.md](ARCHITECTURE.md).

```
cv/
├── content/         resume.es.json · resume.en.json (fuente única del contenido)
├── domain/          modelo de dominio puro (sin frameworks)
├── application/     casos de uso y puertos (solo depende de domain)
└── infrastructure/  app Quarkus: adaptadores REST y de salida, ArchUnit
```

Además: `scripts/` (resumen del pipeline y smoke test), `Dockerfile` y `.github/workflows/ci.yml`.

## Comandos

```bash
mvn verify                                     # compila y ejecuta todas las pruebas
mvn quarkus:dev -pl infrastructure -am         # modo desarrollo en http://localhost:8080
curl 'localhost:8080/api/v1/profile?lang=en'
```

```bash
scripts/smoke-test.sh [http://localhost:8080]  # comprueba una instancia en marcha (health, API, errores, Swagger)
python3 scripts/ci-summary.py                  # resumen de pruebas y cobertura tras un `mvn verify`
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
   empaqueta **el mismo artefacto que se probó**, lo arranca y le pasa `scripts/smoke-test.sh`; si responde,
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
