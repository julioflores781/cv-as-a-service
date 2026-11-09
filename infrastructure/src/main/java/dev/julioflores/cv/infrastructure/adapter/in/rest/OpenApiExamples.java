package dev.julioflores.cv.infrastructure.adapter.in.rest;

/** Ejemplos de respuesta para Swagger. Datos reales y públicos del CV (abreviados). */
final class OpenApiExamples {

    private OpenApiExamples() {}

    static final String PROFILE_ES = """
            {
              "name": "Julio Flores",
              "label": "Senior Full Stack Engineer · foco backend",
              "tagline": "Java/Quarkus & NestJS · Interoperabilidad sanitaria (HL7/FHIR)",
              "city": "Monforte de Lemos",
              "countryCode": "ES",
              "summary": "Diseño microservicios backend que procesan miles de transacciones al día en entornos sanitarios de misión crítica…",
              "availability": "Abierto a roles backend / full stack en España o en remoto."
            }""";

    static final String PROFILE_EN = """
            {
              "name": "Julio Flores",
              "label": "Senior Full Stack Engineer · backend focus",
              "tagline": "Java/Quarkus & NestJS · Healthcare interoperability (HL7/FHIR)",
              "city": "Monforte de Lemos",
              "countryCode": "ES",
              "summary": "I design backend microservices that process thousands of transactions a day in mission-critical healthcare environments…",
              "availability": "Open to backend / full stack roles in Spain or remote."
            }""";

    static final String IMPACT_ES = """
            [
              { "value": "+5.000", "text": "mensajes diarios en un proxy HL7 v2.3.1 → REST" },
              { "value": "+11 años", "text": "de SQL en migraciones de datos con stored procedures y triggers de auditoría" }
            ]""";

    static final String IMPACT_EN = """
            [
              { "value": "5,000+", "text": "daily messages through an HL7 v2.3.1 → REST proxy" },
              { "value": "11+ years", "text": "of SQL in data migrations using stored procedures and audit triggers" }
            ]""";

    static final String EXPERIENCE_LIST_ES = """
            [
              {
                "id": "sale-and-brick",
                "company": "Sale & Brick",
                "position": "Full Stack Developer · foco backend",
                "location": null,
                "period": { "start": "2017-10", "end": null, "current": true },
                "summary": "Progresión dentro de la misma empresa, de soporte a arquitectura de microservicios…",
                "phases": [
                  { "title": "Soporte y análisis funcional", "client": null },
                  { "title": "Liderazgo de implantación xHIS y Escritorio Clínico de DXC Technology", "client": "Swiss Medical Group y Finochetto" },
                  { "title": "Microservicios Quarkus / Spring Boot / NestJS", "client": null }
                ],
                "highlights": ["Modernizo sistemas monolíticos legacy con microservicios en Quarkus, Spring Boot y NestJS…"]
              },
              {
                "id": "central-madeirense",
                "company": "Central Madeirense",
                "position": "Especialista de gestión de eventos TI",
                "location": "Caracas, Venezuela",
                "period": { "start": "2014-03", "end": "2016-08", "current": false },
                "summary": "Monitorización de procesos críticos de transmisión y procesamiento de datos…",
                "phases": [],
                "highlights": []
              }
            ]""";

    static final String EXPERIENCE_ONE_ES = """
            {
              "id": "central-madeirense",
              "company": "Central Madeirense",
              "position": "Especialista de gestión de eventos TI",
              "location": "Caracas, Venezuela",
              "period": { "start": "2014-03", "end": "2016-08", "current": false },
              "summary": "Monitorización de procesos críticos de transmisión y procesamiento de datos…",
              "phases": [],
              "highlights": []
            }""";

    static final String SKILLS_ALL_ES = """
            [
              { "key": "backend", "name": "Backend", "keywords": ["Java 17+", "Quarkus", "Spring Boot", "NestJS"] },
              { "key": "health", "name": "Salud", "keywords": ["HL7 v2", "FHIR", "xHIS", "Escritorio Clínico"] }
            ]""";

    static final String SKILLS_GROUP_ES = """
            [
              { "key": "backend", "name": "Backend", "keywords": ["Java 17+", "Quarkus", "Spring Boot", "NestJS"] }
            ]""";

    static final String EDUCATION_ES = """
            {
              "education": [
                { "institution": "IUT Rufino Blanco Fombona", "studyType": "Técnico Superior Universitario (T.S.U.)", "area": "Informática" }
              ],
              "certificates": [
                { "name": "Oracle Database SQL Expert (1Z0-047)", "issuer": "Oracle University" }
              ],
              "courses": [
                { "name": "Quarkus", "issuer": "OpenWebinars" }
              ]
            }""";

    static final String CONTACT = """
            {
              "email": "julioflores781@gmail.com",
              "profiles": [
                { "network": "LinkedIn", "url": "https://www.linkedin.com/in/julio-flores-193482141/" },
                { "network": "GitHub", "url": "https://github.com/julioflores781" }
              ]
            }""";

