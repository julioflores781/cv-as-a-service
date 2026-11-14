package dev.julioflores.cv.application.audit;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Un hecho auditable. Los detalles son pares clave-valor de texto que el constructor deja inofensivos para un log de una línea por
 * registro: las claves solo conservan {@code [A-Za-z0-9_.-]} (lo demás pasa a {@code _}), las entradas con clave nula o vacía se
 * descartan, un valor nulo es vacío, los espacios, saltos de línea y caracteres de control de un valor pasan a {@code _} y cada
 * valor se trunca a {@value #MAX_VALUE} caracteres. Así nadie puede falsear otra línea ni otro campo. Los detalles quedan
 * ordenados por clave y son inmutables.
 *
 * <p>Nunca van aquí contraseñas, tokens, hashes, cuerpos, comentarios ni datos de mensajes de contacto: quien emite el evento solo
 * pasa identificadores, contadores y banderas.
 */
public record AuditEvent(AuditAction action, AuditOutcome outcome, Map<String, String> details) {

    public static final int MAX_VALUE = 120;

    public AuditEvent {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outcome, "outcome");
        Map<String, String> clean = new TreeMap<>();
        if (details != null) {
            details.forEach((key, value) -> {
                String cleanKey = key(key);
                if (!cleanKey.isEmpty()) {
                    clean.put(cleanKey, value(value));
                }
            });
        }
        details = Collections.unmodifiableMap(clean);
    }

    /** @param keyValues pares {@code clave, valor, clave, valor…}; los valores se pasan con {@code String.valueOf} */
    public static AuditEvent success(AuditAction action, Object... keyValues) {
        return new AuditEvent(action, AuditOutcome.SUCCESS, pairs(keyValues));
    }

    /** Un fallo de seguridad; mismos pares que {@link #success}. */
    public static AuditEvent failure(AuditAction action, Object... keyValues) {
        return new AuditEvent(action, AuditOutcome.FAILURE, pairs(keyValues));
    }

    private static Map<String, String> pairs(Object[] keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("details must be key/value pairs");
        }
        Map<String, String> map = new TreeMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), String.valueOf(keyValues[i + 1]));
        }
        return map;
    }

    private static String key(String raw) {
        return raw == null || raw.isBlank() ? "" : raw.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String value(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(raw.length(), MAX_VALUE));
        for (int i = 0; i < raw.length() && out.length() < MAX_VALUE; i++) {
            char c = raw.charAt(i);
            boolean separator = Character.isISOControl(c) || Character.isWhitespace(c) || Character.isSpaceChar(c)
                    || c == ' ' || c == ' ' || c == '\u0085';
            out.append(separator ? '_' : c);
        }
        if (out.length() > 0 && Character.isHighSurrogate(out.charAt(out.length() - 1)) && out.length() == MAX_VALUE) {
            out.setLength(out.length() - 1); // no deja media pareja suplente
        }
        return out.toString();
    }
}
