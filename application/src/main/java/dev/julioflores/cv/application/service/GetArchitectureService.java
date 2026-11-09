package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.in.GetArchitectureUseCase;
import dev.julioflores.cv.application.port.out.SiteContentRepository;
import dev.julioflores.cv.domain.Architecture;
import dev.julioflores.cv.domain.Language;

public class GetArchitectureService implements GetArchitectureUseCase {

    private final SiteContentRepository content;

    public GetArchitectureService(SiteContentRepository content) {
        this.content = content;
    }

    @Override
    public Architecture execute(Language language) {
        return content.architecture(language);
    }
}
