package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.in.GetContactUseCase;
import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.domain.Contact;
import dev.julioflores.cv.domain.Language;

public class GetContactService implements GetContactUseCase {

    private final ResumeRepository resumes;

    public GetContactService(ResumeRepository resumes) {
        this.resumes = resumes;
    }

    @Override
    public Contact execute(Language language) {
        return ActiveResume.of(resumes, language).contact();
    }
}
