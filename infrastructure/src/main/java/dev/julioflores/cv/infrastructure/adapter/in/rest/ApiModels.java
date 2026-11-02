package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.domain.Contact;
import dev.julioflores.cv.domain.ContactLink;
import dev.julioflores.cv.domain.Impact;
import dev.julioflores.cv.domain.Period;
import dev.julioflores.cv.domain.Phase;
import dev.julioflores.cv.domain.Profile;
import dev.julioflores.cv.domain.Qualifications;
import dev.julioflores.cv.domain.Resume;
import dev.julioflores.cv.domain.SkillGroup;
import dev.julioflores.cv.domain.WorkExperience;
import java.util.List;
import java.util.function.Function;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Contrato JSON de la API. Se mantiene separado del dominio para poder evolucionar cada uno. */
final class ApiModels {

    private ApiModels() {}

    @Schema(name = "Profile", description = "Presentación profesional.")
    record ProfileDto(
            @Schema(required = true, description = "Nombre completo.", example = "Julio Flores") String name,
            @Schema(required = true, description = "Titular profesional.", example = "Senior Full Stack Engineer · foco backend") String label,
            @Schema(nullable = true, description = "Subtítulo con el stack y la especialidad.", example = "Java/Quarkus & NestJS · Interoperabilidad sanitaria (HL7/FHIR)") String tagline,
            @Schema(nullable = true, description = "Ciudad de residencia.", example = "Monforte de Lemos") String city,
            @Schema(nullable = true, description = "País en formato ISO 3166-1 alfa-2.", example = "ES") String countryCode,
            @Schema(nullable = true, description = "Resumen profesional, en el idioma pedido.") String summary,
            @Schema(nullable = true, description = "Disponibilidad laboral.", example = "Abierto a roles backend / full stack en España o en remoto.") String availability) {}

    @Schema(name = "Link", description = "Enlace a un perfil externo.")
    record LinkDto(
            @Schema(required = true, description = "Red o servicio.", example = "GitHub") String network,
            @Schema(required = true, description = "URL del perfil.", example = "https://github.com/julioflores781") String url) {}

    @Schema(name = "Contact", description = "Datos de contacto públicos. Nunca incluye teléfono, documento ni fecha de nacimiento.")
    record ContactDto(
            @Schema(required = true, description = "Correo electrónico.", example = "julioflores781@gmail.com") String email,
            @Schema(required = true, description = "Perfiles externos.") List<LinkDto> profiles) {}

    @Schema(name = "Impact", description = "Una cifra de impacto en producción.")
    record ImpactDto(
            @Schema(required = true, description = "La cifra tal como se muestra.", example = "+5.000") String value,
            @Schema(required = true, description = "Qué mide la cifra.", example = "mensajes diarios en un proxy HL7 v2.3.1 → REST") String text) {}

    @Schema(name = "Period", description = "Periodo de tiempo con precisión de mes.")
    record PeriodDto(
            @Schema(required = true, description = "Inicio, formato AAAA-MM.", example = "2017-10") String start,
            @Schema(nullable = true, description = "Fin, formato AAAA-MM. Nulo si sigue en curso.", example = "2016-08") String end,
            @Schema(required = true, description = "Verdadero si el periodo sigue abierto (`end` es nulo).", example = "false") boolean current) {}

    @Schema(name = "Phase", description = "Etapa dentro de un mismo puesto.")
    record PhaseDto(
            @Schema(required = true, description = "Nombre de la etapa.", example = "Soporte y análisis funcional") String title,
            @Schema(nullable = true, description = "Cliente final, si aplica.", example = "Swiss Medical Group y Finochetto") String client) {}

    @Schema(name = "Experience", description = "Una etapa de la trayectoria laboral.")
    record ExperienceDto(
            @Schema(required = true, description = "Identificador estable, usable en `/experience/{id}`.", example = "sale-and-brick") String id,
            @Schema(required = true, description = "Empresa.", example = "Sale & Brick") String company,
            @Schema(required = true, description = "Puesto.", example = "Full Stack Developer · foco backend") String position,
            @Schema(nullable = true, description = "Lugar, si es relevante.", example = "Caracas, Venezuela") String location,
            @Schema(required = true, description = "Periodo en el puesto.") PeriodDto period,
            @Schema(nullable = true, description = "Resumen de la etapa.") String summary,
            @Schema(required = true, description = "Fases de la progresión interna, en orden. Vacío si no aplica.") List<PhaseDto> phases,
            @Schema(required = true, description = "Logros y responsabilidades. Vacío si no aplica.") List<String> highlights) {}

    @Schema(name = "SkillGroup", description = "Grupo de tecnologías.")
    record SkillGroupDto(
            @Schema(required = true, description = "Clave estable del grupo, usable en `?group=`.", example = "backend") String key,
            @Schema(required = true, description = "Nombre en el idioma pedido.", example = "Backend") String name,
            @Schema(required = true, description = "Tecnologías del grupo.", example = "[\"Java 17+\", \"Quarkus\", \"Spring Boot\"]") List<String> keywords) {}

