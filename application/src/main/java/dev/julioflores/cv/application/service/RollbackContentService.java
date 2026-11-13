package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.audit.AuditAction;
import dev.julioflores.cv.application.audit.AuditEvent;
import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.ContentValidator;
import dev.julioflores.cv.application.content.ContentVersion;
import dev.julioflores.cv.application.content.EditableContent;
import dev.julioflores.cv.application.content.Violation;
import dev.julioflores.cv.application.port.in.RollbackContentUseCase;
import dev.julioflores.cv.application.port.out.AuditLog;
import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import dev.julioflores.cv.application.port.out.ContentVersionRepository;
import dev.julioflores.cv.domain.Language;
import java.util.List;

public class RollbackContentService implements RollbackContentUseCase {

    private static final System.Logger LOG = System.getLogger(RollbackContentService.class.getName());

    private final ContentVersionRepository versions;
    private final ContentCacheInvalidator cache;
    private final AuditLog audit;

    public RollbackContentService(ContentVersionRepository versions, ContentCacheInvalidator cache, AuditLog audit) {
        this.versions = versions;
        this.cache = cache;
        this.audit = audit;
    }

    @Override
    public EditableContent rollback(String rawKind, String rawLanguage, int baseVersion, int targetVersion) {
        ContentKind kind = ContentRequests.kind(rawKind);
        Language language = ContentRequests.language(rawLanguage);

        int active = versions.active(kind, language).map(ContentVersion::version).orElse(0);
        if (active != baseVersion) {
            throw new ContentVersionConflictException(language.tag(), active);
        }
        ContentVersion target = versions.find(kind, language, targetVersion)
                .orElseThrow(() -> new ContentVersionNotFoundException(language.tag(), targetVersion));
        // Una instantánea vieja puede incumplir las reglas de hoy: no se reactiva algo que hoy no se podría guardar.
        List<Violation> violations = ContentValidator.validate(kind, language.tag(), target.content());
        if (!violations.isEmpty()) {
            throw new ContentInvalidException(violations);
        }

        versions.activate(kind, language, baseVersion, targetVersion);
        try {
            cache.invalidate(kind.value(), language);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Rolled back " + kind.value() + "/" + language.tag() + " but could not invalidate its cache; it expires with its TTL", e);
        }
        audit.record(AuditEvent.success(AuditAction.CONTENT_ROLLED_BACK,
                "kind", kind.value(), "lang", language.tag(), "from_version", baseVersion, "to_version", targetVersion));
        return ContentRequests.load(versions, kind);
    }
}
