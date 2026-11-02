package dev.julioflores.cv.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeTest {

    private static final WorkExperience JOB = new WorkExperience(
            "acme", "Acme", "Dev", null, new Period(YearMonth.of(2020, 1), null), null, null, null);
    private static final SkillGroup BACKEND = new SkillGroup("backend", "Backend", List.of("Java"));

    private final Resume resume = new Resume(
            Language.ES,
            new Profile("Julio", "Engineer", null, null, null, null, null),
            new Contact("a@b.c", null),
            null,
            List.of(JOB),
            List.of(BACKEND),
            new Qualifications(null, null, null),
            null);

    @Test
    void findsExperienceById() {
        assertEquals(JOB, resume.findExperience("acme").orElseThrow());
        assertTrue(resume.findExperience("nope").isEmpty());
    }

    @Test
    void findsSkillGroupIgnoringCase() {
        assertEquals(BACKEND, resume.findSkillGroup("BACKEND").orElseThrow());
        assertTrue(resume.findSkillGroup("nope").isEmpty());
    }

    @Test
    void nullCollectionsBecomeEmptyAndImmutable() {
        assertTrue(resume.impact().isEmpty());
        assertTrue(JOB.phases().isEmpty());
        assertTrue(resume.spokenLanguages().isEmpty());
    }
}
