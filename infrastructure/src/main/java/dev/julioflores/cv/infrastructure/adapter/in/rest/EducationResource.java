package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.port.in.GetEducationUseCase;
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

@Path("/api/v1/education")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Resume")
public class EducationResource {

    private final GetEducationUseCase getEducation;

    public EducationResource(GetEducationUseCase getEducation) {
        this.getEducation = getEducation;
    }

    @GET
    @Operation(
            operationId = "getEducation",
            summary = "Formación, certificaciones y cursos",
            description = "Formación reglada, certificaciones profesionales y cursos, en tres listas separadas.")
    @APIResponse(
            responseCode = "200",
            description = "Formación en el idioma pedido. Cada lista puede estar vacía.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ApiModels.QualificationsDto.class),
                    examples = @ExampleObject(value = OpenApiExamples.EDUCATION_ES)))
    @APIResponse(
            responseCode = "404",
            description = "No hay CV publicado en el idioma pedido.",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(value = OpenApiExamples.PROBLEM_RESUME_NOT_FOUND)))
    public ApiModels.QualificationsDto education(@BeanParam LanguageParams params) {
        return ApiModels.toDto(getEducation.execute(params.language()));
    }
}
