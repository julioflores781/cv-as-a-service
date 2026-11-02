package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.in.GetImpactUseCase;
import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.domain.Impact;
import dev.julioflores.cv.domain.Language;
import java.util.List;

public class GetImpactService implements GetImpactUseCase {

    private final ResumeRepository resumes;

    public GetImpactService(ResumeRepository resumes) {
        this.resumes = resumes;
    }

    @Override
    public List<Impact> execute(Language language) {
        return ActiveResume.of(resumes, language).impact();
    }
}
