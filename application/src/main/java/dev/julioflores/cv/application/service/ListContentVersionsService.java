package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.content.ContentVersionSummary;
import dev.julioflores.cv.application.port.in.ListContentVersionsUseCase;
import dev.julioflores.cv.application.port.out.ContentVersionRepository;
import java.util.List;

public class ListContentVersionsService implements ListContentVersionsUseCase {

    static final int MAX_LIMIT = 200;

    private final ContentVersionRepository versions;

    public ListContentVersionsService(ContentVersionRepository versions) {
        this.versions = versions;
    }

    @Override
    public List<ContentVersionSummary> list(String kind, String language, int limit) {
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        return versions.list(ContentRequests.kind(kind), ContentRequests.language(language), bounded);
    }
}
