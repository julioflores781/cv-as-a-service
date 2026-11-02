package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.in.GetExperienceUseCase;
import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.WorkExperience;
import java.util.List;

public class GetExperienceService implements GetExperienceUseCase {

    private final ResumeRepository resumes;

    public GetExperienceService(ResumeRepository resumes) {
        this.resumes = resumes;
    }

    @Override
    public List<WorkExperience> list(Language language) {
        return ActiveResume.of(resumes, language).work();
    }

    @Override
    public WorkExperience byId(Language language, String id) {
        return ActiveResume.of(resumes, language).findExperience(id)
                .orElseThrow(() -> new ExperienceNotFoundException(id));
    }
}
