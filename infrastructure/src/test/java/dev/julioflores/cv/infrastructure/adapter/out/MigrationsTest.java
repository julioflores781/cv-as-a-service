package dev.julioflores.cv.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** Aplica las migraciones sobre un esquema aparte, en dos pasos (V1..V3 y luego V4), como en producción. */
@QuarkusTest
class MigrationsTest {

    private static final String SCHEMA = "mig_v4";

    @Inject
    DataSource dataSource;

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration")
                .placeholderReplacement(false)
                .cleanDisabled(false)
                .target(target)
                .load();
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    private int count(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery(sql)) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int adminTables() throws SQLException {
        return count("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = 'mig_v4' AND table_name = 'admin_account'");
    }

    @Test
    void v4CreatesAnEmptyAdminTableThatRejectsASecondAdminAndNonArgonHashes() throws Exception { // T67
        try {
            flyway("3").migrate();
            assertEquals(0, adminTables());
            flyway("4").migrate();
            assertEquals(1, adminTables());
            assertEquals(0, count("SELECT count(*) FROM mig_v4.admin_account"));

            exec("INSERT INTO mig_v4.admin_account (id, password_hash) VALUES (1, '$argon2id$x')");
            assertEquals(1, count("SELECT session_version FROM mig_v4.admin_account"));
            assertThrows(SQLException.class,
                    () -> exec("INSERT INTO mig_v4.admin_account (id, password_hash) VALUES (2, '$argon2id$x')"));
            assertThrows(SQLException.class,
                    () -> exec("UPDATE mig_v4.admin_account SET password_hash = '$2a$10$bcrypt' WHERE id = 1"));
            assertThrows(SQLException.class,
                    () -> exec("UPDATE mig_v4.admin_account SET session_version = 0 WHERE id = 1"));
        } finally {
            flyway("4").clean();
        }
    }

    @Test
    void v5AddsAuditColumnsToExistingRowsWithoutTouchingThemAndEnforcesItsChecks() throws Exception { // T68
        try {
            flyway("4").migrate();
            exec("INSERT INTO mig_v4.content_version (kind, lang, version, content, active) "
                    + "VALUES ('services', 'es', 1, '{\"services\":[]}', true)");
            flyway("5").migrate();

            assertEquals(1, count("SELECT count(*) FROM mig_v4.content_version WHERE source = 'seed' AND comment IS NULL "
                    + "AND active AND version = 1 AND content = '{\"services\":[]}'::jsonb"));
            exec("INSERT INTO mig_v4.content_version (kind, lang, version, content, source, comment) "
                    + "VALUES ('services', 'es', 2, '{}', 'admin', 'una nota')");
            assertThrows(SQLException.class, () -> exec("INSERT INTO mig_v4.content_version (kind, lang, version, content, source) "
                    + "VALUES ('services', 'es', 3, '{}', 'otro')"));
            assertThrows(SQLException.class, () -> exec("INSERT INTO mig_v4.content_version (kind, lang, version, content, comment) "
                    + "VALUES ('services', 'es', 4, '{}', repeat('x', 201))"));
            exec("INSERT INTO mig_v4.content_version (kind, lang, version, content, comment) "
                    + "VALUES ('services', 'es', 5, '{}', repeat('x', 200))");
            // Sigue valiendo la restricción de una sola versión activa.
            assertThrows(SQLException.class, () -> exec("UPDATE mig_v4.content_version SET active = true WHERE version = 2"));
        } finally {
            flyway("5").clean();
        }
    }
}
