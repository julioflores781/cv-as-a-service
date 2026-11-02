package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.port.in.GetProfileUseCase;
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

@Path("/api/v1/profile")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Resume")
public class ProfileResource {

    private final GetProfileUseCase getProfile;

    public ProfileResource(GetProfileUseCase getProfile) {
        this.getProfile = getProfile;
    }

    @GET
    @Operation(
            operationId = "getProfile",
            summary = "Quién soy y a qué me dedico",
            description = "Presentación profesional: nombre, titular, subtítulo con el stack, ubicación, "
                    + "resumen y disponibilidad laboral.")
    @APIResponse(
            responseCode = "200",
            description = "Perfil en el idioma pedido.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ApiModels.ProfileDto.class),
                    examples = {
                        @ExampleObject(name = "es", summary = "Español", value = OpenApiExamples.PROFILE_ES),
                        @ExampleObject(name = "en", summary = "Inglés (`?lang=en`)", value = OpenApiExamples.PROFILE_EN)
                    }))
    @APIResponse(
            responseCode = "404",
            description = "No hay CV publicado en el idioma pedido.",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(value = OpenApiExamples.PROBLEM_RESUME_NOT_FOUND)))
    public ApiModels.ProfileDto profile(@BeanParam LanguageParams params) {
        return ApiModels.toDto(getProfile.execute(params.language()));
    }
}
