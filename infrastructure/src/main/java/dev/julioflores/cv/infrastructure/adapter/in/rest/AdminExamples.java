package dev.julioflores.cv.infrastructure.adapter.in.rest;

/** Ejemplos de OpenAPI de la zona de administración (texto en español, el idioma por defecto). */
final class AdminExamples {

    private AdminExamples() {}

    static final String TOKEN = """
            {
              "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.firma",
              "tokenType": "Bearer",
              "expiresAt": "2026-11-12T20:15:00Z"
            }""";

    static final String SESSION = """
            {
              "authenticated": true,
              "expiresAt": "2026-11-12T20:15:00Z",
              "passwordChangedAt": "2026-11-01T09:30:00Z"
            }""";

    static final String BAD_REQUEST = """
            {
              "type": "about:blank",
              "title": "Petición incorrecta",
              "status": 400,
              "detail": "La petición no es válida.",
              "code": "BAD_REQUEST"
            }""";

    static final String INVALID_CREDENTIALS = """
            {
              "type": "about:blank",
              "title": "Credenciales incorrectas",
              "status": 401,
              "detail": "La contraseña no es correcta.",
              "code": "ADMIN_INVALID_CREDENTIALS"
            }""";

    static final String LOCKED = """
            {
              "type": "about:blank",
              "title": "Cuenta bloqueada",
              "status": 429,
              "detail": "Demasiados intentos fallidos. Inténtalo de nuevo en 900 segundos.",
              "code": "ADMIN_LOGIN_LOCKED"
            }""";

    static final String RATE_LIMITED = """
            {
              "type": "about:blank",
              "title": "Demasiados intentos",
              "status": 429,
              "detail": "Se han hecho demasiados intentos de acceso. Espera un minuto.",
              "code": "ADMIN_LOGIN_RATE_LIMITED"
            }""";

    static final String CURRENT_WRONG = """
            {
              "type": "about:blank",
              "title": "Contraseña actual incorrecta",
              "status": 403,
              "detail": "La contraseña actual no es correcta.",
              "code": "ADMIN_CURRENT_PASSWORD_WRONG"
            }""";

    static final String WEAK = """
            {
              "type": "about:blank",
              "title": "Contraseña débil",
              "status": 422,
              "detail": "La contraseña nueva debe tener entre 12 y 128 caracteres y ser distinta de la actual.",
              "code": "ADMIN_WEAK_PASSWORD"
            }""";

    static final String UNAUTHORIZED = """
            {
              "type": "about:blank",
              "title": "Sesión no válida",
              "status": 401,
              "detail": "Falta la sesión, ha caducado o ha sido revocada. Inicia sesión de nuevo.",
              "code": "ADMIN_UNAUTHORIZED"
            }""";

}
