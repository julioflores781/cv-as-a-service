package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.content.ContentVersion;
import dev.julioflores.cv.application.port.in.GetContentVersionUseCase;
import dev.julioflores.cv.application.port.out.ContentVersionRepository;
import dev.julioflores.cv.domain.Language;

public class GetContentVersionService implements GetContentVersionUseCase {

    private final ContentVersionRepository versions;

    public GetContentVersionService(ContentVersionRepository versions) {
        this.versions = versions;
    }

    @Override
    public ContentVersion get(String kind, String language, int version) {
        Language lang = ContentRequests.language(language);
        return versions.find(ContentRequests.kind(kind), lang, version)
                .orElseThrow(() -> new ContentVersionNotFoundException(lang.tag(), version));
    }
}
