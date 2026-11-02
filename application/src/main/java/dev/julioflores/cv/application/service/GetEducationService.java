package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.in.GetEducationUseCase;
import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Qualifications;

public class GetEducationService implements GetEducationUseCase {

    private final ResumeRepository resumes;

    public GetEducationService(ResumeRepository resumes) {
        this.resumes = resumes;
    }

    @Override
    public Qualifications execute(Language language) {
        return ActiveResume.of(resumes, language).qualifications();
    }
}
