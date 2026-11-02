package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.application.service.ExperienceNotFoundException;
import dev.julioflores.cv.application.service.GetExperienceService;
import dev.julioflores.cv.application.service.GetSkillsService;
import dev.julioflores.cv.application.service.ResumeNotFoundException;
import dev.julioflores.cv.application.service.SkillGroupNotFoundException;
import dev.julioflores.cv.domain.Language;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorCodesTest {

    @Test
    void unknownExperienceCarriesItsCodeAndTheIdAsArgument() {
        var e = assertThrows(ExperienceNotFoundException.class,
                () -> new GetExperienceService(ResumeFixtures.ONLY_SPANISH).byId(Language.ES, "nope"));
        assertEquals(ErrorCode.EXPERIENCE_NOT_FOUND, e.code());
        assertEquals(List.of("nope"), e.args());
    }

    @Test
    void unknownSkillGroupCarriesItsCodeAndTheGroupAsArgument() {
        var e = assertThrows(SkillGroupNotFoundException.class,
                () -> new GetSkillsService(ResumeFixtures.ONLY_SPANISH).byGroup(Language.ES, "nope"));
        assertEquals(ErrorCode.SKILL_GROUP_NOT_FOUND, e.code());
        assertEquals(List.of("nope"), e.args());
    }

    @Test
    void missingResumeCarriesTheLanguageTag() {
        var e = new ResumeNotFoundException(Language.EN);
        assertEquals(ErrorCode.RESUME_NOT_FOUND, e.code());
        assertEquals(List.of("en"), e.args());
    }

    @Test
    void developerMessageStaysInEnglishForLogs() {
        assertEquals("No experience with id 'x'", new ExperienceNotFoundException("x").getMessage());
    }
}
