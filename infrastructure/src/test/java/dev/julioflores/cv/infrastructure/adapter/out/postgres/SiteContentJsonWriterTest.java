package dev.julioflores.cv.infrastructure.adapter.out.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.julioflores.cv.application.content.ContentKind;
import dev.julioflores.cv.domain.Project;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T106: el escritor es el inverso exacto del lector; lo guardado tiene la forma de db/sample. */
@QuarkusTest
class SiteContentJsonWriterTest {

    private static final Path SAMPLE = Path.of("").toAbsolutePath().getParent().resolve("db/sample");

    @Inject
    ObjectMapper mapper;

    private JsonNode sample(ContentKind kind, String lang) throws IOException {
        return mapper.readTree(SAMPLE.resolve(kind.value() + "." + lang + ".json").toFile());
    }

    /** El mismo documento sin claves nulas: el escritor no las escribe, el sample sí trae {@code "price": null}. */
    private static JsonNode withoutNulls(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (Iterator<String> names = object.fieldNames(); names.hasNext(); ) {
                String name = names.next();
                if (object.get(name).isNull()) {
                    names.remove();
                } else {
                    withoutNulls(object.get(name));
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(SiteContentJsonWriterTest::withoutNulls);
        }
        return node;
    }

    @Test
    void writingWhatTheMapperReadsGivesBackTheSampleDocumentsOfEveryKindAndLanguage() throws IOException { // T106
        for (ContentKind kind : ContentKind.values()) {
            for (String lang : List.of("es", "en")) {
                JsonNode original = sample(kind, lang);
                JsonNode written = SiteContentJsonWriter.canonical(kind, original);
                assertEquals(withoutNulls(original.deepCopy()), written, kind + "/" + lang);
            }
        }
    }

    @Test
    void theRoundTripIsLosslessInBothDirections() throws IOException {
        for (String lang : List.of("es", "en")) {
            JsonNode projects = sample(ContentKind.PROJECTS, lang);
            // lector → escritor → lector: el dominio es idéntico
            assertEquals(SiteContentJsonMapper.projects(projects),
                    SiteContentJsonMapper.projects(SiteContentJsonWriter.canonical(ContentKind.PROJECTS, projects)));
            JsonNode services = sample(ContentKind.SERVICES, lang);
            assertEquals(SiteContentJsonMapper.services(services),
                    SiteContentJsonMapper.services(SiteContentJsonWriter.canonical(ContentKind.SERVICES, services)));
            JsonNode architecture = sample(ContentKind.ARCHITECTURE, lang);
            assertEquals(SiteContentJsonMapper.architecture(architecture),
                    SiteContentJsonMapper.architecture(SiteContentJsonWriter.canonical(ContentKind.ARCHITECTURE, architecture)));
        }
    }

    @Test
    void writingIsIdempotent() throws IOException {
        for (ContentKind kind : ContentKind.values()) {
            JsonNode once = SiteContentJsonWriter.canonical(kind, sample(kind, "es"));
            assertEquals(once, SiteContentJsonWriter.canonical(kind, once));
        }
    }

    @Test
    void anAnonymousClientNeverSurvivesTheWriter() throws IOException { // segunda barrera, tras el validador
        JsonNode projects = sample(ContentKind.PROJECTS, "es").deepCopy();
        ((ObjectNode) projects.get("projects").get(1)).put("client", "Nombre Real S.A.");

        JsonNode written = SiteContentJsonWriter.canonical(ContentKind.PROJECTS, projects);

        assertFalse(written.get("projects").get(1).has("client"));
        assertFalse(written.toString().contains("Nombre Real"));
        Project anonymous = SiteContentJsonMapper.projects(written).get(1);
        assertEquals(null, anonymous.client());
    }

    @Test
    void integersAreWrittenAsIntegersAndDecimalsKeepTheirScaleAndUnknownFieldsAreDropped() throws IOException {
        ObjectNode services = (ObjectNode) mapper.readTree("""
                {"services": [
                  {"id": "a", "title": "A", "summary": "S", "price": {"from": 1500.0, "currency": "EUR"}},
                  {"id": "b", "title": "B", "summary": "S", "price": {"from": 99.5, "currency": "EUR"}, "oculto": true}
                ]}""");

        JsonNode written = SiteContentJsonWriter.canonical(ContentKind.SERVICES, services);

        assertEquals("1500", written.get("services").get(0).get("price").get("from").toString());
        assertEquals("99.5", written.get("services").get(1).get("price").get("from").toString());
        assertFalse(written.get("services").get(1).has("oculto"));
    }

    @Test
    void emptyDocumentsWriteEmptyLists() throws IOException {
        JsonNode written = SiteContentJsonWriter.canonical(ContentKind.SERVICES, mapper.readTree("{}"));
        assertTrue(written.get("services").isArray() && written.get("services").isEmpty());
        JsonNode projects = SiteContentJsonWriter.canonical(ContentKind.PROJECTS, mapper.readTree("{\"projects\":[]}"));
        assertTrue(projects.get("projects").isEmpty());
    }
}
