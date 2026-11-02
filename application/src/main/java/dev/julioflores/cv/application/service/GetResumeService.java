package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.in.GetResumeUseCase;
import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Resume;

public class GetResumeService implements GetResumeUseCase {

    private final ResumeRepository resumes;

    public GetResumeService(ResumeRepository resumes) {
        this.resumes = resumes;
    }

    @Override
    public Resume execute(Language language) {
        return ActiveResume.of(resumes, language);
    }
}