    @Schema(name = "Education", description = "Formación reglada.")
    record EducationDto(
            @Schema(required = true, description = "Centro.", example = "IUT Rufino Blanco Fombona") String institution,
            @Schema(nullable = true, description = "Tipo de titulación.", example = "Técnico Superior Universitario (T.S.U.)") String studyType,
            @Schema(nullable = true, description = "Área de estudio.", example = "Informática") String area) {}

    @Schema(name = "Certificate", description = "Certificación profesional.")
    record CertificateDto(
            @Schema(required = true, description = "Nombre.", example = "Oracle Database SQL Expert (1Z0-047)") String name,
            @Schema(nullable = true, description = "Entidad emisora.", example = "Oracle University") String issuer) {}

    @Schema(name = "Course", description = "Curso de formación.")
    record CourseDto(
            @Schema(required = true, description = "Nombre.", example = "Quarkus") String name,
            @Schema(nullable = true, description = "Plataforma o academia.", example = "OpenWebinars") String issuer) {}

    @Schema(name = "Qualifications", description = "Formación reglada, certificaciones y cursos.")
    record QualificationsDto(
            @Schema(required = true, description = "Formación reglada.") List<EducationDto> education,
            @Schema(required = true, description = "Certificaciones.") List<CertificateDto> certificates,
            @Schema(required = true, description = "Cursos.") List<CourseDto> courses) {}

    @Schema(name = "SpokenLanguage", description = "Idioma hablado y nivel.")
    record SpokenLanguageDto(
            @Schema(required = true, description = "Idioma.", example = "Español") String language,
            @Schema(nullable = true, description = "Nivel.", example = "Nativo") String fluency) {}

    @Schema(name = "Resume", description = "El CV completo en un idioma.")
    record ResumeDto(
            @Schema(required = true, description = "Idioma del contenido.", enumeration = {"es", "en"}, example = "es") String language,
            @Schema(required = true, description = "Presentación profesional.") ProfileDto profile,
            @Schema(required = true, description = "Datos de contacto públicos.") ContactDto contact,
            @Schema(required = true, description = "Cifras de impacto en producción.") List<ImpactDto> impact,
            @Schema(required = true, description = "Trayectoria, de más reciente a más antigua.") List<ExperienceDto> experience,
            @Schema(required = true, description = "Stack tecnológico agrupado por áreas.") List<SkillGroupDto> skills,
            @Schema(required = true, description = "Formación reglada, certificaciones y cursos.") QualificationsDto education,
            @Schema(required = true, description = "Idiomas hablados y nivel.") List<SpokenLanguageDto> languages) {}

    static ProfileDto toDto(Profile p) {
        return new ProfileDto(p.name(), p.label(), p.tagline(), p.city(), p.countryCode(), p.summary(), p.availability());
    }

    static ContactDto toDto(Contact c) {
        return new ContactDto(c.email(), map(c.links(), ApiModels::toDto));
    }

    static LinkDto toDto(ContactLink l) {
        return new LinkDto(l.network(), l.url());
    }

    static ImpactDto toDto(Impact i) {
        return new ImpactDto(i.value(), i.text());
    }

    static PeriodDto toDto(Period p) {
        return new PeriodDto(p.start().toString(), p.end() == null ? null : p.end().toString(), p.current());
    }

    static PhaseDto toDto(Phase p) {
        return new PhaseDto(p.title(), p.client());
    }

    static ExperienceDto toDto(WorkExperience w) {
        return new ExperienceDto(
                w.id(), w.company(), w.position(), w.location(), toDto(w.period()), w.summary(),
                map(w.phases(), ApiModels::toDto), w.highlights());
    }

    static SkillGroupDto toDto(SkillGroup g) {
        return new SkillGroupDto(g.key(), g.name(), g.keywords());
    }

    static QualificationsDto toDto(Qualifications q) {
        return new QualificationsDto(
                map(q.education(), e -> new EducationDto(e.institution(), e.studyType(), e.area())),
                map(q.certificates(), c -> new CertificateDto(c.name(), c.issuer())),
                map(q.courses(), c -> new CourseDto(c.name(), c.issuer())));
    }

    static ResumeDto toDto(Resume r) {
        return new ResumeDto(
                r.language().tag(),
                toDto(r.profile()),
                toDto(r.contact()),
                map(r.impact(), ApiModels::toDto),
                map(r.work(), ApiModels::toDto),
                map(r.skills(), ApiModels::toDto),
                toDto(r.qualifications()),
                map(r.spokenLanguages(), s -> new SpokenLanguageDto(s.language(), s.fluency())));
    }

    static <T, R> List<R> map(List<T> source, Function<T, R> mapper) {
        return source.stream().map(mapper).toList();
    }
}
