# Arquitectura: CV como API

Un `resume.json` (ES/EN) es la fuente única. El dominio no conoce Quarkus, y cada salida (JSON, PDF, `llms.txt`) es un adaptador sobre el mismo modelo.

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
| GET | `/resume` | CV completo en un solo documento (esquema propio; el formato JSON Resume estándar llegará como `?format=jsonresume` con las exportaciones) |
| GET | `/resume.pdf` | PDF apto para ATS |
| POST | `/hl7/translate?format=json\|fhir` | HL7 v2 (text/plain) → JSON o FHIR |
| POST | `/contact/messages` | guarda un mensaje, con límite de peticiones |
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
- **Caché:** `Cache-Control: public, max-age=300` y `Vary: Accept-Language` en los GET con 200 (implementado). `ETag` pendiente.
- **CORS** limitado al dominio propio.
- **Datos públicos**, solo lectura. No hay endpoints de administración: el CV se actualiza desde el pipeline.
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
   ResumeRepository · ContactMessageRepository · Hl7Parser · ResumeRenderer
   Mailer · RateLimiter · StatusProbe · Clock
                                   ▼
                    ┌──────────── adaptadores de salida ──────────────┐
                    │ Postgres (Panache + Flyway) · HAPI HL7/FHIR     │
                    │ PDF (Qute → HTML → PDF) · llms.txt (Qute)       │
                    │ Quarkus Mailer · Uptime Kuma (estado)           │
                    └─────────────────────────────────────────────────┘
```

Decisiones de diseño:
- **Maven multi-módulo:** `domain`, `application` e `infrastructure` (la app Quarkus). El compilador impide que el dominio importe Quarkus. Los módulos de librería necesitan Jandex para que Quarkus descubra sus beans.
- **`domain` y `application` sin dependencias de framework.** Los casos de uso se conectan con `@Produces` en infraestructura.
- **`ResumeRenderer` es un puerto con varios adaptadores:** JSON Resume, PDF y `llms.txt`.
- **`Hl7Parser` es otro puerto,** con un adaptador HAPI v2 y, más adelante, otro HL7 → FHIR.
- **ArchUnit en CI** (API principal con pruebas JUnit normales; `archunit-junit5` 1.5.1 no funciona con JUnit 6 de Quarkus 3.40): el dominio no depende de nada, la aplicación solo del dominio, y los adaptadores no se importan entre sí.

## 3. Datos en PostgreSQL

- `resume_version(id, version, lang, content jsonb, published_at, active)`: instantáneas inmutables por versión e idioma. El dominio las mapea al agregado `Resume`.
- `contact_message(id, name, email, body, created_at, ip_hash)`: la IP se guarda hasheada, nunca en claro.
- `page_view_daily(day, path, count)`: contadores agregados, sin rastreo individual.

Flujo de importación: `content/resume.es.json` y `content/resume.en.json` en el repo → CI → comando `ImportResume` → nueva fila activa. El JSON del repo sigue siendo la fuente única.

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
