package dev.julioflores.cv.infrastructure;

import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import dev.julioflores.cv.domain.Language;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/** Simula una publicación (versión nueva activa) y su reversión sobre content_version, para probar la invalidación. */
public final class ContentVersions {

    private ContentVersions() {}

    /** Desactiva la versión activa e inserta otra, con el título del primer servicio cambiado a {@code title}. */
    public static void publishServices(DataSource dataSource, String lang, String title) throws SQLException {
        execute(dataSource, "UPDATE content_version SET active = false WHERE kind = 'services' AND lang = '" + lang + "' AND active",
                "INSERT INTO content_version (kind, lang, version, content, active) "
                        + "SELECT 'services', lang, version + 1, "
                        + "jsonb_set(content, '{services,0,title}', to_jsonb('" + title + "'::text)), true "
                        + "FROM content_version WHERE kind = 'services' AND lang = '" + lang + "' AND version = "
                        + "(SELECT MAX(version) FROM content_version WHERE kind = 'services' AND lang = '" + lang + "')");
    }

    /** Borra la última versión y reactiva la anterior (deja la base como estaba antes de {@link #publishServices}). */
    public static void revertServices(DataSource dataSource, String lang) throws SQLException {
        execute(dataSource,
                "DELETE FROM content_version WHERE kind = 'services' AND lang = '" + lang + "' AND version = "
                        + "(SELECT MAX(version) FROM content_version WHERE kind = 'services' AND lang = '" + lang + "')",
                "UPDATE content_version SET active = true WHERE kind = 'services' AND lang = '" + lang + "' AND version = "
                        + "(SELECT MAX(version) FROM content_version WHERE kind = 'services' AND lang = '" + lang + "')");
    }

    /** Todas las filas de content_version tal como están (para devolver la base a su estado tras una prueba que escribe). */
    public static List<Object[]> snapshot(DataSource dataSource) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT kind, lang, version, content::text, active, published_at, "
                        + "comment, source FROM content_version ORDER BY id")) {
            while (result.next()) {
                rows.add(new Object[] {result.getString(1), result.getString(2), result.getInt(3), result.getString(4),
                        result.getBoolean(5), result.getTimestamp(6), result.getString(7), result.getString(8)});
            }
        }
        return rows;
    }

    /** Deja content_version exactamente como estaba en {@link #snapshot} y vacía la caché de lectura pública. */
    public static void restore(DataSource dataSource, List<Object[]> rows, ContentCacheInvalidator cache) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement delete = connection.createStatement();
                    PreparedStatement insert = connection.prepareStatement("INSERT INTO content_version "
                            + "(kind, lang, version, content, active, published_at, comment, source) "
                            + "VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)")) {
                delete.executeUpdate("DELETE FROM content_version");
                for (Object[] row : rows) {
                    insert.setString(1, (String) row[0]);
                    insert.setString(2, (String) row[1]);
                    insert.setInt(3, (Integer) row[2]);
                    insert.setString(4, (String) row[3]);
                    insert.setBoolean(5, (Boolean) row[4]);
                    insert.setTimestamp(6, (Timestamp) row[5]);
                    insert.setString(7, (String) row[6]);
                    insert.setString(8, (String) row[7]);
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
        for (String kind : List.of("services", "projects", "architecture")) {
            for (Language language : Language.values()) {
                cache.invalidate(kind, language);
            }
        }
    }

    private static void execute(DataSource dataSource, String... statements) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.executeUpdate(sql);
            }
        }
    }
}
