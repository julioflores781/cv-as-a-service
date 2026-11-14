package dev.julioflores.cv.infrastructure.adapter.in.rest;

import dev.julioflores.cv.domain.Architecture;
import dev.julioflores.cv.domain.ArchitecturePort;
import dev.julioflores.cv.domain.Contact;
import dev.julioflores.cv.domain.ContactLink;
import dev.julioflores.cv.domain.Impact;
import dev.julioflores.cv.domain.Period;
import dev.julioflores.cv.domain.PortAdapter;
import dev.julioflores.cv.domain.Phase;
import dev.julioflores.cv.domain.Profile;
import dev.julioflores.cv.domain.Project;
import dev.julioflores.cv.domain.ProjectLink;
import dev.julioflores.cv.domain.Qualifications;
import dev.julioflores.cv.domain.Resume;
import dev.julioflores.cv.domain.ServiceOffer;
import dev.julioflores.cv.domain.ServicePrice;
import dev.julioflores.cv.domain.SkillGroup;
import dev.julioflores.cv.domain.WorkExperience;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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

    @Schema(name = "ServicePrice", description = "Precio «desde» de un servicio.")
    record ServicePriceDto(
            @Schema(required = true, description = "Importe mínimo, sin impuestos.", example = "1500") BigDecimal from,
            @Schema(required = true, description = "Moneda ISO 4217.", example = "EUR") String currency) {}

    @Schema(name = "Service", description = "Un servicio que ofrezco.")
    record ServiceDto(
            @Schema(required = true, description = "Identificador estable.", example = "api-backend") String id,
            @Schema(required = true, description = "Nombre del servicio.", example = "APIs y backend") String title,
            @Schema(required = true, description = "Qué incluye.") String summary,
            @Schema(nullable = true, description = "Precio «desde». Nulo significa «a consultar».") ServicePriceDto price) {}

    @Schema(name = "ProjectLink", description = "Enlace relacionado con un proyecto.")
    record ProjectLinkDto(
            @Schema(required = true, description = "Texto del enlace.", example = "Abrir la aplicación") String label,
            @Schema(required = true, description = "URL.", example = "https://example.com") String url) {}

    @Schema(name = "Project", description = "Un proyecto en el que he trabajado.")
    record ProjectDto(
            @Schema(required = true, description = "Identificador estable.", example = "turnos-clinica") String id,
            @Schema(required = true, description = "Nombre del proyecto.", example = "Plataforma de turnos médicos") String title,
            @Schema(required = true, description = "Qué hace y qué problema resuelve.") String summary,
            @Schema(required = true, description = "`own`: producto propio; `employer`: trabajo para una empresa.", enumeration = {"own", "employer"}, example = "employer") String relation,
            @Schema(nullable = true, description = "Mi papel en el proyecto.", example = "Backend y web") String role,
            @Schema(required = true, description = "`public`: se puede nombrar al cliente; `anonymous`: solo se describe.", enumeration = {"public", "anonymous"}, example = "anonymous") String disclosure,
            @Schema(nullable = true, description = "Nombre del cliente. Solo aparece si `disclosure` es `public`: en un proyecto anónimo nunca se expone.") String client,
            @Schema(nullable = true, description = "Descripción del cliente sin nombrarlo.", example = "clínica privada") String clientLabel,
            @Schema(nullable = true, description = "Cuándo trabajé en él.") PeriodDto period,
            @Schema(required = true, description = "Tecnologías usadas.") List<String> stack,
            @Schema(required = true, description = "Logros y decisiones destacadas. Vacío si no aplica.") List<String> highlights,
            @Schema(required = true, description = "Enlaces relacionados. Vacío si no aplica.") List<ProjectLinkDto> links,
            @Schema(required = true, description = "Capturas del proyecto; la primera es la portada. Vacío si no hay (y siempre vacío en un proyecto de empleador o anónimo).") List<ProjectImageDto> images) {}

    @Schema(name = "ProjectImage", description = "Una imagen de un proyecto, servida desde el almacenamiento de medios.")
    record ProjectImageDto(
            @Schema(required = true, description = "URL pública (inmutable: la clave cambia si cambia la imagen).", example = "https://media.example.com/site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp") String url,
            @Schema(required = true, description = "Texto alternativo, en el idioma pedido.", example = "Panel de reservas con la agenda semanal") String alt,
            @Schema(required = true, description = "Ancho en píxeles.", example = "1280") int width,
            @Schema(required = true, description = "Alto en píxeles.", example = "720") int height) {}

    @Schema(name = "PortAdapter", description = "Una tecnología que se enchufa a un puerto.")
    record PortAdapterDto(
            @Schema(required = true, description = "Identificador estable.", example = "postgresql") String id,
            @Schema(required = true, description = "Nombre.", example = "PostgreSQL") String name,
            @Schema(required = true, description = "`in-this-api`: implementado en esta API; `production`: usado en sistemas en producción; `experiment`: solo en proyectos de prueba.", enumeration = {"in-this-api", "production", "experiment"}, example = "in-this-api") String status,
            @Schema(nullable = true, description = "Matiz sobre la experiencia con esta tecnología.") String note) {}

    @Schema(name = "ArchitecturePort", description = "Un puerto del hexágono con sus adaptadores intercambiables.")
    record ArchitecturePortDto(
            @Schema(required = true, description = "Identificador estable.", example = "database") String id,
            @Schema(required = true, description = "Nombre del puerto.", example = "Base de datos") String name,
            @Schema(nullable = true, description = "Para qué sirve el puerto.") String description,
            @Schema(required = true, description = "Adaptadores que se pueden enchufar, en orden de presentación.") List<PortAdapterDto> adapters) {}

    @Schema(name = "ArchitectureCore", description = "El núcleo del hexágono, que no cambia al cambiar de adaptador.")
    record ArchitectureCoreDto(
            @Schema(required = true, description = "Nombre del núcleo.", example = "Dominio") String name,
            @Schema(nullable = true, description = "Qué contiene.") String description) {}

    @Schema(name = "Architecture", description = "El diagrama de arquitectura hexagonal. Sin puertos si todavía no hay nada publicado.")
    record ArchitectureDto(
            @Schema(required = true, description = "El núcleo.") ArchitectureCoreDto core,
            @Schema(required = true, description = "Los puertos, en orden de presentación.") List<ArchitecturePortDto> ports) {}

    static ServiceDto toDto(ServiceOffer s) {
        ServicePrice price = s.price();
        return new ServiceDto(s.id(), s.title(), s.summary(),
                price == null ? null : new ServicePriceDto(price.from(), price.currency()));
    }

    /** @param imageUrl la URL pública de una clave; vacío si el almacenamiento no está configurado (entonces no se exponen imágenes) */
    static ProjectDto toDto(Project p, Function<String, Optional<String>> imageUrl) {
        return new ProjectDto(
                p.id(), p.title(), p.summary(), p.relation().value(), p.role(), p.disclosure().value(), p.client(),
                p.clientLabel(), p.period() == null ? null : toDto(p.period()), p.stack(), p.highlights(),
                map(p.links(), l -> new ProjectLinkDto(l.label(), l.url())),
                p.images().stream()
                        .flatMap(i -> imageUrl.apply(i.key()).map(url -> new ProjectImageDto(url, i.alt(), i.width(), i.height())).stream())
                        .toList());
    }

    static ArchitectureDto toDto(Architecture a) {
        return new ArchitectureDto(new ArchitectureCoreDto(a.coreName(), a.coreDescription()), map(a.ports(), ApiModels::toDto));
    }

    static ArchitecturePortDto toDto(ArchitecturePort p) {
        return new ArchitecturePortDto(p.id(), p.name(), p.description(), map(p.adapters(), ApiModels::toDto));
    }

    static PortAdapterDto toDto(PortAdapter a) {
        return new PortAdapterDto(a.id(), a.name(), a.status().value(), a.note());
    }
}
