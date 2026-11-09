package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.application.port.in.GetServicesUseCase;
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

@Path("/api/v1/services")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Content")
public class ServicesResource {

    private final GetServicesUseCase getServices;

    public ServicesResource(GetServicesUseCase getServices) {
        this.getServices = getServices;
    }

    @GET
    @Operation(
            operationId = "getServices",
            summary = "Servicios que ofrezco",
            description = "Lista de servicios con su descripción y, si lo hay, un precio «desde». El contenido se edita en la base de datos, no en la web. Si todavía no hay nada publicado en ese idioma responde 200 con contenido vacío, no un error.")
    @APIResponse(
            responseCode = "200",
            description = "Servicios en el idioma pedido. Sin `price` significa «a consultar».",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY, implementation = ApiModels.ServiceDto.class),
                    examples = {
                        @ExampleObject(name = "es", summary = "Español", value = OpenApiExamples.SERVICES_ES),
                        @ExampleObject(name = "en", summary = "Inglés (`?lang=en`)", value = OpenApiExamples.SERVICES_EN)
                    }))
    public List<ApiModels.ServiceDto> services(@BeanParam LanguageParams params) {
        return ApiModels.map(getServices.execute(params.language()), ApiModels::toDto);
    }
}
