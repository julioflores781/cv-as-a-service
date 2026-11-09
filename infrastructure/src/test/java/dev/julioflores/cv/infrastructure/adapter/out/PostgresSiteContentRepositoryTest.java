package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresSiteContentRepository;
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
class PostgresSiteContentRepositoryTest {

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

    private PostgresSiteContentRepository repository(Probe probe, Duration ttl) {
        return new PostgresSiteContentRepository(probe.dataSource, mapper, ttl);
    }

    @Test
    void readsTheActiveContentAndReusesItWithinTheTtl() {
        Probe probe = new Probe(dataSource);
        PostgresSiteContentRepository repository = repository(probe, Duration.ofMinutes(5));

        assertEquals(2, repository.services(Language.ES).size());
        repository.services(Language.ES);

        assertEquals(1, probe.connections.get());
    }

    @Test
    void eachKindAndLanguageIsCachedSeparately() {
        Probe probe = new Probe(dataSource);
        PostgresSiteContentRepository repository = repository(probe, Duration.ofMinutes(5));

        repository.services(Language.ES);
        repository.services(Language.EN);
        repository.projects(Language.ES);
        repository.architecture(Language.ES);

        assertEquals(4, probe.connections.get());
    }

    @Test
    void reloadsOnceTheTtlExpires() {
        Probe probe = new Probe(dataSource);
        PostgresSiteContentRepository repository = repository(probe, Duration.ZERO);

        repository.projects(Language.EN);
        repository.projects(Language.EN);

        assertEquals(2, probe.connections.get());
    }

    @Test
    void servesTheLastContentWhenTheDatabaseFailsAfterExpiry() {
        Probe probe = new Probe(dataSource);
        PostgresSiteContentRepository repository = repository(probe, Duration.ZERO);
        repository.services(Language.ES);

        probe.down.set(true);

        assertEquals(2, repository.services(Language.ES).size());
    }

    @Test
    void failsWhenThereIsNothingToFallBackOn() {
        Probe probe = new Probe(dataSource);
        probe.down.set(true);

        assertThrows(IllegalStateException.class, () -> repository(probe, Duration.ofMinutes(5)).services(Language.ES));
    }

    @Test
    void unpublishedContentIsServedEmptyWithoutQueryingOnEveryCall() throws SQLException {
        Probe probe = new Probe(dataSource);
        PostgresSiteContentRepository repository = repository(probe, Duration.ofMinutes(5));
        setActive(false);
        try {
            assertTrue(repository.services(Language.EN).isEmpty());
            assertTrue(repository.projects(Language.EN).isEmpty());
            assertTrue(repository.architecture(Language.EN).ports().isEmpty());
            assertFalse(repository.architecture(Language.EN).coreName() == null);
            repository.services(Language.EN);
        } finally {
            setActive(true);
        }
        assertEquals(3, probe.connections.get());
    }

    /** Activa o desactiva el contenido inglés de db/sample (única versión de cada tipo). */
    private void setActive(boolean active) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE content_version SET active = " + active + " WHERE lang = 'en'");
        }
    }
}
