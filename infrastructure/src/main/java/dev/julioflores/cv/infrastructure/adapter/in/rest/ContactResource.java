package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.port.in.GetContactUseCase;
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

@Path("/api/v1/contact")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Resume")
public class ContactResource {

    private final GetContactUseCase getContact;

    public ContactResource(GetContactUseCase getContact) {
        this.getContact = getContact;
    }

    @GET
    @Operation(
            operationId = "getContact",
            summary = "Cómo contactarme",
            description = "Correo electrónico y perfiles externos (LinkedIn, GitHub). "
                    + "No expone teléfono ni otros datos personales.")
    @APIResponse(
            responseCode = "200",
            description = "Datos de contacto públicos.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ApiModels.ContactDto.class),
                    examples = @ExampleObject(value = OpenApiExamples.CONTACT)))
    @APIResponse(
            responseCode = "404",
            description = "No hay CV publicado en el idioma pedido.",
            content = @Content(
                    mediaType = ProblemResponse.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemResponse.class),
                    examples = @ExampleObject(value = OpenApiExamples.PROBLEM_RESUME_NOT_FOUND)))
    public ApiModels.ContactDto contact(@BeanParam LanguageParams params) {
        return ApiModels.toDto(getContact.execute(params.language()));
    }
}
