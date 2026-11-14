package dev.julioflores.cv.application;

import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.application.content.ContentVersion;
import dev.julioflores.cv.application.content.ContentVersionSummary;
import dev.julioflores.cv.application.port.out.ContentCacheInvalidator;
import dev.julioflores.cv.application.port.out.ContentVersionRepository;
import dev.julioflores.cv.application.service.ContentVersionConflictException;
import dev.julioflores.cv.application.service.ContentVersionNotFoundException;
import dev.julioflores.cv.domain.Language;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fakes en memoria de los puertos de contenido editable, con la misma semántica que el adaptador real. */
final class ContentFakes {

    private ContentFakes() {}

    static final class Row {
        final ContentKind kind;
        final Language language;
        final int version;
        final Map<String, Object> content;
        final String source;
        final String comment;
        boolean active;

        Row(ContentKind kind, Language language, int version, Map<String, Object> content, boolean active, String source, String comment) {
            this.kind = kind;
            this.language = language;
            this.version = version;
            this.content = content;
            this.active = active;
            this.source = source;
            this.comment = comment;
        }
    }

    static final class FakeVersions implements ContentVersionRepository {
        final List<Row> rows = new ArrayList<>();
        RuntimeException failOnPublish;
        int activeCalls;
        /** Las lecturas de la versión activa a partir de la n+1 fallan (-1: nunca). */
        int failActiveAfter = -1;
        int publishCalls;
        int activateCalls;
        String lastComment;

        /** Siembra una versión (activa o no) como lo haría make-seed.py. */
        FakeVersions seed(ContentKind kind, Language language, int version, boolean active, Map<String, Object> content) {
            rows.add(new Row(kind, language, version, content, active, "seed", null));
            return this;
        }

        private ContentVersion view(Row r) {
            return new ContentVersion(r.version, r.active, Instant.parse("2026-11-01T10:00:00Z"), r.source, r.comment, r.content);
        }

        private int activeVersion(ContentKind kind, Language language) {
            return rows.stream().filter(r -> r.kind == kind && r.language == language && r.active)
                    .mapToInt(r -> r.version).findFirst().orElse(0);
        }

        @Override
        public Optional<ContentVersion> active(ContentKind kind, Language language) {
            if (failActiveAfter >= 0 && ++activeCalls > failActiveAfter) {
                throw new IllegalStateException("read failed");
            }
            return rows.stream().filter(r -> r.kind == kind && r.language == language && r.active).findFirst().map(this::view);
        }

        @Override
        public Optional<ContentVersion> find(ContentKind kind, Language language, int version) {
            return rows.stream().filter(r -> r.kind == kind && r.language == language && r.version == version)
                    .findFirst().map(this::view);
        }

        @Override
        public List<ContentVersionSummary> list(ContentKind kind, Language language, int limit) {
            return rows.stream().filter(r -> r.kind == kind && r.language == language)
                    .sorted(Comparator.comparingInt((Row r) -> r.version).reversed())
                    .limit(limit)
                    .map(r -> new ContentVersionSummary(r.version, r.active, Instant.parse("2026-11-01T10:00:00Z"), r.source, r.comment))
                    .toList();
        }

        @Override
        public void publish(ContentKind kind, Map<Language, Integer> baseVersions, Map<Language, Map<String, Object>> documents, String comment) {
            publishCalls++;
            if (failOnPublish != null) {
                throw failOnPublish;
            }
            for (Language language : documents.keySet()) {
                int active = activeVersion(kind, language);
                if (active != baseVersions.get(language)) {
                    throw new ContentVersionConflictException(language.tag(), active);
                }
            }
            lastComment = comment;
            for (Map.Entry<Language, Map<String, Object>> entry : documents.entrySet()) {
                Language language = entry.getKey();
                int next = rows.stream().filter(r -> r.kind == kind && r.language == language)
                        .mapToInt(r -> r.version).max().orElse(0) + 1;
                rows.stream().filter(r -> r.kind == kind && r.language == language).forEach(r -> r.active = false);
                rows.add(new Row(kind, language, next, entry.getValue(), true, "admin", comment));
            }
        }

        @Override
        public void activate(ContentKind kind, Language language, int baseVersion, int targetVersion) {
            activateCalls++;
            int active = activeVersion(kind, language);
            if (active != baseVersion) {
                throw new ContentVersionConflictException(language.tag(), active);
            }
            Row target = rows.stream().filter(r -> r.kind == kind && r.language == language && r.version == targetVersion)
                    .findFirst().orElseThrow(() -> new ContentVersionNotFoundException(language.tag(), targetVersion));
            rows.stream().filter(r -> r.kind == kind && r.language == language).forEach(r -> r.active = false);
            target.active = true;
        }
    }

    /** Anota las invalidaciones y las ve en el orden en que llegan. */
    static final class RecordingInvalidator implements ContentCacheInvalidator {
        final List<String> calls = new ArrayList<>();
        /** Cuántas filas había en el repositorio de la prueba cuando se invalidó: prueba que fue DESPUÉS de publicar. */
        final List<Integer> rowsAtCall = new ArrayList<>();
        FakeVersions watching;
        RuntimeException failWith;

        @Override
        public void invalidate(String kind, Language language) {
            calls.add(kind + "/" + language.tag());
            rowsAtCall.add(watching == null ? -1 : watching.rows.size());
            if (failWith != null) {
                throw failWith;
            }
        }
    }
}
