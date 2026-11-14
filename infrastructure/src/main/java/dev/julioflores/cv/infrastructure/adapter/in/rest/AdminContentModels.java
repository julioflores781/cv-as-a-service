package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse.ProblemError;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Cuerpos de la edición de contenido. Los documentos son la forma almacenada (la de `db/sample`), no el DTO público. */
public final class AdminContentModels {

    private AdminContentModels() {}

    private static final String CONTENT = "El documento tal como se guarda: la forma de `db/sample/<tipo>.<idioma>.json` "
            + "(`services`, `projects` o `core` + `ports`). Incluye `client` en los proyectos públicos.";

    @Schema(name = "AdminContentLanguage", description = "Lo activo de un tipo en un idioma. Sin nada publicado: "
            + "`version` 0 y `content` nulo (no es un error).")
    public record LanguageDocument(
            @Schema(required = true, description = "Versión activa; 0 si no hay nada publicado.", example = "3") int version,
            @Schema(description = "Cuándo se publicó (UTC); nulo si no hay nada publicado.", example = "2026-11-10T18:02:11Z")
                    Instant publishedAt,
            @Schema(description = "Quién la publicó: `seed` (el script) o `admin` (el panel).", example = "admin") String source,
            @Schema(description = "Nota del guardado (hasta 200 caracteres), si la hubo.", example = "Cambio de precio")
                    String comment,
            @Schema(type = SchemaType.OBJECT, nullable = true, description = CONTENT) Map<String, Object> content) {}

    @Schema(name = "AdminContentLanguages", description = "Lo activo en español y en inglés.")
    public record Languages(
            @Schema(required = true) LanguageDocument es, @Schema(required = true) LanguageDocument en) {}

    @Schema(name = "AdminContentParity", description = "Si español e inglés tienen la misma estructura (mismos ids, orden y "
            + "campos neutros). Es informativo en las lecturas; al guardar, la paridad rota se rechaza.")
    public record Parity(
            @Schema(required = true, description = "`true` si hay paridad.", example = "true") boolean ok,
            @Schema(required = true, description = "Cada diferencia, con su ruta (sin prefijo de idioma) y el código `PARITY_MISMATCH`.")
                    List<ProblemError> differences) {}

    @Schema(name = "AdminContent", description = "Lo activo de un tipo de contenido en ES y EN, con su paridad.")
    public record ContentResponse(
            @Schema(required = true, description = "`services`, `projects` o `architecture`.", example = "services") String kind,
            @Schema(required = true) Languages languages,
            @Schema(required = true) Parity parity) {}

    @Schema(name = "AdminBaseVersions", description = "La versión activa que el editor tenía delante, por idioma (0 si no había nada).")
    public record BaseVersions(
            @Schema(required = true, minimum = "0", example = "3") Integer es,
            @Schema(required = true, minimum = "0", example = "3") Integer en) {}

    @Schema(name = "AdminDocuments", description = "El documento completo de cada idioma.")
    public record Documents(
            @Schema(type = SchemaType.OBJECT, required = true, description = CONTENT) Map<String, Object> es,
            @Schema(type = SchemaType.OBJECT, required = true, description = CONTENT) Map<String, Object> en) {}

    @Schema(name = "AdminContentSave", description = "Guardado atómico bilingüe: crea una versión nueva en ES y EN, o ninguna.")
    public record SaveRequest(
            @Schema(required = true) BaseVersions baseVersions,
            @Schema(description = "Nota opcional (hasta 200 caracteres).", maxLength = 200, example = "Cambio de precio")
                    String comment,
            @Schema(required = true) Documents documents) {}

    @Schema(name = "AdminContentVersion", description = "Una fila del historial de un idioma.")
    public record VersionSummary(
            @Schema(required = true, description = "Número de versión.", example = "5") int version,
            @Schema(required = true, description = "`true` si es la que sirve la API pública.", example = "false") boolean active,
            @Schema(required = true, description = "Cuándo se publicó (UTC).", example = "2026-11-10T18:02:11Z") Instant publishedAt,
            @Schema(required = true, description = "`seed` (el script) o `admin` (el panel).", example = "admin") String source,
            @Schema(description = "Nota del guardado, si la hubo.", example = "Cambio de precio") String comment) {}

    @Schema(name = "AdminContentVersions", description = "Historial de un idioma, de la versión más nueva a la más antigua.")
    public record VersionList(
            @Schema(required = true, description = "Tipo de contenido.", example = "services") String kind,
            @Schema(required = true, description = "Idioma del historial.", example = "es") String lang,
            @Schema(required = true, description = "Las versiones, de la más nueva a la más antigua.") List<VersionSummary> versions) {}

    @Schema(name = "AdminContentSnapshot", description = "Una versión concreta con su contenido.")
    public record VersionSnapshot(
            @Schema(required = true, description = "Tipo de contenido.", example = "services") String kind,
            @Schema(required = true, description = "Idioma de la versión.", example = "es") String lang,
            @Schema(required = true, description = "Número de versión.", example = "4") int version,
            @Schema(required = true, description = "`true` si es la que sirve la API pública.", example = "true") boolean active,
            @Schema(description = "Cuándo se publicó (UTC).", example = "2026-11-10T18:02:11Z") Instant publishedAt,
            @Schema(description = "`seed` (el script) o `admin` (el panel).", example = "admin") String source,
            @Schema(description = "Nota del guardado, si la hubo.", example = "Cambio de precio") String comment,
            @Schema(type = SchemaType.OBJECT, required = true, description = CONTENT) Map<String, Object> content) {}

    @Schema(name = "AdminContentRollback", description = "Reactivar una versión existente de un idioma.")
    public record RollbackRequest(
            @Schema(required = true, minimum = "0", description = "La versión activa que ve el panel; si no coincide, 409.",
                    example = "5") Integer baseVersion,
            @Schema(required = true, minimum = "1", description = "La versión que se quiere reactivar.", example = "3")
                    Integer targetVersion) {}
}
