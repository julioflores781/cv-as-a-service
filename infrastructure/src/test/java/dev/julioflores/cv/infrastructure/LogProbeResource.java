package dev.julioflores.cv.infrastructure;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/** Solo existe en las pruebas: escribe líneas de log desde el hilo de eventos y desde hilos de trabajo, para comprobar el MDC. */
@Path("/test/log-probe")
@Tag(name = "Test")
public class LogProbeResource {

    static final String LOGGER = "probe";
    private static final Logger LOG = Logger.getLogger(LOGGER);

    @Inject
    DataSource dataSource;

    /** Una línea en el hilo de eventos y otra tras pasar al pool de trabajo (con una consulta JDBC bloqueante). */
    @GET
    @NonBlocking
    @Operation(hidden = true)
    public Uni<String> probe(@QueryParam("tag") String tag) {
        LOG.info("probe-event-loop tag=" + tag);
        return Uni.createFrom().item(() -> {
            LOG.info("probe-worker tag=" + tag);
            select1();
            return "ok";
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /** Un método bloqueante de JAX-RS: Quarkus lo ejecuta en un hilo de trabajo. */
    @GET
    @Path("/blocking")
    @Blocking
    @Operation(hidden = true)
    public String blocking(@QueryParam("tag") String tag) {
        LOG.info("probe-blocking tag=" + tag);
        select1();
        return "ok";
    }

    private void select1() {
        try (Connection c = dataSource.getConnection(); var s = c.createStatement()) {
            s.execute("SELECT 1");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
