package dev.julioflores.cv.domain;

import java.util.List;

/** Formación reglada, certificaciones y cursos. */
public record Qualifications(List<Education> education, List<Certificate> certificates, List<Course> courses) {

    public Qualifications {
        education = Lists.copy(education);
        certificates = Lists.copy(certificates);
        courses = Lists.copy(courses);
    }
}
