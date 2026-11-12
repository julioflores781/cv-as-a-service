package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.ServiceOffer;
import dev.julioflores.cv.infrastructure.ContentVersions;
import dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresSiteContentRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
        /** Si se arma, el cierre de la próxima conexión avisa (queried) y espera (release): una lectura lenta tras consultar. */
        volatile CountDownLatch queried;
        volatile CountDownLatch release;

        /** Si se arma, la próxima getConnection avisa (connecting) y espera (proceed) antes de abrir o fallar. */
        volatile CountDownLatch connecting;
        volatile CountDownLatch proceed;

        void armSlowConnect() {
            connecting = new CountDownLatch(1);
            proceed = new CountDownLatch(1);
        }

        void armSlowRead() {
            queried = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        private Connection gated(Connection real) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("close") && queried != null) {
                            CountDownLatch signal = queried;
                            CountDownLatch gate = release;
                            queried = null;
                            signal.countDown();
                            if (!gate.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("slow read never released");
                            }
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        Probe(DataSource real) {
            dataSource = (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getConnection")) {
                            connections.incrementAndGet();
                            if (connecting != null) {
                                CountDownLatch signal = connecting;
                                CountDownLatch gate = proceed;
                                connecting = null;
                                signal.countDown();
                                if (!gate.await(10, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("slow connect never released");
                                }
                            }
                            if (down.get()) {
                                throw new SQLException("database is down");
                            }
                        }
                        try {
                            Object result = method.invoke(real, args);
                            return method.getName().equals("getConnection") && result instanceof Connection connection
                                    ? gated(connection)
                                    : result;
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

    private static String firstTitle(List<ServiceOffer> services) {
        return services.get(0).title();
    }

    // T113
    @Test
    void aSlowReadStartedBeforeTheInvalidationDoesNotPutTheOldContentBack() throws Exception {
        Probe probe = new Probe(dataSource);
        PostgresSiteContentRepository repository = repository(probe, Duration.ofMinutes(5));
        probe.armSlowRead();
        CountDownLatch queried = probe.queried;
        CountDownLatch release = probe.release;
        CompletableFuture<String> slowRead = CompletableFuture.supplyAsync(() -> firstTitle(repository.services(Language.ES)));
        assertTrue(queried.await(10, TimeUnit.SECONDS), "the slow read must have queried the database");
        ContentVersions.publishServices(dataSource, "es", "Servicio nuevo");
        try {
            repository.invalidate("services", Language.ES);
            release.countDown();

            assertEquals("Servicio de ejemplo", slowRead.get(10, TimeUnit.SECONDS));
            assertEquals("Servicio nuevo", firstTitle(repository.services(Language.ES)));
            assertEquals("Servicio nuevo", firstTitle(repository.services(Language.ES)));
            assertEquals(2, probe.connections.get());
        } finally {
            release.countDown();
            ContentVersions.revertServices(dataSource, "es");
        }
    }

    // T114
    @Test
    void whenTheDatabaseFailsRightAfterAnInvalidationOnlyThatKeyFails() {
        Probe probe = new Probe(dataSource);
        PostgresSiteContentRepository repository = repository(probe, Duration.ZERO);
        repository.services(Language.ES);
        repository.services(Language.EN);

        repository.invalidate("services", Language.ES);
        probe.down.set(true);

        assertThrows(IllegalStateException.class, () -> repository.services(Language.ES));
        assertEquals(2, repository.services(Language.EN).size());
    }

    // CR-1: invalidada mientras refresca y con la BD caída, lo viejo (ya invalidado) no se sirve ni se repone.
    @Test
    void anInvalidationDuringAFailingRefreshPropagatesTheErrorInsteadOfServingTheOldContent() throws Exception {
        Probe probe = new Probe(dataSource);
        PostgresSiteContentRepository repository = repository(probe, Duration.ZERO);
        repository.services(Language.ES);
        probe.down.set(true);
        probe.armSlowConnect();
        CountDownLatch connecting = probe.connecting;
        CountDownLatch proceed = probe.proceed;
        CompletableFuture<List<ServiceOffer>> refresh = CompletableFuture.supplyAsync(() -> repository.services(Language.ES));
        assertTrue(connecting.await(10, TimeUnit.SECONDS), "the refresh must be about to query");

        repository.invalidate("services", Language.ES);
        proceed.countDown();

        ExecutionException failure = assertThrows(ExecutionException.class, () -> refresh.get(10, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof IllegalStateException);
        assertThrows(IllegalStateException.class, () -> repository.services(Language.ES));
    }

    // CR-2: invalidar A mientras una lectura lenta de B está en vuelo no impide cachear B.
    @Test
    void invalidatingAnotherKeyWhileASlowReadIsInFlightStillCachesTheRead() throws Exception {
        Probe probe = new Probe(dataSource);
        PostgresSiteContentRepository repository = repository(probe, Duration.ofMinutes(5));
        probe.armSlowRead();
        CountDownLatch queried = probe.queried;
        CountDownLatch release = probe.release;
        CompletableFuture<List<ServiceOffer>> slowRead = CompletableFuture.supplyAsync(() -> repository.services(Language.EN));
        assertTrue(queried.await(10, TimeUnit.SECONDS), "the slow read must have queried the database");

        repository.invalidate("services", Language.ES);
        release.countDown();

        assertEquals(2, slowRead.get(10, TimeUnit.SECONDS).size());
        assertEquals(2, repository.services(Language.EN).size());
        assertEquals(1, probe.connections.get());
    }

    // T115
    @Test
    void invalidatingOneKeyLeavesTheOthersCached() throws SQLException {
        Probe probe = new Probe(dataSource);
        PostgresSiteContentRepository repository = repository(probe, Duration.ofMinutes(5));
        repository.services(Language.ES);
        repository.services(Language.EN);
        repository.projects(Language.ES);
        assertEquals(3, probe.connections.get());

        repository.invalidate("services", Language.ES);
        repository.services(Language.EN);
        repository.projects(Language.ES);
        assertEquals(3, probe.connections.get());

        repository.services(Language.ES);
        assertEquals(4, probe.connections.get());
    }

    // Extra: tras invalidar se ve el contenido nuevo; sin invalidar se sigue viendo el viejo (TTL).
    @Test
    void anInvalidationExposesANewVersionThatTheTtlWouldHide() throws Exception {
        Probe probe = new Probe(dataSource);
        PostgresSiteContentRepository repository = repository(probe, Duration.ofMinutes(5));
        assertEquals("Servicio de ejemplo", firstTitle(repository.services(Language.ES)));
        ContentVersions.publishServices(dataSource, "es", "Servicio nuevo");
        try {
            assertEquals("Servicio de ejemplo", firstTitle(repository.services(Language.ES)));

            repository.invalidate("services", Language.ES);

            assertEquals("Servicio nuevo", firstTitle(repository.services(Language.ES)));
        } finally {
            ContentVersions.revertServices(dataSource, "es");
        }
    }

    // Extra: invalidar una clave nunca leída no falla y no bloquea la lectura siguiente.
    @Test
    void invalidatingAKeyNeverReadIsHarmless() {
        Probe probe = new Probe(dataSource);
        PostgresSiteContentRepository repository = repository(probe, Duration.ofMinutes(5));

        repository.invalidate("architecture", Language.ES);

        assertFalse(repository.architecture(Language.ES).ports().isEmpty());
        assertEquals(1, probe.connections.get());
    }

    /** Activa o desactiva el contenido inglés de db/sample (única versión de cada tipo). */
    private void setActive(boolean active) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE content_version SET active = " + active + " WHERE lang = 'en'");
        }
    }
}
