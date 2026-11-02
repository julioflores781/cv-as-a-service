package dev.julioflores.cv.application;

import dev.julioflores.cv.application.port.out.ResumeRepository;
import dev.julioflores.cv.domain.Certificate;
import dev.julioflores.cv.domain.Contact;
import dev.julioflores.cv.domain.ContactLink;
import dev.julioflores.cv.domain.Course;
import dev.julioflores.cv.domain.Education;
import dev.julioflores.cv.domain.Impact;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Period;
import dev.julioflores.cv.domain.Phase;
import dev.julioflores.cv.domain.Profile;
import dev.julioflores.cv.domain.Qualifications;
import dev.julioflores.cv.domain.Resume;
import dev.julioflores.cv.domain.SkillGroup;
import dev.julioflores.cv.domain.SpokenLanguage;
import dev.julioflores.cv.domain.WorkExperience;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

final class ResumeFixtures {

    static final Profile PROFILE =
            new Profile("Julio Flores", "Senior Full Stack Engineer", null, "Madrid", "ES", null, null);

    static final WorkExperience CURRENT_JOB = new WorkExperience(
            "sale-and-brick", "Sale & Brick", "Full Stack Developer", null,
            new Period(YearMonth.of(2017, 10), null), null,
            List.of(new Phase("Soporte", null), new Phase("xHIS", "DXC Technology")),
            List.of("Microservicios"));

    static final WorkExperience OLD_JOB = new WorkExperience(
            "central-madeirense", "Central Madeirense", "Especialista TI", "Caracas",
            new Period(YearMonth.of(2014, 3), YearMonth.of(2016, 8)), null, null, null);

    static final SkillGroup BACKEND = new SkillGroup("backend", "Backend", List.of("Java", "Quarkus"));
    static final SkillGroup DATA = new SkillGroup("data", "Datos", List.of("PostgreSQL"));

    static final Resume SPANISH = new Resume(
            Language.ES,
            PROFILE,
            new Contact("julio@example.com", List.of(new ContactLink("GitHub", "https://github.com/x"))),
            List.of(new Impact("+5.000", "mensajes diarios")),
            List.of(CURRENT_JOB, OLD_JOB),
            List.of(BACKEND, DATA),
            new Qualifications(
                    List.of(new Education("IUT", "T.S.U.", "Informática")),
                    List.of(new Certificate("Oracle SQL Expert", "Oracle")),
                    List.of(new Course("Quarkus", "OpenWebinars"))),
            List.of(new SpokenLanguage("Español", "Nativo")));

    /** Repositorio falso: solo existe el CV en español. */
    static final ResumeRepository ONLY_SPANISH =
            language -> language == Language.ES ? Optional.of(SPANISH) : Optional.empty();

    private ResumeFixtures() {}
}
