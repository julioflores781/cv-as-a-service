package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.in.GetSkillsUseCase;
import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.SkillGroup;
import java.util.List;

public class GetSkillsService implements GetSkillsUseCase {

    private final ResumeRepository resumes;

    public GetSkillsService(ResumeRepository resumes) {
        this.resumes = resumes;
    }

    @Override
    public List<SkillGroup> all(Language language) {
        return ActiveResume.of(resumes, language).skills();
    }

    @Override
    public SkillGroup byGroup(Language language, String group) {
        return ActiveResume.of(resumes, language).findSkillGroup(group)
                .orElseThrow(() -> new SkillGroupNotFoundException(group));
    }
}
