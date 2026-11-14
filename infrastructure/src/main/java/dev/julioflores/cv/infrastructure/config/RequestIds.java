package dev.julioflores.cv.infrastructure.config;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Identificador de petición ({@code X-Request-Id}): se acepta el del cliente solo si es opaco y seguro de reflejar; si no, se genera. */
public final class RequestIds {

    /** Cabecera de petición y respuesta. */
    public static final String HEADER = "X-Request-Id";

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private RequestIds() {}

    /** El id del cliente si cumple {@code ^[A-Za-z0-9._-]{8,64}$}; vacío en cualquier otro caso (nunca se refleja un valor ajeno inválido). */
    public static Optional<String> accept(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
    }

    /** 32 caracteres hexadecimales (un UUID aleatorio sin guiones). */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
