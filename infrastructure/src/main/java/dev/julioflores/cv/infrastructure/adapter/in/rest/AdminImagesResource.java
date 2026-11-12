package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.image.CleanupResult;
import dev.julioflores.cv.application.image.ImageEntry;
import dev.julioflores.cv.application.image.UploadedImage;
import dev.julioflores.cv.application.port.in.CleanupOrphanImagesUseCase;
import dev.julioflores.cv.application.port.in.DeleteImageUseCase;
import dev.julioflores.cv.application.port.in.ListImagesUseCase;
import dev.julioflores.cv.application.port.in.UploadProjectImageUseCase;
import dev.julioflores.cv.application.service.ImageInvalidException;
import dev.julioflores.cv.application.service.ImageStorageUnavailableException;
import dev.julioflores.cv.application.service.ImageTooLargeException;
import dev.julioflores.cv.application.service.UploadProjectImageService;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminImagesModels.CleanupResponse;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminImagesModels.ImageItem;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminImagesModels.ImageList;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminImagesModels.UploadForm;
import dev.julioflores.cv.infrastructure.adapter.in.rest.AdminImagesModels.UploadedResponse;
import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Imágenes de los proyectos en Cloudflare R2. La subida solo deja el objeto en el almacenamiento y devuelve su clave; la
 * asociación a un proyecto ocurre al guardar su contenido. Es la única ruta de la API con un límite de cuerpo alto
 * (ver {@code RequestBodyLimits}).
 */
@Path("/api/v1/admin/images")
@Tag(name = "Admin")
@SecurityRequirement(name = AdminAuthResource.BEARER)
public class AdminImagesResource {

    private final UploadProjectImageUseCase uploadImage;
    private final ListImagesUseCase listImages;
    private final DeleteImageUseCase deleteImage;
    private final CleanupOrphanImagesUseCase cleanupImages;

    public AdminImagesResource(
            UploadProjectImageUseCase uploadImage,
            ListImagesUseCase listImages,
            DeleteImageUseCase deleteImage,
            CleanupOrphanImagesUseCase cleanupImages) {
        this.uploadImage = uploadImage;
        this.listImages = listImages;
        this.deleteImage = deleteImage;
        this.cleanupImages = cleanupImages;
    }

