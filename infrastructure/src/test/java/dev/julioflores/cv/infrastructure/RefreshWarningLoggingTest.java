package dev.julioflores.cv.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresResumeRepository;
import dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresSiteContentRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.sql.DataSource;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

/** Cuando falla el refresco de la BD y se sirve lo último leído, el WARN no vuelca mensaje, traza, host ni URL del driver. */
@QuarkusTest
class RefreshWarningLoggingTest {

    private static final String DRIVER_MESSAGE = "Connection to secreto:5432 refused; jdbc:postgresql://secreto:5432/db password=hunter2";

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper mapper;

    private static DataSource failingWhen(DataSource real, AtomicBoolean down) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getConnection") && down.get()) {
                        throw new SQLException(DRIVER_MESSAGE, "08001");
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static void assertCleanWarning(LogRecords logs, String what) {
        ExtLogRecord warn = logs.await(r -> r.getLevel().intValue() == Level.WARNING.intValue()
                && r.getFormattedMessage().startsWith("Cannot refresh the")).get(0);
        assertTrue(warn.getFormattedMessage().contains(what), warn.getFormattedMessage());
        assertTrue(warn.getFormattedMessage().contains("SQLState=08001"), warn.getFormattedMessage());
        assertTrue(warn.getFormattedMessage().endsWith("serving the last one read"), warn.getFormattedMessage());
        assertNull(warn.getThrown(), "sin traza");
        String dump = logs.dump();
        for (String leak : new String[] {"hunter2", "secreto", "jdbc:", "refused", "5432", "PSQLException", "\tat "}) {
            assertFalse(dump.contains(leak), leak + " in " + dump);
        }
    }

    @Test
    void theResumeRefreshWarningLeaksNothingFromTheDriver() {
        AtomicBoolean down = new AtomicBoolean();
        var repository = new PostgresResumeRepository(failingWhen(dataSource, down), mapper, Duration.ZERO);
        assertTrue(repository.findActive(Language.ES).isPresent());
        try (LogRecords logs = LogRecords.attach()) {
            down.set(true);
            assertTrue(repository.findActive(Language.ES).isPresent(), "sirve lo último leído");
            assertCleanWarning(logs, "'es' resume");
        }
    }

    @Test
    void theSiteContentRefreshWarningLeaksNothingFromTheDriver() {
        AtomicBoolean down = new AtomicBoolean();
        var repository = new PostgresSiteContentRepository(failingWhen(dataSource, down), mapper, Duration.ZERO);
        assertEquals(2, repository.services(Language.ES).size());
        try (LogRecords logs = LogRecords.attach()) {
            down.set(true);
            assertEquals(2, repository.services(Language.ES).size(), "sirve lo último leído");
            assertCleanWarning(logs, "'services' content in 'es'");
        }
    }
}
