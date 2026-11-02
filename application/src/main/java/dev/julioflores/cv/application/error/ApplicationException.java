package dev.julioflores.cv.application.error;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Base de todos los errores de negocio. Lleva un {@link ErrorCode} y los argumentos con los que se
 * rellena el texto traducido. El mensaje de la excepción va en inglés y es solo para logs y
 * desarrolladores: nunca se muestra tal cual al usuario.
 */
public class ApplicationException extends RuntimeException {

    private final ErrorCode code;
    private final List<Object> args;

    public ApplicationException(ErrorCode code, String developerMessage, Object... args) {
        super(developerMessage);
        this.code = Objects.requireNonNull(code, "code");
        this.args = List.copyOf(Arrays.asList(args));
    }

    public ErrorCode code() {
        return code;
    }

    /** Valores para los marcadores {0}, {1}… del texto traducido. */
    public List<Object> args() {
        return args;
    }
}
