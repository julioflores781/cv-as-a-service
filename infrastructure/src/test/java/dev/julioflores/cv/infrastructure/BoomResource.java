package dev.julioflores.cv.infrastructure;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Solo existe en las pruebas: fuerza un fallo inesperado para comprobar el 500. */
@Path("/test/boom")
@Tag(name = "Test")
public class BoomResource {

    @GET
    @Operation(hidden = true)
    public String boom() {
        throw new IllegalStateException("secret internal detail: db password is hunter2");
    }
}
