package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.julioflores.cv.application.service.GetProfileService;
import dev.julioflores.cv.application.service.ResumeNotFoundException;
import dev.julioflores.cv.domain.Language;
import org.junit.jupiter.api.Test;

class GetProfileServiceTest {

    private final GetProfileService service = new GetProfileService(ResumeFixtures.ONLY_SPANISH);

    @Test
    void returnsTheProfileOfTheActiveResume() {
        assertEquals(ResumeFixtures.PROFILE, service.execute(Language.ES));
    }

    @Test
    void failsWhenThereIsNoResumeForTheLanguage() {
        assertThrows(ResumeNotFoundException.class, () -> service.execute(Language.EN));
    }
}
