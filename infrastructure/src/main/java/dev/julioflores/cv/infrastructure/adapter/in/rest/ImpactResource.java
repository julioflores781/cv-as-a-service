package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.port.in.GetImpactUseCase;
import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/impact")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Resume")
public class ImpactResource {

    private final GetImpactUseCase getImpact;

    public ImpactResource(GetImpactUseCase getImpact) {
        this.getImpact = getImpact;
    }

    @GET
    @Operation(
            operationId = "getImpact",
            summary = "Cifras en producción",
            description = "Lista de cifras de impacto de los sistemas en los que he trabajado "
                    + "(volumen de mensajes, usuarios, microservicios…). Cada cifra lleva su explicación.")
    @APIResponse(
            responseCode = "200",
            description = "Cifras de impacto en el idioma pedido. La lista nunca es nula, pero puede estar vacía.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY, implementation = ApiModels.ImpactDto.class),
                    examples = {
                        @ExampleObject(name = "es", summary = "Español", value = OpenApiExamples.IMPACT_ES),
                        @ExampleObject(name = "en", summary = "Inglés (`?lang=en`)", value = OpenApiExamples.IMPACT_EN)
                    }))
    @APIResponse(
            responseCode = "404",
            description = "No hay CV publicado en el idioma pedido.",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(value = OpenApiExamples.PROBLEM_RESUME_NOT_FOUND)))
    public List<ApiModels.ImpactDto> impact(@BeanParam LanguageParams params) {
        return ApiModels.map(getImpact.execute(params.language()), ApiModels::toDto);
    }
}
