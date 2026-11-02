package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.port.in.GetResumeUseCase;
import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse;
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

@Path("/api/v1/resume")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Resume")
public class ResumeResource {

    private final GetResumeUseCase getResume;

    public ResumeResource(GetResumeUseCase getResume) {
        this.getResume = getResume;
    }

    @GET
    @Operation(
            operationId = "getResume",
            summary = "El CV completo en un solo documento",
            description = "Reúne en una sola respuesta el perfil, el contacto, las cifras, la experiencia, "
                    + "las skills, la formación y los idiomas. Es lo que consume la web en su build. "
                    + "El ejemplo está abreviado.")
    @APIResponse(
            responseCode = "200",
            description = "CV completo en el idioma pedido.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ApiModels.ResumeDto.class),
                    examples = @ExampleObject(value = OpenApiExamples.RESUME_ES)))
    @APIResponse(
            responseCode = "404",
            description = "No hay CV publicado en el idioma pedido.",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(value = OpenApiExamples.PROBLEM_RESUME_NOT_FOUND)))
    public ApiModels.ResumeDto resume(@BeanParam LanguageParams params) {
        return ApiModels.toDto(getResume.execute(params.language()));
    }
}
