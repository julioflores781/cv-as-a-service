package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.in.GetProfileUseCase;
import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Profile;

public class GetProfileService implements GetProfileUseCase {

    private final ResumeRepository resumes;

    public GetProfileService(ResumeRepository resumes) {
        this.resumes = resumes;
    }

    @Override
    public Profile execute(Language language) {
        return ActiveResume.of(resumes, language).profile();
    }
}
