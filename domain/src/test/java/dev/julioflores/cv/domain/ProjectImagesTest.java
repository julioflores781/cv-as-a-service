package dev.julioflores.cv.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectImagesTest {

    private static final String K1 = "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp";
    private static final String K2 = "site/projects/9f1c2d7e-2222-4a2b-8c3d-bbbbbbbbbbbb.png";

    private static final List<ProjectImage> IMAGES =
            List.of(new ProjectImage(K1, "Portada", 1280, 720), new ProjectImage(K2, "Detalle", 800, 600));

    private static Project project(ProjectRelation relation, Disclosure disclosure, List<ProjectImage> images) {
        return new Project("p", "Título", "Resumen", relation, null, disclosure, null, "etiqueta", null, List.of(), List.of(),
                List.of(), images);
    }

    @Test // T4
    void anOwnPublicProjectKeepsItsImagesAndTheirOrder() {
        Project project = project(ProjectRelation.OWN, Disclosure.PUBLIC, IMAGES);
        assertEquals(IMAGES, project.images());
        assertEquals(K1, project.images().get(0).key());
    }

    @Test // T5
    void employerAndAnonymousProjectsDropTheirImages() {
        assertTrue(project(ProjectRelation.EMPLOYER, Disclosure.PUBLIC, IMAGES).images().isEmpty());
        assertTrue(project(ProjectRelation.OWN, Disclosure.ANONYMOUS, IMAGES).images().isEmpty());
        assertTrue(project(ProjectRelation.EMPLOYER, Disclosure.ANONYMOUS, IMAGES).images().isEmpty());
    }

    @Test // T6
    void anImageWithATooLongAltOrNonPositiveSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectImage(K1, "a".repeat(201), 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new ProjectImage(K1, "ok", 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new ProjectImage(K1, "ok", 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProjectImage(K1, "ok", -1, 10));
        assertEquals(200, new ProjectImage(K1, "a".repeat(200), 10, 10).alt().length());
    }

    // ── extras fuera del plan ──────────────────────────────────────────────────────

    @Test
    void imageListsAreImmutableCopiesAndNullMeansEmpty() {
        assertTrue(project(ProjectRelation.OWN, Disclosure.PUBLIC, null).images().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> project(ProjectRelation.OWN, Disclosure.PUBLIC, IMAGES).images().add(IMAGES.get(0)));
        assertThrows(NullPointerException.class, () -> new ProjectImage(null, "a", 1, 1));
        assertThrows(NullPointerException.class, () -> new ProjectImage(K1, null, 1, 1));
    }

    @Test
    void theTwelveArgumentConstructorMeansNoImages() {
        Project project = new Project("p", "t", "s", ProjectRelation.OWN, null, Disclosure.PUBLIC, null, null, null, null,
                null, null);
        assertTrue(project.images().isEmpty());
    }

    @Test
    void imageKeysHaveExactlyOneShape() {
        UUID id = UUID.fromString("9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa");
        assertEquals(K1, ImageKey.of(id, ImageFormat.WEBP));
        assertTrue(ImageKey.isValid(K1));
        assertTrue(ImageKey.isValid(K2));
        assertEquals(Optional.of("9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa"), ImageKey.idOf(K1));
        for (String bad : new String[] {
            "site/projects/../secret.png", "other/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.png",
            "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.svg", "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.jpeg",
            "site/projects/not-a-uuid.png", "site/projects/9F1C2D7E-1111-4A2B-8C3D-AAAAAAAAAAAA.png",
            "site/projects/x/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.png", "/site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.png",
            "", null}) {
            assertFalse(ImageKey.isValid(bad), String.valueOf(bad));
            assertTrue(ImageKey.idOf(bad).isEmpty());
        }
    }
}
