package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import dev.julioflores.cv.application.port.out.ImageReferenceRepository;
import dev.julioflores.cv.infrastructure.ContentVersions;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Las referencias se buscan en TODAS las versiones de content_version (activas e históricas). */
@QuarkusTest
class PostgresImageReferenceRepositoryTest {

    private static final String SAMPLE = "site/projects/00000000-0000-4000-8000-000000000001.webp";
    private static final String OLD = "site/projects/00000000-0000-4000-8000-0000000000b1.png";

    @Inject
    ImageReferenceRepository references;

    @Inject
    DataSource dataSource;

    @Inject
    ContentCacheInvalidator cache;

    private List<Object[]> backup;

    @BeforeEach
    void snapshot() throws SQLException {
        backup = ContentVersions.snapshot(dataSource);
    }

    @AfterEach
    void restore() throws SQLException {
        ContentVersions.restore(dataSource, backup, cache);
    }

    private void sql(String statement) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(statement);
        }
    }

    @Test
    void theSampleImageIsReferencedAndAnUnknownKeyIsNot() {
        assertTrue(references.isReferenced(SAMPLE));
        assertFalse(references.isReferenced(OLD));
        assertFalse(references.isReferenced("site/projects/%"));
        assertEquals(Set.of(SAMPLE), references.referencedKeys());
    }

    @Test
    void aKeyOnlyInAnInactiveHistoricVersionStillCounts() throws SQLException {
        sql("UPDATE content_version SET active = false WHERE kind = 'projects' AND lang = 'es' AND active");
        sql("INSERT INTO content_version (kind, lang, version, content, active) VALUES ('projects', 'es', 2, "
                + "'{\"projects\": [{\"id\": \"x\", \"images\": [{\"key\": \"" + OLD + "\"}]}]}'::jsonb, true)");
        sql("UPDATE content_version SET content = '{\"projects\": []}'::jsonb WHERE kind = 'projects' AND lang = 'es' AND version = 2");
        // La versión 1 (inactiva) aún referencia la imagen del sample; la 2 (activa) ya no referencia nada.
        assertTrue(references.isReferenced(SAMPLE));
        assertFalse(references.isReferenced(OLD));
        assertEquals(Set.of(SAMPLE), references.referencedKeys());
    }

    @Test
    void imagesOfOtherKindsOrDocumentsWithoutTheFieldAreIgnored() throws SQLException {
        sql("UPDATE content_version SET content = jsonb_set(content, '{services,0,images}', "
                + "'[{\"key\": \"" + OLD + "\"}]'::jsonb) WHERE kind = 'services'");
        assertFalse(references.isReferenced(OLD));
        assertEquals(Set.of(SAMPLE), references.referencedKeys());
    }
}
