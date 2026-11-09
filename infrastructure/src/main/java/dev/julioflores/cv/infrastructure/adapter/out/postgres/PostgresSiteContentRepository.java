package dev.julioflores.cv.infrastructure.adapter.out.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.application.port.out.SiteContentRepository;
import dev.julioflores.cv.domain.Architecture;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Project;
import dev.julioflores.cv.domain.ServiceOffer;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Lee la versión activa de cada contenido editable (tabla {@code content_version}) con la misma estrategia que el CV:
 * se recuerda {@code cv.content.cache-ttl}, y si la base de datos falla al refrescar se sigue sirviendo lo último leído.
 * Lo que no está publicado se sirve vacío: la web omite esa sección en lugar de fallar.
 */
@ApplicationScoped
public class PostgresSiteContentRepository implements SiteContentRepository {

    private static final Logger LOG = Logger.getLogger(PostgresSiteContentRepository.class);
    private static final String ACTIVE_CONTENT =
            "SELECT content::text FROM content_version WHERE kind = ? AND lang = ? AND active";
    /** Un contenido aún sin publicar se recuerda poco: aparece enseguida al cargarlo. */
    private static final long NOT_FOUND_TTL_NANOS = Duration.ofSeconds(10).toNanos();

    private record Key(String kind, Language language) {}

    private record Cached(Optional<JsonNode> document, long expiresAtNanos) {
        boolean isFresh() {
            return System.nanoTime() - expiresAtNanos < 0;
        }
    }

    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final long ttlNanos;
    private final Map<Key, Cached> cache = new ConcurrentHashMap<>();
    private final Map<Key, Object> locks = new ConcurrentHashMap<>();

    public PostgresSiteContentRepository(
            DataSource dataSource, ObjectMapper mapper, @ConfigProperty(name = "cv.content.cache-ttl") Duration ttl) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.ttlNanos = ttl.toNanos();
    }

    @Override
    public List<ServiceOffer> services(Language language) {
        return document("services", language).map(SiteContentJsonMapper::services).orElse(List.of());
    }

    @Override
    public List<Project> projects(Language language) {
        return document("projects", language).map(SiteContentJsonMapper::projects).orElse(List.of());
    }

    @Override
    public Architecture architecture(Language language) {
        return document("architecture", language).map(SiteContentJsonMapper::architecture).orElseGet(Architecture::empty);
    }

    private Optional<JsonNode> document(String kind, Language language) {
        Key key = new Key(kind, language);
        Cached cached = cache.get(key);
        if (cached != null && cached.isFresh()) {
            return cached.document();
        }
        // Un candado por contenido: tras caducar, una sola petición consulta la base de datos y el resto espera su resultado.
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            cached = cache.get(key);
            if (cached == null || !cached.isFresh()) {
                cached = refresh(key, cached);
                cache.put(key, cached);
            }
            return cached.document();
        }
    }

    private Cached refresh(Key key, Cached stale) {
        try {
            Optional<JsonNode> loaded = load(key);
            return new Cached(loaded, System.nanoTime() + (loaded.isPresent() ? ttlNanos : NOT_FOUND_TTL_NANOS));
        } catch (RuntimeException e) {
            if (stale == null) {
                throw e;
            }
            LOG.warnf(e, "Cannot refresh the '%s' content in '%s'; serving the last one read", key.kind(), key.language().tag());
            return new Cached(stale.document(), System.nanoTime() + ttlNanos);
        }
    }

    private Optional<JsonNode> load(Key key) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(ACTIVE_CONTENT)) {
            statement.setString(1, key.kind());
            statement.setString(2, key.language().tag());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapper.readTree(rows.getString(1))) : Optional.empty();
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException(
                    "Cannot read the active '" + key.kind() + "' content for '" + key.language().tag() + "'", e);
        }
    }
}
