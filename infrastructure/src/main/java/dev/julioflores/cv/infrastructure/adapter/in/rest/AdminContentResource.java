package dev.julioflores.cv.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import dev.julioflores.cv.application.content.ContentVersion;
import dev.julioflores.cv.application.content.ContentVersionSummary;
import dev.julioflores.cv.application.content.EditableContent;
import dev.julioflores.cv.application.content.SaveContentCommand;
import dev.julioflores.cv.application.port.in.GetContentVersionUseCase;
import dev.julioflores.cv.application.port.in.GetEditableContentUseCase;
import dev.julioflores.cv.application.port.in.ListContentVersionsUseCase;
import dev.julioflores.cv.application.port.in.RollbackContentUseCase;
import dev.julioflores.cv.application.port.in.SaveEditableContentUseCase;
import dev.julioflores.cv.application.service.ContentTooLargeException;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminContentModels.BaseVersions;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminContentModels.ContentResponse;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminContentModels.Documents;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminContentModels.LanguageDocument;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminContentModels.Languages;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminContentModels.Parity;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminContentModels.RollbackRequest;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminContentModels.SaveRequest;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminContentModels.VersionList;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminContentModels.VersionSnapshot;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminContentModels.VersionSummary;
import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse;
import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse.ProblemError;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Edición del contenido de la web (servicios, proyectos y arquitectura): documento completo por tipo con ES y EN en una
 * sola operación atómica, historial inmutable, rollback y concurrencia optimista por versión base.
 */
@Path("/api/v1/admin/content")
@Tag(name = "Admin")
@SecurityRequirement(name = AdminAuthResource.BEARER)
public class AdminContentResource {

    private static final String KIND = "Tipo de contenido: `services`, `projects` o `architecture`. Otro valor da 404.";
    private static final String LANG = "Idioma del documento: `es` o `en`. Otro valor da 404.";

    private final GetEditableContentUseCase getContent;
    private final SaveEditableContentUseCase saveContent;
    private final ListContentVersionsUseCase listVersions;
    private final GetContentVersionUseCase getVersion;
    private final RollbackContentUseCase rollbackContent;
    private final ObjectMapper strictMapper;
    private final int maxBytes;

    public AdminContentResource(
            GetEditableContentUseCase getContent,
            SaveEditableContentUseCase saveContent,
            ListContentVersionsUseCase listVersions,
            GetContentVersionUseCase getVersion,
            RollbackContentUseCase rollbackContent,
            ObjectMapper mapper,
            @ConfigProperty(name = "cv.admin.content.max-bytes") int maxBytes) {
        this.getContent = getContent;
        this.saveContent = saveContent;
        this.listVersions = listVersions;
        this.getVersion = getVersion;
        this.rollbackContent = rollbackContent;
        // Estricto: ni campos desconocidos ni coerción silenciosa de escalares ("1" por 1, 5 por "5").
        this.strictMapper = configureStrict(mapper.copy());
        this.maxBytes = maxBytes;
    }

