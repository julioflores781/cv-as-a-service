package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.ContentParity;
import dev.julioflores.cv.application.content.ContentValidator;
import dev.julioflores.cv.application.content.EditableContent;
import dev.julioflores.cv.application.content.SaveContentCommand;
import dev.julioflores.cv.application.content.Violation;
import dev.julioflores.cv.application.content.ViolationCode;
import dev.julioflores.cv.application.port.in.SaveEditableContentUseCase;
import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import dev.julioflores.cv.application.port.out.ContentVersionRepository;
import dev.julioflores.cv.domain.Language;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SaveEditableContentService implements SaveEditableContentUseCase {

    private static final System.Logger LOG = System.getLogger(SaveEditableContentService.class.getName());

    private final ContentVersionRepository versions;
    private final ContentCacheInvalidator cache;

    public SaveEditableContentService(ContentVersionRepository versions, ContentCacheInvalidator cache) {
        this.versions = versions;
        this.cache = cache;
    }

    @Override
    public EditableContent save(String rawKind, SaveContentCommand command) {
        ContentKind kind = ContentRequests.kind(rawKind);
        String comment = command.comment() == null || command.comment().isBlank() ? null : command.comment().strip();

        List<Violation> violations = new ArrayList<>();
        if (comment != null && comment.codePointCount(0, comment.length()) > ContentValidator.MAX_COMMENT) {
            violations.add(new Violation("comment", ViolationCode.TOO_LONG));
        }
        violations.addAll(ContentValidator.validate(kind, "es", command.es()));
        violations.addAll(ContentValidator.validate(kind, "en", command.en()));
        if (!violations.isEmpty()) {
            throw new ContentInvalidException(violations);
        }
        List<Violation> differences = ContentParity.compare(kind, command.es(), command.en());
        if (!differences.isEmpty()) {
            throw new ContentParityBrokenException(differences);
        }

        Map<Language, Integer> bases = new EnumMap<>(Language.class);
        bases.put(Language.ES, command.baseVersionEs());
        bases.put(Language.EN, command.baseVersionEn());
        Map<Language, Map<String, Object>> documents = new EnumMap<>(Language.class);
        documents.put(Language.ES, command.es());
        documents.put(Language.EN, command.en());
        versions.publish(kind, bases, documents, comment);

        for (Language language : Language.values()) {
            invalidate(kind, language);
        }
        return ContentRequests.load(versions, kind);
    }

    /** El guardado ya está hecho: si la caché no se puede invalidar, lo peor es esperar a su TTL; solo se avisa. */
    private void invalidate(ContentKind kind, Language language) {
        try {
            cache.invalidate(kind.value(), language);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Saved " + kind.value() + "/" + language.tag() + " but could not invalidate its cache; it expires with its TTL", e);
        }
    }
}
