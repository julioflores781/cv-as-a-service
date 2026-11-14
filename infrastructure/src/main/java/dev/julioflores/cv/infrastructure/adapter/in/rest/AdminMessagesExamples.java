package dev.julioflores.cv.infrastructure.adapter.in.rest;

/** Ejemplos de OpenAPI de la lectura de mensajes de contacto (datos ficticios). */
final class AdminMessagesExamples {

    private AdminMessagesExamples() {}

    static final String LIST = """
            {
              "messages": [
                {
                  "id": 41,
                  "name": "Ada Ejemplo",
                  "email": "ada@example.com",
                  "body": "Hola, me gustaría hablar contigo sobre una vacante de backend.",
                  "createdAt": "2026-11-12T09:30:00Z"
                },
                {
                  "id": 40,
                  "name": "Grace Ejemplo",
                  "email": "grace@example.com",
                  "body": "Buenas, ¿tienes disponibilidad para un proyecto de integración HL7?",
                  "createdAt": "2026-11-11T17:05:00Z"
                }
              ],
              "next": "40"
            }""";

    static final String LAST_PAGE = """
            {
              "messages": [],
              "next": null
            }""";

    static final String BAD_PARAMETER = """
            {
              "type": "about:blank",
              "title": "Petición incorrecta",
              "status": 400,
              "detail": "La petición no es válida.",
              "code": "BAD_REQUEST"
            }""";
}
