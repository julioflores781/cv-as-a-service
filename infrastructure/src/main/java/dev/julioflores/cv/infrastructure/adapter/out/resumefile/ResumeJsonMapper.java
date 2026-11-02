package dev.julioflores.cv.infrastructure.adapter.out.resumefile;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.function.Function;
import java.util.stream.StreamSupport;

/** Traduce el esquema de resume.{lang}.json (basado en JSON Resume) al agregado de dominio. */
final class ResumeJsonMapper {

    private ResumeJsonMapper() {}

    static Resume toResume(Language language, JsonNode root) {
        JsonNode basics = root.path("basics");
        return new Resume(
                language,
                profile(basics),
                contact(basics),
                list(root.path("impact"), n -> new Impact(text(n, "value"), text(n, "text"))),
                list(root.path("work"), ResumeJsonMapper::workExperience),
                list(root.path("skills"), n -> new SkillGroup(text(n, "group"), text(n, "name"), strings(n.path("keywords")))),
                new Qualifications(
                        list(root.path("education"),
                                n -> new Education(text(n, "institution"), text(n, "studyType"), text(n, "area"))),
                        list(root.path("certificates"), n -> new Certificate(text(n, "name"), text(n, "issuer"))),
                        list(root.path("courses"), n -> new Course(text(n, "name"), text(n, "issuer")))),
                list(root.path("languages"), n -> new SpokenLanguage(text(n, "language"), text(n, "fluency"))));
    }

    private static Profile profile(JsonNode basics) {
        JsonNode location = basics.path("location");
        return new Profile(
                text(basics, "name"),
                text(basics, "label"),
                text(basics, "tagline"),
                text(location, "city"),
                text(location, "countryCode"),
                text(basics, "summary"),
                text(basics, "availability"));
    }

    private static Contact contact(JsonNode basics) {
        return new Contact(
                text(basics, "email"),
                list(basics.path("profiles"), n -> new ContactLink(text(n, "network"), text(n, "url"))));
    }

    private static WorkExperience workExperience(JsonNode n) {
        String end = text(n, "endDate");
        return new WorkExperience(
                text(n, "id"),
                text(n, "name"),
                text(n, "position"),
                text(n, "location"),
                new Period(YearMonth.parse(text(n, "startDate")), end == null ? null : YearMonth.parse(end)),
                text(n, "summary"),
                list(n.path("phases"), p -> new Phase(text(p, "title"), text(p, "client"))),
                strings(n.path("highlights")));
    }

    private static <T> List<T> list(JsonNode array, Function<JsonNode, T> mapper) {
        return StreamSupport.stream(array.spliterator(), false).map(mapper).toList();
    }

    private static List<String> strings(JsonNode array) {
        return list(array, JsonNode::asText);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
