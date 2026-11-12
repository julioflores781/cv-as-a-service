package dev.julioflores.cv.infrastructure.adapter.out.postgres;

import dev.julioflores.cv.application.port.out.ImageReferenceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;

/**
 * Qué claves de imagen guardan los proyectos. Mira TODAS las filas de {@code content_version} (activas e históricas): un
 * rollback reactiva una versión vieja y no debe encontrar imágenes borradas. Es una consulta de volumen mínimo (un administrador).
 */
@ApplicationScoped
public class PostgresImageReferenceRepository implements ImageReferenceRepository {

    private static final String IS_REFERENCED = """
            SELECT EXISTS (SELECT 1 FROM content_version WHERE kind = 'projects'
              AND jsonb_path_exists(content, '$.projects[*].images[*] ? (@.key == $k)', jsonb_build_object('k', ?::text)))""";

    private static final String ALL_KEYS = """
            SELECT DISTINCT image ->> 'key'
              FROM content_version, jsonb_path_query(content, '$.projects[*].images[*]') AS image
             WHERE kind = 'projects'""";

    private final DataSource dataSource;

    public PostgresImageReferenceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean isReferenced(String key) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(IS_REFERENCED)) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not check image references", e);
        }
    }

    @Override
    public Set<String> referencedKeys() {
        Set<String> keys = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(ALL_KEYS);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                keys.add(rows.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read image references", e);
        }
        return keys;
    }
}
