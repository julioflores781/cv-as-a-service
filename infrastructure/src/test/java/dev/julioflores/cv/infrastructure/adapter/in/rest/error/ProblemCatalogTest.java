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

    @Test
    void adminErrorsHaveTheirDocumentedStatuses() { // T117 (parte auth): los códigos nuevos y su estado HTTP
        assertEquals(401, ApiError.ADMIN_INVALID_CREDENTIALS.status());
        assertEquals(401, ApiError.ADMIN_UNAUTHORIZED.status());
        assertEquals(403, ApiError.ADMIN_CURRENT_PASSWORD_WRONG.status());
        assertEquals(422, ApiError.ADMIN_WEAK_PASSWORD.status());
        assertEquals(429, ApiError.ADMIN_LOGIN_LOCKED.status());
        assertEquals(429, ApiError.ADMIN_LOGIN_RATE_LIMITED.status());
        assertEquals(ApiError.ADMIN_UNAUTHORIZED, ApiError.fromStatus(401));
        assertEquals(ApiError.FORBIDDEN, ApiError.fromStatus(403));
        assertEquals("Too many failed attempts. Try again in 900 seconds.",
                ProblemMessages.detail(ApiError.ADMIN_LOGIN_LOCKED, Language.EN, List.of(900L)));
    }

    @Test
    void contentErrorsHaveTheirDocumentedStatusesAndMessages() { // T117 (parte contenido)
        assertEquals(409, ApiError.CONTENT_VERSION_CONFLICT.status());
        assertEquals(404, ApiError.CONTENT_VERSION_NOT_FOUND.status());
        assertEquals(422, ApiError.CONTENT_INVALID.status());
        assertEquals(422, ApiError.CONTENT_PARITY_BROKEN.status());
        assertEquals(413, ApiError.CONTENT_TOO_LARGE.status());
        assertEquals(ApiError.NOT_FOUND, ApiError.of(ErrorCode.NOT_FOUND));
        assertEquals("Someone published first: the active version in 'es' is 4, not the one you were editing. Reload and try again.",
                ProblemMessages.detail(ApiError.CONTENT_VERSION_CONFLICT, Language.EN, List.of("es", "4")));
        assertEquals("El contenido incumple las reglas (2 problema(s)). El detalle está en «errors».",
                ProblemMessages.detail(ApiError.CONTENT_INVALID, Language.ES, List.of("2")));
    }

    @Test
    void imageErrorsHaveTheirDocumentedStatusesAndMessages() { // T117 (parte imágenes)
        assertEquals(400, ApiError.IMAGE_INVALID.status());
        assertEquals(413, ApiError.IMAGE_TOO_LARGE.status());
        assertEquals(415, ApiError.IMAGE_UNSUPPORTED_TYPE.status());
        assertEquals(503, ApiError.IMAGE_STORAGE_UNAVAILABLE.status());
        assertEquals(404, ApiError.IMAGE_NOT_FOUND.status());
        assertEquals(409, ApiError.IMAGE_IN_USE.status());
        assertEquals("The image is larger than the 5 MB limit.",
                ProblemMessages.detail(ApiError.IMAGE_TOO_LARGE, Language.EN, List.of("5")));
        assertEquals("No existe ninguna imagen con el id «abc».",
                ProblemMessages.detail(ApiError.IMAGE_NOT_FOUND, Language.ES, List.of("abc")));
        assertEquals("There is no image with id 'abc'.",
                ProblemMessages.detail(ApiError.IMAGE_NOT_FOUND, Language.EN, List.of("abc")));
    }

    @Test
    void noDetailLeavesAnUnsubstitutedPlaceholderWhenGivenAsManyArgumentsAsItHas() {
        // Un texto con {n} necesita n+1 argumentos; si la excepción correspondiente no los pasa, el marcador llega al cliente.
        // Esto fija el contrato del catálogo (cada marcador se puede rellenar); que cada excepción pase sus argumentos lo
        // comprueban las pruebas de los recursos (p. ej. el 404 y el 409 del borrado de imágenes).
        java.util.regex.Pattern marker = java.util.regex.Pattern.compile("\\{(\\d+)}");
        for (ApiError error : ApiError.values()) {
            for (Language language : Language.values()) {
                String raw = ProblemMessages.bundle(language).getString(error.name() + ".detail");
                int needed = marker.matcher(raw).results().mapToInt(m -> Integer.parseInt(m.group(1)) + 1).max().orElse(0);
                List<Object> args = java.util.Collections.nCopies(needed, "X");
                String detail = ProblemMessages.detail(error, language, args);
                assertFalse(marker.matcher(detail).find(), error + " " + language + ": " + detail);
            }
        }
    }
}
