package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.in.GetProjectsUseCase;
import dev.julioflores.cv.application.port.out.SiteContentRepository;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Project;
import java.util.List;

public class GetProjectsService implements GetProjectsUseCase {

    private final SiteContentRepository content;

    public GetProjectsService(SiteContentRepository content) {
        this.content = content;
    }

    @Override
    public List<Project> execute(Language language) {
        return content.projects(language);
    }
}
