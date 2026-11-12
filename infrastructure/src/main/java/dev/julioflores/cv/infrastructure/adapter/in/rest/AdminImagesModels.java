package dev.julioflores.cv.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Cuerpos de la gestión de imágenes de proyectos. */
public final class AdminImagesModels {

    private AdminImagesModels() {}

    @Schema(name = "AdminImageUploadForm", description = "Formulario multipart de la subida: exactamente una parte `file`. "
            + "El nombre del archivo y su `Content-Type` se ignoran; el formato sale de los bytes.")
    public record UploadForm(
            @Schema(type = SchemaType.STRING, format = "binary", required = true,
                            description = "La imagen: JPEG, PNG, WebP o GIF de hasta 5 MB.")
                    String file) {}

    @Schema(name = "AdminImageUploaded", description = "La imagen subida. La clave se asocia a un proyecto al guardar su contenido.")
    public record UploadedResponse(
            @Schema(required = true, description = "Clave en el almacenamiento, generada por el servidor. Es lo que se guarda en "
                    + "`images[].key` del proyecto.", example = "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp") String key,
            @Schema(required = true, description = "URL pública (clave inmutable: sirve con caché de un año).",
                    example = "https://media.example.com/site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp") String url,
            @Schema(required = true, description = "Tipo detectado por los bytes.", example = "image/webp") String contentType,
            @Schema(required = true, description = "Tamaño en bytes.", example = "183422") long bytes,
            @Schema(required = true, description = "Ancho en píxeles.", example = "1280") int width,
            @Schema(required = true, description = "Alto en píxeles.", example = "720") int height) {}

    @Schema(name = "AdminImageEntry", description = "Una imagen del almacenamiento.")
    public record ImageItem(
            @Schema(required = true, description = "Clave en el almacenamiento.",
                    example = "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp") String key,
            @Schema(required = true, description = "URL pública.",
                    example = "https://media.example.com/site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp") String url,
            @Schema(required = true, description = "Tamaño en bytes.", example = "183422") long bytes,
            @Schema(required = true, description = "Última modificación (UTC).", example = "2026-11-10T18:00:00Z") Instant lastModified,
            @Schema(required = true, description = "`true` si alguna versión del contenido (activa o histórica) la referencia.",
                    example = "true") boolean referenced) {}

    @Schema(name = "AdminImageList", description = "Las imágenes de `site/projects/`, de la más nueva a la más antigua.")
    public record ImageList(@Schema(required = true, description = "Las imágenes.") List<ImageItem> images) {}

    @Schema(name = "AdminImageCleanup", description = "Resultado de la limpieza de imágenes huérfanas.")
    public record CleanupResponse(
            @Schema(required = true, description = "`true` si solo se informó, sin borrar nada.", example = "true") boolean dryRun,
            @Schema(required = true, description = "Claves borradas (vacío en `dryRun`).") List<String> deleted,
            @Schema(required = true, description = "Claves que se borrarían (vacío si no es `dryRun`).") List<String> wouldDelete,
            @Schema(required = true, description = "Cuántas no se tocan: referenciadas o dentro del periodo de gracia.", example = "4")
                    int kept) {}
}
