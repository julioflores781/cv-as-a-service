package dev.julioflores.cv.infrastructure.adapter.out.resumefile;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Resume;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Primer adaptador de salida: lee content/resume.{lang}.json del classpath.
 * El contenido no cambia mientras la app está en marcha, así que se parsea una sola vez por idioma
 * (en dev, editar el JSON reinicia la app y la caché empieza de cero).
 * Se sustituirá por el adaptador de PostgreSQL sin tocar dominio ni aplicación.
 */
@ApplicationScoped
public class ClasspathResumeRepository implements ResumeRepository {

    private final ObjectMapper mapper;
    private final Map<Language, Optional<Resume>> cache = new ConcurrentHashMap<>();

    public ClasspathResumeRepository(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Resume> findActive(Language language) {
        return cache.computeIfAbsent(language, this::load);
    }

    private Optional<Resume> load(Language language) {
        String resource = "/resume." + language.tag() + ".json";
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(ResumeJsonMapper.toResume(language, mapper.readTree(in)));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }
}
