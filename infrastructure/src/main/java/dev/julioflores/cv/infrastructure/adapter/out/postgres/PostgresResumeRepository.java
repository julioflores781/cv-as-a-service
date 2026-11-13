package dev.julioflores.cv.infrastructure.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.infrastructure.config.ErrorLogDescription;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Resume;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Lee la versión activa del CV de cada idioma desde PostgreSQL (tabla {@code resume_version}).
 * El CV cambia pocas veces al año, así que cada idioma se mantiene en memoria un rato
 * ({@code cv.resume.cache-ttl}) y una publicación nueva se ve como mucho tras ese tiempo.
 * Si la base de datos falla al refrescar, se sigue sirviendo el último CV leído.
 */
@ApplicationScoped
public class PostgresResumeRepository implements ResumeRepository {

    private static final Logger LOG = Logger.getLogger(PostgresResumeRepository.class);
    private static final String ACTIVE_CONTENT = "SELECT content::text FROM resume_version WHERE lang = ? AND active";
    /** Un CV aún sin publicar se recuerda poco: aparece enseguida al cargarlo, sin una consulta por petición. */
    private static final long NOT_FOUND_TTL_NANOS = Duration.ofSeconds(10).toNanos();

    private record Cached(Optional<Resume> resume, long expiresAtNanos) {
        boolean isFresh() {
            return System.nanoTime() - expiresAtNanos < 0;
        }
    }

    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final long ttlNanos;
    private final Map<Language, Cached> cache = new ConcurrentHashMap<>();
    private final Map<Language, Object> locks = new EnumMap<>(Language.class);

    public PostgresResumeRepository(
            DataSource dataSource, ObjectMapper mapper, @ConfigProperty(name = "cv.resume.cache-ttl") Duration ttl) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.ttlNanos = ttl.toNanos();
        for (Language language : Language.values()) {
            locks.put(language, new Object());
        }
    }

    @Override
    public Optional<Resume> findActive(Language language) {
        Cached cached = cache.get(language);
        if (cached != null && cached.isFresh()) {
            return cached.resume();
        }
        // Un candado por idioma: tras caducar, una sola petición consulta la base de datos y el resto espera su resultado.
        synchronized (locks.get(language)) {
            cached = cache.get(language);
            if (cached == null || !cached.isFresh()) {
                cached = refresh(language, cached);
                cache.put(language, cached);
            }
            return cached.resume();
        }
    }

    private Cached refresh(Language language, Cached stale) {
        try {
            Optional<Resume> loaded = load(language);
            return new Cached(loaded, System.nanoTime() + (loaded.isPresent() ? ttlNanos : NOT_FOUND_TTL_NANOS));
        } catch (RuntimeException e) {
            if (stale == null) {
                throw e;
            }
            // Sin mensaje ni traza de errores SQL/JDBC (pueden llevar host, URL o filas): ver ErrorLogDescription.
            ErrorLogDescription.Described described = ErrorLogDescription.describe(e);
            LOG.warnf(described.thrown(), "Cannot refresh the '%s' resume (%s); serving the last one read", language.tag(), described.message());
            return new Cached(stale.resume(), System.nanoTime() + ttlNanos);
        }
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
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Cannot read the active resume for '" + language.tag() + "'", e);
        }
    }
}