    static final String RESUME_ES = """
            {
              "language": "es",
              "profile": { "name": "Julio Flores", "label": "Senior Full Stack Engineer · foco backend", "city": "Monforte de Lemos", "countryCode": "ES" },
              "contact": { "email": "julioflores781@gmail.com", "profiles": [ { "network": "GitHub", "url": "https://github.com/julioflores781" } ] },
              "impact": [ { "value": "+5.000", "text": "mensajes diarios en un proxy HL7 v2.3.1 → REST" } ],
              "experience": [ { "id": "sale-and-brick", "company": "Sale & Brick", "period": { "start": "2017-10", "end": null, "current": true } } ],
              "skills": [ { "key": "backend", "name": "Backend", "keywords": ["Java 17+", "Quarkus"] } ],
              "education": { "education": [], "certificates": [], "courses": [] },
              "languages": [ { "language": "Español", "fluency": "Nativo" }, { "language": "Inglés", "fluency": "Básico" } ]
            }""";

    static final String SERVICES_ES = """
            [
              {
                "id": "api-backend",
                "title": "APIs y backend",
                "summary": "Servicios en Quarkus o NestJS, bien documentados y listos para producción.",
                "price": { "from": 1500, "currency": "EUR" }
              },
              {
                "id": "modernizacion",
                "title": "Modernización de sistemas",
                "summary": "Llevar un sistema antiguo a una arquitectura actual sin parar el negocio.",
                "price": null
              }
            ]""";

    static final String SERVICES_EN = """
            [
              {
                "id": "api-backend",
                "title": "APIs and backend",
                "summary": "Quarkus or NestJS services, well documented and production-ready.",
                "price": { "from": 1500, "currency": "EUR" }
              },
              {
                "id": "modernizacion",
                "title": "System modernization",
                "summary": "Move a legacy system to a current architecture without stopping the business.",
                "price": null
              }
            ]""";

    static final String PROJECTS_ES = """
            [
              {
                "id": "turnos-clinica",
                "title": "Plataforma de turnos médicos",
                "summary": "Backend y web para que los pacientes reserven turnos y la clínica gestione su agenda.",
                "relation": "employer",
                "role": "Backend y web",
                "disclosure": "anonymous",
                "client": null,
                "clientLabel": "clínica privada",
                "period": { "start": "2021-03", "end": "2022-09", "current": false },
                "stack": ["Java", "Quarkus", "PostgreSQL", "React"],
                "highlights": ["Reservas sin solapamientos", "Avisos a los pacientes"],
                "links": []
              }
            ]""";

    static final String PROJECTS_EN = """
            [
              {
                "id": "turnos-clinica",
                "title": "Medical appointments platform",
                "summary": "Backend and web so patients can book appointments and the clinic can manage its agenda.",
                "relation": "employer",
                "role": "Backend and web",
                "disclosure": "anonymous",
                "client": null,
                "clientLabel": "private clinic",
                "period": { "start": "2021-03", "end": "2022-09", "current": false },
                "stack": ["Java", "Quarkus", "PostgreSQL", "React"],
                "highlights": ["Bookings without overlaps", "Patient reminders"],
                "links": []
              }
            ]""";

    static final String ARCHITECTURE_ES = """
            {
              "core": { "name": "Dominio", "description": "Las reglas del negocio, sin saber qué base de datos ni qué cola hay fuera." },
              "ports": [
                {
                  "id": "database",
                  "name": "Base de datos",
                  "description": "Dónde se guardan los datos.",
                  "adapters": [
                    { "id": "postgresql", "name": "PostgreSQL", "status": "in-this-api", "note": null },
                    { "id": "mongodb", "name": "MongoDB", "status": "production", "note": null }
                  ]
                }
              ]
            }""";

    static final String ARCHITECTURE_EN = """
            {
              "core": { "name": "Domain", "description": "The business rules, unaware of which database or queue sits outside." },
              "ports": [
                {
                  "id": "database",
                  "name": "Database",
                  "description": "Where the data is stored.",
                  "adapters": [
                    { "id": "postgresql", "name": "PostgreSQL", "status": "in-this-api", "note": null },
                    { "id": "mongodb", "name": "MongoDB", "status": "production", "note": null }
                  ]
                }
              ]
            }""";

    static final String PROBLEM_RESUME_NOT_FOUND = """
            {
              "type": "about:blank",
              "title": "CV no encontrado",
              "status": 404,
              "detail": "No hay un CV publicado en el idioma «fr».",
              "code": "RESUME_NOT_FOUND"
            }""";

    static final String PROBLEM_EXPERIENCE_NOT_FOUND = """
            {
              "type": "about:blank",
              "title": "Experiencia no encontrada",
              "status": 404,
              "detail": "No existe ninguna experiencia con el id «nope».",
              "code": "EXPERIENCE_NOT_FOUND"
            }""";

    static final String PROBLEM_EXPERIENCE_NOT_FOUND_EN = """
            {
              "type": "about:blank",
              "title": "Experience not found",
              "status": 404,
              "detail": "No experience with id 'nope'.",
              "code": "EXPERIENCE_NOT_FOUND"
            }""";

    static final String PROBLEM_SKILL_GROUP_NOT_FOUND = """
            {
              "type": "about:blank",
              "title": "Grupo de skills no encontrado",
              "status": 404,
              "detail": "No existe el grupo de skills «nope».",
              "code": "SKILL_GROUP_NOT_FOUND"
            }""";
}
