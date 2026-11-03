package dev.julioflores.cv.infrastructure.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Resume;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Lee la versión activa del CV de cada idioma desde PostgreSQL (tabla {@code resume_version}).
 * El CV cambia pocas veces al año, así que cada idioma se mantiene en memoria un rato
 * ({@code cv.resume.cache-ttl}) y una publicación nueva se ve como mucho tras ese tiempo.
 */
@ApplicationScoped
public class PostgresResumeRepository implements ResumeRepository {

    private static final String ACTIVE_CONTENT = "SELECT content::text FROM resume_version WHERE lang = ? AND active";

    private record Cached(Optional<Resume> resume, long loadedAtNanos) {}

    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final long ttlNanos;
    private final Map<Language, Cached> cache = new ConcurrentHashMap<>();

    public PostgresResumeRepository(
            DataSource dataSource, ObjectMapper mapper, @ConfigProperty(name = "cv.resume.cache-ttl") Duration ttl) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.ttlNanos = ttl.toNanos();
    }

    @Override
    public Optional<Resume> findActive(Language language) {
        long now = System.nanoTime();
        Cached cached = cache.get(language);
        if (cached != null && now - cached.loadedAtNanos() < ttlNanos) {
            return cached.resume();
        }
        Optional<Resume> loaded = load(language);
        // Solo se recuerda lo encontrado: un CV que aún no se ha publicado debe aparecer en cuanto se cargue.
        if (loaded.isPresent()) {
            cache.put(language, new Cached(loaded, now));
        } else {
            cache.remove(language);
        }
        return loaded;
    }

    private Optional<Resume> load(Language language) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(ACTIVE_CONTENT)) {
            statement.setString(1, language.tag());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(ResumeJsonMapper.toResume(language, mapper.readTree(rows.getString(1))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the active resume for '" + language.tag() + "'", e);
        } catch (IOException e) {
            throw new UncheckedIOException("The stored resume for '" + language.tag() + "' is not valid JSON", e);
        }
    }
}
