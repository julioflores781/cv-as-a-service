package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.port.in.GetExperienceUseCase;
import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/experience")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Resume")
public class ExperienceResource {

    private final GetExperienceUseCase getExperience;

    public ExperienceResource(GetExperienceUseCase getExperience) {
        this.getExperience = getExperience;
    }

    @GET
    @Operation(
            operationId = "listExperience",
            summary = "Experiencia, de más reciente a más antigua",
            description = "Trayectoria laboral completa. Cada entrada incluye su periodo (`current` es verdadero "
                    + "si sigue en curso), las fases de progresión interna y los logros. "
                    + "Usa el `id` de cada entrada con `/experience/{id}`.")
    @APIResponse(
            responseCode = "200",
            description = "Experiencia en el idioma pedido, de la más reciente a la más antigua.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY, implementation = ApiModels.ExperienceDto.class),
                    examples = @ExampleObject(value = OpenApiExamples.EXPERIENCE_LIST_ES)))
    @APIResponse(
            responseCode = "404",
            description = "No hay CV publicado en el idioma pedido.",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(value = OpenApiExamples.PROBLEM_RESUME_NOT_FOUND)))
    public List<ApiModels.ExperienceDto> list(@BeanParam LanguageParams params) {
        return ApiModels.map(getExperience.list(params.language()), ApiModels::toDto);
    }

    @GET
    @Path("/{id}")
    @Operation(
            operationId = "getExperienceById",
            summary = "Una etapa de la experiencia por su id",
            description = "Devuelve una sola entrada de la trayectoria. Los ids son estables y no dependen "
                    + "del idioma; los puedes leer en el campo `id` de `/experience`.")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "Identificador de la entrada, tal como aparece en el campo `id` de `/experience`.",
            example = "central-madeirense")
    @APIResponse(
            responseCode = "200",
            description = "La entrada pedida.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ApiModels.ExperienceDto.class),
                    examples = @ExampleObject(value = OpenApiExamples.EXPERIENCE_ONE_ES)))
    @APIResponse(
            responseCode = "404",
            description = "No existe una entrada con ese `id`, o no hay CV publicado en el idioma pedido.",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = {
                        @ExampleObject(
                                name = "unknownId",
                                summary = "Id desconocido",
                                value = OpenApiExamples.PROBLEM_EXPERIENCE_NOT_FOUND),
                        @ExampleObject(
                                name = "unknownIdEn",
                                summary = "Id desconocido, con `?lang=en`",
                                value = OpenApiExamples.PROBLEM_EXPERIENCE_NOT_FOUND_EN),
                        @ExampleObject(
                                name = "noResume",
                                summary = "Sin CV en ese idioma",
                                value = OpenApiExamples.PROBLEM_RESUME_NOT_FOUND)
                    }))
    public ApiModels.ExperienceDto byId(@PathParam("id") String id, @BeanParam LanguageParams params) {
        return ApiModels.toDto(getExperience.byId(params.language(), id));
    }
}
