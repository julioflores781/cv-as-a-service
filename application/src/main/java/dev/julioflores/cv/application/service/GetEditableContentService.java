package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.content.EditableContent;
import dev.julioflores.cv.application.port.in.GetEditableContentUseCase;
import dev.julioflores.cv.application.port.out.ContentVersionRepository;

public class GetEditableContentService implements GetEditableContentUseCase {

    private final ContentVersionRepository versions;

    public GetEditableContentService(ContentVersionRepository versions) {
        this.versions = versions;
    }

    @Override
    public EditableContent get(String kind) {
        return ContentRequests.load(versions, ContentRequests.kind(kind));
    }
}
