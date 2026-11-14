package dev.julioflores.cv.infrastructure.adapter.in.rest;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@OpenAPIDefinition(
        info = @Info(
                title = "Julio Flores · CV as a Service",
                version = "v1",
                description = """
                        Mi CV como una API de solo lectura: el mismo contenido que ves en la web, \
                        servido como un servicio.

                        ## Idioma
                        Todos los endpoints aceptan `?lang=es|en`. Si falta, se usa la cabecera \
                        `Accept-Language`; si tampoco hay, se responde en español (`es`).

                        ## Errores
                        Todos los errores se devuelven como `application/problem+json` (RFC 7807) \
                        con los campos `type`, `title`, `status`, `detail` y `code`.

                        - `code` es un identificador **estable** (por ejemplo `EXPERIENCE_NOT_FOUND`): \
                        úsalo para decidir en tu cliente. No cambia con el idioma.
                        - `title` y `detail` son texto para personas y van **en el idioma pedido** \
                        (`?lang` o `Accept-Language`; español por defecto).

                        | Estado | Cuándo |
                        |---|---|
                        | `404` | El recurso pedido no existe (id de experiencia o grupo de skills desconocido), \
                        no hay CV publicado en ese idioma, o la ruta no existe. |
                        | `400` / `413` / `422` | Solo en la demo HL7: cuerpo vacío o formato desconocido (`400`), \
                        mensaje de más de 64 KB (`413`), mensaje ilegible o de un tipo no soportado (`422`). |
                        | `401` / `403` / `429` | Solo en `/api/v1/admin`: sesión ausente, caducada o revocada (`401`), contraseña \
                        actual incorrecta (`403`), cuenta bloqueada o límite de intentos (`429`). |
                        | `405` | Se usó un método no admitido por el endpoint (los de lectura solo admiten `GET`). |
                        | `500` | Error inesperado. El detalle no revela información interna. |

                        Los códigos posibles y su estado HTTP están listados en el esquema `Problem`.

                        ## Caché
                        Las respuestas `200` llevan `Cache-Control: public, max-age=300` y \
                        `Vary: Accept-Language`. Los errores no se cachean.

                        ## Administración
                        `/api/v1/admin` es la zona privada del dueño (etiqueta **Admin**): exige \
                        `Authorization: Bearer <jwt>`, que solo emite `POST /api/v1/admin/auth/login`. \
                        Sus respuestas llevan `Cache-Control: no-store`.

                        ## Privacidad
                        La API solo expone datos profesionales públicos. No incluye teléfono, \
                        documento de identidad ni fecha de nacimiento.""",
                contact = @Contact(
                        name = "Julio Flores",
                        email = "julioflores781@gmail.com",
                        url = "https://github.com/julioflores781")),
        tags = {
            @Tag(
                    name = "Admin",
                    description = "Zona privada del administrador (un solo usuario): sesión por contraseña con JWT, "
                            + "bloqueo tras intentos fallidos y cambio de contraseña. Requiere `bearerAuth`, salvo el login."),
            @Tag(
                    name = "Resume",
                    description = "Contenido del CV: perfil, cifras, experiencia, skills, formación y contacto."),
            @Tag(
                    name = "HL7",
                    description = "Demo de interoperabilidad sanitaria: traduce mensajes HL7 v2 a JSON o FHIR R4."),
            @Tag(
                    name = "Contact",
                    description = "Formulario de contacto: deja un mensaje y el dueño recibe un aviso."),
            @Tag(
                    name = "Content",
                    description = "Contenido editable de la web, guardado en la base de datos: servicios, proyectos y el diagrama de arquitectura.")
        })
@SecurityScheme(
        securitySchemeName = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT HS256 que devuelve `POST /api/v1/admin/auth/login`. Caduca (8 h por defecto) y deja de valer "
                + "al cambiar la contraseña o cerrar todas las sesiones.")
public class OpenApiConfig extends Application {}
