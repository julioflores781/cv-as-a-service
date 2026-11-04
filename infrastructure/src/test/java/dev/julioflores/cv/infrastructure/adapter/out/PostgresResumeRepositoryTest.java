package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresResumeRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PostgresResumeRepositoryTest {

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper mapper;

    /** DataSource real que cuenta las conexiones y puede simular una base de datos caída. */
    private static final class Probe {
        final AtomicInteger connections = new AtomicInteger();
        final AtomicBoolean down = new AtomicBoolean();
        final DataSource dataSource;

        Probe(DataSource real) {
            dataSource = (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getConnection")) {
                            connections.incrementAndGet();
                            if (down.get()) {
                                throw new SQLException("database is down");
                            }
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }

    private PostgresResumeRepository repository(Probe probe, Duration ttl) {
        return new PostgresResumeRepository(probe.dataSource, mapper, ttl);
    }

    @Test
    void readsTheActiveResumeAndReusesItWithinTheTtl() {
        Probe probe = new Probe(dataSource);
        PostgresResumeRepository repository = repository(probe, Duration.ofMinutes(5));

        assertEquals("Ada Ejemplo", repository.findActive(Language.ES).orElseThrow().profile().name());
        repository.findActive(Language.ES);

        assertEquals(1, probe.connections.get());
    }

    @Test
    void reloadsOnceTheTtlExpires() {
        Probe probe = new Probe(dataSource);
        PostgresResumeRepository repository = repository(probe, Duration.ZERO);

        repository.findActive(Language.EN);
        repository.findActive(Language.EN);

        assertEquals(2, probe.connections.get());
    }

    @Test
    void servesTheLastResumeWhenTheDatabaseFailsAfterExpiry() {
        Probe probe = new Probe(dataSource);
        PostgresResumeRepository repository = repository(probe, Duration.ZERO);
        repository.findActive(Language.ES);

        probe.down.set(true);

        assertTrue(repository.findActive(Language.ES).isPresent());
    }

    @Test
    void failsWhenThereIsNothingToFallBackOn() {
        Probe probe = new Probe(dataSource);
        probe.down.set(true);

        assertThrows(IllegalStateException.class, () -> repository(probe, Duration.ofMinutes(5)).findActive(Language.ES));
    }

    @Test
    void reportsAnUnpublishedLanguageWithoutQueryingOnEveryCall() throws SQLException {
        Probe probe = new Probe(dataSource);
        PostgresResumeRepository repository = repository(probe, Duration.ofMinutes(5));
        setActive(false);
        try {
            assertFalse(repository.findActive(Language.EN).isPresent());
            assertFalse(repository.findActive(Language.EN).isPresent());
        } finally {
            setActive(true);
        }
        assertEquals(1, probe.connections.get());
    }

    /** Activa o desactiva el CV inglés de db/sample (única versión). */
    private void setActive(boolean active) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE resume_version SET active = " + active + " WHERE lang = 'en'");
        }
    }
}
