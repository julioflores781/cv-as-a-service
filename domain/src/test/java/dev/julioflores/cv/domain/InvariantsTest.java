package dev.julioflores.cv.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Los objetos de valor rechazan lo que no pueden representar y no se dejan mutar desde fuera. */
class InvariantsTest {

    private static final Period PERIOD = new Period(YearMonth.of(2020, 1), null);
    private static final Profile PROFILE = new Profile("Julio", "Engineer", null, null, null, null, null);
    private static final Contact CONTACT = new Contact("a@b.c", null);
    private static final Qualifications QUALIFICATIONS = new Qualifications(null, null, null);

    private static void rejectsNull(Executable constructor) {
        assertThrows(NullPointerException.class, constructor);
    }

    @Test
    void periodRequiresAStart() {
        rejectsNull(() -> new Period(null, null));
    }

    @Test
    void simpleValuesRequireTheirMandatoryFields() {
        rejectsNull(() -> new Impact(null, "text"));
        rejectsNull(() -> new Impact("value", null));
        rejectsNull(() -> new Phase(null, null));
        rejectsNull(() -> new Education(null, null, null));
        rejectsNull(() -> new Certificate(null, null));
        rejectsNull(() -> new Course(null, null));
        rejectsNull(() -> new SpokenLanguage(null, null));
        rejectsNull(() -> new ContactLink(null, "https://x"));
        rejectsNull(() -> new ContactLink("GitHub", null));
        rejectsNull(() -> new Contact(null, null));
        rejectsNull(() -> new Profile(null, "label", null, null, null, null, null));
        rejectsNull(() -> new Profile("name", null, null, null, null, null, null));
    }

    @Test
    void skillGroupAndWorkExperienceRequireTheirMandatoryFields() {
        rejectsNull(() -> new SkillGroup(null, "Backend", null));
        rejectsNull(() -> new SkillGroup("backend", null, null));
        rejectsNull(() -> new WorkExperience(null, "Acme", "Dev", null, PERIOD, null, null, null));
        rejectsNull(() -> new WorkExperience("acme", null, "Dev", null, PERIOD, null, null, null));
        rejectsNull(() -> new WorkExperience("acme", "Acme", null, null, PERIOD, null, null, null));
        rejectsNull(() -> new WorkExperience("acme", "Acme", "Dev", null, null, null, null, null));
    }

    @Test
    void resumeRequiresItsMandatoryParts() {
        rejectsNull(() -> new Resume(null, PROFILE, CONTACT, null, null, null, QUALIFICATIONS, null));
        rejectsNull(() -> new Resume(Language.ES, null, CONTACT, null, null, null, QUALIFICATIONS, null));
        rejectsNull(() -> new Resume(Language.ES, PROFILE, null, null, null, null, QUALIFICATIONS, null));
        rejectsNull(() -> new Resume(Language.ES, PROFILE, CONTACT, null, null, null, null, null));
    }

    @Test
    void collectionsAreDefensiveCopiesAndImmutable() {
        List<String> source = new ArrayList<>(List.of("Java"));
        SkillGroup group = new SkillGroup("backend", "Backend", source);
        source.add("Mutated afterwards");

        assertEquals(List.of("Java"), group.keywords());
        assertThrows(UnsupportedOperationException.class, () -> group.keywords().add("x"));
    }

    @Test
    void qualificationsAndContactCopyTheirLists() {
        var courses = new ArrayList<>(List.of(new Course("Quarkus", "OpenWebinars")));
        var qualifications = new Qualifications(null, null, courses);
        courses.clear();

        assertEquals(1, qualifications.courses().size());
        assertTrue(qualifications.education().isEmpty());
        assertTrue(qualifications.certificates().isEmpty());
        assertTrue(new Contact("a@b.c", null).links().isEmpty());
    }

    @Test
    void languageTagIsLowerCase() {
        assertEquals("es", Language.ES.tag());
        assertEquals("en", Language.EN.tag());
    }
}