    @GET
    @Path("/{kind}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "adminGetContent",
            summary = "Lee un contenido editable (ES y EN)",
            description = "Devuelve lo activo del tipo en español e inglés, en su forma almacenada (con `client` en los proyectos "
                    + "públicos), y si ambos tienen la misma estructura (`parity`). Sin nada publicado: `version` 0 y `content` "
                    + "nulo, no un error.")
    @Parameter(name = "kind", description = KIND, example = "services")
    @APIResponse(responseCode = "200", description = "Lo activo en ES y EN.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ContentResponse.class),
                    examples = {
                        @ExampleObject(name = "published", value = AdminContentExamples.CONTENT),
                        @ExampleObject(name = "nothingPublished", value = AdminContentExamples.CONTENT_EMPTY)
                    }))
    @APIResponse(responseCode = "404", description = "El tipo no existe (`NOT_FOUND`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "unknownKind", value = AdminContentExamples.NOT_FOUND)))
    public ContentResponse get(@PathParam("kind") String kind) {
        return response(getContent.get(kind));
    }

    @PUT
    @Path("/{kind}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "adminSaveContent",
            summary = "Guarda un contenido editable (ES y EN a la vez)",
            description = "Valida y publica una versión nueva en español e inglés en una sola transacción: o quedan las dos o "
                    + "ninguna. Las versiones anteriores no se tocan (se pueden recuperar con el rollback). `baseVersions` es la "
                    + "versión activa que el editor tenía delante; si otra persona publicó antes, `409`. Los campos desconocidos "
                    + "se rechazan, un proyecto anónimo no puede traer `client`, y ES y EN deben tener los mismos ids, el mismo "
                    + "orden y los mismos campos neutros (precio, relación, visibilidad, cliente, fechas, estado). "
                    + "Tras confirmar, la caché de la API pública se invalida: `GET /api/v1/{kind}` ve el cambio al instante.")
    @Parameter(name = "kind", description = KIND, example = "services")
    @RequestBody(required = true, description = "Versiones base, nota opcional y los dos documentos completos.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SaveRequest.class),
                    examples = @ExampleObject(name = "services", value = AdminContentExamples.SAVE_REQUEST)))
    @APIResponse(responseCode = "200", description = "Guardado: lo activo ahora (las versiones nuevas).",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ContentResponse.class),
                    examples = @ExampleObject(name = "saved", value = AdminContentExamples.CONTENT)))
    @APIResponse(responseCode = "400", description = "Falta el cuerpo, `baseVersions` o `documents`, o el JSON está mal formado "
            + "o trae campos desconocidos fuera de los documentos (`BAD_REQUEST`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "badRequest", value = AdminExamples.BAD_REQUEST)))
    @APIResponse(responseCode = "404", description = "El tipo no existe (`NOT_FOUND`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "unknownKind", value = AdminContentExamples.NOT_FOUND)))
    @APIResponse(responseCode = "409", description = "Alguien publicó antes: una versión base no es la activa "
            + "(`CONTENT_VERSION_CONFLICT`). No se guarda nada.",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "conflict", value = AdminContentExamples.CONFLICT)))
    @APIResponse(responseCode = "413", description = "El cuerpo supera el límite de contenido (`CONTENT_TOO_LARGE`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "tooLarge", value = AdminContentExamples.TOO_LARGE)))
    @APIResponse(responseCode = "422", description = "El contenido incumple las reglas (`CONTENT_INVALID`, con `errors`) o "
            + "ES y EN no tienen la misma estructura (`CONTENT_PARITY_BROKEN`, con `errors`). No se guarda nada.",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = {
                        @ExampleObject(name = "invalid", summary = "Contenido no válido", value = AdminContentExamples.INVALID),
                        @ExampleObject(name = "parityBroken", summary = "Paridad rota", value = AdminContentExamples.PARITY)
                    }))
    public ContentResponse save(@PathParam("kind") String kind, byte[] body) throws IOException {
        if (body.length > maxBytes) {
            throw new ContentTooLargeException(maxBytes);
        }
        SaveRequest request = strict(body, SaveRequest.class);
        BaseVersions bases = request == null ? null : request.baseVersions();
        Documents documents = request == null ? null : request.documents();
        if (bases == null || bases.es() == null || bases.en() == null || bases.es() < 0 || bases.en() < 0 || documents == null) {
            throw new BadRequestException();
        }
        return response(saveContent.save(kind,
                new SaveContentCommand(bases.es(), bases.en(), request.comment(), documents.es(), documents.en())));
    }

    @GET
    @Path("/{kind}/{lang}/versions")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "adminListContentVersions",
            summary = "Historial de un idioma",
            description = "Las versiones del tipo en ese idioma, de la más nueva a la más antigua, con cuál está activa y quién la "
                    + "publicó (`seed` o `admin`).")
    @Parameter(name = "kind", description = KIND, example = "services")
    @Parameter(name = "lang", description = LANG, example = "es")
    @Parameter(name = "limit", description = "Cuántas versiones devolver, entre 1 y 200.", example = "50")
    @APIResponse(responseCode = "200", description = "El historial.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VersionList.class),
                    examples = @ExampleObject(name = "versions", value = AdminContentExamples.VERSIONS)))
    @APIResponse(responseCode = "404", description = "El tipo o el idioma no existen (`NOT_FOUND`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "unknownKind", value = AdminContentExamples.NOT_FOUND)))
    public VersionList versions(
            @PathParam("kind") String kind,
            @PathParam("lang") String lang,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        List<ContentVersionSummary> versions = listVersions.list(kind, lang, limit);
        return new VersionList(kind, lang, versions.stream()
                .map(v -> new VersionSummary(v.version(), v.active(), v.publishedAt(), v.source(), v.comment())).toList());
    }

    @GET
    @Path("/{kind}/{lang}/versions/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "adminGetContentVersion",
            summary = "Una versión concreta",
            description = "La instantánea de una versión (activa o histórica) con su contenido, para compararla o recuperarla.")
    @Parameter(name = "kind", description = KIND, example = "services")
    @Parameter(name = "lang", description = LANG, example = "es")
    @Parameter(name = "version", description = "Número de versión.", example = "4")
    @APIResponse(responseCode = "200", description = "La versión pedida.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VersionSnapshot.class),
                    examples = @ExampleObject(name = "snapshot", value = AdminContentExamples.SNAPSHOT)))
    @APIResponse(responseCode = "404", description = "El tipo o el idioma no existen (`NOT_FOUND`) o la versión no existe "
            + "(`CONTENT_VERSION_NOT_FOUND`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = {
                        @ExampleObject(name = "versionNotFound", value = AdminContentExamples.VERSION_NOT_FOUND),
                        @ExampleObject(name = "unknownKind", value = AdminContentExamples.NOT_FOUND)
                    }))
    public VersionSnapshot version(
            @PathParam("kind") String kind, @PathParam("lang") String lang, @PathParam("version") int version) {
        ContentVersion found = getVersion.get(kind, lang, version);
        return new VersionSnapshot(kind, lang, found.version(), found.active(), found.publishedAt(), found.source(),
                found.comment(), found.content());
    }

    @POST
    @Path("/{kind}/{lang}/rollback")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "adminRollbackContent",
            summary = "Vuelve a una versión anterior de un idioma",
            description = "Reactiva una versión existente: mueve el puntero de la activa, no crea una versión nueva (un guardado "
                    + "posterior toma la siguiente a la mayor existente, así el historial lo conserva todo). La versión destino "
                    + "se valida con las reglas de hoy: si ya no las cumple, `422` y no se reactiva. `baseVersion` es la activa "
                    + "que ve el panel; si no coincide, `409`. Invalida la caché de la API pública. La respuesta es la misma "
                    + "que la de `GET /content/{kind}`, con la paridad actual (un rollback de un solo idioma puede romperla).")
    @Parameter(name = "kind", description = KIND, example = "services")
    @Parameter(name = "lang", description = LANG, example = "es")
    @RequestBody(required = true, description = "La versión activa que ve el panel y la que se quiere reactivar.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = RollbackRequest.class),
                    examples = @ExampleObject(name = "rollback", value = AdminContentExamples.ROLLBACK_REQUEST)))
    @APIResponse(responseCode = "200", description = "Hecho: lo activo ahora.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ContentResponse.class),
                    examples = @ExampleObject(name = "rolledBack", value = AdminContentExamples.CONTENT)))
    @APIResponse(responseCode = "400", description = "Falta el cuerpo o algún campo, o el JSON está mal formado (`BAD_REQUEST`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "badRequest", value = AdminExamples.BAD_REQUEST)))
    @APIResponse(responseCode = "404", description = "El tipo o el idioma no existen (`NOT_FOUND`) o la versión destino no existe "
            + "(`CONTENT_VERSION_NOT_FOUND`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = {
                        @ExampleObject(name = "versionNotFound", value = AdminContentExamples.VERSION_NOT_FOUND),
                        @ExampleObject(name = "unknownKind", value = AdminContentExamples.NOT_FOUND)
                    }))
    @APIResponse(responseCode = "409", description = "`baseVersion` no es la versión activa (`CONTENT_VERSION_CONFLICT`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "conflict", value = AdminContentExamples.CONFLICT)))
    @APIResponse(responseCode = "422", description = "La versión destino ya no cumple las reglas de validación "
            + "(`CONTENT_INVALID`, con `errors`). No se reactiva.",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "invalid", value = AdminContentExamples.INVALID)))
    public ContentResponse rollback(@PathParam("kind") String kind, @PathParam("lang") String lang, byte[] body)
            throws IOException {
        RollbackRequest request = strict(body, RollbackRequest.class);
        if (request == null || request.baseVersion() == null || request.targetVersion() == null
                || request.baseVersion() < 0 || request.targetVersion() < 1) {
            throw new BadRequestException();
        }
        return response(rollbackContent.rollback(kind, lang, request.baseVersion(), request.targetVersion()));
    }

    /** Un lector sin coerciones: un número donde va un texto, un texto o decimal donde va un entero, etc. fallan. */
    private static ObjectMapper configureStrict(ObjectMapper copy) {
        copy.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        copy.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        copy.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        copy.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return copy;
    }

    /** DTO estrictos: un campo que no existe en el contrato es un 400, no se ignora en silencio. */
    private <T> T strict(byte[] body, Class<T> type) throws IOException {
        return strictMapper.readValue(body, type);
    }

    private static ContentResponse response(EditableContent content) {
        return new ContentResponse(
                content.kind().value(),
                new Languages(document(content.es()), document(content.en())),
                new Parity(content.parityOk(), content.parity().stream()
                        .map(v -> new ProblemError(v.path(), v.code().name())).toList()));
    }

    private static LanguageDocument document(ContentVersion version) {
        return new LanguageDocument(
                version.version(), version.publishedAt(), version.source(), version.comment(), version.content());
    }
}
