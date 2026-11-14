package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.ContentVersion;
import dev.julioflores.cv.application.content.ContentVersionSummary;
import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import dev.julioflores.cv.application.port.out.ContentVersionRepository;
import dev.julioflores.cv.application.service.ContentVersionConflictException;
import dev.julioflores.cv.application.service.ContentVersionNotFoundException;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.infrastructure.ContentVersions;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El adaptador de versiones contra Postgres real (T101 y la semántica de active, MAX+1 y base). */
@QuarkusTest
class PostgresContentVersionRepositoryTest {

    @Inject
    ContentVersionRepository repository;

    @Inject
    DataSource dataSource;

    @Inject
    ContentCacheInvalidator cache;

    @Inject
    ObjectMapper mapper;

    private List<Object[]> backup;

    @BeforeEach
    void snapshot() throws SQLException {
        backup = ContentVersions.snapshot(dataSource);
    }

    @AfterEach
    void restore() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TRIGGER IF EXISTS fail_english ON content_version");
            s.execute("DROP FUNCTION IF EXISTS fail_english()");
        }
        ContentVersions.restore(dataSource, backup, cache);
    }

    private Map<String, Object> services(String title) {
        return mapper.convertValue(Map.of("services", List.of(Map.of("id", "a", "title", title, "summary", "S"))),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    private static Map<Language, Integer> bases(int es, int en) {
        Map<Language, Integer> bases = new EnumMap<>(Language.class);
        bases.put(Language.ES, es);
        bases.put(Language.EN, en);
        return bases;
    }

    private Map<Language, Map<String, Object>> docs(String es, String en) {
        Map<Language, Map<String, Object>> docs = new EnumMap<>(Language.class);
        docs.put(Language.ES, services(es));
        docs.put(Language.EN, services(en));
        return docs;
    }

    private int count(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void aFailureWhileWritingEnglishAlsoLeavesNoSpanishVersion() throws SQLException { // T101
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE FUNCTION fail_english() RETURNS trigger LANGUAGE plpgsql AS "
                    + "$$ BEGIN IF NEW.lang = 'en' AND NEW.source = 'admin' THEN RAISE EXCEPTION 'boom en inglés'; END IF; "
                    + "RETURN NEW; END $$");
            s.execute("CREATE TRIGGER fail_english BEFORE INSERT ON content_version FOR EACH ROW EXECUTE FUNCTION fail_english()");
        }
        int rows = count("SELECT count(*) FROM content_version");

        assertThrows(IllegalStateException.class,
                () -> repository.publish(ContentKind.SERVICES, bases(1, 1), docs("Es", "En"), "x"));

        assertEquals(rows, count("SELECT count(*) FROM content_version"));
        assertEquals(1, count("SELECT version FROM content_version WHERE kind = 'services' AND lang = 'es' AND active"));
        assertEquals(1, count("SELECT version FROM content_version WHERE kind = 'services' AND lang = 'en' AND active"));
    }

    @Test
    void publishCreatesTheNextVersionDeactivatesTheOldOneAndStoresSourceAndComment() {
        repository.publish(ContentKind.SERVICES, bases(1, 1), docs("Es 2", "En 2"), "Una nota");

        ContentVersion es = repository.active(ContentKind.SERVICES, Language.ES).orElseThrow();
        assertEquals(2, es.version());
        assertTrue(es.active());
        assertEquals("admin", es.source());
        assertEquals("Una nota", es.comment());
        assertEquals("Es 2", ((List<Map<String, Object>>) es.content().get("services")).get(0).get("title"));
        ContentVersion old = repository.find(ContentKind.SERVICES, Language.ES, 1).orElseThrow();
        assertFalse(old.active());
        assertEquals("seed", old.source());
        assertNull(old.comment());
    }

    @Test
    void theStoredJsonOfAProjectSaveHasTheShapeOfTheSampleWithoutClientForAnonymous() throws Exception { // T106 (parte BD)
        JsonNode original = mapper.readTree(java.nio.file.Path.of("").toAbsolutePath().getParent()
                .resolve("db/sample/projects.es.json").toFile());
        Map<Language, Map<String, Object>> docs = new EnumMap<>(Language.class);
        docs.put(Language.ES, mapper.convertValue(original, new com.fasterxml.jackson.core.type.TypeReference<>() {}));
        docs.put(Language.EN, mapper.convertValue(original, new com.fasterxml.jackson.core.type.TypeReference<>() {}));

        repository.publish(ContentKind.PROJECTS, bases(1, 1), docs, null);

        JsonNode stored;
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT content::text FROM content_version WHERE kind = 'projects' "
                        + "AND lang = 'es' AND version = 2")) {
            rs.next();
            stored = mapper.readTree(rs.getString(1));
        }
        assertEquals(original, stored);
        assertEquals(2, stored.get("projects").size());
        assertFalse(stored.get("projects").get(1).has("client"));
    }

    @Test
    void aStaleBaseInEitherLanguageIsAConflictAndWritesNothing() throws SQLException {
        int rows = count("SELECT count(*) FROM content_version");

        var onEs = assertThrows(ContentVersionConflictException.class,
                () -> repository.publish(ContentKind.SERVICES, bases(0, 1), docs("a", "b"), null));
        assertEquals(List.of("es", "1"), onEs.args());
        assertThrows(ContentVersionConflictException.class,
                () -> repository.publish(ContentKind.SERVICES, bases(1, 5), docs("a", "b"), null));

        assertEquals(rows, count("SELECT count(*) FROM content_version"));
    }

    @Test
    void aSaveAfterAPointerMoveTakesTheMaximumPlusOneNotTheActivePlusOne() {
        repository.publish(ContentKind.SERVICES, bases(1, 1), docs("v2", "v2"), null);
        repository.publish(ContentKind.SERVICES, bases(2, 2), docs("v3", "v3"), null);
        repository.activate(ContentKind.SERVICES, Language.ES, 3, 1);
        assertEquals(1, repository.active(ContentKind.SERVICES, Language.ES).orElseThrow().version());

        repository.publish(ContentKind.SERVICES, bases(1, 3), docs("v4", "v4"), null);

        assertEquals(4, repository.active(ContentKind.SERVICES, Language.ES).orElseThrow().version());
        assertEquals(List.of(4, 3, 2, 1),
                repository.list(ContentKind.SERVICES, Language.ES, 10).stream().map(ContentVersionSummary::version).toList());
    }

    @Test
    void activateChecksTheBaseAndTheTargetAndMovesOnlyThePointer() throws SQLException {
        repository.publish(ContentKind.SERVICES, bases(1, 1), docs("v2", "v2"), null);
        int rows = count("SELECT count(*) FROM content_version");

        assertThrows(ContentVersionConflictException.class, () -> repository.activate(ContentKind.SERVICES, Language.ES, 1, 2));
        assertThrows(ContentVersionNotFoundException.class, () -> repository.activate(ContentKind.SERVICES, Language.ES, 2, 7));
        repository.activate(ContentKind.SERVICES, Language.ES, 2, 1);

        assertEquals(rows, count("SELECT count(*) FROM content_version"));
        assertEquals(1, count("SELECT version FROM content_version WHERE kind = 'services' AND lang = 'es' AND active"));
        assertEquals(2, count("SELECT version FROM content_version WHERE kind = 'services' AND lang = 'en' AND active"));
    }

    @Test
    void listRespectsTheLimitAndNewestFirstAndAnEmptyKindIsEmpty() throws SQLException {
        repository.publish(ContentKind.SERVICES, bases(1, 1), docs("v2", "v2"), "n");
        repository.publish(ContentKind.SERVICES, bases(2, 2), docs("v3", "v3"), null);

        List<ContentVersionSummary> two = repository.list(ContentKind.SERVICES, Language.EN, 2);
        assertEquals(List.of(3, 2), two.stream().map(ContentVersionSummary::version).toList());
        assertEquals(List.of(true, false), two.stream().map(ContentVersionSummary::active).toList());

        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM content_version WHERE kind = 'architecture'");
        }
        assertEquals(List.of(), repository.list(ContentKind.ARCHITECTURE, Language.ES, 10));
        assertTrue(repository.active(ContentKind.ARCHITECTURE, Language.ES).isEmpty());
        assertTrue(repository.find(ContentKind.SERVICES, Language.ES, 99).isEmpty());
    }

    @Test
    void aStoredDocumentThatIsNotAnObjectIsAnIllegalStateNotASilentDefault() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO content_version (kind, lang, version, content, active) "
                    + "VALUES ('services', 'es', 8, '[1,2]'::jsonb, false)");
        }
        assertThrows(IllegalStateException.class, () -> repository.find(ContentKind.SERVICES, Language.ES, 8));
    }

    private static final String ES_LOCK = "SELECT pg_advisory_xact_lock(hashtextextended('content:services:es', 0))";
    private static final String EN_LOCK = "SELECT pg_advisory_xact_lock(hashtextextended('content:services:en', 0))";

    private Future<Throwable> publishInAnotherThread(ExecutorService pool) {
        return pool.submit(() -> {
            try {
                repository.publish(ContentKind.SERVICES, bases(1, 1), docs("hilo", "thread"), "x");
                return null;
            } catch (RuntimeException e) {
                return e;
            }
        });
    }

    /** T100 (determinista): el candado asesor es lo que serializa; quien llega con una base vieja recibe el conflicto. */
    @Test
    void publishWaitsForTheAdvisoryLockAndThenSeesTheVersionThatWonTheRace() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (Statement s = holder.createStatement()) {
                s.execute(ES_LOCK); // «otro guardado» en curso: tiene el candado de ES con la transacción abierta
                int rows = count("SELECT count(*) FROM content_version");

                Future<Throwable> publish = publishInAnotherThread(pool);

                assertThrows(TimeoutException.class, () -> publish.get(700, TimeUnit.MILLISECONDS)); // sigue bloqueado
                assertEquals(rows, count("SELECT count(*) FROM content_version")); // y no escribió nada

                // El otro guardado gana: publica la versión 2 de ES y confirma.
                s.execute("UPDATE content_version SET active = false WHERE kind = 'services' AND lang = 'es' AND active");
                s.execute("INSERT INTO content_version (kind, lang, version, content, active) "
                        + "VALUES ('services', 'es', 2, '{\"services\":[]}', true)");
                holder.commit();

                Throwable result = publish.get(10, TimeUnit.SECONDS);
                assertInstanceOf(ContentVersionConflictException.class, result);
                assertEquals(List.of("es", "2"), ((ContentVersionConflictException) result).args());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, count("SELECT count(*) FROM content_version WHERE kind = 'services' AND lang = 'es' AND version = 2"));
        assertEquals(0, count("SELECT count(*) FROM content_version WHERE kind = 'services' AND lang = 'en' AND version = 2"));
    }

    /** El orden de los candados es siempre ES y luego EN: con el de EN ocupado, publish ya tiene el de ES y espera. */
    @Test
    void publishTakesTheSpanishLockBeforeTheEnglishOneAndCompletesWhenItIsFree() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection holder = dataSource.getConnection(); Connection probe = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (Statement s = holder.createStatement(); Statement p = probe.createStatement()) {
                s.execute(EN_LOCK);

                Future<Throwable> publish = publishInAnotherThread(pool);

                boolean spanishHeld = false;
                for (int i = 0; i < 100 && !spanishHeld; i++) {
                    try (ResultSet rs = p.executeQuery("SELECT pg_try_advisory_xact_lock(hashtextextended('content:services:es', 0))")) {
                        rs.next();
                        spanishHeld = !rs.getBoolean(1); // false = alguien más lo tiene
                    }
                    if (!spanishHeld) {
                        Thread.sleep(50);
                    }
                }
                assertTrue(spanishHeld, "publish debía tener ya el candado de ES");
                assertThrows(TimeoutException.class, () -> publish.get(300, TimeUnit.MILLISECONDS)); // espera al de EN

                holder.rollback(); // libera EN
                assertNull(publish.get(10, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2, count("SELECT version FROM content_version WHERE kind = 'services' AND lang = 'es' AND active"));
        assertEquals(2, count("SELECT version FROM content_version WHERE kind = 'services' AND lang = 'en' AND active"));
    }
}
