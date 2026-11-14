package dev.julioflores.cv.infrastructure.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.ContentVersion;
import dev.julioflores.cv.application.content.ContentVersionSummary;
import dev.julioflores.cv.application.port.out.ContentVersionRepository;
import dev.julioflores.cv.application.service.ContentVersionConflictException;
import dev.julioflores.cv.application.service.ContentVersionNotFoundException;
import dev.julioflores.cv.domain.Language;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Versiones de {@code content_version}. Cada escritura es UNA transacción que primero toma un candado asesor por tipo e
 * idioma (siempre ES y luego EN, para no interbloquearse), compara con la versión base y solo entonces escribe: dos guardados
 * simultáneos con la misma base se serializan y el segundo ve la activa nueva (409). Convive con {@code make-seed.py}, que
 * hace lo mismo sin candado: si coinciden, {@code UNIQUE (kind, lang, version)} hace perder a uno sin corromper nada.
 */
@ApplicationScoped
public class PostgresContentVersionRepository implements ContentVersionRepository {

    private static final String COLUMNS = "version, active, published_at, source, comment, content::text";
    private static final String ACTIVE = "SELECT " + COLUMNS + " FROM content_version WHERE kind = ? AND lang = ? AND active";
    private static final String FIND = "SELECT " + COLUMNS + " FROM content_version WHERE kind = ? AND lang = ? AND version = ?";
    private static final String LIST = "SELECT version, active, published_at, source, comment FROM content_version "
            + "WHERE kind = ? AND lang = ? ORDER BY version DESC LIMIT ?";
    private static final String LOCK = "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))";
    private static final String ACTIVE_VERSION = "SELECT version FROM content_version WHERE kind = ? AND lang = ? AND active";
    private static final String DEACTIVATE = "UPDATE content_version SET active = false WHERE kind = ? AND lang = ? AND active";
    // MAX y no «activa + 1»: tras un rollback existen versiones posteriores a la activa y no se pueden reutilizar.
    private static final String INSERT = "INSERT INTO content_version (kind, lang, version, content, active, source, comment) "
            + "SELECT ?, ?, COALESCE(MAX(version), 0) + 1, ?::jsonb, true, 'admin', ? FROM content_version "
            + "WHERE kind = ? AND lang = ?";
    private static final String EXISTS = "SELECT 1 FROM content_version WHERE kind = ? AND lang = ? AND version = ?";
    private static final String ACTIVATE = "UPDATE content_version SET active = true WHERE kind = ? AND lang = ? AND version = ?";

    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public PostgresContentVersionRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    @Override
    public Optional<ContentVersion> active(ContentKind kind, Language language) {
        return queryOne(ACTIVE, kind, language, null);
    }

    @Override
    public Optional<ContentVersion> find(ContentKind kind, Language language, int version) {
        return queryOne(FIND, kind, language, version);
    }

