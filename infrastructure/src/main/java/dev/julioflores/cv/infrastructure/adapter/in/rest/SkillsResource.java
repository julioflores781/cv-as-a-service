package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.port.in.GetSkillsUseCase;
import dev.julioflores.cv.infrastructure.adapter.in.rest.error.ProblemResponse;
import dev.julioflores.cv.domain.Language;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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

@Path("/api/v1/skills")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Resume")
public class SkillsResource {

    private final GetSkillsUseCase getSkills;

    public SkillsResource(GetSkillsUseCase getSkills) {
        this.getSkills = getSkills;
    }

    @GET
    @Operation(
            operationId = "getSkills",
            summary = "Herramientas que uso, agrupadas",
            description = "Stack tecnológico agrupado por áreas. Sin parámetros devuelve todos los grupos; "
                    + "con `group` devuelve solo ese grupo (siempre como lista de un elemento, para que la "
                    + "forma de la respuesta no cambie). Claves de grupo: `backend`, `data`, `health`, "
                    + "`platform`, `frontend` y `methodology`.")
    @Parameter(
            name = "group",
            in = ParameterIn.QUERY,
            description = "Clave del grupo a devolver. No distingue mayúsculas. Si se omite, se devuelven todos.",
            schema = @Schema(enumeration = {"backend", "data", "health", "platform", "frontend", "methodology"}),
            example = "backend")
    @APIResponse(
            responseCode = "200",
            description = "Todos los grupos, o solo el pedido con `group`.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY, implementation = ApiModels.SkillGroupDto.class),
                    examples = {
                        @ExampleObject(name = "all", summary = "Sin `group`", value = OpenApiExamples.SKILLS_ALL_ES),
                        @ExampleObject(
                                name = "oneGroup",
                                summary = "Con `?group=backend`",
                                value = OpenApiExamples.SKILLS_GROUP_ES)
                    }))
    @APIResponse(
            responseCode = "404",
            description = "El grupo pedido no existe, o no hay CV publicado en el idioma pedido.",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = {
                        @ExampleObject(
                                name = "unknownGroup",
                                summary = "Grupo desconocido",
                                value = OpenApiExamples.PROBLEM_SKILL_GROUP_NOT_FOUND),
                        @ExampleObject(
                                name = "noResume",
                                summary = "Sin CV en ese idioma",
                                value = OpenApiExamples.PROBLEM_RESUME_NOT_FOUND)
                    }))
    public List<ApiModels.SkillGroupDto> skills(
            @QueryParam("group") String group, @BeanParam LanguageParams params) {
        Language language = params.language();
        if (group == null || group.isBlank()) {
            return ApiModels.map(getSkills.all(language), ApiModels::toDto);
        }
        return List.of(ApiModels.toDto(getSkills.byGroup(language, group)));
    }
}
