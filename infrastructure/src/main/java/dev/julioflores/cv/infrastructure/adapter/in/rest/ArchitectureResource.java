package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.port.in.GetArchitectureUseCase;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/architecture")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Content")
public class ArchitectureResource {

    private final GetArchitectureUseCase getArchitecture;

    public ArchitectureResource(GetArchitectureUseCase getArchitecture) {
        this.getArchitecture = getArchitecture;
    }

    @GET
    @Operation(
            operationId = "getArchitecture",
            summary = "Diagrama de arquitectura hexagonal",
            description = "El núcleo del dominio y los puertos (base de datos, colas, notificaciones…) con los adaptadores que se pueden enchufar a cada uno sin tocar el dominio. Cada adaptador indica si está implementado en esta API, usado en producción o solo probado. Si todavía no hay nada publicado en ese idioma responde 200 con contenido vacío, no un error.")
    @APIResponse(
            responseCode = "200",
            description = "El diagrama en el idioma pedido. Sin puertos si no hay nada publicado.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ApiModels.ArchitectureDto.class),
                    examples = {
                        @ExampleObject(name = "es", summary = "Español", value = OpenApiExamples.ARCHITECTURE_ES),
                        @ExampleObject(name = "en", summary = "Inglés (`?lang=en`)", value = OpenApiExamples.ARCHITECTURE_EN)
                    }))
    public ApiModels.ArchitectureDto architecture(@BeanParam LanguageParams params) {
        return ApiModels.toDto(getArchitecture.execute(params.language()));
    }
}