    private Optional<ContentVersion> queryOne(String sql, ContentKind kind, Language language, Integer version) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, kind.value());
            statement.setString(2, language.tag());
            if (version != null) {
                statement.setInt(3, version);
            }
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read '" + kind.value() + "' content for '" + language.tag() + "'", e);
        }
    }

    private ContentVersion read(ResultSet rows) throws SQLException {
        try {
            Map<String, Object> content = mapper.readValue(rows.getString(6), new TypeReference<LinkedHashMap<String, Object>>() {});
            return new ContentVersion(rows.getInt(1), rows.getBoolean(2), instant(rows.getTimestamp(3)), rows.getString(4),
                    rows.getString(5), content);
        } catch (IOException e) {
            throw new IllegalStateException("A stored content document is not a JSON object", e);
        }
    }

    @Override
    public List<ContentVersionSummary> list(ContentKind kind, Language language, int limit) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LIST)) {
            statement.setString(1, kind.value());
            statement.setString(2, language.tag());
            statement.setInt(3, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<ContentVersionSummary> versions = new ArrayList<>();
                while (rows.next()) {
                    versions.add(new ContentVersionSummary(
                            rows.getInt(1), rows.getBoolean(2), instant(rows.getTimestamp(3)), rows.getString(4), rows.getString(5)));
                }
                return versions;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot list '" + kind.value() + "' versions for '" + language.tag() + "'", e);
        }
    }

    @Override
    public void publish(
            ContentKind kind,
            Map<Language, Integer> baseVersions,
            Map<Language, Map<String, Object>> documents,
            String comment) {
        // Se normaliza ANTES de abrir la transacción: si un documento no se pudiera escribir, no se toca nada.
        Map<Language, String> canonical = new LinkedHashMap<>();
        for (Language language : Language.values()) {
            Map<String, Object> document = documents.get(language);
            if (document != null) {
                canonical.put(language, json(SiteContentJsonWriter.canonical(kind, mapper.valueToTree(document))));
            }
        }
        inTransaction(connection -> {
            for (Language language : canonical.keySet()) { // ES y luego EN: el mismo orden en todos los guardados
                lock(connection, kind, language);
            }
            for (Language language : canonical.keySet()) {
                int active = activeVersion(connection, kind, language);
                if (active != baseVersions.get(language)) {
                    throw new ContentVersionConflictException(language.tag(), active);
                }
            }
            for (Map.Entry<Language, String> entry : canonical.entrySet()) {
                deactivate(connection, kind, entry.getKey());
                insert(connection, kind, entry.getKey(), entry.getValue(), comment);
            }
        });
    }

    @Override
    public void activate(ContentKind kind, Language language, int baseVersion, int targetVersion) {
        inTransaction(connection -> {
            lock(connection, kind, language);
            int active = activeVersion(connection, kind, language);
            if (active != baseVersion) {
                throw new ContentVersionConflictException(language.tag(), active);
            }
            if (!exists(connection, kind, language, targetVersion)) {
                throw new ContentVersionNotFoundException(language.tag(), targetVersion);
            }
            deactivate(connection, kind, language);
            try (PreparedStatement statement = connection.prepareStatement(ACTIVATE)) {
                statement.setString(1, kind.value());
                statement.setString(2, language.tag());
                statement.setInt(3, targetVersion);
                statement.executeUpdate();
            }
        });
    }

    @FunctionalInterface
    private interface Work {
        void run(Connection connection) throws SQLException;
    }

    /** Todo o nada: confirma si {@code work} termina y deshace si lanza cualquier cosa (incluido un conflicto). */
    private void inTransaction(Work work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("Could not write the content version", e);
            } finally {
                connection.setAutoCommit(true); // la conexión vuelve limpia al pool
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not write the content version", e);
        }
    }

    private static void lock(Connection connection, ContentKind kind, Language language) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK)) {
            statement.setString(1, "content:" + kind.value() + ":" + language.tag());
            statement.execute();
        }
    }

    private static int activeVersion(Connection connection, ContentKind kind, Language language) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ACTIVE_VERSION)) {
            statement.setString(1, kind.value());
            statement.setString(2, language.tag());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }

    private static boolean exists(Connection connection, ContentKind kind, Language language, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(EXISTS)) {
            statement.setString(1, kind.value());
            statement.setString(2, language.tag());
            statement.setInt(3, version);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static void deactivate(Connection connection, ContentKind kind, Language language) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DEACTIVATE)) {
            statement.setString(1, kind.value());
            statement.setString(2, language.tag());
            statement.executeUpdate();
        }
    }

    private static void insert(Connection connection, ContentKind kind, Language language, String json, String comment)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, kind.value());
            statement.setString(2, language.tag());
            statement.setString(3, json);
            statement.setString(4, comment);
            statement.setString(5, kind.value());
            statement.setString(6, language.tag());
            statement.executeUpdate();
        }
    }

    private String json(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize a content document", e);
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
