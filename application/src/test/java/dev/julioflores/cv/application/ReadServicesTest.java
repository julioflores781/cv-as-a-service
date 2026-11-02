package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.application.service.ExperienceNotFoundException;
import dev.julioflores.cv.application.service.GetContactService;
import dev.julioflores.cv.application.service.GetEducationService;
import dev.julioflores.cv.application.service.GetExperienceService;
import dev.julioflores.cv.application.service.GetImpactService;
import dev.julioflores.cv.application.service.GetResumeService;
import dev.julioflores.cv.application.service.GetSkillsService;
import dev.julioflores.cv.application.service.ResumeNotFoundException;
import dev.julioflores.cv.application.service.SkillGroupNotFoundException;
import dev.julioflores.cv.domain.Language;
import org.junit.jupiter.api.Test;

class ReadServicesTest {

    private final ResumeRepository repo = ResumeFixtures.ONLY_SPANISH;

    @Test
    void experienceKeepsTheOrderOfTheResume() {
        var jobs = new GetExperienceService(repo).list(Language.ES);
        assertEquals(2, jobs.size());
        assertEquals("sale-and-brick", jobs.get(0).id());
    }

    @Test
    void experienceByIdFindsItOrFails() {
        var service = new GetExperienceService(repo);
        assertEquals(ResumeFixtures.OLD_JOB, service.byId(Language.ES, "central-madeirense"));
        assertThrows(ExperienceNotFoundException.class, () -> service.byId(Language.ES, "nope"));
    }

    @Test
    void skillsAllAndByGroup() {
        var service = new GetSkillsService(repo);
        assertEquals(2, service.all(Language.ES).size());
        assertEquals(ResumeFixtures.DATA, service.byGroup(Language.ES, "DATA"));
        assertThrows(SkillGroupNotFoundException.class, () -> service.byGroup(Language.ES, "nope"));
    }

    @Test
    void impactEducationContactAndResume() {
        assertEquals(1, new GetImpactService(repo).execute(Language.ES).size());
        assertEquals(1, new GetEducationService(repo).execute(Language.ES).courses().size());
        assertEquals("julio@example.com", new GetContactService(repo).execute(Language.ES).email());
        assertEquals(ResumeFixtures.SPANISH, new GetResumeService(repo).execute(Language.ES));
    }

    @Test
    void everyUseCaseFailsWithoutAnActiveResume() {
        assertThrows(ResumeNotFoundException.class, () -> new GetImpactService(repo).execute(Language.EN));
        assertThrows(ResumeNotFoundException.class, () -> new GetExperienceService(repo).list(Language.EN));
        assertThrows(ResumeNotFoundException.class, () -> new GetSkillsService(repo).all(Language.EN));
        assertThrows(ResumeNotFoundException.class, () -> new GetEducationService(repo).execute(Language.EN));
        assertThrows(ResumeNotFoundException.class, () -> new GetContactService(repo).execute(Language.EN));
        assertThrows(ResumeNotFoundException.class, () -> new GetResumeService(repo).execute(Language.EN));
    }
}
