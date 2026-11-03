package dev.julioflores.cv.infrastructure.adapter.in.rest.error;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.julioflores.cv.application.error.ErrorCode;
import dev.julioflores.cv.domain.Language;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guardián de paridad: ningún error se queda sin traducir ni sin estado HTTP. */
class ProblemCatalogTest {

    private static Set<String> keys(Language language) {
        return new TreeSet<>(Collections.list(ProblemMessages.bundle(language).getKeys()));
    }

    @Test
    void everyApplicationErrorCodeHasAnApiError() {
        for (ErrorCode code : ErrorCode.values()) {
            assertDoesNotThrow(() -> ApiError.of(code), code + " no tiene su ApiError");
        }
    }

    @Test
    void everyApiErrorHasTitleAndDetailInEveryLanguage() {
        for (Language language : Language.values()) {
            for (ApiError error : ApiError.values()) {
                String title = ProblemMessages.title(error, language);
                String detail = ProblemMessages.detail(error, language, List.of("x"));
                assertFalse(title.isBlank(), error + " sin título en " + language);
                assertFalse(detail.isBlank(), error + " sin detalle en " + language);
            }
        }
    }

    @Test
    void bundlesHaveExactlyTheKeysOfTheCatalog() {
        Set<String> expected = Arrays.stream(ApiError.values())
                .flatMap(e -> Stream.of(e.name() + ".title", e.name() + ".detail"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(expected, keys(Language.ES), "problems_es.properties no coincide con ApiError");
        assertEquals(expected, keys(Language.EN), "problems_en.properties no coincide con ApiError");
    }

    @Test
    void businessErrorsInterpolateTheirArgument() {
        for (ErrorCode code : ErrorCode.values()) {
            // Un error sin marcador {0} en su texto no lleva dato de la petición: no hay nada que interpolar.
            if (!ProblemMessages.bundle(Language.EN).getString(code.name() + ".detail").contains("{0}")) {
                continue;
            }
            for (Language language : Language.values()) {
                String detail = ProblemMessages.detail(ApiError.of(code), language, List.of("ARG-123"));
                assertTrue(detail.contains("ARG-123"), code + " no usa su argumento en " + language);
            }
        }
    }

    @Test
    void apostrophesSurviveMessageFormat() {
        // Un apóstrofo sin duplicar se come el marcador {0}: es el fallo clásico de MessageFormat.
        assertEquals("No experience with id 'nope'.",
                ProblemMessages.detail(ApiError.EXPERIENCE_NOT_FOUND, Language.EN, List.of("nope")));
        assertEquals("No existe ninguna experiencia con el id «nope».",
                ProblemMessages.detail(ApiError.EXPERIENCE_NOT_FOUND, Language.ES, List.of("nope")));
    }

    @Test
    void jaxRsStatusesMapToTransportErrors() {
        assertEquals(ApiError.NOT_FOUND, ApiError.fromStatus(404));
        assertEquals(ApiError.METHOD_NOT_ALLOWED, ApiError.fromStatus(405));
        assertEquals(ApiError.NOT_ACCEPTABLE, ApiError.fromStatus(406));
        assertEquals(ApiError.UNSUPPORTED_MEDIA_TYPE, ApiError.fromStatus(415));
        assertEquals(ApiError.BAD_REQUEST, ApiError.fromStatus(422));
        assertEquals(ApiError.UNEXPECTED, ApiError.fromStatus(503));
    }

    @Test
    void statusesAreValidHttpErrors() {
        for (ApiError error : ApiError.values()) {
            assertTrue(error.status() >= 400 && error.status() < 600, error + " con estado " + error.status());
        }
    }
}
