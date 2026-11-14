package dev.julioflores.cv.domain;

/**
 * Política de contraseña del administrador, según NIST 800-63B: solo longitud (12 a 128 caracteres,
 * contados como puntos de código Unicode) y sin reglas de composición. No acepta contraseñas en blanco
 * ni repetir la que ya está en uso.
 */
public final class AdminPasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private AdminPasswordPolicy() {}

    /** @param current la contraseña vigente, o {@code null} si no hay ninguna con la que comparar. */
    public static boolean isAcceptable(String candidate, String current) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        int length = candidate.codePointCount(0, candidate.length());
        return length >= MIN_LENGTH && length <= MAX_LENGTH && !candidate.equals(current);
    }
}
