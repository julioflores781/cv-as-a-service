package dev.julioflores.cv.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.julioflores.cv.application.port.out.SiteContentRepository;
import dev.julioflores.cv.application.service.GetArchitectureService;
import dev.julioflores.cv.application.service.GetProjectsService;
import dev.julioflores.cv.application.service.GetServicesService;
import dev.julioflores.cv.domain.Architecture;
import dev.julioflores.cv.domain.Language;
import dev.julioflores.cv.domain.Project;
import dev.julioflores.cv.domain.ServiceOffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SiteContentServicesTest {

    private final List<ServiceOffer> offers = List.of(new ServiceOffer("s", "Servicio", "Resumen", null));
    private final List<Project> projects = List.of();
    private final Architecture architecture = Architecture.empty();
    private final List<Language> asked = new ArrayList<>();

    /** Repositorio de mentira: devuelve siempre lo mismo y anota el idioma pedido. */
    private final SiteContentRepository content = new SiteContentRepository() {
        @Override
        public List<ServiceOffer> services(Language language) {
            asked.add(language);
            return offers;
        }

        @Override
        public List<Project> projects(Language language) {
            asked.add(language);
            return projects;
        }

        @Override
        public Architecture architecture(Language language) {
            asked.add(language);
            return architecture;
        }
    };

    @Test
    void eachUseCaseReturnsWhatTheRepositoryHasForTheRequestedLanguage() {
        assertSame(offers, new GetServicesService(content).execute(Language.ES));
        assertSame(projects, new GetProjectsService(content).execute(Language.EN));
        assertSame(architecture, new GetArchitectureService(content).execute(Language.ES));
        assertEquals(List.of(Language.ES, Language.EN, Language.ES), asked);
    }
}
