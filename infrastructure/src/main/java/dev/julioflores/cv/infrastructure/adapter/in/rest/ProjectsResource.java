package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.port.in.GetProjectsUseCase;
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

@Path("/api/v1/projects")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Content")
public class ProjectsResource {

    private final GetProjectsUseCase getProjects;

    public ProjectsResource(GetProjectsUseCase getProjects) {
        this.getProjects = getProjects;
    }

    @GET
    @Operation(
            operationId = "getProjects",
            summary = "Proyectos en los que he trabajado",
            description = "Proyectos propios y de empresas donde he hecho backend y web. El nombre del cliente solo se expone si el proyecto es `public`; en uno `anonymous` solo se describe al cliente (`clientLabel`). Si todavía no hay nada publicado en ese idioma responde 200 con contenido vacío, no un error.")
    @APIResponse(
            responseCode = "200",
            description = "Proyectos en el idioma pedido.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY, implementation = ApiModels.ProjectDto.class),
                    examples = {
                        @ExampleObject(name = "es", summary = "Español", value = OpenApiExamples.PROJECTS_ES),
                        @ExampleObject(name = "en", summary = "Inglés (`?lang=en`)", value = OpenApiExamples.PROJECTS_EN)
                    }))
    public List<ApiModels.ProjectDto> projects(@BeanParam LanguageParams params) {
        return ApiModels.map(getProjects.execute(params.language()), ApiModels::toDto);
    }
}
