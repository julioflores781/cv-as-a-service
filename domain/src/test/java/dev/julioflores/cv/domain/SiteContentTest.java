package dev.julioflores.cv.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SiteContentTest {

    private static Project project(Disclosure disclosure, String client) {
        return new Project("p", "Título", "Resumen", ProjectRelation.EMPLOYER, "Backend", disclosure, client,
                "prepaga", null, List.of("Java"), List.of(), List.of());
    }

    @Test
    void publicProjectKeepsTheClientName() {
        assertEquals("Ejemplo S.A.", project(Disclosure.PUBLIC, "Ejemplo S.A.").client());
    }

    @Test
    void anonymousProjectNeverCarriesTheClientNameEvenIfGivenOne() {
        Project anonymous = project(Disclosure.ANONYMOUS, "Cliente Secreto");
        assertNull(anonymous.client());
        assertEquals("prepaga", anonymous.clientLabel());
    }

    @Test
    void projectListsAreImmutableCopiesAndNullMeansEmpty() {
        List<String> stack = new ArrayList<>(List.of("Java"));
        Project project = new Project("p", "t", "s", ProjectRelation.OWN, null, Disclosure.PUBLIC, "c", null, null,
                stack, null, null);
        stack.add("Cobol");
        assertEquals(List.of("Java"), project.stack());
        assertTrue(project.highlights().isEmpty());
        assertTrue(project.links().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> project.stack().add("x"));
    }

    @Test
    void projectRequiresItsEssentialFields() {
        assertThrows(NullPointerException.class, () -> new Project(null, "t", "s", ProjectRelation.OWN, null,
                Disclosure.PUBLIC, null, null, null, null, null, null));
        assertThrows(NullPointerException.class, () -> new Project("p", null, "s", ProjectRelation.OWN, null,
                Disclosure.PUBLIC, null, null, null, null, null, null));
        assertThrows(NullPointerException.class, () -> new Project("p", "t", null, ProjectRelation.OWN, null,
                Disclosure.PUBLIC, null, null, null, null, null, null));
        assertThrows(NullPointerException.class, () -> new Project("p", "t", "s", null, null,
                Disclosure.PUBLIC, null, null, null, null, null, null));
        assertThrows(NullPointerException.class, () -> new Project("p", "t", "s", ProjectRelation.OWN, null,
                null, null, null, null, null, null, null));
    }

    @Test
    void projectLinkRequiresLabelAndUrl() {
        assertThrows(NullPointerException.class, () -> new ProjectLink(null, "https://x"));
        assertThrows(NullPointerException.class, () -> new ProjectLink("x", null));
    }

    @Test
    void enumsParseTheirWireValueAndRejectUnknownOnes() {
        assertEquals(Disclosure.ANONYMOUS, Disclosure.fromValue("anonymous"));
        assertEquals("public", Disclosure.PUBLIC.value());
        assertEquals(ProjectRelation.OWN, ProjectRelation.fromValue("own"));
        assertEquals("employer", ProjectRelation.EMPLOYER.value());
        assertEquals(AdapterStatus.IN_THIS_API, AdapterStatus.fromValue("in-this-api"));
        assertEquals("experiment", AdapterStatus.EXPERIMENT.value());
        assertThrows(IllegalArgumentException.class, () -> Disclosure.fromValue("secret"));
        assertThrows(IllegalArgumentException.class, () -> Disclosure.fromValue(null));
        assertThrows(IllegalArgumentException.class, () -> ProjectRelation.fromValue("friend"));
        assertThrows(IllegalArgumentException.class, () -> AdapterStatus.fromValue("magic"));
    }

    @Test
    void servicePriceMustBeNonNegative() {
        assertEquals(new BigDecimal("0"), new ServicePrice(BigDecimal.ZERO, "EUR").from());
        assertThrows(IllegalArgumentException.class, () -> new ServicePrice(new BigDecimal("-1"), "EUR"));
        assertThrows(NullPointerException.class, () -> new ServicePrice(null, "EUR"));
        assertThrows(NullPointerException.class, () -> new ServicePrice(BigDecimal.ONE, null));
    }

    @Test
    void serviceOfferMayHaveNoPriceButNeedsItsText() {
        assertNull(new ServiceOffer("s", "t", "r", null).price());
        assertThrows(NullPointerException.class, () -> new ServiceOffer(null, "t", "r", null));
        assertThrows(NullPointerException.class, () -> new ServiceOffer("s", null, "r", null));
        assertThrows(NullPointerException.class, () -> new ServiceOffer("s", "t", null, null));
    }

    @Test
    void architectureKeepsPortsAndAdaptersInOrder() {
        PortAdapter postgres = new PortAdapter("postgresql", "PostgreSQL", AdapterStatus.IN_THIS_API, null);
        PortAdapter mongo = new PortAdapter("mongodb", "MongoDB", AdapterStatus.PRODUCTION, "nota");
        ArchitecturePort database = new ArchitecturePort("database", "Base de datos", null, List.of(postgres, mongo));
        Architecture architecture = new Architecture("Dominio", null, List.of(database));
        assertEquals(List.of(postgres, mongo), architecture.ports().get(0).adapters());
        assertTrue(new ArchitecturePort("p", "n", null, null).adapters().isEmpty());
    }

    @Test
    void emptyArchitectureHasNoPorts() {
        assertTrue(Architecture.empty().ports().isEmpty());
    }

    @Test
    void architectureRequiresItsNames() {
        assertThrows(NullPointerException.class, () -> new Architecture(null, null, null));
        assertThrows(NullPointerException.class, () -> new ArchitecturePort(null, "n", null, null));
        assertThrows(NullPointerException.class, () -> new ArchitecturePort("i", null, null, null));
        assertThrows(NullPointerException.class, () -> new PortAdapter(null, "n", AdapterStatus.PRODUCTION, null));
        assertThrows(NullPointerException.class, () -> new PortAdapter("i", null, AdapterStatus.PRODUCTION, null));
        assertThrows(NullPointerException.class, () -> new PortAdapter("i", "n", null, null));
    }
}
