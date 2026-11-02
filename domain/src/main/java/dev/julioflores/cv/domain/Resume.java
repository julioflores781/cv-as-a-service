package dev.julioflores.cv.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Agregado raíz: el CV completo en un idioma. */
public record Resume(
        Language language,
        Profile profile,
        Contact contact,
        List<Impact> impact,
        List<WorkExperience> work,
        List<SkillGroup> skills,
        Qualifications qualifications,
        List<SpokenLanguage> spokenLanguages) {

    public Resume {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(contact, "contact");
        Objects.requireNonNull(qualifications, "qualifications");
        impact = Lists.copy(impact);
        work = Lists.copy(work);
        skills = Lists.copy(skills);
        spokenLanguages = Lists.copy(spokenLanguages);
    }

    public Optional<WorkExperience> findExperience(String id) {
        return work.stream().filter(w -> w.id().equals(id)).findFirst();
    }

    public Optional<SkillGroup> findSkillGroup(String key) {
        return skills.stream().filter(g -> g.key().equalsIgnoreCase(key)).findFirst();
    }
}
