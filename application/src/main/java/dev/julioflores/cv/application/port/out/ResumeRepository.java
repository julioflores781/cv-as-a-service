package dev.julioflores.cv.application.port.out;

import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Resume;
import java.util.Optional;

public interface ResumeRepository {

    Optional<Resume> findActive(Language language);
}