    // Sin @Consumes a propósito: con él, una petición de otro tipo se rechazaría con 415 al elegir la ruta, ANTES de la
    // autenticación (CA5: sin token, siempre 401). Un cuerpo que no es multipart da 400 IMAGE_INVALID ya con sesión.
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "adminUploadImage",
            summary = "Sube una imagen de proyecto",
            description = "Multipart con una única parte `file`. Acepta JPEG, PNG, WebP y GIF de hasta 5 MB, decididos por los "
                    + "**bytes** del archivo: el nombre y el `Content-Type` que mande el cliente se ignoran, y un `.jpg` que "
                    + "en realidad es HTML o un SVG se rechaza. La clave `site/projects/<uuid>.<ext>` la genera el servidor. "
                    + "La subida solo deja el objeto en el almacenamiento: para asociarlo a un proyecto `own` + `public` hay que "
                    + "guardar su contenido (`PUT /content/projects`) con `images[].key`. Si el almacenamiento no está "
                    + "configurado o no responde, `503`: guardar contenido no depende de él.")
    @RequestBody(required = true, description = "Formulario con la parte `file`.",
            content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA, schema = @Schema(implementation = UploadForm.class)))
    @APIResponse(responseCode = "201", description = "Subida: dónde quedó y cómo es la imagen.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UploadedResponse.class),
                    examples = @ExampleObject(name = "uploaded", value = AdminImagesExamples.UPLOADED)))
    @APIResponse(responseCode = "400", description = "La petición no es multipart, falta la parte `file`, hay más de una, está vacía "
            + "o sus bytes no son una imagen legible (`IMAGE_INVALID`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "invalid", value = AdminImagesExamples.IMAGE_INVALID)))
    @APIResponse(responseCode = "413", description = "La imagen supera 5 MB (`IMAGE_TOO_LARGE`). Un cuerpo de más de 6 MB lo corta "
            + "el servidor con un 413 sin cuerpo.",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "tooLarge", value = AdminImagesExamples.IMAGE_TOO_LARGE)))
    @APIResponse(responseCode = "415", description = "Un tipo que se reconoce pero no se admite, como SVG o HEIC "
            + "(`IMAGE_UNSUPPORTED_TYPE`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "unsupported", value = AdminImagesExamples.IMAGE_UNSUPPORTED)))
    @APIResponse(responseCode = "503", description = "El almacenamiento no está configurado o no responde "
            + "(`IMAGE_STORAGE_UNAVAILABLE`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "unavailable", value = AdminImagesExamples.IMAGE_STORAGE_UNAVAILABLE)))
    public Response upload(@RestForm("file") List<FileUpload> files) throws IOException {
        if (files == null || files.size() != 1) {
            throw new ImageInvalidException("expected exactly one 'file' part");
        }
        FileUpload file = files.get(0);
        // El tamaño se mira antes de leer nada a memoria; el archivo temporal lo borra el servidor al terminar la petición.
        if (file.size() > UploadProjectImageService.MAX_BYTES) {
            throw new ImageTooLargeException(UploadProjectImageService.MAX_BYTES);
        }
        byte[] content;
        try {
            content = Files.readAllBytes(file.uploadedFile());
        } catch (IOException e) {
            throw new ImageStorageUnavailableException("could not read the uploaded file", e);
        }
        UploadedImage uploaded = uploadImage.upload(content);
        return Response.status(Response.Status.CREATED).entity(new UploadedResponse(
                uploaded.key(), uploaded.url(), uploaded.contentType(), uploaded.bytes(), uploaded.width(), uploaded.height())).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "adminListImages",
            summary = "Lista las imágenes de proyectos",
            description = "Las imágenes de `site/projects/` del almacenamiento (se pagina por dentro), de la más nueva a la más "
                    + "antigua, y si alguna versión del contenido (activa o histórica) las referencia.")
    @APIResponse(responseCode = "200", description = "Las imágenes.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ImageList.class),
                    examples = @ExampleObject(name = "images", value = AdminImagesExamples.LIST)))
    @APIResponse(responseCode = "503", description = "El almacenamiento no está configurado o no responde "
            + "(`IMAGE_STORAGE_UNAVAILABLE`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "unavailable", value = AdminImagesExamples.IMAGE_STORAGE_UNAVAILABLE)))
    public ImageList list() {
        List<ImageEntry> images = listImages.list();
        return new ImageList(images.stream()
                .map(i -> new ImageItem(i.key(), i.url(), i.bytes(), i.lastModified(), i.referenced())).toList());
    }

    @DELETE
    @Path("/{id}")
    @Operation(
            operationId = "adminDeleteImage",
            summary = "Borra una imagen",
            description = "Borra la imagen cuyo UUID es `id` (la parte `<uuid>` de la clave). Si **cualquier** versión del "
                    + "contenido, activa o histórica, la referencia, `409`: un rollback no debe dejar imágenes rotas.")
    @Parameter(name = "id", description = "UUID de la imagen, en minúsculas.", example = "9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa")
    @APIResponse(responseCode = "204", description = "Borrada.")
    @APIResponse(responseCode = "404", description = "No existe esa imagen (`IMAGE_NOT_FOUND`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "notFound", value = AdminImagesExamples.IMAGE_NOT_FOUND)))
    @APIResponse(responseCode = "409", description = "Alguna versión del contenido la referencia (`IMAGE_IN_USE`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "inUse", value = AdminImagesExamples.IMAGE_IN_USE)))
    @APIResponse(responseCode = "503", description = "El almacenamiento no está configurado o no responde "
            + "(`IMAGE_STORAGE_UNAVAILABLE`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "unavailable", value = AdminImagesExamples.IMAGE_STORAGE_UNAVAILABLE)))
    public Response delete(@PathParam("id") String id) {
        deleteImage.delete(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/cleanup")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "adminCleanupImages",
            summary = "Borra las imágenes huérfanas",
            description = "Borra las imágenes que ninguna versión del contenido referencia y que son más antiguas que "
                    + "`olderThanHours` (mínimo 1: una imagen recién subida aún no está guardada en ningún proyecto). "
                    + "Por defecto es un ensayo (`dryRun=true`): informa de lo que borraría y no borra nada. Borra por lotes "
                    + "de hasta 1000.")
    @Parameter(name = "dryRun", description = "Si es `true` (por defecto), solo informa.", example = "true")
    @Parameter(name = "olderThanHours", description = "Gracia en horas (por defecto 24; un valor menor que 1 se sube a 1).",
            example = "24")
    @APIResponse(responseCode = "200", description = "Lo borrado (o lo que se borraría).",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CleanupResponse.class),
                    examples = {
                        @ExampleObject(name = "dryRun", summary = "Ensayo", value = AdminImagesExamples.CLEANUP_DRY_RUN),
                        @ExampleObject(name = "done", summary = "Borrado real", value = AdminImagesExamples.CLEANUP_DONE)
                    }))
    @APIResponse(responseCode = "503", description = "El almacenamiento no está configurado o no responde "
            + "(`IMAGE_STORAGE_UNAVAILABLE`).",
            content = @Content(mediaType = ProblemResponse.MEDIA_TYPE, schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(name = "unavailable", value = AdminImagesExamples.IMAGE_STORAGE_UNAVAILABLE)))
    public CleanupResponse cleanup(
            @QueryParam("dryRun") @DefaultValue("true") boolean dryRun,
            @QueryParam("olderThanHours") @DefaultValue("24") int olderThanHours) {
        CleanupResult result = cleanupImages.cleanup(dryRun, olderThanHours);
        return new CleanupResponse(result.dryRun(), result.deleted(), result.wouldDelete(), result.kept());
    }
}
