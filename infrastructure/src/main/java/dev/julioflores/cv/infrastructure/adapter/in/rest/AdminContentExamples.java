package dev.julioflores.cv.infrastructure.adapter.in.rest;

/** Ejemplos de OpenAPI de la edición de contenido (CV ficticio de Ada Ejemplo; texto en español). */
final class AdminContentExamples {

    private AdminContentExamples() {}

    static final String CONTENT = """
            {
              "kind": "services",
              "languages": {
                "es": {
                  "version": 3,
                  "publishedAt": "2026-11-10T18:02:11Z",
                  "source": "admin",
                  "comment": "Cambio de precio",
                  "content": { "services": [
                    { "id": "servicio-ejemplo", "title": "Servicio de ejemplo", "summary": "Un servicio ficticio.",
                      "price": { "from": 1500, "currency": "EUR" } },
                    { "id": "servicio-a-consultar", "title": "Servicio a consultar", "summary": "Sin precio publicado." } ] }
                },
                "en": {
                  "version": 3,
                  "publishedAt": "2026-11-10T18:02:11Z",
                  "source": "admin",
                  "comment": "Cambio de precio",
                  "content": { "services": [
                    { "id": "servicio-ejemplo", "title": "Sample service", "summary": "A made-up service.",
                      "price": { "from": 1500, "currency": "EUR" } },
                    { "id": "servicio-a-consultar", "title": "Service on request", "summary": "No published price." } ] }
                }
              },
              "parity": { "ok": true, "differences": [] }
            }""";

    static final String CONTENT_EMPTY = """
            {
              "kind": "projects",
              "languages": {
                "es": { "version": 0, "publishedAt": null, "source": null, "comment": null, "content": null },
                "en": { "version": 0, "publishedAt": null, "source": null, "comment": null, "content": null }
              },
              "parity": { "ok": true, "differences": [] }
            }""";

    static final String SAVE_REQUEST = """
            {
              "baseVersions": { "es": 3, "en": 3 },
              "comment": "Cambio de precio",
              "documents": {
                "es": { "services": [
                  { "id": "servicio-ejemplo", "title": "Servicio de ejemplo", "summary": "Un servicio ficticio.",
                    "price": { "from": 1600, "currency": "EUR" } } ] },
                "en": { "services": [
                  { "id": "servicio-ejemplo", "title": "Sample service", "summary": "A made-up service.",
                    "price": { "from": 1600, "currency": "EUR" } } ] }
              }
            }""";

    static final String VERSIONS = """
            {
              "kind": "services",
              "lang": "es",
              "versions": [
                { "version": 5, "active": false, "publishedAt": "2026-11-12T09:00:00Z", "source": "admin", "comment": "Prueba" },
                { "version": 4, "active": true, "publishedAt": "2026-11-10T18:02:11Z", "source": "seed", "comment": null }
              ]
            }""";

    static final String SNAPSHOT = """
            {
              "kind": "services",
              "lang": "es",
              "version": 4,
              "active": true,
              "publishedAt": "2026-11-10T18:02:11Z",
              "source": "seed",
              "comment": null,
              "content": { "services": [
                { "id": "servicio-ejemplo", "title": "Servicio de ejemplo", "summary": "Un servicio ficticio.",
                  "price": { "from": 1500, "currency": "EUR" } } ] }
            }""";

    static final String ROLLBACK_REQUEST = """
            {
              "baseVersion": 5,
              "targetVersion": 3
            }""";

    static final String NOT_FOUND = """
            {
              "type": "about:blank",
              "title": "Recurso no encontrado",
              "status": 404,
              "detail": "La ruta pedida no existe.",
              "code": "NOT_FOUND"
            }""";

    static final String VERSION_NOT_FOUND = """
            {
              "type": "about:blank",
              "title": "Versión no encontrada",
              "status": 404,
              "detail": "No existe la versión 9 del contenido en «es».",
              "code": "CONTENT_VERSION_NOT_FOUND"
            }""";

    static final String CONFLICT = """
            {
              "type": "about:blank",
              "title": "Conflicto de versión",
              "status": 409,
              "detail": "Alguien publicó antes: la versión activa de «es» es la 4, no la que estabas editando. Recarga y vuelve a intentarlo.",
              "code": "CONTENT_VERSION_CONFLICT"
            }""";

    static final String INVALID = """
            {
              "type": "about:blank",
              "title": "Contenido no válido",
              "status": 422,
              "detail": "El contenido incumple las reglas (2 problema(s)). El detalle está en «errors».",
              "code": "CONTENT_INVALID",
              "errors": [
                { "path": "es.projects[1].client", "code": "ANONYMOUS_CLIENT_FORBIDDEN" },
                { "path": "en.projects[0].links[0].url", "code": "INVALID_URL" }
              ]
            }""";

    static final String PARITY = """
            {
              "type": "about:blank",
              "title": "Español e inglés no coinciden",
              "status": 422,
              "detail": "El español y el inglés deben tener la misma estructura: mismos ids, mismo orden y mismos campos neutros (1 diferencia(s), en «errors»).",
              "code": "CONTENT_PARITY_BROKEN",
              "errors": [ { "path": "services[1]", "code": "PARITY_MISMATCH" } ]
            }""";

    static final String TOO_LARGE = """
            {
              "type": "about:blank",
              "title": "Contenido demasiado grande",
              "status": 413,
              "detail": "El cuerpo de la petición supera el límite de 96 KB.",
              "code": "CONTENT_TOO_LARGE"
            }""";
}
