package dev.julioflores.cv.application.service;

import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Resume;

final class ActiveResume {

    private ActiveResume() {}

    static Resume of(ResumeRepository resumes, Language language) {
        return resumes.findActive(language).orElseThrow(() -> new ResumeNotFoundException(language));
    }
}
